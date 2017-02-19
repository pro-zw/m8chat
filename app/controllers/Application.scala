package controllers

import play.api.Play.current
import play.api.cache.Cached
import play.api.mvc._

object Application extends Controller {
  def index = Cached.status(req => "homePage", 200, 3600) {
    Action {
      Ok(views.html.index())
    }
  }

  def register = Cached.status(req => "registerPage", 200, 3600) {
    Action {
      Ok(views.html.register())
    }
  }
}