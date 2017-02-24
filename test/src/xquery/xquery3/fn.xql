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

(: ERROR: It is a dynamic error if evaluation of an expression relies on some part of
          the dynamic context that has not been assigned a value. Context item is absent
declare
    %test:assertFalse
function fnt:has-children-contextItem-empty() {
    ()/has-children()
};
:)

declare
    %test:assertError("XPDY0002")
function fnt:has-children-contextItem-absent() {
    has-children()
};

(: fail with XPTY0019
declare
    %test:assertError("XPTY0004")
function fnt:has-children-contextItem-notNode() {
    "str1"/has-children()
};
:)

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

declare
    %test:assertEquals("b,e,h")
function fnt:innermost() {
    let $doc := document {
        <a>
            <b/>
            <c>
                <d/>
                <e/>
            </c>
            <f>
                <g>
                    <h/>
                </g>
            </f>
        </a>
    } return
        string-join(
            for $inner in fn:innermost(($doc/a/b, $doc/a/c, $doc/a/c/e, $doc/a/f, $doc/a/f/g/h, $doc/a/b))
            return
                local-name($inner)
            ,
            ","
        )
};

declare
    %test:assertEquals("b,c,f")
function fnt:outermost() {
    let $doc := document {
        <a>
            <b/>
            <c>
                <d/>
                <e/>
            </c>
            <f>
                <g>
                    <h/>
                </g>
            </f>
        </a>
    } return
        string-join(
            for $inner in fn:outermost(($doc/a/b, $doc/a/c, $doc/a/c/e, $doc/a/f, $doc/a/f/g/h, $doc/a/b))
            return
                local-name($inner)
            ,
            ","
        )
};