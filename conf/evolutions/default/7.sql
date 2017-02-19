# --- !Ups

-- confirm_email (advert) (tested)
CREATE OR REPLACE FUNCTION advert.confirm_email(IN _advertiser_id INTEGER, IN _token TEXT, OUT _update_count INTEGER, OUT _access_token TEXT)
  AS $$
BEGIN
  _access_token := uuid_generate_v4();;

  UPDATE advert.advertisers
  SET status = 'confirmed'::advertiser_status, email_confirm_token = DEFAULT, access_token = _access_token, authorized_at = current_timestamp
  WHERE advertiser_id = _advertiser_id AND email_confirm_token = _token AND status = 'registered'::advertiser_status AND price NOTNULL;;

  GET DIAGNOSTICS _update_count = ROW_COUNT;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

-- get_business (tested)
CREATE OR REPLACE FUNCTION advert.get_business(IN _advertiser_id INTEGER)
  RETURNS TABLE (_business_name TEXT, _contact_number TEXT, _website TEXT, _address TEXT, _description TEXT, _photo_limit INTEGER, _photos TEXT ARRAY, _active_util TIMESTAMPTZ, _status TEXT, _suspended_reason TEXT, _issued_bill BOOLEAN) AS $$
BEGIN
  UPDATE advert.advertisers
  SET active_util = DEFAULT, status = 'confirmed'::advertiser_status
  WHERE advertiser_id = _advertiser_id AND status = 'active'::advertiser_status AND (active_util NOTNULL AND active_util <= current_timestamp);;

  RETURN QUERY
    SELECT COALESCE(av.business_name, ''), av.contact_number, av.website, COALESCE(av.address, ''), COALESCE(av.description, ''), ad.photo_limit, COALESCE(av.photos[1:ad.photo_limit], '{}'), ad.active_util, ad.status::TEXT, ad.suspended_reason, EXISTS(SELECT 1 FROM advert.get_latest_bill(_advertiser_id) WHERE _bill_status = 'issued')
    FROM advert.advertisers ad LEFT JOIN advert.adverts av on (ad.advertiser_id = av.advertiser_id)
    WHERE ad.advertiser_id = _advertiser_id;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

-- generate_initial_bill (tested)
CREATE OR REPLACE FUNCTION advert.generate_initial_bill(IN _advertiser_id INTEGER, OUT _new_bill_id INTEGER)
  AS $$
DECLARE
  _balance NUMERIC;;
  _price NUMERIC;;
  _issued_bills INTEGER;;
BEGIN
  SELECT balance, price INTO _balance, _price
  FROM advert.advertisers
  WHERE advertiser_id = _advertiser_id AND status = 'confirmed'::advertiser_status;;

  SELECT COUNT(*) INTO _issued_bills
  FROM advert.bills
  WHERE advertiser_id = _advertiser_id AND status = 'issued'::bill_status;;

  IF (_price > _balance) AND (_issued_bills = 0 OR _issued_bills ISNULL) THEN
    INSERT INTO advert.bills (advertiser_id, amount, description)
    VALUES (_advertiser_id, _price - _balance, 'Pay the bill and your advert can display in the m8chat app for a month from the day the bill is paid')
    RETURNING bill_id INTO _new_bill_id;;
  ELSE
    _new_bill_id = 0;;
  END IF;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

-- generate_periodical_bills (tested)
CREATE OR REPLACE FUNCTION advert.generate_periodical_bills()
  RETURNS VOID AS $$
DECLARE
  _bill_info RECORD;;
BEGIN
  FOR _bill_info IN
    SELECT ad.advertiser_id, active_util, (ad.price - ad.balance) AS amount
    FROM advert.advertisers ad LEFT JOIN advert.bills ab ON (ad.advertiser_id = ab.advertiser_id AND ab.status = 'issued'::bill_status)
    WHERE ad.status = 'active'::advertiser_status AND (ad.price - ad.balance) > 0 AND (ad.active_util NOTNULL AND ad.active_util > current_timestamp AND age(ad.active_util, current_timestamp) < '10 days') AND ab.bill_id ISNULL
  LOOP
    INSERT INTO advert.bills (advertiser_id, amount, expiring_at, description)
    VALUES (_bill_info.advertiser_id, _bill_info.amount, _bill_info.active_util, 'Pay the bill to continue to use m8chat advert service for another month');;

    RAISE NOTICE 'A periodical bill is generated with advertiser_id: %, amount: %, expiring_at: %', _bill_info.advertiser_id, _bill_info.amount, _bill_info.active_util;;
  END LOOP;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

-- get_latest_bill (tested)
CREATE OR REPLACE FUNCTION advert.get_latest_bill(IN _advertiser_id INTEGER)
  RETURNS TABLE (_bill_id INTEGER, _issued_at TIMESTAMPTZ, _paid_at TIMESTAMPTZ, _expiring_at TIMESTAMPTZ, _canceled_at TIMESTAMPTZ, _amount NUMERIC, _description TEXT, _bill_status TEXT, _account_status TEXT) AS $$
BEGIN
  UPDATE advert.bills
  SET status = 'expired'::bill_status, description = 'The bill is expired. You can choose to send a new bill and pay it to re-active your advert'
  WHERE advertiser_id = _advertiser_id AND status = 'issued'::bill_status AND (expiring_at NOTNULL AND expiring_at <= current_timestamp);;

  RETURN QUERY
    SELECT ab.bill_id, ab.issued_at, ab.paid_at, ab.expiring_at, ab.canceled_at, ab.amount, ab.description, ab.status::TEXT, ad.status::TEXT
    FROM advert.bills ab INNER JOIN advert.advertisers ad ON (ab.advertiser_id = ad.advertiser_id)
    WHERE ab.advertiser_id = _advertiser_id
    ORDER BY ab.issued_at DESC
    LIMIT 1;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

-- begin_pay_bill (tested)
-- note there are delay after calling begin_pay_bill and finally calling end_pay_bill, which means we must be careful
-- about modifying relevant bills in the bills table
-- for example, do not issue a new bill async by periodical actors when the latest bill expires
CREATE OR REPLACE FUNCTION advert.begin_pay_bill(IN _bill_id INTEGER, IN _advertiser_id INTEGER, OUT _amount NUMERIC)
  AS $$
BEGIN
  SELECT ab.amount INTO _amount
  FROM advert.bills ab INNER JOIN advert.advertisers ad ON (ab.advertiser_id = ad.advertiser_id AND (ad.status = 'confirmed'::advertiser_status OR ad.status = 'active'::advertiser_status))
  WHERE ab.bill_id = _bill_id AND ab.advertiser_id = _advertiser_id AND ab.status = 'issued'::bill_status AND (ab.expiring_at ISNULL OR ab.expiring_at > current_timestamp) AND ab.amount > 0;;
END;;
$$
LANGUAGE 'plpgsql' STABLE;

-- check_pay_bill (tested)
-- the paying is already in process. Most checking is already done in begin_pay_bill
CREATE OR REPLACE FUNCTION advert.check_pay_bill(IN _bill_id INTEGER, IN _advertiser_id INTEGER, OUT _exist BOOLEAN)
  AS $$
BEGIN
  SELECT EXISTS(
      SELECT 1
      FROM advert.bills
      WHERE bill_id = _bill_id AND advertiser_id = _advertiser_id
  ) INTO _exist;;
END;;
$$
LANGUAGE 'plpgsql' STABLE;

-- end_pay_bill (tested)
-- the paying is already executed. Most checking is already done in begin_pay_bill & check_pay_bill
CREATE OR REPLACE FUNCTION advert.end_pay_bill(IN _bill_id INTEGER, IN _advertiser_id INTEGER, IN _payment_id TEXT, OUT _update_count INTEGER)
  AS $$
DECLARE
  _amount DECIMAL;;
  _update_bill_count INTEGER;;
BEGIN
  UPDATE advert.bills
  SET status = 'paid'::bill_status, paid_at = current_timestamp, expiring_at = DEFAULT, canceled_at = DEFAULT, description = 'The bill is paid', payment_id = _payment_id
  WHERE bill_id = _bill_id AND advertiser_id = _advertiser_id;;

  GET DIAGNOSTICS _update_bill_count = ROW_COUNT;;

  IF _update_bill_count = 1 THEN
    SELECT amount INTO _amount
    FROM advert.bills
    WHERE bill_id = _bill_id AND advertiser_id = _advertiser_id;;

    UPDATE advert.advertisers
    SET active_util = CASE WHEN active_util ISNULL THEN current_timestamp + INTERVAL '1 month' ELSE active_util + INTERVAL '1 month' END, status = 'active'::advertiser_status, balance = balance + _amount, balance_charged_at = CASE WHEN balance_charged_at ISNULL THEN current_timestamp ELSE balance_charged_at END
    WHERE advertiser_id = _advertiser_id;;

    GET DIAGNOSTICS _update_count = ROW_COUNT;;
  ELSE
    _update_count = 0;;
  END IF;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

-- charge_balance (tested)
CREATE OR REPLACE FUNCTION advert.charge_balance()
  RETURNS VOID AS $$
  UPDATE advert.advertisers
  SET balance = balance * (EXTRACT(EPOCH FROM(active_util - current_timestamp))::NUMERIC / EXTRACT(EPOCH FROM(active_util - balance_charged_at))::NUMERIC), balance_charged_at = current_timestamp
  WHERE balance > 0 AND active_util NOTNULL AND balance_charged_at NOTNULL AND active_util > current_timestamp AND balance_charged_at < current_timestamp AND status = 'active'::advertiser_status;;

  UPDATE advert.advertisers
  SET status = 'confirmed'::advertiser_status, balance = 0.0, balance_charged_at = DEFAULT
  WHERE (balance <= 0 OR (active_util NOTNULL AND active_util <= current_timestamp)) AND status = 'active'::advertiser_status;;
$$
LANGUAGE 'sql' VOLATILE;

-- expire_bill (tested)
CREATE OR REPLACE FUNCTION advert.expire_bill()
  RETURNS VOID AS $$
  UPDATE advert.bills
  SET status = 'expired'::bill_status
  WHERE status = 'issued'::bill_status AND (expiring_at NOTNULL AND expiring_at <= current_timestamp);;
$$
LANGUAGE 'sql' VOLATILE;

-- trigger ensuring that not insert another 'issued' bill while the one exists (tested)
CREATE OR REPLACE FUNCTION advert.last_issued_bill_checker()
  RETURNS TRIGGER AS $$
DECLARE
  _issued_bill_exists BOOLEAN;;
BEGIN
  SELECT (_bill_status = 'issued') INTO _issued_bill_exists
  FROM advert.get_latest_bill(NEW.advertiser_id)
  WHERE _bill_status NOTNULL;;

  IF (_issued_bill_exists NOTNULL AND _issued_bill_exists) THEN
    RAISE EXCEPTION 'A bill is already issued for the advertiser with id %', NEW.advertiser_id USING HINT = 'Forgot to update and check the latest bill status?', ERRCODE = 'P9999';;
    RETURN NULL;;
  ELSE
    RETURN NEW;;
  END IF;;
END;;
$$
LANGUAGE 'plpgsql' STABLE;

CREATE TRIGGER trig_last_issued_bill_checker
BEFORE INSERT
  ON advert.bills
  FOR EACH ROW
  EXECUTE PROCEDURE advert.last_issued_bill_checker();;

-- trigger ensuring that bill email is scheduled whenever its status changes
CREATE OR REPLACE FUNCTION advert.bill_email_scheduler()
  RETURNS TRIGGER AS $$
BEGIN
  NEW.email_scheduled = FALSE;;
  RETURN NEW;;
END;;
$$
LANGUAGE 'plpgsql' STABLE;

CREATE TRIGGER trig_bill_email_scheduler
BEFORE UPDATE OF status
  ON advert.bills
  FOR EACH ROW
  EXECUTE PROCEDURE advert.bill_email_scheduler();;

-- get_bills_to_email (tested)
CREATE OR REPLACE FUNCTION advert.get_bills_to_email()
  RETURNS TABLE (_name TEXT, _email TEXT, _issued_at TIMESTAMPTZ, _paid_at TIMESTAMPTZ, _expiring_at TIMESTAMPTZ, _canceled_at TIMESTAMPTZ, _amount NUMERIC, _description TEXT, _status TEXT) AS $$
BEGIN
  CREATE TEMP TABLE bills_to_email ON COMMIT DROP AS
    SELECT ad.name, ad.email, ab.issued_at, ab.paid_at, ab.expiring_at, ab.canceled_at, ab.amount, ab.description, ab.status::TEXT
    FROM advert.bills ab INNER JOIN advert.advertisers ad ON (ab.advertiser_id = ad.advertiser_id AND ad.status = 'confirmed'::advertiser_status OR ad.status = 'active'::advertiser_status)
    WHERE NOT ab.email_scheduled
    ORDER BY issued_at ASC;;

  UPDATE advert.bills
  SET email_scheduled = TRUE
  WHERE NOT email_scheduled;;

  RETURN QUERY
    SELECT * FROM bills_to_email;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

# --- !Downs

DROP FUNCTION IF EXISTS advert.confirm_email(INTEGER, TEXT);
DROP FUNCTION IF EXISTS advert.get_business(INTEGER);

DROP FUNCTION IF EXISTS advert.generate_initial_bill(INTEGER);
DROP FUNCTION IF EXISTS advert.generate_periodical_bills();
DROP FUNCTION IF EXISTS advert.get_latest_bill(INTEGER);
DROP FUNCTION IF EXISTS advert.begin_pay_bill(INTEGER, INTEGER);
DROP FUNCTION IF EXISTS advert.check_pay_bill(INTEGER, INTEGER);
DROP FUNCTION IF EXISTS advert.end_pay_bill(INTEGER, INTEGER, TEXT);
DROP FUNCTION IF EXISTS advert.charge_balance();
DROP FUNCTION IF EXISTS advert.expire_bill();

DROP TRIGGER IF EXISTS trig_last_issued_bill_checker ON advert.bills;
DROP FUNCTION IF EXISTS advert.last_issued_bill_checker();

DROP TRIGGER IF EXISTS trig_bill_email_scheduler ON advert.bills;
DROP FUNCTION IF EXISTS advert.bill_email_scheduler();

DROP FUNCTION IF EXISTS advert.get_bills_to_email();