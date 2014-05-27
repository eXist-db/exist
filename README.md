eXist Native XML Database
=========================
[![Build Status](https://travis-ci.org/eXist-db/exist.png?branch=develop)](https://travis-ci.org/eXist-db/exist)
[![Build Status](http://build.exist-db.se/buildStatus/icon?job=github-eXist-db-develop-rebuild-test)](http://build.exist-db.se/view/github/job/github-eXist-db-develop-rebuild-test/)

This is the GitHub for the [eXist](http://www.exist-db.org "eXist") Native XML Database. 

If you're looking to work with the eXist source code, you've come to the right place. If not, you're probably looking for the [eXist Documentation](http://www.exist-db.org/exist/apps/doc/ "Documentation of eXist").

If you're looking for help or discussion, visit the eXist community [mailing lists](http://www.exist-db.org/exist/apps/doc/getting-help.xml "eXist Mailing Lists").


Information for Developers
--------------------------
If you wish to work on the eXist source code we're now using [Git](http://git-scm.org "Git SCM") (and [GitHub](http://www.github.com)) for our source code management. If you're not familiar with Git, we recommend [this excellent online interactive tutorial](http://try.github.io).


Building eXist from Source
--------------------------
eXist itself is written predominantly in Java 6. The build system is [Apache Ant](http://ant.apache.org/ "The Apache Ant Project").

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


Contributing to eXist
---------------------
We welcome all contributions to eXist! 

We strongly suggest that you join the [eXist-development mailing](https://lists.sourceforge.net/lists/listinfo/exist-development "eXist Development Mailing List") list and also subscribe to the [eXist-commits mailing list](https://lists.sourceforge.net/lists/listinfo/exist-commits "eXist SCM Commits Mailing List"), so that you can collaborate with the eXist team and be kept up to date with changes to the codebase.

eXist uses the [GitFlow](http://nvie.com/git-model) branching model for development. Specifically, we're using the [AVH Edition of GitFlow tools](https://github.com/petervanderdoes/gitflow) version.

If you're not familiar with GitFlow, check out some of the good tutorials linked in ["Getting Started"](https://github.com/petervanderdoes/gitflow#getting-started) of the GitFlow AVH Edition page. There's also a very good [git-flow cheatsheet](http://danielkummer.github.io/git-flow-cheatsheet/).

If you wish to contribute, the general approach is:

- Fork the repo on GitHub 
- `git clone` your fork
- Make sure you've [GitFlow AVH Edition](https://github.com/petervanderdoes/gitflow) installed
- Run `git flow init` on the cloned repo using [these settings](#our-git-flow-init-settings).
- Use Git Flow to *start* a hotfix or feature i.e. git flow feature start *my-magic-feature*
- Do your stuff! :-)
- Commit to your repo. We like small, atomic commits that don't mix concerns.
- Use Git Flow to finish the `hotfix` or `feature`. **WARNING:** If you're using a `hotfix`, please don't tag it; there's no way to send an unknown branch from your fork upstream using GitHub's Pull Requests.
- Push your hotfix or feature branch to your GitHub using GitFlow (`git flow feature publish *my-magic-feature*`)
- Send us a Pull Request

Pull Requests are reviewed and tested before they're merged by the core development team.
However, we have one golden rule, even within the core team: **never merge your own pull request**. This simple-but-important rule ensures that at least two people have considered the change. 

Although the following are taken from our [Developer Manifesto](http://www.exist-db.org/exist/apps/doc/devguide_manifesto.xml "eXist Project Developer Manifesto") and [Code Review Guide](http://www.exist-db.org/exist/apps/doc/devguide_codereview.xml "eXist Project Code Review Guide"), the main things that get a Pull Request accepted are:

- **Only change what you need to.** If you must reformat code, keep it in a separate commit to any syntax or functionality changes.
- **Test.** If you fix something prove it, write a test that illustrates the issue before you fix the issue and validate the test. If you add a new feature it needs tests, so that we can understand its intent and try to avoid regressions in future as much as possible.
- **Make sure the appropriate licence header appears at the top of your source code file.** We use [LGPL v2.1](http://opensource.org/licenses/LGPL-2.1 "The GNU Lesser General Public License, version 2.1") for eXist and *strongly* encourage that, but ultimately any compatible [OSI approved license](http://opensource.org/licenses "Open Source Licenses") without further restrictions may be used.
- **Run the full eXist test suite.** We don't accept code that causes regressions.


Do I work on a bug-fix using a `feature` or a `hotfix`?
-------------------------------------------------------
If you want to contribute a *bug-fix*, you need to consider whether this is a `feature` or a `hotfix` in GitFlow terminology.

Making the determination involves considering how the bug-fix is to be applied and how it is to be applied. First, you should carefully read the "Feature branches" and "Hotfix branches" sections from [A successful Git branching model](http://nvie.com/posts/a-successful-git-branching-model/). 

If you're still unsure, consider: 

- The bug-fix is a hotfix if it is **critical** and needs to go into a very soon to be released revision version i.e. 2.1.n to address an immediate production issue.
- Otherwise it is a feature, i.e. its just standard development towards the next release of eXist.

Even for a bug-fix you should most probably use a `feature`. If you're certain you want to create a `hotfix`, please consider discussing first via the `exist-development` mailing list.


Help! I am a human, what does this all mean?
--------------------------------------------
- You work in features using GitFlow in your own fork of our repo.
- If you want to push your feature to your fork before you have finished it locally, i.e. for the purposes of backup or collaboration, you can use *git flow feature publish my-magic-feature*.
- You will only ever send Pull Requests between your 'develop' branch and our 'develop' branch. i.e. finished features.
- If you follow the details above and make it easy for us to accept your Pull Requests, they will get accepted and merged quickly!
- Your fork will eventually become out of sync with the upstream repo as others contribute to eXist. To pull upstream changes into your fork, see: [Syncing a Fork](https://help.github.com/articles/syncing-a-fork). It is usually a good idea to do this at least before you start working on a new feature, and probably before you send us a Pull Request, as it will make merging for us much simpler!


Our `git-flow init` settings
----------------------------
When we started working with the eXist repo we needed to configure it for GitFlow:

```bash
$ git flow init

Which branch should be used for bringing forth production releases?
   - master
Branch name for production releases: [master] 
Branch name for "next release" development: [develop] 

How to name your supporting branch prefixes?
Feature branches? [feature/] 
Release branches? [release/] 
Hotfix branches? [hotfix/] 
Support branches? [support/] 
Version tag prefix? [] eXist-
Hooks and filters directory? [.git/hooks]
```

A new `develop` branch is created, and checked out. 

Verify it like this:

```bash
$ git status
# On branch develop
```

As we have already started with GitFlow, when you run `git flow init`, you'll get slightly different prompts--but the same answers apply! 

You **must** use the following settings:

```bash
$ git flow init

Which branch should be used for bringing forth production releases?
   - develop
Branch name for production releases: [] master

Which branch should be used for integration of the "next release"?
   - develop
Branch name for "next release" development: [develop] 

How to name your supporting branch prefixes?
Feature branches? [feature/] 
Release branches? [release/] 
Hotfix branches? [hotfix/] 
Support branches? [support/] 
Version tag prefix? [] eXist-
Hooks and filters directory? [.git/hooks]
```
