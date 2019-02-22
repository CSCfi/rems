create type item_state as enum ('disabled', 'enabled', 'copied');
--;;
alter table catalogue_item
  add column state item_state default 'enabled' not null;
--;;
update catalogue_item
set state = 'enabled'
where enabled = true;
--;;
update catalogue_item
set state = 'disabled'
where enabled = false;
--;;
alter table catalogue_item
  drop column enabled,
  drop column archived;
--;;
alter table resource
  drop column enabled,
  drop column archived;
--;;
alter table application_form
  drop column enabled,
  drop column archived;
--;;
alter table workflow
  drop column enabled,
  drop column archived;
--;;
alter table license
  drop column enabled,
  drop column archived;
