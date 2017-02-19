# --- !Ups

ALTER TABLE IF EXISTS social.chats
  ADD COLUMN hidden_by INTEGER ARRAY NOT NULL DEFAULT '{}';

CREATE INDEX m8_chats_hidden_by_idx on social.chats USING GIN (hidden_by);

CREATE OR REPLACE FUNCTION social.hide_chat(IN _current_user_id INTEGER, IN _chat_id INTEGER, OUT _update_count INTEGER)
  AS $$
BEGIN
  UPDATE social.chats
  SET hidden_by = (hidden_by | _current_user_id)
  WHERE chat_id = _chat_id AND ARRAY[_current_user_id] <@ participants;;

  GET DIAGNOSTICS _update_count = ROW_COUNT;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

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
      WHERE ARRAY[_current_user_id] <@ ch.participants AND (NOT ARRAY[_current_user_id] <@ ch.hidden_by) AND (array_length(ch.participants, 1) > 2 OR (NOT (ch.participants && u.byed_users) AND u.blocked_times < 7))
      LIMIT 100
  )
  SELECT chs.chat_id, chs.sender_id, r.pictures[1], r.fb_user_id, r.fb_username, chs.first_name, chs.gender, chs.message, chs.sent_at, chs.last_message_is_read
  FROM m8_chats chs INNER JOIN social.m8_users r ON (r.user_id = chs.recipient_id)
  ORDER BY chs.sent_at DESC;;
END;;
$$
LANGUAGE 'plpgsql' STABLE;

CREATE OR REPLACE FUNCTION social.new_message(IN _current_user_id INTEGER, IN _chat_id INTEGER, IN _message TEXT, OUT _message_id INTEGER) AS $$
BEGIN
  INSERT INTO social.messages (sender_id, chat_id, "message", read_by)
  VALUES (_current_user_id, _chat_id, _message, ARRAY[_current_user_id])
  RETURNING message_id INTO _message_id;;

  UPDATE social.chats
  SET last_read_by = hstore(_current_user_id::TEXT, current_timestamp::TEXT), last_message_id = _message_id, hidden_by = hidden_by - participants
  WHERE chat_id = _chat_id;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

# --- !Downs

DROP FUNCTION IF EXISTS social.hide_chat(INTEGER, INTEGER);
DROP INDEX IF EXISTS social.m8_chats_hidden_by_idx;

ALTER TABLE IF EXISTS social.chats
  DROP COLUMN IF EXISTS hidden_by;
