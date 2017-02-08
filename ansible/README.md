# Ansible automation for deploying REMS

## Getting started

1. Get the `rems.pem` SSH private key
2. Run `ansible --private-key=path/to/rems.pem -m ping all` to check you can reach the host
3. Deploy latest version of rems: `ansible-playbook --private-key=path/to/rems.pem -vv rems.yml`
4. See it run on <http://ec2-35-157-98-249.eu-central-1.compute.amazonaws.com/>

## AWS infrastructure

- A private Docker image registry (Amazon Elastic Container Registry)
- An EC2 virtual machine with a role with the
  `AmazonEC2ContainerRegistryReadOnly` policy
