# --- !Ups

CREATE INDEX m8_users_first_name_idx ON social.m8_users USING BTREE (lower(first_name));

CREATE TYPE advertiser_status AS ENUM ('registered', 'confirmed', 'active', 'suspended');
CREATE TYPE bill_status AS ENUM ('issued', 'canceled', 'expired', 'paid');
CREATE TYPE plan_name AS ENUM ('bronze', 'silver', 'gold');
CREATE TYPE listing_days AS ENUM ('monthly', 'quarterly', 'yearly');

ALTER TABLE IF EXISTS social.m8_users
  ALTER COLUMN created_at SET DEFAULT current_timestamp,
  ALTER COLUMN modified_at SET DEFAULT current_timestamp;

CREATE TABLE IF NOT EXISTS advert.advertisers (
  -- basic information --
  advertiser_id serial PRIMARY KEY,
  name TEXT NOT NULL CHECK (LENGTH(name) >= 1 AND LENGTH(name) <= 80),
  company_name TEXT NOT NULL CHECK (LENGTH(company_name) >= 1 AND LENGTH(company_name) <= 100),
  email TEXT NOT NULL CHECK (LENGTH(email) >= 3 AND LENGTH(email) <= 254),
  password TEXT NOT NULL,
  active_util TIMESTAMPTZ,
  status advertiser_status NOT NULL DEFAULT 'registered'::advertiser_status,
  created_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
  modified_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
  authorized_at TIMESTAMPTZ,
  access_token TEXT,
  suspended_reason TEXT CHECK (LENGTH(suspended_reason) <= 120),
  -- plan information --
  plan_name plan_name NOT NULL,
  price NUMERIC(12, 2) CHECK (price > 0),
  listing_days listing_days NOT NULL DEFAULT 'monthly'::listing_days,
  photo_limit INTEGER NOT NULL DEFAULT 0,
  priority INTEGER NOT NULL,
  -- payment --
  balance NUMERIC NOT NULL DEFAULT 0.0,
  balance_charged_at TIMESTAMPTZ,
  -- misc
  email_confirm_token TEXT,
  pwd_reset_token TEXT,
  pwd_reset_expiring_at TIMESTAMPTZ
);

CREATE INDEX advertisers_name_idx ON advert.advertisers USING BTREE (name);
CREATE INDEX advertisers_status_idx ON advert.advertisers USING BTREE (status);
CREATE INDEX advertisers_balance_idx ON advert.advertisers USING BTREE (balance);
CREATE UNIQUE INDEX advertisers_email_idx ON advert.advertisers USING BTREE (lower(email));
CREATE UNIQUE INDEX advertisers_email_confirm_token_idx ON advert.advertisers USING BTREE (lower(email_confirm_token));

CREATE TABLE IF NOT EXISTS advert.adverts (
  advert_id serial PRIMARY KEY,
  advertiser_id INTEGER NOT NULL REFERENCES advert.advertisers(advertiser_id),
  business_name TEXT NOT NULL CHECK (LENGTH(business_name) >= 1 AND LENGTH(business_name) <= 100),
  contact_number TEXT CHECK(LENGTH(contact_number) <= 60),
  website TEXT,
  address TEXT NOT NULL,
  position GEOGRAPHY(POINT, 4326) NOT NULL,
  description TEXT NOT NULL CHECK (LENGTH(description) <= 500),
  displayed_times INTEGER NOT NULL DEFAULT 0,
  photos TEXT ARRAY NOT NULL DEFAULT array_fill(''::TEXT, ARRAY[20])
);

-- For now we only allow one advert per advertiser
CREATE UNIQUE INDEX adverts_advertiser_idx ON advert.adverts USING BTREE (advertiser_id);
CREATE INDEX adverts_position_idx ON advert.adverts USING GIST (position);

CREATE TABLE IF NOT EXISTS advert.bills (
  bill_id serial PRIMARY KEY,
  advertiser_id INTEGER NOT NULL REFERENCES advert.advertisers(advertiser_id),
  issued_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
  paid_at TIMESTAMPTZ,
  expiring_at TIMESTAMPTZ,
  canceled_at TIMESTAMPTZ,
  amount NUMERIC NOT NULL CHECK (amount > 0),
  description TEXT,
  payment_id TEXT,
  status bill_status NOT NULL DEFAULT 'issued'::bill_status,
  email_scheduled BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX bills_advertiser_status_idx ON advert.bills USING BTREE (advertiser_id, status);
CREATE INDEX bills_status_idx ON advert.bills USING BTREE (status);
CREATE INDEX bills_issued_at_idx ON advert.bills USING BTREE (issued_at);
CREATE INDEX bills_expiring_at_idx ON advert.bills USING BTREE (expiring_at);
CREATE INDEX bills_email_scheduled_idx ON advert.bills USING BTREE (email_scheduled);

# --- !Downs

DROP INDEX IF EXISTS social.m8_users_first_name_idx;

DROP TABLE IF EXISTS advert.bills;
DROP TABLE IF EXISTS advert.adverts;
DROP TABLE IF EXISTS advert.advertisers;

DROP TYPE IF EXISTS advertiser_status;
DROP TYPE IF EXISTS bill_status;
DROP TYPE IF EXISTS plan_name;
DROP TYPE IF EXISTS listing_days;
