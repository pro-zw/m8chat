package utils.social

import models.social.M8User
import play.api.mvc._
import utils._

import scala.concurrent.Future
import scala.util.{Failure, Success}

object M8UserAuthAction extends ActionBuilder[M8UserRequest] with ActionRefiner[Request, M8UserRequest] {
  override protected def refine[A](request: Request[A]) = Future.successful {
    request.headers.get(MobileAccessTokenHeader) match {
      case Some(accessToken) =>
        M8User.authenticate(accessToken) match {
          case Success(Some(authResult)) =>
            if (authResult.blocked)
              Left(JsonErrorResult(Results.Unauthorized, "Blocked"))
            else
              Right(new M8UserRequest(authResult, request))
          case _ => Left(JsonErrorResult(Results.Unauthorized, "Unauthorized"))
        }
      case None => Left(JsonErrorResult(Results.Unauthorized, "Unauthorized"))
    }
  }
}
