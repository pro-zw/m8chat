# --- !Ups

DROP FUNCTION IF EXISTS social.new_chat(INTEGER, INTEGER ARRAY, TEXT);

CREATE OR REPLACE FUNCTION social.new_chat(IN _current_user_id INTEGER, IN _recipients INTEGER ARRAY, IN _message TEXT, OUT _chat_id INTEGER, OUT _new_message_id INTEGER)
AS $$
DECLARE
  _filtered_recipients INTEGER ARRAY;;
BEGIN
  _filtered_recipients := uniq(sort(_recipients - _current_user_id));;

  UPDATE social.m8_users
  SET friends = friends | (_filtered_recipients - byed_users)
  WHERE user_id = _current_user_id;;

  UPDATE social.m8_users
  SET friends = friends | (((_filtered_recipients - user_id) | _current_user_id) - byed_users)
  WHERE _filtered_recipients @@ user_id::TEXT::query_int;;

  INSERT INTO social.chats (participants)
  VALUES (_filtered_recipients | _current_user_id)
  RETURNING chat_id INTO _chat_id;;

  SELECT _message_id INTO _new_message_id
  FROM social.new_message(_current_user_id, _chat_id, _message);;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

DROP FUNCTION IF EXISTS social.new_message(INTEGER, INTEGER, TEXT);

CREATE OR REPLACE FUNCTION social.new_message(IN _current_user_id INTEGER, IN _chat_id INTEGER, IN _message TEXT, OUT _message_id INTEGER) AS $$
BEGIN
  INSERT INTO social.messages (sender_id, chat_id, "message", read_by)
  VALUES (_current_user_id, _chat_id, _message, ARRAY[_current_user_id])
  RETURNING message_id INTO _message_id;;

  UPDATE social.chats
  SET last_read_by = hstore(_current_user_id::TEXT, current_timestamp::TEXT), last_message_id = _message_id
  WHERE chat_id = _chat_id;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

DROP FUNCTION IF EXISTS social.get_message_push_info(INTEGER);

CREATE OR REPLACE FUNCTION social.get_message_push_info(IN _message_id INTEGER)
  RETURNS TABLE (_chat_id INTEGER, _sender_first_name TEXT, _message TEXT, _android_apn_tokens TEXT ARRAY, _apple_apn_tokens TEXT ARRAY) AS $$
BEGIN
  RETURN QUERY
  WITH message_sent AS (
    SELECT m.chat_id, m.sender_id, u.first_name, m.message, m.read_by
    FROM social.messages m INNER JOIN social.m8_users u ON (m.sender_id = u.user_id)
    WHERE m.message_id = _message_id
  )
  SELECT ms.chat_id, ms.first_name, ms.message, array_agg(array_to_string(u.android_apn_tokens, ',')), array_agg(array_to_string(u.apple_apn_tokens, ','))
  FROM message_sent ms INNER JOIN social.chats "c" ON (ms.chat_id = "c".chat_id) INNER JOIN social.m8_users u ON (NOT u.deleted AND ARRAY[u.user_id] <@ ("c".participants - ms.sender_id) AND (NOT ARRAY[u.user_id] <@ ms.read_by) AND (("c".previous_read_by ISNULL) OR ("c".previous_read_by ? u.user_id::TEXT AND age(current_timestamp, ("c".previous_read_by -> u.user_id::TEXT)::TIMESTAMPTZ) > '10 seconds')))
  GROUP BY ms.chat_id, ms.first_name, ms.message;;
END;;
$$
LANGUAGE 'plpgsql' STABLE;

CREATE OR REPLACE FUNCTION social.add_apple_apn_token(IN _user_id INTEGER, IN _token TEXT, OUT _update_count INTEGER)
AS $$
BEGIN
  UPDATE social.m8_users
  SET apple_apn_tokens = array_remove(apple_apn_tokens, _token)
  WHERE user_id <> _user_id AND ARRAY[_token] <@ apple_apn_tokens;;

  UPDATE social.m8_users
  SET apple_apn_tokens = (apple_apn_tokens || _token)
  WHERE user_id = _user_id AND NOT ARRAY[_token] <@ apple_apn_tokens AND NOT deleted;;

  GET DIAGNOSTICS _update_count = ROW_COUNT;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;;

CREATE OR REPLACE FUNCTION social.add_android_apn_token(IN _user_id INTEGER, IN _token TEXT, OUT _update_count INTEGER)
AS $$
BEGIN
  UPDATE social.m8_users
  SET android_apn_tokens = array_remove(android_apn_tokens, _token)
  WHERE user_id <> _user_id AND ARRAY[_token] <@ android_apn_tokens;;

  UPDATE social.m8_users
  SET android_apn_tokens = (android_apn_tokens || _token)
  WHERE user_id = _user_id AND NOT ARRAY[_token] <@ android_apn_tokens AND NOT deleted;;

  GET DIAGNOSTICS _update_count = ROW_COUNT;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;;