ALTER TABLE api_key
ADD COLUMN permittedRoles jsonb NOT NULL DEFAULT '["applicant", "handler", "logged-in", "owner", "reporter"]'::jsonb;
