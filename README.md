eXist Native XML Database
=========================
[![Build Status](https://travis-ci.org/eXist-db/exist.png?branch=develop)](https://travis-ci.org/eXist-db/exist) [![Java 8](https://img.shields.io/badge/java-8-blue.svg)](http://java.oracle.com) [![License](https://img.shields.io/badge/license-LGPL%202.1-blue.svg)](https://www.gnu.org/licenses/lgpl-2.1.html)
[![Download](https://api.bintray.com/packages/existdb/releases/exist/images/download.svg)](https://bintray.com/existdb/releases/exist/_latestVersion)
[![HipChat](https://img.shields.io/badge/hipchat-eXist–db-blue.svg)](https://www.hipchat.com/gEBQ3SNfp)


This is the GitHub for the [eXist](http://www.exist-db.org "eXist") Native XML Database. 

If you're looking to work with the eXist source code, you've come to the right place. If not, you're probably looking for the [eXist Documentation](http://www.exist-db.org/exist/apps/doc/ "Documentation of eXist"). The latest eXist release is available from http://www.exist-db.org, alternatively if you want the bleeding-edge then [nightly builds](http://static.adamretter.org.uk/exist-nightly/) are also available.

If you're looking for help or discussion, visit the eXist community [mailing lists](http://www.exist-db.org/exist/apps/doc/getting-help.xml "eXist Mailing Lists") or consider purchasing the [eXist book](http://www.jdoqocy.com/click-7654993-11290546?sid=&url=http%3A%2F%2Fshop.oreilly.com%2Fproduct%2F0636920026525.do%3Fcmp%3Daf-webplatform-books-videos-product_cj_auwidget636_0636920026525_%25zp) from O'Reilly.


Information for Developers
--------------------------
If you wish to work on the eXist source code we're now using [Git](http://git-scm.org "Git SCM") (and [GitHub](http://www.github.com)) for our source code management. If you're not familiar with Git, we recommend [this excellent online interactive tutorial](http://try.github.io).


Building eXist from Source
--------------------------
eXist itself is written in Java 8. The build system is [Apache Ant](http://ant.apache.org/ "The Apache Ant Project").

To build eXist:

- Checkout the Git Repository
- Execute Ant to compile eXist

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

For more build options, see the [eXist Build Documentation](http://www.exist-db.org/exist/apps/doc/building.xml "How to build eXist").
