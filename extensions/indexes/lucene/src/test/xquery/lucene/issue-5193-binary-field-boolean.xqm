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
 : XQSuite regression test for GitHub #5193: ft:binary-field does not cast
 : booleans – returns "true"/"false" as xs:string instead of xs:boolean.
 :
 : @see https://github.com/eXist-db/exist/issues/5193
 :)
module namespace i5193 = "http://exist-db.org/xquery/lucene/issue-5193/test";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

import module namespace ft = "http://exist-db.org/xquery/lucene";

declare variable $i5193:DATA as document-node() := document {
    <items>
        <item flag="true">match</item>
        <item flag="false">match</item>
        <item flag="1">match</item>
        <item flag="0">match</item>
    </items>
};

declare variable $i5193:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0" xmlns:xs="http://www.w3.org/2001/XMLSchema">
        <index>
            <lucene>
                <text qname="item">
                    <field name="my-field" expression="@flag" type="xs:boolean" binary="yes"/>
                    <field name="from-true" expression="true()" type="xs:boolean" binary="yes"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare variable $i5193:COLLECTION_NAME := "i5193-binary-boolean";
declare variable $i5193:COLLECTION := "/db/" || $i5193:COLLECTION_NAME;

declare
    %test:setUp
function i5193:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $i5193:COLLECTION_NAME),
      xmldb:create-collection("/db/system/config/db", $i5193:COLLECTION_NAME),
      xmldb:store("/db/system/config/db/" || $i5193:COLLECTION_NAME, "collection.xconf", $i5193:XCONF),
      xmldb:store($i5193:COLLECTION, "test.xml", $i5193:DATA),
      xmldb:reindex($i5193:COLLECTION) )
};

declare
    %test:tearDown
function i5193:tearDown() {
    xmldb:remove($i5193:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $i5193:COLLECTION_NAME)
};

(: xs:integer for assertEquals - 1=true, 0=false avoids annotation parsing of boolean literals.
   Order by @flag ascending: "0","1","false","true" yields 0,1,0,1 :)
declare
    %test:assertEquals(0, 1, 0, 1)
function i5193:binary-field-returns-boolean() {
    for $hit in collection($i5193:COLLECTION)//item[ft:query(., "match")]
    order by $hit/@flag
    return xs:integer(ft:binary-field($hit, "my-field", "xs:boolean"))
};

declare
    %test:assertTrue
function i5193:binary-field-instance-of-boolean() {
    let $hit := collection($i5193:COLLECTION)//item[ft:query(., "match")][1]
    let $val := ft:binary-field($hit, "my-field", "xs:boolean")
    return $val instance of xs:boolean
};

(: Expression true() from original issue – field indexes constant boolean per item :)
declare
    %test:assertTrue
function i5193:binary-field-from-true-expression() {
    let $hit := collection($i5193:COLLECTION)//item[ft:query(., "match")][1]
    return ft:binary-field($hit, "from-true", "xs:boolean") = true()
};
