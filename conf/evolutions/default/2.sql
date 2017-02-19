# --- !Ups

--- Create postgis extension before running the script
--- CREATE EXTENSION IF NOT EXISTS postgis;

ALTER TABLE IF EXISTS social.m8_users
  ADD COLUMN position GEOGRAPHY(POINT, 4326),
  ADD COLUMN friends INTEGER ARRAY NOT NULL DEFAULT '{}',
  ADD COLUMN android_apn_tokens TEXT ARRAY NOT NULL DEFAULT '{}',
  ADD COLUMN apple_apn_tokens TEXT ARRAY NOT NULL DEFAULT '{}',
  ADD COLUMN interests TEXT ARRAY,
  ADD COLUMN pwd_reset_token TEXT,
  ADD COLUMN pwd_reset_expiring_at TIMESTAMPTZ,
  ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE; -- This one is almost not used for now in all queries

-- Whenever there are new messages posted to the chat, reset the last_read_by
CREATE TABLE IF NOT EXISTS social.chats (
  chat_id serial PRIMARY KEY,
  participants INTEGER ARRAY NOT NULL DEFAULT '{}',
  last_message_id INTEGER NOT NULL DEFAULT 0,
  last_read_by hstore
);

CREATE INDEX m8_chats_participants_idx on social.chats USING GIN (participants);
CREATE INDEX m8_chats_last_readers_idx on social.chats USING GIN (last_read_by);

CREATE TABLE IF NOT EXISTS social.messages (
  message_id serial PRIMARY KEY,
  sender_id INTEGER NOT NULL REFERENCES social.m8_users (user_id),
  chat_id INTEGER NOT NULL REFERENCES social.chats (chat_id) ON DELETE CASCADE,
  message TEXT NOT NULL,
  sent_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp
);

CREATE INDEX m8_users_position_idx ON social.m8_users USING GIST (position);
CREATE INDEX m8_users_deleted_idx ON social.m8_users USING BTREE (deleted);
CREATE INDEX m8_users_android_apn_tokens_idx ON social.m8_users USING GIN (android_apn_tokens);
CREATE INDEX m8_users_apple_apn_tokens_idx ON social.m8_users USING GIN (apple_apn_tokens);

CREATE INDEX m8_messages_sender_idx ON social.messages USING BTREE (sender_id);
CREATE INDEX m8_messages_chat_idx ON social.messages USING BTREE (chat_id);

# --- !Downs

ALTER TABLE IF EXISTS social.m8_users
  DROP COLUMN IF EXISTS position,
  DROP COLUMN IF EXISTS friends,
  DROP COLUMN IF EXISTS android_apn_tokens,
  DROP COLUMN IF EXISTS apple_apn_tokens,
  DROP COLUMN IF EXISTS interests,
  DROP COLUMN IF EXISTS pwd_reset_token,
  DROP COLUMN IF EXISTS pwd_reset_expiring_at,
  DROP COLUMN IF EXISTS deleted;

DROP TABLE IF EXISTS social.chats CASCADE;
DROP TABLE IF EXISTS social.messages CASCADE;
