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

The version number is stored in the `exist-parent/pom.xml` file, in a single property, `<version>`. The Semantic Versioning number `3.2.0-SNAPSHOT` would be stored as follows:
```
<version>3.2.0-SNAPSHOT</version>
```

That version number is also copied into the `META-INF/MANIFEST.MF` file of any Jar packages that are built, using the standard manifest attributes: `Specification-Version` and `Implementation-Version`.

## Release Process

This section details concrete steps for creating and publishing product releases. Each section here assumes you are starting with a clean Git checkout of the `develop` branch from [https://github.com/eXist-db/exist.git](https://github.com/eXist-db/exist.git).

### Initiating Semantic Versioning

Version 3.0.0 was released before Semantic Versioning. The following steps will initiate Semantic Versioning for the remainder of the development phase of the next release, a new minor version to be called version 3.1.0:

1.  Modify `$EXIST_HOME/build.properties` to read:

    ```
    project.version = 3.1.0-SNAPSHOT
    ```

2.  Commit the changes and push to `origin` (or `upstream` if you are on a fork).

### Preparing a Product Release

The preferred release process is now CI-driven and profile-based. The legacy interactive `maven-release-plugin` flow is retained below only as fallback.

#### Preferred: CI-Driven Release (`release-build`)

Once development on a new stable version is complete, prepare a tag from `develop` and let CI perform the release.

1. Merge any outstanding PRs accepted for the target milestone.
2. Ensure `develop` is in a releasable state and all required checks are green.
3. Create and push a release tag (for example, `eXist-7.0.0`) from the intended commit.
4. Trigger the release workflow (tag-triggered or manual dispatch) which should:
   - run release preflight checks in `validate` (credentials, signing keys, push access, etc.)
   - build with `./mvnw -Prelease-build`
   - publish Maven artifacts via Sonatype Central Portal
   - publish Docker images
   - attach release distributions/installer assets to GitHub Releases

For local release-like packaging verification before tagging, use:

```
./mvnw -Prelease-build -DskipTests -Ddependency-check.skip=true clean package
```

#### Credentials and signing material (CI source of truth)

Store publication credentials and signing material in CI secrets, not in developer-local default configuration:

- Sonatype Central Portal user token (`<server id="central">` when generating settings in CI)
- DockerHub credentials
- GPG key material and passphrase
- IzPack signing keystore credentials (if enabled)
- Apple signing/notarization credentials (if enabled)

#### Legacy fallback: interactive `maven-release-plugin` flow

If the CI release path is unavailable, the older process can still be used manually:

```
mvn -Ddocker=true -Dmac-signing=true -P installer -Dizpack-signing=true -Darguments="-Ddocker=true -Dmac-signing=true -P installer -Dizpack-signing=true" release:prepare
mvn -Ddocker=true -Dmac-signing=true -P installer -Dizpack-signing=true -Djarsigner.skip=false -Darguments="-Ddocker=true -Dmac-signing=true -P installer -Dizpack-signing=true -Djarsigner.skip=false" release:perform
```

Use this path only when necessary; prefer the CI-driven process above.

#### Publishing/Promoting the Product Release
1.  Check that the new versions are visible on [Github](https://github.com/eXist-db/exist/releases).

2.  Check that the new versions are visible on [DockerHub](https://hub.docker.com/r/existdb/existdb).

3.  Verify Maven artifacts are published via Sonatype Central Portal and visible in Central search (or complete any required portal-side publish step if auto-publish is disabled).

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
