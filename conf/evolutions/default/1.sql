# --- !Ups

CREATE SCHEMA IF NOT EXISTS social AUTHORIZATION weizheng;
CREATE SCHEMA IF NOT EXISTS advert AUTHORIZATION weizheng;

CREATE TYPE gender AS ENUM ('Male', 'Female', 'Both');

CREATE TABLE social.m8_users (
  user_id serial PRIMARY KEY,
  email TEXT NOT NULL CHECK (LENGTH(email) >= 3 AND LENGTH(email) <= 254),
  username TEXT NOT NULL CHECK (LENGTH(username) >= 3 AND LENGTH(username) <= 200),
  fb_user_id TEXT,
  fb_username TEXT,
  first_name TEXT NOT NULL CHECK (LENGTH(first_name) <= 200),
  password TEXT NOT NULL,
  gender gender NOT NULL,
  prefer_gender gender NOT NULL,
  description TEXT NOT NULL,
  pictures TEXT ARRAY,
  created_at TIMESTAMPTZ NOT NULL,
  modified_at TIMESTAMPTZ NOT NULL,
  authorized_at TIMESTAMPTZ NOT NULL,
  access_token TEXT
);

CREATE UNIQUE INDEX m8_users_email_idx ON social.m8_users USING BTREE (lower(email));
CREATE UNIQUE INDEX m8_users_username_idx ON social.m8_users USING BTREE (username);
CREATE UNIQUE INDEX m8_users_fb_user_id_idx ON social.m8_users USING BTREE (lower(fb_user_id));
CREATE UNIQUE INDEX m8_users_fb_username_idx ON social.m8_users USING BTREE (lower(fb_username));
CREATE UNIQUE INDEX m8_users_access_token_idx ON social.m8_users USING BTREE (lower(access_token));

# --- !Downs
DROP SCHEMA IF EXISTS social CASCADE;
DROP SCHEMA IF EXISTS advert CASCADE;

DROP TYPE IF EXISTS gender;