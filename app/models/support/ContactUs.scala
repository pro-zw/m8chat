package models.support

import play.api.libs.json.Json

/**
 * Model the necessary information for Contact Us
 */
case class ContactUs (name: String,
                      email: String,
                      mobile: Option[String],
                      comment: String) {
}

object ContactUs {
  val jsonReads = Json.reads[ContactUs]
}
