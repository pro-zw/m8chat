# --- !Ups

DROP FUNCTION IF EXISTS advert.get_latest_bill(INTEGER);

CREATE OR REPLACE FUNCTION advert.get_latest_bill(IN _advertiser_id INTEGER)
  RETURNS TABLE (_bill_id INTEGER, _issued_at TIMESTAMPTZ, _paid_at TIMESTAMPTZ, _expiring_at TIMESTAMPTZ, _canceled_at TIMESTAMPTZ, _amount NUMERIC, _description TEXT, _bill_status TEXT, _account_status TEXT, _payment_method TEXT) AS $$
BEGIN
  UPDATE advert.bills
  SET status = 'expired'::bill_status, description = 'The bill is expired. You can choose to send a new bill and pay it to re-active your advert'
  WHERE advertiser_id = _advertiser_id AND status = 'issued'::bill_status AND (expiring_at NOTNULL AND expiring_at <= current_timestamp);;

  RETURN QUERY
    SELECT ab.bill_id, ab.issued_at, ab.paid_at, ab.expiring_at, ab.canceled_at, ab.amount, ab.description, ab.status::TEXT, ad.status::TEXT, ad.payment_method::TEXT
    FROM advert.bills ab INNER JOIN advert.advertisers ad ON (ab.advertiser_id = ad.advertiser_id)
    WHERE ab.advertiser_id = _advertiser_id
    ORDER BY ab.issued_at DESC
    LIMIT 1;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

DROP FUNCTION IF EXISTS advert.begin_pay_next_subscription_bill();

CREATE OR REPLACE FUNCTION advert.begin_pay_next_subscription_bill()
  RETURNS TABLE (_bill_id INTEGER, _advertiser_id INTEGER, _email TEXT, _name TEXT, _stripe_customer_id TEXT, _amount NUMERIC) AS $$
BEGIN
  RETURN QUERY
    SELECT ab.bill_id, ad.advertiser_id, ad.email, ad.name, ad.stripe_customer_id, ab.amount
    FROM advert.bills ab INNER JOIN advert.advertisers ad ON (ab.advertiser_id = ad.advertiser_id AND (ad.status = 'confirmed'::advertiser_status OR ad.status = 'active'::advertiser_status) AND ad.stripe_customer_id NOTNULL AND ad.payment_method = 'subscription'::payment_method)
    WHERE ab.status = 'issued'::bill_status AND (ab.expiring_at ISNULL OR (expiring_at > current_timestamp AND age(ab.expiring_at, current_timestamp) < '2 days'))
    ORDER BY ab.expiring_at ASC NULLS LAST
    LIMIT 1;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

DROP FUNCTION IF EXISTS social.get_chats(INTEGER);

CREATE OR REPLACE FUNCTION social.get_chats(IN _current_user_id INTEGER)
  RETURNS TABLE (_chat_id INTEGER, _sender_id INTEGER, _first_picture TEXT, _fb_user_id TEXT, _fb_username TEXT, _first_name TEXT, _gender TEXT, _last_message TEXT, _last_message_at TIMESTAMPTZ, _last_message_is_read BOOLEAN) AS $$
BEGIN
  RETURN QUERY
  WITH m8_chats AS (
      SELECT
        ch.chat_id,
        m.sender_id,
        (ch.participants - _current_user_id)[1] AS recipient_id,
        u.first_name,
        u.gender::TEXT,
        m.message,
        m.sent_at,
        (ch.last_read_by::hstore -> _current_user_id::TEXT) NOTNULL AS last_message_is_read
      FROM social.chats AS ch
        INNER JOIN social.messages AS m
          ON (m.chat_id = ch.chat_id AND m.message_id = ch.last_message_id)
        INNER JOIN social.m8_users u
          ON (m.sender_id = u.user_id)
      WHERE ARRAY[_current_user_id] <@ ch.participants AND (array_length(ch.participants, 1) > 2 OR (NOT (ch.participants && u.byed_users) AND u.blocked_times < 7))
      LIMIT 100
  )
  SELECT chs.chat_id, chs.sender_id, r.pictures[1], r.fb_user_id, r.fb_username, chs.first_name, chs.gender, chs.message, chs.sent_at, chs.last_message_is_read
  FROM m8_chats chs INNER JOIN social.m8_users r ON (r.user_id = chs.recipient_id)
  ORDER BY chs.sent_at DESC;;
END;;
$$
LANGUAGE 'plpgsql' STABLE;
