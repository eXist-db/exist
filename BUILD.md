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
- `-DskipTests` : skips running all tests
- `-DskipUnitTests=true` : run only integration tests
- `-Ddependency-check.skip=true` : skips validating dependencies

### Maven repositories

Maven resolves dependencies from these repositories (defined in `exist-parent/pom.xml`):

- **Releases:** Maven Central (direct) → exist-db proxy → exist-db → evolved-binary (all public)
- **Snapshots:** GitHub Packages (exist, exist-xqts-runner) → exist-db-snapshots → evolved-binary-snapshots

### GitHub Packages (authentication for SNAPSHOT builds)

When building from `develop` (or any SNAPSHOT version), Maven resolves `exist-xqts-runner` from `https://maven.pkg.github.com/eXist-db/exist-xqts-runner`. GitHub Packages requires authentication; without it you get **401 Unauthorized**.

**Option 1 – Exclude XQTS** (no auth needed): use `mvn -DskipTests package -pl '!exist-xqts'` to skip the XQTS module.

**Option 2 – Configure GitHub auth** (if you need XQTS or a full build): add a GitHub PAT to `~/.m2/settings.xml` as server `github-xqts-runner` (and optionally `github` for eXist snapshots). See `.github/actions/maven-github-settings/action.yml` for the expected `<server>` format.

Further build options can be found at: [eXist-db Build Documentation](http://www.exist-db.org/exist/apps/doc/exist-building.xml "How to build eXist") and on the workflow files of this repo.

### Running tests locally

From the repo root:

- **All tests:** `mvn -V -B verify -Ddependency-check.skip -Dlicense.skip`
- **exist-core only:** add `--projects exist-core --also-make` to the above
- **Single test class:** `mvn -Dtest=fully.qualified.TestClass test --projects exist-core --also-make`

**NOTE:** 
In the above example, we switched the current (checked-out) branch from `develop` to `master`. We use the [GitFlow for eXist-db](#contributing-to-exist) process:
- `develop` is the current (and stable) work-in-progress (the next release)
- `master` is the latest release
The choice of which to use is up to you.


