import anorm.{Column, MetaDataItem, TypeDoesNotMatch}
import models.advert.AdvertiserAuth
import models.social.M8UserAuth
import play.api.{Play, Logger}
import play.api.data.validation.ValidationError
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.Play.current

import scala.language.postfixOps

/**
 * Util routines across domains
 */
package object utils {
  // Json error message writes
  implicit val JsPathWrites =
    Writes[JsPath](p => JsString(p.toString()))

  implicit val ValidationErrorWrites =
    Writes[ValidationError](e => JsString(e.message))

  implicit val JsonValidateErrorWrites = (
    (__ \ "path").write[JsPath] and
      (__ \ "messages").write[Seq[ValidationError]]
      tupled
    )

  implicit val JsonThrowableWrites = new Writes[Throwable] {
    def writes(throwable: Throwable) = Json.obj(
      "path" -> throwable.getClass.getName,
      "messages" -> Json.arr(throwable.getMessage)
    )
  }

  val RootUrl = "https://" + Play.application.configuration.getString("node.frontend.name").getOrElse("m8chat.com")

  // Access logger
  val AccessLogger = Logger("access")
  val PayPalLogger = Logger("paypal")
  val StripeLogger = Logger("stripe")

  // Json error results
  def JsonErrorResult(result: Results.Status, message: String) = {
    AccessLogger.error(s"Json Error Result ($result): $message")
    result(Json.obj(
      "errors" -> Json.arr(Json.toJson(new Exception(message)))
    ))
  }

  def JsonBadRequest(message: String) =
    JsonErrorResult(Results.BadRequest, message)

  def JsonBadRequest(errors: Seq[(JsPath, Seq[ValidationError])]) = {
    val jsonError = Json.toJson(errors)
    AccessLogger.error(s"Validation errors: ${jsonError.toString()}")
    BadRequest(Json.obj("errors" -> jsonError))
  }

  def JsonBadRequest(ex: Throwable) = {
    AccessLogger.error(s"General exception: ${ex.getMessage}")
    BadRequest(Json.obj("errors" -> Json.arr(Json.toJson(ex))))
  }

  // Access tokens
  val MobileAccessTokenHeader = "X-m8chat-Mobile-Access-Token"
  val MobileApiTokenHeader = "X-m8chat-Mobile-Api-Token"

  class M8UserRequest[A](val m8UserAuth: M8UserAuth, request: Request[A]) extends WrappedRequest[A](request)

  val AdvertiserAccessTokenCookie = "X-m8chat-Advertiser-Access-Token"

  class AdvertiserRequest[A](val advertiserAuth: AdvertiserAuth, request: Request[A]) extends WrappedRequest[A](request)

  // Upload
  val UploadRootPath = Play.application.configuration.getString("upload.root").getOrElse("static/upload/")
  val ServerNodeName = Play.application.configuration.getString("node.name").getOrElse("m8chat.com")

  // val ImageContentTypes = Vector("image/gif", "image/jpeg", "image/pjpeg", "image/png", "image/tiff")
  val ImageFileExtensions = Vector("png", "jpg", "jpeg", "tif", "tiff", "gif")

  // Handle anorm array column
  implicit def rowToStringArray: Column[Array[String]] = Column.nonNull { (value, meta) =>
    // val MetaDataItem(qualified, nullable, clazz) = meta
    value match {
      case o: java.sql.Array => Right(o.getArray.asInstanceOf[Array[String]])
      case _ => Left(TypeDoesNotMatch("Cannot convert " + value + ":" + value.asInstanceOf[AnyRef].getClass))
    }
  }

  // JsResult class improvements
  implicit class JsResultImprovements[+A](jsResult: JsResult[A]) {
    def toResponse = jsResult.fold(errors => JsonBadRequest(errors), _:(A => Result))
  }

  // Security
  val AppSecret = Play.application.configuration.getString("application.secret").getOrElse("iLIKUmHvmub@nNH9rr[gY2u9g;ot_5asFdxwC35^6cRv8]DMd6ApxqQTSBW5FaSX")

  // Field validation (regular expression)
  val AlphanumMask = "([A-Za-z0-9_]+)".r
  val EmailMask = """([A-Z0-9a-z.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9]+)+)""".r
}
