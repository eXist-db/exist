xquery version "3.0";

module namespace t="http://exist-db.org/testsuite/permissions";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare variable $t:collection-name := "permission-test";
declare variable $t:collection := "/db/" || $t:collection-name;

declare 
    %test:setUp
function t:setup() {
    xmldb:create-collection("/db", "permission-test"),
    let $collAccess := xmldb:create-collection($t:collection, "accessible")
    let $collForbidden := xmldb:create-collection($t:collection, "inaccessible")
    return (
        map(xmldb:store(?, "test.xml", <test/>), ($collAccess, $collForbidden)),
        sm:chmod(xs:anyURI($collAccess), "rwxrwxrwx"),
        sm:chmod(xs:anyURI($collForbidden), "rwxrwxr--")
    )
};

declare
    %test:user("guest", "guest")
    %test:assertFalse
function t:readHiddenResource() {
    doc-available("/db/permission-test/inaccessible/test.xml")
};

declare
    %test:user("guest", "guest")
    %test:assertError("Permission denied")
function t:readHiddenCollection() {
    collection("/db/permission-test/inaccessible")/*
};

(:~
 : Try to list resources in collection without execute permission.
 : Should not be allowed.
 :)
declare
    %test:user("guest", "guest")
    %test:assertError("Permission denied")
function t:listProtectedCollection() {
    xmldb:get-child-resources($t:collection || "/inaccessible")
};

(:~
 : List child resources in collections with executable flag set and
 : unset. Second case should generate error.
 :)
declare 
    %test:user("guest", "guest")
    %test:args("accessible")
    %test:assertXPath("count($output) = 1")
    %test:args("inaccessible")
    %test:assertError("Permission denied")
function t:testListResources($collection as xs:string) {
    let $target := $t:collection || "/" || $collection || "/"
    for $child in xmldb:get-child-resources($target)
    return
        sm:get-permissions(xs:anyURI($target || $child))
};

(:~
 : Fail: list and print child collections: throws exception even though
 : readable flag is set on collection "inaccessible".
 :)
declare
    %test:user("guest", "guest")
    %test:assertXPath("count($output) = 2")
function t:testListCollection() {
    for $child in xmldb:get-child-collections($t:collection)
    return
        sm:get-permissions(xs:anyURI($t:collection || "/" || $child))
};