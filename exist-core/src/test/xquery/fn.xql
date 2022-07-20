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
xquery version "3.1";

(:~
 : Test various F+O functions
 :)
module namespace fnt="http://exist-db.org/xquery/test/fn";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare variable $fnt:composed := '&#x00C5;&#x0073;&#x0074;&#x0072;&#x00F6;&#x006D;';
declare variable $fnt:decomposed := '&#x0041;&#x030A;&#x0073;&#x0074;&#x0072;&#x006F;&#x0308;&#x006D;';

declare
    %test:setUp
function fnt:store() {
    let $col := xmldb:create-collection("/db", "fn-test")
    return
        (
            xmldb:store($col, "test.xml", <books><book/></books>),
            xmldb:store($col, "test.bin", "some binary", "application/octet-stream")
        )
};

declare
    %test:tearDown
function fnt:cleanup() {
    xmldb:remove("/db/fn-test")
};

declare
    %test:args("NFC")
    %test:assertEquals(6, 6)
    %test:args("NFD")
    %test:assertEquals(8, 8)
    %test:args("NFKD")
    %test:assertEquals(8, 8)
    %test:args("NFKC")
    %test:assertEquals(6, 6)
    %test:args("XYZ")
    %test:assertError
function fnt:normalize-unicode($normalization-form as xs:string) {
    count(string-to-codepoints(normalize-unicode($fnt:composed, $normalization-form))),
    count(string-to-codepoints(normalize-unicode($fnt:decomposed, $normalization-form)))
};

declare
    %test:args("test.xml")
    %test:assertEquals("true")
    %test:args("test.bin")
    %test:assertEquals("false")
    %test:args("no-such-file.xml")
    %test:assertEquals("false")
    %test:args("no-such-file.bin")
    %test:assertEquals("false")
function fnt:doc-available($filename as xs:string) {
    fn:doc-available("/db/fn-test/" || $filename)
};

(:
  Intentionally marked as pending as it has
  an external dependency
:)
declare
    %test:pending
    %test:args("https://www.kingjamesbibleonline.org/Genesis-Chapter-1_Original-1611-KJV/")
    %test:assertEquals("false")
function fnt:doc-available-remote($uri as xs:string) {
    fn:doc-available($uri)
};

declare
    %test:args("\adamretter")
    %test:assertError("FODC0005")
function fnt:doc-available-invalid-uri($uri as xs:string) {
    fn:doc-available($uri)
};

declare
    %test:args("test.xml")
    %test:assertEquals("true")
function fnt:doc-returns-document-node($filename as xs:string) {
    fn:doc("/db/fn-test/" || $filename) instance of document-node()
};

declare
    %test:args("test.xml")
    %test:assertEmpty
function fnt:doc-does-not-return-element-node($filename as xs:string) {
    fn:doc("/db/fn-test/" || $filename)/book
};

declare
    %test:assertEquals(0, 0)
function fnt:tokenize-empty() {
     count(() => tokenize("\s")),
     count(tokenize((), "\s"))
};

declare
    %test:args("hello adam")
    %test:assertEquals("hello", "adam")
    %test:args("hello   adam")
    %test:assertEquals("hello", "adam")
function fnt:tokenize-onearg($str as xs:string) {
     tokenize($str)
};

declare
    %test:assertError("err:XPDY0002")
function fnt:document-uri0_noarg() {
     document-uri()
};

declare
    %test:assertError("err:XPDY0002")
function fnt:document-uri0() {
     document-uri(.)
};

declare
    %test:assertEquals("/db/fn-test/test.xml")
function fnt:document-uri0_context() {
     root(collection('/db/fn-test')//book)/document-uri()
};

declare
    %test:assertEmpty
function fnt:document-uri0_context_empty() {
     root(collection('/db/fn-test')//bookies)/document-uri()
};

declare
    %test:assertEquals("/db/fn-test/test.xml")
function fnt:document-uri1() {
     document-uri(root(collection('/db/fn-test')//book))
};

declare
    %test:assertEmpty
function fnt:document-uri1_empty() {
     document-uri(root(collection('/db/fn-test')//bookies))
};

declare
    %test:assertTrue
function fnt:fn-document-uri-32() {
     let $uri := "/db/fn-test/test.xml"
     return
         fn:contains(fn:doc($uri)/document-uri(), $uri)
};

declare
    %test:assertError("err:XPTY0004")
function fnt:document-uri0_no_node_context() {
     util:eval-with-context("document-uri()", (), false(), "a")
};
