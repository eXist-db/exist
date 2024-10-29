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

module namespace obt = "http://exist-db.org/xquery/test/order-by";

declare namespace test = "http://exist-db.org/xquery/xqsuite";


declare variable $obt:collections :=
    <collection name="db" size="2">
        <collection name="test" size="3">
            <collection name="test2" size="2"/>
            <collection name="test1" size="1"/>
            <collection name="test3" size="4"/>
        </collection>
        <collection name="system" size="2">
            <collection name="security" size="2"/>
            <collection name="config" size="2"/>
        </collection>
    </collection>;


declare
    %test:assertError("err:XPTY0004")
function obt:non-comparable-types() {
  for $v in (xs:untypedAtomic("2017-09-18"), xs:date("1999-12-17"))
  order by $v
  return $v
};

declare
    %test:assertEquals("http://hello.com", "https://hello.com")
function obt:comparable-types() {
  for $v at $i in (xs:anyURI("http://hello.com"), xs:string("https://hello.com"))
  order by $v, $i
  return $v
};

declare
    %test:assertXPath("fn:count($result) eq 2 and $result[1] cast as xs:string eq 'NaN' and $result[2] eq xs:double(1)")
function obt:comparable-types-nan() as xs:double+ {
  let $numbers := (1, xs:double("NaN"))
  for $i in $numbers order by $i empty least
  return
    $i
};

declare
    %test:assertXPath("fn:count($result) eq 3 and $result[1] cast as xs:string eq '-INF' and $result[2] eq xs:double(1) and $result[3] cast as xs:string eq 'INF'")
function obt:comparable-types-inf() as xs:double+ {
  let $numbers := (1, xs:double("-INF"), xs:double("INF"))
  for $i in $numbers order by $i empty least
  return
    $i
};

declare %private function obt:list-ordered($collections as element()*) {
  for $collection in $collections
  where $collection/@size > 1
  order by $collection/@name ascending
  return
    <col>
    { $collection/@name, obt:list-ordered($collection/*) }
    </col>
};

declare
    %test:assertEquals(
        '<col name="db"><col name="system"><col name="config"/><col name="security"/></col><col name="test"><col name="test2"/><col name="test3"/></col></col>')
function obt:recursive-orderby() {
    obt:list-ordered($obt:collections)
};
