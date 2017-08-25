# Ansible automation for deploying REMS

## Creating resource in cPouta

Dependencies:
  * shade (pip install shade)

1. Make sure you have a value set for at least these
  * rems_secgroup_rules (json list of security group rules for Heat)
  * stack_name (the name of the Heat stack for REMS)
2. Source an openrc file to get credentials to OpenStack to your shell environment
3. Run `ansible-playbook -i <inventory-name> heat_deploy.yml` if you have set stack_name in a vars file
4. Run `ansible-playbook -i <inventory-name> heat_deploy.yml -e "stack_name=<your-stack-name>"` otherwise

## Playbooks

- `heat_deploy.yml` deploy resources to OpenStack for REMS
- `rems.yml` deploy rems war to tomcat
- `postgres.yml` install & configure postgres database
- `tomcat_env.yml` install httpd, shibd and tomcat
