xquery version "3.0";

(:~ Additional tests for the fn:nill function :)
module namespace nill="http://exist-db.org/xquery/test/nill";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";


declare 
    %test:assertEquals("false")
function nill:nilfalse() {
    let $a := <a xsi:nil="false"></a>
    return
    nilled($a)
};

declare
%test:assertEquals("true")
    function nill:niltrue() {
    let $a := <a xsi:nil="true"></a>
    return
    nilled($a)
};

declare
%test:assertEquals("true")
function nill:empty() {
    let $a := ()
    return
      empty( nilled($a) )
};

declare
%test:assertEquals("true")
function nill:noelementnode() {
    let $a := <a b="c"/>
    
    return
    empty( nilled($a/@b) )
};