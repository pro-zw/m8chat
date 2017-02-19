package scala.models.advert

import akka.actor.ActorSystem
import akka.util.Timeout
import anorm._
import com.typesafe.config.ConfigFactory
import models.advert.{Bill, Advertiser}
import play.api.db.DB

import scala.concurrent.duration._
import scala.language.postfixOps

class BillSpec extends UnitSpec {
  implicit val actorSystem = ActorSystem("testActorSystem", ConfigFactory.load())
  implicit val timeout = Timeout(60, SECONDS)

  "Bill object" must {
    "get bill of an advertiser" in {
      val advertiserResult = registerWeiZhengAsAdvertiser.get
      Advertiser.confirmEmail(advertiserResult.advertiserId, advertiserResult.emailConfirmToken)

      val result = Bill.getLatest(advertiserResult.advertiserId)
      result.success.value must not be empty
    }

    // Check individual PostgreSQL functions in 7.sql
    "manual payment functions sets and procedures" in {
      val advertiserResult = registerWeiZhengAsAdvertiser.get
      Advertiser.confirmEmail(advertiserResult.advertiserId, advertiserResult.emailConfirmToken)

      val result = Bill.getLatest(advertiserResult.advertiserId)
      result.success.value must not be empty

      DB.withConnection { implicit c =>
        SQL(s"select * from advert.get_paypal_bill(${result.success.value.get.billId}, ${advertiserResult.advertiserId})")
          .apply().map {
          row => row[BigDecimal]("_amount") must be > BigDecimal(30.0)
        }
      }

      DB.withConnection { implicit c =>
        SQL(s"select * from advert.begin_pay_paypal_bill(${result.success.value.get.billId}, ${advertiserResult.advertiserId})")
          .apply().map {
          row => row[Int]("_update_count") must be > 0
        }
      }

      DB.withConnection { implicit c =>
        SQL(s"select * from advert.end_pay_bill(${result.success.value.get.billId}, ${advertiserResult.advertiserId}, {paymentId})")
          .on('paymentId -> "Test Only")
          .apply().map {
          row => row[Int]("_update_count") must be > 0
        }
      }
    }

    "get bills to email" in {
      val advertiserResult = registerWeiZhengAsAdvertiser.get
      Advertiser.confirmEmail(advertiserResult.advertiserId, advertiserResult.emailConfirmToken)

      DB.withTransaction { implicit c =>
        SQL("select count(*) as _bills_count from advert.get_bills_to_email()")
          .apply().map {
          row => row[Int]("_bills_count") mustBe 1
        }
      }

      DB.withTransaction { implicit c =>
        SQL("select count(*) as _bills_count from advert.get_bills_to_email()")
          .apply().map {
          row => row[Int]("_bills_count") mustBe 0
        }
      }
    }
  }
}
