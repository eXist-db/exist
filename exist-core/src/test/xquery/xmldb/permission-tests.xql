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
        for-each(($collAccess, $collForbidden), xmldb:store(?, "test.xml", <test/>)),
        sm:chmod(xs:anyURI($collAccess), "rwxrwxrwx"),
        sm:chmod(xs:anyURI($collForbidden), "rwxrwxr--"),
        xmldb:store($t:collection, "hidden.xml", <hidden/>),
        sm:chmod(xs:anyURI($t:collection || "/hidden.xml"), "rw-------")
    )
};

declare
    %test:tearDown
function t:cleanup() {
    xmldb:remove($t:collection)
};

declare
    %test:user("guest", "guest")
    %test:assertFalse
function t:fnDocAvailableOnHiddenResource() {
    doc-available("/db/permission-test/hidden.xml")
};

declare
    %test:user("guest", "guest")
    %test:assertError
function t:fnDocOnHiddenResource() {
    doc("/db/permission-test/hidden.xml")//hidden
};

declare
    %test:user("guest", "guest")
    %test:assertEquals(0)
function t:fnCollectionOnHiddenResource() {
    count(collection("/db/permission-test")//hidden)
};

declare
    %test:user("guest", "guest")
    %test:assertFalse
function t:fnDocOnHiddenCollection() {
    doc-available("/db/permission-test/inaccessible/test.xml")
};

declare
    %test:user("guest", "guest")
    %test:assertError("Permission denied")
function t:fnCollectionOnHiddenCollection() {
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
function t:listResources($collection as xs:string) {
    let $target := $t:collection || "/" || $collection || "/"
    for $child in xmldb:get-child-resources($target)
    return
        sm:get-permissions(xs:anyURI($target || $child))
};

(:~
 : List and print child collections: throws exception even though
 : readable flag is set on collection "inaccessible".
 :)
declare
    %test:user("guest", "guest")
    %test:assertXPath("count($result) eq 1")
function t:listCollection() {
    for $child in xmldb:get-child-collections($t:collection)
    let $child-uri := xs:anyURI($t:collection || "/" || $child)
    return
        if (sm:has-access($child-uri, "rx"))
        then
            sm:get-permissions($child-uri)
        else ()
};

declare 
    %test:user("guest", "guest")
    %test:assertError("Permission denied|not found")
function t:copyCollection() {
    xmldb:copy-collection($t:collection || "/inaccessible", $t:collection || "/copy")
};

declare 
    %test:user("guest", "guest")
    %test:assertError("Permission denied|not found")
function t:moveCollection() {
    xmldb:move($t:collection || "/inaccessible", $t:collection || "/moved")
};

declare 
    %test:user("guest", "guest")
    %test:assertError("Permission denied|insufficient privileges")
function t:renameCollection() {
    xmldb:rename($t:collection || "/inaccessible", "renamed")
};

declare 
    %test:user("guest", "guest")
    %test:assertError("Permission denied|not allowed")
function t:removeCollection() {
    xmldb:remove($t:collection || "/inaccessible")
};
