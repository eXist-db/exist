xquery version "3.0";

module namespace fnt="http://exist-db.org/xquery/test/fnfunctions";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace xpf = "http://www.w3.org/2005/xpath-functions";

declare
    %test:assertEquals(0, 4, 3, 2, 1)
function fnt:fold-right() {
let $seq := (1,2,3,4)
return
    fold-right ($seq, (0) , function($item as xs:integer, $accu as xs:integer*) {
    ($accu, $item)
    })
};

declare
    %test:args('test', 't e s t', '') %test:assertFalse
    %test:args('test', 't e s t', 'x') %test:assertTrue
function fnt:analyze-matches($str, $pat, $flags) {
	exists(analyze-string($str, $pat, $flags)/xpf:match)
};

declare
    %test:assertTrue
function fnt:has-children-contextItem() {
    <a><b/></a>/has-children()
};

declare
    %test:assertFalse
function fnt:has-children-contextItem-noChildren() {
    <a/>/has-children()
};

declare
    %test:assertFalse
function fnt:has-children-contextItem-empty() {
    ()/has-children()
};

declare
    %test:assertError("XPDY0002")
function fnt:has-children-contextItem-absent() {
    has-children()
};

declare
    %test:assertError("XPTY0004")
function fnt:has-children-contextItem-notNode() {
    "str1"/has-children()
};

declare
    %test:assertTrue
function fnt:has-children() {
    has-children(<a><b/></a>)
};

declare
    %test:assertFalse
function fnt:has-children-noChildren() {
   has-children(<a/>)
};

declare
    %test:assertFalse
function fnt:has-children-empty() {
    has-children(())
};
