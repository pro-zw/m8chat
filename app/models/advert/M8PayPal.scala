package models.advert

import java.util

import anorm._
import com.paypal.api.payments._
import com.paypal.base.rest.{APIContext, OAuthTokenCredential}
import com.paypal.base.rest.PayPalResource
import play.api.Play.current
import play.api.db.DB
import play.api.{Play, Logger}

import scala.collection.JavaConversions._
import scala.util.Try
import utils._
import support.AdminEmailSender

object M8PayPal {
  val sdkMode = Play.application.configuration.getString("paypal.mode").getOrElse("sandbox")
  val clientID = Play.application.configuration.getString("paypal.clientID").getOrElse("AbW-0RCpThQd5Cbw27-phPdLfs60aLSRdRZjlyFcT-VZYNRezjVXJEv1UWT9")
  val clientSecret = Play.application.configuration.getString("paypal.clientSecret").getOrElse("EPYJHRD3gPzRRFumOqzaHIl9XayEwtmAhVX4saPH113cXYNQ2IctW2TeDFRo")

  PayPalResource.initConfig(Play.application.resourceAsStream("sdk_config.properties").get)
  val sdkConfig = new util.HashMap[String, String]()
  sdkConfig.put("mode", sdkMode)

  def createPayment(billId: Long,
                    advertiserId: Long):Try[Payment] = {
    Try(DB.withTransaction { implicit c =>
        SQL(s"select * from advert.get_paypal_bill($billId, $advertiserId)")
          .apply().headOption match {
          case Some(row) if row[Option[BigDecimal]]("_amount").isDefined && row[BigDecimal]("_amount") > 0 =>
            createRegularPayment(billId, advertiserId, row[BigDecimal]("_amount"))
          case _ => throw new Exception("Bill information changed and you are not charged. Please refresh the page to get the latest bill")
        }
    })
  }

  def executePayment(billId: Long,
                     advertiserId: Long,
                     paymentId: String,
                     payerId: String):Try[Int] = {
    Try(
      DB.withTransaction { implicit c =>
        SQL(s"select * from advert.begin_pay_paypal_bill($billId, $advertiserId)")
          .apply().map(row => row[Int]("_update_count")).head
      } match {
        case 1 =>
          try {
            val accessToken = new OAuthTokenCredential(clientID, clientSecret, sdkConfig).getAccessToken
            val apiContext = new APIContext(accessToken)
            apiContext.setConfigurationMap(sdkConfig)

            val paymentExecution = new PaymentExecution()
            paymentExecution.setPayerId(payerId)

            // Exception will be thrown here if the execution fails
            val payment = new Payment()
            payment.setId(paymentId)
            payment.execute(apiContext, paymentExecution)

            PayPalLogger.info(s"Executed payment with bill id: $billId, advertiser id: $advertiserId, paymentId: $paymentId, payerId: $payerId")

            val updateCount = DB.withTransaction { implicit c =>
              SQL(s"select * from advert.end_pay_bill($billId, $advertiserId, {paymentId})")
                .on('paymentId -> paymentId)
                .apply().map(row => row[Int]("_update_count")).head
            }

            if (updateCount <= 0) {
              val message = s"Executed PayPal payment with bill id: $billId, advertiser id: $advertiserId, paymentId: $paymentId, payerId: $payerId, but failed to extend the advertiser's expiry date. Manual intervention may be needed."
              PayPalLogger.error(message)
              AdminEmailSender !("[m8chat Server] Charged but fail to update database", message)
            }

            updateCount
          } catch {
            case ex: Exception =>
              DB.withTransaction { implicit c =>
                SQL(s"select * from advert.cancel_pay_paypal_bill($billId, $advertiserId)")
              }

              PayPalLogger.error(s"PayPal failed to charge an advertiser. His/her id: $advertiserId, and already canceled the corresponding bill")
              throw new Exception(s"Payment failed and you are not charged. Old bill is canceled and a new bill is generated. Cause: ${ex.getCause}")
          }
        case _ =>
          throw new Exception("Bill information changed and you are not charged. Please refresh the page to get the latest bill")
      }
    )
  }

  private def createRegularPayment(billId: Long,
                                   advertiserId: Long,
                                   aAmount: BigDecimal):Payment = {
    val accessToken = new OAuthTokenCredential(clientID, clientSecret, sdkConfig).getAccessToken
    val apiContext = new APIContext(accessToken)
    apiContext.setConfigurationMap(sdkConfig)

    // Detail
    val details = new Details()
    details.setSubtotal(f"${aAmount / 1.1}%.2f")
    details.setTax(f"${aAmount - aAmount / 1.1}%.2f")

    // Amount
    val amount = new Amount()
    amount.setCurrency("AUD")
    amount.setTotal(f"$aAmount%.2f")
    amount.setDetails(details)

    // Transaction
    val transaction = new Transaction()
    transaction.setAmount(amount)
    transaction.setDescription(f"m8chat advert service fee: $$$aAmount%.2f (including GST: $$${aAmount - aAmount / 1.1}%.2f)")

    val transactions = Vector(transaction)

    // Payer
    val payer = new Payer()
    payer.setPaymentMethod("paypal")

    // Redirect url
    val redirectUrls = new RedirectUrls()
    redirectUrls.setCancelUrl(RootUrl + controllers.advert.routes.BillController.bill().url)
    redirectUrls.setReturnUrl(RootUrl + controllers.advert.routes.M8PayPalController.executePaymentPage(billId, advertiserId).url)

    // Payment
    val payment = new Payment()
    payment.setIntent("sale")
    payment.setTransactions(transactions)
    payment.setRedirectUrls(redirectUrls)
    payment.setPayer(payer)

    val paymentCreated = payment.create(apiContext)
    PayPalLogger.info(s"Created payment with bill id: $billId, advertiser id: $advertiserId, amount: ${f"$aAmount%.2f"}, status: ${paymentCreated.getState}")
    paymentCreated
  }
}
