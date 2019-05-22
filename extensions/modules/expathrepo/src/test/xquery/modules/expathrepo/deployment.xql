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
        <dependency package="http://exist-db.org/apps/shared" semver-min="0.4.0"/>
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