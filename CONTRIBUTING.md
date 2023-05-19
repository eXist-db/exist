# Contributing to eXist-db
We welcome everyone to contribute to eXist-db. We will consider each individual contribution on its own merits.
We strongly suggest that you join the [eXist-db Slack Channel](https://exist-db.slack.com), so that you can collaborate with the eXist-db community. It is often valuable to discuss a potential contribution before undertaking any work.

We follow a "Hub and Spoke" like development model, therefore you should fork our eXist-db repository, work on branches within your own fork, and then send Pull-Requests for your branches to our GitHub repository.

## Branch Naming
eXist-db uses a [GitFlow](http://nvie.com/git-model) like branching model for development.

The names of each branch should reflect their purpose, the following branches may be of interest:
* `develop` - the main line of development for the next version of eXist-db.
* `master` - reflects the `tag` of the last released version of eXist-db.

There are also branches that enable us to backport hot-fixes and features to older major versions of eXist-db, so that we might release small updates occasionally.
* `develop-4.x.x` - development of the 4.x.x version line of eXist-db, mostly now only used for hot-fixes.
* `develop-5.x.x` - development of the 5.x.x version line of eXist-db.
* `develop-6.x.x` - development of the 6.x.x version line of eXist-db.

When contributing to eXist-db you should branch one of the development branches above, your branch should be named in one of two ways:

* `feature/<name-of-my-feature>`
    This naming convention should be used when contributing new features to eXist-db. For example `feature/xquery31-sliding-window`
* `hotfix/<name-of-my-fix>`
  This naming convention should be used when contributing bug fixes to eXist-db. For example `feature/xquery31-sliding-window`

Additionally, if you are back-porting a feature or bug fix to a previous version of eXist-db, you should prefix your branch name with a `V.x.x/` where `V` is the major version number, for example: `6.x.x/feature/xquery31-sliding-window`.

## Code Formatting
All new Java code is expected to be formatted inline with the [IntelliJ Default Style for Java code](https://www.jetbrains.com/help/idea/configuring-code-style.html#configure-code-style-schemes). 


## Commit Messages
Commits to eXist-db by developers *should* follow the Git [Commit Guidelines](https://git-scm.com/book/en/v2/Distributed-Git-Contributing-to-a-Project#_commit_guidelines). In addition the summary line of the commit message *must* be prefixed with a label from our controlled list that helps us to better understand the commit and also to generate Change Logs.

### Commit Labels
Our controlled list of commit labels that should be prefixed to each commit summary is:

* `[feature]`
    This should be used when a commit adds a new feature.
* `[bugfix]`
    This should be used when a commit addresses a bug or issue.
* `[refactor]`
    This should be used when a commit is simply refactoring existing code.
* `[optimize]`
    This should be used when a commit is refactoring existing code to provide a performance and/or memory optimization.
* `[ignore]`
    This should be used when code is cleaned up by automated means, e.g. reformatting.
* `[doc]`
    This should be used for documentation.
* `[test]`
    This should be used when a commit solely contains changes to existing tests or adds further tests.
* `[ci]`
    This should be used when a commit solely makes changes to CI configuration.

In addition any commit that addresses a GitHub issue, should have an additonal line in its commit after the summary and before any fuller explaination that takes this form:
```
Closes https://github.com/eXist-db/exist/issues/<github-issue-number>
```

### Commit Message Example
For example, here is a correctly formatted commit message:

```
[bugfix] Fix relative paths in EXPath classpath.txt files.

Closes https://github.com/eXist-db/exist/issues/4901
We now store the path of Jar files in each EXPath Package's `.exist/classpath.txt` file relative to the package's `content/` sub-folder.
```

## Pull Requests and Code Review
Pull Requests are reviewed and tested before they're merged by the eXist-db Core Development Team.
We have a policy around how Pull Requests are reviewed in a timely and fair manner. That policy is available here - [Community Code Review and Merge Policy for the exist-db/exist Git Repository](PR-CODE-REVIEW-POLICY.md). 
Worth restating, is the one "golden rule", even within the Core Team, **no developer should merge their own pull request**. This simple-but-important rule ensures that at least two people have considered the change.

Although the following are taken from our [Developer Manifesto](http://www.exist-db.org/exist/apps/doc/devguide_manifesto.xml "eXist Project Developer Manifesto") and [Code Review Guide](http://www.exist-db.org/exist/apps/doc/devguide_codereview.xml "eXist Project Code Review Guide"), the main things that get a Pull Request accepted are:

-   **Only change what you need to.** If you must reformat code, keep it in a separate commit to any syntax or functionality changes.
-   **Test.** If you fix something prove it, write a test that illustrates the issue and validate the test. If you add a new feature it also requires tests, so that we can understand its intent and try to avoid regressions in future as much as possible.
-   **Make sure the appropriate licence header appears at the top of your source code file.** We use [LGPL v2.1](http://opensource.org/licenses/LGPL-2.1 "The GNU Lesser General Public License, version 2.1") for eXist-db and *strongly* encourage that, but ultimately any compatible [OSI approved license](http://opensource.org/licenses "Open Source Licenses") without further restrictions may be used.
-   **Run the full eXist test suite.** We don't accept code that causes regressions. This will also be checked in CI.


## Security Issues
***If you find a security vulnerability, do NOT open an issue.***

Any security issues should be submitted directly to <security@exist-db.org>.  In order to determine whether you are dealing with a security issue, ask yourself these two questions:

*   Can I access something that's not mine, or something I shouldn't have access to?
*   Can I disable something for other people?

If the answer to either of those two questions are "yes", then you're probably dealing with a security issue. Note that even if you answer "no" to both questions, you may still be dealing with a security issue, so if you're unsure, just email us at <security@exist-db.org>.

## Versions and Releases
eXist follows a Semantic Versioning scheme, this is further documented in the [eXist Versioning Scheme and Release Process](exist-versioning-release.md) document.

### Porting during Release Candidate development phase
When developing one of more stable release lines and/or a release-candidate in parallel, this may require commits to be both back- and forward-ported until the release-candidate has become the next stable release.

Under these circumstance pull-request for the same purpose may be opened multiple times against different `develop`* branches

#### Backport
Assuming the stable is `6.x.x` and the RC is `7.x.x`
-   create a second branch `6.x.x/feature/<name-of-my-feature>` based off `develop-6.x.x`
-   [`cherry-pick`](https://git-scm.com/docs/git-cherry-pick) your commits from `feature/<name-of-my-feature>` into `6.x.x/feature/<name-of-my-feature>`
-   open a second PR from `6.x.x/feature/<name-of-my-feature>` against `develop-6.x.x` mentioning the original PR in the commit message

### Forward-port
Works just as backport but with `feature/<name-of-my-feature>` and `develop`


## Syncing a Fork
Your fork will eventually become out of sync with the upstream repo as others contribute to eXist. To pull upstream changes into your fork, you have two options:

1.  [Merging](https://help.github.com/articles/syncing-a-fork).
2.  Rebasing.

Rebasing leads to a cleaner revision history which is much easier to follow and is our preferred approach. However, `git rebase` is a very sharp tool and must be used with care. For those new to rebase, we would suggest having a backup of your local (and possibly remote) git repos before continuing. Read on to learn how to sync using rebase.


#### Rebase Example
Lets say that you have a fork of eXist-db's GitHub repo, and you have been working in your feature branch called `feature/my-feature` for some time, you are happy with how your work is progressing, but you want to sync so that your changes are based on the latest and greatest changes from eXist-db. The way to do this using `git rebase` is as follows:

1.  If you have any un-committed changes you need to stash them using: `git stash save "changes before rebase"`.

2.  If you have not added eXist-db's GitHub as an upstream remote, you need to do so once by running `git remote add upstream https://github.com/exist-db/exist.git`. You can view your existing remotes, by running `git remote -v`.

3.  You need to fetch the latest changes from eXist-db's GitHub: `git fetch upstream`. This will not yet change your local branches in any way.

4.  You should first sync your `develop` branch with eXist-db's `develop` branch. As you always work in feature branches, this should a simple fast-forward by running: `git checkout develop` and then `git rebase upstream/develop`.
    1.  If all goes well in (4) then you can push your `develop` branch to your remote server (e.g. GitHub) with `git push origin develop`.

5.  You can then replay your work in your feature branch `feature/my-feature` atop the lastest changes from the `develop` branch by running: `git checkout feature/my-feature` and then `git rebase develop`.
    1.  Should you encounter any conflicts during (5) you can resolve them using `git mergetool` and then `git rebase --continue`.
    2.  If all goes well in (5), and take care to check your history is correct with `git log`, then you can force push your `feature/my-feature` branch to your remote server (e.g. GitHub) with `git push -f origin feature/my-feature`. *NOTE* the reason you need to use the `-f` to force the push is because the commit ids of your revisions will have changed after the rebase.

Note that it is worth syncing your branches that you are working on relatively frequently to prevent any large rebases which could lead to resolving many conflicting changes where your branch has diverged over a long period of time.

## Tools
Some developers may find that GitFlow tools can help them follow the above branching model. One such tool which may help is the [AVH Edition of GitFlow tools](https://github.com/petervanderdoes/gitflow).

If you're not familiar with GitFlow, check out some of the good tutorials linked in ["Getting Started"](https://github.com/petervanderdoes/gitflow#getting-started) of the GitFlow AVH Edition page. There's also a very good [git-flow cheatsheet](http://danielkummer.github.io/git-flow-cheatsheet/).

If you wish to contribute, the general approach using GitFlow AVH Edition is:

-   Fork the repo on GitHub
-   `git clone` your fork
-   Make sure you've [GitFlow AVH Edition](https://github.com/petervanderdoes/gitflow) installed
-   Run `git flow init` on the cloned repo using [these settings](#our-git-flow-init-settings).
-   Use Git Flow to *start* a hotfix or feature i.e. `git flow feature start my-feature`.
-   Do your stuff! :-)
-   Commit to your repo. We like small, atomic commits that don't mix concerns.
-   **Do NOT** finish the `hotfix` or `feature` with GitFlow.
-   Make sure your branch is based on the latest eXist develop branch before making a pull-request. This will ensure that we can easily merge in your changes. See [Syncing a Fork](#syncing-a-fork).
-   Push your hotfix or feature branch to your GitHub using GitFlow: `git flow feature publish my-feature`.
-   Send us a Pull Request on GitHub from your branch to our develop branch.
-   Once the Pull Request is merged you can delete your branch, you need not finish or merge it, you will however want to sync your develop branch to bring back your changes. See [Syncing a Fork](#syncing-a-fork).

### Our `git-flow init` settings
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
