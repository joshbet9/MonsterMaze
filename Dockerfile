FROM ubuntu:24.04

RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 ca-certificates logrotate \
    && rm -rf /var/lib/apt/lists/*

# The player Solo packs contain Windows-only runtimes.  The server templates are
# copied from those packs, but both production servers use the Linux Java 21 runtime
# installed below.  Java 21 is already verified to run both Monster Maze servers.
RUN apt-get update \
    && apt-get install -y --no-install-recommends openjdk-21-jre-headless \
    && rm -rf /var/lib/apt/lists/*

COPY solo/solo-dist/server /opt/mm18-template
COPY solo/1.21/solo-dist/server /opt/mm21-template
COPY docker/entrypoint.sh /entrypoint.sh
COPY docker/submitter.py /opt/monstermaze-submitter.py
COPY docker/logrotate.conf /etc/logrotate.d/monstermaze

RUN chmod +x /entrypoint.sh \
    && mkdir -p /data/1.8 /data/1.21

VOLUME ["/data"]
EXPOSE 25565/tcp 25566/tcp

ENTRYPOINT ["/entrypoint.sh"]
