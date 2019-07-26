create table if not exists application_form
(
    id             serial                                 not null
        constraint application_form_pkey
            primary key,
    owneruserid    varchar(255)                           not null,
    modifieruserid varchar(255)                           not null,
    title          varchar(256)                           not null,
    visibility     scope                                  not null,
    start          timestamp with time zone default now() not null,
    endt           timestamp with time zone,
    organization   varchar(255)                           not null,
    enabled        boolean                  default true  not null,
    archived       boolean                  default false not null
);
--;;
create table if not exists application_form_item
(
    id             serial                                 not null
        constraint application_form_item_pkey
            primary key,
    owneruserid    varchar(255)                           not null,
    modifieruserid varchar(255)                           not null,
    type           itemtype,
    value          bigint                                 not null,
    visibility     scope                                  not null,
    start          timestamp with time zone default now() not null,
    endt           timestamp with time zone
);
--;;
create table if not exists application_form_item_localization
(
    itemid      integer       not null
        constraint application_form_item_localization_itemid_fkey
            references application_form_item,
    langcode    varchar(64),
    title       varchar(4096) not null,
    tooltip     varchar(256) default NULL::character varying,
    inputprompt varchar(256) default NULL::character varying,
    constraint application_form_item_localization_itemid_langcode_key
        unique (itemid, langcode)
);
--;;
create table if not exists application_form_item_map
(
    id               serial                                 not null
        constraint application_form_item_map_pkey
            primary key,
    formid           integer
        constraint application_form_item_map_ibfk_1
            references application_form,
    formitemid       integer
        constraint application_form_item_map_ibfk_2
            references application_form_item,
    formitemoptional boolean                  default false not null,
    modifieruserid   varchar(255)                           not null,
    itemorder        integer,
    start            timestamp with time zone default now() not null,
    endt             timestamp with time zone,
    maxlength        smallint
);
--;;
create table if not exists application_form_item_options
(
    itemid       integer      not null
        constraint application_form_item_options_itemid_fkey
            references application_form_item,
    key          varchar(255) not null,
    langcode     varchar(64)  not null,
    label        varchar(255) not null,
    displayorder integer      not null,
    constraint application_form_item_options_pkey
        primary key (itemid, key, langcode)
);
--;;
create table if not exists application_license_approval_values
(
    id             serial                                 not null
        constraint application_license_approval_values_pkey
            primary key,
    catappid       integer
        constraint application_license_approval_values_ibfk_1
            references catalogue_item_application,
    formmapid      integer
        constraint application_license_approval_values_ibfk_2
            references application_form_item_map,
    licid          integer                                not null
        constraint application_license_approval_values_ibfk_3
            references license,
    modifieruserid varchar(255)             default NULL::character varying,
    state          license_status                         not null,
    start          timestamp with time zone default now() not null,
    endt           timestamp with time zone
);
--;;
create table if not exists application_text_values
(
    id             serial                                 not null
        constraint application_text_values_pkey
            primary key,
    catappid       integer
        constraint application_text_values_ibfk_1
            references catalogue_item_application,
    formmapid      integer
        constraint application_text_values_ibfk_2
            references application_form_item_map,
    modifieruserid varchar(255)                           not null,
    value          varchar(4096)                          not null,
    start          timestamp with time zone default now() not null,
    endt           timestamp with time zone,
    constraint application_text_values_catappid_formmapid_key
        unique (catappid, formmapid)
);
--;;
create table if not exists catalogue_item_application_items
(
    catappid  integer
        constraint catalogue_item_application_items_catappid
            references catalogue_item_application,
    catitemid integer
        constraint catalogue_item_application_items_catitemid
            references catalogue_item
);
--;;
create table if not exists catalogue_item_application_licenses
(
    id          serial                                                    not null
        constraint catalogue_item_application_licenses_pkey
            primary key,
    catappid    integer
        constraint catalogue_item_application_licenses_ibfk_1
            references catalogue_item_application,
    licid       integer
        constraint catalogue_item_application_licenses_ibfk_2
            references license,
    actoruserid varchar(255)                                              not null,
    round       integer                                                   not null,
    stalling    boolean                  default false                    not null,
    state       license_state            default 'created'::license_state not null,
    start       timestamp with time zone default now()                    not null,
    endt        timestamp with time zone
);
--;;
create table if not exists resource_close_period
(
    id             serial                                 not null
        constraint resource_close_period_pkey
            primary key,
    resid          integer
        constraint resource_close_period_ibfk_1
            references resource,
    closeperiod    integer,
    modifieruserid varchar(255)                           not null,
    start          timestamp with time zone default now() not null,
    endt           timestamp with time zone
);
--;;
create table if not exists resource_refresh_period
(
    id             serial                                 not null
        constraint resource_refresh_period_pkey
            primary key,
    resid          integer
        constraint resource_refresh_period_ibfk_1
            references resource,
    refreshperiod  integer,
    modifieruserid varchar(255)                           not null,
    start          timestamp with time zone default now() not null,
    endt           timestamp with time zone
);
--;;
create table if not exists resource_state
(
    id             serial                                 not null
        constraint resource_state_pkey
            primary key,
    resid          integer
        constraint resource_state_ibfk_1
            references resource,
    owneruserid    varchar(255)                           not null,
    modifieruserid varchar(255)                           not null,
    start          timestamp with time zone default now() not null,
    endt           timestamp with time zone
);
--;;
create table if not exists workflow_actors
(
    id          serial                                 not null
        constraint workflow_actors_pkey
            primary key,
    wfid        integer
        constraint workflow_actors_ibfk_1
            references workflow,
    actoruserid varchar(255)                           not null,
    role        workflow_actor_role                    not null,
    round       integer                                not null,
    start       timestamp with time zone default now() not null,
    endt        timestamp with time zone
);
--;;
create table if not exists workflow_approver_options
(
    id          serial                                 not null
        constraint workflow_approver_options_pkey
            primary key,
    wfapprid    integer
        constraint workflow_approver_options_ibfk_1
            references workflow_actors,
    keyvalue    varchar(256)                           not null,
    optionvalue varchar(256)                           not null,
    start       timestamp with time zone default now() not null,
    endt        timestamp with time zone
);
--;;
create table if not exists workflow_round_min
(
    id    serial                                 not null
        constraint workflow_round_min_pkey
            primary key,
    wfid  integer
        constraint workflow_round_min_ibfk_1
            references workflow,
    min   integer                                not null,
    round integer                                not null,
    start timestamp with time zone default now() not null,
    endt  timestamp with time zone
);
--;;
