# --- !Ups

CREATE OR REPLACE FUNCTION advert.generate_bills(IN _advertiser_id INTEGER)
  RETURNS VOID AS $$
DECLARE
  _initial_bill_info RECORD;;
  _periodical_bill_info RECORD;;
BEGIN
  -- cancel paying bills which is not fulfilled
  UPDATE advert.bills
  SET status = 'canceled'::bill_status
  WHERE (_advertiser_id ISNULL OR advertiser_id = _advertiser_id) AND status = 'paying'::bill_status AND age(current_timestamp, paying_at) >= '15 minutes';;

  -- expire bills which are due
  UPDATE advert.bills AS ab
  SET status = 'expired'::bill_status
  FROM advert.advertisers AS ad
  WHERE (_advertiser_id ISNULL OR ab.advertiser_id = _advertiser_id) AND ab.status = 'issued'::bill_status AND ab.expiring_at NOTNULL AND ad.advertiser_id = ab.advertiser_id AND ad.status = 'confirmed'::advertiser_status;;

  -- generate initial bills (won't generate any bills if there are issued or paying bills)
  FOR _initial_bill_info IN
    SELECT ad.advertiser_id, (ad.price - ad.balance) AS amount
    FROM advert.advertisers ad LEFT JOIN advert.bills ab ON (ad.advertiser_id = ab.advertiser_id AND (ab.status = 'issued'::bill_status OR ab.status = 'paying'::bill_status))
    WHERE (_advertiser_id ISNULL OR ad.advertiser_id = _advertiser_id) AND ad.status = 'confirmed'::advertiser_status AND ab.bill_id ISNULL
  LOOP
    INSERT INTO advert.bills (advertiser_id, amount)
    VALUES (_initial_bill_info.advertiser_id, _initial_bill_info.amount);;

    RAISE NOTICE 'A initial bill is generated with advertiser_id: %, amount: %', _initial_bill_info.advertiser_id, _initial_bill_info.amount;;
  END LOOP;;

  -- generate periodical bills (won't generate any bills if there are issued or paying bills)
  FOR _periodical_bill_info IN
    SELECT ad.advertiser_id, active_util, (ad.price - ad.balance) AS amount
    FROM advert.advertisers ad LEFT JOIN advert.bills ab ON (ad.advertiser_id = ab.advertiser_id AND (ab.status = 'issued'::bill_status OR ab.status = 'paying'::bill_status))
    WHERE (_advertiser_id ISNULL OR ad.advertiser_id = _advertiser_id) AND ad.status = 'active'::advertiser_status AND (ad.price - ad.balance) > 0 AND (ad.active_util NOTNULL AND ad.active_util > current_timestamp AND age(ad.active_util, current_timestamp) < '10 days') AND ab.bill_id ISNULL
  LOOP
    INSERT INTO advert.bills (advertiser_id, amount, expiring_at)
    VALUES (_periodical_bill_info.advertiser_id, _periodical_bill_info.amount, _periodical_bill_info.active_util);;

    RAISE NOTICE 'A periodical bill is generated with advertiser_id: %, amount: %, expiring_at: %', _periodical_bill_info.advertiser_id, _periodical_bill_info.amount, _periodical_bill_info.active_util;;
  END LOOP;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;