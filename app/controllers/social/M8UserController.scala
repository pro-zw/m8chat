package controllers.social

import models.advert.Advertiser
import play.api.cache.{Cache, Cached}
import play.api.Play.current
import scala.util.{Success, Failure}
import models.social._
import play.api.libs.json.Json
import play.api.mvc.{Action, Results, Controller}
import utils._
import utils.social._

object M8UserController extends Controller {
  def login = MobileApiAuthAction(parse.json) { request =>
    implicit val reader = M8User.jsonLoginReads
    implicit val writer = M8User.jsonLoginWrites

    request.body.validate[M8UserLogin].toResponse(
      loginInfo => M8User.login(loginInfo) match {
        case Success(Some(result)) => Ok(Json.toJson(result))
        case _ => JsonErrorResult(Results.Unauthorized, "Unauthorized")
      }
    )
  }

  def register = MobileApiAuthAction(parse.json(8388608)) { request =>
    implicit val reader = M8User.jsonRegisterReads
    implicit val writer = M8User.jsonRegisterWrites

    request.body.validate[M8UserRegister].toResponse(
      regInfo => M8User.register(regInfo) match {
        case Success(Some(result)) => Ok(Json.toJson(result))
        case Failure(ex) => JsonBadRequest(ex)
        case _ => JsonBadRequest("Unable to register the user")
      }
    )
  }

  def checkEmailExists = MobileApiAuthAction(parse.json) { request =>
    val email = (request.body \ "email").as[String]
    Ok(Json.obj("exists" -> M8User.checkEmailExists(email)))
  }

  def checkUsernameExists = MobileApiAuthAction(parse.json) { request =>
    val username = (request.body \ "username").as[String]
    Ok(Json.obj("exists" -> M8User.checkUsernameExists(username)))
  }

  def checkFbAccountExists = MobileApiAuthAction(parse.json) { request =>
    val fbUserId = (request.body \ "fbUserId").as[String]
    val fbUsername = (request.body \ "fbUsername").as[String]
    Ok(Json.obj("exists" -> M8User.checkFbAccountExists(fbUserId, fbUsername)))
  }

  def updatePosition() = (MobileApiAuthAction andThen M8UserAuthAction)(parse.json) { request =>
    implicit val reader = M8User.jsonPositionReads

    request.body.validate[M8UserPosition].toResponse(
      posInfo => M8User.updatePosition(request.m8UserAuth.userId, posInfo) match {
        case Success(_) => NoContent
        case Failure(ex) => JsonBadRequest(ex)
      }
    )
  }

  def updateInterests() = (MobileApiAuthAction andThen M8UserAuthAction)(parse.json) { request =>
    val interests = (request.body \ "interests").as[Set[String]]

    M8User.updateInterests(request.m8UserAuth.userId, interests) match {
      case Success(_) => NoContent
      case Failure(ex) => JsonBadRequest(ex)
    }
  }

  def listUsersNearby(latitude: Option[Double],
                      longitude: Option[Double],
                      page: Int,
                      pageSize: Int) = (MobileApiAuthAction andThen M8UserAuthAction) { request =>
    implicit val writer = M8User.jsonUserListWrites

    M8User.listUsersNearby(request.m8UserAuth.userId,
      new M8UserList(latitude, longitude, page, pageSize)) match {
      case Success(users) => Ok(Json.obj("users" -> users))
      case Failure(ex) => JsonBadRequest(ex)
    }
  }

  def searchUsers(criteria: String,
                  latitude: Option[Double],
                  longitude: Option[Double],
                  page: Int,
                  pageSize: Int) = (MobileApiAuthAction andThen M8UserAuthAction) { request =>
    if (criteria.trim.length <= 1) {
      JsonBadRequest("The search criteria of users is too short")
    } else {
      implicit val writer = M8User.jsonUserListWrites

      M8User.searchUsers(request.m8UserAuth.userId, criteria.trim,
        new M8UserList(latitude, longitude, page, pageSize)) match {
        case Success(users) => Ok(Json.obj("users" -> users))
        case Failure(ex) => JsonBadRequest(ex)
      }
    }
  }

  def getOtherUserProfile(otherUserId: Long) = (MobileApiAuthAction andThen M8UserAuthAction) { request =>
    implicit val writer = M8User.jsonUserProfileOfOtherWrites

    val userId = request.m8UserAuth.userId
    if (userId == otherUserId) {
      JsonBadRequest("Try to get other user's profile with own id")
    } else {
      M8User.getOtherUserProfile(userId, otherUserId) match {
        case Success(Some(result)) => Ok(Json.toJson(result))
        case Success(None) => Ok(Json.obj())
        case Failure(ex) => JsonBadRequest(ex)
      }
    }
  }

  def createNewChat = (MobileApiAuthAction andThen M8UserAuthAction)(parse.json) { request =>
    implicit val reader = M8User.jsonNewChatReads

    request.body.validate[M8UserNewChat].toResponse(
      chatInfo => M8User.createNewChat(request.m8UserAuth.userId, chatInfo) match {
        case Success(result) =>
          MessageNotificationRouter ! M8UserMessageNotification(result.messageId)
          Ok(Json.obj("chatId" -> result.chatId))
        case Failure(ex) => JsonBadRequest(ex)
      }
    )
  }

  def createNewMessage(chatId: Long) = (MobileApiAuthAction andThen M8UserAuthAction)(parse.json) { request =>
    implicit val reader = M8User.jsonNewMessageReads

    val userId = request.m8UserAuth.userId
    val cacheKey = s"getChatMessages of $chatId by $userId"

    request.body.validate[M8UserNewMessage].toResponse(
      messageInfo => M8User.createNewMessage(userId, chatId, messageInfo) match {
        case Success(messageId) =>
          Cache.remove(cacheKey)
          MessageNotificationRouter ! M8UserMessageNotification(messageId)
          NoContent
        case Failure(ex) => JsonBadRequest(ex)
      }
    )
  }

  def getFriends = (MobileApiAuthAction andThen M8UserAuthAction) { request =>
    implicit val writer = M8User.jsonFriendListWrites

    M8User.getFriends(request.m8UserAuth.userId) match {
      case Success(friends) => Ok(Json.obj("friends" -> friends))
      case Failure(ex) => JsonBadRequest(ex)
    }
  }

  def leaveChat(chatId: Long) = (MobileApiAuthAction andThen M8UserAuthAction) { request =>
    M8User.leaveChat(request.m8UserAuth.userId, chatId) match {
      case Success(_) => NoContent
      case Failure(ex) => JsonBadRequest(ex)
    }
  }

  def hideChat(chatId: Long) = (MobileApiAuthAction andThen M8UserAuthAction) { request =>
    M8User.hideChat(request.m8UserAuth.userId, chatId) match {
      case Success(_) => NoContent
      case Failure(ex) => JsonBadRequest(ex)
    }
  }

  def getChats = (MobileApiAuthAction andThen M8UserAuthAction) { request =>
    implicit val writer = M8User.jsonChatListWrites

    M8User.getChats(request.m8UserAuth.userId) match {
      case Success(chats) => Ok(Json.obj("chats" -> chats))
      case Failure(ex) => JsonBadRequest(ex)
    }
  }

  def getChatMessages(chatId: Long) = (MobileApiAuthAction andThen M8UserAuthAction) { request =>
    implicit val writer = M8User.jsonMessageListWrites

    val userId = request.m8UserAuth.userId
    val cacheKey = s"getChatMessages of $chatId by $userId"

    Cache.getAs[List[M8UserMessageListItem]](cacheKey) match {
      case Some(messages) =>
        Ok(Json.obj("messages" -> messages))
      case None =>
        M8User.getChatMessages(userId, chatId) match {
          case Success(messages) =>
            Cache.set(cacheKey, messages, 10)
            Ok(Json.obj("messages" -> messages))
          case Failure(ex) => JsonBadRequest(ex)
        }
    }
  }

  def getChatOtherParticipants(chatId: Long) = (MobileApiAuthAction andThen M8UserAuthAction) { request =>
    M8User.getChatOtherParticipants(request.m8UserAuth.userId, chatId) match {
      case Success(participants) => Ok(Json.obj("otherParticipants" -> Json.toJson(participants)))
      case Failure(ex) => JsonBadRequest(ex)
    }
  }

  def byeUser(targetUserId: Long) = (MobileApiAuthAction andThen M8UserAuthAction) { request =>
    val userId = request.m8UserAuth.userId
    if (userId == targetUserId) {
      JsonBadRequest("Try to bye self")
    } else {
      M8User.byeUser(userId, targetUserId) match {
        case Success(_) => NoContent
        case Failure(ex) => JsonBadRequest(ex)
      }
    }
  }

  def blockUser(targetUserId: Long) = (MobileApiAuthAction andThen M8UserAuthAction) { request =>
    val userId = request.m8UserAuth.userId
    if (userId == targetUserId) {
      JsonBadRequest("Try to block self")
    } else {
      M8User.blockUser(userId, targetUserId) match {
        case Success(_) => NoContent
        case Failure(ex) => JsonBadRequest(ex)
      }
    }
  }

  def listAdvertsNearby(latitude: Option[Double],
                        longitude: Option[Double],
                        page: Int,
                        pageSize: Int) = (MobileApiAuthAction andThen M8UserAuthAction) { request =>
    implicit val writer = Advertiser.jsonAdvertListWrites

    M8User.listAdvertsNearby(request.m8UserAuth.userId, M8UserList(latitude, longitude, page, pageSize)) match {
      case Success(adverts) => Ok(Json.obj("adverts" -> adverts))
      case Failure(ex) => JsonBadRequest(ex)
    }
  }

  def forgotPassword = MobileApiAuthAction(parse.json) { request =>
    val identity = (request.body \ "identity").as[String]

    M8User.forgotPassword(identity) match {
      case Success(result) if result.updateCount > 0 && result.userId.isDefined =>
        ResetPasswordEmailRouter ! result
        NoContent
      case Success(result) if result.updateCount <= 0 =>
        JsonBadRequest("Username or email doesn't exist")
      case Failure(ex) => JsonBadRequest(ex)
    }
  }

  def resetPasswordPage(userId: Long,
                        resetDigest: String) = Action {
    M8User.getNameOfPasswordReset(userId, resetDigest) match {
      case Success(firstNameOpt) => Ok(views.html.mobile.resetPassword(firstNameOpt, None))
      case Failure(ex) => Ok(views.html.mobile.resetPassword(None, Some(ex.getMessage)))
    }
  }

  def resetPassword(userId: Long,
                    resetDigest: String) = Action(parse.json) { request =>
    implicit val reader = M8User.jsonNewPasswordReads

    request.body.validate[M8UserNewPassword].toResponse {
      newPassword => M8User.resetPassword(userId, resetDigest, newPassword) match {
        case Success(result) if result > 0 => NoContent
        case Success(result) if result <= 0 => JsonBadRequest("Cannot reset the password due to wrong parameters provided")
        case Failure(ex) => JsonBadRequest(ex)
      }
    }
  }

  def changePassword() = (MobileApiAuthAction andThen M8UserAuthAction)(parse.json) { request =>
    implicit val reader = M8User.jsonChangePasswordReads

    request.body.validate[M8UserChangePassword].toResponse {
      changeInfo => M8User.changePassword(request.m8UserAuth.userId, changeInfo) match {
        case Success(result) if result > 0 => Ok(Json.obj("result" -> "success"))
        case Success(result) if result <= 0 => Ok(Json.obj("result" -> "failure"))
        case Failure(ex) => JsonBadRequest(ex)
      }
    }
  }

  def getProfile = (MobileApiAuthAction andThen M8UserAuthAction) { request =>
    implicit val writer = M8User.jsonUserProfileWrites

    M8User.getProfile(request.m8UserAuth.userId) match {
      case Success(Some(result)) => Ok(Json.toJson(result))
      case Success(None) => JsonBadRequest("Cannot retrieve the current user's profile")
      case Failure(ex) => JsonBadRequest(ex)
    }
  }

  def updateProfile() = (MobileApiAuthAction andThen M8UserAuthAction)(parse.json(8388608)) { request =>
    implicit val reader = M8User.jsonUpdateReads

    request.body.validate[M8UserUpdate].toResponse {
      updateInfo => M8User.updateProfile(request.m8UserAuth.userId, updateInfo) match {
        case Success(result) if result > 0 => NoContent
        case Success(result) if result <= 0 => JsonBadRequest("Cannot update the profile. Please try again")
        case Failure(ex) => JsonBadRequest(ex)
      }
    }
  }

  def deleteAccount() = (MobileApiAuthAction andThen M8UserAuthAction) { request =>
    M8User.deleteAccount(request.m8UserAuth.userId) match {
      case Success(result) if result > 0 => NoContent
      case Success(result) if result <= 0 => JsonBadRequest("Cannot delete the account. Please try again")
      case Failure(ex) => JsonBadRequest(ex)
    }
  }

  def addAppleApnToken() = (MobileApiAuthAction andThen M8UserAuthAction)(parse.json) { request =>
    val token = (request.body \ "token").as[String]

    M8User.addAppleApnToken(request.m8UserAuth.userId, token) match {
      case Success(_) => NoContent
      case Failure(ex) => JsonBadRequest(ex)
    }
  }

  def addAndroidApnToken() = (MobileApiAuthAction andThen M8UserAuthAction)(parse.json) { request =>
    val token = (request.body \ "token").as[String]

    M8User.addAndroidApnToken(request.m8UserAuth.userId, token) match {
      case Success(_) => NoContent
      case Failure(ex) => JsonBadRequest(ex)
    }
  }
}
