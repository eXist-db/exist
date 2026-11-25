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
 : An index with a filter (here lower-casing) does not compare by codepoint: its keys, and the query's, went through the
 : filter. It cannot answer a comparison that names a collation, which compares the strings as they are; it declines,
 : and the comparison is evaluated as written. A comparison that names no collation keeps the index's own comparison,
 : filter included, by design.
 :)
module namespace rccf="http://exist-db.org/xquery/range/collation-filter/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $rccf:CODEPOINT := "http://www.w3.org/2005/xpath-functions/collation/codepoint";

declare variable $rccf:DATA := <doc><w k="Apple">Apple</w><w k="apple">apple</w><w k="Banana">Banana</w></doc>;

(: collection name -> index configuration; "none" has no configuration :)
declare variable $rccf:CONFIGS := map {
    "rccf-filter": <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema"><range><create qname="w" type="xs:string">
            <filter class="org.apache.lucene.analysis.core.LowerCaseFilter"/>
        </create></range></index>
    </collection>,
    "rccf-filter-field": <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema"><range><create qname="w">
            <field name="k" match="@k" type="xs:string"/>
            <filter class="org.apache.lucene.analysis.core.LowerCaseFilter"/>
        </create></range></index>
    </collection>,
    "rccf-none": ()
};

declare
    %test:setUp
function rccf:setup() {
    xmldb:create-collection("/db/system", "config"),
    xmldb:create-collection("/db/system/config", "db"),
    for $name in map:keys($rccf:CONFIGS)
    return (
        if (exists($rccf:CONFIGS($name))) then (
            xmldb:create-collection("/db/system/config/db", $name),
            xmldb:store("/db/system/config/db/" || $name, "collection.xconf", $rccf:CONFIGS($name))
        ) else (),
        xmldb:create-collection("/db", $name),
        xmldb:store("/db/" || $name, "d.xml", $rccf:DATA)
    )
};

declare
    %test:tearDown
function rccf:teardown() {
    for $name in map:keys($rccf:CONFIGS)
    return (
        xmldb:remove("/db/" || $name),
        if (xmldb:collection-available("/db/system/config/db/" || $name)) then xmldb:remove("/db/system/config/db/" || $name) else ()
    )
};

declare %test:assertEquals(0) function rccf:no-index-equals() {
    count(collection("/db/rccf-none")//w[equals(., "APPLE", $rccf:CODEPOINT)])
};

(: the keys are in lower case: the index must not answer, or 'Apple' and 'apple' match as well :)
declare %test:assertEquals(0) function rccf:filter-equals() {
    count(collection("/db/rccf-filter")//w[equals(., "APPLE", $rccf:CODEPOINT)])
};

declare %test:assertEquals(0) function rccf:filter-starts-with() {
    count(collection("/db/rccf-filter")//w[starts-with(., "APP", $rccf:CODEPOINT)])
};

declare %test:assertEquals(1) function rccf:filter-equals-exact() {
    count(collection("/db/rccf-filter")//w[equals(., "Apple", $rccf:CODEPOINT)])
};

declare %test:assertEquals(0) function rccf:filter-field-equals() {
    count(collection("/db/rccf-filter-field")//w[equals(@k, "APPLE", $rccf:CODEPOINT)])
};

(: no collation named: the index's own comparison, filter included, by design :)
declare %test:assertEquals(2) function rccf:filter-without-collation() {
    count(collection("/db/rccf-filter")//w[. = "APPLE"])
};

declare %test:assertEquals(2) function rccf:filter-field-without-collation() {
    count(collection("/db/rccf-filter-field")//w[@k = "APPLE"])
};
