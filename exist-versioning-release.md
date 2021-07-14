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

This section details concrete steps for creating and publishing product releases. 

### Release preprations and prerequisites 

#### Required Software 
Once development on a new stable version is complete, the following steps will prepare the version for release. For purposes of illustration, we will assume we are preparing the stable release of version 5.3.0.
You will require a system with:

* macOS
* JDK 8
* Maven 3.6.0+
* Docker
* GnuPG

#### Required Keys, Credentials and Permissions

##### GPG Key / Keystore and Credentials
A private GPG key for signing release artifacts is required. To gather the required information run `gpg --list-secret-keys --keyid-format SHORT`

```
gpg --list-secret-keys --keyid-format SHORT
/Users/username/.gnupg/pubring.kbx
----------------------------------
sec   rsa4096/12345678 2013-04-26 [SC]
```

This gives you the keystore you are using (here: `/Users/username/.gnupg/pubring.kbx`) and your key-id (here: `12345678` the part after rsa4096/). 

**Important Note** 
GnuPG is using an [optimized keystore since version 2.1](https://www.gnupg.org/documentation/manuals/gnupg/GPG-Configuration.html) which contains public and secret keys. Former GnuPG version had seperated keystore for public (~/.gnupg/pubring.gpg) and private keys (~/.gnupg/secring.gpg). 

###### Maven Keystore values prior GnuPG 2.1

* existdb.release.public-keyfile=~/.gnupg/pubring.gpg
* existdb.release.private-keyfile=~/.gnupg/secring.gpg

###### Maven Keystore values for GnuPG 2.1 or newer

* existdb.release.public-keyfile=~/.gnupg/pubring.kbx
* existdb.release.private-keyfile=~/.gnupg/pubring.kbx
                   
To test gpg simply create a file `test.txt` and run`gpg -ab test.txt`to sign it. you should see a new file `test.txt.asc` which means GnuPG signing worked fine. 

##### Java KeyStore 
* To create an eXist-db release a Java KeyStore with key is needed to sign the IzPack Installer. In case you don't have a Java KeyStore you can create one with: 

```bash
keytool -genkey \
  -alias some-alias \
  -keystore keystore.p12 \
  -storetype PKCS12 \
  -keyalg RSA \
  -storepass some-pass \
  -validity 7300 \
  -keysize 4096
```

To verify if your Java KeyStore works fine run 

```bash
keytool -list -v -keystore /path/to/keystore.p12
```

##### Apple Developer Certificate

A valid Apple Developer Certificate to sign Mac DMG is required. If you don't have one you will need to join the [Apple Developer Program](https://developer.apple.com/programs/enroll/) and to create a certificate via XCode. 

##### Github

A Github account and username / password or Github Personal (access tokens)[https://github.com/settings/tokens] with permission to publish Github releases to the eXist-db org. 

##### Sonatype OSS

You will need a Sonatype OSS acount which has been authorized for the eXist-db organisation at Sonatype OSS staging for Maven Central - https://oss.sonatype.org/



##### Docker 

A DockerHub user with permissions to publish to https://cloud.docker.com/orgs/existdb/ is required. 


#### Prepare Release Notes 

* There are various possibilities to create the release notes.

> This section is still work in progress and manual tasks are needed to generate the release notes

The release notes start with an introduction summarizing the most important changes for end-users and developers since the last version was released.

Three sections follow

- a list of all changes and features with links to their PRs
- a list of all fixes with links the their PRs
- a list of all dependencies that got updated along with the new version that is now used in existdb

##### gitchangelog (Python) 

* https://pypi.org/project/gitchangelog/
* configure .gitchangelog.rc in the root of eXist-db repo
* run `gitchangelog` in the eXist-db git repo. 
* the list of dependency updates can be prepared with
    ```
    gitchangelog eXist-5.3.0..HEAD | grep "Updates" | sort > dependency-updates.txt
    ```
    Replace **eXist-5.3.0** with the tag of the latest release.


### Preparing a Product Release

#### Maven settings.xml

Required credentials and the Maven `existdb-release-signing` profile should be stored securely in the `<servers>` section on your machine in your local `~/.m2/settings.xml` file. The contents should follow the example below.
    
```xml
    <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">

        <servers>
        
            <!-- Sonatype OSS staging for Maven Central -->
            <server>
                <id>sonatype-nexus-staging</id>
                <username>YOUR-USERNAME</username>
                <password>YOUR-PASSWORD</password>
            </server>
            
            <!-- eXist-db DockerHub -->
            <server>
                <id>docker.io</id>
                <username>YOUR-USERNAME</username>
                <password>YOUR-PASSWORD</password>
            </server>

            <!-- eXist-db Github Release -->
            <server>
                <id>github</id>
                <privateKey>YOUR-Github-ACCESS-TOKEN</privateKey>
            </server>
        </servers>
        
        <profiles>
           <profile>
               <id>existdb-release-signing</id>
               <properties>                   
                      
                   <existdb.release.key>YOUR-GPG-KEY-ID</existdb.release.key>
                   <existdb.release.public-keyfile>/your/part/to/.gnupg/pubring.gpg</existdb.release.public-keyfile>
                   <existdb.release.private-keyfile>/your/part/to/.gnupg/secring.gpg</existdb.release.private-keyfile>
                   <existdb.release.key.passphrase>YOUR-GPG-PASSWORD</existdb.release.key.passphrase>

                   <existdb.release.keystore>/your/path/to/.keystore</existdb.release.keystore>
                   <existdb.release.keystore.pass>YOUR-KEYSTORE-PASSWORD</existdb.release.keystore.pass>
                   <existdb.release.keystore.key.alias>YOUR-KEYSTORE-ALIAS</existdb.release.keystore.key.alias>
                   <existdb.release.keystore.key.pass>YOUR-KEYSTORE-PASSWORD</existdb.release.keystore.key.pass>
               </properties>
           </profile>
        </profiles>
        <activeProfiles>
            <activeProfile>existdb-release-signing</activeProfile>
        </activeProfiles>
    </settings>
```

1. Merge any outstanding PRs that have been reviewed and accepted for the milestone eXist-5.3.0.

2. Create a new clone of the branch you want to release from into a new folder.
   In the example below the `develop` branch is cloned into the folder `release`


    ```bash
    git clone \ 
      -b develop \
      git@github.com:eXist-db/exist.git \
      release
    ```
    
3. switch to that directory

    ```bash
    cd release
    ```

    You can verify that origin now points to the main repository `git@github.com:eXist-db/exist.git`
    
    ```bash
    git remote -v
    ```



4.  Prepare the release
    **NOTE:** If this is the first time you prepare a release or you made changes to the process you should do a dry-run first by setting `-DdryRun=true`
    With `-V` you can also check the maven and JDK version that will be used

    ```bash
    mvn \
      -Pdocker \
      -Pinstaller \
      -Pconcurrency-stress-tests \
      -Pmicro-benchmarks \
      -Darguments="-Ddocker=true -Dmac-signing=true -Dizpack-signing=true -Dmac.codesign.identity=\"your codesigning ID\" \
      release:prepare
    ```
    
    Maven will start the release process and prompt you for any information that it requires, for example:
    
    ```
    [INFO] --- maven-release-plugin:2.1:prepare (default-cli) @ exist ---
    [INFO] Verifying that there are no local modifications...
    [INFO]   ignoring changes on: pom.xml.next, pom.xml.releaseBackup, pom.xml.tag, pom.xml.backup, pom.xml.branch, release.properties
    [INFO] Executing: /bin/sh -c cd /Users/aretter/code/exist.maven && git status
    [INFO] Working directory: /Users/aretter/code/exist.maven
    [INFO] Checking dependencies and plugins for snapshots ...
    What is the release version for "eXist-db"? (org.exist-db:exist) 5.3.0: :
    What is SCM release tag or label for "eXist-db"? (org.exist-db:exist) eXist-5.3.0: :
    What is the new development version for "eXist-db"? (org.exist-db:exist) 5.4.0-SNAPSHOT: :
    ```

5. Test the release artefacts as much as possible on different Operating systems (Mac, Windows, Linux).  

   * `$EXIST/exist-distribution/target/` 
     * eXist-db-<version>.dmg
     * exist-distribution-eXist-db-<version>.bz2
     * exist-distribution-eXist-db-<version>-win.zip
   * `$EXIST/exist-installer-<version>.jar` 

#### Signing Binaries (work in progress)

In order to allow end-users to run the installer as well as the application on macOSX (Catalina) the application needs to be notarized by Apple.
    
At this step the application is ready to be notarized and the notarisation ticket must be stapled to the app once that was successful.
    
#### Troubleshooting

##### Cleanup dry-run

    mvn \
      -Pdocker \
      -Pinstaller \
      -Pconcurrency-stress-tests \
      -Pmicro-benchmarks \
      release:clean

##### Rollback Release

If you need to rollback the `mvn release:prepare` phase:

`mvn release:rollback` works but

- the two commits will remain in the git history and
- you will need to remove git tag from github by hand    
    
    
### Releasing the Product 
    
1. Once the prepare process completes successfully and artifacts have been tested you can perform the release. This will upload Maven Artifacts to Maven Central (staging), Docker images to Docker Hub, and eXist-db distributions and installer to Github releases. 
    
    ```bash
    mvn \
      -Pdocker \
      -Pinstaller \
      -Pconcurrency-stress-tests \
      -Pmicro-benchmarks \
      -Darguments="-Djarsigner.skip=false -Dmac-signing=true -Dmac.codesign.identity=\"your codesign ID\"" \
      release:perform
    ```

    #### Notarizing macOSX installer (work in progress)
    
    Now the installer in `target/checkout/exist-distribution/eXist-5.3.0.dmg` needs to be notarized and the notarisation ticket must be stapled to the installer once that was successful.
    The notarized installer containing the notarized app must then be uploaded to the github releases page.

    
2. Update the stable branch (`master`) of eXist-db to reflect the latest release:
    
    
    ```bash
    git checkout -b next-master eXist-5.3.0
    git push origin next-master:master
    ```

3.  Check that the new versions are visible on [Github](https://github.com/eXist-db/exist/releases).

4.  Check that the new versions are visible on [DockerHub](https://hub.docker.com/r/existdb/existdb).

5.  Login to https://oss.sonatype.org and release the Maven artifacts to Maven central as described [here](https://central.sonatype.org/pages/releasing-the-deployment.html).

6.  Update the Mac HomeBrew for eXist-db, see: [Releasing to Homebrew](https://github.com/eXist-db/exist/blob/develop/exist-versioning-release.md#releasing-to-homebrew).

### Publishing/Promoting the Product Release


1. Edit the links for the downloads on the eXist website.
    1. `$ git clone https://github.com/exist-db/website.git`
    2.  Edit the file `website/index.html`, you need to modify the HTML under `<a name="downloads"/>` and update the version numbers for the current release:

    ```html
    <a name="downloads"/>
      <div class="row">
        <div class="col-md-12">
          <h2 id="download">Download</h2>
            <a href="https://github.com/eXist-db/exist/releases/latest">
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

    4. Commit your change and push: 

        ```bash
        $ git commit index.html expath-pkg.xml -m "Update for eXist-5.3.0 website" && git push origin master
        ```

    5. Tag your release of the Website and push the tag: 
    
        ```bash
        $ git tag -s -m "Release tag for eXist 5.3.0 website" eXist-5.3.0 && git push origin eXist-5.3.0
        ```

    6. Create a XAR for the website: 
    
    ```bash
    $ git checkout eXist-5.3.0 && ant
    ```

    7. Visit https://www.exist-db.org/exist/apps/dashboard/index.html, login and upload the new `build/homepage.xar` file via the Package Manager.

2.  Login to the blog at [https://exist-db.org/exist/apps/wiki/blogs/eXist/](http://exist-db.org/exist/apps/wiki/blogs/eXist/) and add a new news item which announces the release and holds the release notes. It should be named like [https://exist-db.org/exist/apps/wiki/blogs/eXist/eXistdb530](https://exist-db.org/exist/apps/wiki/blogs/eXist/eXistdb530)

    2.1. Warning: there is a known issue in AtomicWiki where your release notes might suddenly disappear. In case this happens your data is not lost but stored in `/db/apps/wiki/data/blogs/eXist/.md`. You can rename it or move the content to a `eXistdb<VERSION>.md` file and create an according `eXistdb<VERSION>.atom` for it. Once these two files are available the blog entry will become visible on the eXist-db homepage and it will be visible in the eXist-db blog. 

3.  Visit the GitHub releases page [https://github.com/eXist-db/exist/releases](https://github.com/eXist-db/exist/releases) and create a new release, enter the tag you previously created and link the release notes from the blog.

4.  Send an email to the `exist-open` mailing list announcing the release with a title similar to `[ANN] Release of eXist 5.3.0`, copy and paste the release notes from the blog into the email and reformat appropriately (see [past emails](https://sourceforge.net/p/exist/mailman/search/?mail_list=exist-open)).

5.  Tweet about it using the `existdb` twitter account.

6. Post it to the LinkedIn eXist-db group: [https://www.linkedin.com/groups/35624](https://www.linkedin.com/groups/35624)

7. Submit a news item to XML.com - [https://www.xml.com/news/submit-news-item/](https://www.xml.com/news/submit-news-item/).
  * use tags: database, nosql, xml, exist-db, xquery, xslt         

8. Update the Wikipedia page with the new version details - [https://en.wikipedia.org/wiki/EXist](https://en.wikipedia.org/wiki/EXist).

9. Go to GitHub and move all issues and PRs which are still open for the release milestone to the next release milestone. Close the release milestone.


### Releasing to Homebrew

[Homebrew](http://brew.sh) is a popular command-line package manager for macOS. Once Homebrew is installed, applications like eXist can be installed via a simple command. eXist's presence on Homebrew is found in the Caskroom project, as a "cask", at [https://github.com/caskroom/homebrew-cask/blob/master/Casks/exist-db.rb](https://github.com/caskroom/homebrew-cask/blob/master/Casks/exist-db.rb).

> **Terminology:** "Caskroom" is the Homebrew extension project where pre-built binaries and GUI applications go, whereas the original "Homebrew" project is reserved for command-line utilities that can be built from source. Because the macOS version of eXist is released as an app bundle with GUI components, it is distributed via Caskroom.

When there is a new release of eXist, a member of the community can submit a pull request with the necessary changes to the eXist cask. [Follow the directions on the Homebrew-cask Github](https://github.com/caskroom/homebrew-cask/blob/master/CONTRIBUTING.md#updating-a-cask) - summarized here adapted to OpenRefine:

```bash
# install and setup script - only needed once
brew install vitorgalvao/tiny-scripts/cask-repair
cask-repair --help

# use to update eXist
cask-repair exist-db
```

The cask-repair tool will prompt you to enter the new version number. It will then use this version number to construct a download URL using the formula (where `{version}` represents the version number):

```bash
https://github.com/eXist-db/exist/releases/download/eXist-{version}/eXist-db-{version}.dmg
```

**Note:** It is important that both version number components (the tag and version number) match, so that the formula can find the installer's URL.

Once cask-repair has successfully downloaded the new installer, it will calculate the new SHA-256 fingerprint value and construct a pull request, like this one: [https://github.com/caskroom/homebrew-cask/pull/42509](https://github.com/caskroom/homebrew-cask/pull/42509). Once the pull request is submitted, continuous integration tests will run, and a member of the caskroom community will review the PR. At times there is a backlog on the CI servers, but once tests pass, the community review is typically completed in a matter of hours.

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

eXist now has a single version number, forumlated according to the precise principles of Semantic Versioning, captured in a single property:

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

While a release candidate is being tested, only bugfix patch PRs for that RC can be merged. We could consider a slightly more complex branch and release process to enable the `develop` branch to continue unrestricted.

### Maven Compatibility

The use of the `BUILD` label may have to be refined if we migrate to Maven. Maven Snapshots have two forms:

1.  A base version which is not actualised, e.g.: `3.1.0-SNAPSHOT`. This fits with our current proposals.

2.  A published SNAPSHOT release which looks: ``3.1.0-20170507.213722-1`. This is not incompatible with Semver, but would replace the `PRERELEASE` label `SNAPSHOT` with a concrete `PRERELEASE` timestamp.
