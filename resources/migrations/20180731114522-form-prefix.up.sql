alter table application_form
  add column prefix varchar(255) not null default 'default';
--;;
alter table application_form
  alter column prefix drop default;
