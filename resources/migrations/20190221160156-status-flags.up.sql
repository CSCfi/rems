alter table catalogue_item
  add column enabled boolean default true not null ,
  add column archived boolean default false not null;
--;;
update catalogue_item
set enabled = (state = 'enabled');
--;;
alter table catalogue_item
  drop column state;
--;;
drop type item_state;
--;;
alter table resource
  add column enabled boolean default true not null ,
  add column archived boolean default false not null;
--;;
alter table application_form
  add column enabled boolean default true not null ,
  add column archived boolean default false not null;
--;;
alter table workflow
  add column enabled boolean default true not null ,
  add column archived boolean default false not null;
--;;
alter table license
  add column enabled boolean default true not null ,
  add column archived boolean default false not null;
