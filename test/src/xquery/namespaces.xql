xquery version "3.0";

module namespace nt="http://exist-db.org/xquery/test/namespaces";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare 
    %test:assertEquals("<test xmlns:foo='http://foo.com'/>")
function nt:dynamicNSConstr1() {
    <test>
    { namespace foo { "http://foo.com" } }
    </test>
};

declare 
    %test:assertEquals("<test xmlns:foo='http://foo.com'>bla</test>")
function nt:dynamicNSConstr2() {
    element { "test" } {
        namespace foo { "http://foo.com" },
        "bla"
    }
};

declare 
    %test:assertEquals("<test xmlns:foo1='http://foo.com'/>")
function nt:dynamicNSConstr3() {
    <test>
    { namespace { "foo" || 1 } { "http://foo.com" } }
    </test>
};

declare 
    %test:assertEquals("<html xmlns:ev='http://www.w3.org/2001/xml-events'></html>")
function nt:dynamicNSConstr4() {
    let $xml :=
        <html xmlns:ev="http://www.w3.org/2001/xml-events"></html>
    return
        element { node-name($xml) } {
            nt:copy-ns($xml)
        }
};

declare %private function nt:copy-ns($node) {
    for $prefix in in-scope-prefixes($node)
    return
        namespace { $prefix } { namespace-uri-for-prefix($prefix, $node) } 
};

declare 
    %test:assertError
function nt:dynamicNSConstrError() {
    <test>
    { namespace { (1, 2) } { "http://foo.com" } }
    </test>
};
