package scala.controllers.social

import play.api.Logger
import play.api.libs.json._
import play.api.libs.ws.{WS, WSRequestHolder}
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

class ZeroPushSpec extends UnitSpec {
  val zeroPushNotify = "https://api.zeropush.com/notify"
  val zeroPushIosToken = "iosprod_YrjMTUYhBsqxuNwnAmy4"
  val zeroPushAndroidToken = "gcmprod_FMapz7gFqbSfk1MoTVXJ"
  val requestHolder: WSRequestHolder = WS.url(zeroPushNotify)
    .withHeaders("Content-Type" -> "application/json",
      "Accept" -> "application/json")
    .withRequestTimeout(60000)

  "ZeroPush service" must {
    "push notification" in {
      val appleJsonPayload = Json.obj(
        "auth_token" -> zeroPushIosToken,
        "device_tokens" -> Array[String]("4556d3ff3ff1c7eb353165012c5a20d5c8185fff639eddafc27a28b4ab2e106d", "fc5248cadcf70eadf33288b16fe862307fbc578b287783ca03d5f28148c5869a", "44033b0790e967ddc5ea6aeca2fb7b157f38ebdfd99d1f3ff367a3bc07ec8768"),
        "alert" -> "I am testing. Don't open the notification.",
        "info" -> Json.obj("chatId" -> 0),
        "sound" -> "default"
      )

      /*
      Await.result(requestHolder.post(appleJsonPayload).map {
        response =>
          Logger.debug(response.toString)
          (response.json \ "sent_count").asOpt[Int].map(sendCount =>
            Logger.debug(s"Apple notifications sent: $sendCount"))
      }, 80 seconds)
      */
    }
  }
}