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

module namespace groupby="http://exist-db.org/xquery/test/groupby";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $groupby:plays := (
  document {
   <play>
    <title>Hamlet</title>
    <characters>
      <character>Hamlet</character>
      <character>Polonius</character>
    </characters>
   </play>
   },
   document {<play>
    <title>Rosenkrantz and Guildenstern are Dead</title>
    <characters>
      <character>Alfred</character>
      <character>Hamlet</character>
    </characters>
   </play>
   }
);

declare variable $groupby:works :=
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
 </works>;

declare variable $groupby:sales-records-doc :=
<sales>
  <record>
    <product-name>broiler</product-name>
    <store-number>1</store-number>
    <qty>20</qty>
  </record>
  <record>
    <product-name>toaster</product-name>
    <store-number>2</store-number>
    <qty>100</qty>
  </record>
  <record>
    <product-name>toaster</product-name>
    <store-number>2</store-number>
    <qty>50</qty>
  </record>
  <record>
    <product-name>toaster</product-name>
    <store-number>3</store-number>
    <qty>50</qty>
  </record>
  <record>
    <product-name>blender</product-name>
    <store-number>3</store-number>
    <qty>100</qty>
  </record>
  <record>
    <product-name>blender</product-name>
    <store-number>3</store-number>
    <qty>150</qty>
  </record>
  <record>
    <product-name>socks</product-name>
    <store-number>1</store-number>
    <qty>500</qty>
  </record>
  <record>
    <product-name>socks</product-name>
    <store-number>2</store-number>
    <qty>10</qty>
  </record>
  <record>
    <product-name>shirt</product-name>
    <store-number>3</store-number>
    <qty>10</qty>
  </record>
</sales>;

declare variable $groupby:stores-doc :=
<stores>
  <store>
    <store-number>1</store-number>
    <state>CA</state>
  </store>
  <store>
    <store-number>2</store-number>
    <state>CA</state>
  </store>
  <store>
    <store-number>3</store-number>
    <state>MA</state>
  </store>
  <store>
    <store-number>4</store-number>
    <state>WA</state>
  </store>
</stores>;

declare variable $groupby:products-doc :=
<products>
  <product>
    <name>broiler</name>
    <category>kitchen</category>
    <price>100</price>
    <cost>70</cost>
  </product>
  <product>
    <name>toaster</name>
    <category>kitchen</category>
    <price>30</price>
    <cost>10</cost>
  </product>
  <product>
    <name>blender</name>
    <category>kitchen</category>
    <price>50</price>
    <cost>25</cost>
  </product>
  <product>
    <name>socks</name>
    <category>clothes</category>
    <price>5</price>
    <cost>2</cost>
  </product>
  <product>
    <name>shirt</name>
    <category>clothes</category>
    <price>10</price>
    <cost>3</cost>
  </product>
</products>;

declare variable $groupby:addresses :=
    <addresses>
        <address>
            <name>Rudi Rüssel</name>
            <city>Düsseldorf</city>
        </address>
        <address>
            <name>Berta Bär</name>
            <city>Dusseldorf</city>
        </address>
    </addresses>;

declare variable $groupby:collections :=
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

declare variable $groupby:values-recursive :=
    <t>
        <v n="1">
            <v n="2"/>
            <v n="3"/>
            <v n="2"/>
        </v>
    </t>;
    
declare
    %test:assertEqualsPermutation(
        "1 11 21 31 41 51 61 71 81 91", "2 12 22 32 42 52 62 72 82 92", "3 13 23 33 43 53 63 73 83 93",
        "4 14 24 34 44 54 64 74 84 94", "5 15 25 35 45 55 65 75 85 95", "6 16 26 36 46 56 66 76 86 96",
        "7 17 27 37 47 57 67 77 87 97", "8 18 28 38 48 58 68 78 88 98", "9 19 29 39 49 59 69 79 89 99",
        "10 20 30 40 50 60 70 80 90 100"
    )
function groupby:atomic1() {
    for $x in 1 to 100
    let $key := $x mod 10
    group by $key
    return string(text{$x})
};

declare
    %test:assertEqualsPermutation(
        "1 11 21 31 41 51 61 71 81 91", "2 12 22 32 42 52 62 72 82 92", "3 13 23 33 43 53 63 73 83 93",
        "4 14 24 34 44 54 64 74 84 94", "5 15 25 35 45 55 65 75 85 95", "6 16 26 36 46 56 66 76 86 96",
        "7 17 27 37 47 57 67 77 87 97", "8 18 28 38 48 58 68 78 88 98", "9 19 29 39 49 59 69 79 89 99",
        "10 20 30 40 50 60 70 80 90 100"
    )
function groupby:atomic2() {
    for $x in 1 to 100
    group by $key := $x mod 10
    return
        string(text{$x})
};

declare
    %test:assertEqualsPermutation(
        "female:Jane Doe 1,Jane Doe 3,Jane Doe 5,Jane Doe 7,Jane Doe 9,Jane Doe 11,Jane Doe 13",
        "male:John Doe 2,John Doe 4,John Doe 6,John Doe 8,John Doe 10,John Doe 12"
    )
function groupby:basic1() {
    for $x in $groupby:works//employee
    let $key := $x/@gender
    group by $key
    return concat($key, ':',
           string-join(for $e in $x return $e/@name/string(), ','))
};

declare
    %test:assertEqualsPermutation(
        "female:Jane Doe 1,Jane Doe 3,Jane Doe 5,Jane Doe 7,Jane Doe 9,Jane Doe 11,Jane Doe 13",
        "male:John Doe 2,John Doe 4,John Doe 6,John Doe 8,John Doe 10,John Doe 12"
    )
function groupby:basic2() {
    for $x in $groupby:works//employee
    group by $key := $x/@gender
    return concat($key, ':',
           string-join(for $e in $x return $e/@name/string(), ','))
};

declare
    %test:assertEqualsPermutation(
        "false:Jane Doe 1,Jane Doe 3,Jane Doe 5,Jane Doe 7,Jane Doe 9,Jane Doe 11,Jane Doe 13",
        "true:John Doe 2,John Doe 4,John Doe 6,John Doe 8,John Doe 10,John Doe 12"
    )
function groupby:basicBooleanGroupKey1() {
    for $x in $groupby:works//employee
    let $key := ($x/@gender = 'male')
    group by $key
    return concat($key, ':',
           string-join(for $e in $x return $e/@name/string(), ','))
};

declare
    %test:assertEqualsPermutation(
        "false:Jane Doe 1,Jane Doe 3,Jane Doe 5,Jane Doe 7,Jane Doe 9,Jane Doe 11,Jane Doe 13",
        "true:John Doe 2,John Doe 4,John Doe 6,John Doe 8,John Doe 10,John Doe 12"
    )
function groupby:basicBooleanGroupKey2() {
    for $x in $groupby:works//employee
    group by $key := ($x/@gender = 'male')
    return concat($key, ':',
           string-join(for $e in $x return $e/@name/string(), ','))
};

declare
    %test:assertEqualsPermutation("female:41.25", "male:37.75")
function groupby:aggregateOverKey() {
    for $x in $groupby:works//employee
    let $key := $x/@gender
    group by $key
    return concat($key, ':', avg($x/hours))
};

declare
    %test:assertXPath("$result/group[@status='active'][@count='1']")
function groupby:emptyGroupKey() {
    (: <out><group status="" count="12"/><group status="active" count="1"/></out> :)
    <out>{
        for $x in $groupby:works//employee
        group by $key := $x/status
        return <group status="{$key}" count="{count($x)}"/>
    }</out>
};

declare
    %test:assertEquals('<out><group count="6" key="E1">P1|P2|P3|P4|P5|P6</group><group count="2" key="E2">P1|P2</group><group count="2" key="E3">P2|P2</group><group count="3" key="E4">P2|P4|P5</group></out>')
function groupby:collation() {
    <out>{
        for $x in $groupby:works//employee
        let $key := $x/empnum
        group by $key collation "http://www.w3.org/2005/xpath-functions/collation/codepoint"
        order by $key
        return <group count="{count($x)}" key="{$key}"> {string-join($x/pnum, '|')} </group>
    }</out>
};

declare
    %test:assertEquals('<out><group count="6" key="E1">P1|P2|P3|P4|P5|P6</group><group count="2" key="E2">P1|P2</group><group count="2" key="E3">P2|P2</group><group count="3" key="E4">P2|P4|P5</group></out>')
function groupby:collation2() {
    <out>{
        for $x in $groupby:works//employee
        group by $key := $x/empnum collation "http://www.w3.org/2005/xpath-functions/collation/codepoint"
        order by $key
        return <group count="{count($x)}" key="{$key}"> {string-join($x/pnum, '|')} </group>
    }</out>
};

declare
    %test:assertEqualsPermutation("40:3", "80:3", "20:5")
function groupby:whereClause() {
    for $x in $groupby:works//employee
    let $key := $x/hours[1]
    group by $key
    where count($x) gt 2
    return concat($key, ':', count($x))
};

declare
    %test:assertEquals('<out><group count="2">Jane Doe 7|John Doe 8</group><group count="2">Jane Doe 9|John Doe 10</group><group count="3">Jane Doe 11|John Doe 12|Jane Doe 13</group><group count="6">Jane Doe 1|John Doe 2|Jane Doe 3|John Doe 4|Jane Doe 5|John Doe 6</group></out>')
function groupby:orderByClause() {
    <out>{
        for $x in $groupby:works//employee
        let $key := $x/empnum
        group by $key
        order by count($x), $key
        return <group count="{count($x)}"> {string-join($x/@name, '|')} </group>
    }</out>
};

declare
    %test:assertEquals('<sales-qty-by-product><product name="blender">250</product><product name="broiler">20</product><product name="shirt">10</product><product name="socks">510</product><product name="toaster">200</product></sales-qty-by-product>')
function groupby:useCase1() {
    <sales-qty-by-product>
    {
        for $sales in $groupby:sales-records-doc/record
        let $pname := $sales/product-name
        group by $pname
        order by $pname
        return
            <product name="{$pname}">{
                sum($sales/qty)
            }</product>
    }</sales-qty-by-product>
};

declare
    %test:assertEquals('<result><group><state>CA</state><category>clothes</category><total-qty>510</total-qty></group><group><state>CA</state><category>kitchen</category><total-qty>170</total-qty></group><group><state>MA</state><category>clothes</category><total-qty>10</total-qty></group><group><state>MA</state><category>kitchen</category><total-qty>300</total-qty></group></result>')
function groupby:useCase2() {
    <result>{
        for $sales in $groupby:sales-records-doc/record
        let $state := $groupby:stores-doc/store[store-number = $sales/store-number]/state/string()
        let $category := $groupby:products-doc/product[name = $sales/product-name]/category/string()
        group by $state, $category
        order by $state, $category
        return
            <group>
                <state>{$state}</state>
                <category>{$category}</category>
                <total-qty>{sum($sales/qty)}</total-qty>
            </group>
   }</result>
};

declare
    %test:assertEquals('<result><group><state>CA</state><category>clothes</category><total-revenue>2550</total-revenue></group><group><state>CA</state><category>kitchen</category><total-revenue>6500</total-revenue></group><group><state>MA</state><category>clothes</category><total-revenue>100</total-revenue></group><group><state>MA</state><category>kitchen</category><total-revenue>14000</total-revenue></group></result>')
function groupby:useCase3() {
    <result>{
        for $sales in $groupby:sales-records-doc/record
        let $state := $groupby:stores-doc/store[store-number = $sales/store-number]/state
        let $product := $groupby:products-doc/product[name = $sales/product-name]
        let $category := $product/category
        let $revenue := $sales/qty * $product/price
        group by $state, $category
        order by $state, $category
        return
            <group>
                <state>{$state}</state>
                <category>{$category}</category>
                <total-revenue>{sum($revenue)}</total-revenue>
            </group>
    }</result>
};

declare
    %test:assertEquals('<result><state name="CA"><category name="clothes"><product name="socks" total-qty="510"/></category><category name="kitchen"><product name="broiler" total-qty="20"/><product name="toaster" total-qty="150"/></category></state><state name="MA"><category name="clothes"><product name="shirt" total-qty="10"/></category><category name="kitchen"><product name="blender" total-qty="250"/><product name="toaster" total-qty="50"/></category></state><state name="WA"><category name="clothes"/><category name="kitchen"/></state></result>')
function groupby:useCase4() {
    <result>{
        for $store in $groupby:stores-doc/store
        let $state := $store/state
        group by $state
        order by $state
        return
            <state name="{$state}">{
                for $product in $groupby:products-doc/product
                let $category := $product/category
                group by $category
                order by $category
                return
                    <category name="{$category}">{
                        for $sales in $groupby:sales-records-doc/record[store-number = $store/store-number
                        and product-name = $product/name]
                        let $pname := $sales/product-name
                        group by $pname
                        order by $pname
                        return
                        <product name="{$pname}" total-qty="{sum($sales/qty)}" />
                    }</category>
            }</state>
    }</result>
};

declare
    %test:assertEquals('<result><store number="1"><product name="socks" qty="500"/><product name="broiler" qty="20"/></store><store number="2"><product name="toaster" qty="100"/><product name="toaster" qty="50"/><product name="socks" qty="10"/></store><store number="3"><product name="blender" qty="150"/><product name="blender" qty="100"/><product name="toaster" qty="50"/><product name="shirt" qty="10"/></store></result>')
function groupby:useCase5() {
    <result>{
        for $sales in $groupby:sales-records-doc/record
        let $storeno := $sales/store-number
        group by $storeno
        order by $storeno
        return
            <store number = "{$storeno}">{
                for $s in $sales
                order by xs:int($s/qty) descending
                return
                    <product name = "{$s/product-name}" qty = "{$s/qty}"/>
            }</store>
    }</result>
};

declare
    %test:assertEquals('<result><store number="3" total-profit="7320"/><store number="2" total-profit="3030"/><store number="1" total-profit="2100"/></result>')
function groupby:useCase6() {
    <result>{
        for $sales in $groupby:sales-records-doc/record
        let $storeno := $sales/store-number,
            $product := $groupby:products-doc/product[name = $sales/product-name],
            $prd := $product,
            $profit := $sales/qty * ($prd/price - $prd/cost)
        group by $storeno
        let $total-store-profit := sum($profit)
        where $total-store-profit > 100
        order by $total-store-profit descending
        return
            <store number = "{$storeno}" total-profit = "{$total-store-profit}"/>
    }</result>
};

(: Should create two groups for "Düsseldorf" and "Dusseldorf" :)
declare
    %test:assertEquals(1, 1)
function groupby:collation3() {
    for $address in $groupby:addresses/address
    group by $city := $address/city
    return
        count($address)
};

(: Should create one group only due to the collation :)
declare
    %test:assertEquals(2)
function groupby:collation4() {
    for $address in $groupby:addresses/address
    group by $city := $address/city collation "?strength=primary"
    return
        count($address)
};

declare
    %test:assertEquals(
        '<grp even="1" y="1">1 1 1 1 3 3 3 3 5 5 5 5 7 7 7 7 9 9 9 9</grp>',
        '<grp even="0" y="1">2 2 2 2 4 4 4 4 6 6 6 6 8 8 8 8 10 10 10 10</grp>')
function groupby:existing-var()
{
    for $x in 1 to 10, $y in 1 to 4
    let $org_y := $y
    group by $y, $y := $x mod 2
    return <grp y="{$org_y[1]}" even="{$y}">{$x}</grp>
};

(: https://github.com/eXist-db/exist/issues/384 :)
declare
    %test:assertEquals('<character name="Hamlet"><play><title>Hamlet</title></play><play><title>Rosenkrantz and Guildenstern are Dead</title></play></character>',
    '<character name="Polonius"><play><title>Hamlet</title></play></character>',
    '<character name="Alfred"><play><title>Rosenkrantz and Guildenstern are Dead</title></play></character>')
function groupby:multi-for-groupby-bug1() {
    for $play in $groupby:plays/play
    let $title := $play/title
    for $character in $play/characters/character
    group by $character
    return
        <character name="{$character}">
         {
            $title ! <play>{ . }</play>
         }
        </character>
};

(: https://github.com/eXist-db/exist/issues/384 :)
declare
    %test:assertEquals('<character name="Hamlet"><play><title>Hamlet</title></play><play><title>Rosenkrantz and Guildenstern are Dead</title></play></character>',
    '<character name="Polonius"><play><title>Hamlet</title></play></character>',
    '<character name="Alfred"><play><title>Rosenkrantz and Guildenstern are Dead</title></play></character>')
function groupby:multi-for-groupby-bug2() {
    for $play in $groupby:plays/play
    let $title := $play/title
    for $c in  $play/characters/character
    group by $character := $c
    return
        <character name="{$character}">
         {
            $title ! <play>{ . }</play>
         }
        </character>
};

declare %private function groupby:list-ordered($collections as element()*) {
    for $collection in $collections
    where $collection/@size > 1
    order by $collection/@name ascending
    return
        <col>
        { $collection/@name, groupby:list-ordered($collection/*) }
        </col>
};

declare 
    %test:assertEquals(
        '<col name="db"><col name="system"><col name="config"/><col name="security"/></col><col name="test"><col name="test2"/><col name="test3"/></col></col>')
function groupby:recursive-orderby() {
    groupby:list-ordered($groupby:collections)
};

declare %private function groupby:recursive-groupby($values as element()*) {
    for $value in $values
    group by $key := $value/@n
    return
        <g n="{$key}" c="{count($value)}">
        { for $v in $value return groupby:recursive-groupby($v/v) }
        </g>
};

declare
    %test:assertEquals(
        '<g n="1" c="1"><g n="2" c="2"/><g n="3" c="1"/></g>')
function groupby:recursive-groupby() {
    groupby:recursive-groupby($groupby:values-recursive/v)
};

declare
%test:assertEquals(1, 2)
function groupby:issue-967() {
    for $nr at $pos in (1,2)
    group by $pos
    return
    $nr
};

declare
  %test:assertEquals("<result><working-time><person/><other/></working-time><working-time><person>Anton</person><other><person>Anton</person></other></working-time><working-time><person>Barbara</person><other><person>Barbara</person></other></working-time><working-time><person>Clara</person><other><person>Clara</person></other></working-time></result>")
function groupby:atomize-group-vars() {
  let $data := document {
    <stream>
      <event time="2006-01-01T01:00:00-00:00"/>
      <event time="2006-01-01T10:30:00-00:00">
        <person>Anton</person>
        <direction>in</direction>
      </event>
      <event time="2006-01-01T11:00:00-00:00">
        <person>Barbara</person>
        <direction>in</direction>
      </event>
      <event time="2006-01-01T11:15:00-00:00">
        <person>Clara</person>
        <direction>in</direction>
      </event>
      <event time="2006-01-01T12:15:00-00:00">
        <person>Clara</person>
        <direction>out</direction>
      </event>
      <event time="2006-01-01T14:00:00-00:00">
        <person>Barbara</person>
        <direction>out</direction>
      </event>
      <event time="2006-01-01T15:00:00-00:00">
        <person>Anton</person>
        <direction>out</direction>
      </event>
      <event time="2006-01-01T23:00:00-00:00"/>
      <event time="2006-01-02T01:00:00-00:00"/>
      <event time="2006-01-02T11:00:00-00:00">
        <person>Anton</person>
        <direction>in</direction>
      </event>
      <event time="2006-01-02T12:00:00-00:00">
        <person>Clara</person>
        <direction>in</direction>
      </event>
      <event time="2006-01-02T12:10:00-00:00">
        <person>Clara</person>
        <direction>out</direction>
      </event>
      <event time="2006-01-02T12:15:00-00:00">
        <person>Clara</person>
        <direction>in</direction>
      </event>
      <event time="2006-01-02T12:20:00-00:00">
        <person>Clara</person>
        <direction>out</direction>
      </event>
        <event time="2006-01-02T12:25:00-00:00">
        <person>Clara</person>
        <direction>in</direction>
      </event>
      <event time="2006-01-02T12:40:00-00:00">
        <person>Clara</person>
        <direction>out</direction>
      </event>
      <event time="2006-01-02T14:00:00-00:00">
        <person>Clara</person>
        <direction>in</direction>
      </event>
      <event time="2006-01-02T16:00:00-00:00">
        <person>Anton</person>
        <direction>out</direction>
      </event>
      <event time="2006-01-02T16:15:00-00:00">
        <person>Clara</person>
        <direction>out</direction>
      </event>
      <event time="2006-01-02T23:00:00-00:00"/>
    </stream>
  } return
    <result>{
      for $s in $data/stream/event
      let $person := $s/person
      let $x := $s/person
      group by $person
      order by $person
      return
        <working-time>
          <person>{$person}</person>
          <other>{$x[1]}</other>
        </working-time>
    }</result>
};
