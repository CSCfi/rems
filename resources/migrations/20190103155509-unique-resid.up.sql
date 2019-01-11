create unique index resource_resid_u on resource (organization, resid, coalesce(endt, '10000-01-01'));
