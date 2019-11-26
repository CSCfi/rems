CREATE TABLE entitlement_post_log (
  time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  payload jsonb,
  status varchar(32)
);
