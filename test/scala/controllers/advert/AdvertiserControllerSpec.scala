package scala.controllers.advert

import models.advert.{Advertiser, AdvertiserBusinessUpdate}
import models.social.{M8User, M8UserList, M8UserPosition}
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest, Helpers}
import utils._

class AdvertiserControllerSpec extends UnitSpec {
  "AdvertiserController" must {
    "get the advert by id" in {
      val vladResult = registerVlad.get
      val advertiserResult = registerWeiZhengAsAdvertiser.get

      M8User.updatePosition(vladResult.userId, M8UserPosition(-25.274398, 133.775126))
      Advertiser.confirmEmail(advertiserResult.advertiserId, advertiserResult.emailConfirmToken)
      activeAdvertiser(advertiserResult.advertiserId)

      Advertiser.updateBusiness(advertiserResult.advertiserId,
        AdvertiserBusinessUpdate("Crazydog Apps", Some("012345678"),
          Some("www.crazydog.com.au"), "4/15 Robinson Street", -25.274398, 133.775136, "We are a software company"))

      val result = M8User.listAdvertsNearby(vladResult.userId, M8UserList(None, None, 0, 10))
      val fakeRequest = FakeRequest(Helpers.GET,
        controllers.advert.routes.AdvertiserController.getAdvert(result.get(0).advertId).url,
        FakeHeaders(Seq(MobileApiTokenHeader -> Seq(mobileApiToken),
          MobileAccessTokenHeader -> Seq(vladResult.accessToken))),
        Json.obj()
      )

      verifyHttpResult(route(fakeRequest).get, Status.OK, "Crazydog Apps")
    }
  }
}
