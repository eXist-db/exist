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
 : A wildcard or prefix term that expands to more terms than a Lucene boolean query may have (1024) still finds
 : its documents, with or without the filter-rewrite option.
 :)
module namespace wmt="http://exist-db.org/xquery/lucene/wildcard-many-terms/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $wmt:COLLECTION_NAME := "lucene-test-wildcard-many-terms";
declare variable $wmt:COLLECTION := "/db/" || $wmt:COLLECTION_NAME;

declare variable $wmt:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <lucene>
                <text qname="p"/>
            </lucene>
        </index>
    </collection>;

declare
    %test:setUp
function wmt:setUp() {
    xmldb:create-collection("/db/system/config", "db"),
    xmldb:create-collection("/db/system/config/db", $wmt:COLLECTION_NAME),
    xmldb:store("/db/system/config/db/" || $wmt:COLLECTION_NAME, "collection.xconf", $wmt:XCONF),
    xmldb:create-collection("/db", $wmt:COLLECTION_NAME),
    xmldb:store($wmt:COLLECTION, "words.xml",
        <words>{ for $i in 1 to 1100 return <p>{ "word" || format-integer($i, "0000") }</p> }</words>)
};

declare
    %test:tearDown
function wmt:tearDown() {
    xmldb:remove($wmt:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $wmt:COLLECTION_NAME)
};

declare
    %test:args("word*")
    %test:assertEquals(1100)
    %test:args("word1*")
    %test:assertEquals(101)
    %test:args("wor?0*")
    %test:assertEquals(999)
function wmt:expands-beyond-the-clause-limit($query as xs:string) {
    count(collection($wmt:COLLECTION)//p[ft:query(., $query)])
};

declare
    %test:args("word*")
    %test:assertEquals(1100)
function wmt:with-filter-rewrite($query as xs:string) {
    count(collection($wmt:COLLECTION)//p[ft:query(., $query, map { "filter-rewrite": "yes" })])
};
