# Server Installation and Update

If you are feeling anxious about issuing the commands, you can check the changes beforehand with a dry run by appending `--diff --check` at the end of the ansible commands.

Common additional arguments:

1. You need to supply a SSH private key, for the target server, to ansible by either
  * using the ansible `--private-key` command line flag
  * adding it to your `.ssh/config`
  * adding it to your `ssh-agent`
2. To use an inventory instead the default one add `-i <inventory-name>` command line flag
3. Run `ansible -m ping all` to check you can reach the host

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
