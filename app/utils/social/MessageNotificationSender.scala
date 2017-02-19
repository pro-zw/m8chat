package utils.social

import akka.actor.Actor
import models.social.M8UserMessageNotification
import play.api.Play
import utils._
import anorm._
import play.api.Play.current
import play.api.db.DB
import play.api.libs.ws._
import play.api.libs.json._
import scala.util.{Failure, Success, Try}

class MessageNotificationSender extends Actor {
  implicit val executionContext = context.dispatcher

  val zeroPushNotify = Play.application.configuration.getString("zeropush.endpoint.notify").getOrElse("https://api.zeropush.com/notify")
  val zeroPushIosToken = Play.application.configuration.getString("zeropush.ios.token").getOrElse("iosprod_YrjMTUYhBsqxuNwnAmy4")
  val zeroPushAndroidToken = Play.application.configuration.getString("zeropush.android.token").getOrElse("gcmprod_FMapz7gFqbSfk1MoTVXJ")
  val requestHolder: WSRequestHolder = WS.url(zeroPushNotify)
    .withHeaders("Content-Type" -> "application/json",
      "Accept" -> "application/json")
    .withRequestTimeout(120000)

  override def receive = {
    case messageInfo: M8UserMessageNotification =>
      AccessLogger.debug("Sending m8 user new message notification...")

      Try(DB.withConnection { implicit c =>
        SQL(s"select * from social.get_message_push_info(${messageInfo.messageId})")
          .apply().headOption
      }) match {
        case Success(Some(row)) =>
          val chatId = row[Long]("_chat_id")
          val alert = s"${row[String]("_sender_first_name")}: ${row[String]("_message")}"
          val appleApnTokens = row[Array[String]]("_apple_apn_tokens").map(_.split(",")).flatten.distinct.filter(_.length > 0)
          val androidApnTokens = row[Array[String]]("_android_apn_tokens").map(_.split(",")).flatten.distinct.filter(_.length > 0)

          if (appleApnTokens.length > 0) {
            val appleJsonPayload = Json.obj(
              "auth_token" -> zeroPushIosToken,
              "device_tokens" -> appleApnTokens,
              "alert" -> alert,
              "info" -> Json.obj("chatId" -> chatId),
              "sound" -> "default"
            )

            requestHolder.post(appleJsonPayload).map {
              response =>
                (response.json \ "sent_count").asOpt[Int].map(sendCount =>
                  AccessLogger.debug(s"Message id ${messageInfo.messageId} apple notifications sent: $sendCount"))
            }
          }

          if (androidApnTokens.length > 0) {
            val androidJsonPayload = Json.obj(
              "auth_token" -> zeroPushAndroidToken,
              "device_tokens" -> androidApnTokens,
              "data" -> Json.obj("alert" -> alert, "chatId" -> chatId)
            )

            requestHolder.post(androidJsonPayload).map {
              response =>
                (response.json \ "sent_count").asOpt[Int].map(sendCount =>
                  AccessLogger.debug(s"Message id ${messageInfo.messageId} android notifications sent: $sendCount"))
            }
          }
        case Success(None) => None
        case Failure(ex) => AccessLogger.debug(s"Sending m8 user new message notification fails: ${ex.getMessage}")
      }
  }
}
