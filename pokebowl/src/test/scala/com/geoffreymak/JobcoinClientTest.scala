package com.geoffreymak

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.geoffreymak.JobcoinClient.{AddressInfo, Transaction}
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.DispatcherSelector
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper

class JobcoinClientTest extends AsyncWordSpec with Matchers with ScalaFutures with ScalatestRouteTest {
   implicit val actorSystem = ActorSystem[Nothing](Behaviors.empty, "pokebowl-test", TestActorSystemConfig.actorSystemConfig)
   override implicit val executionContext = actorSystem.dispatchers.lookup(DispatcherSelector.fromConfig("akka.actor.default-dispatcher"))
   val client = new JobcoinClient

   "getAddressInfo" should {
      "return the address info of new address" in {
         client.getAddressInfo("testNewAddress").map(x => {
            x mustBe a[AddressInfo]
            x.balance should ===("0")
            x.transactions should ===(Seq())
         })
      }
      "return the address info of existing address" in {
         client.getAddressInfo("testGetAddress").map(x => {
            x mustBe a[AddressInfo]
            x.balance should ===("49")
            x.transactions should ===(
               Seq(
               Transaction("2021-04-21T01:53:06.633Z", "testGetAddress", "50", None),
               Transaction("2021-04-21T02:00:14.563Z", "testGetAddress2", "1" ,Some("testGetAddress"))
               )
            )
         })
      }
   }

   "listTransactions" should {
      "return a list of all transactions" in {
         client.listTransactions().map(x => {
            x mustBe a[Array[Transaction]]
         })
      }
   }

//   "postTransaction" should {
//      "return success" in {
//         client.postTransactions("testPostTransaction1", "testPostTransaction2", "0.1").map(x => {
//            x should ===(())
//         })
//      }
//   }
}
