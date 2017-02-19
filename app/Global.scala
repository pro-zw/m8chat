import play.api.libs.json.Json
import play.api.mvc.Results._
import play.api.{GlobalSettings, Application, Logger}
import play.api.mvc._
import play.api.Play.current

/* import play.api.libs.concurrent.Execution.Implicits.defaultContext */
import scala.concurrent.Future

import utils._
import utils.advert._

/*
object AccessLoggingFilter extends Filter {
  override def apply(next: (RequestHeader) => Future[Result])(request: RequestHeader): Future[Result] = {
    val startTime = System.currentTimeMillis()
    val resultFuture = next(request)

    resultFuture.foreach(result => {
      val endTime = System.currentTimeMillis()
      AccessLogger.info(s"method=${request.method} uri=${request.uri} remote-address=${request.remoteAddress}" +
        s" status=${result.header.status} time=${endTime - startTime}ms")
    })

    resultFuture
  }
}
*/

/* object Global extends WithFilters(AccessLoggingFilter) { */
object Global extends GlobalSettings {
  override def onStart(app: Application) = {
    AccountManager ! "Start"
    StripeChargeActor ! "Start"
    BillEmailScheduler ! "Start"
  }

  override def onStop(app: Application) = {
    AccountManager ! "Stop"
    StripeChargeActor ! "Stop"
    BillEmailScheduler ! "Stop"
  }

  override def onError(request: RequestHeader, ex: Throwable) = {
    AccessLogger.warn(s"Global onError: ${ex.getMessage}")

    Future.successful(InternalServerError(
      Json.obj("errors" -> Json.arr(Json.toJson(ex.getCause))))
    )
  }
}
