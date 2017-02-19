package utils

import akka.actor.Props
import akka.routing.RoundRobinPool
import play.api.Logger
import play.api.libs.concurrent.Akka
import play.api.Play.current

package object advert {
  val RegisterConfirmEmailRouter = Akka.system.actorOf(RoundRobinPool(5).props(Props[RegisterConfirmEmailSender]), "advert.RegisterConfirmEmailRouter")
  val ResetPasswordEmailRouter = Akka.system.actorOf(RoundRobinPool(5).props(Props[ResetPasswordEmailSender]), "advert.ResetPasswordEmailRouter")

  val StripeChargeActor = Akka.system.actorOf(Props[StripeChargeActor].withDispatcher("stripe-dispatcher"), "advert.StripeChargeActor")
  val AccountManager = Akka.system.actorOf(Props[AccountManager], "advert.AccountManager")
  val BillEmailScheduler = Akka.system.actorOf(Props[BillEmailScheduler], "advert.BillEmailScheduler")
}
