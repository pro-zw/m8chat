# --- !Ups

CREATE OR REPLACE FUNCTION social.bye_user(IN _current_user_id INTEGER, IN _target_user_id INTEGER)
  RETURNS VOID AS $$
BEGIN
  UPDATE social.m8_users
  SET byed_users = uniq(sort(byed_users | _target_user_id)), friends = friends - _target_user_id
  WHERE _current_user_id <> _target_user_id AND user_id = _current_user_id;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

# --- !Downs

DROP FUNCTION IF EXISTS social.bye_user(INTEGER, INTEGER);