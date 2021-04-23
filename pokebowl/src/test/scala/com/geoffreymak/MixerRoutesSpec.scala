package com.geoffreymak

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MixerRoutesSpec extends AnyWordSpec with Matchers with ScalaFutures with ScalatestRouteTest {

  lazy val testKit = ActorTestKit()
  implicit def typedSystem = testKit.system
  override def createActorSystem(): akka.actor.ActorSystem =
    testKit.system.classicSystem

  val mixerRegistry = testKit.spawn(MixerRegistry())
  lazy val routes = new MixerRoutes(mixerRegistry).routes

  import JsonFormats._
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

  "MixerRoutes" should {
    "return deposit address (POST /mixer)" in {
      val data =
        """
          |{
          |    "depositAmount":"0.3",
          |    "disbursements": [
          |        {"toAddress": "Alice", "amount": "0.1"},
          |        {"toAddress": "Bob", "amount": "0.2"}
          |    ]
          |}
          |""".stripMargin
      val request = HttpRequest(
        method = HttpMethods.POST,
        uri = "/mixer",
        entity = HttpEntity(ContentTypes.`application/json`, data.getBytes())
      )

      request ~> routes ~> check {
        status should ===(StatusCodes.Created)

        contentType should ===(ContentTypes.`application/json`)

        entityAs[DepositAddress]
      }
    }

    "return error at (POST /mixer/confirmDeposit)" in {
      val request = HttpRequest(
        method = HttpMethods.POST,
        uri = "/mixer/confirmDeposit/randomAddress"
      )

      request ~> routes ~> check {
        status should ===(StatusCodes.BadRequest)
      }
    }
  }
}
