package models.social

import java.nio.charset.Charset
import java.nio.file.{Files, Paths}
import java.sql.Connection

import anorm._
import models.advert.AdvertiserAdvertListItem
import org.apache.commons.codec.binary.Base64
import org.joda.time.DateTime
import play.api.cache.Cache
import play.api.data.validation.ValidationError
import play.api.libs.Codecs
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.db.DB
import play.api.Play.current
import utils._

import scala.util.Try
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Success, Failure}

/* Case class server self uses */
case class M8UserAuth(userId: Long,
                      email: String,
                      username: String,
                      blocked: Boolean)

case class M8UserNewChatResult(chatId: Long,
                               messageId: Long)

case class M8UserMessageNotification(messageId: Long)

/* Case classes inbound */
case class M8UserRegister(email: String,
                          username: String,
                          fbUserId: Option[String],
                          fbUsername: Option[String],
                          firstName: String,
                          plainPassword: String,
                          gender: String,
                          preferGender: String,
                          description: String,
                          pictures: Seq[String])

case class M8UserUpdate(email: String,
                        username: String,
                        firstName: String,
                        gender: String,
                        preferGender: String,
                        description: String,
                        pictures: Seq[Option[String]],
                        interests: Seq[String])

case class M8UserLogin(identity: String,
                       plainPassword: String)

case class M8UserPosition(latitude: Double,
                          longitude: Double)

case class M8UserList(latitude: Option[Double],
                      longitude: Option[Double],
                      page: Int,
                      pageSize: Int)

case class M8UserNewChat(recipients: Seq[Long],
                         message: String)

case class M8UserNewMessage(message: String)

case class M8UserNewPassword(plainPassword: String)

case class M8UserChangePassword(oldPlainPassword: String,
                                newPlainPassword: String)

/* Case classes outbound */
case class M8UserLoginResult(userId: Long,
                             accessToken: String,
                             gender: String,
                             fbUserId: Option[String],
                             fbUsername: Option[String])

case class M8UserRegisterResult(userId: Long,
                                accessToken: String)

case class M8UserListItem(userId: Long,
                          firstPictureUrl: String,
                          firstName: String,
                          distance: Int,
                          gender: String,
                          isFriend: Boolean,
                          fbUserId: Option[String],
                          fbUsername: Option[String])

case class M8UserProfileOfOther(userId: Long,
                                pictures: Seq[String],
                                username: String,
                                firstName: String,
                                gender: String,
                                distance: Int,
                                interests: Seq[String],
                                description: String,
                                authorizedAt: DateTime,
                                isFriend: Boolean,
                                fbUserId: Option[String],
                                fbUsername: Option[String])

case class M8UserFriendListItem(friendId: Long,
                                firstPictureUrl: String,
                                username: String,
                                firstName: String,
                                fbUserId: Option[String],
                                fbUsername: Option[String])

/* The field values of this class becomes vague during the requirements change */
/* The friendFirstPictureUrl, friendFbUserId and friendFbUsername are the OTHER recipient vs the current user, not always the sender's */
/* The firstName and gender are always relevant to the sender */
case class M8UserChatListItem(chatId: Long,
                              senderId: Long,
                              isSentByMe: Boolean,
                              friendFirstPictureUrl: String,
                              friendFbUserId: Option[String],
                              friendFbUsername: Option[String],
                              firstName: String,
                              gender: String,
                              lastMessage: String,
                              lastMessageAt: DateTime,
                              lastMessageIsRead: Boolean)

case class M8UserMessageListItem(senderId: Long,
                                 isSentByMe: Boolean,
                                 firstPictureUrl: String,
                                 fbUserId: Option[String],
                                 fbUsername: Option[String],
                                 firstName: String,
                                 gender: String,
                                 message: String,
                                 sentAt: DateTime)

case class M8UserForgotPassword(updateCount: Int,
                                userId: Option[Long],
                                email: Option[String],
                                resetDigest: Option[String])

case class M8UserProfile(email: String,
                         username: String,
                         firstName: String,
                         gender: String,
                         preferGender: String,
                         description: String,
                         pictures: Seq[String],
                         interests: Seq[String],
                         fbUserId: Option[String],
                         fbUsername: Option[String])

object M8User {
  val validGenders = List("Male", "Female")
  val validPrefGenders = List("Male", "Female", "Both")

  val jsonRegisterReads:Reads[M8UserRegister] = (
    (__ \ "email").read[String](maxLength[String](180))
      .filter(ValidationError("Invalid email address")) {
      case EmailMask(_) => true
      case _ => false
    } and
      (__ \ "username").read[String](minLength[String](3) andKeep maxLength[String](14))
        .filter(ValidationError("Username can only contain alphanumeric and underscore")) {
        case AlphanumMask(_) => true
        case _ => false
      } and
      (__ \ "fbUserId").readNullable[String] and
      (__ \ "fbUsername").readNullable[String] and
      (__ \ "firstName").read[String](maxLength[String](40)) and
      (__ \ "plainPassword").read[String](minLength[String](6)) and
      (__ \ "gender").read[String]
        .filter(ValidationError("Valid gender values: " + validGenders.mkString(", "))) {
        case gender: String if validGenders.contains(gender) => true
        case _ => false
      } and
      (__ \ "preferGender").read[String]
        .filter(ValidationError("Valid prefer gender values: " + validPrefGenders.mkString(", "))) {
        case gender:String if validPrefGenders.contains(gender) => true
        case _ => false
      } and
      (__ \ "description").read[String](maxLength[String](500)) and
      (__ \ "pictures").read[Seq[String]]
    )(M8UserRegister)

  val jsonUpdateReads:Reads[M8UserUpdate] = (
    (__ \ "email").read[String](maxLength[String](180))
      .filter(ValidationError("Invalid email address")) {
      case EmailMask(_) => true
      case _ => false
    } and
      (__ \ "username").read[String](minLength[String](3) andKeep maxLength[String](14))
        .filter(ValidationError("Username can only contain alphanumeric and underscore")) {
        case AlphanumMask(_) => true
        case _ => false
      } and
      (__ \ "firstName").read[String](maxLength[String](40)) and
      (__ \ "gender").read[String]
        .filter(ValidationError("Valid gender values: " + validGenders.mkString(", "))) {
        case gender: String if validGenders.contains(gender) => true
        case _ => false
      } and
      (__ \ "preferGender").read[String]
        .filter(ValidationError("Valid prefer gender values: " + validPrefGenders.mkString(", "))) {
        case gender:String if validPrefGenders.contains(gender) => true
        case _ => false
      } and
      (__ \ "description").read[String](maxLength[String](500)) and
      (__ \ "pictures").read[Seq[Option[String]]]
        .filter(ValidationError("Pictures array must contain exact 6 elements")) {
        case pictures:Seq[Option[String]] if pictures.length == 6 => true
        case _ => false
      } and
      (__ \ "interests").read[Seq[String]]
    )(M8UserUpdate)

  val jsonNewChatReads:Reads[M8UserNewChat] = (
    (__ \ "recipients").read[Seq[Long]] and
      (__ \ "message").read[String](maxLength[String](300))
  )(M8UserNewChat)

  val jsonNewMessageReads:Reads[M8UserNewMessage] =
    (__ \ "message").read[String](maxLength[String](300)).map(m => M8UserNewMessage(m))

  val jsonNewPasswordReads:Reads[M8UserNewPassword] =
    (__ \ "plainPassword").read[String](minLength[String](6)).map(p => M8UserNewPassword(p))

  val jsonChangePasswordReads:Reads[M8UserChangePassword] = (
    (__ \ "oldPlainPassword").read[String](minLength[String](6)) and
      (__ \ "newPlainPassword").read[String](minLength[String](6))
    )(M8UserChangePassword)

  val jsonLoginWrites:Writes[M8UserLoginResult] = Json.writes[M8UserLoginResult]
  val jsonRegisterWrites:Writes[M8UserRegisterResult] = Json.writes[M8UserRegisterResult]
  val jsonUserListWrites:Writes[M8UserListItem] = Json.writes[M8UserListItem]
  val jsonUserProfileOfOtherWrites:Writes[M8UserProfileOfOther] = Json.writes[M8UserProfileOfOther]
  val jsonUserProfileWrites:Writes[M8UserProfile] = Json.writes[M8UserProfile]
  val jsonFriendListWrites:Writes[M8UserFriendListItem] = Json.writes[M8UserFriendListItem]
  val jsonChatListWrites:Writes[M8UserChatListItem] = Json.writes[M8UserChatListItem]
  val jsonMessageListWrites:Writes[M8UserMessageListItem] = Json.writes[M8UserMessageListItem]

  val jsonLoginReads:Reads[M8UserLogin] = Json.reads[M8UserLogin]
  val jsonPositionReads:Reads[M8UserPosition] = Json.reads[M8UserPosition]

  def login(loginInfo: M8UserLogin):Try[Option[M8UserLoginResult]] = {
    Try(DB.withConnection { implicit c =>
      SQL(s"select * from social.login({identity}, {password})")
        .on('identity -> loginInfo.identity,
            'password -> Codecs.sha1(loginInfo.plainPassword))
        .apply().headOption match {
        case Some(row) =>
          Some(M8UserLoginResult(row[Long]("_user_id"),
            row[String]("_access_token"), row[String]("_gender"),
            row[Option[String]]("_fb_user_id"), row[Option[String]]("_fb_username")
          ))
        case None => None
      }
    })
  }

  def register(regInfo: M8UserRegister):Try[Option[M8UserRegisterResult]] = {
    val accessToken = java.util.UUID.randomUUID.toString
    Try(DB.withConnection { implicit c =>
        SQL(
          """
            |insert into social.m8_users (email, username, fb_user_id, fb_username, first_name, password, gender, prefer_gender, description, authorized_at, access_token)
            |values (lower({email}), {username}, {fbUserId}, {fbUsername}, {firstName}, {password}, {gender}::gender, {preferGender}::gender, {description}, current_timestamp, {accessToken})
          """.stripMargin
        ).on('email -> regInfo.email.trim,
            'username -> regInfo.username.trim,
            'fbUserId -> regInfo.fbUserId,
            'fbUsername -> regInfo.fbUsername,
            'firstName -> regInfo.firstName.trim,
            'password -> Codecs.sha1(regInfo.plainPassword),
            'gender -> regInfo.gender,
            'preferGender -> regInfo.preferGender,
            'description -> regInfo.description.trim,
            'accessToken -> accessToken
          ).executeInsert()
      } match {
        case Some(userId: Long) =>
          // Handle pictures

          // Create a sub-folder for the new m8user
          makeUploadFolder(userId)

          // Save all image files
          if (regInfo.pictures.length > 0) {
            val picPaths = regInfo.pictures.map(base64Data => {
              "'" + ServerNodeName + "/" + createImageFile(userId, base64Data).normalize().toString + "'"
            }) ++ Seq.fill(6)("''")

            DB.withConnection { implicit c =>
              SQL(s"update social.m8_users set pictures = array[${picPaths.mkString(",")}] where user_id = $userId")
                .executeUpdate()
            }
          }

          Some(M8UserRegisterResult(userId, accessToken))
        case _ => None
      }
    )
  }

  def checkEmailExists(email: String) = {
    DB.withConnection { implicit c =>
      SQL(s"select 1 from social.m8_users where lower(email) = lower('$email')")().headOption match {
        case Some(_) => true
        case None => false
      }
    }
  }

  def checkUsernameExists(username: String) = {
    DB.withConnection { implicit c =>
      SQL(s"select 1 from social.m8_users where lower(username) = lower('$username')")().headOption match {
        case Some(_) => true
        case None => false
      }
    }
  }

  def checkFbAccountExists(fbUserId: String,
                           fbUsername: String) = {
    DB.withConnection { implicit c =>
      SQL(s"select 1 from social.m8_users where fb_user_id = '$fbUserId' or fb_username = '$fbUsername'")().headOption match {
        case Some(_) => true
        case None => false
      }
    }
  }

  def updatePosition(userId: Long,
                     posInfo: M8UserPosition):Try[Int] = {
    Try(DB.withConnection { implicit c =>
      updatePositionHelper(userId, posInfo)
    })
  }

  def updateInterests(userId: Long,
                      interests: Set[String]):Try[Int] = {
    Try(DB.withConnection { implicit c =>
      SQL(
        s"""
           |update social.m8_users
           |set interests = array[${interests.map("'" + _.toLowerCase + "'").mkString(",")}]
           |where user_id = $userId
         """.stripMargin)
        .executeUpdate()
    })
  }

  def listUsersNearby(userId: Long,
                      listInfo: M8UserList):Try[List[M8UserListItem]] = {
    Try(DB.withConnection { implicit c =>
      // If there are position information uploaded, we should update the current user's position firstly
      if (listInfo.latitude.isDefined && listInfo.longitude.isDefined) {
        updatePositionHelper(userId, M8UserPosition(listInfo.latitude.get, listInfo.longitude.get))
      }

      SQL(s"select * from social.list_users_nearby($userId, ${listInfo.page}, ${listInfo.pageSize})")
        .apply()
        .map(row =>
          M8UserListItem(row[Long]("_user_id"),
            row[String]("_first_picture"), row[String]("_first_name"),
            if (row[Int]("_distance") == 0) 1 else row[Int]("_distance"),
            row[String]("_gender"), row[Boolean]("_is_friend"),
            row[Option[String]]("_fb_user_id"), row[Option[String]]("_fb_username"))
      ).toList
    })
  }

  def searchUsers(userId: Long,
                  criteria: String,
                  listInfo: M8UserList):Try[List[M8UserListItem]] = {
    Try(DB.withConnection { implicit c =>
      if (listInfo.latitude.isDefined && listInfo.longitude.isDefined) {
        updatePositionHelper(userId, M8UserPosition(listInfo.latitude.get, listInfo.longitude.get))
      }

      SQL(s"select * from social.search_users($userId, {criteria}, ${listInfo.page}, ${listInfo.pageSize})")
        .on('criteria -> criteria)
        .apply()
        .map(row =>
          M8UserListItem(row[Long]("_user_id"),
            row[String]("_first_picture"), row[String]("_first_name"),
            if (row[Int]("_distance") == 0) 1 else row[Int]("_distance"),
            row[String]("_gender"), row[Boolean]("_is_friend"),
            row[Option[String]]("_fb_user_id"), row[Option[String]]("_fb_username"))
        ).toList
    })
  }

  def getOtherUserProfile(userId: Long,
                          otherUserId: Long):Try[Option[M8UserProfileOfOther]] = {
    Try(DB.withConnection { implicit c =>
      SQL(s"select * from social.get_other_user_profile($userId, $otherUserId)")().headOption match {
        case Some(row) => Some(M8UserProfileOfOther(row[Long]("_other_user_id"),
          row[Array[String]]("_pictures"), row[String]("_username"), row[String]("_first_name"), row[String]("_gender"),
          if (row[Int]("_distance") == 0) 1 else row[Int]("_distance"),
          row[Array[String]]("_interests"), row[String]("_description"),
          row[DateTime]("_authorized_at"), row[Boolean]("_is_friend"),
          row[Option[String]]("_fb_user_id"), row[Option[String]]("_fb_username")))
        case None => None
      }
    })
  }

  def authenticate(accessToken: String):Try[Option[M8UserAuth]] = {
    Try(
      Cache.getAs[M8UserAuth](accessToken) match {
        case Some(authInfo) => Some(authInfo)
        case None =>
          DB.withConnection { implicit c =>
            SQL(s"select * from social.authenticate({accessToken})")
              .on('accessToken -> accessToken)
              .apply().headOption match {
              case Some(row) =>
                val authInfo = M8UserAuth(row[Long]("_user_id"),
                  row[String]("_email"), row[String]("_username"), row[Boolean]("_blocked"))
                Cache.set(accessToken, authInfo, 10 seconds)
                Some(authInfo)
              case None =>
                None
            }
        }
    })
  }

  def makeUploadFolder(userId: Long) = {
    Files.createDirectories(Paths.get(uploadFolder(userId)))
  }

  def createNewChat(userId: Long,
                    chatInfo: M8UserNewChat):Try[M8UserNewChatResult] = {
    Try(DB.withConnection { implicit c =>
      SQL(s"select * from social.new_chat($userId, array[${chatInfo.recipients.mkString(",")}], {message})")
        .on('message -> chatInfo.message)
        .apply()
        .map(row => M8UserNewChatResult(row[Long]("_chat_id"), row[Long]("_new_message_id")))
        .head
    })
  }

  def createNewMessage(userId: Long,
                       chatId: Long,
                       messageInfo: M8UserNewMessage):Try[Long] = {
    Try(DB.withConnection { implicit c =>
      SQL(s"select * from social.new_message($userId, $chatId, {message})")
        .on('message -> messageInfo.message)
        .apply()
        .map(row => row[Long]("_message_id"))
        .head
    })
  }

  def getChats(userId: Long):Try[List[M8UserChatListItem]] = {
    Try(DB.withConnection { implicit c =>
      SQL(s"select * from social.get_chats($userId)")
        .apply()
        .map(row =>
          M8UserChatListItem(row[Long]("_chat_id"), row[Long]("_sender_id"), row[Long]("_sender_id") == userId,
            row[String]("_first_picture"), row[Option[String]]("_fb_user_id"), row[Option[String]]("_fb_username"),
            row[String]("_first_name"), row[String]("_gender"),
            row[String]("_last_message"), row[DateTime]("_last_message_at"),
            row[Boolean]("_last_message_is_read"))
        ).toList
    })
  }

  def getChatMessages(userId: Long,
                      chatId: Long):Try[List[M8UserMessageListItem]] = {
    Try(DB.withConnection { implicit c =>
      SQL(s"select * from social.get_chat_messages($userId, $chatId, 50)")
        .apply()
        .map(row =>
          M8UserMessageListItem(row[Long]("_sender_id"), row[Long]("_sender_id") == userId,
            row[String]("_first_picture"), row[Option[String]]("_fb_user_id"), row[Option[String]]("_fb_username"),
            row[String]("_first_name"), row[String]("_gender"),
            row[String]("_message"), row[DateTime]("_sent_at"))).toList
    })
  }

  def getChatOtherParticipants(userId: Long,
                               chatId: Long):Try[Array[Long]] = {
    Try(DB.withConnection { implicit c =>
      SQL(s"select participants - $userId as _other_participants from social.chats where chat_id = $chatId")
        .apply()
        .map(row => row[Array[Long]]("_other_participants"))
        .head
    })
  }

  def leaveChat(userId: Long,
                chatId: Long):Try[Int] = {
    Try(DB.withConnection { implicit c =>
      SQL(s"select * from social.leave_chat($userId, $chatId)")
        .apply().map(row => row[Int]("_update_count")).head
    })
  }

  def hideChat(userId: Long,
               chatId: Long):Try[Int] = {
    Try(DB.withConnection { implicit c =>
      SQL(s"select * from social.hide_chat($userId, $chatId)")
        .apply().map(row => row[Int]("_update_count")).head
    })
  }

  def getFriends(userId: Long):Try[List[M8UserFriendListItem]] = {
    Try(DB.withConnection { implicit c =>
      SQL(s"select * from social.get_friends($userId)")
        .apply()
        .map(row =>
          M8UserFriendListItem(row[Long]("_friend_id"),
            row[String]("_first_picture"), row[String]("_username"),
            row[String]("_first_name"), row[Option[String]]("_fb_user_id"), row[Option[String]]("_fb_username")
          )
        ).toList
    })
  }

  def byeUser(userId: Long,
              targetUserId: Long):Try[Unit] = {
    Try(DB.withConnection { implicit c =>
      SQL(s"select social.bye_user($userId, $targetUserId)")
        .executeQuery()
    })
  }

  def blockUser(userId: Long,
                targetUserId: Long):Try[Unit] = {
    Try(DB.withConnection { implicit c =>
      SQL(s"select social.block_user($userId, $targetUserId)")
        .executeQuery()
    })
  }

  def listAdvertsNearby(userId: Long,
                        listInfo: M8UserList):Try[List[AdvertiserAdvertListItem]] = {
    Try(DB.withConnection { implicit c =>
      if (listInfo.latitude.isDefined && listInfo.longitude.isDefined) {
        updatePositionHelper(userId, M8UserPosition(listInfo.latitude.get, listInfo.longitude.get))
      }

      SQL(s"select * from advert.list_adverts_nearby($userId, ${listInfo.page}, ${listInfo.pageSize})")
        .apply()
        .map(row => AdvertiserAdvertListItem(row[Long]("_advert_id"),
          row[String]("_business_name"), row[String]("_first_photo"),
          row[String]("_plan_name"))).toList
    })
  }

  def forgotPassword(identity: String):Try[M8UserForgotPassword] = {
    Try(DB.withConnection { implicit c =>
      SQL(s"select * from social.forgot_password({identity}, {secret})")
        .on('identity -> identity,
            'secret -> AppSecret)
        .apply()
        .map(row => M8UserForgotPassword(row[Int]("_update_count"),
          row[Option[Long]]("_user_id"), row[Option[String]]("_email"),
          row[Option[String]]("_reset_digest"))).head
    })
  }

  def getNameOfPasswordReset(userId: Long,
                             resetDigest: String):Try[Option[String]] = {
    Try(DB.withConnection { implicit c =>
      SQL(s"select social.get_name_of_password_reset($userId, {secret}, {resetDigest}) as _first_name")
        .on('secret -> AppSecret,
            'resetDigest -> resetDigest)
        .apply().headOption match {
        case Some(row) => row[Option[String]]("_first_name")
        case None => None
      }
    })
  }

  def resetPassword(userId: Long,
                    resetDigest: String,
                    newPassword: M8UserNewPassword):Try[Int] = {
    Try(DB.withConnection { implicit c =>
      SQL(s"select * from social.reset_password($userId, {secret}, {resetDigest}, {newPassword})")
        .on('secret -> AppSecret,
            'resetDigest -> resetDigest,
            'newPassword -> Codecs.sha1(newPassword.plainPassword))
        .apply().map(row => row[Int]("_update_count")).head
    })
  }

  def changePassword(userId: Long,
                     changeInfo: M8UserChangePassword):Try[Int] = {
    Try(DB.withConnection { implicit c =>
      SQL(s"select * from social.change_password($userId, {oldPassword}, {newPassword})")
        .on('oldPassword -> Codecs.sha1(changeInfo.oldPlainPassword),
            'newPassword -> Codecs.sha1(changeInfo.newPlainPassword)
        ).apply().map(row => row[Int]("_update_count")).head
    })
  }

  def getProfile(userId: Long):Try[Option[M8UserProfile]] = {
    Try(DB.withConnection { implicit c =>
      SQL(s"select * from social.get_profile($userId)")
        .apply().map(row => M8UserProfile(row[String]("_email"),
        row[String]("_username"), row[String]("_first_name"),
        row[String]("_gender"), row[String]("_prefer_gender"),
        row[String]("_description"), row[Array[String]]("_pictures"),
        row[Array[String]]("_interests"), row[Option[String]]("_fb_user_id"), row[Option[String]]("_fb_username")
      )).headOption
    })
  }

  def updateProfile(userId: Long,
                    updateInfo: M8UserUpdate):Try[Int] = {
    makeUploadFolder(userId)

    val picPaths = updateInfo.pictures.map {
      case Some(data) if data.length > 0 =>
        "'" + ServerNodeName + "/" + createImageFile(userId, data).normalize().toString + "'"
      case None => "NULL"
      case _ => "''"
    }

    Try(DB.withConnection { implicit c =>
      SQL(s"select * from social.update_profile($userId, lower({email}), {username}, {firstName}, {gender}, {preferGender}, {description}, array[${picPaths.mkString(",")}], array[${updateInfo.interests.map("'" + _.toLowerCase + "'").mkString(",")}])")
        .on('email -> updateInfo.email.trim,
            'username -> updateInfo.username.trim,
            'firstName -> updateInfo.firstName.trim,
            'gender -> updateInfo.gender,
            'preferGender -> updateInfo.preferGender,
            'description -> updateInfo.description.trim
        ).apply().map(row => row[Int]("_update_count")).head
    })
  }

  def deleteAccount(userId: Long):Try[Int] = {
    Try(DB.withConnection { implicit c =>
      SQL(s"select * from social.delete_account($userId)")
        .apply().map(row => row[Int]("_update_count")).head
    })
  }

  def addAppleApnToken(userId: Long,
                       token: String):Try[Int] = {
    Try(DB.withConnection { implicit c =>
      if (token.length <= 0) throw new Exception("Invalid apn token")

      SQL(s"select * from social.add_apple_apn_token($userId, {token})")
        .on('token -> token)
        .apply().map(row => row[Int]("_update_count")).head
    })
  }

  def addAndroidApnToken(userId: Long,
                         token: String):Try[Int] = {
    Try(DB.withConnection { implicit c =>
      if (token.length <= 0) throw new Exception("Invalid apn token")

      SQL(s"select * from social.add_android_apn_token($userId, {token})")
        .on('token -> token)
        .apply().map(row => row[Int]("_update_count")).head
    })
  }

  private def uploadFolder(userId: Long) = {
    UploadRootPath + s"m8user-${userId.toString}"
  }

  private def createImageFile(userId: Long,
                              base64Data: String) = {
    Files.write(Paths.get(uploadFolder(userId), s"${java.util.UUID.randomUUID.toString}.jpg"),
      Base64.decodeBase64(base64Data.getBytes(Charset.forName("UTF-8"))))
  }

  private def updatePositionHelper(userId: Long,
                                   posInfo: M8UserPosition)(implicit c:Connection): Int = {
    SQL(
      s"""
         |update social.m8_users
         |set position = ST_GeogFromText('SRID=4326;POINT(${posInfo.longitude} ${posInfo.latitude})')
         |where user_id = $userId
       """.stripMargin)
      .executeUpdate()
  }
}
