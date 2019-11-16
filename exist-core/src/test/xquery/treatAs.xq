xquery version "1.0";

module namespace ta="http://exist-db.org/test/treat-as";

declare namespace test="http://exist-db.org/xquery/xqsuite";


declare
    %test:assertEquals(4)
function ta:unary-expr() {
    3 treat as item()+ + +1
};
