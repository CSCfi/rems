alter table application_event
    drop column userid;
--;;
alter table application_event
    drop column round;
--;;
alter table application_event
    drop column event;
--;;
alter table application_event
    drop column comment;
--;;
alter table application_event
    drop column time;
--;;

alter table catalogue_item_application
    drop column applicantuserid;
--;;
alter table catalogue_item_application
    drop column start;
--;;
alter table catalogue_item_application
    drop column endt;
--;;
alter table catalogue_item_application
    drop column modifieruserid;
--;;
alter table catalogue_item_application
    drop column wfid;
--;;
alter table catalogue_item_application
    drop column description;
--;;

alter table catalogue_item_localization
    drop column start;
--;;
alter table catalogue_item_localization
    drop column endt;
--;;

alter table form_template
    drop column visibility;
--;;

alter table license
    drop column attid;
--;;
alter table license
    drop column visibility;
--;;

alter table license_localization
    drop column attid;
--;;
alter table license_localization
    drop column start;
--;;
alter table license_localization
    drop column endt;
--;;

alter table resource_licenses
    drop column stalling;
--;;

alter table workflow
    drop column fnlround;
--;;
alter table workflow
    drop column visibility;
--;;

alter table workflow_licenses
    drop column round;
--;;
alter table workflow_licenses
    drop column stalling;
--;;
