package com.geoffreymak

import com.geoffreymak.MixerRegistry.ActionPerformed

//#json-formats
import spray.json.DefaultJsonProtocol

object JsonFormats  {
  // import the default encoders for primitive types (Int, String, Lists etc)
  import DefaultJsonProtocol._

  implicit val depositAddress = jsonFormat1(DepositAddress)
  implicit val disbursementRequestJsonFormat = jsonFormat2(DisbursementRequest)
  implicit val disbursementJsonFormat = jsonFormat4(Disbursement)
  implicit val mixingRequestJsonFormat = jsonFormat2(MixingRequest)
  implicit val mixingJsonFormat = jsonFormat3(Mixing)
  implicit val mixerActionPerformedJsonFormat = jsonFormat2(MixerRegistry.ActionPerformed)
}
//#json-formats
