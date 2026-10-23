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
 : Review of PR 6914, finding 1: a pattern that is only an anchor, "^" or "$", matches every string in XPath.
 : Rewritten to the range index, it must still match every value, not only the value "^" or "$".
 :)
module namespace rba = "http://exist-db.org/xquery/test/review-bare-anchor";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

declare variable $rba:COLLECTION := "/db/review-bare-anchor";
declare variable $rba:CONFIG := "/db/system/config" || $rba:COLLECTION;

declare
    %test:setUp
function rba:setup() {
    let $_ := xmldb:create-collection("/db", substring-after($rba:COLLECTION, "/db/"))
    let $_ := xmldb:create-collection("/db/system/config/db", substring-after($rba:COLLECTION, "/db/"))
    let $_ := xmldb:store($rba:CONFIG, "collection.xconf",
        <collection xmlns="http://exist-db.org/collection-config/1.0">
            <index><range><create qname="v" type="xs:string"/></range></index>
        </collection>)
    return xmldb:store($rba:COLLECTION, "doc.xml",
        <items><item><v>ham</v></item><item><v>^</v></item><item><v>$</v></item><item><v>bacon</v></item></items>)
};

declare
    %test:tearDown
function rba:teardown() {
    xmldb:remove($rba:COLLECTION),
    xmldb:remove($rba:CONFIG)
};

(: reference: the same calls without an index :)
declare %test:assertEquals(4, 4) function rba:in-memory() {
    let $items := <items><item><v>ham</v></item><item><v>^</v></item><item><v>$</v></item><item><v>bacon</v></item></items>
    return (count($items/item[matches(v, "^")]), count($items/item[matches(v, "$")]))
};

declare %test:assertXPath("$result = 4") function rba:caret-only() {
    count(collection($rba:COLLECTION)//item[matches(v, "^")])
};

declare %test:assertXPath("$result = 4") function rba:dollar-only() {
    count(collection($rba:COLLECTION)//item[matches(v, "$")])
};

(: control: a translatable pattern the index serves, so that the rewrite is known to happen :)
declare
    %test:stats
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function rba:control-is-optimized() {
    collection($rba:COLLECTION)//item[matches(v, "^ha")]
};

declare
    %test:stats
    %test:assertXPath("$result//stats:index[@type eq 'new-range'][@optimization-level eq 'OPTIMIZED']")
function rba:caret-only-is-optimized() {
    collection($rba:COLLECTION)//item[matches(v, "^")]
};
