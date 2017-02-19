# --- !Ups

ALTER TABLE IF EXISTS social.chats
  ADD COLUMN previous_read_by hstore;

CREATE INDEX m8_chats_previous_readers_idx on social.chats USING GIN (previous_read_by);

ALTER TABLE IF EXISTS social.messages
  ADD COLUMN read_by INTEGER ARRAY NOT NULL DEFAULT '{}';

CREATE INDEX m8_messages_readers_idx ON social.messages USING GIST (read_by);

DROP FUNCTION IF EXISTS social.new_message(INTEGER, INTEGER, TEXT);

CREATE OR REPLACE FUNCTION social.new_message(IN _current_user_id INTEGER, IN _chat_id INTEGER, IN _message TEXT)
  RETURNS VOID AS $$
DECLARE
  _last_message_id INTEGER;;
BEGIN
  INSERT INTO social.messages (sender_id, chat_id, "message", read_by)
  VALUES (_current_user_id, _chat_id, _message, ARRAY[_current_user_id])
  RETURNING message_id INTO _last_message_id;;

  UPDATE social.chats
  SET previous_read_by = last_read_by, last_read_by = hstore(_current_user_id::TEXT, current_timestamp::TEXT), last_message_id = _last_message_id
  WHERE chat_id = _chat_id;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

DROP FUNCTION IF EXISTS social.get_chat_messages(INTEGER, INTEGER, INTEGER);

CREATE OR REPLACE FUNCTION social.get_chat_messages(IN _current_user_id INTEGER, IN _chat_id INTEGER, IN _limit INTEGER)
  RETURNS TABLE (_sender_id INTEGER, _first_picture TEXT, _first_name TEXT, _gender TEXT, _message TEXT, _sent_at TIMESTAMPTZ) AS $$
BEGIN
  UPDATE social.chats
  SET last_read_by = (last_read_by || hstore(_current_user_id::TEXT, current_timestamp::TEXT))
  WHERE chat_id = _chat_id;;

  UPDATE social.messages
  SET read_by = read_by | _current_user_id
  WHERE chat_id = _chat_id AND NOT ARRAY[_current_user_id] <@ read_by;;

  RETURN QUERY
    SELECT m.sender_id, u.pictures[1], u.first_name::TEXT, u.gender::TEXT, m.message, m.sent_at
    FROM social.chats AS ch INNER JOIN social.messages AS m ON (m.chat_id = ch.chat_id) INNER JOIN social.m8_users u ON (((ch.participants @@  _current_user_id::TEXT::query_int) OR m.sender_id = _current_user_id) AND m.sender_id = u.user_id)
    WHERE ch.chat_id = _chat_id
    ORDER BY m.sent_at DESC
    LIMIT _limit;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

CREATE OR REPLACE FUNCTION social.get_message_push_info(IN _message_id INTEGER, OUT _chat_id INTEGER, OUT _sender_first_name TEXT, OUT _message TEXT, OUT _android_apn_tokens TEXT ARRAY, OUT _apple_apn_tokens TEXT ARRAY) AS $$
BEGIN
  WITH message_sent AS (
    SELECT m.chat_id, m.sender_id, u.first_name, m.message, m.read_by
    FROM social.messages m INNER JOIN social.m8_users u ON (m.sender_id = u.user_id)
    WHERE m.message_id = _message_id
  )
  SELECT ms.chat_id, ms.first_name, ms.message, array_agg(unnest(u.android_apn_tokens)), array_agg(unnest(u.apple_apn_tokens)) INTO _chat_id, _sender_first_name, _message, _android_apn_tokens, _apple_apn_tokens
  FROM message_sent ms INNER JOIN social.chats "c" ON (ms.chat_id = "c".chat_id) INNER JOIN social.m8_users u ON (ARRAY[u.user_id] <@ ("c".participants - ms.sender_id) AND (NOT ARRAY[u.user_id] <@ ms.read_by) AND (("c".previous_read_by ISNULL) OR ("c".previous_read_by ? u.user_id::TEXT AND age(current_timestamp, ("c".previous_read_by -> u.user_id::TEXT)::TIMESTAMPTZ) > '10 seconds')))
  GROUP BY ms.chat_id, ms.first_name, ms.message;;
END;;
$$
LANGUAGE 'plpgsql' STABLE;

DROP FUNCTION IF EXISTS social.new_message(INTEGER, INTEGER, TEXT);

CREATE OR REPLACE FUNCTION social.new_message(IN _current_user_id INTEGER, IN _chat_id INTEGER, IN _message TEXT, OUT _message_id INTEGER) AS $$
BEGIN
  INSERT INTO social.messages (sender_id, chat_id, "message")
  VALUES (_current_user_id, _chat_id, _message)
  RETURNING message_id INTO _message_id;;

  UPDATE social.chats
  SET last_read_by = hstore(_current_user_id::TEXT, current_timestamp::TEXT), last_message_id = _message_id
  WHERE chat_id = _chat_id;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

# --- !Downs

DROP FUNCTION IF EXISTS social.get_message_push_info(INTEGER);

DROP INDEX IF EXISTS social.m8_chats_previous_readers_idx;

ALTER TABLE IF EXISTS social.chats
  DROP COLUMN IF EXISTS previous_read_by;

DROP INDEX IF EXISTS social.m8_messages_readers_idx;

ALTER TABLE IF EXISTS social.messages
  DROP COLUMN IF EXISTS read_by;