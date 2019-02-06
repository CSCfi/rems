CREATE TABLE catalogue_item_application_free_comment_values (
	id serial NOT NULL,
	userid varchar(255) NOT NULL,
	catappid int4 NULL,
	"comment" varchar(4096) NULL DEFAULT NULL::character varying,
	public bool NOT NULL DEFAULT false,
	"start" timestamptz NOT NULL DEFAULT now(),
	endt timestamptz NULL,
	CONSTRAINT catalogue_item_application_free_comment_values_pkey PRIMARY KEY (id),
	CONSTRAINT catalogue_item_application_free_comment_values_ibfk_1 FOREIGN KEY (catappid) REFERENCES catalogue_item_application(id)
);
--;;
CREATE TABLE catalogue_item_application_members (
	id serial NOT NULL,
	catappid int4 NULL,
	memberuserid varchar(255) NOT NULL,
	modifieruserid varchar(255) NULL DEFAULT '-1'::character varying,
	"start" timestamptz NOT NULL DEFAULT now(),
	endt timestamptz NULL,
	CONSTRAINT catalogue_item_application_members_pkey PRIMARY KEY (id),
	CONSTRAINT catalogue_item_application_members_ibfk_1 FOREIGN KEY (catappid) REFERENCES catalogue_item_application(id)
);
--;;
CREATE TABLE catalogue_item_application_metadata (
	id serial NOT NULL,
	userid varchar(255) NOT NULL,
	catappid int4 NULL,
	"key" varchar(32) NOT NULL,
	value varchar(256) NOT NULL,
	"start" timestamptz NOT NULL DEFAULT now(),
	endt timestamptz NULL,
	CONSTRAINT catalogue_item_application_metadata_pkey PRIMARY KEY (id),
	CONSTRAINT catalogue_item_application_metadata_ibfk_1 FOREIGN KEY (catappid) REFERENCES catalogue_item_application(id)
);
--;;
CREATE TABLE catalogue_item_application_predecessor (
	id serial NOT NULL,
	pre_catappid int4 NULL,
	suc_catappid int4 NULL,
	modifieruserid varchar(255) NOT NULL,
	"start" timestamptz NOT NULL DEFAULT now(),
	endt timestamptz NULL,
	CONSTRAINT catalogue_item_application_predecessor_pkey PRIMARY KEY (id),
	CONSTRAINT catalogue_item_application_predecessor_ibfk_1 FOREIGN KEY (pre_catappid) REFERENCES catalogue_item_application(id),
	CONSTRAINT catalogue_item_application_predecessor_ibfk_2 FOREIGN KEY (suc_catappid) REFERENCES catalogue_item_application(id)
);
