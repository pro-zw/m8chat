package utils.advert

import akka.actor.Actor
import models.advert.AdvertiserForgotPassword
import org.apache.commons.mail.HtmlEmail
import utils._
import utils.support.EmailUtil

class ResetPasswordEmailSender extends Actor {
  override def receive = {
    case resetInfo: AdvertiserForgotPassword =>
      AccessLogger.debug("Sending advertiser password reset email...")

      if (resetInfo.advertiserId.isDefined) {
        val mail = new HtmlEmail()
        mail.setFrom(EmailUtil.noReplySender, "m8chat")
        mail.addTo(resetInfo.email.get)
        mail.setSubject("m8chat Advertiser Password Reset")
        mail.setHtmlMsg(emails.advert.html.resetPassword(RootUrl, resetInfo).body)
        mail.setCharset("utf-8")
        EmailUtil.send(mail)
      } else {
        AccessLogger.error("Send empty resetInfo to advertiser ResetPasswordEmailSender")
      }
  }
}
