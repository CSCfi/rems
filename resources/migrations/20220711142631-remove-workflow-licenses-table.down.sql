CREATE TABLE if not exists workflow_licenses (
  id serial NOT NULL PRIMARY KEY,
  wfId integer DEFAULT NULL,
  licId integer DEFAULT NULL,
  CONSTRAINT workflow_licenses_ibfk_1 FOREIGN KEY (wfId) REFERENCES workflow (id),
  CONSTRAINT workflow_licenses_ibfk_2 FOREIGN KEY (licId) REFERENCES license (id)
);
