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
xquery version "3.0";

module namespace swt = "http://exist-db.org/xquery/test/sliding-window";

declare namespace test = "http://exist-db.org/xquery/xqsuite";


declare
    %test:assertEquals("<window>2 4 6</window>", "<window>4 6 8</window>", "<window>6 8 10</window>", "<window>8 10 12</window>", "<window>10 12 14</window>")
function swt:windows-of-three-items() {
  for sliding window $w in (2, 4, 6, 8, 10, 12, 14)
  start at $s when fn:true()
  only end at $e when $e - $s eq 2
  return
    <window>{ $w }</window>
};

declare
    %test:assertEquals(4, 6, 8, 10, 12)
function swt:moving-averages-of-three-items() {
  for sliding window $w in (2, 4, 6, 8, 10, 12, 14)
  start at $s when fn:true()
  only end at $e when $e - $s eq 2
  return
    avg($w)
};

declare
    %test:assertEquals("<window>2 4 6</window>", "<window>4 6 8</window>", "<window>6 8 10</window>", "<window>8 10 12</window>", "<window>10 12 14</window>", "<window>12 14</window>", "<window>14</window>")
function swt:overlapping-windows-of-three-items() {
  for sliding window $w in (2, 4, 6, 8, 10, 12, 14)
  start at $s when fn:true()
  end at $e when $e - $s eq 2
  return
    <window>{ $w }</window>
};
