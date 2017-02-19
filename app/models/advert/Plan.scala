package models.advert

import play.api.Play
import play.api.Play.current

object Plan {
  val bronze = Play.application.configuration.getConfig("advert.plans.bronze").get
  val silver = Play.application.configuration.getConfig("advert.plans.silver").get
  val gold = Play.application.configuration.getConfig("advert.plans.gold").get
}
