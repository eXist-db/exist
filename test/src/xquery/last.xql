xquery version "3.0";

module namespace pt="http://exist-db.org/xquery/test/position";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare variable $pt:INMEM := 
    <nodes><test n="1"/>,<test/><test n="3"/></nodes>;

declare 
    %test:setUp
function pt:store() {
    xmldb:create-collection("/db", "position-test"),
    xmldb:store("/db/position-test", "test.xml", $pt:INMEM)
};

declare
    %test:tearDown
function pt:cleanup() {
    xmldb:remove("/db/position-test")
};

declare function pt:doc() {
    doc("/db/position-test/test.xml")/nodes
};

declare 
    %test:assertEquals("<test n='3'/>")
function pt:memory-last1() {
    $pt:INMEM//test[@n][last()]
};

declare 
    %test:assertEquals("<test n='3'/>")
function pt:memory-last2() {
    $pt:INMEM//test[@n][xs:integer(last())]
};

declare 
    %test:assertEquals("<test n='1'/>")
function pt:memory-last3() {
    $pt:INMEM//test[@n eq '3']/preceding-sibling::*[last()]
};

declare 
    %test:assertEquals("<test n='1'/>")
function pt:memory-last4() {
    $pt:INMEM//test[@n eq '3']/preceding-sibling::*[xs:integer(last())]
};

declare 
    %test:assertEquals("<test n='3'/>")
function pt:stored-last1() {
    pt:doc()//test[@n][last()]
};

declare 
    %test:assertEquals("<test n='3'/>")
function pt:stored-last2() {
    pt:doc()//test[@n][xs:integer(last())]
};

declare 
    %test:assertEquals("<test n='1'/>")
function pt:stored-last3() {
    pt:doc()//test[@n eq '3']/preceding-sibling::*[last()]
};

declare 
    %test:assertEquals("<test n='1'/>")
function pt:stored-last4() {
    pt:doc()//test[@n eq '3']/preceding-sibling::*[xs:integer(last())]
};

declare 
    %test:assertEquals("<test n='3'/>")
function pt:filtered-last() {
    let $test := pt:doc()//test[@n]
    return
        $test[last()]
};

declare 
    %test:assertEquals("<test/>")
function pt:filtered-last() {
    let $test := pt:doc()//test[@n]/preceding-sibling::*
    return
        $test[last()]
};