package com.geoffreymak

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.geoffreymak.MixerRegistry._
import JsonFormats._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes._

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout

import scala.concurrent.Future

class MixerRoutes(mixerRegistry: ActorRef[MixerRegistry.Command])(implicit val system: ActorSystem[_]) {
  // If ask takes more time than this to complete the request is failed
  private implicit val timeout = Timeout.create(system.settings.config.getDuration("pokebowl.server.timeout"))

  def createMixing(mixingRequest: MixingRequest): Future[CreateMixingResponse] =
    mixerRegistry.ask(CreateMixing(mixingRequest, _))
  def performMixing(depositAddress: String): Future[ActionPerformed] =
    mixerRegistry.ask(ConfirmDeposit(depositAddress, _))

  val routes: Route =
    pathPrefix("mixer") {
      concat(
        pathEnd {
          post {
            entity(as[MixingRequest]) { mixingRequest =>
              onSuccess(createMixing(mixingRequest)) { createMixingResponse =>
                createMixingResponse.maybeDepositAddress match {
                  case Some(addr) => complete((Created, addr))
                  case _ => complete(BadRequest)
                }
              }
            }
          }
        },
        pathPrefix("confirmDeposit") {
          path(Segment) { depositAddress =>
            post {
              onSuccess(performMixing(depositAddress)) { performed =>
                complete((Accepted, performed))
              }
            }
          }
        }
      )
    }
}
