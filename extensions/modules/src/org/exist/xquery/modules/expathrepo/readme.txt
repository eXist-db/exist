--------
Overview
--------

eXist db now uses expath repository to resolve external dependencies located in repository to be used by xquery
and other XML technologies.

-----
Build
-----

This module relies on the presence of the following jar files in the EXIST_HOME/lib/core folder:

	pkg-repo.jar

(which Florent Georges maintains as part of expath package repo)

If you are building from source then the module build process will try and download
these files for you.

This module is enabled by default as eXist now depends on expath package manager.

To configure build set in local.build.properties (or build.properties):

	include.module.expathrepo = true

To enable or disable expathrepo look in conf.xml for

    <module class="org.exist.xquery.modules.expathrepo.ExpathPackageModule"
            uri="http://exist-db.org/xquery/repo" />

------------
XQuery Usage
------------

List packages

    import module namespace repo="http://exist-db.org/xquery/repo";
    repo:list()

Install packages

    import module namespace repo="http://exist-db.org/xquery/repo";
    repo:install('functx-1.0.xar')

Replace packages

    import module namespace repo="http://exist-db.org/xquery/repo";
    repo:remove('functx-1.0.xar')


---------------
Developer Notes
---------------

* This module is default built and enabled with eXist

* It downloads expath repo pck-repo.jar and places under lib/core

* start.config now has an entry for this jar

* Main.java/XQueryContext.java will create repository under webapp/WEB-INF/expathrepo if exist.home is present

* Main.java/XQueryContext.java will create repository under /expathrepo if exist.home is not present

* Repository will be placed relative to exist.home or top level directory

* XQueryContext.java will resolve modules in expath repository

* org.exist.xquery.modules.expathrepo exposes repo functionality as XQuery extension functions under
http://exist-db.org/xquery/repo namespace


------
Future
------

* refactor code, remove any hard codings, add more exception checking

* enhance documentation

* add LocateFunction.java once it works properly inside repo-pkg.jar

* convert existing xquery libs into .xar

* store repository inside eXist ?

* add CreateFunction.java to create new repositories

* load .xar into eXist ?


-------------------------
Issues/Status/Limitations
-------------------------

* currently test/src/xquery/expathrepo unit tests disabled

* repo.removePackage seems to throw PackageException on success (will follow up with Florent Georges)

* eXist-db requires a restart to recognize changes to packages loaded or removed from repository

* expath repository directory needs to be initiated by eXist (in XQueryContext on startup