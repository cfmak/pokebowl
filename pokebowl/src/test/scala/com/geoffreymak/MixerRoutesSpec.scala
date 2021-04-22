package com.geoffreymak

//#mixer-routes-spec
//#test-top
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

//#set-up
class MixerRoutesSpec extends AnyWordSpec with Matchers with ScalaFutures with ScalatestRouteTest {
  //#test-top

  lazy val testKit = ActorTestKit()
  implicit def typedSystem = testKit.system
  override def createActorSystem(): akka.actor.ActorSystem =
    testKit.system.classicSystem

  // Here we need to implement all the abstract members of UserRoutes.
  // We use the real UserRegistryActor to test it while we hit the Routes,
  // but we could "mock" it by implementing it in-place or by using a TestProbe
  // created with testKit.createTestProbe()
  val mixerRegistry = testKit.spawn(MixerRegistry())
  lazy val routes = new MixerRoutes(mixerRegistry).routes

  // use the json formats to marshal and unmarshall objects in the test
  import JsonFormats._
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  //#set-up

  //#actual-test
  "MixerRoutes" should {
    "return deposit address (POST /mixer)" in {
      // note that there's no need for the host part in the uri:
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

        // we expect the response to be json:
        contentType should ===(ContentTypes.`application/json`)

        // and no entries should be in the list:
        entityAs[DepositAddress]
      }
    }
    //#actual-test
  }
  //#actual-test

  //#set-up
}
//#set-up
//#mixer-routes-spec
