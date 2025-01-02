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

# Install latest JRE 8 in Debian bookworm (which is the base of gcr.io/distroless/java)
# FROM maven:3-eclipse-temurin-8 as updated-jre

# RUN apt-get update && apt-get -y dist-upgrade
# RUN apt install -y wget apt-transport-https gpg
# RUN wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor | tee /etc/apt/trusted.gpg.d/adoptium.gpg > /dev/null
# RUN echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | tee /etc/apt/sources.list.d/adoptium.list
# RUN apt-get update && apt-get install -y temurin-8-jre

FROM gcr.io/distroless/java-base-debian12:latest

ENV JAVA_HOME=/opt/java/openjdk
COPY --from=eclipse-temurin:8-jre $JAVA_HOME $JAVA_HOME
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# Copy eXist-db
COPY LICENSE /exist/LICENSE
COPY autodeploy /exist/autodeploy
COPY etc /exist/etc
COPY lib /exist/lib
COPY logs /exist/logs


# Build-time metadata as defined at http://label-schema.org
# and used by autobuilder @hooks/build
LABEL org.label-schema.build-date=${maven.build.timestamp} \
      org.label-schema.description="${project.description}" \
      org.label-schema.name="existdb" \
      org.label-schema.schema-version="1.0" \
      org.label-schema.url="${project.url}" \
      org.label-schema.vcs-ref=${build-commit-abbrev} \
      org.label-schema.vcs-url="${project.scm.url}" \
      org.label-schema.vendor="existdb"

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
  -Dlog4j.configurationFile=/exist/etc/log4j2.xml \
  -Dexist.home=/exist \
  -Dexist.configurationFile=/exist/etc/conf.xml \
  -Djetty.home=/exist \
  -Dexist.jetty.config=/exist/etc/jetty/standard.enabled-jetty-configs \
  -XX:+UseG1GC \
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
