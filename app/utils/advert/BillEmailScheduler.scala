package utils.advert

import akka.actor.{Props, ReceiveTimeout, Actor}
import akka.routing.RoundRobinPool
import models.advert.BillEmail
import org.joda.time.DateTime
import play.api.Logger
import play.api.db.DB
import utils._
import anorm._
import play.api.Play.current
import scala.util.{Failure, Try}

import scala.concurrent.duration._
import scala.language.postfixOps

class BillEmailScheduler extends Actor {
  val billEmailRouter = context.actorOf(RoundRobinPool(5).props(Props[BillEmailSender]), "advert.billEmailRouter")

  override def receive = {
    case "Start" =>
      context.setReceiveTimeout(10 minutes)
    case ReceiveTimeout =>
      Logger.debug("Begin to schedule sending all billing emails")

      Try(DB.withTransaction { implicit c =>
        SQL("select * from advert.get_bills_to_email()")
          .apply().map(row => billEmailRouter ! BillEmail(row[String]("_name"), row[String]("_email"),
          row[DateTime]("_issued_at"), row[Option[DateTime]]("_paid_at"),
          row[Option[DateTime]]("_expiring_at"), row[Option[DateTime]]("_canceled_at"),
          row[BigDecimal]("_amount"), row[String]("_status")))
      }) match {
        case Failure(ex) => AccessLogger.error(s"Fail to schedule sending all billing emails: ${ex.getMessage}")
        case _ => AccessLogger.debug("Schedule sending all billing emails completes once")
      }
    case "Stop" =>
      context.setReceiveTimeout(Duration.Undefined)
  }
}
