package com.geoffreymak

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

final case class MixingRequest(depositAmount: String, disbursements: Seq[Disbursement])
final case class DepositAddress(depositAddress: String)
final case class Disbursement(toAddress: String, amount: String)
final case class Mixing(depositAddress: String, depositAmount: String, disbursements: Seq[Disbursement])

object MixerRegistry {
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

  def apply()(implicit system: ActorSystem[_], actorContext: ActorContext[Command], ec: ExecutionContext): Behavior[Command] = registry(Map.empty)

  def randomUUID() = java.util.UUID.randomUUID.toString

  def validateMixingRequest(mixingRequest: MixingRequest): Boolean = {
    val deposit = BigDecimal(mixingRequest.depositAmount)
    val disbursementTotal = mixingRequest.disbursements.foldLeft(BigDecimal(0))((z, d) => z + BigDecimal(d.amount))
    deposit > 0 && deposit == disbursementTotal
  }

  def validateAmountInDepositAddress(depositAddress: String, mixingMap: Map[String, Mixing])(implicit system: ActorSystem[_], ec: ExecutionContext): Future[Boolean] = {
    val mixing = mixingMap.get(depositAddress)
    mixing match {
      case Some(m) => {
        val jobcoinClient:JobcoinClient = new JobcoinClient
        jobcoinClient.getAddressInfo(depositAddress).transformWith {
          case Success(addressInfo) => {
            Future.successful(BigDecimal(addressInfo.balance) == BigDecimal(m.depositAmount))
          }
          case Failure(e) => Future.successful(false)
        }
      }
      case _ => Future.successful(false)
    }
  }

  def transact(fromAddress: String, toAddress: String, amount: String)(implicit system: ActorSystem[_], ec: ExecutionContext): Future[Unit] = {
    val jobcoinClient:JobcoinClient = new JobcoinClient
    jobcoinClient.postTransactions(fromAddress, toAddress, amount)
  }

  // TODO: abstract mixingMap to become a MixingRepository trait,
  //  and implement a InMemoryMixingRepository and a DBMixingRepository
  private def registry(mixingMap: Map[String, Mixing])(implicit system: ActorSystem[_], context: ActorContext[MixerRegistry.Command], ec: ExecutionContext): Behavior[Command] = {
    val houseAddress = system.settings.config.getString("pokebowl.jobcoin.houseAddress")
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
      case ConfirmDeposit(depositAddress, replyTo) => {
        val validation = validateAmountInDepositAddress(depositAddress, mixingMap)
        context.pipeToSelf(validation) {
          case Success(true) => {
            replyTo ! ActionPerformed("Deposit address verified. Perform mixing")
            FlushDepositToHouse(mixingMap(depositAddress))
          }
          case _ => {
            // TODO: this should return some 4xx
            replyTo ! ActionPerformed("Failed to confirm the deposit address")
            DepositValueError()
          }
        }
        Behaviors.same
      }
      case FlushDepositToHouse(mixing) => {
        val transactionStatus = transact(mixing.depositAddress, houseAddress, mixing.depositAmount)
        context.pipeToSelf(transactionStatus) {
          case Success(_) => Disburse(mixing)
          case _ => FlushDepositToHouseError()
        }
        Behaviors.same
      }
      case Disburse(mixing) => {
        mixing.disbursements.foreach(disbursement => {
          transact(houseAddress, disbursement.toAddress, disbursement.amount)
        })
        Behaviors.same
      }
      case _ => Behaviors.same
      // TODO: handle the error cases by retry or at least log them
    }
  }
}
