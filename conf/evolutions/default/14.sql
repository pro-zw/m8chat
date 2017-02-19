# --- !Ups

CREATE OR REPLACE FUNCTION social.bye_user(IN _current_user_id INTEGER, IN _target_user_id INTEGER)
  RETURNS VOID AS $$
BEGIN
  UPDATE social.m8_users
  SET byed_users = byed_users | _target_user_id, friends = friends - _target_user_id
  WHERE _current_user_id <> _target_user_id AND user_id = _current_user_id;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

CREATE OR REPLACE FUNCTION social.block_user(IN _current_user_id INTEGER, IN _target_user_id INTEGER)
  RETURNS VOID AS $$
BEGIN
  UPDATE social.m8_users
  SET blocked_times = blocked_times + 1
  WHERE _current_user_id <> _target_user_id AND user_id = _target_user_id;;

  UPDATE social.m8_users
  SET byed_users = byed_users | _target_user_id, friends = friends - _target_user_id
  WHERE _current_user_id <> _target_user_id AND user_id = _current_user_id;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

CREATE OR REPLACE FUNCTION social.new_chat(IN _current_user_id INTEGER, IN _recipients INTEGER ARRAY, IN _message TEXT, OUT _chat_id INTEGER)
AS $$
DECLARE
  _filtered_recipients INTEGER ARRAY;;
  _last_message_id INTEGER;;
BEGIN
  _filtered_recipients := uniq(sort(_recipients - _current_user_id));;

  UPDATE social.m8_users
  SET friends = friends | (_filtered_recipients - byed_users)
  WHERE user_id = _current_user_id;;

  UPDATE social.m8_users
  SET friends = friends | (((_filtered_recipients - user_id) | _current_user_id) - byed_users)
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

# --- !Downs