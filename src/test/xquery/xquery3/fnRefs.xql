xquery version "3.0";

module namespace fnRefs="http://exist-db.org/xquery/test/function_reference";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace xpf = "http://www.w3.org/2005/xpath-functions";

declare
    %test:assertEquals("/db")
function fnRefs:castFunctionReference() {
    let $as-uri := xs:anyURI(?)
    return
        $as-uri("/db")
};