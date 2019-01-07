ALTER TABLE resource
ALTER COLUMN start TYPE timestamp without time zone,
ALTER COLUMN endt TYPE timestamp without time zone;
--;;
ALTER TABLE workflow
ALTER COLUMN start TYPE timestamp without time zone,
ALTER COLUMN endt TYPE timestamp without time zone;
--;;
ALTER TABLE application_form
ALTER COLUMN start TYPE timestamp without time zone,
ALTER COLUMN endt TYPE timestamp without time zone;
--;;
ALTER TABLE catalogue_item
ALTER COLUMN start TYPE timestamp without time zone,
ALTER COLUMN endt TYPE timestamp without time zone;
--;;
ALTER TABLE catalogue_item_application
ALTER COLUMN start TYPE timestamp without time zone,
ALTER COLUMN endt TYPE timestamp without time zone;
--;;
ALTER TABLE application_form_item
ALTER COLUMN start TYPE timestamp without time zone,
ALTER COLUMN endt TYPE timestamp without time zone;
--;;
ALTER TABLE application_form_item_map
ALTER COLUMN start TYPE timestamp without time zone,
ALTER COLUMN endt TYPE timestamp without time zone;
--;;
ALTER TABLE license
ALTER COLUMN start TYPE timestamp without time zone,
ALTER COLUMN endt TYPE timestamp without time zone;
--;;
ALTER TABLE application_license_approval_values
ALTER COLUMN start TYPE timestamp without time zone,
ALTER COLUMN endt TYPE timestamp without time zone;
--;;
ALTER TABLE application_text_values
ALTER COLUMN start TYPE timestamp without time zone,
ALTER COLUMN endt TYPE timestamp without time zone;
--;;
ALTER TABLE workflow_actors
ALTER COLUMN start TYPE timestamp without time zone,
ALTER COLUMN endt TYPE timestamp without time zone;
--;;
ALTER TABLE catalogue_item_application_free_comment_values
ALTER COLUMN start TYPE timestamp without time zone,
ALTER COLUMN endt TYPE timestamp without time zone;
--;;
ALTER TABLE catalogue_item_application_licenses
ALTER COLUMN start TYPE timestamp without time zone,
ALTER COLUMN endt TYPE timestamp without time zone;
--;;
ALTER TABLE catalogue_item_application_members
ALTER COLUMN start TYPE timestamp without time zone,
ALTER COLUMN endt TYPE timestamp without time zone;
--;;
ALTER TABLE catalogue_item_application_metadata
ALTER COLUMN start TYPE timestamp without time zone,
ALTER COLUMN endt TYPE timestamp without time zone;
--;;
ALTER TABLE catalogue_item_application_predecessor
ALTER COLUMN start TYPE timestamp without time zone,
ALTER COLUMN endt TYPE timestamp without time zone;
--;;
ALTER TABLE catalogue_item_localization
ALTER COLUMN start TYPE timestamp without time zone,
ALTER COLUMN endt TYPE timestamp without time zone;
--;;
ALTER TABLE entitlement
ALTER COLUMN start TYPE timestamp without time zone,
ALTER COLUMN endt TYPE timestamp without time zone;
--;;
ALTER TABLE license_localization
ALTER COLUMN start TYPE timestamp without time zone,
ALTER COLUMN endt TYPE timestamp without time zone;
--;;
ALTER TABLE resource_close_period
ALTER COLUMN start TYPE timestamp without time zone,
ALTER COLUMN endt TYPE timestamp without time zone;
--;;
ALTER TABLE resource_licenses
ALTER COLUMN start TYPE timestamp without time zone,
ALTER COLUMN endt TYPE timestamp without time zone;
--;;
ALTER TABLE resource_refresh_period
ALTER COLUMN start TYPE timestamp without time zone,
ALTER COLUMN endt TYPE timestamp without time zone;
--;;
ALTER TABLE resource_state
ALTER COLUMN start TYPE timestamp without time zone,
ALTER COLUMN endt TYPE timestamp without time zone;
--;;
ALTER TABLE user_selection_names
ALTER COLUMN start TYPE timestamp without time zone,
ALTER COLUMN endt TYPE timestamp without time zone;
--;;
ALTER TABLE user_selections
ALTER COLUMN start TYPE timestamp without time zone,
ALTER COLUMN endt TYPE timestamp without time zone;
--;;
ALTER TABLE workflow_approver_options
ALTER COLUMN start TYPE timestamp without time zone,
ALTER COLUMN endt TYPE timestamp without time zone;
--;;
ALTER TABLE workflow_licenses
ALTER COLUMN start TYPE timestamp without time zone,
ALTER COLUMN endt TYPE timestamp without time zone;
--;;
ALTER TABLE workflow_round_min
ALTER COLUMN start TYPE timestamp without time zone,
ALTER COLUMN endt TYPE timestamp without time zone;
--;;
ALTER TABLE application_event
ALTER COLUMN time TYPE timestamp without time zone;
--;;
ALTER TABLE entitlement_post_log
ALTER COLUMN time TYPE timestamp without time zone;
