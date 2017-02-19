package utils.support

import akka.actor.Actor
import org.apache.commons.mail._
import play.api.Play
import play.api.Play.current

class AdminEmailSender extends Actor {
  val adminRecipient = Play.application.configuration.getString("admin.email.recipient").getOrElse("paul@m8chat.com")

  override def receive = {
    case (subject: String, message: String) =>
      val mail = new SimpleEmail()
      mail.setFrom(EmailUtil.noReplySender, "m8chat")
      mail.addTo(adminRecipient)
      mail.setSubject(subject)
      mail.setMsg(message)
      mail.setCharset("utf-8")
      EmailUtil.send(mail)
  }
}
