# eXist Versioning Scheme and Release Process

![eXist Logo](https://github.com/eXist-db/exist/raw/develop/webapp/logo.jpg)

## Overview

This document describes a new Versioning Scheme and Release Process for eXist. These two topics are tightly connected, so both are covered in this document. 

* The Versioning Scheme describes how eXist's source code and releases are to be named. The goal is that every new version number unambiguously inform users and developers about its significance and order relative to past and future versions.

* The Release Process describes how the release manager should take a snapshot of eXist source code, apply the Versioning Scheme, assemble it, and publish the resulting products. The goal is to have a clear procedure for altering the version number to mark transitions in phases of development leading up to each release, and to ensure that releases are consistently sourced from a specific point in the project repository's history.

The final section of the document explains the motivation for the recommendation here, for those who would like additional context. Assuming the proposal is adopted, this section could be removed from the final version of this document. 

### TL;DR - The Motivation in Brief

This proposal is part of a larger effort to facilitate more rapid releases, with the goal of getting new features and bug fixes out to the community without sacrificing quality or stability. Critical to the success of this effort is achieving a common understanding about version numbers and managing version changes during releases. 

The immediate problems with versioning crept in during the long development phase of eXist 3.0, when eXist's version-related properties (as recorded in the `$EXIST_HOME/build.properties` file) diverged, and as a result, the community struggled with having two ways of talking about eXist's version, e.g., the public label, `3.0.RC2`, and the numeric version, `3.0.3`. 

If this proposal is adopted, the next version of eXist, version 3.1.0, will inaugurate the use of a single version number for all versions of eXist. 

The new versioning scheme uses the popular Semantic Versioning scheme, in which each number here reflects major, minor, and patch versions. This single version-related property will bring clarity and semantic precision to eXist's releases. The Semantic Versioning scheme allows the eXist team to label development versions as snapshots or release candidates, and to release these and nightly builds with clear version numbers. 

Paired with a revised set of release procedures, the new versioning scheme will ensure the names of new versions of eXist delivered to the community will be precise and reliable. Removing versioning ambiguities and clarifying release practices will be a key step in moving eXist to a more rapid cycle of development and release.

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

* `PRERELEASE` is a series of dot separated identifiers, each identifier must use only the following ASCII characters `[0-9A-Za-z-]` and must not be empty.

* The presence of `PRERELEASE` indicates that the version is pre-release and not yet considered stable. Product releases do not have `PRERELEASE`.

* Given two versions in which `MAJOR`, `MINOR`, and `PATCH` are equal, the version with a `PRERELEASE` has lower precedence than one without it. The following rules hold true in terms of version number preference:

    * `3.0.0` > `3.0.0-RC2`
    * `3.0.0-RC2` > `3.0.0-RC1`
    * `3.0.0-RC1` > `2.2.4`

eXist uses only two clearly defined forms of `PRERELEASE` label:

* `RCx` is used for release candidates. The `x` should be replaced with the iteration of the release candidate, for example `3.0.0-RC1` for the first release candidate of eXist 3, and `3.0.0-RC2` for the second release candidate of eXist 3. While not all releases are necessarily preceded by a release candidate (which are feature complete and considered ready for release), the core developers may opt to issue one or more release candidates in order to gather feedback from testing by early adopters.

* `SNAPSHOT` is used for point-in-time builds. These products are typically not published or distributed, but used only for local testing by developers or by the nightly-build system.

### Nightly Builds

A nightly build is similar to a snapshot, except it is automatically built from the latest source code and released once daily. To help distinguish between one day's nightly build and the next's, a 5-component Semantic Versioning version number is used for nightly builds' filenames: "`MAJOR`**.**`MINOR`**.**`PATCH`**-**`PRERELEASE`**+**`BUILD`. We follow Semantic Versioning's definitions for the `BUILD` label scheme:

* `BUILD` is a series of dot separated identifiers, each identifier must use only ASCII alphanumerics and hyphen [0-9A-Za-z-]
 and must be empty. Build metadata SHOULD be ignored when determining version precedence.

* The presence of `BUILD` indicates that the version is pre-release and not yet considered stable. Product releases do not have `BUILD`.

eXist adds a further constraint and modifies the precedence for the `BUILD` label:

* The `BUILD` label is a UTC timezone timestamp, in the format `YYYYMMDDHHmmSS` (as would be given by the UNIX command `date +%Y%m%d%H%M%S`).

* The precedence of the `BUILD` label, may be numerically compared by timestamp, e.g. `20170227142409 > 20160204000001`.

For example, the macOS disk image for the build from the SNAPSHOT pre-release version of eXist 3.2.0 on May 7, 2017 at 21:37:22 UTC would be named:

    * eXist-db-3.2.0-SNAPSHOT+20170507213722.dmg

It is trivial for a developer to relate a timestamp back to a Git hash (by using the command `git rev-list -1 --before="$DATE" develop`), should they need to do so.

### Where the version number is stored

The version number is stored in the `$EXIST_HOME/build.properties` file, in a single property, `project.version`. The Semantic Versioning number `3.2.0-SNAPSHOT` would be stored as follows:

```
project.version = 3.2.0-SNAPSHOT
```

That version number must also should be copied into the `META-INF/MANIFEST.MF` file of any Jar packages that are built, using the standard manifest attributes: `Specification-Version` and `Implementation-Version`; this copying process should be automated as part of the build system.

## Release Process

This section details concrete steps for creating and publishing product releases. Each section here assumes you are starting with a clean Git checkout of the `develop` branch from https://github.com/eXist-db/exist.git.

### Initiating Semantic Versioning

Version 3.0.0 was released before Semantic Versioning. The following steps will initiate Semantic Versioning for the remainder of the development phase of the next release, a new minor version to be called version 3.1.0: 

1. Modify `$EXIST_HOME/build.properties` to read:

    ```
    project.version = 3.1.0-SNAPSHOT
    ```
    
2. Commit the changes and push to `origin` (or `upstream` if you are on a fork).

### Preparing a Product Release

Once development on a new stable version is complete, the following steps will prepare the version for release. For purposes of illustration, we will assume we are preparing the stable release of version 3.1.0.

1. Merge any outstanding PRs that have been reviewed and accepted for the milestone eXist-3.1.0.

2. Make sure that you have the HEAD of `origin/develop` (or `upstream` if you are on a fork).

3. Modify `$EXIST_HOME/build.properties` to remove any `LABEL` (e.g., `-SNAPSHOT` or `-RCx`) and read as follows:
    ```
    project.version = 3.1.0
    ```
    
    And commit the changes and push to `origin` (or `upstream` if you are on a fork).

4. Git tag **and sign** eXist-3.1.0 from the `HEAD` of `develop` branch and push the tag to `origin` (or `upstream` if you are on a fork):
    ```
    $ git tag -s -m "Release tag for eXist 3.1.0" eXist-3.1.0
    $ git push origin eXist-3.1.0
    ```
5. Update the stable branch (`master`) of eXist-db to reflect the latest release:
    ```
    $ git push origin develop:master
    ```

6. Prepare for development on the next version of eXist by modifying `$EXIST_HOME/build.properties` to increment to the next the snapshot phase of the next minor version:
    ```
    project.version = 3.2.0-SNAPSHOT
    ```

    And commit the changes and push to `origin` (or `upstream` if you are on a fork).

    **NOTE:** We increment to the next `MINOR` version, rather than to the next `PATCH` or `MAJOR` version, for two reasons. First, we assume the next version will likely contain features and not just bug patches, although this does not prevent us from doing a `3.1.1` (a `PATCH` release) release next, should we have only patches. By the same token, the future is uncertain and we recognise that it is easier to release features with non-breaking API changes and patches, although this still does not prevent us from doing a `4.0.0` release next, should we have breaking API changes.

6. Check out the `eXist-3.1.0` tag and create product builds for publication:

    1. If you haven't previously done so, download and install [IzPack 4.3.5](http://download.jboss.org/jbosstools/updates/requirements/izpack/4.3.5/IzPack-install-4.3.5.jar) to `/usr/local/izpack-4.3.5` or somewhere equally sensible.
    
    2. If you haven't previously done so, create the file `$EXIST_HOME/local.build.properties` and set your identity to use for code signing the Jar files and Mac products:
        ```
        keystore.file=/home/my-username/exist-release-build_key.store
        keystore.alias=exist-release-build
        keystore.password=exist-release-build-password
        
        izpack.dir = /usr/local/izpack-4.3.5

        mac.codesign.identity=Developer ID Application: Your Megacorp Here
        ```

    3. If you haven't previously done so, or you are not using your own existing key, you need to create the Java keystore file `/home/my-username/exist-release-build_key.store`:
        ```
        $ keytool -genkeypair --alias exist-release-build -storepass exist-release-build-password -validity 9999 -keystore /home/my-username/exist-release-build_key.store
        ```

    4. Perform the build of the tag:
        ```
        $ git checkout eXist-3.1.0
        $ ./build.sh jnlp-unsign-all all jnlp-sign-exist jnlp-sign-core jnlp-sign-exist-extensions
        $ ./build.sh installer app-signed dist-war
        ```

#### Publishing the Product Release

1. Login to https://bintray.com/existdb/ and create a new "Version", then upload the files `$EXIST_HOME/installer/eXist-db-setup-3.2.0.jar`, `$EXIST_HOME/dist/eXist-db-3.2.0.dmg` and `$EXIST_HOME/dist/exist-3.1.0.war`. Once the files have uploaded, make sure to click "Publish" to publish them to the version. Once published, you need to go to the "Files" section of the version, and click "Actions"->"Show in downloads list" for each file.

2. Update and publish the latest Maven artifacts as described here: https://github.com/exist-db/mvn-repo

3. Ask [Evolved Binary](http://www.evolvedbinary.com) to build and upload new Docker Images for the latest release.

4. Edit the links for the downloads on the eXist website.

    1. `$ git clone https://github.com/exist-db/website.git`
    
    2. Edit the file `website/index.html`, you need to modify the HTML under `<a name="downloads"/>` and update the version numbers for the current release:

   ```html
   <a name="downloads"/>
   <div class="row">
     <div class="col-md-12">
         <h2 id="download">Download</h2>
         <a href="https://bintray.com/existdb/releases/exist/3.1.0/view">
             <button class="btn btn-default download-btn stable" type="button">
                 <span class="status">Latest Release</span>
                 <span class="icon">
                     <i class="fa fa-download"/>
                 </span>
                 <span class="exist-version">Version 3.1.0</span>
             </button>
         </a>
         <a href="https://hub.docker.com/r/evolvedbinary/exist-db/tags/">
             <button class="btn btn-default download-btn docker-images" type="button">
                 <span class="status">Docker Images</span>
                 <span class="icon">
                     <i class="fa fa-ship"/>
                 </span>
                 <span class="exist-version">Version 3.1.0</span>
             </button>
         </a>
         <a href="https://github.com/exist-db/mvn-repo">
             <button class="btn btn-default download-btn maven" type="button">
                 <span class="status">Maven Artifacts</span>
                 <span class="icon">
                     <i class="fa fa-github"/>
                 </span>
                 <span class="exist-version">Version 3.1.0</span>
             </button>
         </a>
   ```
   
    3. Edit the file `expath-pkg.xml` and modify `version="3.1.0"` to reflect the new version.
    
    4. Commit your change and push: `$ git commit index.html expath-pkg.xml -m "Update for eXist-3.1.0 website" && git push origin master`
    
    5. Tag your release of the Website and push the tag: `$ git tag -s -m "Release tag for eXist 3.1.0 website" eXist-3.1.0 && git push origin eXist-3.1.0`.
    
    6. Create a XAR for the website: `$ git checkout eXist-3.1.0 && ant`.
    
    7. Visit http://www.exist-db.org/exist/apps/dashboard/index.html, login and upload the new `build/homepage.xar` file via the Package Manager.

5. Login to the blog at http://exist-db.org/exist/apps/wiki/blogs/eXist/ and add a new news item which announces the release and holds the release notes. It should be named like http://exist-db.org/exist/apps/wiki/blogs/eXist/eXistdb310

6. Visit the GitHub releases page https://github.com/eXist-db/exist/releases and create a new release, enter the tag you previously created and link the release notes from the blog and the binaries from BinTray.

7. Send an email to the `exist-open` mailing list announcing the release with a title similar to `[ANN] Release of eXist 3.1.0`, copy and paste the release notes from the blog into the email and reformat appropriately (see past emails).

8. Tweet about it using the `existdb` twitter account.

9. Submit a news item to XML.com - https://www.xml.com/news/submit-news-item/.

10. Go to GitHub and move all issues and PRs which are still open for the release milestone to the next release milestone. Close the release milestone.


### Preparing a Patch Release

Assuming that eXist 3.1.0 has been released and a new patch version is ready to be released, the steps for creating and publishing eXist 3.1.1 are:

1. Merge any outstanding PRs that have been reviewed (and accepted) for the milestone eXist-3.1.1.

2. Make sure that you have the HEAD of `origin/develop` (or `upstream` if you are on a fork).

3. Modify the `$EXIST_HOME/build.properties` to look like:
    ```
    project.version = 3.1.1
    ```
    Commit the changes and push to `origin` (or `upstream` if you are on a fork).

4. Git tag **and sign** eXist-3.1.1 from the `HEAD` of `develop` branch and push the tag to `origin` (or `upstream` if you are on a fork):
    ```
    $ git tag -s -m "Release tag for eXist 3.1.1" eXist-3.1.1
    $ git push origin eXist-3.1.1
    ```

5. Prepare for development on the next version of eXist; Modify `$EXIST_HOME/build.properties` to look like:
    ```
    project.version = 3.2.0-SNAPSHOT
    ```
    
    Commit the changes and push to `origin` (or `upstream` if you are on a fork).

6. Checkout the `eXist-3.1.1` tag and create product builds for publication:
    ```
    $ git checkout eXist-3.1.1
    $ ./build.sh jnlp-unsign-all all jnlp-sign-exist jnlp-sign-core
    $ ./build.sh installer installer-exe app dist-war
    ```

### Preparing a Release Candidate

Assuming that work on eXist 3.2.0 is complete and the changes from the previous release (3.1.x) are substantial (therefore introducing a stability concern), and merit a release candidate phase, the steps for creating and publishing eXist 3.2.0-RC1 are:

1. Merge any outstanding PRs that have been reviewed (and accepted) for the milestone eXist-3.2.0.

2. Make sure that you have the HEAD of `origin/develop` (or `upstream` if you are on a fork).

3. Modify the `$EXIST_HOME/build.properties` to look like:
    ```
    project.version = 3.2.0-RC1
    ```
    Commit the changes and push to `origin` (or `upstream` if you are on a fork).

4. Git tag **and sign** eXist-3.2.0-RC1 from the `HEAD` of `develop` branch and push the tag to `origin` (or `upstream` if you are on a fork):
    ```
    $ git tag -s -m "Release tag for eXist 3.2.0-RC1" eXist-3.2.0-RC1
    $ git push origin eXist-3.2.0-RC1
    ```
    
5. Prepare for development on the next version of eXist; Modify `$EXIST_HOME/build.properties` to look like:
    ```
    project.version = 3.2.0-RC2
    ```
    
    Commit the changes and push to `origin` (or `upstream` if you are on a fork).

    **NOTE:** We advance to the next release candidate, rather than restoring the `SNAPSHOT` label, because we assume there may be a need for a subsequent release candidate before release, although this does not prevent us from skipping straight to the final release, should the release candidate prove as stable as hoped.

    **NOTE:** The timeframe between a RC and final release (or next RC) must be short, and therefore is **limited to two weeks**. During this time **ONLY patch bugfix** PRs should be merged.

6. Checkout the `eXist-3.2.0-RC1` tag and create product builds for publication:
    ```
    $ git checkout eXist-3.2.0-RC1
    $ ./build.sh jnlp-unsign-all all jnlp-sign-exist jnlp-sign-core
    $ ./build.sh installer installer-exe app dist-war
    ```

## Motivation

The goal of this proposal is to ensure the theory and practice for each new version of eXist is solid is well understood by core developers and the eXist community. Versioning practices adopted during the development of eXist 3.0 and some well-intentioned mistakes in its public release led the authors to propose a new scheme which could avoid these problems and establish a firmer foundation in versioning and release practices.

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

Once this proposal is adopted, eXist will have a single version number, forumlated according to the precise principles of Semantic Versioning, captured in a single property:

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

1. While a release candidate is being tested, only bugfix patch PRs for that RC can be merged. We could consider a slightly more complex branch and release process to enable the `develop` branch to continue unrestricted.

### Maven Compatibility

The use of the `BUILD` label may have to be refined if we migrate to Maven. Maven Snapshots have two forms:

1. A base version which is not actualised, e.g.: `3.1.0-SNAPSHOT`. This fits with our current proposals.

2. A published SNAPSHOT release which looks: ``3.1.0-20170507.213722-1`. This is not incompatible with Semver, but would replace the `PRERELEASE` label `SNAPSHOT` with a concrete `PRERELEASE` timestamp.


