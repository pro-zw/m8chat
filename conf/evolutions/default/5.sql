# --- !Ups

-- arrayIntersect
CREATE OR REPLACE FUNCTION social.array_intersect(ANYARRAY, ANYARRAY)
  RETURNS ANYARRAY AS $$
  SELECT ARRAY(
    SELECT UNNEST($1)
    INTERSECT
    SELECT UNNEST($2)
  );;
$$
LANGUAGE 'sql' IMMUTABLE;

-- listUsersNearby
CREATE OR REPLACE FUNCTION social.list_users_nearby(IN _current_user_id INTEGER, IN _page INTEGER, IN _page_size INTEGER)
  RETURNS TABLE (_user_id INTEGER, _first_picture TEXT, _first_name TEXT, _distance INTEGER, _gender TEXT, _is_friend BOOLEAN) AS $$
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
  SELECT u.user_id AS _user_id, u.pictures[1] AS _first_picture, u.first_name::TEXT AS _first_name, ceil(ST_Distance(u.position, cu.position)/1000.0)::INTEGER AS _distance, u.gender::Text AS _gender, (ARRAY[u.user_id] <@ cu.friends) AS _is_friend
  FROM social.m8_users AS u, m8_current_user AS cu
  WHERE u.user_id != _current_user_id AND NOT u.deleted AND u.blocked_times < 7 AND NOT (ARRAY[u.user_id] <@ cu.byed_users) AND NOT (ARRAY[cu.user_id] <@ u.byed_users) AND cu.position IS NOT NULL AND u.position IS NOT NULL AND authorized_at IS NOT NULL AND age(current_timestamp, authorized_at) <= '28 days' AND ceil(ST_Distance(u.position, cu.position)/1000.0)::INTEGER <= 30 AND ((cu.prefer_gender = 'Both'::gender OR cu.prefer_gender = u.gender) AND (u.prefer_gender = 'Both'::gender OR u.prefer_gender = cu.gender))
  ORDER BY _distance ASC, _first_name ASC
  OFFSET _page_offset
  LIMIT _page_size;;
END;;
$$
LANGUAGE 'plpgsql' STABLE;

-- searchUsers
CREATE OR REPLACE FUNCTION social.search_users(IN _current_user_id INTEGER, IN _criteria TEXT, IN _page INTEGER, IN _page_size INTEGER)
  RETURNS TABLE (_user_id INTEGER, _first_picture TEXT, _first_name TEXT, _distance INTEGER, _gender TEXT, _is_friend BOOLEAN) AS $$
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
  SELECT u.user_id AS _user_id, u.pictures[1] AS _first_picture, u.first_name::TEXT AS _first_name, ceil(ST_Distance(u.position, cu.position)/1000.0)::INTEGER AS _distance, u.gender::Text AS _gender, (ARRAY[u.user_id] <@ cu.friends) AS _is_friend
  FROM social.m8_users AS u, m8_current_user AS cu
  WHERE u.user_id != _current_user_id AND NOT u.deleted AND u.blocked_times < 7 AND NOT (ARRAY[u.user_id] <@ cu.byed_users) AND NOT (ARRAY[cu.user_id] <@ u.byed_users) AND cu.position IS NOT NULL AND u.position IS NOT NULL AND authorized_at IS NOT NULL AND age(current_timestamp, authorized_at) <= '28 days' AND (lower(u.username) LIKE lower(_criteria) || '%' OR lower(u.first_name) LIKE lower(_criteria) || '%' OR lower(u.email) LIKE lower(_criteria))
  ORDER BY _distance ASC, _first_name ASC
  OFFSET _page_offset
  LIMIT _page_size;;
END;;
$$
LANGUAGE 'plpgsql' STABLE;

-- getOtherUserProfile
CREATE OR REPLACE FUNCTION social.get_other_user_profile(IN _current_user_id INTEGER, IN _target_user_id INTEGER)
  RETURNS TABLE (_other_user_id INTEGER, _pictures TEXT ARRAY, _username TEXT, _first_name TEXT, _gender TEXT, _distance INTEGER, _interests TEXT ARRAY, _description TEXT, _authorized_at TIMESTAMPTZ, _is_friend BOOLEAN) AS $$
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
  SELECT u.user_id AS _other_user_id, u.pictures[1:6] AS _pictures, u.username::TEXT AS _username, u.first_name::TEXT AS _first_name, u.gender::TEXT AS _gender, ceil(ST_Distance(u.position, cu.position)/1000.0)::INTEGER AS _distance, COALESCE(u.interests, '{}') AS _interests, u.description AS _description, u.authorized_at AS _authorized_at, (ARRAY[u.user_id] <@ cu.friends) AS _is_friend
  FROM social.m8_users AS u, m8_current_user AS cu
  WHERE u.user_id = _target_user_id;;
END;;
$$
LANGUAGE 'plpgsql' STABLE;

-- getFriends
CREATE OR REPLACE FUNCTION social.get_friends(IN _current_user_id INTEGER)
  RETURNS TABLE (_friend_id INTEGER, _first_picture TEXT, _username TEXT, _first_name TEXT) AS $$
BEGIN
  RETURN QUERY
  WITH m8_current_user AS (
      SELECT
        friends
      FROM social.m8_users AS cu
      WHERE cu.user_id = _current_user_id
  )
  SELECT u.user_id AS _friend_id, u.pictures[1] AS _first_picture, u.username::TEXT AS _username, u.first_name::TEXT AS _first_name
  FROM social.m8_users AS u INNER JOIN m8_current_user AS cu ON (ARRAY[u.user_id] <@ cu.friends AND u.user_id <> _current_user_id AND NOT u.deleted AND u.blocked_times < 7)
  ORDER BY u.username;;
END;;
$$
LANGUAGE 'plpgsql' STABLE;

-- blockUser (unilateral)
CREATE OR REPLACE FUNCTION social.block_user(IN _current_user_id INTEGER, IN _target_user_id INTEGER)
  RETURNS VOID AS $$
BEGIN
  UPDATE social.m8_users
  SET blocked_times = blocked_times + 1
  WHERE _current_user_id <> _target_user_id AND user_id = _target_user_id;;

  UPDATE social.m8_users
  SET byed_users = uniq(sort(byed_users | _target_user_id)), friends = friends - _target_user_id
  WHERE _current_user_id <> _target_user_id AND user_id = _current_user_id;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

-- Trigger for updating modified_at field in various tables
CREATE OR REPLACE FUNCTION modified_time_stamper() RETURNS trigger AS
  $$
BEGIN
  NEW.modified_at := CURRENT_TIMESTAMP;;
  RETURN NEW;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

CREATE TRIGGER trig_modified_time_stamper
BEFORE INSERT OR UPDATE
  ON social.m8_users
  FOR EACH ROW
  EXECUTE PROCEDURE modified_time_stamper();

CREATE TRIGGER trig_modified_time_stamper
BEFORE INSERT OR UPDATE
  ON advert.advertisers
  FOR EACH ROW
  EXECUTE PROCEDURE modified_time_stamper();

# --- !Downs

DROP FUNCTION IF EXISTS social.array_intersect(ANYARRAY, ANYARRAY);
DROP FUNCTION IF EXISTS social.list_users_nearby(INTEGER, INTEGER, INTEGER);
DROP FUNCTION IF EXISTS social.get_other_user_profile(INTEGER, INTEGER);
DROP FUNCTION IF EXISTS social.search_users(INTEGER, TEXT, INTEGER, INTEGER);
DROP FUNCTION IF EXISTS social.block_user(INTEGER, INTEGER);
DROP FUNCTION IF EXISTS social.get_friends(INTEGER);

DROP TRIGGER IF EXISTS trig_modified_time_stamper ON social.m8_users;
DROP TRIGGER IF EXISTS trig_modified_time_stamper ON advert.advertisers;
DROP FUNCTION IF EXISTS modified_time_stamper();