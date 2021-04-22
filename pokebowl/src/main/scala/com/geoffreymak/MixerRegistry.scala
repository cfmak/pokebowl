package com.geoffreymak

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}
import akka.util.Timeout

final case class MixingRequest(depositAmount: String, disbursements: Seq[Disbursement])

final case class DepositAddress(depositAddress: String)

final case class Disbursement(toAddress: String, amount: String)

final case class Mixing(depositAddress: String, depositAmount: String, disbursements: Seq[Disbursement])

object MixerRegistry {
  var actorRef: Option[ActorRef[Command]] = None

  // actor protocol
  sealed trait Command

  final case class CreateMixing(mixingRequest: MixingRequest, replyTo: ActorRef[CreateMixingResponse]) extends Command

  final case class ConfirmDeposit(depositAddress: String, replyTo: ActorRef[ActionPerformed]) extends Command

  final case class FlushDepositToHouse(mixing: Mixing) extends Command

  final case class FlushDepositToHouseError() extends Command

  final case class Disburse(mixing: Mixing) extends Command

  final case class DepositValueError() extends Command

  // responses
  final case class CreateMixingResponse(maybeDepositAddress: Option[DepositAddress])

  final case class ActionPerformed(description: String)

  def apply()(implicit actorContext: ActorContext[Command]): Behavior[Command] = registry(Map.empty)

  def randomUUID():String = java.util.UUID.randomUUID.toString

  def validateMixingRequest(mixingRequest: MixingRequest): Boolean = {
    val deposit = BigDecimal(mixingRequest.depositAmount)
    val disbursementTotal = mixingRequest.disbursements.foldLeft(BigDecimal(0))((z, d) => z + BigDecimal(d.amount))
    deposit > 0 && deposit == disbursementTotal
  }

  def validateAmountInDepositAddress(depositAddress: String, mixingMap: Map[String, Mixing])(implicit actorContext: ActorContext[Command]): Future[Boolean] = {
    implicit val system: ActorSystem[Nothing] = actorContext.system
    implicit val executionContext: ExecutionContextExecutor = actorContext.executionContext
    val mixing = mixingMap.get(depositAddress)
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

  def transact(fromAddress: String, toAddress: String, amount: String)(implicit actorContext: ActorContext[Command]): Future[Unit] = {
    implicit val system: ActorSystem[Nothing] = actorContext.system
    implicit val executionContext: ExecutionContextExecutor = actorContext.executionContext
    val jobcoinClient: JobcoinClient = new JobcoinClient
    jobcoinClient.postTransactions(fromAddress, toAddress, amount)
  }

  // TODO: abstract mixingMap to become a MixingRepository trait,
  //  and implement a InMemoryMixingRepository and a DBMixingRepository
  private def registry(mixingMap: Map[String, Mixing])(implicit context: ActorContext[Command]): Behavior[Command] = {
    val houseAddress = context.system.settings.config.getString("pokebowl.jobcoin.houseAddress")
    implicit val timeout: Timeout = Timeout.create(context.system.settings.config.getDuration("pokebowl.server.timeout"))
    implicit val ec: ExecutionContextExecutor = context.executionContext
    Behaviors.receiveMessage {
      case CreateMixing(mixingRequest, replyTo) =>
        if (validateMixingRequest(mixingRequest)) {
          val depositAddress = randomUUID()
          val mixing = Mixing(depositAddress, mixingRequest.depositAmount, mixingRequest.disbursements)
          replyTo ! CreateMixingResponse(Some(DepositAddress(depositAddress)))
          registry(mixingMap + (mixing.depositAddress -> mixing))
        } else {
          replyTo ! CreateMixingResponse(None)
          Behaviors.same
        }
      case ConfirmDeposit(depositAddress, replyTo) =>
        val validation = validateAmountInDepositAddress(depositAddress, mixingMap)
        validation.onComplete {
          case Success(true) =>
            replyTo ! ActionPerformed("Deposit address verified. Perform mixing")
            actorRef.get ! FlushDepositToHouse(mixingMap(depositAddress))
          case _ =>
            // TODO: this should return some 4xx
            replyTo ! ActionPerformed("Failed to confirm the deposit address")
            actorRef.get ! DepositValueError()
        }
        Behaviors.same
      case FlushDepositToHouse(mixing) =>
        val transactionStatus = transact(mixing.depositAddress, houseAddress, mixing.depositAmount)
        transactionStatus.onComplete {
          case Success(_) =>
            actorRef.get ! Disburse(mixing)
          case _ =>
            actorRef.get ! FlushDepositToHouseError()
        }
        Behaviors.same
      case Disburse(mixing) =>
        mixing.disbursements.foreach(disbursement => {
          transact(houseAddress, disbursement.toAddress, disbursement.amount)
        })
        registry(mixingMap.-(mixing.depositAddress))
      case _ =>
        Behaviors.same
      // TODO: handle the error cases by retry or at least log them
    }
  }
}
