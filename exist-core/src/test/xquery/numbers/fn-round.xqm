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

module namespace fr="http://exist-db.org/xquery/test/fn-round";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare
    %test:args("2.3")
    %test:assertEquals("2.0")
    %test:args("2.5")
    %test:assertEquals("3.0")
    %test:args("-2.5")
    %test:assertEquals("-2.0")
    %test:args("-2.51")
    %test:assertEquals("-3.0")
function fr:round-double($number as xs:double) {
    fn:round($number)
};

declare
    %test:args(2.3, 1)
    %test:assertEquals(2.3)
    %test:args(2.5, 1)
    %test:assertEquals(2.5)
    %test:args("-2.5", 1)
    %test:assertEquals("-2.5")
    %test:args("-2.51", 1)
    %test:assertEquals("-2.5")
    %test:args("-2.51", 2)
    %test:assertEquals("-2.51")
function fr:round-double-precision($number as xs:double, $precision as xs:integer) {
    fn:round($number, $precision)
};

declare
    %test:args("2.5")
    %test:assertEquals("3.0")
    %test:args("2.4999")
    %test:assertEquals("2.0")
    %test:args("-2.5")
    %test:assertEquals("-2.0")
function fr:round-specification-examples-1($number as xs:double) {
    fn:round($number)
};

declare
    %test:args("1.125", 2)
    %test:assertEquals("1.13")
function fr:round-specification-examples-2-decimal($number as xs:decimal, $precision as xs:integer) {
    fn:round($number, $precision)
};

declare
    %test:args("1.125", 2)
    %test:assertEquals("1.13")
    %test:args("1.125", 2)
    %test:assertEquals("1.13")
    %test:args("8452", "-2")
    %test:assertEquals("8500")
    %test:args("3.1415e0", "2")
    %test:assertEquals("3.14e0")
function fr:round-specification-examples-2($number as xs:double, $precision as xs:integer) {
    fn:round($number, $precision)
};

declare
    %test:args("1.125", 2)
    %test:assertEquals("1.12")
    %test:args("1.135", 2)
    %test:assertEquals("1.14")
    %test:args("-1.125", 2)
    %test:assertEquals("-1.12")
    %test:args("-1.135", 2)
    %test:assertEquals("-1.14")
    %test:args(3.567812e+3, 2)
    %test:assertEquals(3567.81e0)
    %test:args(4.7564e-3, 2)
    %test:assertEquals(0.0e0)
    %test:args(35612.25, "-2")
    %test:assertEquals(35600)
function fr:round-half-to-even-precision($number as xs:double, $precision as xs:integer) {
    fn:round-half-to-even($number, $precision)
};

declare
    %test:args("0.5")
    %test:assertEquals(0)
    %test:args("1.5")
    %test:assertEquals(2.0)
    %test:args("2.5")
    %test:assertEquals(2.0)
function fr:round-half-to-even($number as xs:double) {
    fn:round-half-to-even($number)
};

declare
    %test:args("-0.41",1)
    %test:assertEquals("-0.4")
    %test:args("-0.41",0)
    %test:assertEquals("-0")
function fr:round-negative-zero($number as xs:decimal, $precision as xs:integer) {
    fn:round($number, $precision)
};


declare
    %test:args("-0.41",1)
    %test:assertEquals("-0.4")
    %test:args("-0.41",0)
    %test:assertEquals("-0")
function fr:round-negative-zero-double($number as xs:double, $precision as xs:integer) {
    fn:round($number, $precision)
};