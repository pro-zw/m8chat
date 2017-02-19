package controllers.advert

import models.advert._
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import utils.advert.{AdvertiserAuthAction, AdvertiserApiAuthAction}
import scala.concurrent.Future
import scala.util.{Success, Failure}
import utils._
import utils.advert._

object AdvertiserController extends Controller {
  def register = Action(parse.json) { request =>
    implicit val reader = Advertiser.jsonRegisterReads
    implicit val writer = Advertiser.jsonRegisterWrites

    request.body.validate[AdvertiserRegister].toResponse(
      regInfo => Advertiser.register(regInfo) match {
        case Success(Some(regResult)) if regResult.advertiserId > 0 =>
          RegisterConfirmEmailRouter ! AdvertiserRegisterConfirm(regResult.advertiserId,
            regInfo.name, regInfo.email, regResult.emailConfirmToken)
          Ok(Json.toJson(regResult))
        case Success(Some(regResult)) if regResult.advertiserId <= 0 =>
          JsonBadRequest("Unable to register. Please try again")
        case Failure(ex) if ex.getMessage.contains("advertisers_email_idx") => JsonBadRequest("User already exists")
        case Failure(ex) => JsonBadRequest(ex)
      }
    )
  }

  def login = Action(parse.json) { request =>
    implicit val reader = Advertiser.jsonLoginReads
    implicit val write = Advertiser.jsonLoginWrites

    request.body.validate[AdvertiserLogin].toResponse(
      loginInfo => Advertiser.login(loginInfo) match {
        case Success(Some(loginResult)) if loginResult.accessToken.isDefined =>
          Ok(Json.toJson(loginResult))
            .withCookies(Cookie(AdvertiserAccessTokenCookie, loginResult.accessToken.get))
        case Success(Some(loginResult)) if loginResult.accessToken.isEmpty =>
          Ok(Json.toJson(loginResult))
            .discardingCookies(DiscardingCookie(AdvertiserAccessTokenCookie))
        case _ =>
          JsonErrorResult(Results.Unauthorized, "Unauthorized")
            .discardingCookies(DiscardingCookie(AdvertiserAccessTokenCookie))
      }
    )
  }

  def logout = AdvertiserAuthAction { request =>
    Advertiser.logout(request.advertiserAuth.advertiserId)
    Results.Redirect(controllers.routes.Application.index())
      .discardingCookies(DiscardingCookie(AdvertiserAccessTokenCookie))
  }

  def confirmEmail(advertiserId: Long,
                   token: String) = Action { request =>
    Advertiser.confirmEmail(advertiserId, token) match {
      case Success(result) if result.updateCount > 0 =>
        Ok(views.html.myEmailConfirmed())
          .withCookies(Cookie(AdvertiserAccessTokenCookie, result.accessToken))
      case Success(result) if result.updateCount <= 0 =>
        Ok(views.html.message("Account Abnormal", "The account is either already confirmed, or in abnormal status. Please try to login or contact us"))
      case Failure(ex) =>
        Ok(views.html.message("Error", ex.getMessage))
    }
  }

  def checkEmailExists = Action(parse.json) { request =>
    val email = (request.body \ "email").as[String]
    Ok(Json.obj("exists" -> Advertiser.checkEmailExists(email)))
  }

  def updateBusiness() = AdvertiserApiAuthAction(parse.json) { request =>
    implicit val reader = Advertiser.jsonBusinessReads

    request.body.validate[AdvertiserBusinessUpdate].toResponse(
      businessInfo => Advertiser.updateBusiness(
        request.advertiserAuth.advertiserId, businessInfo) match {
        case Success(_) => NoContent
        case Failure(ex) => JsonBadRequest(ex)
      }
    )
  }

  def updatePhoto(photoIndex: Int) = AdvertiserApiAuthAction(parse.multipartFormData) { request =>
    request.body.file("photo").map {
      photo => {
        Advertiser.updatePhoto(request.advertiserAuth.advertiserId,
          request.advertiserAuth.photoLimit, photoIndex, photo) match {
          case Success(photoPath) => Ok(Json.obj("photoPath" -> photoPath))
          case Failure(ex) => JsonBadRequest(ex)
        }
      }
    }.getOrElse {
      JsonBadRequest("Missing photo file")
    }
  }

  def deletePhoto(photoIndex: Int) = AdvertiserApiAuthAction { request =>
    Advertiser.deletePhoto(request.advertiserAuth.advertiserId, photoIndex) match {
      case Success(_) => NoContent
      case Failure(ex) => JsonBadRequest(ex)
    }
  }

  def advert = AdvertiserAuthAction { request =>
    Advertiser.getBusiness(request.advertiserAuth.advertiserId) match {
      case Success(Some(business)) => Ok(views.html.myAdvert(request.advertiserAuth.name, business))
      case Success(None) => Ok(views.html.message("Error", "No account information associated with the current user"))
      case Failure(ex) => Ok(views.html.message("Error", ex.getMessage))
    }
  }

  def getAdvert(advertId: Long) = Action { request =>
    implicit val writer = Advertiser.jsonAdvertWrites

    Advertiser.getAdvert(advertId) match {
      case Success(Some(advert)) => Ok(Json.toJson(advert))
      case Success(None) => Ok(Json.obj())
      case Failure(ex) => JsonBadRequest(ex)
    }
  }

  def accountDetails = AdvertiserAuthAction { request =>
    Advertiser.getAccountDetails(request.advertiserAuth.advertiserId) match {
      case Success(Some(advertiser)) => Ok(views.html.myAccountDetail(request.advertiserAuth.name, advertiser))
      case Success(None) => Ok(views.html.message("Error", "No account information associated with the current user"))
      case Failure(ex) => Ok(views.html.message("Error", ex.getMessage))
    }
  }

  def updateAccount() = AdvertiserApiAuthAction(parse.json) { request =>
    implicit val reader = Advertiser.jsonAccountReads

    request.body.validate[AdvertiserAccountUpdate].toResponse {
      accountInfo => Advertiser.updateAccount(request.advertiserAuth.advertiserId,
        accountInfo) match {
        case Success(result) if result > 0 => NoContent
        case Success(result) if result <= 0 => JsonBadRequest("No account information associated with the current user")
        case Failure(ex) if ex.getMessage.contains("advertisers_email_idx") => JsonBadRequest("User already exists")
        case Failure(ex) => JsonBadRequest(ex)
      }
    }
  }

  def plan = AdvertiserAuthAction { request =>
    Advertiser.getPlan(request.advertiserAuth.advertiserId) match {
      case Success(Some(planInfo)) => Ok(views.html.myPlan(planInfo))
      case Success(None) => Ok(views.html.message("Error", "No account information associated with the current user"))
      case Failure(ex) => Ok(views.html.message("Error", ex.getMessage))
    }
  }

  def forgotPassword = Action(parse.json) { request =>
    val email = (request.body \ "email").as[String]

    Advertiser.forgotPassword(email) match {
      case Success(result) if result.updateCount > 0 && result.advertiserId.isDefined =>
        ResetPasswordEmailRouter ! result
        NoContent
      case Success(result) if result.updateCount <= 0 =>
        JsonBadRequest("Email doesn't exist")
      case Failure(ex) => JsonBadRequest(ex)
    }
  }

  def resetPasswordPage(advertiserId: Long,
                        resetDigest: String) = Action {
    Advertiser.getNameOfPasswordReset(advertiserId, resetDigest) match {
      case Success(Some(name)) => Ok(views.html.resetPassword(name))
      case Success(None) => Ok(views.html.message("Error", "The resetting request is not found or has expired. Please try to reset password again"))
      case Failure(ex) => Ok(views.html.message("Error", ex.getMessage))
    }
  }

  def resetPassword(advertiserId: Long,
                    resetDigest: String) = Action(parse.json) { request =>
    implicit val reader = Advertiser.jsonNewPasswordReads

    request.body.validate[AdvertiserNewPassword].toResponse {
      newPassword => Advertiser.resetPassword(advertiserId, resetDigest, newPassword) match {
        case Success(result) if result > 0 => NoContent
        case Success(result) if result <= 0 => JsonBadRequest("Cannot reset the password due to wrong parameters provided")
        case Failure(ex) => JsonBadRequest(ex)
      }
    }
  }

  def changePlan() = AdvertiserAuthAction(parse.json) { request =>
    val planName = (request.body \ "planName").as[String]

    Advertiser.changePlan(request.advertiserAuth.advertiserId, planName) match {
      case Success(_) => NoContent
      case Failure(ex) => JsonBadRequest(ex)
    }
  }

  def paymentMethod() = AdvertiserAuthAction { request =>
    Advertiser.getPaymentMethod(request.advertiserAuth.advertiserId) match {
      case Success(Some(result)) => Ok(views.html.myPaymentMethod(result))
      case Success(None) => Ok(views.html.message("Error", "No account information associated with the current user"))
      case Failure(ex) => Ok(views.html.message("Error", ex.getMessage))
    }
  }

  def getCreditCard = AdvertiserApiAuthAction.async { request =>
    implicit val writer = Advertiser.jsonCreditCardWrites

    Advertiser.getCreditCard(request.advertiserAuth.advertiserId) match {
      case Success(Some(futureResult)) => futureResult.map(result => Ok(Json.toJson(result)))
      case Success(None) => Future.successful(JsonBadRequest("No credit card information associated with the current user"))
      case Failure(ex) => Future.successful(JsonBadRequest(ex))
    }
  }

  def changePaymentMethod() = AdvertiserApiAuthAction.async(parse.json) { request =>
    implicit val reader = Advertiser.jsonPaymentMethodReads

    request.body.validate[AdvertiserPaymentMethodUpdate].fold(
      errors => Future.successful(JsonBadRequest(errors)),
      methodInfo => Advertiser.changePaymentMethod(request.advertiserAuth.advertiserId,
        request.advertiserAuth.email, methodInfo) match {
        case Success(futureResult) => futureResult.map { result =>
          if (result > 0) {
            NoContent
          } else {
            JsonBadRequest("No account information associated with the current user")
          }
        }
        case Failure(ex) => Future.successful(JsonBadRequest(ex))
      }
    )
  }

  def removeStripeCustomerId() = AdvertiserApiAuthAction { request =>
    Advertiser.removeStripeCustomerId(request.advertiserAuth.advertiserId) match {
      case Success(_) => NoContent
      case Failure(ex) => JsonBadRequest(ex)
    }
  }

  /*
  private def photoFileParser() = parse.when(
    requestHeader => {
      accessLogger.debug(requestHeader.contentType.toString)
      ImageContentTypes.contains(requestHeader.contentType)
    },
    parse.multipartFormData,
    requestHeader => Future.successful { JsonBadRequest("Invalid file type (only image files are allowed)") }
  )
  */
}
