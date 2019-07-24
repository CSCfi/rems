alter table application_event
    add if not exists userid varchar(255);
--;;
alter table application_event
    add if not exists round integer not null default '-1';
--;;
alter table application_event
    add if not exists event varchar(100);
--;;
alter table application_event
    add if not exists comment varchar(4096) default NULL::character varying;
--;;
alter table application_event
    add if not exists time timestamp with time zone default now() not null;
--;;

alter table catalogue_item_application
    add if not exists applicantuserid varchar(255) not null default '';
--;;
alter table catalogue_item_application
    add if not exists start timestamp with time zone default now() not null;
--;;
alter table catalogue_item_application
    add if not exists endt timestamp with time zone;
--;;
alter table catalogue_item_application
    add if not exists modifieruserid varchar(255) default NULL::character varying;
--;;
alter table catalogue_item_application
    add if not exists wfid integer;
--;;
alter table catalogue_item_application
    add if not exists description varchar(255);
--;;

alter table catalogue_item_localization
    add if not exists start timestamp with time zone default now() not null;
--;;
alter table catalogue_item_localization
    add if not exists endt timestamp with time zone;
--;;

alter table form_template
    add if not exists visibility scope not null default 'public';
--;;

alter table license
    add if not exists attid integer;
--;;
alter table license
    add if not exists visibility scope default 'private'::scope not null;
--;;

alter table license_localization
    add if not exists attid integer;
--;;
alter table license_localization
    add if not exists start timestamp with time zone default now() not null;
--;;
alter table license_localization
    add if not exists endt timestamp with time zone;
--;;

alter table resource_licenses
    add if not exists stalling boolean default false not null;
--;;

alter table workflow
    add if not exists fnlround integer not null default 0;
--;;
alter table workflow
    add if not exists visibility scope default 'private'::scope not null;
--;;

alter table workflow_licenses
    add if not exists round integer not null default 0;
--;;
alter table workflow_licenses
    add if not exists stalling boolean default false not null;
--;;
