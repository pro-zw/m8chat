package scala

import anorm._
import _root_.models.advert.{Advertiser, AdvertiserRegister}
import org.scalatest.{TryValues, BeforeAndAfterEach}
import org.scalatestplus.play.{PlaySpec, OneAppPerSuite}
import play.api.Play
import play.api.db.DB

import _root_.models.social._
import play.api.http.Status
import play.api.mvc.Result
import play.api.test.Helpers._

import scala.concurrent.Future

abstract class UnitSpec extends PlaySpec with OneAppPerSuite with BeforeAndAfterEach with TryValues {
  val mobileApiToken = Play.application.configuration.getString("mobile.api.token").getOrElse("UYtsECp8eD1cqHk3zOPLvoluxSvoQPGc4ympcufJwzqEcayMtD")

  override protected def beforeEach(): Unit = {
    clearTestEnv()
  }

  override protected def afterEach(): Unit = {
    clearTestEnv()
  }

  def registerWeiZheng = {
    val regInfo = M8UserRegister("wade.zheng.pro@gmail.com",
      "pro_zw", Some("100"), Some("wei.zheng.1"), "Wei", "12345678", "Male", "Both",
      "m8user created for testing", Seq()
    )
    M8User.register(regInfo).get
  }

  def registerVlad = {
    val regInfo = M8UserRegister("verngt@gmail.com",
      "verngt", Some("200"), Some("verngt.1"), "Vladimir", "87654321", "Male", "Both",
      "m8user created for testing", Seq()
    )
    M8User.register(regInfo).get
  }

  def registerBrandon = {
    val regInfo = M8UserRegister("bcowan@crazydogapps.com.au",
      "bcowan", Some("300"), Some("bcowan.1"), "Brandon", "12345678", "Male", "Male",
      "m8user created for testing", Seq()
    )
    M8User.register(regInfo).get
  }

  def registerWeiZhengAsAdvertiser = {
    val regInfo = AdvertiserRegister("Wei Zheng",
      "Crazydog Apps", "wade.zheng.pro@gmail.com", "12345678", "bronze")
    Advertiser.register(regInfo).get
  }

  def activeAdvertiser(advertiserId: Long) = {
    DB.withConnection { implicit c =>
      SQL(
        s"""
           |update advert.advertisers
           |set status = 'active'::advertiser_status, active_util = current_timestamp + INTERVAL '1 day', balance_charged_at = current_timestamp - INTERVAL '1 day', balance = 10.00
           |where advertiser_id = $advertiserId
         """.stripMargin).executeUpdate()
    }
  }

  def verifyHttpResult(result: Future[Result],
                       exceptedStatus: Int,
                       content: String) = {
    status(result) mustBe exceptedStatus

    if (exceptedStatus != Status.NO_CONTENT) {
      contentType(result) mustBe Some("application/json")
      charset(result) mustBe Some("utf-8")
      contentAsString(result) must include (content)
    }
  }

  private def clearTestEnv(): Unit = {
    // We don't purge the contents in the upload folder during the test (it is not necessary)

    // Drop all rows in the database table
    DB.withConnection { implicit c =>
      SQL("delete from social.messages").executeUpdate()
      SQL("delete from social.chats").executeUpdate()
      SQL("delete from social.m8_users").executeUpdate()

      SQL("delete from advert.adverts").executeUpdate()
      SQL("delete from advert.bills").executeUpdate()
      SQL("delete from advert.advertisers").executeUpdate()
    }
  }
}
