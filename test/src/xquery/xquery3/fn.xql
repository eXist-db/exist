xquery version "3.0";

module namespace fn="http://exist-db.org/xquery/test/fnfunctions";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare
    %test:assertEquals(0, 4, 3, 2, 1)
function fn:fold-right() {
let $seq := (1,2,3,4)
return
    fold-right ($seq, (0) , function($item as xs:integer, $accu as xs:integer*) {
    ($accu, $item)
    })
};

