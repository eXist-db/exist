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
 : Fields with the names the index uses itself, or a name that reads as a hexadecimal number, store and search like
 : any other, and do not disturb searches of other names. See https://github.com/eXist-db/exist/issues/6989
 :)
module namespace lfn="http://exist-db.org/xquery/lucene/reserved-field-names/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $lfn:COLLECTION_NAME := "test-lucene-reserved-field-names";
declare variable $lfn:COLLECTION := "/db/" || $lfn:COLLECTION_NAME;
declare variable $lfn:CONF := "/db/system/config/db/" || $lfn:COLLECTION_NAME;

declare variable $lfn:XCONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <lucene>
                <text qname="item"/>
                <text qname="other">
                    <field name="docId" expression="@k"/>
                    <field name="docIdKeyword" expression="@k"/>
                    <field name="docUri" expression="@k"/>
                    <field name="nodeId" expression="@k"/>
                    <field name="_idx" expression="@k"/>
                    <field name="_boost" expression="@k"/>
                    <field name="cafe" expression="@k"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare
    %test:setUp
function lfn:setUp() {
    xmldb:create-collection("/db/system", "config"),
    xmldb:create-collection("/db/system/config", "db"),
    xmldb:create-collection("/db/system/config/db", $lfn:COLLECTION_NAME),
    xmldb:store($lfn:CONF, "collection.xconf", $lfn:XCONF),
    xmldb:create-collection("/db", $lfn:COLLECTION_NAME),
    for $i in 1 to 3
    let $term := if ($i <= 2) then "hit" else "miss"
    return xmldb:store($lfn:COLLECTION, "d" || $i || ".xml",
        <wrap><item n="{$i}">{$term}</item><other n="o{$i}" k="{$term}"/></wrap>)
};

declare
    %test:tearDown
function lfn:tearDown() {
    xmldb:remove($lfn:COLLECTION),
    xmldb:remove($lfn:CONF)
};

declare
    %test:args("docId")
    %test:assertEquals("o1", "o2")
    %test:args("docIdKeyword")
    %test:assertEquals("o1", "o2")
    %test:args("docUri")
    %test:assertEquals("o1", "o2")
    %test:args("nodeId")
    %test:assertEquals("o1", "o2")
    %test:args("_idx")
    %test:assertEquals("o1", "o2")
    %test:args("_boost")
    %test:assertEquals("o1", "o2")
    %test:args("cafe")
    %test:assertEquals("o1", "o2")
function lfn:query-field($field as xs:string) {
    sort(collection($lfn:COLLECTION)//other[ft:query-field($field, "hit")]/@n/string())
};

(: replacing a document removes its entries :)
declare
    %test:assertEquals(1, 0)
function lfn:query-field-after-replace() {
    let $stored := xmldb:store($lfn:COLLECTION, "replaced.xml", <wrap><other n="r" k="before"/></wrap>)
    let $before := count(collection($lfn:COLLECTION)//other[ft:query-field("docId", "before")])
    let $replaced := xmldb:store($lfn:COLLECTION, "replaced.xml", <wrap><other n="r" k="after"/></wrap>)
    let $after := count(collection($lfn:COLLECTION)//other[ft:query-field("docId", "before")])
    let $removed := xmldb:remove($lfn:COLLECTION, "replaced.xml")
    return ($before, $after)
};

declare
    %test:assertEquals("1", "2")
function lfn:query-on-a-wildcard-step() {
    sort(collection($lfn:COLLECTION)/wrap/*[ft:query(., "hit")]/@n/string())
};
