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

module namespace testTransform="http://exist-db.org/xquery/test/function_transform";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $testTransform:works := document {
 <works>
  <employee name="Jane Doe 1" gender="female">
   <empnum>E1</empnum>
   <pnum>P1</pnum>
   <hours>40</hours>
  </employee>
  <employee name = "John Doe 2" gender="male">
   <empnum>E1</empnum>
   <pnum>P2</pnum>
   <hours>70</hours>
   <hours>20</hours>Text data from Employee[2]
  </employee>
  <employee name = "Jane Doe 3" gender="female">
   <empnum>E1</empnum>
   <pnum>P3</pnum>
   <hours>80</hours>
  </employee>
  <employee name= "John Doe 4" gender="male">
   <empnum>E1</empnum>
   <pnum>P4</pnum>
   <hours>20</hours>
   <hours>40</hours>
  </employee>
  <employee name= "Jane Doe 5" gender="female">
   <empnum>E1</empnum>
   <pnum>P5</pnum>
   <hours>20</hours>
   <hours>30</hours>
  </employee>
  <employee name= "John Doe 6" gender="male">
   <empnum>E1</empnum>
   <pnum>P6</pnum>
   <hours>12</hours>
  </employee>
  <employee name= "Jane Doe 7" gender="female">
   <empnum>E2</empnum>
   <pnum>P1</pnum>
   <hours>40</hours>
  </employee>
  <employee name= "John Doe 8" gender="male">
   <empnum>E2</empnum>
   <pnum>P2</pnum>
   <hours>80</hours>
  </employee>
  <employee name= "Jane Doe 9" gender="female">
   <empnum>E3</empnum>
   <pnum>P2</pnum>
   <hours>20</hours>
  </employee>
  <employee name= "John Doe 10" gender="male">
   <empnum>E3</empnum>
   <pnum>P2</pnum>
   <hours>20</hours>
  </employee>
  <employee name= "Jane Doe 11" gender="female">
   <empnum>E4</empnum>
   <pnum>P2</pnum>
   <hours>20</hours>
  </employee>
  <employee name= "John Doe 12" gender="male">
   <empnum>E4</empnum>
   <pnum>P4</pnum>
   <hours>40</hours>
   <overtime>
     <day>Monday</day>
     <day>Tuesday</day>
   </overtime>
  </employee>
  <employee name= "Jane Doe 13" gender="female" type="FT">
   <empnum>E4</empnum>
   <pnum>P5</pnum>
   <hours>80</hours>
   <status>active</status>
  </employee>
 </works> };

declare
    %test:assertContains("fn/transform/staticbaseuri.xsl")
function testTransform:transform-25-xsl() {
    let $result := fn:transform(map {"stylesheet-location" : "transform/render.xsl",
    "source-node" : $testTransform:works})("output")
    return $result?output
};
