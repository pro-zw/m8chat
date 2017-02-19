package utils

import play.api.Play
import play.api.Play.current
import play.api.mvc._

import scala.concurrent.Future

object MobileApiAuthAction extends ActionBuilder[Request] with ActionFilter[Request] {
  val mobileApiToken = Play.application.configuration.getString("mobile.api.token").getOrElse("UYtsECp8eD1cqHk3zOPLvoluxSvoQPGc4ympcufJwzqEcayMtD")

  override def filter[A](request: Request[A]) = Future.successful {
    request.headers.get(MobileApiTokenHeader) match {
      case Some(apiToken) if apiToken == mobileApiToken => None
      case _ => Some(JsonErrorResult(Results.BadRequest, "Mobile api token is not provided or is wrong"))
    }
  }
}
