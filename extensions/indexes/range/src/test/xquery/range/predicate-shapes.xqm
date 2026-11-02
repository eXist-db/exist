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
 : Range functions in predicates of steps of different shapes. See https://github.com/eXist-db/exist/issues/6974
 :)
module namespace rps="http://exist-db.org/xquery/range/predicate-shapes/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";
import module namespace range="http://exist-db.org/xquery/range";

declare variable $rps:COLLECTION_NAME := "test-range-predicate-shapes";
declare variable $rps:COLLECTION := "/db/" || $rps:COLLECTION_NAME;
declare variable $rps:CONF := "/db/system/config/db/" || $rps:COLLECTION_NAME;

declare variable $rps:XCONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <range>
                <create qname="item" type="xs:string"/>
                <create qname="other"><field name="rps-field" match="t" type="xs:string"/></create>
            </range>
        </index>
    </collection>;

declare
    %test:setUp
function rps:setUp() {
    xmldb:create-collection("/db/system", "config"),
    xmldb:create-collection("/db/system/config", "db"),
    xmldb:create-collection("/db/system/config/db", $rps:COLLECTION_NAME),
    xmldb:store($rps:CONF, "collection.xconf", $rps:XCONF),
    xmldb:create-collection("/db", $rps:COLLECTION_NAME),
    for $i in 1 to 4
    let $term := if ($i <= 2) then "hit" else "miss"
    return xmldb:store($rps:COLLECTION, "d" || $i || ".xml",
        <wrap><!--c--><item n="{$i}">{$term}</item><other n="o{$i}"><t>{$term}</t></other></wrap>)
};

declare
    %test:tearDown
function rps:tearDown() {
    xmldb:remove($rps:COLLECTION),
    xmldb:remove($rps:CONF)
};

(: a step without a name matches no configuration by name, and gives the nodes its index finds :)
declare
    %test:assertEquals("1", "2")
function rps:range-eq-on-a-wildcard-step() {
    sort(collection($rps:COLLECTION)/wrap/*[range:eq(., "hit")]/@n/string())
};

declare
    %test:assertEquals("1", "2")
function rps:range-eq-on-a-node-test-step() {
    sort(collection($rps:COLLECTION)/wrap/node()[range:eq(., "hit")]/@n/string())
};

declare
    %test:assertEquals("1", "2")
function rps:range-eq-on-an-element-test-step() {
    sort(collection($rps:COLLECTION)/wrap/element()[range:eq(., "hit")]/@n/string())
};

declare
    %test:assertEquals(0)
function rps:range-eq-on-a-comment-test-step() {
    count(collection($rps:COLLECTION)/wrap/comment()[range:eq(., "hit")])
};

declare
    %test:assertEquals("1", "2")
function rps:range-matches-on-a-wildcard-step() {
    sort(collection($rps:COLLECTION)/wrap/*[range:matches(., "hit")]/@n/string())
};

(: fn:matches is not rewritten to the field: it gives the XPath answer, with its flags :)
declare
    %test:assertEquals("o1", "o2")
function rps:matches-on-a-field-path() {
    sort(collection($rps:COLLECTION)/wrap/other[matches(t, "hit")]/@n/string())
};

declare
    %test:assertEquals("o1", "o2")
function rps:matches-with-flags-on-a-field-path() {
    sort(collection($rps:COLLECTION)//other[matches(t, "HIT", "i")]/@n/string())
};

(: a comparison still goes to the field :)
declare
    %test:assertEquals("o1", "o2")
function rps:comparison-on-a-field-path() {
    sort(collection($rps:COLLECTION)//other[t = "hit"]/@n/string())
};
