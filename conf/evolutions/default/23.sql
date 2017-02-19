# --- !Ups

CREATE OR REPLACE FUNCTION advert.expire_accounts(IN _advertiser_id INTEGER)
  RETURNS VOID AS $$
BEGIN
  UPDATE advert.advertisers
  SET status = 'confirmed'::advertiser_status, balance = 0.0, balance_charged_at = DEFAULT, active_util = DEFAULT
  WHERE (_advertiser_id ISNULL OR advertiser_id = _advertiser_id) AND (balance <= 0 OR (active_util NOTNULL AND active_util <= current_timestamp)) AND status = 'active'::advertiser_status;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

CREATE OR REPLACE FUNCTION advert.manage_accounts(IN _advertiser_id INTEGER)
  RETURNS VOID AS $$
BEGIN
  PERFORM advert.update_balance(_advertiser_id);;
  PERFORM advert.expire_accounts(_advertiser_id);;
  PERFORM advert.generate_bills(_advertiser_id);;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

CREATE OR REPLACE FUNCTION advert.change_plan(IN _advertiser_id INTEGER, IN _plan_name TEXT, IN _price DECIMAL, IN _photo_limit INTEGER, IN _priority INTEGER)
  RETURNS VOID AS $$
BEGIN
  IF _price <= 0 THEN
    RAISE EXCEPTION 'Plan price must be greater than zero' USING HINT = 'Invalid plan price?', ERRCODE = 'P9999';;
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
  UPDATE advert.advertisers
  SET plan_name = _plan_name::plan_name, price = _price, photo_limit = _photo_limit, priority = _priority, active_util = CASE WHEN (active_util NOTNULL AND price <> _price) THEN current_timestamp + interval '1 month' * (balance / _price) ELSE active_util END
  WHERE advertiser_id = _advertiser_id;;

  -- regenerate the bill
  PERFORM advert.generate_bills(_advertiser_id);;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;
