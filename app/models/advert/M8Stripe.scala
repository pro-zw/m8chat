package models.advert

import play.api.Play
import play.api.Play.current
import play.api.libs.ws._
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import scala.language.postfixOps
import utils._

case class AdvertiserCreditCard(holder: String,
                                last4Digits: String,
                                expiryMonth: Int,
                                expiryYear: Int)

object M8Stripe {
  val apiKey = Play.application.configuration.getString("stripe.key.secret").getOrElse("sk_live_6I3Cn0GnkvQ0p93aNuW3KMNM")
  val endpoint = Play.application.configuration.getString("stripe.endpoint").getOrElse("https://api.stripe.com/v1")

  def getCustomer(customerId: String): Future[AdvertiserCreditCard] = {
    WS.url(endpoint + s"/customers/$customerId")
      .withHeaders("Content-Type" -> "application/x-www-form-urlencoded", "Accept" -> "application/json")
      .withRequestTimeout(60000)
      .withAuth(apiKey, "", WSAuthScheme.BASIC)
      .get().map { response =>
      if (response.status == 200) {
        val jsonCard = (response.json \ "sources" \ "data")(0)
        AdvertiserCreditCard((jsonCard \ "name").as[String],
          (jsonCard \ "last4").as[String], (jsonCard \ "exp_month").as[Int],
          (jsonCard \ "exp_year").as[Int])
      } else {
        StripeLogger.error(s"Error occurs when retrieving customer from Stripe: ${response.json.toString()}")
        throwStripeException(response.json)
      }
    }
  }

  def createCustomer(advertiserId: Long,
                     email: String,
                     cardToken: String): Future[String] = {
    WS.url(endpoint + s"/customers")
      .withHeaders("Content-Type" -> "application/x-www-form-urlencoded", "Accept" -> "application/json")
      .withRequestTimeout(60000)
      .withAuth(apiKey, "", WSAuthScheme.BASIC)
      .post(Map(
        "description" -> Seq(s"Advertiser with id - $advertiserId"),
        "card" -> Seq(cardToken),
        "email" -> Seq(email)
      )).map { response =>
        if (response.status == 200) {
          (response.json \ "id").as[String]
        } else {
          StripeLogger.error(s"Error occurs when creating customer with Stripe: ${response.json.toString()}")
          throwStripeException(response.json)
        }
      }
  }

  def chargeCustomer(customerId: String,
                     amount: Double): Future[(String, Boolean)] = {
    WS.url(endpoint + s"/charges")
      .withHeaders("Content-Type" -> "application/x-www-form-urlencoded", "Accept" -> "application/json")
      .withRequestTimeout(60000)
      .withAuth(apiKey, "", WSAuthScheme.BASIC)
      .post(Map(
        "amount" -> Seq(s"${(amount * 100).toInt}"),
        "currency" -> Seq("aud"),
        "customer" -> Seq(s"$customerId"),
        "description" -> Seq("m8chat advert service subscription"),
        "statement_descriptor" -> Seq("m8chat Advert Service")
      )).map { response =>
        if (response.status == 200) {
          ((response.json \ "id").as[String], (response.json \ "paid").as[Boolean])
        } else {
          StripeLogger.error(s"Error occurs when charging customer with Stripe: ${response.json.toString()}")
          throwStripeException(response.json)
        }
      }
  }

  private def throwStripeException(error: JsValue) = {
    val errorType = (error \ "error" \ "type").as[String]
    val defaultMessage = s"Error $errorType occurs. Please try again."
    val message = (error \ "error" \ "message").as[Option[String]].getOrElse(defaultMessage)
    if (errorType == "card_error") {
      throw new Exception(message)
    } else {
      throw new Exception(defaultMessage)
    }
  }
}
