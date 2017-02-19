package utils.advert

import akka.actor.Actor
import models.advert.AdvertiserRegisterConfirm
import org.apache.commons.mail.HtmlEmail
import utils.support.EmailUtil
import utils._

class RegisterConfirmEmailSender extends Actor {
  override def receive = {
    case confirmInfo : AdvertiserRegisterConfirm =>
      AccessLogger.debug("Sending advertiser register confirmation email...")

      val mail = new HtmlEmail()
      mail.setFrom(EmailUtil.noReplySender, "m8chat")
      mail.addTo(confirmInfo.email)
      mail.setSubject("m8chat Advertiser Registration Confirmation")
      mail.setHtmlMsg(emails.advert.html.registerConfirm(RootUrl, confirmInfo).body)
      mail.setCharset("utf-8")
      EmailUtil.send(mail)
  }
}
