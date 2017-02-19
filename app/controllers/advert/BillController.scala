package controllers.advert

import models.advert.Bill
import play.api.mvc.Controller
import utils._
import utils.advert.AdvertiserAuthAction

import scala.util.{Failure, Success}

object BillController extends Controller {
  def bill = AdvertiserAuthAction { request =>
    Bill.getLatest(request.advertiserAuth.advertiserId) match {
      case Success(Some(bill)) => Ok(views.html.myBill(request.advertiserAuth.name, bill))
      case Success(None) => Ok(views.html.message("Error", "No bill information associated with the current user"))
      case Failure(ex) => Ok(views.html.message("Error", ex.getMessage))
    }
  }
}
