xquery version "3.1";

module namespace ut = "http://exist-db.org/xquery/test/util-eval-with-context";
 
declare namespace test = "http://exist-db.org/xquery/xqsuite";

declare 
    %test:assertError("exerr:ERROR")
function ut:timeout() {
    let $context :=
        <static-context>
            <timeout value="1"/>
        </static-context>
    return
        util:eval-with-context("util:wait(2), <ok/>", $context, false() )
};