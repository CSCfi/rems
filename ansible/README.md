# Ansible automation for deploying REMS

## Getting started

1. Get the `rems.pem` SSH private key. You need to supply this private key to ansible by either
  * using the ansible `--private-key` command line flag
  * adding it to your `.ssh/config`
  * adding it to your `ssh-agent`
2. Run `ansible -m ping all` to check you can reach the host
3. Deploy latest version of rems: `ansible-playbook -vv rems.yml`
4. See it run on <http://vm0773.kaj.pouta.csc.fi/> (not publicly accessible)

## Playbooks

- `rems.yml` deploy rems docker image
- `postgres.yml` install & configure postgres database
