package utils.advert

import akka.actor.{Props, ReceiveTimeout, Actor}
import anorm._
import models.advert.{AdvertiserStripeChargeFaild, M8Stripe}
import play.api.Play.current
import play.api.db.DB
import utils._
import support.AdminEmailSender

import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import scala.language.postfixOps

class StripeChargeActor extends Actor {
  import context.dispatcher
  val failedEmailSender = context.actorOf(Props[StripeChargeFailedEmailSender], name = "advert.StripeChargeFailedEmailSender")

  private case class StripeBill(billId: Long,
                                advertiserId: Long,
                                name: String,
                                email: String,
                                customerId: String,
                                amount: Double)

  override def receive = {
    case "Start" =>
      context.setReceiveTimeout(2 minutes)
    case ReceiveTimeout =>
      val bills = DB.withTransaction { implicit c =>
        SQL("select * from advert.begin_pay_subscription_bills()").apply().map { row =>
          StripeBill(row[Long]("_bill_id"),
            row[Long]("_advertiser_id"), row[String]("_name"),
            row[String]("_email"), row[String]("_stripe_customer_id"),
            amount = row[Double]("_amount")
          )
        }.toList
      }

      if (bills.length > 0) {
        context.setReceiveTimeout(2 minutes)
      } else {
        context.setReceiveTimeout(20 minutes)
      }

      bills.foreach { bill =>
        StripeLogger.debug(s"Begin to charge subscription advertiser (id ${bill.advertiserId})'s credit card with Stripe")

        M8Stripe.chargeCustomer(bill.customerId, bill.amount).map {
          case (_, false) =>
            DB.withTransaction { implicit c =>
              SQL(s"select * from advert.cancel_pay_subscription_bill(${bill.billId}, ${bill.advertiserId})")
            }

            StripeLogger.error(s"Stripe failed to charge an advertiser's credit card. His/her id: ${bill.advertiserId}, and already switch his/her payment method to manual and canceled the corresponding bill")
            failedEmailSender ! AdvertiserStripeChargeFaild(bill.name, bill.email, None)
          case (paymentId: String, true) =>
            StripeLogger.debug(s"Charged subscription advertiser (id ${bill.advertiserId})'s credit card with Stripe")

            DB.withTransaction { implicit c =>
              val updateCount = SQL(s"select * from advert.end_pay_bill(${bill.billId}, ${bill.advertiserId}, {paymentId})")
                .on('paymentId -> paymentId)
                .apply().map(row => row[Int]("_update_count")).head

              if (updateCount <= 0) {
                val message = s"Executed Stripe payment with bill id: ${bill.billId}, advertiser id: ${bill.advertiserId}, paymentId: $paymentId, but failed to extend the advertiser's expiry date. Manual intervention may be needed."
                StripeLogger.error(message)
                AdminEmailSender ! ("[m8chat Server] Charged but fail to update database", message)
              }
            }
        } recover {
          case ex =>
            DB.withTransaction { implicit c =>
              SQL(s"select * from advert.cancel_pay_subscription_bill(${bill.billId}, ${bill.advertiserId})")
            }

            StripeLogger.error(s"Stripe failed to charge an advertiser's credit card: ${ex.getMessage}. His/her id: ${bill.advertiserId}, and already switched his/her payment method to manual and canceled the corresponding bill")
            failedEmailSender ! AdvertiserStripeChargeFaild(bill.name, bill.email, Some(ex.getMessage))
        }
      }
    case "Stop" =>
      context.setReceiveTimeout(Duration.Undefined)
  }
}
