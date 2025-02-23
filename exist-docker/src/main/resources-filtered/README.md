# ${project.description}
${project.description}

[![Build Status](https://travis-ci.com/eXist-db/exist.png?branch=develop)](https://travis-ci.com/eXist-db/exist)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/c5d7a02842dd4a3c85b1b2ad421b0d13)](https://www.codacy.com/app/eXist-db/exist?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=eXist-db/exist&amp;utm_campaign=Badge_Grade)
[![License](https://img.shields.io/badge/license-AGPL%203.1-orange.svg)](https://www.gnu.org/licenses/agpl-3.0.html)
[![](https://images.microbadger.com/badges/image/existdb/existdb.svg)](https://microbadger.com/images/existdb/existdb "Get your own image badge on microbadger.com")
[![](https://images.microbadger.com/badges/version/existdb/existdb.svg)](https://microbadger.com/images/existdb/existdb "Get your own version badge on microbadger.com")
[![](https://images.microbadger.com/badges/commit/existdb/existdb.svg)](https://microbadger.com/images/existdb/existdb "Get your own commit badge on microbadger.com")

This module holds the source files for building a minimal docker image of the [exist-db](https://www.exist-db.org) xml 
database, images are automatically updated as part of the build-test life-cycle. 
The images are based on Google Cloud Platform's ["Distroless" Docker Images](https://github.com/GoogleCloudPlatform/distroless).


## Requirements
*   [Docker](https://www.docker.com): `18-stable`
### For building
*   [maven](https://maven.apache.org/): `^3.6.0`
*   [java](https://www.java.com/): `21`
*   [bats](https://github.com/bats-core/bats-core): `^1.1.0` (for testing)

## How to use
Pre-build images are available on [DockerHub](https://hub.docker.com/r/existdb/existdb/). 
There are two continuously updated channels:
*   `release` for the stable releases based on the [`master` branch](https://github.com/eXist-db/exist/tree/master)
*   `latest` for the latest commit to the [`develop` branch](https://github.com/eXist-db/exist/tree/develop).

To download the image run:
```bash
docker pull existdb/existdb:latest
```

Once the download is complete, you can run the image
```bash
docker run -dit -p 8080:8080 -p 8443:8443 --name exist existdb/existdb:latest
```

### What does this do?

*   `-it` allocates a TTY and keeps STDIN open.  This allows you to interact with the running Docker container via your console.
*   `-d` detaches the container from the terminal that started it. So your container won't stop when you close the terminal.
*   `-p` maps the Containers internal and external port assignments (we recommend sticking with matching pairs). This allows you to connect to the eXist-db Web Server running in the Docker container.
*   `--name` lets you provide a name (instead of using a randomly generated one)

The only required parts are `docker run existdb/existdb`. 
For a full list of available options see the official [Docker documentation](https://docs.docker.com/engine/reference/commandline/run/)

After running the `pull` and `run` commands, you can access eXist-db via [localhost:8080](localhost:8080) in your browser.

To stop the container issue:
```bash
docker stop exist
```

or if you omitted the `-d` flag earlier press `CTRL-C` inside the terminal showing the exist logs.

### Interacting with the running container
You can interact with a running container as if it were a regular Linux host (without a shell in our case). 
You can issue shell-like commands to the Java admin client, as we do throughout this readme, but you can't open the shell in interactive mode.

The name of the container in this readme is `exist`, adjust the name in the commands to suit your needs:

```bash
# Using java syntax on a running eXist-db instances
docker exec exist java org.exist.start.Main client --no-gui --xpath "system:get-version()"

# Interacting with the JVM
docker exec exist java -version
```

Containers build from this image run periodical healthchecks to ensure that eXist-db is operating normally. 
If `docker ps` reports `unhealthy` you can get a more detailed report with this command:  
```bash
docker inspect --format='{{json .State.Health}}' exist
```

### Logging
There is a slight modification to eXist's logger to ease access to the logs via:
```bash
docker logs exist
```

This works best when providing the `-t` flag when running an image.

## Use as base image
A common usage of these images is as a base image for your own applications. 
We'll take a quick look at three scenarios of increasing complexity, to demonstrate how to achieve common tasks from inside `Dockerfile`.

### A simple app image
The simplest and straightforward case assumes that you have a `.xar` app inside a `build` folder on the same level as the `Dockerfile`. 
To get an image of an eXist-db instance with your app installed and running, simply adopt the `docker cp ...` command to the appropriate `Dockerfile` syntax.
```docker
FROM existdb/existdb:5.0.0

COPY build/*.xar /exist/autodeploy
```

You should see something like this:

```bash
Sending build context to Docker daemon  4.337MB
Step 1/2 : FROM existdb/existdb:5.0.0
 ---> 3f4dbbce9afa
Step 2/2 : COPY build/*.xar /exist/autodeploy
 ---> ace38b0809de
```

The result is a new image of your app installed into eXist-db. 
Since you didn't provide further instructions it will simply reuse the `EXPOSE`, `CMD`, `HEALTHCHECK`, etc instructions defined by the base image. 
You can now publish this image to a docker registry and share it with others.

### A slightly more complex single stage image
The following example will install your app, but also modify the underlying eXist-db instance in which your app is running. 
Instead of a local build directory, we'll download the `.xar` from the web, and copy a modified `conf.xml` from a `src/` directory along side your `Dockerfile`. 
To execute any of the `docker exec …` style commands from this readme, we need to use `RUN`.

```docker
FROM existdb/existdb

# NOTE: this is for syntax demo purposes only
RUN [ "java", "org.exist.start.Main", "client", "--no-gui",  "-l", "-u", "admin", "-P", "", "-x", "sm:passwd('admin','123')" ]

# use a modified conf.xml
COPY src/conf.xml /exist/etc

ADD https://github.com/eXist-db/documentation/releases/download/4.0.4/exist-documentation-4.0.4.xar /exist/autodeploy
```

The above is intended to demonstrate the kind of operations available to you in a single stage build. 
For security reasons [more elaborate techniques](https://docs.docker.com/engine/swarm/secrets/) for not sharing your password in the clear are highly recommended, 
such as the use of secure variables inside your CI environment. 
However, the above shows you how to execute the [Java Admin Client](http://www.exist-db.org/exist/apps/doc/java-admin-client.xml) from inside a `Dockerfile`, 
which in turn allows you to run any XQuery code you want when modifying the eXist-db instance that will ship with your images. You can also chain multiple `RUN` commands.

As for the sequence of the commands, those with the most frequent changes should come last to avoid cache busting. 
Chances are, you wouldn't change the admin password very often, but the `.xar` might change more frequently.

### Multi-stage build with ant
Lastly, you can eliminate external dependencies even further by using a multi-stage build. 
To ensure compatibility between different Java engines we recommend sticking with debian based images for the builder stage.

The following 2-stage build will download and install `ant` and `nodeJS` into a builder stage which then downloads frontend dependencies before building the `.xar` file.
The second stage (each `FROM` begins a stage) is just the simple example from above. 
Such a setup ensures that non of your collaborators has to have `java` or `nodeJS` installed, and is great for fully automated builds and deployment.

```docker
# START STAGE 1
FROM openjdk:8-jdk-slim as builder

USER root

ENV ANT_VERSION 1.10.5
ENV ANT_HOME /etc/ant-${ANT_VERSION}

WORKDIR /tmp

RUN wget http://www-us.apache.org/dist/ant/binaries/apache-ant-${ANT_VERSION}-bin.tar.gz \
    && mkdir ant-${ANT_VERSION} \
    && tar -zxvf apache-ant-${ANT_VERSION}-bin.tar.gz \
    && mv apache-ant-${ANT_VERSION} ${ANT_HOME} \
    && rm apache-ant-${ANT_VERSION}-bin.tar.gz \
    && rm -rf ant-${ANT_VERSION} \
    && rm -rf ${ANT_HOME}/manual \
    && unset ANT_VERSION

ENV PATH ${PATH}:${ANT_HOME}/bin

WORKDIR /home/my-app
COPY . .
RUN apk add --no-cache --virtual .build-deps \
 nodejs \
 nodejs-npm \
 git \
 && npm i npm@latest -g \
 && ant


# START STAGE 2
FROM existdb/existdb:release

COPY --from=builder /home/my-app/build/*.xar /exist/autodeploy

EXPOSE 8080 8443

CMD [ "java", "org.exist.start.Main", "jetty" ]
```

The basic idea of the multi-staging is that everything you need for building your software should be managed by docker, 
so that all collaborators can rely on one stable environment. In the end, and after how ever many stages you need, 
only the files necessary to run your app should go into the final stage. The possibilities are virtually endless, 
but with this example and the `Dockerfile` in this repo you should get a pretty good idea of how you might apply this idea to your own projects.

## Development use via `docker-compose`
We highly recommend use of a `docker-compose.yml` for use with [docker-compose](https://docs.docker.com/compose/). 
docker-compose for local development or integration into multi-container environments. 
For options on how to configure your own compose file, follow the link at the beginning of this paragraph.

To start exist using a compose file, type:
```bash
# starting eXist-db
docker-compose up -d
# stop eXist-db
docker-compose down
```

[Volumes](https://docs.docker.com/storage/volumes/) let you ensure data persistence between reboots, 
in particular:

*   `exist/data` so that any database changes persist through reboots and updates.
*   `exist/etc` so you can configure eXist startup options.

can be declared as mount volumes. 

You can configure additional volumes e.g. for backups, 
or additional services such as an nginx reverse proxy via a `docker-compose.yml`, to suite your needs.

To update the exist-docker image from a newer version
```bash
docker-compose pull
```

### Caveat
As with normal installations, the password for the default dba user `admin` is empty. 
Change it via the [usermanager](http://localhost:8080/exist/apps/usermanager/index.html) or from CLI \(s.a.\).

## Building the Image
Building is integrated into maven via the [fabric8 plugin](https://dmp.fabric8.io): 
To build a docker image from a local clone of exist:
```bash
mvn -Pdocker -DskipTests -Ddependency-check.skip=true clean package
```

`-P` activates the docker profile. The maven plugin provides for a number of usefull commands to work with containers, e.g.:

```bash
cd exist-docker
mvn docker:push
```

For a full list see the plugin documentation. 

### Testing
There are unit tests for our images that run on CI using the [bats](https://github.com/bats-core/bats-core) framework. The test are located in `exist-docker/src/test/bats`.

To execute them run:
```bash
bats exist-docker/src/test/bats/*.bats 
```
The tests use fixtures and are creating a modified image call `ex-mod`. By default they expect a name container `exist-ci` to be up and running. When running test locally you must ensure that no previous image `ex-mod` exists, and that `exist-ci` is running before starting the testsuite. 

### Available Arguments and Defaults
eXist-db's cache size and maximum brokers can be configured at build time using the following syntax.
```bash
mvn -DskipTests clean package docker:build --build-arg MAX_CACHE=312 MAX_BROKER=15 .
```

NOTE: Due to the fact that the final images does not provide a shell, setting ENV variables via docker does not work.
```bash
# !This has no effect!
docker run -dit -p8080:8080 -e MAX_BROKER=10 ae4d6d653d30
```

If you wish to permanently adopt a customized cache or broker configuration, 
you can either make a local copy of the `Dockerfile` and edit the default values there.

```bash
ARG MAX_BROKER=10
```

Or modify eXist-db's configuration files via xslt scripts located at `exist-docker/src/main/xslt/`.
For multi-stage builds e.g. [xmlstarlet](http://xmlstar.sourceforge.net) let's you modify the default config files from within the builder stage, 
e.g.:
```docker
# Config files are modified here
RUN echo 'modifying conf files'\
&& cd $EXIST_HOME/etc \
&& xmlstarlet ed  -L -s '/Configuration/Loggers/Root' -t elem -n 'AppenderRefTMP' -v '' \
 -i //AppenderRefTMP -t attr -n 'ref' -v 'STDOUT'\
 -r //AppenderRefTMP -v AppenderRef \
 log4j2.xml
```


#### JVM configuration
This image uses an advanced JVM configuration, via the  `JAVA_TOOL_OPTIONS` env variable inside the Dockerfile. 
You should avoid the traditional way of setting the heap size via `-Xmx` arguments, 
this can lead to frequent crashes since Java8 and Docker are (literally) not on the same page concerning available memory.

Instead, use the `-XX:MaxRAMFraction=1` argument to modify the memory available to the JVM *inside* the container. 
For production use we recommend to increase the value to `2` or even `4`. 
This value expresses a ratio, so setting it to `2` means half the container's memory will be available to the JVM, '4' means ¼,  etc.

To allocate e.g. 600mb to the container *around* the JVM use:
```bash
docker run -m 600m …
```

Lastly, this image uses a new garbage collection mechanism 
[The Z Garbage Collector](https://docs.oracle.com/en/java/javase/11/gctuning/z-garbage-collector1.html) `-XX:+UseZGC`
and [string deduplication](http://openjdk.java.net/jeps/192) `-XX:+UseStringDeduplication` to improve performance.

To disable or further tweak these features edit the relevant parts of the `Dockerfile`, or when running the image. 
As always when using the latest and greatest, YMMV. 
Feedback about real world experiences with these features in connection with eXist-db is very much welcome.
