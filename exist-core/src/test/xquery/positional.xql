xquery version "3.0";

(:~
 : Test various ways to specify a positional predicate.
 :)
module namespace pf="http://exist-db.org/xquery/test/positional-filter";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare variable $pf:XML := 
    <items>
       <item>
           <target>
               <id>uuid-538a6e13-f88b-462c-a965-f523c3e02bbf</id>
               <start>3</start>
               <offset>4</offset>
           </target>
       </item>
       <item>
           <target>
               <id>uuid-538a6e13-f88b-462c-a965-f523c3e02bbf</id>
               <start>8</start>
               <offset>15</offset>
           </target>
       </item>
    </items>;

declare
    %test:assertEquals(3, 8)
function pf:position-in-loop() {
    for $item at $pos in $pf:XML/item
    return
        $pf:XML/item[$pos]/target/start/number()
};

declare 
    %test:args(1)
    %test:assertEquals(8, 8)
    %test:args(2)
    %test:assertEquals(3, 3)
function pf:computed-position-minus($offset as xs:integer) {
    $pf:XML/*[3 - $offset]/target/start/number(),
    $pf:XML/item[3 - $offset]/target/start/number()
};

declare 
    %test:args(4)
    %test:assertEquals(8)
    %test:args(2)
    %test:assertEquals(3)
function pf:computed-position-division($offset as xs:integer) {
    $pf:XML/*[$offset idiv 2]/target/start/number()
};

declare 
    %test:args(3)
    %test:assertEquals(8)
    %test:args(2)
    %test:assertEquals(3)
function pf:computed-position-func-in-op($offset as xs:integer) {
    $pf:XML/*[$offset - abs(-1)]/target/start/number()
};

declare 
    %test:args(1)
    %test:assertEquals(3)
    %test:args(2)
    %test:assertEquals(8)
function pf:computed-position-func($offset as xs:integer) {
    $pf:XML/*[abs($offset)]/target/start/number()
};

declare 
    %test:args(3)
    %test:assertEquals(8)
    %test:args(2)
    %test:assertEquals(3)
function pf:computed-position-multi($offset as xs:integer) {
    $pf:XML/*[($offset - 1) * 1]/target/start/number()
};