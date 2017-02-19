package utils.support

import akka.actor.Actor
import org.apache.commons.mail._
import play.api.Play
import play.api.Play.current

import utils._
import models.support.ContactUs

/**
 * This actor is responsible for sending all emails
 */
class ContactUsEmailSender extends Actor {
  val contactRecipient = Play.application.configuration.getString("contact.email.recipient").getOrElse("paul@m8chat.com")

  override def receive = {
    case contactUs : ContactUs =>
      AccessLogger.debug("Sending contact us email...")

      val mail = new HtmlEmail()
      mail.setFrom(contactUs.email)
      mail.addTo(contactRecipient)
      mail.setSubject("Contact message from a m8chat website visitor")
      mail.setHtmlMsg(emails.support.html.contactUs(contactUs).body)
      mail.setCharset("utf-8")
      EmailUtil.send(mail)
  }
}
