package controllers.support

import play.api.libs.json.Json
import play.api.mvc._

import models.support.ContactUs
import utils._
import utils.support._

/**
 * The controller for contact us function
 */
object ContactUsController extends Controller {
  implicit val reader = ContactUs.jsonReads

  def sendEmail = Action(parse.json) { request =>
    val jsonValue = request.body
    jsonValue.validate[ContactUs].fold(
      invalid = {
        errors => JsonBadRequest(errors)
      },
      valid = {
        case contactUs =>
          ContactUsEmailRouter ! contactUs
          Ok(Json.obj("success" -> true))
      }
    )
  }
}
