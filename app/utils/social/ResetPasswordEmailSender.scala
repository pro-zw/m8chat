package utils.social

import akka.actor.Actor
import models.social.M8UserForgotPassword
import org.apache.commons.mail.HtmlEmail
import utils._
import utils.support.EmailUtil

class ResetPasswordEmailSender extends Actor {
  override def receive = {
    case resetInfo: M8UserForgotPassword =>
      AccessLogger.debug("Sending m8 user password reset email...")

      if (resetInfo.userId.isDefined) {
        val mail = new HtmlEmail()
        mail.setFrom(EmailUtil.noReplySender, "m8chat")
        mail.addTo(resetInfo.email.get)
        mail.setSubject("m8chat Mobile User Password Reset")
        mail.setHtmlMsg(emails.social.html.resetPassword(RootUrl, resetInfo).body)
        mail.setCharset("utf-8")
        EmailUtil.send(mail)
      } else {
        AccessLogger.error("Send empty resetInfo to m8chat mobile user ResetPasswordEmailSender")
      }
  }
}
