xquery version "3.0";

(:~
 : Tests for the Inspect module
 :)
module namespace insp = "http://exist-db.org/test/insp";

import module namespace test = "http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";
import module namespace inspect = "http://exist-db.org/xquery/inspection";

declare
    %test:setUp
function insp:setup() {
    let $coll := xmldb:create-collection("/db", "inspect-test")
    return (
        xmldb:store($coll, "mod1.xqy", "<main>{current-dateTime()}</main>", "application/xquery"),
        xmldb:store($coll, "mod1.xqm", "module namespace x = ""http://x.com""; declare function x:y() { <library>{current-dateTime()}</library> };", "application/xquery")
    )
};

declare
    %test:tearDown
function insp:cleanup() {
    xmldb:remove("/db/inspect-test")
};

declare
    %test:assertEquals(0)
function insp:module-functions-main-module() {
    count(inspect:inspect-module(xs:anyURI("xmldb:exist:///db/inspect-test/mod1.xqy")))
};

declare
    %test:assertEquals(1)
function insp:module-functions-library-module() {
    count(inspect:inspect-module(xs:anyURI("xmldb:exist:///db/inspect-test/mod1.xqm")))
};

(: When there is an issue, the issue is printed as part of the error messages:)
declare
    %test:assertEquals("END")
function insp:check_if_prefix_is_present() {
    (
        for $namespace-uri in util:registered-modules()
        let $functions := try { util:registered-functions($namespace-uri) } catch * { <error>{$err:code} raised when getting registered functions for module {$namespace-uri}</error> }
        for $fie in $functions
        return
            if( contains($fie,":") or contains($namespace-uri, "w3.org") )
            then   () else $fie || " in " || $namespace-uri
    ,"END")
};

