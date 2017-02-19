package controllers.advert

import com.paypal.api.payments.Payment
import models.advert.M8PayPal
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}
import utils._
import utils.advert.AdvertiserAuthAction
import scala.collection.JavaConverters._

import scala.util.{Failure, Success}

object M8PayPalController extends Controller {
  def createPayment(billId: Long) = AdvertiserAuthAction { request =>
    M8PayPal.createPayment(billId, request.advertiserAuth.advertiserId) match {
      case Success(payment: Payment) if payment.getLinks.asScala.exists(link => link.getRel.equalsIgnoreCase("approval_url")) =>
        payment.getLinks.asScala.filter(link => link.getRel.equalsIgnoreCase("approval_url"))
          .map(link => Ok(Json.obj("approvalUrl" -> link.getHref))).head
      case Failure(ex) => JsonBadRequest(ex)
    }
  }

  def executePaymentPage(billId: Long,
                         advertiserId: Long) = Action {
    Ok(views.html.executePayment())
  }

  def executePayment(billId: Long,
                     advertiserId: Long,
                     paymentId: String,
                     payerId: String) = Action {
    M8PayPal.executePayment(billId, advertiserId, paymentId, payerId) match {
      case Success(result) if result > 0 =>
        NoContent
      case Success(result) if result <= 0 =>
        JsonBadRequest(s"Payment executed but failed to update your expiry date. Please contact us if you have been charged")
      case Failure(ex) => JsonBadRequest(ex)
    }
  }
}
