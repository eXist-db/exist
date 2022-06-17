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
 : Tests for the "le" operator.
 :)
module namespace onle = "http://exist-db.org/test/op-numeric-less-than-or-equal";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

declare
    %test:args("1.13", "1.13")
    %test:assertTrue
function onle:numeric-less-than-or-equal-float-double($f as xs:float, $d as xs:double) {
    $f le $d
};

declare
    %test:args("1.13", "1.13")
    %test:assertFalse
function onle:numeric-less-than-or-equal-double-float($d as xs:double, $f as xs:float) {
    $d le $f
};

declare
    %test:args("1.13", "1.13")
    %test:assertTrue
function onle:numeric-less-than-or-equal-double-decimal($d as xs:double, $dec as xs:decimal) {
    $d le $dec
};

declare
    %test:args("1.13", "1.13")
    %test:assertTrue
function onle:numeric-less-than-or-equal-decimal-double($dec as xs:decimal, $d as xs:double) {
    $dec le $d
};

declare
    %test:args("1.13", "1.13")
    %test:assertTrue
function onle:numeric-less-than-or-equal-decimal-float($dec as xs:decimal, $f as xs:float) {
    $dec le $f
};

declare
    %test:args("1.13", "1.13")
    %test:assertTrue
function onle:numeric-less-than-or-equal-float-decimal($f as xs:float, $dec as xs:decimal) {
    $f le $dec
};
