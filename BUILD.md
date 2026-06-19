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

From here, you now have a compiled version of eXist-db in the `exist-distribution/target` folder that you may use just as you would an installed version of eXist-db. An installer is also build and present in `exist-installer/target` for easy installation elsewhere.

Useful build switches:
- `-Ddocker=true` : builds the docker image
- `-DskipTests` : skips running all tests
- `-DskipUnitTests=true` : run only integration tests
- `-Ddependency-check.skip=true` : skips validating dependencies

### Maven repositories

Maven resolves dependencies from these repositories (defined in `exist-parent/pom.xml`):

- **Releases:** Maven Central (direct) → exist-db proxy → exist-db → evolved-binary (all public)
- **Snapshots and a few release artifacts:** GitHub Packages (`exist`, `exist-xqts-runner`, `jackrabbit-webdav-jakarta`) → exist-db-snapshots → evolved-binary-snapshots

### GitHub Packages (authentication required)

When building from `develop` (or any SNAPSHOT version), Maven resolves several artifacts from `maven.pkg.github.com/eXist-db/...`. GitHub Packages requires authentication; without it you get **401 Unauthorized**. Three Maven server IDs are involved (defined in `exist-parent/pom.xml`):

| Server ID | URL | What it provides |
|---|---|---|
| `github` | `https://maven.pkg.github.com/eXist-db/exist` | eXist-db inter-module SNAPSHOTs |
| `github-xqts-runner` | `https://maven.pkg.github.com/eXist-db/exist-xqts-runner` | `exist-xqts-runner` (used by the `exist-xqts` module) |
| `github-jackrabbit-webdav-jakarta` | `https://maven.pkg.github.com/eXist-db/jackrabbit-webdav-jakarta` | `org.exist-db.thirdparty.org.apache.jackrabbit:jackrabbit-webdav:2.22.3-jakarta-ee10` (used by `extensions/webdav`) |

Add all three to `~/.m2/settings.xml`. **The same GitHub PAT (with the `read:packages` scope) can be reused across all three server entries** — Maven matches the `<id>` of a `<server>` to the matching `<repository>` declared in the pom, so each repo needs its own `<server>` block even though the credentials are identical:

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
  <servers>
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_GITHUB_PAT_WITH_READ_PACKAGES</password>
    </server>
    <server>
      <id>github-xqts-runner</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_GITHUB_PAT_WITH_READ_PACKAGES</password>
    </server>
    <server>
      <id>github-jackrabbit-webdav-jakarta</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_GITHUB_PAT_WITH_READ_PACKAGES</password>
    </server>
  </servers>
</settings>
```

CI generates the same shape from secrets via `.github/actions/maven-github-settings/action.yml`.

If a previous resolve failed with 401, Maven may cache the failure as `*.lastUpdated` under `~/.m2/repository/`. After fixing `settings.xml`, delete that artifact directory or add `-U` on the next build.

Example: verify Jackrabbit resolves after configuring auth:

```bash
rm -rf ~/.m2/repository/org/exist-db/thirdparty/org/apache/jackrabbit/jackrabbit-webdav/2.22.3-jakarta-ee10
mvn dependency:get \
  -Dartifact=org.exist-db.thirdparty.org.apache.jackrabbit:jackrabbit-webdav:2.22.3-jakarta-ee10 \
  -U -Ddependency-check.skip=true
```

Further build options can be found at: [eXist-db Build Documentation](http://www.exist-db.org/exist/apps/doc/exist-building.xml "How to build eXist") and on the workflow files of this repo.

### Running tests locally

From the repo root:

- **All tests:** `mvn -V -B verify -Ddependency-check.skip -Dlicense.skip`
- **exist-core only:** add `--projects exist-core --also-make` to the above
- **Single test class:** `mvn -Dtest=fully.qualified.TestClass test --projects exist-core --also-make`
- **WebDAV round-trip tests:** `mvn test -pl extensions/webdav --also-make -Dtest=org.exist.webdav.WebDavRoundTripTest -Dsurefire.failIfNoSpecifiedTests=false` (requires `github-jackrabbit-webdav-jakarta` auth; litmus compliance runs in Docker CI)

**NOTE:** 
In the above example, we switched the current (checked-out) branch from `develop` to `master`. We use the [GitFlow for eXist-db](#contributing-to-exist) process:
- `develop` is the current (and stable) work-in-progress (the next release)
- `master` is the latest release
The choice of which to use is up to you.


