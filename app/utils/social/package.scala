package utils

import akka.actor.Props
import akka.routing.RoundRobinPool
import play.api.libs.concurrent.Akka
import play.api.Play.current

package object social {
  val ResetPasswordEmailRouter = Akka.system.actorOf(RoundRobinPool(5).props(Props[ResetPasswordEmailSender]), "social.ResetPasswordEmailRouter")
  val MessageNotificationRouter = Akka.system.actorOf(RoundRobinPool(5).props(Props[MessageNotificationSender]), "social.MessageNotificationRouter")
}
