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

module namespace fi="http://exist-db.org/xquery/test/format-integer";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare
    %test:args("#0")
    %test:assertEmpty
    %test:args("#0.0")
    %test:assertEmpty
    %test:args("#0.00")
    %test:assertEmpty
    %test:args("#0.000")
    %test:assertEmpty
    %test:args("#0.0#")
    %test:assertEmpty
function fi:format-empty($picture as xs:string) {
    format-integer((), $picture)
};

declare
    %test:args("#0")
    %test:assertEquals("0")
    %test:args("#0.0")
    %test:assertEquals("0.0")
    %test:args("#0.00")
    %test:assertEquals("0.00")
    %test:args("#0.000")
    %test:assertEquals("0.000")
function fi:format-zeros($picture as xs:string) {
    format-integer(0, $picture)
};

declare
    %test:args("#0", 1)
    %test:assertEquals("1")
    %test:args("#00", 1)
    %test:assertEquals("01")
    %test:args("#0.00", 1)
    %test:assertEquals("0.01")
    %test:args("#0.000", 1)
    %test:assertEquals("0.001")
function fi:format-values($picture as xs:string, $value as xs:integer) {
    format-integer($value, $picture)
};

declare
    %test:args("#0#,555", 10000)
    %test:assertError("FODF1310")
    %test:args("#3||456", 1)
    %test:assertError("FODF1310")
    %test:args("|1", 1)
    %test:assertError("FODF1310")
    %test:args("1+", 1)
    %test:assertError("FODF1310")
    %test:args("1#", 1)
    %test:assertError("FODF1310")
    %test:args("##|3#|45", 1)
    %test:assertError("FODF1310")
    %test:args("12,#45", 1)
    %test:assertError("FODF1310")
function fi:invalid-pictures($picture as xs:string, $value as xs:integer) {
    format-integer($value, $picture)
};

declare
    %test:args("##|#3|456", 5)
    %test:assertEquals("0|005")
    %test:args("##|#3|45", 5)
    %test:assertEquals("0|05")
    %test:args("##|#3|45", 56789)
    %test:assertEquals("5|67|89")
function fi:separator-pictures($picture as xs:string, $value as xs:integer) {
    format-integer($value, $picture)
};

declare
    %test:args("#|##3|456", "-123456789")
    %test:assertEquals("-123|456|789")
    %test:args("#|##3|456", "-0")
    %test:assertEquals("0|000")
function fi:format-negatives($picture as xs:string, $value as xs:integer) {
    format-integer($value, $picture)
};

