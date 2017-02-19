package models.advert

import java.io.File
import java.nio.file.Paths

import akka.actor.ReceiveTimeout
import anorm._
import org.apache.commons.io.FilenameUtils
import org.joda.time.DateTime
import play.api.data.validation.ValidationError
import play.api.mvc.MultipartFormData
import play.api.db.DB
import play.api.libs.{Files, Codecs}
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.functional.syntax._
import play.api.Play.current

import scala.concurrent.Future
import scala.util.{Success, Try}

import utils._
import utils.advert._

/* Case class server self uses */
case class AdvertiserRegisterConfirm(advertiserId: Long,
                                     name: String,
                                     email: String,
                                     emailConfirmToken: String)

case class AdvertiserAuth(advertiserId: Long,
                          name: String,
                          email: String,
                          photoLimit: Int)

case class AdvertiserEmailConfirmResult(updateCount: Long,
                                        accessToken: String)

case class AdvertiserStripeChargeFaild(name: String,
                                       email: String,
                                       error: Option[String])

/* Case classes inbound */
case class AdvertiserRegister(name: String,
                              companyName: String,
                              email: String,
                              plainPassword: String,
                              planName: String)

case class AdvertiserLogin(email: String,
                           plainPassword: String)

case class AdvertiserBusinessUpdate(businessName: String,
                                    contactNumber: Option[String],
                                    website: Option[String],
                                    address: String,
                                    latitude: Double,
                                    longitude: Double,
                                    description: String)

case class AdvertiserAccountUpdate(name: String,
                                   companyName: String,
                                   email: String,
                                   plainPassword: Option[String])

case class AdvertiserNewPassword(plainPassword: String)

case class AdvertiserPaymentMethodUpdate(paymentMethod: String,
                                         cardToken: Option[String])

/* Case classes outbound */
case class AdvertiserRegisterResult(advertiserId: Long,
                                    emailConfirmToken: String)

case class AdvertiserLoginResult(status: String,
                                 accessToken: Option[String])

case class AdvertiserBusiness(businessName: String,
                              contactNumber: Option[String],
                              website: Option[String],
                              address: String,
                              description: String,
                              photoLimit: Int,
                              photos: Seq[String],
                              activeUtil: Option[DateTime],
                              status: String,
                              suspendedReason: Option[String],
                              issuedBill: Boolean,
                              subscribed: Boolean,
                              freeSubscription: Boolean)

case class AdvertiserAdvertListItem(advertId: Long,
                                    businessName: String,
                                    firstPhotoUrl: String,
                                    planName: String)

case class AdvertiserAdvert(businessName: String,
                            photos: Seq[String],
                            description: String,
                            phone: Option[String],
                            email: String,
                            website: Option[String],
                            address: String)

case class AdvertiserAccountDetails(name: String,
                                    companyName: String,
                                    email: String,
                                    issuedBill: Boolean,
                                    subscribed: Boolean,
                                    freeSubscription: Boolean)

case class AdvertiserPlan(name: String,
                          planName: String,
                          issuedBill: Boolean,
                          subscribed: Boolean,
                          freeSubscription: Boolean)

case class AdvertiserPaymentMethod(name: String,
                                   paymentMethod: String,
                                   hasCreditCard: Boolean,
                                   issuedBill: Boolean,
                                   subscribed: Boolean,
                                   freeSubscription: Boolean)

case class AdvertiserForgotPassword(updateCount: Int,
                                    advertiserId: Option[Long],
                                    email: Option[String],
                                    resetDigest: Option[String])

// Advertiser api is only used internally, so we depends on the database to check some data's valid.
// Most of those data are only set by the code or by the admin and are not controlled by the advertiser.
object Advertiser {
  val validPaymentMethods = List("manual", "subscription")

  val jsonRegisterReads:Reads[AdvertiserRegister] = (
    (__ \ "name").read[String](maxLength[String](80)) and
      (__ \ "companyName").read[String](maxLength[String](100)) and
      (__ \ "email").read[String](maxLength[String](180))
        .filter(ValidationError("Invalid email address")) {
        case EmailMask(_) => true
        case _ => false
      } and
      (__ \ "plainPassword").read[String](minLength[String](6)) and
      (__ \ "planName").read[String]
    )(AdvertiserRegister)

  val jsonLoginReads:Reads[AdvertiserLogin] = (
    (__ \ "email").read[String](maxLength[String](180))
      .filter(ValidationError("Invalid email address")) {
      case EmailMask(_) => true
      case _ => false
    } and
      (__ \ "plainPassword").read[String](minLength[String](6))
    )(AdvertiserLogin)

  val jsonBusinessReads:Reads[AdvertiserBusinessUpdate] = (
    (__ \ "businessName").read[String](maxLength[String](100)) and
      (__ \ "contactNumber").readNullable[String](maxLength[String](60)) and
      (__ \ "website").readNullable[String] and
      (__ \ "address").read[String] and
      (__ \ "latitude").read[Double] and
      (__ \ "longitude").read[Double] and
      (__ \ "description").read[String](maxLength[String](500))
    )(AdvertiserBusinessUpdate)

  val jsonAccountReads:Reads[AdvertiserAccountUpdate] = (
    (__ \ "name").read[String](maxLength[String](80)) and
      (__ \ "companyName").read[String](maxLength[String](100)) and
      (__ \ "email").read[String](maxLength[String](180))
        .filter(ValidationError("Invalid email address")) {
        case EmailMask(_) => true
        case _ => false
      } and
      (__ \ "plainPassword").readNullable[String](minLength[String](6))
    )(AdvertiserAccountUpdate)

  val jsonNewPasswordReads:Reads[AdvertiserNewPassword] =
    (__ \ "plainPassword").read[String](minLength[String](6)).map(p => AdvertiserNewPassword(p))

  val jsonPaymentMethodReads:Reads[AdvertiserPaymentMethodUpdate] = (
    (__ \ "paymentMethod").read[String]
      .filter(ValidationError("Valid payment methods: " + validPaymentMethods.mkString(", "))) {
      case method: String if validPaymentMethods.contains(method) => true
      case _ => false
    } and
      (__ \ "cardToken").readNullable[String]
    )(AdvertiserPaymentMethodUpdate)

  val jsonRegisterWrites:Writes[AdvertiserRegisterResult] = Json.writes[AdvertiserRegisterResult]
  val jsonLoginWrites:Writes[AdvertiserLoginResult] = Json.writes[AdvertiserLoginResult]
  val jsonAdvertListWrites:Writes[AdvertiserAdvertListItem] = Json.writes[AdvertiserAdvertListItem]
  val jsonAdvertWrites:Writes[AdvertiserAdvert] = Json.writes[AdvertiserAdvert]
  val jsonCreditCardWrites:Writes[AdvertiserCreditCard] = Json.writes[AdvertiserCreditCard]

  def register(regInfo: AdvertiserRegister):Try[Option[AdvertiserRegisterResult]] = {
    Try(DB.withTransaction { implicit c =>
      val emailConfirmToken = java.util.UUID.randomUUID.toString
      val planConfig = regInfo.planName.toLowerCase match {
        case "bronze" => Plan.bronze
        case "silver" => Plan.silver
        case _ => throw new Exception("Invalid advert plan when registering")
      }

      val advertiserIdOption:Option[Long] = SQL(
        """
          |insert into advert.advertisers (name, company_name, email, password, plan_name, price, listing_days, photo_limit, priority, email_confirm_token)
          |values ({name}, {companyName}, lower({email}), {password}, {planName}::plan_name, {price}, {listingDays}::listing_days, {photoLimit}, {priority}, {emailConfirmToken})
        """.stripMargin
      ).on('name -> regInfo.name.trim,
           'companyName -> regInfo.companyName.trim,
           'email -> regInfo.email.trim,
           'password -> Codecs.sha1(regInfo.plainPassword),
           'planName -> regInfo.planName,
           'price -> planConfig.getDouble("price").get,
           'listingDays -> planConfig.getString("listingDays").get,
           'photoLimit -> planConfig.getInt("photoLimit").get,
           'priority -> planConfig.getInt("priority").get,
           'emailConfirmToken -> emailConfirmToken
        ).executeInsert()

      advertiserIdOption match {
        case Some(advertiserId) => Some(AdvertiserRegisterResult(advertiserId, emailConfirmToken))
        case None => None
      }
    })
  }

  def login(loginInfo: AdvertiserLogin):Try[Option[AdvertiserLoginResult]] = {
    Try(DB.withTransaction { implicit c =>
      SQL(s"select * from advert.login({email}, {password})")
        .on('email -> loginInfo.email,
            'password -> Codecs.sha1(loginInfo.plainPassword))
        .apply().headOption match {
        case Some(row) if row[String]("_status") == "confirmed" || row[String]("_status") == "active" =>
          Some(AdvertiserLoginResult(row[String]("_status"), Some(row[String]("_access_token"))))
        case Some(row) if row[String]("_status") != "confirmed" && row[String]("_status") != "active" =>
          Some(AdvertiserLoginResult(row[String]("_status"), None))
        case None => None
      }
    })
  }

  def logout(advertiserId: Long):Try[Int] = {
    Try(DB.withTransaction { implicit c =>
      SQL(
        s"""
           |update advert.advertisers
           |set access_token = DEFAULT, authorized_at = DEFAULT
           |where advertiser_id = $advertiserId
        """.stripMargin)
        .executeUpdate()
    })
  }

  def confirmEmail(advertiserId: Long,
                   emailConfirmToken: String):Try[AdvertiserEmailConfirmResult] = {
    Try(DB.withTransaction { implicit c =>
      SQL(s"select * from advert.confirm_email($advertiserId, {emailConfirmToken})")
        .on('emailConfirmToken -> emailConfirmToken)
        .apply().map(row =>
          AdvertiserEmailConfirmResult(row[Int]("_update_count"), row[String]("_access_token"))).head
    })
  }

  def checkEmailExists(email: String) = {
    DB.withTransaction { implicit c =>
      SQL(s"select 1 from advert.advertisers where email = lower('$email')")().headOption match {
        case Some(_) => true
        case None => false
      }
    }
  }

  def authenticate(accessToken: String):Try[Option[AdvertiserAuth]] = {
    Try(DB.withTransaction { implicit c =>
      SQL(s"select * from advert.authenticate({accessToken})")
        .on('accessToken -> accessToken)
        .apply().headOption match {
        case Some(row) => Some(AdvertiserAuth(row[Long]("_advertiser_id"), row[String]("_name"), row[String]("_email"), row[Int]("_photo_limit")))
        case None => None
      }
    })
  }

  def updateBusiness(advertiserId: Long,
                     businessInfo: AdvertiserBusinessUpdate):Try[Option[Long]] = {
    Try(DB.withTransaction { implicit c =>
      SQL(
        s"""
           |update advert.adverts
           |set business_name = {businessName}, contact_number = {contactNumber}, website = {website}, address = {address}, position = ST_GeogFromText('SRID=4326;POINT(${businessInfo.longitude} ${businessInfo.latitude})'), description = {description}
           |where advertiser_id = $advertiserId
         """.stripMargin)
        .on('businessName -> businessInfo.businessName.trim,
            'contactNumber -> businessInfo.contactNumber.map(_.trim),
            'website -> businessInfo.website.map(_.trim),
            'address -> businessInfo.address.trim,
            'description -> businessInfo.description.trim)
        .executeUpdate() match {
        case 0 =>
          SQL(
            s"""
               |insert into advert.adverts (advertiser_id, business_name, contact_number, website, address, position, description)
               |values ($advertiserId, {businessName}, {contactNumber}, {website}, {address}, ST_GeogFromText('SRID=4326;POINT(${businessInfo.longitude} ${businessInfo.latitude})'), {description})
             """.stripMargin)
            .on('businessName -> businessInfo.businessName.trim,
              'contactNumber -> businessInfo.contactNumber.map(_.trim),
              'website -> businessInfo.website.map(_.trim),
              'address -> businessInfo.address.trim,
              'description -> businessInfo.description.trim)
            .executeInsert()
        case 1 => Some(1)
        case _ => None
      }
    })
  }

  def getBusiness(advertiserId: Long):Try[Option[AdvertiserBusiness]] = {
    Try(DB.withTransaction { implicit c =>
      SQL(s"select * from advert.get_business($advertiserId)")
        .apply().headOption match {
        case Some(row) => Some(AdvertiserBusiness(row[String]("_business_name"),
          row[Option[String]]("_contact_number"), row[Option[String]]("_website"),
          row[String]("_address"), row[String]("_description"),
          row[Int]("_photo_limit"), row[Array[String]]("_photos"),
          row[Option[DateTime]]("_active_util"), row[String]("_status"),
          row[Option[String]]("_suspended_reason"), row[Boolean]("_issued_bill"),
          row[Boolean]("_subscribed"), row[Boolean]("_free_subscription")))
        case None => None
      }
    })
  }

  // Note we follow the PostgreSQL array convention, and the photoIndex starts from 1
  // We allow maximum 20 advert photos, please see the default value for "photos" field in advert.adverts table
  def updatePhoto(advertiserId: Long,
                  photoLimit: Int,
                  photoIndex: Int,
                  photo: MultipartFormData.FilePart[Files.TemporaryFile]):Try[String] = {
    if (photoLimit < 0) throw new Exception("Invalid photo limit")
    if (photoIndex < 1 || photoIndex > 20) throw new Exception("Invalid photo index")
    if (photoIndex > photoLimit) throw new Exception("Exceeding the photo limit")

    val fileExtension = FilenameUtils.getExtension(photo.filename)
    if (!ImageFileExtensions.contains(fileExtension)) {
      throw new Exception(s"Invalid image file extension. Valid value: ${ImageFileExtensions.mkString(", ")}")
    }

    java.nio.file.Files.createDirectories(Paths.get(uploadFolder(advertiserId)))

    val photoPath = Paths.get(uploadFolder(advertiserId),
      s"${java.util.UUID.randomUUID.toString}.$fileExtension")
      .normalize().toString
    photo.ref.moveTo(new File(photoPath), replace = true)

    val fullPhotoPath = ServerNodeName + "/" + photoPath
    Try(DB.withTransaction { implicit c =>
      SQL(
        s"""
           |select photos[$photoIndex] as photo_path
           |from advert.adverts
           |where advertiser_id = $advertiserId
         """.stripMargin)().headOption match {
        case Some(row) =>
          deletePhotoFromDisk(advertiserId, row[String]("photo_path"))

          SQL(
            s"""
               |update advert.adverts
               |set photos[$photoIndex] = '$fullPhotoPath'
               |where advertiser_id = $advertiserId
             """.stripMargin).executeUpdate()
          fullPhotoPath
        case None =>
          throw new Exception("Cannot find relevant business information")
      }
    })
  }

  def deletePhoto(advertiserId: Long,
                  photoIndex: Int):Try[Int] = {
    Try(DB.withTransaction { implicit c =>
      if (photoIndex < 1 || photoIndex > 20) throw new Exception("Invalid photo index")

      SQL(
        s"""
           |select photos[$photoIndex] as photo_path
           |from advert.adverts
           |where advertiser_id = $advertiserId
         """.stripMargin)().headOption match {
        case Some(row) =>
          deletePhotoFromDisk(advertiserId, row[String]("photo_path"))

          SQL(
            s"""
               |update advert.adverts
               |set photos[$photoIndex] = ''
               |where advertiser_id = $advertiserId
             """.stripMargin).executeUpdate()
        case None =>
          throw new Exception("Cannot find relevant business information")
      }
    })
  }

  def getAdvert(advertId: Long):Try[Option[AdvertiserAdvert]] = {
    Try(DB.withTransaction { implicit c =>
      SQL(s"select * from advert.get_single_advert($advertId)")().headOption match {
        case Some(row) => Some(AdvertiserAdvert(row[String]("_business_name"),
          row[Array[String]]("_photos"), row[String]("_description"),
          row[Option[String]]("_phone"), row[String]("_email"),
          row[Option[String]]("_website"), row[String]("_address")
        ))
        case None => None
      }
    })
  }

  def getAccountDetails(advertiserId: Long):Try[Option[AdvertiserAccountDetails]] = {
    Try(DB.withTransaction { implicit c =>
      SQL(s"select name::TEXT, company_name::TEXT, email::TEXT, EXISTS(SELECT 1 FROM advert.get_latest_bill($advertiserId) WHERE _bill_status = 'issued') AS issued_bill, payment_method = 'subscription'::payment_method AS subscribed, NOT price > 0 AS free_subscription from advert.advertisers where advertiser_id = $advertiserId")
        .apply().headOption match {
        case Some(row) => Some(AdvertiserAccountDetails(row[String]("name"),
          row[String]("company_name"), row[String]("email"),
          row[Boolean]("issued_bill"), row[Boolean]("subscribed"),
          row[Boolean]("free_subscription")))
        case None => None
      }
    })
  }

  def updateAccount(advertiserId: Long,
                    accountInfo: AdvertiserAccountUpdate):Try[Int] = {
    Try(DB.withTransaction { implicit c =>
      accountInfo.plainPassword match {
        case Some(plainPassword) =>
          SQL(
            s"""
            |update advert.advertisers
            |set name = {name}, company_name = {companyName}, email = {email}, password = {password}
            |where advertiser_id = $advertiserId
            """.stripMargin)
            .on('name -> accountInfo.name,
                'companyName -> accountInfo.companyName,
                'email -> accountInfo.email,
                'password -> Codecs.sha1(plainPassword))
            .executeUpdate()
        case None =>
          SQL(
            s"""
            |update advert.advertisers
            |set name = {name}, company_name = {companyName}, email = {email}
            |where advertiser_id = $advertiserId
            """.stripMargin)
            .on('name -> accountInfo.name,
                'companyName -> accountInfo.companyName,
                'email -> accountInfo.email)
            .executeUpdate()
      }
    })
  }

  def forgotPassword(email: String):Try[AdvertiserForgotPassword] = {
    Try(DB.withTransaction { implicit c =>
      SQL(s"select * from advert.forgot_password({email}, {secret})")
        .on('email -> email,
            'secret -> AppSecret)
        .apply()
        .map(row => AdvertiserForgotPassword(row[Int]("_update_count"),
          row[Option[Long]]("_advertiser_id"), row[Option[String]]("_email"),
          row[Option[String]]("_reset_digest"))).head
    })
  }

  def getNameOfPasswordReset(advertiserId: Long,
                             resetDigest: String):Try[Option[String]] = {
    Try(DB.withTransaction { implicit c =>
      SQL(s"select advert.get_name_of_password_reset($advertiserId, {secret}, {resetDigest}) as _name")
        .on('secret -> AppSecret,
            'resetDigest -> resetDigest)
        .apply().headOption match {
        case Some(row) => row[Option[String]]("_name")
        case None => None
      }
    })
  }

  def resetPassword(advertiserId: Long,
                    resetDigest: String,
                    newPassword: AdvertiserNewPassword):Try[Int] = {
    Try(DB.withTransaction { implicit c =>
      SQL(s"select * from advert.reset_password($advertiserId, {secret}, {resetDigest}, {newPassword})")
        .on('secret -> AppSecret,
            'resetDigest -> resetDigest,
            'newPassword -> Codecs.sha1(newPassword.plainPassword))
        .apply().map(row => row[Int]("_update_count")).head
    })
  }

  def getPlan(advertiserId: Long):Try[Option[AdvertiserPlan]] = {
    Try(DB.withTransaction { implicit c =>
      SQL(s"select name, plan_name::TEXT, EXISTS(SELECT 1 FROM advert.get_latest_bill($advertiserId) WHERE _bill_status = 'issued') AS issued_bill, payment_method = 'subscription'::payment_method AS subscribed, NOT price > 0 AS free_subscription from advert.advertisers where advertiser_id = $advertiserId")
        .apply()
        .map(row => AdvertiserPlan(row[String]("name"),
          row[String]("plan_name"), row[Boolean]("issued_bill"),
          row[Boolean]("subscribed"), row[Boolean]("free_subscription"))
        ).headOption
    })
  }

  def changePlan(advertiserId: Long,
                 planName: String):Try[Unit] = {
    val planConfig = planName match {
      case "bronze" => Plan.bronze
      case "silver" => Plan.silver
      case _ => throw new Exception("Invalid advert plan when changing the plan")
    }

    Try(DB.withTransaction { implicit c =>
      SQL(s"select advert.change_plan($advertiserId, {planName}, {price}, {photoLimit}, {priority})")
        .on('planName -> planName,
            'price -> BigDecimal(planConfig.getDouble("price").get),
            'photoLimit -> planConfig.getInt("photoLimit").get,
            'priority -> planConfig.getInt("priority").get)
        .executeQuery()
    })
  }

  def getPaymentMethod(advertiserId: Long): Try[Option[AdvertiserPaymentMethod]] = {
    Try(
      DB.withTransaction { implicit c =>
        SQL(s"select name, payment_method::TEXT, stripe_customer_id notnull as has_credit_card, exists(select 1 from advert.get_latest_bill($advertiserId) where _bill_status = 'issued') as issued_bill, payment_method = 'subscription'::payment_method AS subscribed, NOT price > 0 AS free_subscription from advert.advertisers where advertiser_id = $advertiserId")
        .apply().headOption
      } match {
        case Some(row) =>
          Some(AdvertiserPaymentMethod(row[String]("name"),
            row[String]("payment_method"), row[Boolean]("has_credit_card"),
            row[Boolean]("issued_bill"), row[Boolean]("subscribed"),
            row[Boolean]("free_subscription")))
        case _ => None
      })
  }

  def getCreditCard(advertiserId: Long): Try[Option[Future[AdvertiserCreditCard]]] = {
    Try(DB.withTransaction { implicit c =>
      SQL(s"select stripe_customer_id from advert.advertisers where advertiser_id = $advertiserId")
        .apply()
        .map(row =>
          row[Option[String]]("stripe_customer_id") match {
            case Some(customerId) => Some(M8Stripe.getCustomer(customerId))
            case None => None
          }
        ).headOption.flatten
    })
  }

  def changePaymentMethod(advertiserId: Long,
                          email: String,
                          methodInfo: AdvertiserPaymentMethodUpdate): Try[Future[Int]] = {
    Try(
      methodInfo.cardToken.map(cardToken => {
        M8Stripe.createCustomer(advertiserId, email, cardToken).map { customerId =>
          val result = DB.withTransaction { implicit c =>
            SQL(s"select * from advert.change_payment_method($advertiserId, {customerId}, '${methodInfo.paymentMethod}'::payment_method)")
              .on('customerId -> customerId)
              .apply().map(row => row[Int]("_update_count")).head
          }
          StripeChargeActor ! ReceiveTimeout
          result
        }
      }).getOrElse(
        Future(
          DB.withTransaction { implicit c =>
            SQL(s"select * from advert.change_payment_method($advertiserId, NULL, '${methodInfo.paymentMethod}'::payment_method)")
              .apply().map(row => row[Int]("_update_count")).head
          }
        )
      )
    )
  }

  /* Remove the strip customer id information from the database and switch to manual payment */
  def removeStripeCustomerId(advertiserId: Long): Try[Unit] = {
    Try(DB.withTransaction { implicit c =>
      SQL(s"update advert.advertisers set stripe_customer_id = default, payment_method = 'manual'::payment_method where advertiser_id = $advertiserId")
        .executeUpdate()
    })
  }

  private def uploadFolder(advertiserId: Long) = {
    UploadRootPath + s"advertiser-${advertiserId.toString}"
  }

  private def deletePhotoFromDisk(advertiserId: Long,
                                  path: String) = {
    try {
      if (path.length > 0) {
        val fileName = FilenameUtils.getName(path)
        if (fileName.length > 0) {
          val filePath = Paths.get(uploadFolder(advertiserId), fileName)
          if (!java.nio.file.Files.isDirectory(filePath)) {
            java.nio.file.Files.deleteIfExists(filePath)
          }
        }
      }
    } catch {
      case ex:Exception => AccessLogger.error(s"Fail to delete a photo from disk: ${ex.getMessage}")
    }
  }
}
