package com.geoffreymak

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._

import scala.concurrent.{ExecutionContext, Future}
import akka.http.scaladsl.unmarshalling.Unmarshal
import spray.json.RootJsonFormat
import spray.json.DefaultJsonProtocol._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

import java.io.IOException

class JobcoinClient (implicit system: ActorSystem[_], ec: ExecutionContext) {
  val config = system.settings.config
  private val apiAddressesUrl = config.getString("pokebowl.jobcoin.apiAddressesUrl")

  def getAddressInfo(addressId: String): Future[JobcoinClient.AddressInfo] = {
    val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = apiAddressesUrl+s"/$addressId"))

    responseFuture.flatMap { response => parseAddressInfo(response) }
  }

  def parseAddressInfo(response: HttpResponse) : Future[JobcoinClient.AddressInfo] = {
    response.status match {
      case OK if (response.entity.contentType == ContentTypes.`application/json`) =>
        Unmarshal(response.entity).to[JobcoinClient.AddressInfo]
      case _ => Unmarshal(response.entity).to[String].flatMap { entity =>
        val error = s"Request failed with status code ${response.status} and entity $entity"
        Future.failed(new IOException(error))
      }
    }
  }
}

object JobcoinClient {
  case class Transaction(timestamp: String, toAddress: String, amount: String, fromAddress: Option[String])
  object Transaction {
    implicit val jsonReads: RootJsonFormat[Transaction] = jsonFormat4(Transaction.apply)
  }
  case class AddressInfo(balance: String, transactions: Array[Transaction])
  object AddressInfo {
    implicit val jsonReads: RootJsonFormat[AddressInfo] = jsonFormat2(AddressInfo.apply)
  }
}