(:
 : eXist-db Open Source Native XML Database
 : Copyright (C) 2001 The eXist-db Authors
 :
 : info@exist-db.org
 : http://www.exist-db.org
 :
 : This library is free software; you can redistribute it and/or
 : modify it under the terms of the GNU Lesser General Public
 : License as published by the Free Software Foundation; either
 : version 2.1 of the License, or (at your option) any later version.
 :
 : This library is distributed in the hope that it will be useful,
 : but WITHOUT ANY WARRANTY; without even the implied warranty of
 : MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 : Lesser General Public License for more details.
 :
 : You should have received a copy of the GNU Lesser General Public
 : License along with this library; if not, write to the Free Software
 : Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 :)
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
    %test:assertError("XPTY0019")
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
            for $outer in fn:outermost(($doc/a/b, $doc/a/c, $doc/a/c/e, $doc/a/f, $doc/a/f/g/h, $doc/a/b))
            return
                local-name($outer)
            ,
            ","
        )
};

declare
      %test:args('red green blue ', 'red')          %test:assertTrue
      %test:args("red, green, blue", ' red ')       %test:assertFalse
      %test:args('', 'red')                         %test:assertFalse
      %test:args('red green blue', '')              %test:assertFalse
      %test:args('red green blue', ' ')             %test:assertFalse
      %test:args('red green blue ', 'RED ')         %test:assertFalse
function fnt:contains-token-tests($input, $token) {
    contains-token($input, $token)
};

declare
      %test:arg("input", "red", "green", "blue")
      %test:arg("token", ' red ')
      %test:assertTrue
function fnt:contains-token-tests-input-sequence($input, $token) {
    contains-token($input, $token)
};

declare
      %test:arg("input")
      %test:arg("token", ' red ')
      %test:assertFalse
function fnt:contains-token-tests-empty-input-sequence($input, $token) {
    contains-token($input, $token)
};

declare
     %test:args('red green blue', 'RED', "http://www.w3.org/2005/xpath-functions/collation/html-ascii-case-insensitive")
     %test:assertTrue
function fnt:contains-token-tests-collation($input, $token, $collation) {
    contains-token($input, $token, $collation)
};
