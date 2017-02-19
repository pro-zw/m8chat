package utils.advert

import models.advert.Advertiser
import play.api.mvc._
import utils._

import scala.concurrent.Future
import scala.util.Success

object AdvertiserAuthAction extends ActionBuilder[AdvertiserRequest] with ActionRefiner[Request, AdvertiserRequest] {
  override protected def refine[A](request: Request[A]) = Future.successful {
    request.cookies.get(AdvertiserAccessTokenCookie) match {
      case Some(cookie: Cookie) =>
        Advertiser.authenticate(cookie.value) match {
          case Success(Some(authResult)) => Right(new AdvertiserRequest(authResult, request))
          case _ => Left(Results.Redirect(controllers.routes.Application.index())
            .discardingCookies(DiscardingCookie(AdvertiserAccessTokenCookie)))
        }
      case None => Left(Results.Redirect(controllers.routes.Application.index()))
    }
  }
}
