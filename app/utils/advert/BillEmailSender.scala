package utils.advert

import akka.actor.Actor
import models.advert.BillEmail
import org.apache.commons.mail.HtmlEmail
import utils._
import utils.support.EmailUtil

class BillEmailSender extends Actor {
  override def receive = {
    case billInfo: BillEmail =>
      AccessLogger.debug("Sending bill email...")

      val mail = new HtmlEmail()
      mail.setFrom(EmailUtil.noReplySender, "m8chat")
      mail.addTo(billInfo.email)
      mail.setSubject("m8chat Advertiser Bill Information")
      mail.setHtmlMsg(emails.advert.html.bill(RootUrl, billInfo).body)
      mail.setCharset("utf-8")
      EmailUtil.send(mail)
  }
}
