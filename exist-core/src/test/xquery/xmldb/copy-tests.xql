xquery version "3.0";

module namespace t="http://exist-db.org/testsuite/copy";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare variable $t:collection-name := "copy-test";
declare variable $t:collection := "/db/" || $t:collection-name;
declare variable $t:target-collection-name := "copy-target";
declare variable $t:target-collection := "/db/" || $t:target-collection-name;

declare
    %test:setUp
function t:setup() {
    xmldb:create-collection("/db", $t:collection-name),
    xmldb:create-collection("/db", $t:target-collection-name),
    xmldb:store($t:collection, "test.xml", <test/>),
    sm:chmod(xs:anyURI($t:collection || "/test.xml"), "rw-r-----")
};

declare
    %test:tearDown
function t:cleanup() {
    xmldb:remove($t:collection),
    xmldb:remove($t:target-collection)
};

declare
    %test:assertEquals("/db/copy-test/copy.xml", "<test/>")
function t:copy-resource-same-collection() {
    let $copy := xmldb:copy-resource($t:collection, "test.xml", $t:collection, "copy.xml")
    return (
        $copy,
        doc($copy)
    )
};

declare
    %test:assertEquals("/db/copy-target/copy.xml", "<test/>")
function t:copy-resource-different-collection() {
    let $copy := xmldb:copy-resource($t:collection, "test.xml", $t:target-collection, "copy.xml")
    return (
        $copy,
        doc($copy)
    )
};

declare
    %test:assertTrue
function t:copy-resource-preserve-last-modified() {
    let $lastModified := xmldb:last-modified($t:collection, "test.xml")
    let $copy := xmldb:copy-resource($t:collection, "test.xml", $t:target-collection, "preserve.xml", true())
    return (
        $lastModified = xmldb:last-modified($t:target-collection, "preserve.xml")
    )
};

declare
    %test:assertTrue
function t:copy-resource-preserve-permissions() {
    let $perms := sm:get-permissions(xs:anyURI($t:collection || "/test.xml"))/sm:permission
    let $copy := xmldb:copy-resource($t:collection, "test.xml", $t:target-collection, "preserve.xml", true())
    return (
        deep-equal($perms, sm:get-permissions(xs:anyURI($t:target-collection || "/preserve.xml"))/sm:permission)
    )
};

declare
    %test:assertEquals("/db/copy-target/copy-test", "test")
function t:copy-collection() {
    let $copy := xmldb:copy-collection($t:collection, $t:target-collection)
    return (
        $copy,
        doc($t:target-collection || "/" || $t:collection-name || "/test.xml")/*/local-name(.),
        xmldb:remove($t:target-collection || "/" || $t:collection-name)
    )
};

declare
    %test:assertEquals("/db/copy-target/copy-test", "test", "true")
function t:copy-collection-preserve() {
    let $perms := sm:get-permissions(xs:anyURI($t:collection))/sm:permission
    let $copy := xmldb:copy-collection($t:collection, $t:target-collection, true())
    return (
        $copy,
        doc($t:target-collection || "/" || $t:collection-name || "/test.xml")/*/local-name(.),
        deep-equal($perms, sm:get-permissions(xs:anyURI($t:target-collection || "/" || $t:collection-name))/sm:permission),
        xmldb:remove($t:target-collection || "/" || $t:collection-name)
    )
};

declare
    %test:assertError
function t:copy-collection-on-itself() {
    xmldb:copy-collection($t:collection, $t:collection)
};