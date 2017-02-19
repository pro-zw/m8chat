package utils

import akka.actor.Props
import akka.routing.RoundRobinPool
import org.apache.commons.mail.{DefaultAuthenticator, Email}
import play.api.Play
import play.api.libs.concurrent.Akka
import play.api.Play.current

/**
 * Util routines belong to custom support domain
 */
package object support {
  object EmailUtil {
    val smtpHost = Play.application.configuration.getString("smtp.host").getOrElse("mail.m8chat.com")
    val smtpPort = Play.application.configuration.getInt("smtp.port").getOrElse(587)
    val smtpUser = Play.application.configuration.getString("smtp.user").getOrElse("webmaster@m8chat.com")
    val smtpPassword = Play.application.configuration.getString("smtp.password").getOrElse("64PscSUvTHRV8wB3")
    val smtpStartTls = Play.application.configuration.getBoolean("smtp.startTls").getOrElse(true)
    val smtpDebug = Play.application.configuration.getBoolean("smtp.debug").getOrElse(false)

    val noReplySender = "noreply@m8chat.com"

    def send(mail: Email) = {
      mail.setHostName(EmailUtil.smtpHost)
      mail.setSmtpPort(EmailUtil.smtpPort)
      mail.setAuthenticator(new DefaultAuthenticator(EmailUtil.smtpUser, EmailUtil.smtpPassword))
      mail.setSSLCheckServerIdentity(false)
      mail.setStartTLSRequired(EmailUtil.smtpStartTls)
      mail.setDebug(EmailUtil.smtpDebug)
      mail.send()
    }
  }

  val ContactUsEmailRouter = Akka.system.actorOf(RoundRobinPool(3).props(Props[ContactUsEmailSender]), "support.ContactUsEmailRouter")
  val AdminEmailSender = Akka.system.actorOf(Props[AdminEmailSender], "support.AdminEmailSender")
}
