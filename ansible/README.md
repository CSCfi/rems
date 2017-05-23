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

## Deploying the application

1. Make sure your server is setup (see below)
2. Get the `rems.pem` SSH private key. You need to supply this private key to ansible by either
  * using the ansible `--private-key` command line flag
  * adding it to your `.ssh/config`
  * adding it to your `ssh-agent`
3. To use an inventory instead the default one add `-i <inventory-name>` command line flag
4. Run `ansible -m ping all` to check you can reach the host
5. Deploy latest version of rems: `ansible-playbook -vv rems.yml`
6. See it run on <http://vm0773.kaj.pouta.csc.fi/> (not publicly accessible)

## Setting up server
If you already have setup the server you can ignore this step.

1. The playbook assumes that you have already copied host certs and keys to the server.
2. If you are deploying to a new server, please add a `host_vars` file for it and add it to `inventory`.
3. Provide vault password for tomcat_env.yml playbook either with cli arguments `--ask-vault-pass` or `--vault-password-file <path-to-pass-file>`
4. Run `ansible-playbook -vv tomcat_env.yml`
5. Run `ansible-playbook -vv postgres.yml`

## Playbooks

- `heat_deploy.yml` deploy resources to OpenStack for REMS
- `rems.yml` deploy rems war to tomcat
- `postgres.yml` install & configure postgres database
- `tomcat_env.yml` install httpd, shibd and tomcat
