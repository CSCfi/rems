-- NB! this might fail if db contains duplicate resids.
CREATE UNIQUE INDEX resource_resid_u ON resource (organization, resid);
