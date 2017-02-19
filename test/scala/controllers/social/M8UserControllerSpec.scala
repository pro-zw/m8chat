package scala.controllers.social

import models.advert.{AdvertiserBusinessUpdate, Advertiser}
import models.social._
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, Helpers, FakeRequest}
import utils._

class M8UserControllerSpec extends UnitSpec {
  "M8UserController" must {
    "login the user" in {
      implicit val writer = Json.writes[M8UserLogin]

      registerWeiZheng

      // Login by email address
      var loginInfo = M8UserLogin("wade.zheng.pro@gmail.com", "12345678")

      var fakeRequest = FakeRequest(Helpers.POST,
        controllers.social.routes.M8UserController.login().url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken))),
        Json.toJson(loginInfo)
      )

      verifyHttpResult(route(fakeRequest).get, Status.OK, "accessToken")

      // Login by username
      loginInfo = M8UserLogin("pro_zw", "12345678")

      fakeRequest = FakeRequest(Helpers.POST,
        controllers.social.routes.M8UserController.login().url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken))),
        Json.toJson(loginInfo)
      )

      verifyHttpResult(route(fakeRequest).get, Status.OK, "accessToken")

      // Logger.debug(contentAsString(route(fakeRequest).get))

      // Login with wrong password
      loginInfo = M8UserLogin("pro_zw", "87654321")

      fakeRequest = FakeRequest(Helpers.POST,
        controllers.social.routes.M8UserController.login().url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken))),
        Json.toJson(loginInfo)
      )

      verifyHttpResult(route(fakeRequest).get, Status.UNAUTHORIZED, "Unauthorized")

      // Call the login api without mobile api token
      loginInfo = M8UserLogin("pro_zw", "12345678")

      fakeRequest = FakeRequest(Helpers.POST,
        controllers.social.routes.M8UserController.login().url,
        FakeHeaders(),
        Json.toJson(loginInfo)
      )

      verifyHttpResult(route(fakeRequest).get, Status.BAD_REQUEST, "Mobile api token is not provided")
    }

    "register new user" in {
      // The writes and readers are only used in this test
      implicit val writer = Json.writes[M8UserRegister]
      implicit val reader = Json.reads[M8UserRegisterResult]

      var regInfo = M8UserRegister("wade.zheng.pro@gmail.com",
        "pro_zw", None, None, "Wei", "12345678", "Male", "Both",
        "m8user created for testing", Seq()
      )

      var fakeRequest = FakeRequest(Helpers.PUT,
        controllers.social.routes.M8UserController.register().url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken))),
        Json.toJson(regInfo))

      verifyHttpResult(route(fakeRequest).get, Status.OK, "accessToken")

      // Test invalid username
      regInfo = M8UserRegister("wade.zheng.pro@gmail.com",
        "pro_zw@tom.com", None, None, "Wei", "12345678", "Male", "Both",
        "m8user created for testing", Seq()
      )

      fakeRequest = FakeRequest(Helpers.PUT,
        controllers.social.routes.M8UserController.register().url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken))),
        Json.toJson(regInfo))

      verifyHttpResult(route(fakeRequest).get, Status.BAD_REQUEST, "alphanumeric")

      // Test invalid email
      regInfo = M8UserRegister("pro_zw",
        "pro_zw", None, None, "Wei", "12345678", "Male", "Both",
        "m8user created for testing", Seq()
      )

      fakeRequest = FakeRequest(Helpers.PUT,
        controllers.social.routes.M8UserController.register().url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken))),
        Json.toJson(regInfo))

      verifyHttpResult(route(fakeRequest).get, Status.BAD_REQUEST, "Invalid email address")
    }

    "check if email exists" in {
      registerWeiZheng

      val fakeRequest = FakeRequest(Helpers.POST,
        controllers.social.routes.M8UserController.checkEmailExists().url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken))),
        Json.toJson(Json.obj("email" -> "helen_hah@gmail.com")))

      verifyHttpResult(route(fakeRequest).get, Status.OK, "false")
    }

    "check if username exists" in {
      registerWeiZheng

      val fakeRequest = FakeRequest(Helpers.POST,
        controllers.social.routes.M8UserController.checkUsernameExists().url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken))),
        Json.toJson(Json.obj("username" -> "pro_zw")))

      verifyHttpResult(route(fakeRequest).get, Status.OK, "true")
    }

    "check if facebook account exists" in {
      registerWeiZheng

      val fakeRequest = FakeRequest(Helpers.POST,
        controllers.social.routes.M8UserController.checkFbAccountExists().url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken))),
        Json.toJson(Json.obj("fbUserId" -> "100", "fbUsername" -> "helen.hah.1")))

      verifyHttpResult(route(fakeRequest).get, Status.OK, "true")
    }

    "update the user's position" in {
      implicit val writer = Json.writes[M8UserPosition]

      val result = registerWeiZheng.get

      val fakeRequest = FakeRequest(Helpers.POST,
        controllers.social.routes.M8UserController.updatePosition().url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken),
          MobileAccessTokenHeader -> Seq(result.accessToken))),
        Json.toJson(M8UserPosition(-25.274398, 133.775136)))

      verifyHttpResult(route(fakeRequest).get, Status.NO_CONTENT, "{}")
    }

    "update the user's interests" in {
      val result = registerWeiZheng.get

      val fakeRequest = FakeRequest(Helpers.POST,
        controllers.social.routes.M8UserController.updateInterests().url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken),
          MobileAccessTokenHeader -> Seq(result.accessToken))),
        Json.obj("interests" -> Json.arr("Music", "Food::Bread", "Sport::Football")))

      verifyHttpResult(route(fakeRequest).get, Status.NO_CONTENT, "{}")
    }

    "get users nearby" in {
      implicit val writer = Json.writes[M8UserList]

      val weiResult = registerWeiZheng.get
      val vladResult = registerVlad.get
      val bcowanResult = registerBrandon.get

      M8User.updatePosition(weiResult.userId, M8UserPosition(22.24819, 114.20340))
      M8User.updatePosition(bcowanResult.userId, M8UserPosition(-25.274398, 133.775136))
      M8User.updatePosition(vladResult.userId, M8UserPosition(-25.274318, 133.775236))

      var fakeRequest = FakeRequest(Helpers.GET,
        "/mobile/social/api/users/nearby?page=0&pageSize=10",
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken),
          MobileAccessTokenHeader -> Seq(vladResult.accessToken))),
        Json.obj()
      )

      verifyHttpResult(route(fakeRequest).get, Status.OK, "distance")

      fakeRequest = FakeRequest(Helpers.GET,
        "/mobile/social/api/users/nearby?latitude=-25.274398&longitude=133.775137&page=0&pageSize=10",
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken),
          MobileAccessTokenHeader -> Seq(vladResult.accessToken))),
        Json.obj()
      )

      verifyHttpResult(route(fakeRequest).get, Status.OK, "distance")
    }

    "search users" in {
      implicit val writer = Json.writes[M8UserList]

      val weiResult = registerWeiZheng.get
      val vladResult = registerVlad.get
      val bcowanResult = registerBrandon.get

      M8User.updatePosition(weiResult.userId, M8UserPosition(22.24819, 114.20340))
      M8User.updatePosition(bcowanResult.userId, M8UserPosition(-25.274398, 133.775136))
      M8User.updatePosition(vladResult.userId, M8UserPosition(-25.274318, 133.775236))

      var fakeRequest = FakeRequest(Helpers.GET,
        "/mobile/social/api/users/search?criteria=bco&page=0&pageSize=10",
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken),
          MobileAccessTokenHeader -> Seq(vladResult.accessToken))),
        Json.obj()
      )
      verifyHttpResult(route(fakeRequest).get, Status.OK, "distance")

      fakeRequest = FakeRequest(Helpers.GET,
        "/mobile/social/api/users/search?criteria=br&latitude=-25.274398&longitude=133.775137&page=0&pageSize=10",
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken),
          MobileAccessTokenHeader -> Seq(vladResult.accessToken))),
        Json.obj()
      )
      verifyHttpResult(route(fakeRequest).get, Status.OK, "distance")

      fakeRequest = FakeRequest(Helpers.GET,
        "/mobile/social/api/users/search?criteria=v&page=0&pageSize=10",
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken),
          MobileAccessTokenHeader -> Seq(bcowanResult.accessToken))),
        Json.obj()
      )
      verifyHttpResult(route(fakeRequest).get, Status.BAD_REQUEST, "is too short")
    }

    "get profile of the other user" in {
      val weiResult = registerWeiZheng.get
      val bcowanResult = registerBrandon.get

      M8User.updatePosition(weiResult.userId, M8UserPosition(22.24819, 114.20340))
      M8User.updatePosition(bcowanResult.userId, M8UserPosition(-25.274398, 133.775136))

      M8User.updateInterests(weiResult.userId, Set("Music", "Reading", "Programming"))
      M8User.updateInterests(bcowanResult.userId, Set("Music", "Reading", "Sport:Football"))

      var fakeRequest = FakeRequest(Helpers.GET,
        controllers.social.routes.M8UserController.getOtherUserProfile(bcowanResult.userId).url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken),
          MobileAccessTokenHeader -> Seq(weiResult.accessToken))),
        Json.obj())

      verifyHttpResult(route(fakeRequest).get, Status.OK, "distance")

      fakeRequest = FakeRequest(Helpers.GET,
        controllers.social.routes.M8UserController.getOtherUserProfile(bcowanResult.userId).url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken),
          MobileAccessTokenHeader -> Seq(bcowanResult.accessToken))),
        Json.obj())

      verifyHttpResult(route(fakeRequest).get, Status.BAD_REQUEST, "with own id")
    }

    "start new chat (as well as maintain friend relationship)" in {
      implicit val writer = Json.writes[M8UserNewChat]

      val weiResult = registerWeiZheng.get
      val bcowanResult = registerBrandon.get

      var fakeRequest = FakeRequest(Helpers.PUT,
        controllers.social.routes.M8UserController.createNewChat().url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken),
          MobileAccessTokenHeader -> Seq(weiResult.accessToken))),
        Json.toJson(M8UserNewChat(Vector(bcowanResult.userId), "Hi mate"))
      )

      verifyHttpResult(route(fakeRequest).get, Status.OK, "chatId")

      fakeRequest = FakeRequest(Helpers.GET,
        controllers.social.routes.M8UserController.getFriends().url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken),
          MobileAccessTokenHeader -> Seq(weiResult.accessToken))),
        Json.obj()
      )

      // Logger.debug(contentAsString(route(fakeRequest).get))

      verifyHttpResult(route(fakeRequest).get, Status.OK, "bcowan")

      fakeRequest = FakeRequest(Helpers.POST,
        controllers.social.routes.M8UserController.blockUser(bcowanResult.userId).url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken),
          MobileAccessTokenHeader -> Seq(weiResult.accessToken))),
        Json.obj()
      )

      verifyHttpResult(route(fakeRequest).get, Status.NO_CONTENT, "")

      fakeRequest = FakeRequest(Helpers.GET,
        controllers.social.routes.M8UserController.getFriends().url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken),
          MobileAccessTokenHeader -> Seq(weiResult.accessToken))),
        Json.obj()
      )

      verifyHttpResult(route(fakeRequest).get, Status.OK, "[]")
    }

    "send new message to previous chat" in {
      implicit val messageWriter = Json.writes[M8UserNewMessage]
      implicit val chatWriter = Json.writes[M8UserNewChat]

      val weiResult = registerWeiZheng.get
      val bcowanResult = registerBrandon.get

      var fakeRequest = FakeRequest(Helpers.PUT,
        controllers.social.routes.M8UserController.createNewChat().url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken),
          MobileAccessTokenHeader -> Seq(weiResult.accessToken))),
        Json.toJson(M8UserNewChat(Vector(bcowanResult.userId), "Hi mate"))
      )

      val chatResult = Json.parse(contentAsString(route(fakeRequest).get))

      fakeRequest = FakeRequest(Helpers.PUT,
        controllers.social.routes.M8UserController.createNewMessage((chatResult \ "chatId").as[Long]).url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken),
          MobileAccessTokenHeader -> Seq(weiResult.accessToken))),
        Json.toJson(M8UserNewMessage("What is up?"))
      )

      verifyHttpResult(route(fakeRequest).get, Status.NO_CONTENT, "")
    }

    "get chats" in {
      implicit val chatWriter = Json.writes[M8UserNewChat]

      val weiResult = registerWeiZheng.get
      val bcowanResult = registerBrandon.get

      var fakeRequest = FakeRequest(Helpers.PUT,
        controllers.social.routes.M8UserController.createNewChat().url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken),
          MobileAccessTokenHeader -> Seq(weiResult.accessToken))),
        Json.toJson(M8UserNewChat(Vector(bcowanResult.userId), "Hi mate"))
      )

      verifyHttpResult(route(fakeRequest).get, Status.OK, "chatId")

      fakeRequest = FakeRequest(Helpers.GET,
        controllers.social.routes.M8UserController.getChats().url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken),
          MobileAccessTokenHeader -> Seq(bcowanResult.accessToken))),
        Json.obj()
      )

      verifyHttpResult(route(fakeRequest).get, Status.OK, "Wei")

      // Logger.debug(contentAsString(route(fakeRequest).get))
    }

    "get chat messages" in {
      implicit val chatWriter = Json.writes[M8UserNewChat]

      val weiResult = registerWeiZheng.get
      val bcowanResult = registerBrandon.get

      var fakeRequest = FakeRequest(Helpers.PUT,
        controllers.social.routes.M8UserController.createNewChat().url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken),
          MobileAccessTokenHeader -> Seq(weiResult.accessToken))),
        Json.toJson(M8UserNewChat(Vector(bcowanResult.userId), "Hi mate"))
      )

      val chatResult = Json.parse(contentAsString(route(fakeRequest).get))

      fakeRequest = FakeRequest(Helpers.GET,
        controllers.social.routes.M8UserController.getChatMessages((chatResult \ "chatId").as[Long]).url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken),
          MobileAccessTokenHeader -> Seq(bcowanResult.accessToken))),
        Json.obj()
      )

      verifyHttpResult(route(fakeRequest).get, Status.OK, "Hi mate")

      // Logger.debug(contentAsString(route(fakeRequest).get))
    }

    "get other participants" in {
      implicit val chatWriter = Json.writes[M8UserNewChat]

      val weiResult = registerWeiZheng.get
      val bcowanResult = registerBrandon.get

      var fakeRequest = FakeRequest(Helpers.PUT,
        controllers.social.routes.M8UserController.createNewChat().url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken),
          MobileAccessTokenHeader -> Seq(weiResult.accessToken))),
        Json.toJson(M8UserNewChat(Vector(bcowanResult.userId), "Hi mate"))
      )

      val chatResult = Json.parse(contentAsString(route(fakeRequest).get))

      fakeRequest = FakeRequest(Helpers.GET,
        controllers.social.routes.M8UserController.getChatOtherParticipants((chatResult \ "chatId").as[Long]).url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken),
          MobileAccessTokenHeader -> Seq(bcowanResult.accessToken))),
        Json.obj()
      )

      // Logger.debug(contentAsString(route(fakeRequest).get))
      verifyHttpResult(route(fakeRequest).get, Status.OK, s"[${weiResult.userId}]")
    }

    "leave a chat" in {
      implicit val chatWriter = Json.writes[M8UserNewChat]

      val weiResult = registerWeiZheng.get
      val bcowanResult = registerBrandon.get

      var fakeRequest = FakeRequest(Helpers.PUT,
        controllers.social.routes.M8UserController.createNewChat().url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken),
          MobileAccessTokenHeader -> Seq(weiResult.accessToken))),
        Json.toJson(M8UserNewChat(Vector(bcowanResult.userId), "Hi mate"))
      )

      val chatResult = Json.parse(contentAsString(route(fakeRequest).get))

      /*
      fakeRequest = FakeRequest(Helpers.GET,
        controllers.social.routes.M8UserController.getChats().url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken),
          MobileAccessTokenHeader -> Seq(bcowanResult.accessToken))),
        Json.obj()
      )

      verifyHttpResult(route(fakeRequest).get, Status.OK, "Wei")
      */

      fakeRequest = FakeRequest(Helpers.POST,
        controllers.social.routes.M8UserController.leaveChat((chatResult \ "chatId").as[Long]).url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken),
          MobileAccessTokenHeader -> Seq(bcowanResult.accessToken))),
        Json.obj()
      )

      verifyHttpResult(route(fakeRequest).get, Status.NO_CONTENT, "")

      fakeRequest = FakeRequest(Helpers.GET,
        controllers.social.routes.M8UserController.getChats().url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken),
          MobileAccessTokenHeader -> Seq(bcowanResult.accessToken))),
        Json.obj()
      )

      verifyHttpResult(route(fakeRequest).get, Status.OK, "[]")
    }

    "list adverts nearby" in {
      val vladResult = registerVlad.get
      val advertiserResult = registerWeiZhengAsAdvertiser.get

      M8User.updatePosition(vladResult.userId, M8UserPosition(-25.274398, 133.775126))
      Advertiser.confirmEmail(advertiserResult.advertiserId, advertiserResult.emailConfirmToken)
      activeAdvertiser(advertiserResult.advertiserId)

      Advertiser.updateBusiness(advertiserResult.advertiserId,
        AdvertiserBusinessUpdate("Crazydog Apps", Some("012345678"),
          Some("www.crazydog.com.au"), "4/15 Robinson Street", -25.274398, 133.775136, "We are a software company"))

      val fakeRequest = FakeRequest(Helpers.GET,
        "/mobile/advert/api/users/me/adverts/nearby?page=0&pageSize=10",
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken),
          MobileAccessTokenHeader -> Seq(vladResult.accessToken))),
        Json.obj()
      )

      verifyHttpResult(route(fakeRequest).get, Status.OK, "Crazydog Apps")
    }

    "get profile" in {
      val weiResult = registerWeiZheng.get
      M8User.updateInterests(weiResult.userId, Set("Music", "Reading", "Programming", "Food::Chinese"))

      val fakeRequest = FakeRequest(Helpers.GET,
        controllers.social.routes.M8UserController.getProfile().url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken),
          MobileAccessTokenHeader -> Seq(weiResult.accessToken))),
        Json.obj()
      )

      verifyHttpResult(route(fakeRequest).get, Status.OK, "Wei")
    }

    "update profile" in {
      implicit val writer = Json.writes[M8UserUpdate]

      val weiResult = registerWeiZheng.get

      val fakeRequest = FakeRequest(Helpers.POST,
        controllers.social.routes.M8UserController.updateProfile().url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken),
          MobileAccessTokenHeader -> Seq(weiResult.accessToken))),
        Json.toJson(M8UserUpdate("pro_zw@tom.com",
          "pro_zw_2", "WeiUpdated", "Female", "Male", "I've changed my description",
          Vector(None, None, None, Some(""), Some(""), Some("")),
          Vector("Make new friends", "Music", "Reading")))
      )

      verifyHttpResult(route(fakeRequest).get, Status.NO_CONTENT, "")
    }

    "change password" in {
      implicit val writer = Json.writes[M8UserChangePassword]

      val weiResult = registerWeiZheng.get
      var fakeRequest = FakeRequest(Helpers.POST,
        controllers.social.routes.M8UserController.changePassword().url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken),
          MobileAccessTokenHeader -> Seq(weiResult.accessToken))),
        Json.toJson(M8UserChangePassword("12345678", "87654321"))
      )

      verifyHttpResult(route(fakeRequest).get, Status.OK, "success")

      fakeRequest = FakeRequest(Helpers.POST,
        controllers.social.routes.M8UserController.changePassword().url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken),
          MobileAccessTokenHeader -> Seq(weiResult.accessToken))),
        Json.toJson(M8UserChangePassword("12345678", "87654321"))
      )

      verifyHttpResult(route(fakeRequest).get, Status.OK, "failure")
    }

    "delete account" in {
      val weiResult = registerWeiZheng.get
      val fakeRequest = FakeRequest(Helpers.DELETE,
        controllers.social.routes.M8UserController.deleteAccount().url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken),
          MobileAccessTokenHeader -> Seq(weiResult.accessToken))),
        Json.obj())

      verifyHttpResult(route(fakeRequest).get, Status.NO_CONTENT, "")
    }

    "add apple apn token" in {
      val weiResult = registerWeiZheng.get

      val fakeRequest = FakeRequest(Helpers.POST,
        controllers.social.routes.M8UserController.addAppleApnToken().url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken),
          MobileAccessTokenHeader -> Seq(weiResult.accessToken))),
        Json.obj("token" -> "TestOnlyToken")
      )

      verifyHttpResult(route(fakeRequest).get, Status.NO_CONTENT, "")
    }

    "add android apn token" in {
      val weiResult = registerWeiZheng.get

      val fakeRequest = FakeRequest(Helpers.POST,
        controllers.social.routes.M8UserController.addAndroidApnToken().url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken),
          MobileAccessTokenHeader -> Seq(weiResult.accessToken))),
        Json.obj("token" -> "TestOnlyToken")
      )

      verifyHttpResult(route(fakeRequest).get, Status.NO_CONTENT, "")
    }
  }
}
