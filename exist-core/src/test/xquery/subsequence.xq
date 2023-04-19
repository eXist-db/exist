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

module namespace ss="http://exist-db.org/test/subsequence";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare
    %test:assertTrue
function ss:pipeline1() {
    ("x", "y") => subsequence(1, 1) => matches("x")
};

declare
    %test:args(1,1)
    %test:assertEmpty
    %test:args(3,1)
    %test:assertEmpty
    %test:args(5,1)
    %test:assertEmpty
    %test:args(1,2)
    %test:assertEquals(2)
    %test:args(3,2)
    %test:assertEquals(4)
    %test:args(5,2)
    %test:assertEmpty(4)
    %test:args(1,3)
    %test:assertEquals(2,3)
    %test:args(3,3)
    %test:assertEquals(4,5)
    %test:args(1,5)
    %test:assertEquals(2,3,4,5)
function ss:tail($start, $length) {
    tail(
        subsequence((1 to 5), $start, $length))
};
