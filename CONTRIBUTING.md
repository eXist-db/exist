Contributing to eXist
---------------------
We welcome all contributions to eXist! 

We strongly suggest that you join the [eXist-development mailing](https://lists.sourceforge.net/lists/listinfo/exist-development "eXist Development Mailing List") list and also subscribe to the [eXist-commits mailing list](https://lists.sourceforge.net/lists/listinfo/exist-commits "eXist SCM Commits Mailing List"), so that you can collaborate with the eXist team and be kept up to date with changes to the codebase.

### General Issues
eXist uses the [GitFlow](http://nvie.com/git-model) branching model for development. Specifically, we're using the [AVH Edition of GitFlow tools](https://github.com/petervanderdoes/gitflow) version.

If you're not familiar with GitFlow, check out some of the good tutorials linked in ["Getting Started"](https://github.com/petervanderdoes/gitflow#getting-started) of the GitFlow AVH Edition page. There's also a very good [git-flow cheatsheet](http://danielkummer.github.io/git-flow-cheatsheet/).

If you wish to contribute, the general approach is:

- Fork the repo on GitHub 
- `git clone` your fork
- Make sure you've [GitFlow AVH Edition](https://github.com/petervanderdoes/gitflow) installed
- Run `git flow init` on the cloned repo using [these settings](#our-git-flow-init-settings).
- Use Git Flow to *start* a hotfix or feature i.e. `git flow feature start my-feature`.
- Do your stuff! :-)
- Commit to your repo. We like small, atomic commits that don't mix concerns.
- **Do NOT** finish the `hotfix` or `feature` with GitFlow.
- Make sure your branch is based on the latest eXist develop branch before making a pull-request. This will ensure that we can easily merge in your changes. See [Syncing a Fork](#syncing-a-fork).
- Push your hotfix or feature branch to your GitHub using GitFlow: `git flow feature publish my-feature`.
- Send us a Pull Request on GitHub from your branch to our develop branch.
- Once the Pull Request is merged you can delete your branch, you need not finish or merge it, you will however want to sync your develop branch to bring back your changes. See [Syncing a Fork](#syncing-a-fork).

Pull Requests are reviewed and tested before they're merged by the core development team.
However, we have one golden rule, even within the core team: **never merge your own pull request**. This simple-but-important rule ensures that at least two people have considered the change. 

Although the following are taken from our [Developer Manifesto](http://www.exist-db.org/exist/apps/doc/devguide_manifesto.xml "eXist Project Developer Manifesto") and [Code Review Guide](http://www.exist-db.org/exist/apps/doc/devguide_codereview.xml "eXist Project Code Review Guide"), the main things that get a Pull Request accepted are:

- **Only change what you need to.** If you must reformat code, keep it in a separate commit to any syntax or functionality changes.
- **Test.** If you fix something prove it, write a test that illustrates the issue before you fix the issue and validate the test. If you add a new feature it needs tests, so that we can understand its intent and try to avoid regressions in future as much as possible.
- **Make sure the appropriate licence header appears at the top of your source code file.** We use [LGPL v2.1](http://opensource.org/licenses/LGPL-2.1 "The GNU Lesser General Public License, version 2.1") for eXist and *strongly* encourage that, but ultimately any compatible [OSI approved license](http://opensource.org/licenses "Open Source Licenses") without further restrictions may be used.
- **Run the full eXist test suite.** We don't accept code that causes regressions.


### Security Issues

***If you find a security vulnerability, do NOT open an issue.***

Any security issues should be submitted directly to <security@exist-db.org>.  In order to determine whether you are dealing with a security issue, ask yourself these two questions:

*   Can I access something that's not mine, or something I shouldn't have access to?
*   Can I disable something for other people?

If the answer to either of those two questions are "yes", then you're probably dealing with a security issue. Note that even if you answer "no" to both questions, you may still be dealing with a security issue, so if you're unsure, just email us at <security@exist-db.org>.

Versions and Releases
---------------------
eXist follows a Semantic Verisoning scheme, this is further documented in the [eXist Versioning Scheme and Release Process](exist-versioning-release.md) document.

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
- If you want to push your feature to your fork before you have finished it locally, i.e. for the purposes of backup or collaboration, you can use `git flow feature publish my-feature`.
- You will only ever send Pull Requests between your 'develop' branch and our 'develop' branch. i.e. finished features.
- If you follow the details above and make it easy for us to accept your Pull Requests, they will get accepted and merged quickly!


Syncing a Fork
--------------
Your fork will eventually become out of sync with the upstream repo as others contribute to eXist. To pull upstream changes into your fork, you have two options:

1. [Merging](https://help.github.com/articles/syncing-a-fork).
2. Rebasing.

Rebasing leads to a cleaner revision history which is much easier to follow and is our preferred approach. However, `git rebase` is a very sharp tool and must be used with care. For those new to rebase, we would suggest having a backup of your local (and possibly remote) git repos before continuing. Read on to learn how to sync using rebase.


#### Rebase Example

Lets say that you have a fork of eXist's GitHub repo, and you have been working in your feature branch called `my-feature` for sometime, you are happy with how your work is progressing, but you want to sync so that your changes are based on the latest and greatest changes from eXist. The way to do this using `git rebase` is as follows:

1. If you have any un-committed changes you need to stash them using: `git stash save "changes before rebase"`.

2. If you have not added eXist's GitHub as an upstream remote, you need to do so by running `git remote add upstream https://github.com/exist-db/exist.git`. You can view your existing remotes, by running `git remote -v`.

3. You need to fetch the latest changes from eXist's GitHub: `git fetch upstream`. This will not yet change your local branches in any way.

4. You should first sync your `develop` branch with eXist's `develop` branch. As you always work in feature branches, this should a simple fast-forward by running: `git checkout develop` and then `git rebase upstream/develop`.
  1. If all goes well in (4) then you can push your `develop` branch to your remote server (e.g. GitHub) with `git push origin develop`.

5. You can then replay your work in your feature branch `my-feature` atop the lastest changes from the develop branch by running: `git checkout feature/my-feature` and then `git rebase develop`.
  1. Should you encounter any conflicts during (5) you can resolve them using `git mergetool` and then `git rebase --continue`.
  2. If all goes well in (5), and take care to check your history is correct with `git log`, then you can force push your `feature/my-feature` branch to your remote server (e.g. GitHub) with `git push -f origin feature/my-feature`. *NOTE* the reason you need to use the `-f` to force the push is because the commit ids of your revisions will have changed after the rebase.

Note that it is worth syncing your branches that you are working on relatively frequently to prevent any large rebases which could lead to resolving many conflicting changes where your branch has diverged over a long period of time.


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
