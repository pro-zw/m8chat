# --- !Ups

CREATE OR REPLACE FUNCTION social.new_message(IN _current_user_id INTEGER, IN _chat_id INTEGER, IN _message TEXT, OUT _message_id INTEGER) AS $$
BEGIN
  INSERT INTO social.messages (sender_id, chat_id, "message", read_by)
  VALUES (_current_user_id, _chat_id, _message, ARRAY[_current_user_id])
  RETURNING message_id INTO _message_id;;

  UPDATE social.chats
  SET last_read_by = hstore(_current_user_id::TEXT, current_timestamp::TEXT), last_message_id = _message_id, hidden_by = DEFAULT
  WHERE chat_id = _chat_id;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

--------------------------------------------------------------

-- This one should be executed manually
-- ALTER TYPE bill_status ADD VALUE 'paying' AFTER 'paid';

ALTER TABLE advert.bills
  ADD COLUMN paying_at TIMESTAMPTZ;

DROP FUNCTION IF EXISTS advert.begin_pay_next_subscription_bill();

DROP FUNCTION IF EXISTS advert.begin_pay_subscription_bills();
CREATE OR REPLACE FUNCTION advert.begin_pay_subscription_bills()
  RETURNS TABLE (_bill_id INTEGER, _advertiser_id INTEGER, _email TEXT, _name TEXT, _stripe_customer_id TEXT, _amount NUMERIC) AS $$
BEGIN
  RETURN QUERY
    WITH bills_to_pay AS (
      UPDATE advert.bills AS ab
      SET status = 'paying'::bill_status, paying_at = current_timestamp
      FROM advert.advertisers ad
      WHERE ab.advertiser_id = ad.advertiser_id AND bill_id IN (
        SELECT ab.bill_id
        FROM advert.bills ab INNER JOIN advert.advertisers ad ON (ab.advertiser_id = ad.advertiser_id AND (ad.status = 'confirmed'::advertiser_status OR ad.status = 'active'::advertiser_status) AND ad.stripe_customer_id NOTNULL AND ad.payment_method = 'subscription'::payment_method)
        WHERE ab.status = 'issued'::bill_status AND (ab.expiring_at ISNULL OR (expiring_at > current_timestamp AND age(ab.expiring_at, current_timestamp) < '2 days'))
        ORDER BY ab.expiring_at ASC NULLS LAST
        LIMIT 10
        FOR UPDATE
      )
      RETURNING ab.bill_id, ad.advertiser_id, ad.email, ad.name, ad.stripe_customer_id, ab.amount
    )
    SELECT * FROM bills_to_pay;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

DROP FUNCTION IF EXISTS advert.cancel_pay_subscription_bill(INTEGER);

DROP FUNCTION IF EXISTS advert.cancel_pay_subscription_bill(INTEGER, INTEGER);
CREATE OR REPLACE FUNCTION advert.cancel_pay_subscription_bill(IN _bill_id INTEGER, IN _advertiser_id INTEGER)
  RETURNS VOID AS $$
BEGIN
  PERFORM advert.change_payment_method(_advertiser_id, NULL, 'manual'::payment_method);;

  UPDATE advert.bills
  SET status = 'canceled'::bill_status
  WHERE bill_id = _bill_id AND status = 'paying'::bill_status;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

--------------------------------------------------------------

DROP TRIGGER IF EXISTS trig_bill_email_scheduler ON advert.bills;
DROP FUNCTION IF EXISTS advert.bill_email_scheduler();

DROP FUNCTION IF EXISTS advert.bills_email_scheduler() CASCADE;
CREATE OR REPLACE FUNCTION advert.bills_email_scheduler()
  RETURNS TRIGGER AS $$
BEGIN
  NEW.email_scheduled = FALSE;;
  RETURN NEW;;
END;;
$$
LANGUAGE 'plpgsql' STABLE;

DROP TRIGGER IF EXISTS trig_bills_email_scheduler ON advert.bills;
CREATE TRIGGER trig_bills_email_scheduler
BEFORE UPDATE OF status
  ON advert.bills
  FOR EACH ROW
  WHEN (OLD.status <> 'paying'::bill_status AND NEW.status <> 'paying'::bill_status)
  EXECUTE PROCEDURE advert.bills_email_scheduler();

--------------------------------------------------------------

DROP TRIGGER IF EXISTS trig_last_issued_bill_checker ON advert.bills;
DROP FUNCTION IF EXISTS advert.last_issued_bill_checker();

--------------------------------------------------------------

DROP FUNCTION IF EXISTS advert.generate_initial_bill(INTEGER);

DROP FUNCTION IF EXISTS advert.generate_initial_bill() CASCADE;
CREATE OR REPLACE FUNCTION advert.generate_initial_bill()
  RETURNS TRIGGER AS $$
DECLARE
  _bill_info RECORD;;
BEGIN
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

CREATE OR REPLACE FUNCTION advert.generate_periodical_bills()
  RETURNS VOID AS $$
DECLARE
  _bill_info RECORD;;
BEGIN
  FOR _bill_info IN
    SELECT ad.advertiser_id, active_util, (ad.price - ad.balance) AS amount
    FROM advert.advertisers ad LEFT JOIN advert.bills ab ON (ad.advertiser_id = ab.advertiser_id AND (ab.status = 'issued'::bill_status OR ab.status = 'paying'::bill_status))
    WHERE ad.status = 'active'::advertiser_status AND (ad.price - ad.balance) > 0 AND (ad.active_util NOTNULL AND ad.active_util > current_timestamp AND age(ad.active_util, current_timestamp) < '10 days') AND ab.bill_id ISNULL
  LOOP
    INSERT INTO advert.bills (advertiser_id, amount, expiring_at)
    VALUES (_bill_info.advertiser_id, _bill_info.amount, _bill_info.active_util);;

    RAISE NOTICE 'A periodical bill is generated with advertiser_id: %, amount: %, expiring_at: %', _bill_info.advertiser_id, _bill_info.amount, _bill_info.active_util;;
  END LOOP;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

--------------------------------------------------------------

DROP FUNCTION advert.get_latest_bill(integer);
CREATE OR REPLACE FUNCTION advert.get_latest_bill(IN _advertiser_id INTEGER)
  RETURNS TABLE (_bill_id INTEGER, _issued_at TIMESTAMPTZ, _paid_at TIMESTAMPTZ, _expiring_at TIMESTAMPTZ, _canceled_at TIMESTAMPTZ, _amount NUMERIC, _bill_status TEXT, _account_status TEXT, _payment_method TEXT) AS $$
BEGIN
  PERFORM advert.expire_bills(_advertiser_id);;

  RETURN QUERY
    SELECT ab.bill_id, ab.issued_at, ab.paid_at, ab.expiring_at, ab.canceled_at, ab.amount, ab.status::TEXT, ad.status::TEXT, ad.payment_method::TEXT
    FROM advert.bills ab INNER JOIN advert.advertisers ad ON (ab.advertiser_id = ad.advertiser_id)
    WHERE ab.advertiser_id = _advertiser_id
    ORDER BY ab.issued_at DESC
    LIMIT 1;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

DROP FUNCTION IF EXISTS advert.expire_bill();

DROP FUNCTION IF EXISTS advert.expire_bills(INTEGER);
CREATE OR REPLACE FUNCTION advert.expire_bills(IN _advertiser_id INTEGER)
  RETURNS VOID AS $$
BEGIN
  UPDATE advert.bills
  SET status = 'expired'::bill_status
  WHERE (_advertiser_id ISNULL OR advertiser_id = _advertiser_id) AND (expiring_at NOTNULL AND ((status = 'issued'::bill_status AND expiring_at <= current_timestamp) OR (status = 'paying'::bill_status AND age(current_timestamp, expiring_at) > '6 hours')));;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

--------------------------------------------------------------

DROP TRIGGER IF EXISTS trig_modified_time_stamper ON social.m8_users;
DROP TRIGGER IF EXISTS trig_modified_time_stamper ON advert.advertisers;
DROP FUNCTION IF EXISTS public.modified_time_stamper();

ALTER TABLE social.m8_users
  DROP COLUMN IF EXISTS modified_at;

ALTER TABLE advert.advertisers
  DROP COLUMN IF EXISTS modified_at;

--------------------------------------------------------------

DROP FUNCTION IF EXISTS advert.change_payment_method();
CREATE OR REPLACE FUNCTION advert.change_payment_method(IN _advertiser_id INTEGER, IN _stripe_customer_id TEXT, IN _payment_method payment_method, OUT _update_count INTEGER)
 AS $$
BEGIN
  IF _payment_method = 'subscription'::payment_method THEN
    IF _stripe_customer_id ISNULL THEN
      RAISE EXCEPTION 'Set payment method of the advertiser with id % to subscription without Stripe customer id', advertiser_id USING HINT = 'Forgot to set strip customer id?', ERRCODE = 'P9999';;
    ELSE
      UPDATE advert.advertisers
      SET stripe_customer_id = _stripe_customer_id, payment_method = _payment_method
      WHERE advertiser_id = _advertiser_id;;
    END IF;;
  ELSE
    UPDATE advert.advertisers
    SET payment_method = _payment_method
    WHERE advertiser_id = _advertiser_id;;
  END IF;;

  GET DIAGNOSTICS _update_count = ROW_COUNT;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

--------------------------------------------------------------

DROP FUNCTION IF EXISTS advert.list_adverts_nearby(INTEGER, INTEGER, INTEGER);
CREATE OR REPLACE FUNCTION advert.list_adverts_nearby(IN _current_user_id INTEGER, IN _page INTEGER, IN _page_size INTEGER)
  RETURNS TABLE (_advert_id INTEGER, _business_name TEXT, _first_photo TEXT, _plan_name TEXT, _distance INTEGER) AS $$
DECLARE
  _page_offset INTEGER := _page * _page_size;;
BEGIN
  RETURN QUERY
    WITH adverts_nearby AS (
      UPDATE advert.adverts AS av
      SET displayed_times = displayed_times + 1
      FROM advert.advertisers AS ar INNER JOIN social.m8_users AS u ON (u.user_id = _current_user_id)
      WHERE av.advertiser_id = ar.advertiser_id AND av.advert_id IN (
        SELECT av.advert_id
        FROM advert.adverts av INNER JOIN social.m8_users AS u ON (u.user_id = _current_user_id) INNER JOIN advert.advertisers ar ON (av.advertiser_id = ar.advertiser_id AND ar.status = 'active'::advertiser_status AND ceil(ST_Distance(av.position, u.position)/1000.0)::INTEGER <= 30)
        ORDER BY ar.plan_name DESC, ceil(ST_Distance(av.position, u.position)/1000.0)::INTEGER ASC, av.business_name ASC
        OFFSET _page_offset
        LIMIT _page_size
        FOR UPDATE
      )
      RETURNING av.advert_id, av.business_name::TEXT, av.photos[1], ar.plan_name::TEXT, ceil(ST_Distance(av.position, u.position)/1000.0)::INTEGER AS _cte_distance
    )
    SELECT * FROM adverts_nearby ORDER BY plan_name::plan_name DESC, _cte_distance ASC, business_name ASC;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

--------------------------------------------------------------

DROP FUNCTION advert.get_bills_to_email();
CREATE OR REPLACE FUNCTION advert.get_bills_to_email()
  RETURNS TABLE (_name TEXT, _email TEXT, _issued_at TIMESTAMPTZ, _paid_at TIMESTAMPTZ, _expiring_at TIMESTAMPTZ, _canceled_at TIMESTAMPTZ, _amount NUMERIC, _status TEXT) AS $$
BEGIN
  RETURN QUERY
    WITH bills_to_email AS (
      UPDATE advert.bills AS ab
      SET email_scheduled = TRUE
      FROM advert.advertisers ad
      WHERE NOT ab.email_scheduled AND ab.advertiser_id = ad.advertiser_id
      RETURNING ad.name, ad.email, ab.issued_at, ab.paid_at, ab.expiring_at, ab.canceled_at, ab.amount, ab.status::TEXT, ad.status AS _advertiser_status
    )
    SELECT "name", email, issued_at, paid_at, expiring_at, canceled_at, amount, status::TEXT
    FROM bills_to_email
    WHERE _advertiser_status = 'confirmed'::advertiser_status OR _advertiser_status = 'active'::advertiser_status;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

--------------------------------------------------------------

DROP FUNCTION IF EXISTS advert.get_business(INTEGER);
CREATE OR REPLACE FUNCTION advert.get_business(IN _advertiser_id INTEGER)
  RETURNS TABLE (_business_name TEXT, _contact_number TEXT, _website TEXT, _address TEXT, _description TEXT, _photo_limit INTEGER, _photos TEXT ARRAY, _active_util TIMESTAMPTZ, _status TEXT, _suspended_reason TEXT, _issued_bill BOOLEAN, _subscribed BOOLEAN) AS $$
BEGIN
  PERFORM advert.charge_balance(_advertiser_id);;

  RETURN QUERY
    SELECT COALESCE(av.business_name, ''), av.contact_number, av.website, COALESCE(av.address, ''), COALESCE(av.description, ''), ad.photo_limit, COALESCE(av.photos[1:ad.photo_limit], '{}'), ad.active_util, ad.status::TEXT, ad.suspended_reason, EXISTS(SELECT 1 FROM advert.get_latest_bill(_advertiser_id) WHERE _bill_status = 'issued'), payment_method = 'subscription'::payment_method
    FROM advert.advertisers ad LEFT JOIN advert.adverts av on (ad.advertiser_id = av.advertiser_id)
    WHERE ad.advertiser_id = _advertiser_id;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

--------------------------------------------------------------

DROP FUNCTION IF EXISTS advert.charge_balance();
CREATE OR REPLACE FUNCTION advert.charge_balance(IN _advertiser_id INTEGER)
  RETURNS VOID AS $$
  UPDATE advert.advertisers
  SET balance = balance * (EXTRACT(EPOCH FROM(active_util - current_timestamp))::NUMERIC / EXTRACT(EPOCH FROM(active_util - balance_charged_at))::NUMERIC), balance_charged_at = current_timestamp
  WHERE (_advertiser_id ISNULL OR advertiser_id = _advertiser_id) AND balance > 0 AND active_util NOTNULL AND balance_charged_at NOTNULL AND active_util > current_timestamp AND balance_charged_at < current_timestamp AND status = 'active'::advertiser_status;;

  UPDATE advert.advertisers
  SET status = 'confirmed'::advertiser_status, balance = 0.0, balance_charged_at = DEFAULT
  WHERE (_advertiser_id ISNULL OR advertiser_id = _advertiser_id) AND (balance <= 0 OR (active_util NOTNULL AND active_util <= current_timestamp)) AND status = 'active'::advertiser_status;;
$$
LANGUAGE 'sql' VOLATILE;

--------------------------------------------------------------

CREATE OR REPLACE FUNCTION advert.end_pay_bill(IN _bill_id INTEGER, IN _advertiser_id INTEGER, IN _payment_id TEXT, OUT _update_count INTEGER)
AS $$
DECLARE
  _update_bill_count INTEGER;;
BEGIN
  UPDATE advert.bills
  SET status = 'paid'::bill_status, paid_at = current_timestamp, expiring_at = DEFAULT, canceled_at = DEFAULT, payment_id = _payment_id
  WHERE bill_id = _bill_id AND advertiser_id = _advertiser_id;;

  GET DIAGNOSTICS _update_bill_count = ROW_COUNT;;

  IF _update_bill_count = 1 THEN
    UPDATE advert.advertisers AS ad
    SET active_util = CASE WHEN active_util ISNULL THEN current_timestamp + INTERVAL '1 month' ELSE active_util + INTERVAL '1 month' END, status = 'active'::advertiser_status, balance = balance + ab.amount, balance_charged_at = CASE WHEN balance_charged_at ISNULL THEN current_timestamp ELSE balance_charged_at END
    FROM advert.bills AS ab
    WHERE ad.advertiser_id = _advertiser_id AND ad.advertiser_id = ab.advertiser_id AND ab.bill_id = _bill_id;;

    GET DIAGNOSTICS _update_count = ROW_COUNT;;
  ELSE
    _update_count = 0;;
  END IF;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

ALTER TABLE advert.bills
  DROP COLUMN IF EXISTS description;

--------------------------------------------------------------

-- ALTER TABLE IF EXISTS advert.advertisers DROP CONSTRAINT advertisers_price_check;
-- ALTER TABLE IF EXISTS advert.advertisers ADD CONSTRAINT advertisers_price_check CHECK (price >= 0::numeric);

# --- !Downs

ALTER TABLE advert.bills
  DROP COLUMN IF EXISTS paying_at;
