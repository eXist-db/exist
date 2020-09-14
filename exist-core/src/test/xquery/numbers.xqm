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

module namespace nums = "http://exist-db.org/xquery/test/numbers";

declare namespace test="http://exist-db.org/xquery/xqsuite";


declare
    %test:args(10000000000000001,  10000000000000002)
    %test:assertFalse
    %test:args(100000000000000001, 100000000000000002)
    %test:assertFalse
    %test:args(100000000000000010, 100000000000000020)
    %test:assertFalse
    %test:args(100000000000000100, 100000000000000200)
    %test:assertFalse
    %test:args(310000000000920000300, 310000000000920000200)
    %test:assertFalse
function nums:compare-eq($a, $b) {
    $a eq $b
};

declare
  %test:assertEquals(
      310000000000920000200,
      310000000000920000300,
      310000000000930001700,
      310000000000930001800,
      310000000000930002800,
      310000000000930003800,
      310000000000930003900,
      310000000000930004900,
      310000000000970000300,
      310000000000970000400
  )
function nums:order() {
  let $data := (
    310000000000920000300,
    310000000000930001800,
    310000000000930003900,
    310000000000970000400,
    310000000000930002800,
    310000000000920000200,
    310000000000930001700,
    310000000000930004900,
    310000000000930003800,
    310000000000970000300
  )
  return
    for $i in $data
    order by $i
    return $i
};
