FROM ubuntu:24.04

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates logrotate \
    && rm -rf /var/lib/apt/lists/*

# Both public servers run from the same image, with Java 21 selected as the
# common runtime. The release pipeline supplies the exact tested server
# templates in docker/build-context/.
RUN apt-get update \
    && apt-get install -y --no-install-recommends openjdk-21-jre-headless \
    && rm -rf /var/lib/apt/lists/*

COPY docker/build-context/1.8/server /opt/mm18-template
COPY docker/build-context/1.21/server /opt/mm21-template
COPY docker/deployments/fly/1.8/config.yml /opt/mm18-deployment-config.yml
COPY docker/deployments/fly/1.21/config.yml /opt/mm21-deployment-config.yml
COPY docker/entrypoint.sh /entrypoint.sh
COPY docker/logrotate.conf /etc/logrotate.d/monstermaze

RUN sed -i 's/\r$//' /entrypoint.sh \
    && chmod +x /entrypoint.sh \
    && mkdir -p /data/1.8 /data/1.21

EXPOSE 25565/tcp 25566/tcp

ENTRYPOINT ["/bin/bash", "-c", "sed -i 's/\\r$//' /entrypoint.sh && exec /bin/bash /entrypoint.sh \"$@\"", "--"]
