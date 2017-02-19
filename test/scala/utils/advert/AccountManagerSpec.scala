package scala.utils.advert

import akka.actor.{ReceiveTimeout, ActorSystem}
import akka.testkit.TestActorRef
import akka.util.Timeout
import akka.pattern.ask
import anorm._
import com.typesafe.config.ConfigFactory
import models.advert.Advertiser
import play.api.db.DB
import utils.advert.AccountManager

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

class AccountManagerSpec extends UnitSpec {
  implicit val actorSystem = ActorSystem("testActorSystem", ConfigFactory.load())
  implicit val timeout = Timeout(60, SECONDS)

  "AccountManager" must {
    "manage accounts periodically" in {
      val advertiserResult = registerWeiZhengAsAdvertiser.get
      Advertiser.confirmEmail(advertiserResult.advertiserId, advertiserResult.emailConfirmToken)
      activeAdvertiser(advertiserResult.advertiserId)

      val actorRef = TestActorRef[AccountManager]
      val future = actorRef ? ReceiveTimeout
      future.map(_ => {
        DB.withConnection { implicit c =>
          SQL(s"select balance from advert.advertisers limit 1")
            .apply()
            .map(row => row[BigDecimal]("balance") must be < BigDecimal(6.0))

          SQL(s"select count(*) as bills_count from advert.bills")
            .apply()
            .map(row => row[Int]("bills_count") mustBe 1)
        }
      })
    }
  }
}
