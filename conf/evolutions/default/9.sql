# --- !Ups

ALTER TABLE IF EXISTS social.m8_users
    ADD COLUMN admin_note TEXT;

ALTER TABLE IF EXISTS advert.advertisers
    ADD COLUMN admin_note TEXT;

-- charge_single_balance
CREATE OR REPLACE FUNCTION advert.charge_balance(IN _advertiser_id INTEGER)
  RETURNS VOID AS $$
  UPDATE advert.advertisers
  SET balance = balance * (EXTRACT(EPOCH FROM(active_util - current_timestamp))::NUMERIC / EXTRACT(EPOCH FROM(active_util - balance_charged_at))::NUMERIC), balance_charged_at = current_timestamp
  WHERE advertiser_id = _advertiser_id AND balance > 0 AND active_util NOTNULL AND balance_charged_at NOTNULL AND active_util > current_timestamp AND balance_charged_at < current_timestamp AND status = 'active'::advertiser_status;;

  UPDATE advert.advertisers
  SET status = 'confirmed'::advertiser_status, balance = 0.0, balance_charged_at = DEFAULT
  WHERE advertiser_id = _advertiser_id AND (balance <= 0 OR (active_util NOTNULL AND active_util <= current_timestamp)) AND status = 'active'::advertiser_status;;
$$
LANGUAGE 'sql' VOLATILE;

-- change_plan
CREATE OR REPLACE FUNCTION advert.change_plan(IN _advertiser_id INTEGER, IN _plan_name TEXT, IN _price DECIMAL, IN _photo_limit INTEGER, IN _priority INTEGER)
  RETURNS VOID AS $$
BEGIN
  IF _price <=0 THEN
    RAISE EXCEPTION 'Plan price must be greater than zero' USING HINT = 'Invalid plan price?', ERRCODE = 'P9999';;
  END IF;;

  IF _photo_limit <=0 THEN
    RAISE EXCEPTION 'Plan photo limit must be greater than zero' USING HINT = 'Invalid plan photo limit?', ERRCODE = 'P9999';;
  END IF;;

  -- update the advertiser's balance firstly
  PERFORM advert.charge_balance(_advertiser_id);;

  -- cancel the current issued but not paid bill (if any)
  UPDATE advert.bills
  SET status = 'canceled'::bill_status, canceled_at = current_timestamp
  WHERE advertiser_id = _advertiser_id AND status = 'issued'::bill_status;;

  -- change the plan (most important - active_util)
  UPDATE advert.advertisers
  SET plan_name = _plan_name::plan_name, price = _price, photo_limit = _photo_limit, priority = _priority, active_util = CASE WHEN ((active_util NOTNULL) AND (price <> _price)) THEN current_timestamp + interval '1 month' * (balance / _price) ELSE active_util END
  WHERE advertiser_id = _advertiser_id;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

# --- !Downs

ALTER TABLE IF EXISTS social.m8_users
    DROP COLUMN IF EXISTS admin_note;

ALTER TABLE IF EXISTS advert.advertisers
    DROP COLUMN IF EXISTS admin_note;

DROP FUNCTION IF EXISTS advert.charge_balance(INTEGER);
DROP FUNCTION IF EXISTS advert.change_plan(INTEGER, TEXT, DECIMAL, INTEGER, INTEGER);