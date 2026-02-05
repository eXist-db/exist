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
 : Some tests on features of the test suite itself.
 :)
module namespace t="http://exist-db.org/xquery/test/xqsuite";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare
    %test:assertXPath("/name[. = 'Item1']")
function t:xpath() {
    <item>
        <name>Item1</name>
    </item>
};

declare 
    %test:assertXPath("/t:name[. = 'Item1']")
function t:xpath-with-namespace() {
    <t:item>
        <t:name>Item1</t:name>
    </t:item>
};

declare 
    %test:assertXPath("/t:name/x:id[. = 'abc']")
function t:xpath-with-different-namespaces() {
    <t:item>
        <t:name><x:id xmlns:x="http://test.com/x">abc</x:id></t:name>
    </t:item>
};

declare 
    %test:assertXPath("declare namespace f='http://foo.com'; $result//f:name[. = 'Item1']")
function t:xpath-with-default-namespace() {
    <item xmlns="http://foo.com">
        <name>Item1</name>
    </item>
};

declare 
    %test:assertXPath("string-length($result) = 5")
function t:xpath-atomic-value() {
    "Hello"
};

declare
    %test:args("-0")
    %test:assertEquals("-0")
    %test:args(0)
    %test:assertEquals(0)
    %test:args(0.0)
    %test:assertEquals(0.0)
    %test:args(0e0)
    %test:assertEquals(0e0)
function t:args-assert-item($arg) {
    $arg
};

declare
    %test:args("-0")
    %test:assertEquals("-0")
    %test:args(0)
    %test:assertEquals(0)
    %test:args(0.0)
    %test:assertEquals(0.0)
    %test:args(0e0)
    %test:assertEquals(0e0)
function t:args-assert-numeric($arg as xs:numeric) as xs:numeric {
    $arg
};

declare
    %test:args("-0")
    %test:assertEquals("-0")
    %test:args(0)
    %test:assertEquals(0)
    %test:args(0.0)
    %test:assertEquals(0.0)
    %test:args(0e0)
    %test:assertEquals(0e0)
function t:args-assert-double($arg as xs:double) as xs:double {
    $arg
};

declare
    %test:args("-0")
    %test:assertEquals("-0")
    %test:args(0)
    %test:assertEquals(0)
    %test:args(0.0)
    %test:assertEquals(0.0)
    %test:args(0e0)
    %test:assertEquals(0e0)
function t:args-assert-integer($arg as xs:integer) as xs:integer {
    $arg
};

declare
    %test:args("-1")
    %test:assertEquals("-1")
function t:args-assert-negative-integer($arg as xs:negativeInteger) as xs:negativeInteger {
    $arg
};

declare
    %test:args(1)
    %test:assertEquals(1)
function t:args-assert-positive-integer($arg as xs:positiveInteger) as xs:positiveInteger {
    $arg
};

declare
    %test:args("true")
    %test:assertTrue
    %test:args("false")
    %test:assertFalse
    %test:args("true")
    %test:assertEquals("true")
    %test:args("false")
    %test:assertEquals("false")
    %test:args("1")
    %test:assertEquals("true")
    %test:args("0")
    %test:assertEquals("false")
    %test:args("1")
    %test:assertTrue
    %test:args("0")
    %test:assertFalse
    %test:args(1)
    %test:assertTrue
    %test:args(0)
    %test:assertFalse
function t:args-assert-boolean($arg as xs:boolean) as xs:boolean {
    $arg
};

declare
    %test:args("uri/like")
    %test:assertEquals("uri/like")
function t:args-assert-anyURI($arg as xs:anyURI) as xs:anyURI {
    $arg
};

declare
    %test:args("asdf")
    %test:assertEquals("asdf")
function t:args-assert-ncname($arg as xs:NCName) as xs:NCName {
    $arg
};

declare
    %test:args("test:asdf")
    %test:assertEquals("test:asdf")
function t:args-assert-qname($arg as xs:QName) as xs:QName {
    $arg
};

declare
    %test:args("2001-01-01")
    %test:assertEquals("2001-01-01")
function t:args-assert-date($arg as xs:date) as xs:date {
    $arg
};

declare
    %test:args("00:00:00.000")
    %test:assertEquals("00:00:00.000")
function t:args-assert-time($arg as xs:time) as xs:time {
    $arg
};

declare
    %test:args("2001-01-01T01:01:01.001")
    %test:assertEquals("2001-01-01T01:01:01.001")
function t:args-assert-dateTime($arg as xs:dateTime) as xs:dateTime {
    $arg
};

declare
    %test:args("2001-01-01T01:01:01.001Z")
    %test:assertEquals("2001-01-01T01:01:01.001Z")
function t:args-assert-dateTimeStamp($arg as xs:dateTimeStamp) as xs:dateTimeStamp {
    $arg
};

declare
    %test:args("P1Y1M1DT1H")
    %test:assertEquals("P1Y1M1DT1H")
function t:args-assert-duration($arg as xs:duration) as xs:duration {
    $arg
};

declare
    %test:args("P1Y1M")
    %test:assertEquals("P1Y1M")
function t:args-assert-yearMonthDuration($arg as xs:yearMonthDuration) as xs:yearMonthDuration {
    $arg
};

declare
    %test:args("P1DT1H")
    %test:assertEquals("P1DT1H")
function t:args-assert-dayTimeDuration($arg as xs:dayTimeDuration) as xs:dayTimeDuration {
    $arg
};

declare
    %test:args("-0")
    %test:assertEquals("-0")
    %test:args(0)
    %test:assertEquals(0)
    %test:args(0.0)
    %test:assertEquals(0.0)
    %test:args(0e0)
    %test:assertEquals(0e0)
function t:args-assert-text($arg as text()) as text() {
    $arg
};

declare
    %test:args("<test />")
    %test:assertEquals("<test />")
function t:args-assert-element($arg as element()) as element() {
    $arg
};
