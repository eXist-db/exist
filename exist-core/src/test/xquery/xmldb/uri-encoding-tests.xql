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

module namespace t="http://exist-db.org/testsuite/xmldb-uri-encoding";

declare namespace test="http://exist-db.org/xquery/xqsuite";

(: ~
 : Regression tests for xmldb:decode / xmldb:decode-uri percent-decoding.
 : eXist-db/exist#1824 and #44: a '+' must be decoded as a literal plus sign (RFC 3986),
 : not turned into a space (which is application/x-www-form-urlencoded behavior).
 :)

declare
    %test:assertEquals("a+b")
function t:decode-uri-plus-is-literal-when-encoded() {
    xmldb:decode-uri(xs:anyURI("a%2Bb"))
};

declare
    %test:assertEquals("a+b")
function t:decode-uri-bare-plus-is-literal() {
    xmldb:decode-uri(xs:anyURI("a+b"))
};

declare
    %test:assertEquals("a+b")
function t:decode-plus-is-literal-when-encoded() {
    xmldb:decode("a%2Bb")
};

declare
    %test:assertEquals("a b")
function t:decode-uri-percent-20-is-space() {
    xmldb:decode-uri(xs:anyURI("a%20b"))
};

declare
    %test:assertEquals("My Report (2024)+final.xml")
function t:decode-uri-mixed() {
    xmldb:decode-uri(xs:anyURI("My%20Report%20%282024%29%2Bfinal.xml"))
};
