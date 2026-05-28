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
- **GitHub Packages (releases):** `jackrabbit-webdav-jakarta` — transformed Jackrabbit WebDAV JAR used by `exist-webdav` ([PR #6364](https://github.com/eXist-db/exist/pull/6364))
- **Snapshots:** GitHub Packages (`exist`, `exist-xqts-runner`) → exist-db-snapshots → evolved-binary-snapshots

### GitHub Packages (authentication)

Some artifacts are hosted on GitHub Packages under the `eXist-db` org. GitHub requires authentication; without it Maven reports **401 Unauthorized**.

| Server id in `settings.xml` | Repository | Used for |
|-----------------------------|------------|----------|
| `github` | `eXist-db/exist` | eXist SNAPSHOT builds published to GitHub Packages |
| `github-xqts-runner` | `eXist-db/exist-xqts-runner` | `exist-xqts` conformance runner (SNAPSHOT) |
| `github-jackrabbit-webdav-jakarta` | `eXist-db/jackrabbit-webdav-jakarta` | `jackrabbit-webdav` compile dependency in `exist-webdav` |

Maven matches credentials by **repository id**: the `<id>` in each `<repository>` block in `exist-parent/pom.xml` must match a `<server><id>…</id>` in `~/.m2/settings.xml`. A PAT configured only as `github` is **not** applied to the other two repos.

**Option 1 – Skip modules that need GitHub Packages** (no auth needed):

- XQTS: `mvn -DskipTests package -pl '!exist-xqts'`
- WebDAV tests: `mvn -DskipTests package -pl '!exist-webdav'` (or skip tests only: `-Dtest=!* -pl exist-webdav` is not ideal; prefer `-pl '!exist-webdav'` for compile-only)

**Option 2 – Configure GitHub auth** (full build including `exist-webdav` and `exist-xqts`): add a GitHub PAT with **`read:packages`** (and org access to `eXist-db` packages) to `~/.m2/settings.xml`. Use the same token for all three server entries; only the `<id>` differs. See `.github/actions/maven-github-settings/action.yml` for the canonical format:

```xml
<servers>
  <server>
    <id>github</id>
    <username>YOUR_GITHUB_USERNAME</username>
    <password>YOUR_GITHUB_PAT</password>
  </server>
  <server>
    <id>github-xqts-runner</id>
    <username>YOUR_GITHUB_USERNAME</username>
    <password>YOUR_GITHUB_PAT</password>
  </server>
  <server>
    <id>github-jackrabbit-webdav-jakarta</id>
    <username>YOUR_GITHUB_USERNAME</username>
    <password>YOUR_GITHUB_PAT</password>
  </server>
</servers>
```

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


