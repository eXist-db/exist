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
 : XQSuite regression test for GitHub #6012: ft:facets#3 and ft:facets#4
 : should accept count=0 and return empty map (consistency with util:index-keys,
 : range:index-keys-for-field which return empty when max-number-returned=0).
 :
 : @see https://github.com/eXist-db/exist/issues/6012
 :)
module namespace i6012 = "http://exist-db.org/xquery/lucene/issue-6012/test";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

import module namespace ft = "http://exist-db.org/xquery/lucene";

declare variable $i6012:COLLECTION := "i6012-facets-zero";

declare variable $i6012:DATA as document-node() :=
    document {
        <items>
            <item publisher="Alpha">text 1</item>
            <item publisher="Beta">text 2</item>
        </items>
    };

declare variable $i6012:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <text qname="item">
                    <field name="publisher" expression="string(@publisher)"/>
                    <facet dimension="publisher" expression="string(@publisher)"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare
    %test:setUp
function i6012:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $i6012:COLLECTION),
      xmldb:create-collection("/db/system/config/db", $i6012:COLLECTION),
      xmldb:store("/db/" || $i6012:COLLECTION, "data.xml", $i6012:DATA),
      xmldb:store("/db/system/config/db/" || $i6012:COLLECTION, "collection.xconf", $i6012:XCONF),
      xmldb:reindex("/db/" || $i6012:COLLECTION) )
};

declare
    %test:tearDown
function i6012:tearDown() {
    xmldb:remove("/db/" || $i6012:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $i6012:COLLECTION)
};

(: ft:facets#3 with count=0 returns empty map. :)
declare
    %test:assertEquals(0)
function i6012:facets-zero-limit-returns-empty() {
    let $hits := collection("/db/" || $i6012:COLLECTION)//item[ft:query(., ())]
    return count(map:keys(ft:facets($hits, "publisher", 0)))
};

(: ft:facets#3 with count=0 returns map (not error). :)
declare
    %test:assertTrue
function i6012:facets-zero-limit-returns-map() {
    let $hits := collection("/db/" || $i6012:COLLECTION)//item[ft:query(., ())]
    let $result := ft:facets($hits, "publisher", 0)
    return $result instance of map(*)
};
