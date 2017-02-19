# --- !Ups

CREATE TYPE payment_method AS ENUM ('manual', 'subscription');

ALTER TABLE IF EXISTS advert.advertisers
    ADD COLUMN stripe_customer_id TEXT,
    ADd COLUMN payment_method payment_method NOT NULL DEFAULT 'manual'::payment_method;

CREATE INDEX advertisers_stripe_customer_idx ON advert.advertisers USING BTREE (stripe_customer_id);

CREATE OR REPLACE FUNCTION social.login(IN _identity TEXT, IN _password TEXT, OUT _user_id INTEGER, OUT _access_token TEXT, OUT _gender TEXT)
AS $$
BEGIN
  UPDATE social.m8_users
  SET authorized_at = current_timestamp
  WHERE (lower(email) = lower(_identity) OR lower(username) = lower(_identity)) AND "password" = _password AND NOT deleted;;

  UPDATE social.m8_users
  SET access_token = uuid_generate_v4()
  WHERE (lower(email) = lower(_identity) OR lower(username) = lower(_identity)) AND "password" = _password AND (age(current_timestamp, authorized_at) >= '7 days' OR access_token IS NULL) AND NOT deleted;;

  SELECT user_id, access_token, gender::TEXT INTO _user_id, _access_token, _gender
  FROM social.m8_users
  WHERE (lower(email) = lower(_identity) OR lower(username) = lower(_identity)) AND "password" = _password AND NOT deleted;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;

# --- !Downs
DROP TYPE IF EXISTS payment_method;
DROP FUNCTION IF EXISTS social.login(TEXT, TEXT);

DROP INDEX IF EXISTS advert.advertisers_stripe_customer_idx;

ALTER TABLE IF EXISTS advert.advertisers
    DROP COLUMN IF EXISTS stripe_customer_id,
    DROP COLUMN IF EXISTS payment_method;
