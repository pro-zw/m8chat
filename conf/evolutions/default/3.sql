# --- !Ups

ALTER TABLE IF EXISTS social.m8_users
  ALTER COLUMN pictures SET NOT NULL,
  ALTER COLUMN pictures SET DEFAULT array_fill(''::TEXT, ARRAY[6]),
  ADD COLUMN blocked_times INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN byed_users INTEGER ARRAY NOT NULL DEFAULT '{}';

CREATE INDEX m8_users_authorized_at_idx ON social.m8_users USING BTREE (authorized_at);
CREATE INDEX m8_users_friends_idx on social.m8_users USING GIN (friends);
CREATE INDEX m8_users_byed_users_idx on social.m8_users USING GIN (byed_users);

# --- !Downs

DROP INDEX IF EXISTS social.m8_users_authorized_at_idx;
DROP INDEX IF EXISTS social.m8_users_friends_idx;
DROP INDEX IF EXISTS social.m8_users_byed_users_idx;

ALTER TABLE IF EXISTS social.m8_users
  DROP COLUMN IF EXISTS blocked_times,
  DROP COLUMN IF EXISTS byed_users;
