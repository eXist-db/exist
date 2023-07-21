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

module namespace cc="http://exist-db.org/xquery/test/cast-constructor";

declare namespace test="http://exist-db.org/xquery/xqsuite";

(:~
  check if CastExpression#toFunction has correct cardinality check
  see https://github.com/eXist-db/exist/issues/4971
:)
declare
    %test:assertEmpty
function cc:after-arrow-empty-sequence () {
    () => xs:string()
};

declare
    %test:assertEquals("1")
function cc:after-arrow-integer () {
    1 => xs:string()
};

declare
    %test:assertError("XPTY0004")
function cc:after-arrow-integer-sequence () {
    (1,2) => xs:string()
};

declare
    %test:assertEquals("test")
function cc:atomize-element () as xs:string {
    xs:string(<div>test</div>)
};

declare
    %test:assertEquals("1970-01-01")
function cc:atomize-attribute () as xs:date {
    xs:date(attribute when { "1970-01-01" })
};

declare
    %test:assertEquals(1)
function cc:atomize-array () as xs:integer {
    xs:integer([1])
};

declare
    %test:assertError("XPTY0004")
function cc:atomize-array-2 () as xs:string {
    xs:string([1,2])
};

declare
    %test:assertError("FOTY0013")
function cc:atomize-map-fails () {
    xs:integer(map { 0 : 1 })
};
