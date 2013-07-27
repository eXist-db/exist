eXist Native XML Database
=========================

This is the GitHub for the [eXist](http://www.exist-db.org "eXist") Native XML Database. If you are looking to work with the Source code of eXist you have come to the right place, if not you are probably looking for the [eXist Documentation](http://www.exist-db.org/exist/apps/doc/ "Documentation of eXist").

If you are looking for help or discussion, you should visit the eXist community [mailing lists](http://www.exist-db.org/exist/apps/doc/getting-help.xml "eXist Mailing Lists").

Information for Developers
--------------------------
If you wish to work with the eXist source code we are now using [Git](http://git-scm.org "Git SCM"), and more specifically [GitHub](http://www.github.com) for our Source Code Management. If you are not familiar with Git, then we recommend this excellent online interactive tutorial http://try.github.io/.

Building eXist from Source
--------------------------
eXist itself is written predominantly in Java 6, and it's build system is [Apache Ant](http://ant.apache.org/ "The Apache Ant Project").

The steps for building eXist are:
- Checkout the Git Repository
- Execute Ant to compile eXist

```bash
$ git clone git@github.com:eXist-db/exist.git
$ cd exist
$ git checkout master
$ ./build.sh
```

**Note** In the above example, we switch the checked out branch of eXist from "develop" to "master". We are making use of [GitFlow for eXist](#contributing-to-exist), and as such "develop" is our current stable work in progress which will eventually become the next release version, whereas "master" is always the last released version. The choice of which to use is entirely yours.

**Hint** In the above example, we use the SSH form of the GitHub repo URL to clone eXist, however if you are behind a HTTP Proxy Server and your organisation does not permit outgoing SSH connections (which is true of many larger organisations) then you should instead try the HTTPS URL for our GitHub repo, i.e. https://github.com/eXist-db/exist.git.

From here, you now have a compiled version of eXist that you may use just as you would an installed version of eXist, however it may be desirable to package this up for easy installation elsewhere. If you wish to create a simple Zip distribution of eXist you can optionally run:
```bash
$ ./build.sh dist-zip
```

Likewise, you may also wish to optionally build a full Installer for eXist, note that this requires the installation of IzPack, and setting your path to IzPack in *exist/build.properties* before you run:

```bash
$ ./build.sh installer
```

Otherwise, you may wish to deploy eXist into a Web Application Server as a WAR file, for which we provide a build too, simply run:

```bash
$ ./build.sh dist-war
```

For further build options see the [eXist Build Documentation](http://www.exist-db.org/exist/apps/doc/building.xml "How to build eXist").

Contributing to eXist
---------------------
We welcome all contributions to eXist.

We strongly suggest that you join the [eXist-development mailing](https://lists.sourceforge.net/lists/listinfo/exist-development "eXist Development Mailing List") list and also subscribe to the [eXist-commits mailing list](https://lists.sourceforge.net/lists/listinfo/exist-commits "eXist SCM Commits Mailing List"), so that you can collaborate with the eXist team and be kept up to date with changes to the codebase.

eXist uses [GitFlow](http://nvie.com/git-model) as it's code management methodology. Specifically we are using the [AVH Edition of GitFlow tools](https://github.com/petervanderdoes/gitflow) version.
If you do not know GitFlow, there are several good tutorials linked from the [Getting Started part](https://github.com/petervanderdoes/gitflow#getting-started) of the GitFlow AVH Edition page. Also there is a very good [git-flow cheatsheet](http://danielkummer.github.io/git-flow-cheatsheet/).

If you wish to contribute the general approach is:

- Fork the Repo to your own GitHub
- Checkout your Fork
- Make sure you have [GitFlow AVH Edition](https://github.com/petervanderdoes/gitflow) installed
- Use Git Flow to *start* a hotfix or feature i.e. git flow feature start my-magic-feature
- Do your stuff :-)
- Commit(s) to your repo. We like small atomic commits that do not mix concerns
- Do **NOT** *finish() the hotfix or feature, we want to review before it is merged to the *develop* branch.
- Push your hotfix or feature branch to your GitHub
- Send us a Pull Request

All Pull Requests are reviewed and tested before they are merged by the core development team.
However we have one golden rule, even within the core team, **no developer may ever merge his own pull request**. This simple but important rule ensures that at least two people have consisered the change. 

Although the following are taken from our [Developer Manifesto](http://www.exist-db.org/exist/apps/doc/devguide_manifesto.xml "eXist Project Developer Manifesto") and [Code Review Guide](http://www.exist-db.org/exist/apps/doc/devguide_codereview.xml "eXist Project Code Review Guide"), the main things that will help us to merge your Pull Request:

- Only change what you need to. If you must reformat code, keep it in a seperate commit to any syntax or functionality changes.
- Test. If you fix something prove it, write a test that illustrates the issue before you fix the issue and validate the test. If you add a new feature it needs tests, so that we can understand its intent and try to avoid regressions in future as much as possible.
- Make sure the approriate licence header appears at the top of your source code file. We use [LGPL v2.1](http://opensource.org/licenses/LGPL-2.1 "The GNU Lesser General Public License, version 2.1") for eXist and *strongly* encourage that, but ultimately any compatible [OSI approved license](http://opensource.org/licenses "Open Source Licenses") without further restrictions may be used.
- Run the full eXist test suite. We do not accept code that causes regressions.


