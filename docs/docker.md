# Deploy Rems in docker

1. Build rems with lein or copy rems.jar to target/uberjar/rems.jar
2. Build rems docker image. Use the docker file located in the root directory of rems git.
Example command: `docker build /path/to/rems/git/root/dir/ -t rems:tag`
2. Mount your config.edn file to /rems/config/config.edn in the container (Optional)
3. Mount certificate to be added to rems certificate store to /rems/certs/.
This process currecntly supports only one certificate. (Optional)
