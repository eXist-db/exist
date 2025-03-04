Building eXist-db from Source
--------------------------

eXist-db itself is written in and qualified with Java 21. The build system is [Apache Maven](http://maven.apache.org/). If you're not familiar with Git, we recommend [this excellent online interactive tutorial](http://try.github.io).

To build eXist-db:

- Checkout the Git Repository
- Execute a Maven to compile eXist-db

```bash
$ git clone https://github.com/eXist-db/exist.git
$ cd exist
$ git checkout master
$ mvn -DskipTests package
```

From here, you now have a compiled version of eXist-db in the `exist-distribution/target` folder that you may use just as you would an installed version of eXist-db. An installer is also build and present uin `exist-installer/target` for easy installation elsewhere.

Useful build switches:
- `-Ddocker=true` : builds the docker image
- `-DskipTests` : skips running tests
- `-Ddependency-check.skip=true` : skips validating dependencies

Further build options can be found at: [eXist-db Build Documentation](http://www.exist-db.org/exist/apps/doc/exist-building.xml "How to build eXist").

**NOTE:** 
In the above example, we switched the current (checked-out) branch from `develop` to `master`. We use the [GitFlow for eXist-db](#contributing-to-exist) process:
- `develop` is the current (and stable) work-in-progress (the next release)
- `master` is the latest release
The choice of which to use is up to you.


