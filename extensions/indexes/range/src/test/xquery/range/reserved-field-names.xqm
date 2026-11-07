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
module namespace rfn="http://exist-db.org/xquery/range/reserved-field-names/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";
import module namespace range="http://exist-db.org/xquery/range";

declare variable $rfn:COLLECTION_NAME := "test-range-reserved-field-names";
declare variable $rfn:COLLECTION := "/db/" || $rfn:COLLECTION_NAME;
declare variable $rfn:CONF := "/db/system/config/db/" || $rfn:COLLECTION_NAME;

declare variable $rfn:XCONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <range>
                <create qname="item" type="xs:string"/>
                <!-- one attribute per field: of fields with the same match, only one gets the value, see https://github.com/eXist-db/exist/issues/6992 -->
                <create qname="other">
                    <field name="id" match="@a" type="xs:string"/>
                    <field name="nodeId" match="@b" type="xs:string"/>
                    <field name="docId" match="@c" type="xs:string"/>
                    <field name="address" match="@d" type="xs:string"/>
                    <field name="face" match="@e" type="xs:string"/>
                </create>
            </range>
        </index>
    </collection>;

declare
    %test:setUp
function rfn:setUp() {
    xmldb:create-collection("/db/system", "config"),
    xmldb:create-collection("/db/system/config", "db"),
    xmldb:create-collection("/db/system/config/db", $rfn:COLLECTION_NAME),
    xmldb:store($rfn:CONF, "collection.xconf", $rfn:XCONF),
    xmldb:create-collection("/db", $rfn:COLLECTION_NAME),
    for $i in 1 to 3
    let $term := if ($i <= 2) then "hit" else "miss"
    return xmldb:store($rfn:COLLECTION, "d" || $i || ".xml",
        <wrap><item n="{$i}">{$term}</item><other n="o{$i}" a="{$term}" b="{$term}" c="{$term}" d="{$term}" e="{$term}"/></wrap>)
};

declare
    %test:tearDown
function rfn:tearDown() {
    xmldb:remove($rfn:COLLECTION),
    xmldb:remove($rfn:CONF)
};

declare
    %test:args("id")
    %test:assertEquals("o1", "o2")
    %test:args("nodeId")
    %test:assertEquals("o1", "o2")
    %test:args("docId")
    %test:assertEquals("o1", "o2")
    %test:args("address")
    %test:assertEquals("o1", "o2")
    %test:args("face")
    %test:assertEquals("o1", "o2")
function rfn:field-eq($field as xs:string) {
    sort(collection($rfn:COLLECTION)//range:field-eq($field, "hit")/@n/string())
};

(: replacing a document removes its entries :)
declare
    %test:assertEquals(1, 0)
function rfn:field-eq-after-replace() {
    let $stored := xmldb:store($rfn:COLLECTION, "replaced.xml", <wrap><other n="r" a="before"/></wrap>)
    let $before := count(collection($rfn:COLLECTION)//range:field-eq("id", "before"))
    let $replaced := xmldb:store($rfn:COLLECTION, "replaced.xml", <wrap><other n="r" a="after"/></wrap>)
    let $after := count(collection($rfn:COLLECTION)//range:field-eq("id", "before"))
    let $removed := xmldb:remove($rfn:COLLECTION, "replaced.xml")
    return ($before, $after)
};

(: a search without an element name looks in all element indexes, and passes over the fields :)
declare
    %test:assertEquals("1", "2")
function rfn:range-eq-on-a-wildcard-step() {
    sort(collection($rfn:COLLECTION)/wrap/*[range:eq(., "hit")]/@n/string())
};

declare
    %test:assertEquals("1", "2")
function rfn:range-matches-on-a-node-step() {
    sort(collection($rfn:COLLECTION)/wrap/node()[range:matches(., "hit")]/@n/string())
};

declare
    %test:assertEquals("hit", "miss")
function rfn:index-keys-without-names() {
    sort(distinct-values(util:index-keys(collection($rfn:COLLECTION)//item, (), function($key, $count) { $key }, (), "range-index")))
};
