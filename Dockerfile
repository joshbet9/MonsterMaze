FROM ubuntu:24.04

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates logrotate \
    && rm -rf /var/lib/apt/lists/*

# Both public servers are currently verified to run correctly on Linux Java 21.
RUN apt-get update \
    && apt-get install -y --no-install-recommends openjdk-21-jre-headless \
    && rm -rf /var/lib/apt/lists/*

COPY solo/solo-dist/server /opt/mm18-template
COPY solo/1.21/solo-dist/server /opt/mm21-template
COPY docker/entrypoint.sh /entrypoint.sh
COPY docker/logrotate.conf /etc/logrotate.d/monstermaze

# Never depend on source-checkout line endings at runtime. Fly process-group
# commands are passed as arguments to the image entrypoint.
RUN sed -i 's/\r$//' /entrypoint.sh \
    && chmod +x /entrypoint.sh \
    && mkdir -p /data/1.8 /data/1.21

EXPOSE 25565/tcp 25566/tcp

ENTRYPOINT ["/bin/bash", "-c", "sed -i 's/\\r$//' /entrypoint.sh && exec /bin/bash /entrypoint.sh \"$@\"", "--"]
