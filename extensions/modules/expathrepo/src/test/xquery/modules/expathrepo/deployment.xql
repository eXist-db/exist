(:
 : eXist-db Open Source Native XML Database
 : Copyright (C) 2001 The eXist-db Authors
 :
 : info@exist-db.org
 : http://www.exist-db.org
 :
 : This library is free software; you can redistribute it and/or
 : modify it under the terms of the GNU Lesser General Public
 : License as published by the Free Software Foundation; either
 : version 2.1 of the License, or (at your option) any later version.
 :
 : This library is distributed in the hope that it will be useful,
 : but WITHOUT ANY WARRANTY; without even the implied warranty of
 : MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 : Lesser General Public License for more details.
 :
 : You should have received a copy of the GNU Lesser General Public
 : License along with this library; if not, write to the Free Software
 : Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 :)
xquery version "3.1";

(:~
 : Tests for expath package deployment and removal.
 :)
module namespace deploy="http://exist-db.org/test/deploy";

declare namespace test="http://exist-db.org/xquery/xqsuite";

import module namespace compression = "http://exist-db.org/xquery/compression";
import module namespace repo = "http://exist-db.org/xquery/repo";
import module namespace xmldb = "http://exist-db.org/xquery/xmldb";

declare variable $deploy:expathxml := 
    <package xmlns="http://expath.org/ns/pkg" name="http://exist-db.org/apps/dtest" abbrev="dtest" version="1.0.0" spec="1.0">
        <title>Deployment Test</title>
        <dependency package="http://exist-db.org/html-templating" semver-min="1.0.2"/>
    </package>;

declare variable $deploy:repoxml :=
    <meta xmlns="http://exist-db.org/xquery/repo">
        <description>Deployment Test</description>
        <type>application</type>
        <target>dtest</target>
    </meta>;

declare variable $deploy:repoxml-library :=
    <meta xmlns="http://exist-db.org/xquery/repo">
        <description>Deployment Test</description>
        <type>library</type>
        <target/>
    </meta>;
    
declare variable $deploy:entries := (
    <entry name="expath-pkg.xml" type="xml">{$deploy:expathxml}</entry>,
    <entry name="repo.xml" type="xml">{$deploy:repoxml}</entry>,
    <entry name="test.xml" type="xml"><test><foo/></test></entry>
);

declare variable $deploy:entries-overwrite := (
    <entry name="expath-pkg.xml" type="xml">{$deploy:expathxml}</entry>,
    <entry name="repo.xml" type="xml">{$deploy:repoxml}</entry>,
    <entry name="test-new.xml" type="xml"><test><foo/></test></entry>
);

declare variable $deploy:entries-library := (
    <entry name="expath-pkg.xml" type="xml">{$deploy:expathxml}</entry>,
    <entry name="repo.xml" type="xml">{$deploy:repoxml-library}</entry>,
    <entry name="test.xml" type="xml"><test><foo/></test></entry>
);

(: Separate fixture for the version-routing regression test — uses a distinct
   package name so leftover state doesn't pollute the dtest tests above. :)
declare variable $deploy:expathxml-vtest-high :=
    <package xmlns="http://expath.org/ns/pkg" name="http://exist-db.org/apps/dtest-versioning" abbrev="dtest-versioning" version="1.0.0" spec="1.0">
        <title>Deployment Test (versioning)</title>
        <dependency package="http://exist-db.org/html-templating" semver-min="1.0.2"/>
    </package>;

declare variable $deploy:expathxml-vtest-low :=
    <package xmlns="http://expath.org/ns/pkg" name="http://exist-db.org/apps/dtest-versioning" abbrev="dtest-versioning" version="0.5.0" spec="1.0">
        <title>Deployment Test (versioning, older)</title>
        <dependency package="http://exist-db.org/html-templating" semver-min="1.0.2"/>
    </package>;

declare variable $deploy:repoxml-vtest :=
    <meta xmlns="http://exist-db.org/xquery/repo">
        <description>Deployment Test (versioning)</description>
        <type>application</type>
        <target>dtest-versioning</target>
    </meta>;

declare variable $deploy:entries-vtest-high := (
    <entry name="expath-pkg.xml" type="xml">{$deploy:expathxml-vtest-high}</entry>,
    <entry name="repo.xml" type="xml">{$deploy:repoxml-vtest}</entry>,
    <entry name="test-high.xml" type="xml"><test><foo/></test></entry>
);

declare variable $deploy:entries-vtest-low := (
    <entry name="expath-pkg.xml" type="xml">{$deploy:expathxml-vtest-low}</entry>,
    <entry name="repo.xml" type="xml">{$deploy:repoxml-vtest}</entry>,
    <entry name="test-low.xml" type="xml"><test><foo/></test></entry>
);

declare
    %test:setUp
function deploy:setup() {
    xmldb:create-collection("/db", "deployment-test")
};

declare
    %test:tearDown
function deploy:cleanup() {
    xmldb:remove("/db/deployment-test")
};

declare 
    %test:name("Install app package and remove it afterwards")
    %test:assertEquals("ok", "true", "ok", "true", "true", "false")
function deploy:install-uninstall() {
    let $zip := compression:zip($deploy:entries, false())
    let $stored := xmldb:store("/db/deployment-test", "dtest-1.0", $zip)
    let $deployed := repo:install-and-deploy-from-db($stored)
    let $inList := exists(repo:list()[. = "http://exist-db.org/apps/dtest"])
    let $avail1 := exists(doc("/db/apps/dtest/test.xml")/*)
    let $undeploy := repo:undeploy("http://exist-db.org/apps/dtest")
    let $remove := repo:remove("http://exist-db.org/apps/dtest")
    let $avail2 := exists(collection("/db/apps/dtest")/*)
    return (
        $deployed/@result/string(),
        $inList,
        $undeploy/@result/string(), 
        $remove,
        $avail1,
        $avail2
    )
};

declare 
    %test:name("Install app package twice - should not overwrite")
    %test:assertEquals("ok", "ok", "ok", "true", "true", "true", "false")
function deploy:no-overwrite-installed() {
    let $zip := compression:zip($deploy:entries, false())
    let $stored := xmldb:store("/db/deployment-test", "dtest-1.0", $zip)
    let $deployed1 := repo:install-and-deploy-from-db($stored)
    let $avail1 := exists(doc("/db/apps/dtest/expath-pkg.xml")/*)
    let $zip := compression:zip($deploy:entries-overwrite, false())
    let $stored := xmldb:store("/db/deployment-test", "dtest-1.0", $zip)
    let $deployed2 := repo:install-and-deploy-from-db($stored)
    let $avail2 := exists(doc("/db/apps/dtest/test.xml")/*)
    let $undeploy := repo:undeploy("http://exist-db.org/apps/dtest")
    let $remove := repo:remove("http://exist-db.org/apps/dtest")
    let $avail3 := exists(collection("/db/apps/dtest")/*)
    return (
        $deployed1/@result/string(), 
        $deployed2/@result/string(), 
        $undeploy/@result/string(), 
        $remove,
        $avail1,
        $avail2,
        $avail3
    )
};

declare 
    %test:name("Install app package, remove, reinstall - should overwrite")
    %test:assertEquals("ok", "ok", "true", "ok", "ok", "true", "true", "true", "false")
function deploy:overwrite-installed() {
    let $zip := compression:zip($deploy:entries, false())
    let $stored := xmldb:store("/db/deployment-test", "dtest-1.0", $zip)
    let $deployed1 := repo:install-and-deploy-from-db($stored)
    let $avail1 := exists(doc("/db/apps/dtest/expath-pkg.xml")/*)
    let $undeploy1 := repo:undeploy("http://exist-db.org/apps/dtest")
    let $remove1 := repo:remove("http://exist-db.org/apps/dtest")
    let $zip := compression:zip($deploy:entries-overwrite, false())
    let $stored := xmldb:store("/db/deployment-test", "dtest-1.0", $zip)
    let $deployed2 := repo:install-and-deploy-from-db($stored)
    let $avail2 := exists(doc("/db/apps/dtest/test-new.xml")/*)
    let $undeploy2 := repo:undeploy("http://exist-db.org/apps/dtest")
    let $remove2 := repo:remove("http://exist-db.org/apps/dtest")
    let $avail3 := exists(collection("/db/apps/dtest")/*)
    return (
        $deployed1/@result/string(),
        $undeploy1/@result/string(),
        $remove1,
        $deployed2/@result/string(),
        $undeploy2/@result/string(), 
        $remove2,
        $avail1,
        $avail2,
        $avail3
    )
};

declare 
    %test:name("Install library package and remove it afterwards")
    %test:assertEquals("ok", "true", "false", "true", "ok", "true", "false")
function deploy:install-uninstall-library() {
    let $zip := compression:zip($deploy:entries-library, false())
    let $stored := xmldb:store("/db/deployment-test", "dtest-1.0", $zip)
    let $deployed := repo:install-and-deploy-from-db($stored)
    let $inList := exists(repo:list()[. = "http://exist-db.org/apps/dtest"])
    (: Library packages are not deployed into the db :)
    let $avail1 := exists(collection("/db/apps/dtest")/*)
    (: But files can be retrieved from repository :)
    let $avail2 := exists(repo:get-resource("http://exist-db.org/apps/dtest", "test.xml"))
    let $undeploy := repo:undeploy("http://exist-db.org/apps/dtest")
    let $remove := repo:remove("http://exist-db.org/apps/dtest")
    let $avail3 := exists(collection("/db/apps/dtest")/*)
    return (
        $deployed/@result/string(),
        $inList,
        $avail1,
        $avail2,
        $undeploy/@result/string(),
        $remove,
        $avail3
    )
};

declare
    %test:name("repo:remove on unknown package throws EXPATH007 rather than silently returning false")
    %test:assertError("EXPATH007")
function deploy:remove-unknown-throws() {
    repo:remove("http://exist-db.org/apps/this-package-does-not-exist-and-never-did")
};

declare
    %test:name("install-and-deploy respects the freshly installed package version (regression for Deployment.installAndDeploy deploying packages.latest() instead of the version just installed)")
    %test:assertEquals("ok", "ok", "0.5.0", "true")
function deploy:install-lower-version-after-higher() {
    (: Use a distinct package name from the other tests (`dtest-versioning`,
       not `dtest`) so leftover state doesn't pollute the rest of the suite. :)
    let $pkg-name := "http://exist-db.org/apps/dtest-versioning"
    let $target-coll := "/db/apps/dtest-versioning"

    (: Install version 1.0.0 first. :)
    let $zip-high := compression:zip($deploy:entries-vtest-high, false())
    let $stored-high := xmldb:store("/db/deployment-test", "dtest-versioning-high", $zip-high)
    let $deployed-high := repo:install-and-deploy-from-db($stored-high)

    (: Then install version 0.5.0 (lower) while 1.0.0 is still present.
       Before the fix, Deployment.installAndDeploy correctly fetched 0.5.0
       but the subsequent deploy() step ran against packages.latest() (1.0.0)
       and the user-visible deployed version stayed at 1.0.0. :)
    let $zip-low := compression:zip($deploy:entries-vtest-low, false())
    let $stored-low := xmldb:store("/db/deployment-test", "dtest-versioning-low", $zip-low)
    let $deployed-low := repo:install-and-deploy-from-db($stored-low)

    (: After the second install, the descriptor copied into the deployed target
       collection should be 0.5.0's (the just-installed version), not 1.0.0's
       (the existing higher version). Before the fix the second deploy step
       used packages.latest() and copied 1.0.0's descriptor instead.
       NOTE: we read /db/apps/<target>/expath-pkg.xml, NOT
       repo:get-resource(name, "expath-pkg.xml") — the latter reads from
       packages.latest()'s dir, not from the deployed target. :)
    let $deployed-version := doc($target-coll || "/expath-pkg.xml")/*:package/@version/string()

    (: Sanity check: the just-installed 0.5.0 XAR's distinctive resource
       test-low.xml should be present in the deployed target collection.
       (test-high.xml may also still be there from the prior 1.0.0 deploy —
       deploy does not clean up old files — so its presence is not
       informative either way.) :)
    let $low-resource-deployed := exists(doc($target-coll || "/test-low.xml"))

    (: Cleanup — best effort; loop until repo:list no longer contains the package
       (both versions are now in the registry, so a single remove may not suffice). :)
    let $_cleanup :=
        for $attempt in 1 to 5
        where exists(repo:list()[. = $pkg-name])
        return (repo:undeploy($pkg-name), try { repo:remove($pkg-name) } catch * { () })

    return (
        $deployed-high/@result/string(),
        $deployed-low/@result/string(),
        $deployed-version,
        string($low-resource-deployed)
    )
};
