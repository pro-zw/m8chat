package models.advert

import org.joda.time.DateTime
import play.api.db.DB
import play.api.Play.current
import anorm._
import scala.util.Try

/* Case class server self uses */
case class BillOfAdvertiser(billId: Long,
                            issuedAt: DateTime,
                            paidAt: Option[DateTime],
                            expiringAt: Option[DateTime],
                            canceledAt: Option[DateTime],
                            amount: BigDecimal,
                            billStatus: String,
                            accountStatus: String,
                            paymentMethod: String)

case class BillEmail(name: String,
                     email: String,
                     issuedAt: DateTime,
                     paidAt: Option[DateTime],
                     expiringAt: Option[DateTime],
                     canceledAt: Option[DateTime],
                     amount: BigDecimal,
                     status: String)

object Bill {
  def getLatest(advertiserId: Long):Try[Option[BillOfAdvertiser]] = {
    Try(DB.withTransaction { implicit c =>
      SQL(s"select * from advert.get_latest_bill($advertiserId)")
        .apply().headOption match {
        case Some(row) => Some(BillOfAdvertiser(row[Long]("_bill_id"), row[DateTime]("_issued_at"),
          row[Option[DateTime]]("_paid_at"), row[Option[DateTime]]("_expiring_at"),
          row[Option[DateTime]]("_canceled_at"), row[BigDecimal]("_amount"),
          row[String]("_bill_status"), row[String]("_account_status"),
          row[String]("_payment_method")
        ))
        case None => None
      }
    })
  }
}
