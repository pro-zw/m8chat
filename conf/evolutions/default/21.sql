# --- !Ups

DROP FUNCTION IF EXISTS advert.generate_periodical_bills();

DROP FUNCTION IF EXISTS advert.generate_initial_bill() CASCADE;
DROP TRIGGER IF EXISTS trig_generate_initial_bill ON advert.bills;
DROP TRIGGER IF EXISTS trig_generate_initial_bill ON advert.advertisers;

--------------------------------------------------------------

DROP FUNCTION IF EXISTS advert.begin_pay_bill(INTEGER, INTEGER);
DROP FUNCTION IF EXISTS advert.check_pay_bill(INTEGER, INTEGER);

DROP FUNCTION IF EXISTS advert.get_paypal_bill(INTEGER, INTEGER);
CREATE OR REPLACE FUNCTION advert.get_paypal_bill(IN _bill_id INTEGER, IN _advertiser_id INTEGER, OUT _amount NUMERIC)
AS $$
BEGIN
  SELECT ab.amount INTO _amount
  FROM advert.bills ab INNER JOIN advert.advertisers ad ON (ab.advertiser_id = ad.advertiser_id AND (ad.status = 'confirmed'::advertiser_status OR ad.status = 'active'::advertiser_status))
  WHERE ab.bill_id = _bill_id AND ab.advertiser_id = _advertiser_id AND ab.status = 'issued'::bill_status AND (ab.expiring_at ISNULL OR ab.expiring_at > current_timestamp);;
END;;
$$
LANGUAGE 'plpgsql' STABLE;

DROP FUNCTION IF EXISTS advert.begin_pay_paypal_bill(INTEGER, INTEGER);
CREATE OR REPLACE FUNCTION advert.begin_pay_paypal_bill(IN _bill_id INTEGER, IN _advertiser_id INTEGER, OUT _update_count INTEGER)
AS $$
BEGIN
  UPDATE advert.bills AS ab
  SET status = 'paying'::bill_status, paying_at = current_timestamp
  FROM advert.advertisers AS ad
  WHERE ab.bill_id = _bill_id AND ab.advertiser_id = _advertiser_id AND ab.status = 'issued'::bill_status AND (ab.expiring_at ISNULL OR ab.expiring_at > current_timestamp) AND ab.advertiser_id = ad.advertiser_id AND (ad.status = 'confirmed'::advertiser_status OR ad.status = 'active'::advertiser_status);;

  GET DIAGNOSTICS _update_count = ROW_COUNT;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

DROP FUNCTION IF EXISTS advert.cancel_pay_paypal_bill(INTEGER, INTEGER);
CREATE OR REPLACE FUNCTION advert.cancel_pay_paypal_bill(IN _bill_id INTEGER, IN _advertiser_id INTEGER)
  RETURNS VOID AS $$
BEGIN
  UPDATE advert.bills
  SET status = 'canceled'::bill_status
  WHERE bill_id = _bill_id AND status = 'paying'::bill_status;;

  PERFORM advert.generate_bills(_advertiser_id);;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

--------------------------------------------------------------

DROP FUNCTION IF EXISTS advert.cancel_pay_subscription_bill(INTEGER, INTEGER);
CREATE OR REPLACE FUNCTION advert.cancel_pay_subscription_bill(IN _bill_id INTEGER, IN _advertiser_id INTEGER)
  RETURNS VOID AS $$
BEGIN
  PERFORM advert.change_payment_method(_advertiser_id, NULL, 'manual'::payment_method);;

  UPDATE advert.bills
  SET status = 'canceled'::bill_status
  WHERE bill_id = _bill_id AND status = 'paying'::bill_status;;

  PERFORM advert.generate_bills(_advertiser_id);;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

--------------------------------------------------------------

CREATE OR REPLACE FUNCTION advert.confirm_email(IN _advertiser_id INTEGER, IN _token TEXT, OUT _update_count INTEGER, OUT _access_token TEXT)
AS $$
BEGIN
  _access_token := uuid_generate_v4();;

  UPDATE advert.advertisers
  SET status = 'confirmed'::advertiser_status, email_confirm_token = DEFAULT, access_token = _access_token, authorized_at = current_timestamp
  WHERE advertiser_id = _advertiser_id AND email_confirm_token = _token AND status = 'registered'::advertiser_status AND price NOTNULL;;

  GET DIAGNOSTICS _update_count = ROW_COUNT;;

  PERFORM advert.generate_bills(_advertiser_id);;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

--------------------------------------------------------------

CREATE OR REPLACE FUNCTION advert.update_balance(IN _advertiser_id INTEGER)
  RETURNS VOID AS $$
BEGIN
  UPDATE advert.advertisers
  SET balance = balance * (EXTRACT(EPOCH FROM(active_util - current_timestamp))::NUMERIC / EXTRACT(EPOCH FROM(active_util - balance_charged_at))::NUMERIC), balance_charged_at = current_timestamp
  WHERE (_advertiser_id ISNULL OR advertiser_id = _advertiser_id) AND balance > 0 AND active_util NOTNULL AND balance_charged_at NOTNULL AND active_util > current_timestamp AND balance_charged_at < current_timestamp AND status = 'active'::advertiser_status;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

CREATE OR REPLACE FUNCTION advert.generate_bills(IN _advertiser_id INTEGER)
  RETURNS VOID AS $$
DECLARE
  _initial_bill_info RECORD;;
  _periodical_bill_info RECORD;;
BEGIN
  -- cancel paying bills which is not fulfilled
  UPDATE advert.bills
  SET status = 'canceled'::bill_status
  WHERE (_advertiser_id ISNULL OR advertiser_id = _advertiser_id) AND status = 'paying'::bill_status AND age(current_timestamp, paying_at) >= '15 minutes';;

  -- generate initial bills (won't generate any bills if there are issued or paying bills)
  FOR _initial_bill_info IN
    SELECT ad.advertiser_id, (ad.price - ad.balance) AS amount
    FROM advert.advertisers ad LEFT JOIN advert.bills ab ON (ad.advertiser_id = ab.advertiser_id AND (ab.status = 'issued'::bill_status OR ab.status = 'paying'::bill_status))
    WHERE (_advertiser_id ISNULL OR ad.advertiser_id = _advertiser_id) AND ad.status = 'confirmed'::advertiser_status AND ab.bill_id ISNULL
  LOOP
    INSERT INTO advert.bills (advertiser_id, amount)
    VALUES (_initial_bill_info.advertiser_id, _initial_bill_info.amount);;

    RAISE NOTICE 'A initial bill is generated with advertiser_id: %, amount: %', _initial_bill_info.advertiser_id, _initial_bill_info.amount;;
  END LOOP;;

  -- generate periodical bills (won't generate any bills if there are issued or paying bills)
  FOR _periodical_bill_info IN
    SELECT ad.advertiser_id, active_util, (ad.price - ad.balance) AS amount
    FROM advert.advertisers ad LEFT JOIN advert.bills ab ON (ad.advertiser_id = ab.advertiser_id AND (ab.status = 'issued'::bill_status OR ab.status = 'paying'::bill_status))
    WHERE (_advertiser_id ISNULL OR ad.advertiser_id = _advertiser_id) AND ad.status = 'active'::advertiser_status AND (ad.price - ad.balance) > 0 AND (ad.active_util NOTNULL AND ad.active_util > current_timestamp AND age(ad.active_util, current_timestamp) < '10 days') AND ab.bill_id ISNULL
  LOOP
    INSERT INTO advert.bills (advertiser_id, amount, expiring_at)
    VALUES (_periodical_bill_info.advertiser_id, _periodical_bill_info.amount, _periodical_bill_info.active_util);;

    RAISE NOTICE 'A periodical bill is generated with advertiser_id: %, amount: %, expiring_at: %', _periodical_bill_info.advertiser_id, _periodical_bill_info.amount, _periodical_bill_info.active_util;;
  END LOOP;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

DROP FUNCTION IF EXISTS advert.charge_balance(INTEGER);
CREATE OR REPLACE FUNCTION advert.manage_accounts(IN _advertiser_id INTEGER)
  RETURNS VOID AS $$
BEGIN
  PERFORM advert.update_balance(_advertiser_id);;

  UPDATE advert.advertisers
  SET status = 'confirmed'::advertiser_status, balance = 0.0, balance_charged_at = DEFAULT, active_util = DEFAULT
  WHERE (_advertiser_id ISNULL OR advertiser_id = _advertiser_id) AND (balance <= 0 OR (active_util NOTNULL AND active_util <= current_timestamp)) AND status = 'active'::advertiser_status;;

  PERFORM advert.generate_bills(_advertiser_id);;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

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

  IF EXISTS(SELECT 1 FROM advert.bills WHERE status = 'paying'::bill_status AND advertiser_id = _advertiser_id) THEN
    RAISE EXCEPTION 'There is still a pending paying-bill for the advertiser. Please try again after 1 hour or so. If the error persists, please contact us' USING HINT = 'Pending paying-bill is not allowed when changing plan', ERRCODE = 'P9999';;
  END IF;;

  -- cancel the current issued but not paid bill (if any)
  UPDATE advert.bills
  SET status = 'canceled'::bill_status, canceled_at = current_timestamp
  WHERE advertiser_id = _advertiser_id AND status = 'issued'::bill_status;;

  -- update the advertiser's balance firstly
  PERFORM advert.update_balance(_advertiser_id);;

  -- change the plan (most important field: active_util)
  UPDATE advert.advertisers
  SET plan_name = _plan_name::plan_name, price = _price, photo_limit = _photo_limit, priority = _priority, active_util = CASE WHEN ((active_util NOTNULL) AND (price <> _price)) THEN current_timestamp + interval '1 month' * (balance / _price) ELSE active_util END
  WHERE advertiser_id = _advertiser_id;;

  -- regenerate the bill
  PERFORM advert.generate_bills(_advertiser_id);;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

--------------------------------------------------------------

CREATE OR REPLACE FUNCTION advert.get_business(IN _advertiser_id INTEGER)
  RETURNS TABLE (_business_name TEXT, _contact_number TEXT, _website TEXT, _address TEXT, _description TEXT, _photo_limit INTEGER, _photos TEXT ARRAY, _active_util TIMESTAMPTZ, _status TEXT, _suspended_reason TEXT, _issued_bill BOOLEAN, _subscribed BOOLEAN) AS $$
BEGIN
  PERFORM advert.manage_accounts(_advertiser_id);;

  RETURN QUERY
    SELECT COALESCE(av.business_name, ''), av.contact_number, av.website, COALESCE(av.address, ''), COALESCE(av.description, ''), ad.photo_limit, COALESCE(av.photos[1:ad.photo_limit], '{}'), ad.active_util, ad.status::TEXT, ad.suspended_reason, EXISTS(SELECT 1 FROM advert.get_latest_bill(_advertiser_id) WHERE _bill_status = 'issued'), payment_method = 'subscription'::payment_method
    FROM advert.advertisers ad LEFT JOIN advert.adverts av on (ad.advertiser_id = av.advertiser_id)
    WHERE ad.advertiser_id = _advertiser_id;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

--------------------------------------------------------------
