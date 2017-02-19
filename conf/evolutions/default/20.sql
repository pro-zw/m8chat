# --- !Ups

DROP FUNCTION advert.get_latest_bill(integer);
CREATE OR REPLACE FUNCTION advert.get_latest_bill(IN _advertiser_id INTEGER)
  RETURNS TABLE (_bill_id INTEGER, _issued_at TIMESTAMPTZ, _paid_at TIMESTAMPTZ, _expiring_at TIMESTAMPTZ, _canceled_at TIMESTAMPTZ, _amount NUMERIC, _bill_status TEXT, _account_status TEXT, _payment_method TEXT) AS $$
BEGIN
  RETURN QUERY
    SELECT ab.bill_id, ab.issued_at, ab.paid_at, ab.expiring_at, ab.canceled_at, ab.amount, ab.status::TEXT, ad.status::TEXT, ad.payment_method::TEXT
    FROM advert.bills ab INNER JOIN advert.advertisers ad ON (ab.advertiser_id = ad.advertiser_id)
    WHERE ab.advertiser_id = _advertiser_id
    ORDER BY ab.issued_at DESC
    LIMIT 1;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

DROP FUNCTION IF EXISTS advert.expire_bills(INTEGER);

--------------------------------------------------------------

DROP FUNCTION IF EXISTS advert.generate_initial_bill() CASCADE;
CREATE OR REPLACE FUNCTION advert.generate_initial_bill()
  RETURNS TRIGGER AS $$
DECLARE
  _bill_info RECORD;;
BEGIN
  -- expires the issued bill (paying bill won't be affected)
  IF OLD.status = 'active'::advertiser_status AND NEW.status = 'confirmed'::advertiser_status THEN
    UPDATE advert.bills
    SET status = 'expired'::bill_status
    WHERE advertiser_id = _advertiser_id AND status = 'issued'::bill_status;;
  END IF;;

  FOR _bill_info IN
    SELECT ad.advertiser_id, (ad.price - ad.balance) AS amount
    FROM advert.advertisers ad LEFT JOIN advert.bills ab ON (ad.advertiser_id = ab.advertiser_id AND (ab.status = 'issued'::bill_status OR ab.status = 'paying'::bill_status))
    WHERE ad.advertiser_id = NEW.advertiser_id AND ad.status = 'confirmed'::advertiser_status AND ab.bill_id ISNULL
  LOOP
    INSERT INTO advert.bills(advertiser_id, amount)
    VALUES (_bill_info.advertiser_id, _bill_info.amount);;

    RAISE NOTICE 'A initial bill is generated with advertiser_id: %, amount: %', _bill_info.advertiser_id, _bill_info.amount;;
  END LOOP;;

  RETURN NEW;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

DROP TRIGGER IF EXISTS trig_generate_initial_bill ON advert.bills;
CREATE TRIGGER trig_generate_initial_bill
AFTER UPDATE
  ON advert.bills
  FOR EACH ROW
  WHEN (NEW.status = 'canceled'::bill_status)
  EXECUTE PROCEDURE advert.generate_initial_bill();

DROP TRIGGER IF EXISTS trig_generate_initial_bill ON advert.advertisers;
CREATE TRIGGER trig_generate_initial_bill
AFTER INSERT OR UPDATE
  ON advert.advertisers
  FOR EACH ROW
  WHEN (NEW.status = 'confirmed'::advertiser_status)
  EXECUTE PROCEDURE advert.generate_initial_bill();

--------------------------------------------------------------

CREATE OR REPLACE FUNCTION advert.charge_balance(IN _advertiser_id INTEGER)
  RETURNS VOID AS $$
  UPDATE advert.advertisers
  SET balance = balance * (EXTRACT(EPOCH FROM(active_util - current_timestamp))::NUMERIC / EXTRACT(EPOCH FROM(active_util - balance_charged_at))::NUMERIC), balance_charged_at = current_timestamp
  WHERE (_advertiser_id ISNULL OR advertiser_id = _advertiser_id) AND balance > 0 AND active_util NOTNULL AND balance_charged_at NOTNULL AND active_util > current_timestamp AND balance_charged_at < current_timestamp AND status = 'active'::advertiser_status;;

  UPDATE advert.advertisers
  SET status = 'confirmed'::advertiser_status, balance = 0.0, balance_charged_at = DEFAULT, active_util = DEFAULT
  WHERE (_advertiser_id ISNULL OR advertiser_id = _advertiser_id) AND (balance <= 0 OR (active_util NOTNULL AND active_util <= current_timestamp)) AND status = 'active'::advertiser_status;;
$$
LANGUAGE 'sql' VOLATILE;

--------------------------------------------------------------

CREATE OR REPLACE FUNCTION advert.change_plan(IN _advertiser_id INTEGER, IN _plan_name TEXT, IN _price DECIMAL, IN _photo_limit INTEGER, IN _priority INTEGER)
  RETURNS VOID AS $$
BEGIN
  IF _price <= 0 THEN
    RAISE EXCEPTION 'Plan price must be greater than zero' USING HINT = 'Invalid plan price?', ERRCODE = 'P9999';;
  END IF;;

  IF _photo_limit <= 0 THEN
    RAISE EXCEPTION 'Plan photo limit must be greater than zero' USING HINT = 'Invalid plan photo limit?', ERRCODE = 'P9999';;
  END IF;;

  -- update the advertiser's balance firstly
  PERFORM advert.charge_balance(_advertiser_id);;

  -- change the plan (most important - active_util)
  UPDATE advert.advertisers
  SET plan_name = _plan_name::plan_name, price = _price, photo_limit = _photo_limit, priority = _priority, active_util = CASE WHEN ((active_util NOTNULL) AND (price <> _price)) THEN current_timestamp + interval '1 month' * (balance / _price) ELSE active_util END
  WHERE advertiser_id = _advertiser_id;;

  -- cancel the current issued but not paid bill (if any)
  UPDATE advert.bills
  SET status = 'canceled'::bill_status, canceled_at = current_timestamp
  WHERE advertiser_id = _advertiser_id AND status = 'issued'::bill_status;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

--------------------------------------------------------------
