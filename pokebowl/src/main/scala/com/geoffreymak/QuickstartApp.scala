package com.geoffreymak

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route

import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success

//#main-class
object QuickstartApp {
  //#start-http-server
  private def startHttpServer(routes: Route)(implicit system: ActorSystem[_]): Unit = {
    // Akka HTTP still needs a classic ActorSystem to start
    import system.executionContext
    val config = system.settings.config
    val hostname = config.getString("pokebowl.server.hostname")
    val port = config.getInt("pokebowl.server.port")
    val futureBinding = Http().newServerAt(hostname, port).bind(routes)
      .map(_.addToCoordinatedShutdown(hardTerminationDeadline = 10.seconds))
    futureBinding.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info("Server online at http://{}:{}/", address.getHostString, address.getPort)
      case Failure(ex) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }
  }

  //#start-http-server
  def main(args: Array[String]): Unit = {
    //#server-bootstrapping
    val rootBehavior = Behaviors.setup[Nothing] { context =>
      val userRegistryActor = context.spawn(UserRegistry(), "UserRegistryActor")
      context.watch(userRegistryActor)

      val routes = new UserRoutes(userRegistryActor)(context.system)
      startHttpServer(routes.userRoutes)(context.system)

      Behaviors.empty
    }

    implicit val actorSystem = ActorSystem[Nothing](rootBehavior, "PokebowlAkkaHttpServer")
    implicit val executionContext = actorSystem.executionContext
    val client = new JobcoinClient
    client.getAddressInfo("Alice").map(addressInfo => {
      println(addressInfo.balance)
      addressInfo.transactions.foreach( tx => {
        println(s"${tx.timestamp}, ${tx.amount}, ${tx.fromAddress}, ${tx.toAddress}")
      })
    })

    client.listTransactions().map(transactions => {
      transactions.foreach( tx => {
        println(s"${tx.timestamp}, ${tx.amount}, ${tx.fromAddress}, ${tx.toAddress}")
      })
    })
    //#server-bootstrapping
  }
}
//#main-class
