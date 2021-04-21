package com.geoffreymak

import com.geoffreymak.UserRegistry.ActionPerformed

//#json-formats
import spray.json.DefaultJsonProtocol

object JsonFormats  {
  // import the default encoders for primitive types (Int, String, Lists etc)
  import DefaultJsonProtocol._

  implicit val userJsonFormat = jsonFormat3(User)
  implicit val usersJsonFormat = jsonFormat1(Users)

  implicit val depositAddress = jsonFormat1(DepositAddress)
  implicit val disbursementJsonFormat = jsonFormat2(Disbursement)
  implicit val mixingRequestJsonFormat = jsonFormat2(MixingRequest)
  implicit val mixingJsonFormat = jsonFormat3(Mixing)
  implicit val actionPerformedJsonFormat = jsonFormat1(ActionPerformed)
}
//#json-formats
