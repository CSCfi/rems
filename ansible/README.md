# Ansible automation for deploying REMS

See [docs/server_maintenance.md](../docs/server_maintenance.md)

## Playbooks

- `heat_deploy.yml` deploy resources to OpenStack for REMS
- `rems.yml` deploy rems war to tomcat
- `postgres.yml` install & configure postgres database
- `tomcat_env.yml` install httpd, shibd and tomcat
