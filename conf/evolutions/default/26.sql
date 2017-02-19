# --- !Ups

CREATE OR REPLACE FUNCTION advert.change_plan(IN _advertiser_id INTEGER, IN _plan_name TEXT, IN _price DECIMAL, IN _photo_limit INTEGER, IN _priority INTEGER)
  RETURNS VOID AS $$
BEGIN
  IF _price < 0 THEN
    RAISE EXCEPTION 'Plan price must be greater than or equal to zero' USING HINT = 'Invalid plan price?', ERRCODE = 'P9999';;
  END IF;;

  IF _photo_limit <= 0 THEN
    RAISE EXCEPTION 'Plan photo limit must be greater than zero' USING HINT = 'Invalid plan photo limit?', ERRCODE = 'P9999';;
  END IF;;

  IF EXISTS(SELECT 1 FROM advert.bills WHERE status = 'paying'::bill_status AND advertiser_id = _advertiser_id) THEN
    RAISE EXCEPTION 'There is still a pending paying-bill for the advertiser. Please try again after 1 hour or so. If the error persists, please contact us' USING HINT = 'Pending paying-bill is not allowed when changing plan', ERRCODE = 'P9999';;
  END IF;;

  -- cancel the current issued but not paid bill (if any)
  UPDATE advert.bills
  SET status = 'canceled'::bill_status, canceled_at = current_timestamp
  WHERE advertiser_id = _advertiser_id AND status = 'issued'::bill_status;;

  -- update the advertiser's balance and account firstly
  PERFORM advert.update_balance(_advertiser_id);;
  PERFORM advert.expire_accounts(_advertiser_id);;

  -- change the plan (most important field: active_util)
  IF _price > 0 THEN
    UPDATE advert.advertisers
    SET plan_name = _plan_name::plan_name, price = _price, photo_limit = _photo_limit, priority = _priority, active_util = CASE WHEN (status = 'active'::advertiser_status AND price <> _price) THEN current_timestamp + interval '1 month' * (balance / _price) ELSE active_util END
    WHERE advertiser_id = _advertiser_id;;

    -- expire accounts again in case we are switching from price = 0 (free subscription) to price > 0, we potentially change active_util above when _price > 0
    PERFORM advert.expire_accounts(_advertiser_id);;
  ELSE
    UPDATE advert.advertisers
    SET plan_name = _plan_name::plan_name, price = _price, photo_limit = _photo_limit, priority = _priority, active_util = DEFAULT, status = CASE WHEN email_confirm_token ISNULL THEN 'active'::advertiser_status END
    WHERE advertiser_id = _advertiser_id;;
  END IF;;

  -- regenerate the bill
  PERFORM advert.generate_bills(_advertiser_id);;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

------------------------------------------------------------------------------------------

DROP FUNCTION IF EXISTS advert.get_business(INTEGER);
CREATE OR REPLACE FUNCTION advert.get_business(IN _advertiser_id INTEGER)
  RETURNS TABLE (_business_name TEXT, _contact_number TEXT, _website TEXT, _address TEXT, _description TEXT, _photo_limit INTEGER, _photos TEXT ARRAY, _active_util TIMESTAMPTZ, _status TEXT, _suspended_reason TEXT, _issued_bill BOOLEAN, _subscribed BOOLEAN, _free_subscription BOOLEAN) AS $$
BEGIN
  PERFORM advert.manage_accounts(_advertiser_id);;

  RETURN QUERY
    SELECT COALESCE(av.business_name, ''), av.contact_number, av.website, COALESCE(av.address, ''), COALESCE(av.description, ''), ad.photo_limit, COALESCE(av.photos[1:ad.photo_limit], '{}'), ad.active_util, ad.status::TEXT, ad.suspended_reason, EXISTS(SELECT 1 FROM advert.get_latest_bill(_advertiser_id) WHERE _bill_status = 'issued'), payment_method = 'subscription'::payment_method, NOT ad.price > 0
    FROM advert.advertisers ad LEFT JOIN advert.adverts av on (ad.advertiser_id = av.advertiser_id)
    WHERE ad.advertiser_id = _advertiser_id;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

------------------------------------------------------------------------------------------