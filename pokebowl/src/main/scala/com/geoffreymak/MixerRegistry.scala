package com.geoffreymak

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model.StatusCodes.{Accepted, BadRequest, Created}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import akka.util.Timeout

final case class MixingRequest(depositAmount: String, disbursements: Seq[DisbursementRequest])
final case class DisbursementRequest(toAddress: String, amount: String)

final case class DepositAddress(depositAddress: String)

final case class Disbursement(depositAddress: String, toAddress: String, amount: String, delayCount: Int)

final case class Mixing(depositAddress: String, depositAmount: String, disbursements: Seq[Disbursement])

object MixerRegistry {
  var actorRef: Option[ActorRef[Command]] = None

  // actor protocol
  sealed trait Command

  final case class CreateMixing(mixingRequest: MixingRequest, replyTo: ActorRef[CreateMixingResponse]) extends Command

  final case class ConfirmDeposit(depositAddress: String, replyTo: ActorRef[ActionPerformed]) extends Command

  final case class FlushDepositToHouse(mixing: Mixing) extends Command

  final case class FlushDepositToHouseError() extends Command

  final case class MixedAtHouseAddress(mixing: Mixing) extends Command

  final case class Disburse() extends Command

  final case class DepositValueError() extends Command

  // responses
  final case class CreateMixingResponse(maybeDepositAddress: Option[DepositAddress], statusCode: Int)

  final case class ActionPerformed(description: String, statusCode: Int)

  def apply()(implicit system: ActorSystem[Nothing], executionContext: ExecutionContext): Behavior[Command] = registry(Map.empty, Map.empty, Seq.empty)

  def randomUUID():String = java.util.UUID.randomUUID.toString

  def validateMixingRequest(mixingRequest: MixingRequest): Boolean = {
    val deposit = BigDecimal(mixingRequest.depositAmount)
    val disbursementTotal = mixingRequest.disbursements.foldLeft(BigDecimal(0))((z, d) => z + BigDecimal(d.amount))
    deposit > 0 && deposit == disbursementTotal
  }

  def validateAmountInDepositAddress(depositAddress: String, preMix: Map[String, Mixing])(implicit system: ActorSystem[Nothing], executionContext: ExecutionContext): Future[Boolean] = {
    val mixing = preMix.get(depositAddress)
    mixing match {
      case Some(m) =>
        val jobcoinClient: JobcoinClient = new JobcoinClient
        jobcoinClient.getAddressInfo(depositAddress).transformWith {
          case Success(addressInfo) =>
            Future.successful(BigDecimal(addressInfo.balance) == BigDecimal(m.depositAmount))
          case Failure(_) =>
            Future.successful(false)
        }
      case _ => Future.successful(false)
    }
  }

  def transact(fromAddress: String, toAddress: String, amount: String)(implicit system: ActorSystem[Nothing], executionContext: ExecutionContext): Future[Unit] = {
    val jobcoinClient: JobcoinClient = new JobcoinClient
    jobcoinClient.postTransactions(fromAddress, toAddress, amount)
  }

  private val logOf2 = scala.math.log(2)
  private def log2(x: Double): Double = scala.math.log(x)/logOf2

  def entropy(mixingMap: Map[String, BigDecimal]) : Double = {
    val depositTotal = mixingMap.values.foldLeft(BigDecimal(0))((z, d) => z + d)
    if (depositTotal <= 0) {
      0
    } else {
      // entropy = - sum_for_all_i(P[i] * log(P[i]))
      -mixingMap.values.foldLeft(BigDecimal(0))((z, d) => {
        val prob_d = d / depositTotal
        z + prob_d * log2(prob_d.doubleValue) // casting to double here is fine. prob_d -> 0 or -> 1 contributes 0 to total entropy
      }).doubleValue
    }
  }

  // We should only disburse when entropy(houseMap) > entropyThreshold
  // entropy(houseMap) measures the how well mixed the house address is.
  // Low entropy makes it easy for attacker to figure out which disbursement belongs to which deposit.
  // For example, if the house address consists of:
  // 1 deposit, entropy = 0 (all amount in house belongs to the same deposit)
  // 2 deposits, equal weighting [0.5, 0.5], entropy = 1
  // 3 deposits, equal weighting [0.33, 0.33, 0.33], entropy = 1.58
  // Having a big dominant deposit decreases entropy:
  // 2 deposits, weighting = [0.1, 0.9] (or [0.9, 0.1], order does not matter), entropy = 0.47
  // 3 deposits, weighting = [0.8, 0.1, 0.1], entropy = 0.92
  val entropyThreshold = 1.0 // In the real world it should be much higher

  // we delay disbursement randomly so our disbursement is not deterministic.
  val maxDelayCount = 3 // we can delay a disbursement 3 times max
  val probDelay = 0.5 // probability of delay

  // TODO: abstract mixingMap to become a MixingRepository trait,
  //  and implement a InMemoryMixingRepository and a DBMixingRepository
  private def registry(preMix: Map[String, Mixing], houseMap: Map[String, BigDecimal], disbursements: Seq[Disbursement])(implicit system: ActorSystem[Nothing], executionContext: ExecutionContext): Behavior[Command] = {
    val houseAddress = system.settings.config.getString("pokebowl.jobcoin.houseAddress")
    implicit val timeout: Timeout = Timeout.create(system.settings.config.getDuration("pokebowl.server.timeout"))
    Behaviors.receiveMessage {
      case CreateMixing(mixingRequest, replyTo) =>
        if (validateMixingRequest(mixingRequest)) {
          val depositAddress = randomUUID()
          val mixing = Mixing(depositAddress,
            mixingRequest.depositAmount,
            mixingRequest.disbursements.map{d => Disbursement(depositAddress, d.toAddress, d.amount, 0)})
          replyTo ! CreateMixingResponse(Some(DepositAddress(depositAddress)), Created.intValue)
          registry(preMix + (mixing.depositAddress -> mixing), houseMap, disbursements)
        } else {
          replyTo ! CreateMixingResponse(None, BadRequest.intValue)
          Behaviors.same
        }
      case ConfirmDeposit(depositAddress, replyTo) =>
        val validation = validateAmountInDepositAddress(depositAddress, preMix)
        validation.onComplete {
          case Success(true) =>
            replyTo ! ActionPerformed("Deposit address verified. Start mixing...", Accepted.intValue)
            actorRef.get ! FlushDepositToHouse(preMix(depositAddress))
          case _ =>
            replyTo ! ActionPerformed("Failed to verify the deposit address", BadRequest.intValue)
            actorRef.get ! DepositValueError()
        }
        Behaviors.same
      case FlushDepositToHouse(mixing) =>
        val transactionStatus = transact(mixing.depositAddress, houseAddress, mixing.depositAmount)
        transactionStatus.onComplete {
          case Success(_) =>
            actorRef.get ! MixedAtHouseAddress(mixing)
          case _ =>
            actorRef.get ! FlushDepositToHouseError()
        }
        Behaviors.same
      case MixedAtHouseAddress(mixing) =>
        registry(preMix.-(mixing.depositAddress),
          houseMap + (mixing.depositAddress -> BigDecimal(mixing.depositAmount)),
          disbursements ++ mixing.disbursements)
      case Disburse() =>
        batchDisburse(houseAddress, preMix, houseMap, disbursements)
      case _ =>
        Behaviors.same
      // TODO: handle the error cases by retry or at least log them
    }
  }

  def batchDisburse(houseAddress: String, preMix: Map[String, Mixing], houseMap: Map[String, BigDecimal], disbursements: Seq[Disbursement])(implicit system: ActorSystem[Nothing], executionContext: ExecutionContext): Behavior[Command] = {
    var newHouseMap = houseMap
    var disbursedFrom = Set[String]()
    val newDisbursements: Seq[Disbursement] = disbursements.flatMap(d => {
      if (entropy(newHouseMap) > entropyThreshold && !disbursedFrom.contains(d.depositAddress)) {
        if (scala.util.Random.nextFloat() < probDelay && d.delayCount < maxDelayCount) {
          Some(Disbursement(d.depositAddress, d.toAddress, d.amount, d.delayCount + 1))
        } else {
          transact(houseAddress, d.toAddress, d.amount)
          val newAmount:BigDecimal =  newHouseMap.getOrElse(d.depositAddress, BigDecimal(0)) - BigDecimal(d.amount)
          disbursedFrom += d.depositAddress
          if (newAmount > 0) {
            newHouseMap += (d.depositAddress -> newAmount)
          } else {
            newHouseMap -= d.depositAddress
          }
          None
        }
      } else {
        Some(d)
      }
    })
    registry(preMix, newHouseMap, newDisbursements)
  }
}
