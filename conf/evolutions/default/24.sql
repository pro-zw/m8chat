# --- !Ups

CREATE OR REPLACE FUNCTION advert.list_adverts_nearby(IN _current_user_id INTEGER, IN _page INTEGER, IN _page_size INTEGER)
  RETURNS TABLE (_advert_id INTEGER, _business_name TEXT, _first_photo TEXT, _plan_name TEXT, _distance INTEGER) AS $$
DECLARE
  _page_offset INTEGER := _page * _page_size;;
BEGIN
  RETURN QUERY
    WITH adverts_nearby AS (
      UPDATE advert.adverts AS av
      SET displayed_times = displayed_times + 1
      FROM advert.advertisers AS ar INNER JOIN social.m8_users AS u ON (u.user_id = _current_user_id)
      WHERE av.advertiser_id = ar.advertiser_id AND av.advert_id IN (
        SELECT av.advert_id
        FROM advert.adverts av INNER JOIN social.m8_users AS u ON (u.user_id = _current_user_id) INNER JOIN advert.advertisers ar ON (av.advertiser_id = ar.advertiser_id AND ar.status = 'active'::advertiser_status AND ceil(ST_Distance(av.position, u.position)/1000.0)::INTEGER <= 30)
        ORDER BY ar.plan_name DESC, ceil(ST_Distance(av.position, u.position)/1000.0)::INTEGER ASC, av.business_name ASC
        OFFSET _page_offset
        LIMIT _page_size
        FOR UPDATE
      )
      RETURNING av.advert_id, av.business_name::TEXT, CASE WHEN array_length(array_remove(av.photos, ''), 1) > 0 THEN (array_remove(av.photos, ''))[1] ELSE '' END, ar.plan_name::TEXT, ceil(ST_Distance(av.position, u.position)/1000.0)::INTEGER AS _cte_distance
    )
    SELECT * FROM adverts_nearby ORDER BY plan_name::plan_name DESC, _cte_distance ASC, business_name ASC;;
END;;
$$
LANGUAGE 'plpgsql' VOLATILE;
