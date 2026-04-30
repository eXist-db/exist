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
$ ./mvnw -DskipTests package
```

From here, you now have a compiled version of eXist-db in the `exist-distribution/target` folder that you may use just as you would an installed version of eXist-db. An installer is also build and present in `exist-installer/target` for easy installation elsewhere.

Useful build switches:
- `-Ddocker=true` : builds the docker image
- `-DskipTests` : skips running all tests
- `-DskipUnitTests=true` : run only integration tests
- `-Ddependency-check.skip=true` : skips validating dependencies

### Maven version

This repo uses **Maven Wrapper** (`./mvnw`) so you can build with the project’s pinned Maven version without installing Maven via a system package manager.
Maven 4 requires **Java 17+** to run; eXist-db builds with **Java 21**.

### Maven repositories

Maven resolves dependencies from these repositories (defined in `exist-parent/pom.xml`):

- **Releases:** Maven Central (direct) → exist-db proxy → exist-db → evolved-binary (all public)
- **Snapshots:** GitHub Packages (exist, exist-xqts-runner) → exist-db-snapshots → evolved-binary-snapshots

### GitHub Packages (authentication for SNAPSHOT builds)

When building from `develop` (or any SNAPSHOT version), Maven resolves `exist-xqts-runner` from `https://maven.pkg.github.com/eXist-db/exist-xqts-runner`. GitHub Packages requires authentication; without it you get **401 Unauthorized**.

**Option 1 – Exclude XQTS** (no auth needed): use `./mvnw -DskipTests package -pl '!exist-xqts'` to skip the XQTS module.

**Option 2 – Configure GitHub auth** (if you need XQTS or a full build): add a GitHub PAT to `~/.m2/settings.xml` as server `github-xqts-runner` (and optionally `github` for eXist snapshots). See `.github/actions/maven-github-settings/action.yml` for the expected `<server>` format.

Further build options can be found at: [eXist-db Build Documentation](http://www.exist-db.org/exist/apps/doc/exist-building.xml "How to build eXist") and on the workflow files of this repo.

### Running tests locally

From the repo root:

- **All tests:** `./mvnw -V -B verify -Ddependency-check.skip -Dlicense.skip`
- **exist-core only:** add `--projects exist-core --also-make` to the above
- **Single test class:** `./mvnw -Dtest=fully.qualified.TestClass test --projects exist-core --also-make`

### Maven 4 rerun/resume guidance

Use Maven 4's reactor resume mode (`-r` / `--resume`) for reruns after module failures:

- Initial CI-like run:
  - `./mvnw -V -B --no-transfer-progress -DskipTests -Ddependency-check.skip=true clean verify`
- Retry from the failed module onward:
  - `./mvnw -V -B --no-transfer-progress -DskipTests -Ddependency-check.skip=true -r verify`

This avoids rebuilding already successful modules and keeps reruns deterministic.

For release/snapshot publish jobs that call `deploy`, treat `-r deploy` as a full publish rerun strategy only. Maven 4 deploy behavior is effectively all-or-nothing when using deploy-at-end semantics, so do not assume partial publish recovery from a previous failed deploy.

**NOTE:** 
In the above example, we switched the current (checked-out) branch from `develop` to `master`. We use the [GitFlow for eXist-db](#contributing-to-exist) process:
- `develop` is the current (and stable) work-in-progress (the next release)
- `master` is the latest release
The choice of which to use is up to you.


