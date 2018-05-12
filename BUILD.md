Building eXist from Source
--------------------------

eXist itself is written in Java 8. The build system is [Apache Ant](http://ant.apache.org/ "The Apache Ant Project"), which is provided with the source code. If you're not familiar with Git, we recommend [this excellent online interactive tutorial](http://try.github.io).

To build eXist:

- Checkout the Git Repository
- Execute a build script to compile eXist

```bash
$ git clone git@github.com:eXist-db/exist.git
$ cd exist
$ git checkout master
$ ./build.sh
```

**NOTE:** 
In the above example, we switched the current (checked-out) branch from `develop` to `master`. We use the [GitFlow for eXist](#contributing-to-exist) process:
- `develop` is the current (and stable) work-in-progress (the next release)
- `master` is the latest release
The choice of which to use is up to you.

**HINT:** 
In the example above, we use the SSH form of the GitHub repo URL to clone eXist. However, if you're behind a HTTP proxy and your organisation doesn't allow outgoing SSH connections, try the HTTPS URL for our GitHub repo <https://github.com/eXist-db/exist.git>.

From here, you now have a compiled version of eXist that you may use just as you would an installed version of eXist, however it may be desirable to package this up for easy installation elsewhere. If you wish to create a simple ZIP distribution of eXist, run:

```bash
$ ./build.sh dist-zip
```

To build a full Installer for eXist, you'll need to have IzPack installed. Set your path to IzPack in `exist/build.properties` and run:

```bash
$ ./build.sh installer
```

Otherwise, you may wish to deploy eXist into a Web Application Server as a WAR file. We provide a build for that, too:

```bash
$ ./build.sh dist-war
```

For more build options, see the [eXist Build Documentation](http://www.exist-db.org/exist/apps/doc/exist-building.xml "How to build eXist").
