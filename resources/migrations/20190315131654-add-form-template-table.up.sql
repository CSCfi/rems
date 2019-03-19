CREATE TABLE form_template (
  id serial NOT NULL PRIMARY KEY,
  ownerUserId varchar(255) NOT NULL,
  modifierUserId varchar(255) NOT NULL,
  title varchar(256) NOT NULL,
  visibility scope NOT NULL,
  fields jsonb,
  start timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  endt timestamp with time zone NULL DEFAULT NULL,
  organization varchar(255) not null,
  enabled boolean default true not null,
  archived boolean default false not null
);
