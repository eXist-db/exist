#
# eXist-db Open Source Native XML Database
# Copyright (C) 2001 The eXist-db Authors
#
# info@exist-db.org
# http://www.exist-db.org
#
# This library is free software; you can redistribute it and/or
# modify it under the terms of the GNU Lesser General Public
# License as published by the Free Software Foundation; either
# version 2.1 of the License, or (at your option) any later version.
#
# This library is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
# Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public
# License along with this library; if not, write to the Free Software
# Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
#

FROM ${docker.base.image}

# Copy eXist-db
COPY LICENSE /exist/LICENSE
COPY autodeploy /exist/autodeploy
COPY etc /exist/etc
COPY lib /exist/lib
COPY logs /exist/logs
COPY schema /exist/schema


# Build-time metadata as defined by OCI
# https://github.com/opencontainers/image-spec/blob/main/annotations.md
LABEL org.opencontainers.image.created="${maven.build.timestamp}" \
      org.opencontainers.image.description="${project.description}" \
      org.opencontainers.image.revision=${git.commit.id} \
      org.opencontainers.image.version=${project.version} \
      org.opencontainers.image.url="${docker.url}" \
      org.opencontainers.image.source="${docker.source}" \
      org.opencontainers.image.vendor="${docker.vendor}" \
      org.opencontainers.image.licenses="${docker.licenses}" \
      org.opencontainers.image.title="${docker.title}" \
      org.opencontainers.image.base.name=${docker.base.image}

EXPOSE 8080 8443

# make CACHE_MEM, MAX_BROKER, and JVM_MAX_RAM_PERCENTAGE available to users
ARG CACHE_MEM
ARG MAX_BROKER
ARG JVM_MAX_RAM_PERCENTAGE

ENV EXIST_HOME=/exist
ENV CLASSPATH=/exist/lib/${exist.uber.jar.filename}

ENV JAVA_TOOL_OPTIONS="\
  -Dfile.encoding=UTF8 \
  -Dsun.jnu.encoding=UTF-8 \
  -Djava.awt.headless=true \
  -Dorg.exist.db-connection.cacheSize=${CACHE_MEM:-256}M \
  -Dorg.exist.db-connection.pool.max=${MAX_BROKER:-20} \
  -Dlog4j.configurationFile=/exist/etc/log4j2-container.xml \
  -Dexist.home=/exist \
  -Dexist.configurationFile=/exist/etc/conf.xml \
  -Djetty.home=/exist \
  -Dexist.jetty.config=/exist/etc/jetty/standard.enabled-jetty-configs \
  -XX:+UseNUMA \
  -XX:+UseZGC \
  -XX:+UseStringDeduplication \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=${JVM_MAX_RAM_PERCENTAGE:-75.0} \
  -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK CMD [ "java", \
    "org.exist.start.Main", "client", \
    "--no-gui",  \
    "--user", "guest", "--password", "guest", \
    "--xpath", "system:get-version()" ]

ENTRYPOINT [ "java", \
    "org.exist.start.Main"]
CMD ["jetty" ]
