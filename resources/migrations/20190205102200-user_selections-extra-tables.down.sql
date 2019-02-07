CREATE TABLE user_selections (
	id serial NOT NULL,
	actionid int8 NOT NULL,
	groupid int4 NOT NULL,
	userid varchar(255) NOT NULL,
	"start" timestamptz NOT NULL DEFAULT now(),
	endt timestamptz NULL,
	CONSTRAINT user_selections_pkey PRIMARY KEY (id)
);
--;;
CREATE TABLE user_selection_names (
	id serial NOT NULL,
	actionid int8 NOT NULL,
	groupid int4 NOT NULL,
	listname varchar(32) NULL DEFAULT NULL::character varying,
	"start" timestamptz NOT NULL DEFAULT now(),
	endt timestamptz NULL,
	CONSTRAINT user_selection_names_pkey PRIMARY KEY (id)
);