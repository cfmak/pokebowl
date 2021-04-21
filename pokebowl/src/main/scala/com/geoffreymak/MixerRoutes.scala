package com.geoffreymak

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.geoffreymak.MixerRegistry._
import JsonFormats._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import scala.concurrent.Future

class MixerRoutes(mixerRegistry: ActorRef[MixerRegistry.Command])(implicit val system: ActorSystem[_]) {
  // If ask takes more time than this to complete the request is failed
  private implicit val timeout = Timeout.create(system.settings.config.getDuration("pokebowl.server.timeout"))

  def createMixing(mixingRequest: MixingRequest): Future[DepositAddress] =
    mixerRegistry.ask(CreateMixing(mixingRequest, _))
  def confirmDeposit(depositAddress: String): Future[String] =
    Future.successful("OK")
//    mixerRegistry.ask(GetUser(name, _))

  val routes: Route =
    pathPrefix("mixer") {
      concat(
        pathEnd {
          concat(
            post {
              entity(as[MixingRequest]) { mixingRequest =>
                onSuccess(createMixing(mixingRequest)) { performed =>
                  complete((StatusCodes.Created, performed))
                }
              }
            })
        }
      )
    }
}