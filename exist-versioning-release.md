# eXist Versioning Scheme and Release Process

![eXist Logo](https://github.com/eXist-db/exist/raw/develop/exist-jetty-config/src/main/resources/webapp/logo.jpg)

## Overview
This document describes the Versioning Scheme and Release Process for eXist. These two topics are tightly connected, so both are covered in this document.

*   The Versioning Scheme describes how eXist's source code and releases are named. Version numbers unambiguously inform users and developers about the significance of the release and order relative to past and future versions.

*   The Release Process describes how the Release Manager (the person who orchestrates a release) should take a `snapshot (tag)` of eXist source code, apply the Versioning Scheme, assemble it, and publish the resulting products. The goal is to have a clear procedure for altering the version number to mark transitions in phases of development leading up to each release, and to ensure that releases are consistently sourced from a specific point in the project repository's history.

The final section of the document describes the old versioning practice, compared to the new one.

### Motivation

This proposal is intended to facilitate more rapid releases, with the goal of getting new features and bug fixes out to the community without sacrificing quality or stability. Critical to the success of this effort is achieving a common understanding about version numbers and managing version changes during releases.

Problems with versioning crept in during the long development phase of eXist 3.0, when eXist's version-related properties (as recorded in the `$EXIST_HOME/build.properties` file) diverged, and as a result, the community struggled with having two ways of talking about eXist's version, e.g., the public label, `3.0.RC2`, and the numeric version, `3.0.3`.

This proposal was first implemented with eXist 3.1.0, which inaugurated the use of a single version number for all versions of eXist. The document has been refined and expanded since then.

The new versioning scheme uses the popular Semantic Versioning scheme, in which each number here reflects major, minor, and patch versions. This single version-related property will bring clarity and semantic precision to eXist's releases. The Semantic Versioning scheme allows the eXist team to label development versions as snapshots or release candidates, and to release these and nightly builds with clear version numbers.

Paired with a revised set of release procedures, the new versioning scheme ensures the names of new versions of eXist delivered to the community are precise and reliable. Removing versioning ambiguities and clarifying release practices facilitates a rapid cycle of development and release.

## Versioning Scheme

eXist follows a widely-used, semantically precise versioning scheme called [Semantic Versioning](http://semver.org/) (specifically [version 2.0.0](https://github.com/mojombo/semver/tree/v2.0.0)) of this scheme. For a complete introduction to Semantic Versioning, please consult the documentation. Here, we summarize how the principles of Semantic Versioning are applied to eXist.

### Product Releases

For product releases (also called stable or final releases), a 3-component Semantic Versioning version number is used: "`MAJOR`**.**`MINOR`**.**`PATCH`". When a new version is released, its version number is incremented according to the following criteria:

1. `MAJOR` versions contain incompatible API changes, including changes to the on-disk format of the database;
2. `MINOR` versions add functionality or deprecate API functions, without breaking backward compatibility; and
3. `PATCH` versions contain only backwards-compatible bug fixes.

(Any public or protected methods at public or protected classes are eXist-db API)

For example, the 3rd major version of eXist would have the Semantic Version number `3.0.0`. A new release following this including new features would be version `3.1.0`. A bugfix-only release following that would be version `3.1.1`.

**NOTE:** For the purposes of this present document, we do not define the scope of an API for eXist. This may be defined in a future standalone document.

### Pre-Releases

For pre-releases, such as [release candidates](https://en.wikipedia.org/wiki/Software_release_life_cycle#Release_candidate) or [snapshots](https://docs.oracle.com/middleware/1212/core/MAVEN/maven_version.htm#MAVEN401), a 4-component Semantic Versioning version number is used: "`MAJOR`**.**`MINOR`**.**`PATCH`**-**`PRERELEASE`. We follow Semantic Versioning's definitions for the `PRERELEASE` label scheme:

*   `PRERELEASE` is a series of dot separated identifiers, each identifier must use only the following ASCII characters `[0-9A-Za-z-]` and must not be empty.

*   The presence of `PRERELEASE` indicates that the version is pre-release and not yet considered stable. Product releases do not have `PRERELEASE`.

*   Given two versions in which `MAJOR`, `MINOR`, and `PATCH` are equal, the version with a `PRERELEASE` has lower precedence than one without it. The following rules hold true in terms of version number preference:

    *   `3.0.0` > `3.0.0-RC2`
    *   `3.0.0-RC2` > `3.0.0-RC1`
    *   `3.0.0-RC1` > `2.2.4`

eXist uses only two clearly defined forms of `PRERELEASE` label:

*   `RCx` is used for release candidates. The `x` should be replaced with the iteration of the release candidate, for example `3.0.0-RC1` for the first release candidate of eXist 3, and `3.0.0-RC2` for the second release candidate of eXist 3. While not all releases are necessarily preceded by a release candidate (which are feature complete and considered ready for release), the core developers may opt to issue one or more release candidates in order to gather feedback from testing by early adopters.

*   `SNAPSHOT` is used for point-in-time builds. These products are typically not published or distributed, but used only for local testing by developers or by the nightly-build system.

### Nightly Builds

A nightly build is similar to a snapshot, except it is automatically built from the latest source code and released once daily. To help distinguish between one day's nightly build and the next's, a 5-component Semantic Versioning version number is used for nightly builds' filenames: "`MAJOR`**.**`MINOR`**.**`PATCH`**-**`PRERELEASE`**+**`BUILD`. We follow Semantic Versioning's definitions for the `BUILD` label scheme:

*   `BUILD` is a series of dot separated identifiers, each identifier must use only ASCII alphanumerics and hyphen [0-9A-Za-z-]
 and must be empty. Build metadata SHOULD be ignored when determining version precedence.

*   The presence of `BUILD` indicates that the version is pre-release and not yet considered stable. Product releases do not have `BUILD`.

eXist adds a further constraint and modifies the precedence for the `BUILD` label:

*   The `BUILD` label is a UTC timezone timestamp, in the format `YYYYMMDDHHmmSS` (as would be given by the UNIX command `date +%Y%m%d%H%M%S`).

*   The precedence of the `BUILD` label, may be numerically compared by timestamp, e.g. `20170227142409 > 20160204000001`.

For example, the macOS disk image for the build from the SNAPSHOT pre-release version of eXist 3.2.0 on May 7, 2017 at 21:37:22 UTC would be named:

    * eXist-db-3.2.0-SNAPSHOT+20170507213722.dmg

It is trivial for a developer to relate a timestamp back to a Git hash (by using the command `git rev-list -1 --before="$DATE" develop`), should they need to do so.

### Where the version number is stored

The version number is stored in `exist-parent/pom.xml` as the `<revision>` CI-friendly property:
```xml
<properties>
    <revision>7.0.0-SNAPSHOT</revision>
</properties>
```

All module `<parent>` blocks reference `<version>${revision}</version>`. The `flatten-maven-plugin` resolves `${revision}` to a literal version in every installed or deployed POM so that downstream consumers see a concrete version, not the placeholder.

That version number is also copied into the `META-INF/MANIFEST.MF` file of any Jar packages that are built, using the standard manifest attributes: `Specification-Version` and `Implementation-Version`.

## Release Process

This section details concrete steps for creating and publishing product releases. Releases are fully automated via two GitHub Actions workflows — no local Maven invocation is required for normal releases.

### Overview: two-workflow release pattern

| Workflow | Trigger | What it does |
|----------|---------|--------------|
| `ci-release-prepare.yml` | `workflow_dispatch` | Updates `CITATION.cff`, commits, creates annotated tag `eXist-X.Y.Z`, pushes |
| `ci-release.yml` | `push.tags: eXist-*` | Parallel platform builds → GitHub Release |

The `ci-release.yml` workflow runs four jobs in parallel then converges:

| Job | Runner | Output |
|-----|--------|--------|
| `build-linux` | ubuntu-latest | zip + tar.bz2 archives + installer JAR; deploy all to Maven Central (GPG-signed) |
| `build-mac` | macos-latest | signed + notarized DMG (installer excluded) |
| `build-windows` | windows-latest | jarsigner + Authenticode signed installer JAR + `.exe` for GitHub Release |
| `publish-github-release` | ubuntu-latest | GitHub Release with all assets attached |

### Prerequisites: required GitHub secrets and repository rules

All secrets must be in place under **Settings → Secrets and variables → Actions** before running a release. See `internal-release-runbook.template.md` for the full table and rotation guidance.

A **tag ruleset** restricts creation and deletion of `eXist-*` tags to users with the Maintain or Admin role (Settings → Rules → Rulesets). The `RELEASE_PAT` owner must hold one of those roles; all other automation only reads tags and is unaffected.

| Secret | Used by |
|--------|---------|
| `RELEASE_PAT` | `ci-release-prepare` — PAT with `contents:write`; required because `GITHUB_TOKEN` pushes do not trigger downstream tag workflows |
| `EXISTDB_RELEASE_KEY` | `build-linux` — GPG private key (armored, base64-encoded); imported via `gpg --import` |
| `EXISTDB_RELEASE_KEY_ID` | `build-linux` — GPG key ID passed to `-Dexistdb.release.key=` (e.g. `ABC1234`) |
| `EXISTDB_RELEASE_KEY_PASSPHRASE` | `build-linux` — GPG passphrase |
| `CENTRAL_TOKEN_USERNAME` | `build-linux` — Sonatype Central Portal user token username |
| `CENTRAL_TOKEN_PASSWORD` | `build-linux` — Sonatype Central Portal user token password |
| `EXISTDB_MAC_CERTIFICATE` | `build-mac` — Developer ID Application cert (base64 PKCS12) for codesigning |
| `EXISTDB_MAC_CERTIFICATE_PASSWORD` | `build-mac` — Certificate password |
| `EXISTDB_APPLE_API_KEY` | `build-mac` — App Store Connect API private key (base64-encoded `.p8`) for notarytool |
| `EXISTDB_APPLE_API_KEY_ID` | `build-mac` — API Key ID (10-char alphanumeric) |
| `EXISTDB_APPLE_API_ISSUER_ID` | `build-mac` — App Store Connect Issuer ID (UUID) |
| `AZURE_CLIENT_ID` | `build-windows` — OIDC app registration client ID |
| `AZURE_TENANT_ID` | `build-windows` — Azure tenant |
| `AZURE_SUBSCRIPTION_ID` | `build-windows` — Azure subscription |

Repository variables (not secrets): `AZURE_KEYVAULT_URI` (e.g. `https://exist-db-signing.vault.azure.net/`), `AZURE_KEYVAULT_CERT_NAME` (the certificate name as it appears in Key Vault, e.g. `existdb-code-signing`), and `EXISTDB_MAC_CODESIGN_IDENTITY`.

### Preparing a Product Release

For purposes of illustration, we will assume we are preparing the stable release of version 7.0.0.

1.  Merge any outstanding PRs that have been reviewed and accepted for the milestone eXist-7.0.0.

2.  Confirm that CI is green on `develop` (or your release branch).

3.  Go to **Actions → Prepare Release** and click **Run workflow**. Enter the version number (e.g. `7.0.0`). Click **Run workflow**.

    The workflow will:
    - Run `mvn -Pcitation-release-metadata -DupdateCff=true -Drevision=7.0.0 validate` to update `CITATION.cff`.
    - Commit the change if `CITATION.cff` was modified (`[release] Prepare eXist-7.0.0`).
    - Create an annotated tag `eXist-7.0.0`.
    - Push the commit and the tag.

4.  The tag push automatically triggers **Actions → Release**. Monitor the four parallel jobs. Total runtime is approximately 45–90 minutes (macOS notarization and Windows Authenticode signing dominate).

5.  When all four jobs pass, the **Publish GitHub Release** job creates the GitHub Release and attaches all artifacts automatically.

#### Publishing/Promoting the Product Release

1.  Check that the new versions are visible on [Github](https://github.com/eXist-db/exist/releases).

2.  Check that the new versions are visible on [DockerHub](https://hub.docker.com/r/existdb/existdb).

3.  Maven Central: the `build-linux` job deploys via `central-publishing-maven-plugin` with `autoPublish=true`. Artifacts are queued for sync immediately after upload. Check [central.sonatype.com](https://central.sonatype.com) for the `org.exist-db` namespace to confirm propagation (typically 15–30 min after job completion).

4.  Update the Mac HomeBrew for eXist-db, see: [Releasing to Homebrew](https://github.com/eXist-db/exist/blob/develop/exist-versioning-release.md#releasing-to-homebrew).

5.  Edit the links for the downloads on the eXist website.
    1. `$ git clone https://github.com/exist-db/website.git`
    2.  Edit the file `website/index.html`, you need to modify the HTML under `<a name="downloads"/>` and update the version numbers for the current release:

   ```html
   <a name="downloads"/>
   <div class="row">
     <div class="col-md-12">
         <h2 id="download">Download</h2>
         <a href="https://github.com/eXist-db/exist/releases/tag/eXist-5.3.0">
             <button class="btn btn-default download-btn stable" type="button">
                 <span class="status">Latest Release</span>
                 <span class="icon">
                     <i class="fa fa-download"/>
                 </span>
                 <span class="exist-version">Version 5.3.0</span>
             </button>
         </a>
         <a href="https://hub.docker.com/r/evolvedbinary/exist-db/tags/">
             <button class="btn btn-default download-btn docker-images" type="button">
                 <span class="status">Docker Images</span>
                 <span class="icon">
                     <i class="fa fa-ship"/>
                 </span>
                 <span class="exist-version">Version 5.3.0</span>
             </button>
         </a>
         <a href="https://github.com/exist-db/mvn-repo">
             <button class="btn btn-default download-btn maven" type="button">
                 <span class="status">Maven Artifacts</span>
                 <span class="icon">
                     <i class="fa fa-github"/>
                 </span>
                 <span class="exist-version">Version 5.3.0</span>
             </button>
         </a>
   ```

    3. Edit the file `expath-pkg.xml` and bump the version i.e. `version="4"` to reflect the new version.

    4. Commit your change and push: `$ git commit index.html expath-pkg.xml -m "Update for eXist-5.3.0 website" && git push origin master`

    5. Tag your release of the Website and push the tag: `$ git tag -s -m "Release tag for eXist 5.3.0 website" eXist-5.3.0 && git push origin eXist-5.3.0`.

    6. Create a XAR for the website: `$ git checkout eXist-5.3.0 && ant`.

    7. Visit http://www.exist-db.org/exist/apps/dashboard/index.html, login and upload the new `build/homepage.xar` file via the Package Manager.

6.  Login to the blog at [http://exist-db.org/exist/apps/wiki/blogs/eXist/](http://exist-db.org/exist/apps/wiki/blogs/eXist/) and add a new news item which announces the release and holds the release notes. It should be named like [http://exist-db.org/exist/apps/wiki/blogs/eXist/eXistdb500](http://exist-db.org/exist/apps/wiki/blogs/eXist/eXistdb500)

    6.1. Warning: there is a know issue in Atomic-Wiki where your release notes might suddenly disappear. In case this happens your data is not lost but stored in  /db/apps/wiki/data/blogs/eXist/.md. You can rename it or move the content to a eXistdb<VERSION>.md file and create an according eXistdb<VERSION>.atom for it. Once these two files are available the blog entry will become visible on the eXist-db homepage and it will be visible in the eXist-db blog. 

7.  Visit the GitHub releases page [https://github.com/eXist-db/exist/releases](https://github.com/eXist-db/exist/releases) and create a new release, enter the tag you previously created and link the release notes from the blog.

8.  Send an email to the `exist-open` mailing list announcing the release with a title similar to `[ANN] Release of eXist 5.3.0`, copy and paste the release notes from the blog into the email and reformat appropriately (see past emails).

9.  Tweet about it using the `existdb` twitter account.

10. Post it to the LinkedIn eXist-db group: [https://www.linkedin.com/groups/35624](https://www.linkedin.com/groups/35624)

11. Submit a news item to XML.com - [https://www.xml.com/news/submit-news-item/](https://www.xml.com/news/submit-news-item/).

12. Update the Wikipedia page with the new version details - [https://en.wikipedia.org/wiki/EXist](https://en.wikipedia.org/wiki/EXist).

13. Go to GitHub and move all issues and PRs which are still open for the release milestone to the next release milestone. Close the release milestone.


### Manual fallback (CI unavailable)

If the CI release workflow is unavailable, the `exist-release` Maven profile (backed by `maven-release-plugin`) remains usable for emergency releases. This path requires a local macOS machine with valid Apple credentials, GnuPG, and all signing keys available in `~/.m2/settings.xml`. GitHub Release assets are uploaded via `gh release create` / `gh release upload` (the `exec-maven-plugin` execution in the `exist-release` profile creates the GitHub release from the tag if CI has not already done so, then falls back to uploading into an existing one). The installer JAR is signed locally via the `codesign-izpack-jar` profile (`-Dizpack-signing=true`) using a local keystore — the required `~/.m2/settings.xml` properties are `existdb.release.keystore`, `existdb.release.keystore.pass`, `existdb.release.keystore.key.alias`, and `existdb.release.keystore.key.pass`.

Tag naming must still follow the `eXist-X.Y.Z` convention. After a manual tag push, re-run **Actions → Release** via `workflow_dispatch` with the tag name to produce platform artifacts that were skipped locally.

### Releasing to Homebrew
[Homebrew](http://brew.sh) is a popular command-line package manager for macOS. Once Homebrew is installed, applications like eXist can be installed via a simple command. eXist's presence on Homebrew is found in the Caskroom project, as a "cask", at [https://github.com/caskroom/homebrew-cask/blob/master/Casks/exist-db.rb](https://github.com/caskroom/homebrew-cask/blob/master/Casks/exist-db.rb).

**Terminology:** "Homebrew Cask" is the segment of Homebrew where pre-built binaries and GUI applications go, whereas the original "Homebrew" project is reserved for command-line utilities that can be built from source. Because the macOS version of eXist-db is released as an app bundle with GUI components, it is handled as a Homebrew Cask.

When there is a new release of eXist, registering the new release with Homebrew can be easily accomplished using Homebrew's `brew bump-cask-pr` command. Full directions for this utility as well as procedures for more complex PRs can be found on [the Homebrew Cask CONTRIBUTING page](https://github.com/Homebrew/homebrew-cask/blob/master/CONTRIBUTING.md), but, a simple version bump is a one-line command. For example, to update Homebrew's version of eXist-db to 5.3.0, use this command:

```
brew bump-cask-pr --version 5.3.0 exist-db
```

This command will cause your local Homebrew installation to download the new version of eXist-db, calculate the installer's new SHA-256 fingerprint value, and construct a pull request under your GitHub account, like [this one](https://github.com/Homebrew/homebrew-cask/pull/107778). Once the pull request is submitted, continuous integration tests will run, and a member of the Homebrew community will review the PR. At times there is a backlog on the CI servers, but once tests pass, the community review is typically completed in a matter of hours.

## Comparison to the Old Versioning and Release Procedures

### The Old Way
During the development of eXist 3.0, the version-related properties in `$EXIST_HOME/build.proprties` diverged and looked like this:

```
project.version = 3.0.RC2
project.version.numeric = 3.0.3
```

Here there are two different version numbers above: `project.version` and `project.version.numeric`. The second version number was introduced in an attempt to assist many users who were running custom-compiled versions and needing to detect API changes during the very extended release candidate phase. The divergence in version numbers caused real confusion and consternation among users who tried to communicate these version numbers with each other.

When eXist 3.0 was released, these properties were manually modified for sake of expediency and therefore did not match the Git tag `eXist-3.0`. Rather, its `$EXIST_HOME/build.properties` contained the following version components:

```
project.version = 3.0.0
project.version.numeric = 3.0.4
```

Our goal is to prevent such a divergence in versioning from creeping back into eXist and to ensure a clean versioning system to serve eXist through its future development and release cycles.

### The New Way
eXist now has a single version number, formulated according to the precise principles of Semantic Versioning, captured in a single property:

```
project.version = 3.0.0
```

Once a stable release has been tagged, we will immediately initiate the next version, assuming a `MINOR` release, unless the core developers select a `PATCH` or `MAJOR` version; this next version will have a `LABEL` appended, e.g., `3.1.0-SNAPSHOT`, which will persist until 3.1.0 is released, unless a new `PATCH` or `MAJOR` version must be released first.

We can call the installer (and/or packages) anything we want, but it would be sensible for them to reflect the version number clearly. So for simplicity we suggest just using the same version as is in project.version, i.e.:

```
eXist-db-setup-3.1.0.exe
eXist-db-setup-3.1.0.jar
eXist-db-3.1.0.dmg
```

Similarly the Maven artifacts that are (currently) manually produced (for https://github.com/exist-db/exist.git)  would be named like:

```
exist-core-3.1.0.jar
exist-core-3.1.0.pom
```

For a future potential RC, we suggest:

```
eXist-db-setup-4.0.0-RC1.exe
eXist-db-setup-4.0.0-RC1.jar
eXist-db-4.0.0-RC1.dmg

exist-core-4.0.0-RC1.jar
exist-core-4.0.0-RC1.pom
```

Having the git commit hash in any final release filenames is redundant. It only really made sense when we didn't have frequent releases.

Either a git commit ID or a timestamp should be appended for nightly builds. We propose using the Semantic Versioning mechanism for the optional 5th component. A git commit ID would appear as follows:

A timestamp, which would make future integration with Maven compliant systems much easier, would appear as follows:

```
eXist-db-setup-3.2.0-SNAPSHOT+20170507213722.exe
eXist-db-setup-3.2.0-SNAPSHOT+20170507213722.jar
eXist-db-3.2.0-SNAPSHOT+20170507213722.dmg
```

It is trivial for a developer to relate a timestamp back to a Git commit (by using the command `git rev-list -1 --before="$DATE" develop`), should they need to do so. Another benefit of the latter is that users can more readily identify sequence from the human-readable timestamps than git commit IDs.


## Future Considerations

### Release Candidates
1.  While a release candidate is being tested, only bugfix patch PRs for that RC can be merged. We could consider a slightly more complex branch and release process to enable the `develop` branch to continue unrestricted.

### Maven Compatibility
The use of the `BUILD` label may have to be refined if we migrate to Maven. Maven Snapshots have two forms:

1.  A base version which is not actualised, e.g.: `3.1.0-SNAPSHOT`. This fits with our current proposals.

2.  A published SNAPSHOT release which looks: ``3.1.0-20170507.213722-1`. This is not incompatible with Semver, but would replace the `PRERELEASE` label `SNAPSHOT` with a concrete `PRERELEASE` timestamp.
