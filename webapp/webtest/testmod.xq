xquery version "1.0";
(: $Id$ :)

module namespace testmod="http://exist-db.org/xquery/testmod";

declare function testmod:hello-world() as element() {
    <h1>PASS</h1>
};