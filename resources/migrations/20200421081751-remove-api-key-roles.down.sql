ALTER TABLE api_key
ADD COLUMN permittedRoles jsonb NOT NULL DEFAULT '["applicant", "decider", "handler", "logged-in", "owner", "past-reviewer", "reporter", "reviewer", "user-owner", "organization-owner"]'::jsonb;
