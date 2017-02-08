# Ansible automation for deploying REMS

## Getting started

1. Get the `rems.pem` SSH private key
2. Run `ansible --private-key=path/to/rems.pem -m ping all` to check you can reach the host

## AWS infrastructure

- A private Docker image registry (Amazon Elastic Container Registry)
- An EC2 virtual machine with a role with the
  `AmazonEC2ContainerRegistryReadOnly` policy
