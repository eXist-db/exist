xquery version "3.0";

module namespace fn="http://exist-db.org/xquery/test/fnfunctions";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace xpf = "http://www.w3.org/2005/xpath-functions";

declare
    %test:assertEquals(0, 4, 3, 2, 1)
function fn:fold-right() {
let $seq := (1,2,3,4)
return
    fold-right ($seq, (0) , function($item as xs:integer, $accu as xs:integer*) {
    ($accu, $item)
    })
};

declare
    %test:args('test', 't e s t', '') %test:assertFalse
    %test:args('test', 't e s t', 'x') %test:assertTrue
function fn:analyze-matches($str, $pat, $flags) {
	exists(analyze-string($str, $pat, $flags)/xpf:match)
};
