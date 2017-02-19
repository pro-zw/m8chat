# --- !Ups

-- login (social)
CREATE OR REPLACE FUNCTION social.login(IN _identity TEXT, IN _password TEXT, OUT _access_token TEXT, OUT _gender TEXT)
  AS $$
BEGIN
  UPDATE social.m8_users
  SET authorized_at = current_timestamp
  WHERE (lower(email) = lower(_identity) OR lower(username) = lower(_identity)) AND "password" = _password AND NOT deleted;;

  UPDATE social.m8_users
  SET access_token = uuid_generate_v4()
  WHERE (lower(email) = lower(_identity) OR lower(username) = lower(_identity)) AND "password" = _password AND (age(current_timestamp, authorized_at) >= '7 days' OR access_token IS NULL) AND NOT deleted;;

  SELECT access_token, gender::TEXT INTO _access_token, _gender
  FROM social.m8_users
  WHERE (lower(email) = lower(_identity) OR lower(username) = lower(_identity)) AND "password" = _password AND NOT deleted;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

-- authenticate (social)
CREATE OR REPLACE FUNCTION social.authenticate(IN _access_token TEXT)
  RETURNS TABLE (_user_id INTEGER, _email TEXT, _username TEXT, _blocked BOOLEAN) AS $$
BEGIN
  UPDATE social.m8_users
  SET authorized_at = current_timestamp
  WHERE access_token = _access_token AND age(current_timestamp, authorized_at) < '7 days' AND blocked_times < 7 AND NOT deleted;;

  RETURN QUERY
    SELECT user_id, email::TEXT, username::TEXT, blocked_times >= 7 AS _blocked
    FROM social.m8_users
    WHERE access_token = _access_token AND age(current_timestamp, authorized_at) < '7 days' AND NOT deleted;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

-- login (advert)
CREATE OR REPLACE FUNCTION advert.login(IN _email TEXT, IN _password TEXT)
  RETURNS TABLE (_status TEXT, _access_token TEXT) AS $$
BEGIN
  UPDATE advert.advertisers
  SET authorized_at = current_timestamp
  WHERE lower(email) = lower(_email) AND "password" = _password AND (status = 'confirmed'::advertiser_status OR status = 'active'::advertiser_status);;

  UPDATE advert.advertisers
  SET access_token = uuid_generate_v4()
  WHERE lower(email) = lower(_email) AND "password" = _password AND (status = 'confirmed'::advertiser_status OR status = 'active'::advertiser_status) AND (age(current_timestamp, authorized_at) >= '7 days' OR access_token IS NULL);;

  RETURN QUERY
    SELECT status::TEXT, access_token
    FROM advert.advertisers
    WHERE lower(email) = lower(_email) AND "password" = _password;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

-- authenticate (advert)
CREATE OR REPLACE FUNCTION advert.authenticate(IN _access_token TEXT)
  RETURNS TABLE (_advertiser_id INTEGER, _name TEXT, _email TEXT, _photo_limit INTEGER) AS $$
BEGIN
  UPDATE advert.advertisers
  SET authorized_at = current_timestamp
  WHERE access_token = _access_token AND age(current_timestamp, authorized_at) < '7 days' AND (status = 'confirmed'::advertiser_status OR status = 'active'::advertiser_status);;

  RETURN QUERY
    SELECT advertiser_id, "name"::TEXT, email::TEXT, photo_limit
    FROM advert.advertisers
    WHERE access_token = _access_token AND age(current_timestamp, authorized_at) < '7 days' AND (status = 'confirmed'::advertiser_status OR status = 'active'::advertiser_status);;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

-- new_chat (new chat)
CREATE OR REPLACE FUNCTION social.new_chat(IN _current_user_id INTEGER, IN _recipients INTEGER ARRAY, IN _message TEXT, OUT _chat_id INTEGER)
  AS $$
DECLARE
  _filtered_recipients INTEGER ARRAY;;
  _last_message_id INTEGER;;
BEGIN
  _filtered_recipients := uniq(sort(_recipients - _current_user_id));;

  UPDATE social.m8_users
  SET friends = uniq(sort(friends | (_filtered_recipients - byed_users)))
  WHERE user_id = _current_user_id;;

  UPDATE social.m8_users
  SET friends = uniq(sort(friends | (((_filtered_recipients - user_id) | _current_user_id) - byed_users)))
  WHERE _filtered_recipients @@ user_id::TEXT::query_int;;

  INSERT INTO social.chats (participants, last_read_by)
  VALUES (_filtered_recipients | _current_user_id, hstore(_current_user_id::TEXT, current_timestamp::TEXT))
  RETURNING chat_id INTO _chat_id;;

  INSERT INTO social.messages (sender_id, chat_id, "message")
  VALUES (_current_user_id, _chat_id, _message)
  RETURNING message_id INTO _last_message_id;;

  UPDATE social.chats
  SET last_message_id = _last_message_id
  WHERE chat_id = _chat_id;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

-- new_message (existing chat)
CREATE OR REPLACE FUNCTION social.new_message(IN _current_user_id INTEGER, IN _chat_id INTEGER, IN _message TEXT)
  RETURNS VOID AS $$
DECLARE
  _last_message_id INTEGER;;
BEGIN
  INSERT INTO social.messages (sender_id, chat_id, "message")
  VALUES (_current_user_id, _chat_id, _message)
  RETURNING message_id INTO _last_message_id;;

  UPDATE social.chats
  SET last_read_by = hstore(_current_user_id::TEXT, current_timestamp::TEXT), last_message_id = _last_message_id
  WHERE chat_id = _chat_id;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

-- get_chats
CREATE OR REPLACE FUNCTION social.get_chats(IN _current_user_id INTEGER)
  RETURNS TABLE (_chat_id INTEGER, _sender_id INTEGER, _first_picture TEXT, _first_name TEXT, _gender TEXT, _last_message TEXT, _last_message_at TIMESTAMPTZ, _last_message_is_read BOOLEAN) AS $$
BEGIN
  RETURN QUERY
    SELECT ch.chat_id, m.sender_id, u.pictures[1], u.first_name::TEXT, u.gender::TEXT, m.message, m.sent_at, (ch.last_read_by::hstore->_current_user_id::TEXT) NOTNULL
    FROM social.chats AS ch INNER JOIN social.messages AS m ON (m.chat_id = ch.chat_id AND m.message_id = ch.last_message_id) INNER JOIN social.m8_users u ON ((ch.participants @@  _current_user_id::TEXT::query_int) AND m.sender_id = u.user_id)
    WHERE array_length(ch.participants, 1) > 2 OR (NOT (ch.participants && u.byed_users) AND blocked_times < 7)
    ORDER BY m.sent_at DESC
    LIMIT 100;;
END;;
$$
LANGUAGE 'plpgsql' STABLE;

-- get_chat_messages
CREATE OR REPLACE FUNCTION social.get_chat_messages(IN _current_user_id INTEGER, IN _chat_id INTEGER, IN _limit INTEGER)
  RETURNS TABLE (_sender_id INTEGER, _first_picture TEXT, _first_name TEXT, _gender TEXT, _message TEXT, _sent_at TIMESTAMPTZ) AS $$
BEGIN
  UPDATE social.chats
  SET last_read_by = (last_read_by || hstore(_current_user_id::TEXT, current_timestamp::TEXT))
  WHERE chat_id = _chat_id;;

  RETURN QUERY
    SELECT m.sender_id, u.pictures[1], u.first_name::TEXT, u.gender::TEXT, m.message, m.sent_at
    FROM social.chats AS ch INNER JOIN social.messages AS m ON (m.chat_id = ch.chat_id) INNER JOIN social.m8_users u ON (((ch.participants @@  _current_user_id::TEXT::query_int) OR m.sender_id = _current_user_id) AND m.sender_id = u.user_id)
    WHERE ch.chat_id = _chat_id
    ORDER BY m.sent_at DESC
    LIMIT _limit;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

-- leave_chat
CREATE OR REPLACE FUNCTION social.leave_chat(IN _current_user_id INTEGER, IN _chat_id INTEGER, OUT _update_count INTEGER)
  AS $$
BEGIN
  UPDATE social.chats
  SET participants = participants - _current_user_id
  WHERE chat_id = _chat_id;;

  GET DIAGNOSTICS _update_count = ROW_COUNT;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

-- list_adverts_nearby
CREATE OR REPLACE FUNCTION advert.list_adverts_nearby(IN _current_user_id INTEGER, IN _page INTEGER, IN _page_size INTEGER)
  RETURNS TABLE (_advert_id INTEGER, _business_name TEXT, _first_photo TEXT, _plan_name TEXT) AS $$
DECLARE
  _page_offset INTEGER := _page * _page_size;;
BEGIN
  UPDATE advert.adverts
  SET displayed_times = displayed_times + 1
  WHERE advert_id in (
    SELECT av.advert_id
    FROM advert.adverts av INNER JOIN social.m8_users AS u ON (u.user_id = _current_user_id) INNER JOIN advert.advertisers ar ON (av.advertiser_id = ar.advertiser_id AND ar.status = 'active'::advertiser_status AND ceil(ST_Distance(av.position, u.position)/1000.0)::INTEGER <= 30)
    ORDER BY ar.plan_name DESC, ceil(ST_Distance(av.position, u.position)/1000.0)::INTEGER ASC, av.business_name ASC
    OFFSET _page_offset
    LIMIT _page_size
  );;

  RETURN QUERY
    SELECT av.advert_id, av.business_name::TEXT, av.photos[1], ar.plan_name::TEXT
    FROM advert.adverts av INNER JOIN social.m8_users AS u ON (u.user_id = _current_user_id) INNER JOIN advert.advertisers ar ON (av.advertiser_id = ar.advertiser_id AND ar.status = 'active'::advertiser_status AND ceil(ST_Distance(av.position, u.position)/1000.0)::INTEGER <= 30)
    ORDER BY ar.plan_name DESC, ceil(ST_Distance(av.position, u.position)/1000.0)::INTEGER ASC, av.business_name ASC
    OFFSET _page_offset
    LIMIT _page_size;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

-- get_single_advert
CREATE OR REPLACE FUNCTION advert.get_single_advert(IN _advert_id INTEGER)
  RETURNS TABLE (_business_name TEXT, _photos TEXT ARRAY, _description TEXT, _phone TEXT, _email TEXT, _website TEXT, _address TEXT) AS $$
BEGIN
  RETURN QUERY
    SELECT av.business_name::TEXT, av.photos[1:ar.photo_limit], av.description, av.contact_number::TEXT, ar.email::TEXT, av.website, av.address
    FROM advert.adverts av INNER JOIN advert.advertisers ar ON (av.advertiser_id = ar.advertiser_id)
    WHERE av.advert_id = _advert_id;;
END;;
$$
LANGUAGE 'plpgsql' STABLE;

-- forgot_password (social)
CREATE OR REPLACE FUNCTION social.forgot_password(IN _identity TEXT, IN _secret TEXT, OUT _update_count INTEGER, OUT _user_id INTEGER, OUT _email TEXT, OUT _reset_digest TEXT)
  AS $$
BEGIN
  UPDATE social.m8_users
  SET pwd_reset_token = uuid_generate_v4(), pwd_reset_expiring_at = current_timestamp + interval '7 days'
  WHERE email = lower(_identity) OR username = _identity;;

  GET DIAGNOSTICS _update_count = ROW_COUNT;;

  IF _update_count NOTNULL AND _update_count > 0 THEN
    SELECT user_id, email::TEXT, encode(digest(pwd_reset_token || _secret, 'sha1'), 'hex') INTO _user_id, _email, _reset_digest
    FROM social.m8_users
    WHERE email = lower(_identity) OR username = _identity;;
  END IF;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

-- get_name_of_password_reset (social)
CREATE OR REPLACE FUNCTION social.get_name_of_password_reset(IN _user_id INTEGER, IN _secret TEXT, IN _reset_digest TEXT)
  RETURNS TEXT AS $$
  SELECT first_name
  FROM social.m8_users
  WHERE user_id = _user_id AND encode(digest(pwd_reset_token || _secret, 'sha1'), 'hex') = _reset_digest AND current_timestamp < pwd_reset_expiring_at;;
$$
LANGUAGE 'sql' STABLE;

-- reset_password (social)
CREATE OR REPLACE FUNCTION social.reset_password(IN _user_id INTEGER, IN _secret TEXT, IN _reset_digest TEXT, IN _new_password TEXT, OUT _update_count INTEGER)
  AS $$
BEGIN
  UPDATE social.m8_users
  SET "password" = _new_password, pwd_reset_token = DEFAULT, pwd_reset_expiring_at = DEFAULT
  WHERE user_id = _user_id AND encode(digest(pwd_reset_token || _secret, 'sha1'), 'hex') = _reset_digest AND current_timestamp < pwd_reset_expiring_at;;

  GET DIAGNOSTICS _update_count = ROW_COUNT;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

-- forgot_password (advert)
CREATE OR REPLACE FUNCTION advert.forgot_password(INOUT _email TEXT, IN _secret TEXT, OUT _update_count INTEGER, OUT _advertiser_id INTEGER, OUT _reset_digest TEXT)
  AS $$
BEGIN
  UPDATE advert.advertisers
  SET pwd_reset_token = uuid_generate_v4(), pwd_reset_expiring_at = current_timestamp + interval '7 days'
  WHERE email = lower(_email);;

  GET DIAGNOSTICS _update_count = ROW_COUNT;;

  IF _update_count NOTNULL AND _update_count > 0 THEN
    SELECT email, advertiser_id, encode(digest(pwd_reset_token || _secret, 'sha1'), 'hex') INTO _email, _advertiser_id, _reset_digest
    FROM advert.advertisers
    WHERE email = lower(_email);;
  END IF;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

-- get_name_of_password_reset (advert)
CREATE OR REPLACE FUNCTION advert.get_name_of_password_reset(IN _advertiser_id INTEGER, IN _secret TEXT, IN _reset_digest TEXT)
  RETURNS TEXT AS $$
  SELECT "name"
  FROM advert.advertisers
  WHERE advertiser_id = _advertiser_id AND encode(digest(pwd_reset_token || _secret, 'sha1'), 'hex') = _reset_digest AND current_timestamp < pwd_reset_expiring_at;;
$$
LANGUAGE 'sql' STABLE;

-- reset_password (advert)
CREATE OR REPLACE FUNCTION advert.reset_password(IN _advertiser_id INTEGER, IN _secret TEXT, IN _reset_digest TEXT, IN _new_password TEXT, OUT _update_count INTEGER)
  AS $$
BEGIN
  UPDATE advert.advertisers
  SET "password" = _new_password, pwd_reset_token = DEFAULT, pwd_reset_expiring_at = DEFAULT
  WHERE advertiser_id = _advertiser_id AND encode(digest(pwd_reset_token || _secret, 'sha1'), 'hex') = _reset_digest AND current_timestamp < pwd_reset_expiring_at;;

  GET DIAGNOSTICS _update_count = ROW_COUNT;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

# --- !Downs
DROP FUNCTION IF EXISTS social.login(TEXT, TEXT);
DROP FUNCTION IF EXISTS social.authenticate(TEXT);
DROP FUNCTION IF EXISTS social.new_chat(INTEGER, INTEGER ARRAY, TEXT, INTEGER);
DROP FUNCTION IF EXISTS social.new_message(INTEGER, INTEGER, TEXT);
DROP FUNCTION IF EXISTS social.forgot_password(TEXT, TEXT);
DROP FUNCTION IF EXISTS social.get_name_of_password_reset(INTEGER, TEXT, TEXT);
DROP FUNCTION IF EXISTS social.reset_password(INTEGER, TEXT, TEXT, TEXT);
DROP FUNCTION IF EXISTS social.get_chats(INTEGER);
DROP FUNCTION IF EXISTS social.leave_chat(INTEGER, INTEGER);
DROP FUNCTION IF EXISTS social.get_chat_messages(INTEGER, INTEGER, INTEGER);

DROP FUNCTION IF EXISTS advert.login(TEXT, TEXT);
DROP FUNCTION IF EXISTS advert.authenticate(TEXT);
DROP FUNCTION IF EXISTS advert.list_adverts_nearby(INTEGER, INTEGER, INTEGER);
DROP FUNCTION IF EXISTS advert.get_single_advert(INTEGER);
DROP FUNCTION IF EXISTS advert.forgot_password(TEXT, TEXT);
DROP FUNCTION IF EXISTS advert.get_name_of_password_reset(INTEGER, TEXT, TEXT);
DROP FUNCTION IF EXISTS advert.reset_password(INTEGER, TEXT, TEXT, TEXT);
