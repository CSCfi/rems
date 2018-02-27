# Server Installation and Update

## Ansible

The tool this project uses to automate installation and deployment is
Ansible. You can find the ansible configuration and playbooks in the
[ansible directory](../ansible/).

If you are feeling anxious about issuing the commands, you can check
the changes beforehand with a dry run by appending `--diff --check` at
the end of the ansible commands.

Common additional arguments:

1. You need to supply a SSH private key, for the target server, to ansible by either
  * using the ansible `--private-key` command line flag
  * adding it to your `.ssh/config`
  * adding it to your `ssh-agent`
2. To use an inventory instead the default one add `-i <inventory-name>` command line flag
3. Run `ansible -m ping all` to check you can reach the host

## Creating a virtual machine in OpenStack

(Optional)

1. Make sure you have a value set for at least these
  * rems_secgroup_rules (json list of security group rules for Heat)
  * stack_name (the name of the Heat stack for REMS)
2. Source an openrc file to get credentials to OpenStack to your shell environment
3. Run `ansible-playbook -i <inventory-name> heat_deploy.yml` if you have set stack_name in a vars file
4. Run `ansible-playbook -i <inventory-name> heat_deploy.yml -e "stack_name=<your-stack-name>"` otherwise

## Preinstall step

Note that these steps will create the deploy user and as such these steps need to be run with a different user.
Admin and normal users can be created to a server by running the `preinstall.yml` with these steps:

1. Run `ansible-galaxy install -f -r requirements.yaml`
2. Specify the users to be added in variables. For more information check the documentation at https://github.com/CSCfi/ansible-role-users.
3. Run `ansible-playbook -vv -e "ansible_user=<user-name>" preinstall.yml`

## Setting up a server
If you already have setup the server you can ignore this step.

1. The playbook assumes that you have already copied host certs and keys to the server.
2. If you are deploying to a new server, please add a `host_vars` file for it and add it to `inventory`.
3. Run `ansible-playbook -vv tomcat_env.yml`
4. Run `ansible-playbook -vv postgres.yml`

## Deploying the application

1. Make sure your server is setup (see above)
2. Deploy latest version of rems: `ansible-playbook -vv rems.yml`

## Database reset (*Possible danger zone*)

This step might be needed if the newly deployed application version contains database changes. If the database in the server in question can be reset (such as in rems2demo), then the following commands can be run:
```
ssh -i path-to-private-key -L localhost:5432:localhost:5432 insert-username@insert-host
DATABASE_URL="postgresql://localhost/rems?user=rems" lein run rollback
DATABASE_URL="postgresql://localhost/rems?user=rems" lein run migrate
DATABASE_URL="postgresql://localhost/rems?user=rems" lein run demo-data
```
