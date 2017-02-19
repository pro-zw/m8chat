# --- !Ups

DROP FUNCTION IF EXISTS social.login(TEXT, TEXT);

CREATE OR REPLACE FUNCTION social.login(IN _identity TEXT, IN _password TEXT, OUT _user_id INTEGER, OUT _access_token TEXT, OUT _gender TEXT, OUT _fb_user_id TEXT, OUT _fb_username TEXT)
AS $$
BEGIN
  UPDATE social.m8_users
  SET authorized_at = current_timestamp
  WHERE (lower(email) = lower(_identity) OR lower(username) = lower(_identity)) AND "password" = _password AND NOT deleted;;

  UPDATE social.m8_users
  SET access_token = uuid_generate_v4()
  WHERE (lower(email) = lower(_identity) OR lower(username) = lower(_identity)) AND "password" = _password AND (age(current_timestamp, authorized_at) >= '7 days' OR access_token IS NULL) AND NOT deleted;;

  SELECT user_id, access_token, gender::TEXT, fb_user_id, fb_username INTO _user_id, _access_token, _gender, _fb_user_id, _fb_username
  FROM social.m8_users
  WHERE (lower(email) = lower(_identity) OR lower(username) = lower(_identity)) AND "password" = _password AND NOT deleted;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

DROP FUNCTION IF EXISTS social.list_users_nearby(INTEGER, INTEGER, INTEGER);

CREATE OR REPLACE FUNCTION social.list_users_nearby(IN _current_user_id INTEGER, IN _page INTEGER, IN _page_size INTEGER)
  RETURNS TABLE (_user_id INTEGER, _first_picture TEXT, _first_name TEXT, _distance INTEGER, _gender TEXT, _is_friend BOOLEAN, _fb_user_id TEXT, _fb_username TEXT) AS $$
DECLARE
  _page_offset INTEGER := _page * _page_size;;
BEGIN
  RETURN QUERY
  WITH m8_current_user AS (
    SELECT
      user_id,
      byed_users,
      "position",
      gender,
      prefer_gender,
      friends
    FROM social.m8_users AS cu
    WHERE cu.user_id = _current_user_id
  )
  SELECT u.user_id AS _user_id, u.pictures[1] AS _first_picture, u.first_name::TEXT AS _first_name, ceil(ST_Distance(u.position, cu.position)/1000.0)::INTEGER AS _distance, u.gender::Text AS _gender, (ARRAY[u.user_id] <@ cu.friends) AS _is_friend, u.fb_user_id AS _fb_user_id, u.fb_username AS _fb_username
  FROM social.m8_users AS u, m8_current_user AS cu
  WHERE u.user_id != _current_user_id AND NOT u.deleted AND u.blocked_times < 7 AND NOT (ARRAY[u.user_id] <@ cu.byed_users) AND NOT (ARRAY[cu.user_id] <@ u.byed_users) AND cu.position IS NOT NULL AND u.position IS NOT NULL AND authorized_at IS NOT NULL AND age(current_timestamp, authorized_at) <= '28 days' AND ceil(ST_Distance(u.position, cu.position)/1000.0)::INTEGER <= 30 AND ((cu.prefer_gender = 'Both'::gender OR cu.prefer_gender = u.gender) AND (u.prefer_gender = 'Both'::gender OR u.prefer_gender = cu.gender))
  ORDER BY _distance ASC, _first_name ASC
  OFFSET _page_offset
  LIMIT _page_size;;
END;;
$$
LANGUAGE 'plpgsql' STABLE;

DROP FUNCTION IF EXISTS social.search_users(INTEGER, TEXT, INTEGER, INTEGER);

CREATE OR REPLACE FUNCTION social.search_users(IN _current_user_id INTEGER, IN _criteria TEXT, IN _page INTEGER, IN _page_size INTEGER)
  RETURNS TABLE (_user_id INTEGER, _first_picture TEXT, _first_name TEXT, _distance INTEGER, _gender TEXT, _is_friend BOOLEAN, _fb_user_id TEXT, _fb_username TEXT) AS $$
DECLARE
  _page_offset INTEGER := _page * _page_size;;
BEGIN
  RETURN QUERY
  WITH m8_current_user AS (
      SELECT
        user_id,
        byed_users,
        "position",
        gender,
        prefer_gender,
        friends
      FROM social.m8_users AS cu
      WHERE cu.user_id = _current_user_id
  )
  SELECT u.user_id AS _user_id, u.pictures[1] AS _first_picture, u.first_name::TEXT AS _first_name, ceil(ST_Distance(u.position, cu.position)/1000.0)::INTEGER AS _distance, u.gender::Text AS _gender, (ARRAY[u.user_id] <@ cu.friends) AS _is_friend, u.fb_user_id AS _fb_user_id, u.fb_username AS _fb_username
  FROM social.m8_users AS u, m8_current_user AS cu
  WHERE u.user_id != _current_user_id AND NOT u.deleted AND u.blocked_times < 7 AND NOT (ARRAY[u.user_id] <@ cu.byed_users) AND NOT (ARRAY[cu.user_id] <@ u.byed_users) AND cu.position IS NOT NULL AND u.position IS NOT NULL AND authorized_at IS NOT NULL AND age(current_timestamp, authorized_at) <= '28 days' AND (lower(u.username) LIKE lower(_criteria) || '%' OR lower(u.first_name) LIKE lower(_criteria) || '%' OR lower(u.email) LIKE lower(_criteria))
  ORDER BY _distance ASC, _first_name ASC
  OFFSET _page_offset
  LIMIT _page_size;;
END;;
$$
LANGUAGE 'plpgsql' STABLE;

DROP FUNCTION IF EXISTS social.get_other_user_profile(INTEGER, INTEGER);

CREATE OR REPLACE FUNCTION social.get_other_user_profile(IN _current_user_id INTEGER, IN _target_user_id INTEGER)
  RETURNS TABLE (_other_user_id INTEGER, _pictures TEXT ARRAY, _username TEXT, _first_name TEXT, _gender TEXT, _distance INTEGER, _interests TEXT ARRAY, _description TEXT, _authorized_at TIMESTAMPTZ, _is_friend BOOLEAN, _fb_user_id TEXT, _fb_username TEXT) AS $$
BEGIN
  RETURN QUERY
  WITH m8_current_user AS (
    SELECT
      "position",
      friends,
      interests
    FROM social.m8_users AS cu
    WHERE cu.user_id = _current_user_id
  )
  SELECT u.user_id AS _other_user_id, u.pictures[1:6] AS _pictures, u.username::TEXT AS _username, u.first_name::TEXT AS _first_name, u.gender::TEXT AS _gender, ceil(ST_Distance(u.position, cu.position)/1000.0)::INTEGER AS _distance, COALESCE(u.interests, '{}') AS _interests, u.description AS _description, u.authorized_at AS _authorized_at, (ARRAY[u.user_id] <@ cu.friends) AS _is_friend, u.fb_user_id AS _fb_user_id, u.fb_username AS _fb_username
  FROM social.m8_users AS u, m8_current_user AS cu
  WHERE u.user_id = _target_user_id;;
END;;
$$
LANGUAGE 'plpgsql' STABLE;

DROP FUNCTION IF EXISTS social.get_chat_messages(INTEGER, INTEGER, INTEGER);

CREATE OR REPLACE FUNCTION social.get_chat_messages(IN _current_user_id INTEGER, IN _chat_id INTEGER, IN _limit INTEGER)
  RETURNS TABLE (_sender_id INTEGER, _first_picture TEXT, _fb_user_id TEXT, _fb_username TEXT, _first_name TEXT, _gender TEXT, _message TEXT, _sent_at TIMESTAMPTZ) AS $$
BEGIN
  UPDATE social.chats
  SET last_read_by = (last_read_by || hstore(_current_user_id::TEXT, current_timestamp::TEXT))
  WHERE chat_id = _chat_id;;

  UPDATE social.messages
  SET read_by = read_by | _current_user_id
  WHERE chat_id = _chat_id AND NOT ARRAY[_current_user_id] <@ read_by;;

  RETURN QUERY
    SELECT m.sender_id, u.pictures[1], u.fb_user_id, u.fb_username, u.first_name::TEXT, u.gender::TEXT, m.message, m.sent_at
    FROM social.chats AS ch INNER JOIN social.messages AS m ON (m.chat_id = ch.chat_id) INNER JOIN social.m8_users u ON (((ch.participants @@  _current_user_id::TEXT::query_int) OR m.sender_id = _current_user_id) AND m.sender_id = u.user_id)
    WHERE ch.chat_id = _chat_id
    ORDER BY m.sent_at DESC
    LIMIT _limit;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

DROP FUNCTION IF EXISTS social.get_friends(INTEGER);

CREATE OR REPLACE FUNCTION social.get_friends(IN _current_user_id INTEGER)
  RETURNS TABLE (_friend_id INTEGER, _first_picture TEXT, _username TEXT, _first_name TEXT, _fb_user_id TEXT, _fb_username TEXT) AS $$
BEGIN
  RETURN QUERY
  WITH m8_current_user AS (
      SELECT
        friends
      FROM social.m8_users AS cu
      WHERE cu.user_id = _current_user_id
  )
  SELECT u.user_id AS _friend_id, u.pictures[1] AS _first_picture, u.username::TEXT AS _username, u.first_name::TEXT AS _first_name, u.fb_user_id AS _fb_user_id, u.fb_username AS _fb_username
  FROM social.m8_users AS u INNER JOIN m8_current_user AS cu ON (ARRAY[u.user_id] <@ cu.friends AND u.user_id <> _current_user_id AND NOT u.deleted AND u.blocked_times < 7)
  ORDER BY u.username;;
END;;
$$
LANGUAGE 'plpgsql' STABLE;

DROP FUNCTION IF EXISTS social.get_profile(INTEGER);

CREATE OR REPLACE FUNCTION social.get_profile(IN _user_id INTEGER)
  RETURNS TABLE (_email TEXT, _username TEXT, _first_name TEXT, _gender TEXT, _prefer_gender TEXT, _description TEXT, _pictures TEXT ARRAY, _interests TEXT ARRAY, _fb_user_id TEXT, _fb_username TEXT) AS $$
BEGIN
  RETURN QUERY
    SELECT email, username, first_name, gender::TEXT, prefer_gender::TEXT, description, pictures, COALESCE(interests, '{}'), fb_user_id, fb_username
    FROM social.m8_users
    WHERE user_id = _user_id;;
END;;
$$
LANGUAGE 'plpgsql' STABLE;
