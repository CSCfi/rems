# adminUtils.Dockerfile
# Pin to postgres:15 on Debian; if you want fewer surprises, you can use :15-bookworm
FROM postgres:15

ENV DEBIAN_FRONTEND=noninteractive

# Minimal, useful admin tooling; netcat-openbsd provides nc
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    jq \
    vim-tiny \
    less \
    procps \
    netcat-openbsd \
    ca-certificates \
  && rm -rf /var/lib/apt/lists/*

# You'll exec with /bin/bash; ensure it's present (it is on postgres:15, but harmless to add)
# RUN apt-get update && apt-get install -y --no-install-recommends bash && rm -rf /var/lib/apt/lists/*

# Nothing else needed; ECS Exec will override the command (e.g., /bin/bash)



