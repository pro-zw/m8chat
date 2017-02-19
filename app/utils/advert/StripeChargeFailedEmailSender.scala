package utils.advert

import akka.actor.Actor
import models.advert.AdvertiserStripeChargeFaild
import org.apache.commons.mail.HtmlEmail
import utils._
import utils.support.EmailUtil

class StripeChargeFailedEmailSender extends Actor {
  override def receive = {
    case info: AdvertiserStripeChargeFaild =>
      val mail = new HtmlEmail()
      mail.setFrom(EmailUtil.noReplySender, "m8chat")
      mail.addTo(info.email)
      mail.setSubject("m8chat Advertiser Subscription Direct Credit Failed")
      mail.setHtmlMsg(emails.advert.html.stripeChargeFail(RootUrl, info.name, info.error).body)
      mail.setCharset("utf-8")
      EmailUtil.send(mail)
  }
}
