package scala.models.advert

import models.advert._
import models.social.{M8UserList, M8UserPosition, M8User}
import org.scalatest.concurrent.ScalaFutures

class AdvertiserSpec extends UnitSpec with ScalaFutures {
  "Advertiser object" must {
    "register new advertiser" in {
      val regInfo = AdvertiserRegister("Wei Zheng",
        "Crazydog Apps", "wade.zheng.pro@gmail.com", "12345678", "bronze")

      Advertiser.register(regInfo) must be a 'success
    }

    "login" in {
      val registerResult = registerWeiZhengAsAdvertiser.get

      var loginResult = Advertiser.login(AdvertiserLogin("wade.zheng.pro@gmail.com", "12345678"))
      loginResult.success.value.get.accessToken mustBe empty

      Advertiser.confirmEmail(registerResult.advertiserId, registerResult.emailConfirmToken)

      loginResult = Advertiser.login(AdvertiserLogin("wade.zheng.pro@gmail.com", "12345678"))
      loginResult.success.value.get.status mustBe "confirmed"
      loginResult.success.value.get.accessToken must not be empty
    }

    "confirm the email" in {
      val weiResult = registerWeiZhengAsAdvertiser.get

      val result = Advertiser.confirmEmail(weiResult.advertiserId, weiResult.emailConfirmToken)
      result.success.value.updateCount mustBe 1
    }

    "check if email exists" in {
      registerWeiZhengAsAdvertiser
      assert(Advertiser.checkEmailExists("wade.zheng.pro@gmail.com"))
      assert(!Advertiser.checkEmailExists("helen_hah@gmail.com"))
    }

    "update business information" in {
      val registerResult = registerWeiZhengAsAdvertiser.get

      var result = Advertiser.updateBusiness(registerResult.advertiserId,
        AdvertiserBusinessUpdate("Crazydog Apps", Some("012345678"),
          Some("www.crazydog.com.au"), "4/15 Robinson Street", -25.274398, 133.775136, "We are a software company"))
      result.success.value must not be empty
      result.success.value.get must be >= 1L

      result = Advertiser.updateBusiness(registerResult.advertiserId,
        AdvertiserBusinessUpdate("Crazydog Apps", Some("012345678"),
          Some("www.crazydog.com.au"), "4/15 Robinson Street", -25.274398, 133.775136, "We are a software company"))
      result.success.value must not be empty
      result.success.value.get mustBe 1L
    }

    "authenticate advertiser" in {
      val registerResult = registerWeiZhengAsAdvertiser.get
      Advertiser.confirmEmail(registerResult.advertiserId, registerResult.emailConfirmToken) must be a 'success

      val loginResult = Advertiser.login(AdvertiserLogin("wade.zheng.pro@gmail.com", "12345678"))
      loginResult.success.value.get.status mustBe "confirmed"
      loginResult.success.value.get.accessToken must not be empty

      Advertiser.authenticate(loginResult.get.get.accessToken.get) must be a 'success
    }

    "get business information" in {
      val registerResult = registerWeiZhengAsAdvertiser.get
      Advertiser.confirmEmail(registerResult.advertiserId, registerResult.emailConfirmToken)
      Advertiser.getBusiness(registerResult.advertiserId) must be a 'success

      Advertiser.updateBusiness(registerResult.advertiserId,
        AdvertiserBusinessUpdate("Crazydog Apps", Some("012345678"),
          Some("www.crazydog.com.au"), "4/15 Robinson Street", -25.274398, 133.775136, "We are a software company"))

      val result = Advertiser.getBusiness(registerResult.advertiserId)
      result.success.value must not be empty
      result.success.value.get.businessName mustBe "Crazydog Apps"
    }

    "get the advert by id" in {
      val vladResult = registerVlad.get
      val advertiserResult = registerWeiZhengAsAdvertiser.get

      M8User.updatePosition(vladResult.userId, M8UserPosition(-25.274398, 133.775126))
      Advertiser.confirmEmail(advertiserResult.advertiserId, advertiserResult.emailConfirmToken)
      activeAdvertiser(advertiserResult.advertiserId)

      Advertiser.updateBusiness(advertiserResult.advertiserId,
        AdvertiserBusinessUpdate("Crazydog Apps", Some("012345678"),
          Some("www.crazydog.com.au"), "4/15 Robinson Street", -25.274398, 133.775136, "We are a software company"))

      val advertResult = M8User.listAdvertsNearby(vladResult.userId, M8UserList(None, None, 0, 10))

      val result = Advertiser.getAdvert(advertResult.get(0).advertId)
      result.success.value.get.businessName mustBe "Crazydog Apps"
    }

    "get account detail" in {
      val registerResult = registerWeiZhengAsAdvertiser.get

      val result = Advertiser.getAccountDetails(registerResult.advertiserId)
      result.success.value must not be empty
      result.success.value.get.name mustBe "Wei Zheng"
    }

    "update account" in {
      val registerResult = registerWeiZhengAsAdvertiser.get

      var result = Advertiser.updateAccount(registerResult.advertiserId,
        AdvertiserAccountUpdate("Wei Zheng 2", "XYZ Company", "pro_zw@gmail.com", None))
      result.success.value mustBe 1

      result = Advertiser.updateAccount(registerResult.advertiserId,
        AdvertiserAccountUpdate("Wei Zheng 3", "ZYX Company", "pro_zw_2@gmail.com", Some("87654321")))
      result.success.value mustBe 1

      result = Advertiser.updateAccount(100,
        AdvertiserAccountUpdate("Wei Zheng 4", "ZYX Company", "pro_zw_2@gmail.com", Some("87654321")))
      result.success.value mustBe 0
    }

    "forgot password request" in {
      registerWeiZhengAsAdvertiser

      var result = Advertiser.forgotPassword("wade.zheng.pro@gmail.com")
      result.success.value.updateCount mustBe 1
      result.success.value.resetDigest must not be empty

      result = Advertiser.forgotPassword("pro_zw@gmail.com")
      result.success.value.updateCount mustBe 0
      result.success.value.resetDigest mustBe empty
    }

    "reset password" in {
      registerWeiZhengAsAdvertiser

      val forgotResult = Advertiser.forgotPassword("wade.zheng.pro@gmail.com")
      val result = Advertiser.resetPassword(forgotResult.get.advertiserId.get,
        forgotResult.get.resetDigest.get, AdvertiserNewPassword("87654321"))
      result.success.value mustBe 1
    }

    "get plan" in {
      val registerResult = registerWeiZhengAsAdvertiser.get

      val result = Advertiser.getPlan(registerResult.advertiserId)
      result.success.value.get.name mustBe "Wei Zheng"
      result.success.value.get.planName mustBe "bronze"
    }

    "change plan" in {
      val registerResult = registerWeiZhengAsAdvertiser.get

      Advertiser.changePlan(registerResult.advertiserId, "silver") must be a 'success
      val result = Advertiser.getPlan(registerResult.advertiserId)
      result.success.value.get.planName mustBe "silver"
    }

    "get payment method" in {
      val registerResult = registerWeiZhengAsAdvertiser.get

      val result = Advertiser.getPaymentMethod(registerResult.advertiserId)
      result.success.value.get.name mustBe "Wei Zheng"
      result.success.value.get.paymentMethod mustBe "manual"
      assert(!result.success.value.get.hasCreditCard)
    }

    "change payment method" in {
      val registerResult = registerWeiZhengAsAdvertiser.get

      /*
      Advertiser.changePaymentMethod(registerResult.advertiserId, "wade.zheng.pro@gmail.com", AdvertiserPaymentMethodUpdate("subscription", Some("TestStripToken"))).success.value mustBe 1
      Advertiser.getPaymentMethod(registerResult.advertiserId).success.value.get.paymentMethod mustBe "subscription"
      */

      whenReady(Advertiser.changePaymentMethod(registerResult.advertiserId, "wade.zheng.pro@gmail.com", AdvertiserPaymentMethodUpdate("manual", None)).success.get) { result =>
        result mustBe 1
        Advertiser.getPaymentMethod(registerResult.advertiserId).success.value.get.paymentMethod mustBe "manual"
      }
    }
  }
}
