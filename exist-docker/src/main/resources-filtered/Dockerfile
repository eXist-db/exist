FROM gcr.io/distroless/java

# Build-time metadata as defined at http://label-schema.org
# and used by autobuilder @hooks/build
LABEL org.label-schema.build-date=${build-tstamp} \
      org.label-schema.description="${project.description}" \
      org.label-schema.name="existdb" \
      org.label-schema.schema-version="1.0" \
      org.label-schema.url="${project.url}" \
      org.label-schema.vcs-ref=${build-commit-abbrev} \
      org.label-schema.vcs-url="${project.scm.url}" \
      org.label-schema.vendor="existdb"

ENV EXIST_HOME  "/exist"

COPY LICENSE /exist/LICENSE
COPY autodeploy /exist/autodeploy
COPY bin /exist/bin
COPY etc /exist/etc
COPY lib /exist/lib
COPY logs /exist/logs

EXPOSE 8080 8443

HEALTHCHECK CMD [ "java", \
    "-Xms128m", \
    "-Dfile.encoding=UTF-8", \
    "-Dlog4j.configurationFile=/exist/etc/log4j2.xml", \
    "-Dexist.home=/exist", \
    "-Dexist.configurationFile=/exist/etc/conf.xml", \
    "-Djetty.home=/exist", \
    "-Dexist.jetty.config=/exist/etc/jetty/standard.enabled-jetty-configs", \
    "-classpath", \
    "/exist/etc:/exist/lib/appassembler-booter-2.0.1-SNAPSHOT.jar:/exist/lib/appassembler-model-2.0.1-SNAPSHOT.jar:/exist/lib/plexus-utils-3.0.24.jar:/exist/lib/stax-api-1.0.1.jar:/exist/lib/stax-1.1.1-dev.jar", \
    "-Dapp.name=client", \
    "-Dapp.repo=/exist/lib", \
    "-Dapp.home=/exist", \
    "-Dbasedir=/exist", \
    "org.codehaus.mojo.appassembler.booter.AppassemblerBooter", \
    "--no-gui",  "--xpath", "system:get-version()" ]

ENTRYPOINT [ "java", \
    "-Xms128m", \
    "-Dfile.encoding=UTF-8", \
    "-Dlog4j.configurationFile=/exist/etc/log4j2.xml", \
    "-Dexist.home=/exist", \
    "-Dexist.configurationFile=/exist/etc/conf.xml", \
    "-Djetty.home=/exist", \
    "-Dexist.jetty.config=/exist/etc/jetty/standard.enabled-jetty-configs", \
    "-classpath", \
    "/exist/etc:/exist/lib/appassembler-booter-2.0.1-SNAPSHOT.jar:/exist/lib/appassembler-model-2.0.1-SNAPSHOT.jar:/exist/lib/plexus-utils-3.0.24.jar:/exist/lib/stax-api-1.0.1.jar:/exist/lib/stax-1.1.1-dev.jar", \
    "-Dapp.name=startup", \
    "-Dapp.repo=/exist/lib", \
    "-Dapp.home=/exist", \
    "-Dbasedir=/exist", \
    "org.codehaus.mojo.appassembler.booter.AppassemblerBooter" ]