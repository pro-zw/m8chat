# --- !Ups

DROP INDEX IF EXISTS social.m8_users_username_idx;
CREATE UNIQUE INDEX m8_users_username_idx ON social.m8_users USING BTREE (lower(username));

-- pictures_combination
CREATE OR REPLACE FUNCTION social.pictures_combination(TEXT ARRAY, TEXT ARRAY)
  RETURNS TEXT ARRAY AS $$
DECLARE
  _pictures_concat TEXT ARRAY;;
  _pictures TEXT ARRAY;;
BEGIN
  _pictures_concat = array_cat($1[1:6], $2[1:6]);;
  SELECT ARRAY[
    CASE WHEN _pictures_concat[1] ISNULL THEN _pictures_concat[7] ELSE _pictures_concat[1] END,
    CASE WHEN _pictures_concat[2] ISNULL THEN _pictures_concat[8] ELSE _pictures_concat[2] END,
    CASE WHEN _pictures_concat[3] ISNULL THEN _pictures_concat[9] ELSE _pictures_concat[3] END,
    CASE WHEN _pictures_concat[4] ISNULL THEN _pictures_concat[10] ELSE _pictures_concat[4] END,
    CASE WHEN _pictures_concat[5] ISNULL THEN _pictures_concat[11] ELSE _pictures_concat[5] END,
    CASE WHEN _pictures_concat[6] ISNULL THEN _pictures_concat[12] ELSE _pictures_concat[6] END
  ] INTO _pictures;;

  _pictures = array_cat(array_remove(_pictures, ''), array_fill(''::TEXT, ARRAY[6]));;

  RETURN _pictures;;
END;;
$$
LANGUAGE 'plpgsql' IMMUTABLE;

-- get_profile
CREATE OR REPLACE FUNCTION social.get_profile(IN _user_id INTEGER)
  RETURNS TABLE (_email TEXT, _username TEXT, _first_name TEXT, _gender TEXT, _prefer_gender TEXT, _description TEXT, _pictures TEXT ARRAY, _interests TEXT ARRAY) AS $$
BEGIN
  RETURN QUERY
    SELECT email, username, first_name, gender::TEXT, prefer_gender::TEXT, description, pictures, COALESCE(interests, '{}')
    FROM social.m8_users
    WHERE user_id = _user_id;;
END;;
$$
LANGUAGE 'plpgsql' STABLE;

-- update_profile
CREATE OR REPLACE FUNCTION social.update_profile(IN _user_id INTEGER, IN _email TEXT, IN _username TEXT, IN _first_name TEXT, _gender TEXT, _prefer_gender TEXT, _description TEXT, _pictures TEXT ARRAY, _interests TEXT ARRAY, OUT _update_count INTEGER)
  AS $$
BEGIN
  UPDATE social.m8_users
  SET email = lower(_email), username = _username, first_name = _first_name, gender = _gender::gender, prefer_gender = _prefer_gender::gender, description = _description, pictures = social.pictures_combination(_pictures, pictures), interests = _interests
  WHERE user_id = _user_id;;

  GET DIAGNOSTICS _update_count = ROW_COUNT;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;;

-- change_password
CREATE OR REPLACE FUNCTION social.change_password(IN _user_id INTEGER, IN _old_password TEXT, IN _new_password TEXT, OUT _update_count INTEGER)
  AS $$
BEGIN
  UPDATE social.m8_users
  SET "password" = _new_password
  WHERE user_id = _user_id AND "password" = _old_password;;

  GET DIAGNOSTICS _update_count = ROW_COUNT;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;;

-- delete_account
CREATE OR REPLACE FUNCTION social.delete_account(IN _user_id INTEGER, OUT _update_count INTEGER)
  AS $$
DECLARE
  _deleted_suffix TEXT;;
BEGIN
  _deleted_suffix := '_deleted_' || uuid_generate_v4();;

  UPDATE social.m8_users
  SET username = username || _deleted_suffix, email = email || _deleted_suffix, deleted = TRUE, fb_user_id = CASE WHEN fb_user_id NOTNULL THEN (fb_user_id || _deleted_suffix) ELSE NULL END, fb_username = CASE WHEN fb_username NOTNULL THEN (fb_username || _deleted_suffix) ELSE NULL END, access_token = DEFAULT
  WHERE user_id = _user_id;;

  GET DIAGNOSTICS _update_count = ROW_COUNT;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;;

-- add_apple_apn_token
CREATE OR REPLACE FUNCTION social.add_apple_apn_token(IN _user_id INTEGER, IN _token TEXT, OUT _update_count INTEGER)
  AS $$
BEGIN
  UPDATE social.m8_users
  SET apple_apn_tokens = (apple_apn_tokens || _token)
  WHERE user_id = _user_id AND NOT ARRAY[_token] <@ apple_apn_tokens AND NOT deleted;;

  GET DIAGNOSTICS _update_count = ROW_COUNT;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;;

-- add_android_apn_token
CREATE OR REPLACE FUNCTION social.add_android_apn_token(IN _user_id INTEGER, IN _token TEXT, OUT _update_count INTEGER)
  AS $$
BEGIN
  UPDATE social.m8_users
  SET android_apn_tokens = (android_apn_tokens || _token)
  WHERE user_id = _user_id AND NOT ARRAY[_token] <@ android_apn_tokens AND NOT deleted;;

  GET DIAGNOSTICS _update_count = ROW_COUNT;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;;

# --- !Downs

DROP FUNCTION IF EXISTS social.get_profile(INTEGER);
DROP FUNCTION IF EXISTS social.pictures_combination(TEXT ARRAY, TEXT ARRAY);
DROP FUNCTION IF EXISTS social.update_profile(INTEGER, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT, TEXT ARRAY, TEXT ARRAY);
DROP FUNCTION IF EXISTS social.change_password(INTEGER, TEXT, TEXT);
DROP FUNCTION IF EXISTS social.delete_account(INTEGER);

DROP FUNCTION IF EXISTS social.add_apple_apn_token(INTEGER, TEXT);
DROP FUNCTION IF EXISTS social.add_android_apn_token(INTEGER, TEXT);
