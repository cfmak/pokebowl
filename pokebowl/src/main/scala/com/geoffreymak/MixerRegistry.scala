package com.geoffreymak

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

final case class MixingRequest(depositAmount: String, disbursements: Seq[Disbursement])
final case class DepositAddress(depositAddress: String)
final case class Disbursement(toAddress: String, amount: String)
final case class Mixing(depositAddress: String, depositAmount: String, disbursements: Seq[Disbursement])

object MixerRegistry {
  // actor protocol
  sealed trait Command
  final case class CreateMixing(mixingRequest: MixingRequest, replyTo: ActorRef[DepositAddress]) extends Command
//  final case class CreateUser(user: User, replyTo: ActorRef[ActionPerformed]) extends Command
//  final case class GetUser(name: String, replyTo: ActorRef[GetUserResponse]) extends Command
//  final case class DeleteUser(name: String, replyTo: ActorRef[ActionPerformed]) extends Command

  final case class GetUserResponse(maybeUser: Option[User])
  final case class ActionPerformed(description: String)

  def apply(): Behavior[Command] = registry(Map.empty)

  def randomUUID() = java.util.UUID.randomUUID.toString

  // TODO: abstract mixingMap to become a MixingRepository trait,
  //  and implement a InMemoryMixingRepository and a DBMixingRepository
  private def registry(mixingMap: Map[String, Mixing]): Behavior[Command] =
    Behaviors.receiveMessage {
      case CreateMixing(mixingRequest, replyTo) =>
        val depositAddress = randomUUID()
        val mixing = Mixing(depositAddress, mixingRequest.depositAmount, mixingRequest.disbursements)
        replyTo ! DepositAddress(depositAddress)
        registry(mixingMap + (mixing.depositAddress -> mixing))
//      case GetUsers(replyTo) =>
//        replyTo ! Users(users.toSeq)
//        Behaviors.same
//      case CreateUser(user, replyTo) =>
//        replyTo ! ActionPerformed(s"User ${user.name} created.")
//        registry(users + user)
//      case GetUser(name, replyTo) =>
//        replyTo ! GetUserResponse(users.find(_.name == name))
//        Behaviors.same
//      case DeleteUser(name, replyTo) =>
//        replyTo ! ActionPerformed(s"User $name deleted.")
//        registry(users.filterNot(_.name == name))
    }
}
//#user-registry-actor
