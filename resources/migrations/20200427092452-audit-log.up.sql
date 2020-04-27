CREATE TABLE audit_log (
  time timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  path varchar(255) NOT NULL,
  method varchar(10) NOT NULL,
  apiKey varchar(255),
  userid varchar(255),
  status varchar(10) NOT NULL
);
--;;
CREATE INDEX audit_log_time ON audit_log (time);
--;;
CREATE INDEX audit_log_userid ON audit_log (userid);
