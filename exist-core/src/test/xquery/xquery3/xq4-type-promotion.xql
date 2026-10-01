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
xquery version "4.0";

module namespace tp="http://exist-db.org/xquery/test/type-promotion";

declare namespace test="http://exist-db.org/xquery/xqsuite";

(: Helper function that expects xs:anyURI :)
declare function tp:expect-uri($uri as xs:anyURI) as xs:string {
    string($uri)
};

(: Helper function that expects xs:base64Binary :)
declare function tp:expect-base64($val as xs:base64Binary) as xs:string {
    string($val)
};

(: Helper function that expects xs:hexBinary :)
declare function tp:expect-hex($val as xs:hexBinary) as xs:string {
    string($val)
};

(: Helper function that expects xs:decimal :)
declare function tp:expect-decimal($val as xs:decimal) as xs:decimal {
    $val
};

(: === xs:string -> xs:anyURI implicit casting === :)

declare
    %test:assertEquals("http://example.com")
function tp:string-to-anyuri-coercion() {
    tp:expect-uri("http://example.com")
};

declare
    %test:assertEquals("")
function tp:empty-string-to-anyuri() {
    tp:expect-uri("")
};

(: === xs:hexBinary <-> xs:base64Binary implicit casting === :)

declare
    %test:assertEquals("AQID")
function tp:hex-to-base64-coercion() {
    tp:expect-base64(xs:hexBinary("010203"))
};

declare
    %test:assertEquals("010203")
function tp:base64-to-hex-coercion() {
    tp:expect-hex(xs:base64Binary("AQID"))
};

(: === Bidirectional numeric implicit casting === :)

declare
    %test:assertEquals(3.14)
function tp:double-to-decimal-coercion() {
    tp:expect-decimal(3.14e0)
};

(: === Relabeling: derived atomic types (§3.4.1 item 6) === :)

(: Helper function that expects xs:positiveInteger :)
declare function tp:expect-positive-integer($val as xs:positiveInteger) as xs:integer {
    $val
};

declare
    %test:assertEquals(42)
function tp:integer-to-positive-integer-relabeling() {
    tp:expect-positive-integer(42)
};

declare
    %test:assertError("XPTY0004")
function tp:negative-integer-to-positive-integer-fails() {
    tp:expect-positive-integer(-5)
};

(: === Version gating: ensure coercion only works in XQ4 === :)
(: These tests run in this 4.0 module, so they should pass :)

declare
    %test:assertTrue
function tp:string-to-anyuri-instance-check() {
    let $result := tp:expect-uri("test")
    return $result instance of xs:string
};
