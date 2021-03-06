package com.geoffreymak

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
  private implicit val timeout: Timeout = Timeout.create(system.settings.config.getDuration("pokebowl.server.timeout"))

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
                createMixingResponse.statusCode match {
                  case Created.intValue => complete((createMixingResponse.statusCode, createMixingResponse.maybeDepositAddress.get))
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
                complete((performed.statusCode, performed))
              }
            }
          }
        }
      )
    }
}
