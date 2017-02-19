package scala.models.social

import anorm._
import models.advert.{Advertiser, AdvertiserBusinessUpdate}
import models.social._
import play.api.db.DB

import scala.util.Try

// If there is enough time, we'd like to move all tests from spec2 (in spec package) to ScalaTest (in scala package)
class M8UserSpec extends UnitSpec {
  "M8User object" must {
    "register new user" in {
      val regInfo = M8UserRegister("wade.zheng.pro@gmail.com",
        "pro_zw", None, None, "Wei", "12345678", "Male", "Both",
        "m8user created for testing", Seq()
      )

      M8User.register(regInfo) must be a 'success
    }

    "login existing user" in {
      registerWeiZheng
      M8User.login(M8UserLogin("wade.zheng.pro@gmail.com", "12345678")) must be a 'success
    }

    "check if email exists" in {
      registerWeiZheng
      assert(M8User.checkEmailExists("wade.zheng.pro@gmail.com"))
      assert(!M8User.checkEmailExists("helen_hah@gmail.com"))
    }

    "check if username exists" in {
      registerWeiZheng
      assert(M8User.checkUsernameExists("pro_zw"))
      assert(!M8User.checkUsernameExists("helen_hah"))
    }

    "check if facebook account exists" in {
      registerWeiZheng
      assert(M8User.checkFbAccountExists("100", "helen.han.2"))
      assert(!M8User.checkFbAccountExists("200", "helen.han.2"))
    }

    "update position" in {
      val result = registerWeiZheng.get
      M8User.updatePosition(result.userId, M8UserPosition(-25.274398, 133.775136)).success.value mustBe 1
    }

    "authenticate by access token" in {
      val registerResult = registerWeiZheng.get
      val authResult = M8User.authenticate(registerResult.accessToken)

      authResult.success.value.get.email mustBe "wade.zheng.pro@gmail.com"
      authResult.success.value.get.userId mustBe registerResult.userId
      authResult.success.value.get.username mustBe "pro_zw"

      M8User.authenticate("12345678").success.value mustBe empty
    }

    "list nearby users" in {
      val weiResult = registerWeiZheng.get
      val vladResult = registerVlad.get
      val bcowanResult = registerBrandon.get

      M8User.updatePosition(weiResult.userId, M8UserPosition(22.24819, 114.20340))
      M8User.updatePosition(vladResult.userId, M8UserPosition(-25.274318, 133.775236))
      M8User.updatePosition(bcowanResult.userId, M8UserPosition(-25.274398, 133.775136))

      var result = M8User.listUsersNearby(bcowanResult.userId, M8UserList(Some(-25.274399), Some(133.775136), 0, 10))
      result.success.value.length mustBe 1

      result = M8User.listUsersNearby(vladResult.userId, M8UserList(None, None, 0, 10))
      result.success.value.length mustBe 1
    }

    "search users" in {
      val vladResult = registerVlad.get
      val bcowanResult = registerBrandon.get

      M8User.updatePosition(vladResult.userId, M8UserPosition(-25.274318, 133.775236))
      M8User.updatePosition(bcowanResult.userId, M8UserPosition(-25.274398, 133.775136))

      var result = M8User.searchUsers(bcowanResult.userId, "ver", M8UserList(None, None, 0, 10))
      result.success.value.length mustBe 1

      result = M8User.searchUsers(vladResult.userId, "bco", M8UserList(None, None, 0, 10))
      result.success.value.length mustBe 1
    }

    "get profile of the other user" in {
      val weiResult = registerWeiZheng.get
      val bcowanResult = registerBrandon.get

      M8User.updatePosition(weiResult.userId, M8UserPosition(22.24819, 114.20340))
      M8User.updatePosition(bcowanResult.userId, M8UserPosition(-25.274398, 133.775136))

      M8User.updateInterests(weiResult.userId, Set("Music", "Reading", "Programming", "Food::Chinese")).success.value mustBe 1
      M8User.updateInterests(bcowanResult.userId, Set("Music", "Reading", "Sport::Football")).success.value mustBe 1

      val result = M8User.getOtherUserProfile(weiResult.userId, bcowanResult.userId)
      result.success.value.get.firstName mustBe "Brandon"
      result.success.value.get.interests must contain ("reading")
    }

    "start new chat (as well as maintain friend relationship)" in {
      val weiResult = registerWeiZheng.get
      val bcowanResult = registerBrandon.get

      M8User.createNewChat(weiResult.userId, M8UserNewChat(Seq(bcowanResult.userId), "Hi")) must be a 'success

      var result = M8User.getFriends(weiResult.userId)
      result.success.value.length mustBe 1

      M8User.blockUser(weiResult.userId, bcowanResult.userId) must be a 'success
      result = M8User.getFriends(weiResult.userId)
      result.success.value.length mustBe 0
    }

    "send new message to previous chat" in {
      val weiResult = registerWeiZheng.get
      val bcowanResult = registerBrandon.get

      val result = M8User.createNewChat(weiResult.userId, M8UserNewChat(Seq(bcowanResult.userId), "Hi"))
      M8User.createNewMessage(bcowanResult.userId, result.get.chatId, M8UserNewMessage("What is up")) must be a 'success
    }

    "get chats" in {
      val weiResult = registerWeiZheng.get
      val bcowanResult = registerBrandon.get
      val vladResult = registerVlad.get

      val result = M8User.createNewChat(weiResult.userId, M8UserNewChat(Seq(bcowanResult.userId), "Hi"))
      M8User.createNewMessage(bcowanResult.userId, result.get.chatId, M8UserNewMessage("What is up")) must be a 'success

      var chats = M8User.getChats(weiResult.userId)
      chats.success.value.length mustBe 1
      assert(!chats.success.value(0).lastMessageIsRead)
      chats.success.value(0).firstName mustBe "Brandon"

      M8User.createNewChat(vladResult.userId, M8UserNewChat(Seq(bcowanResult.userId, weiResult.userId), "Hi")) must be a 'success
      chats = M8User.getChats(weiResult.userId)
      chats.success.value.length mustBe 2
      assert(!chats.success.value(0).lastMessageIsRead)
      chats.success.value(0).firstName mustBe "Vladimir"
    }

    "get chat messages" in {
      val weiResult = registerWeiZheng.get
      val bcowanResult = registerBrandon.get
      val vladResult = registerVlad.get

      val chatIdOpt = M8User.createNewChat(weiResult.userId, M8UserNewChat(Seq(bcowanResult.userId, vladResult.userId), "Hi"))
      M8User.createNewMessage(bcowanResult.userId, chatIdOpt.get.chatId, M8UserNewMessage("What is up"))
      M8User.createNewMessage(vladResult.userId, chatIdOpt.get.chatId, M8UserNewMessage("Nice to join"))
      M8User.createNewMessage(weiResult.userId, chatIdOpt.get.chatId, M8UserNewMessage("About the progress of m8chat"))

      val messages = M8User.getChatMessages(vladResult.userId, chatIdOpt.get.chatId)
      messages.success.value.length mustBe 4
    }

    "get other participants" in {
      val weiResult = registerWeiZheng.get
      val bcowanResult = registerBrandon.get

      val chatIdOpt = M8User.createNewChat(weiResult.userId, M8UserNewChat(Seq(bcowanResult.userId), "Hi"))
      val otherParticipants = M8User.getChatOtherParticipants(weiResult.userId, chatIdOpt.get.chatId)
      otherParticipants.success.value.length mustBe 1
      otherParticipants.success.value(0) mustBe bcowanResult.userId
    }

    "leave a chat" in {
      val weiResult = registerWeiZheng.get
      val bcowanResult = registerBrandon.get
      val vladResult = registerVlad.get

      val chatIdOpt = M8User.createNewChat(weiResult.userId, M8UserNewChat(Seq(bcowanResult.userId, vladResult.userId), "Hi"))
      M8User.createNewMessage(bcowanResult.userId, chatIdOpt.get.chatId, M8UserNewMessage("What is up")) must be a 'success

      var chats = M8User.getChats(bcowanResult.userId)
      chats.success.value.length mustBe 1

      val result = M8User.leaveChat(bcowanResult.userId, chatIdOpt.get.chatId)
      result.success.value mustBe 1
      chats = M8User.getChats(bcowanResult.userId)
      chats.success.value.length mustBe 0
    }

    "hide and unhide a chat" in {
      val weiResult = registerWeiZheng.get
      val bcowanResult = registerBrandon.get
      val vladResult = registerVlad.get
      val chatIdOpt = M8User.createNewChat(weiResult.userId, M8UserNewChat(Seq(bcowanResult.userId, vladResult.userId), "Hi"))

      var result = M8User.hideChat(bcowanResult.userId, chatIdOpt.get.chatId)
      result.success.value mustBe 1

      var chats = M8User.getChats(bcowanResult.userId)
      chats.success.value.length mustBe 0

      chats = M8User.getChats(weiResult.userId)
      chats.success.value.length mustBe 1

      M8User.createNewMessage(vladResult.userId, chatIdOpt.get.chatId, M8UserNewMessage("What is up")) must be a 'success
      chats = M8User.getChats(bcowanResult.userId)
      chats.success.value.length mustBe 1
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

      val result = M8User.listAdvertsNearby(vladResult.userId, M8UserList(None, None, 0, 10))
      result.success.value.length mustBe 1
    }

    "handle forgot password request" in {
      registerWeiZheng.get

      var result = M8User.forgotPassword("wade.zheng.pro@gmail.com")
      result.success.value.updateCount mustBe 1
      result.success.value.resetDigest must not be empty

      result = M8User.forgotPassword("pro_zw")
      result.success.value.updateCount mustBe 1
      result.success.value.resetDigest must not be empty

      result = M8User.forgotPassword("verngt")
      result.success.value.updateCount mustBe 0
      result.success.value.resetDigest mustBe empty
    }

    "get name of password reset" in {
      registerWeiZheng.get

      val forgotResult = M8User.forgotPassword("wade.zheng.pro@gmail.com")
      val result = M8User.getNameOfPasswordReset(forgotResult.get.userId.get,
        forgotResult.get.resetDigest.get)
      result.success.value must not be empty
      result.success.value.get mustBe "Wei"
    }

    "reset password" in {
      registerWeiZheng.get

      val forgotResult = M8User.forgotPassword("wade.zheng.pro@gmail.com")
      val result = M8User.resetPassword(forgotResult.get.userId.get,
        forgotResult.get.resetDigest.get, M8UserNewPassword("87654321"))
      result.success.value mustBe 1
    }

    "get profile" in {
      val weiResult = registerWeiZheng.get

      val result = M8User.getProfile(weiResult.userId)
      result.success.value must not be empty
      result.success.value.get.firstName mustBe "Wei"
    }

    "update profile" in {
      val weiResult = registerWeiZheng.get

      val updateResult = M8User.updateProfile(weiResult.userId,
        M8UserUpdate("pro_zw@tom.com",
          "pro_zw_2", "WeiUpdated", "Female", "Male", "I've changed my description",
          Vector(None, None, None, Some(""), Some(""), Some("")),
          Vector("Make new friends", "Music", "Reading"))
      )
      updateResult.success.value mustBe 1

      val result = M8User.getProfile(weiResult.userId)
      result.success.value must not be empty
      result.success.value.get.firstName mustBe "WeiUpdated"
    }

    "change password" in {
      val weiResult = registerWeiZheng.get

      val result = M8User.changePassword(weiResult.userId, M8UserChangePassword("12345678", "87654321"))
      result.success.value mustBe 1
    }

    "delete account" in {
      val vladResult = registerVlad.get
      val bcowanResult = registerBrandon.get

      M8User.updatePosition(vladResult.userId, M8UserPosition(-25.274318, 133.775236))
      M8User.updatePosition(bcowanResult.userId, M8UserPosition(-25.274398, 133.775136))

      var searchResult = M8User.searchUsers(bcowanResult.userId, "ver", M8UserList(None, None, 0, 10))
      searchResult.success.value.length mustBe 1

      val result = M8User.deleteAccount(vladResult.userId)
      result.success.value mustBe 1

      searchResult = M8User.searchUsers(bcowanResult.userId, "ver", M8UserList(None, None, 0, 10))
      searchResult.success.value.length mustBe 0
    }

    "add apple apn token" in {
      val weiResult = registerWeiZheng.get

      val result = M8User.addAppleApnToken(weiResult.userId, "TestOnlyToken")
      result.success.value mustBe 1
    }

    "add android apn token" in {
      val weiResult = registerWeiZheng.get

      val result = M8User.addAndroidApnToken(weiResult.userId, "TestOnlyToken")
      result.success.value mustBe 1
    }

    "get message push info" in {
      val weiResult = registerWeiZheng.get
      val bcowanResult = registerBrandon.get

      M8User.addAppleApnToken(bcowanResult.userId, "TestOnlyToken01")
      M8User.addAppleApnToken(bcowanResult.userId, "TestOnlyToken02")
      M8User.addAppleApnToken(bcowanResult.userId, "TestOnlyToken03")
      val chatIdOpt = M8User.createNewChat(weiResult.userId, M8UserNewChat(Seq(bcowanResult.userId), "Hi"))

      val result =
        Try(DB.withTransaction { implicit c =>
          SQL(s"select * from social.get_message_push_info(${chatIdOpt.get.messageId})")
            .apply()
            .map(row => (row[Long]("_chat_id"),
            s"${row[String]("_sender_first_name")}: ${row[String]("_message")}",
                row[Array[String]]("_apple_apn_tokens").distinct,
                row[Array[String]]("_android_apn_tokens").distinct
            ))
            .headOption
        })
      result.success.value must not be empty
      result.success.value.get._1 mustBe chatIdOpt.get.chatId
      /*
      val tokens = result.success.value.get._3.map(_.split(",")).flatten
      Console.println(tokens(0))
      */
    }
  }
}
