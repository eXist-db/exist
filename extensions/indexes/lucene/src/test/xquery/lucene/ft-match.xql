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

module namespace ftt="http://exist-db.org/xquery/ft-match/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace stats="http://exist-db.org/xquery/profiling";

declare variable $ftt:COLLECTION_CONFIG := 
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <text qname="div">
                    <ignore qname="div"/>
                    <ignore qname="hi"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare variable $ftt:DATA :=
    <body>
        <div>
            <p>Introduction text</p>
            <div>
                <p>text in nested div and more <hi>text</hi>.</p>
            </div>
        </div>
    </body>;
    
declare variable $ftt:COLLECTION_NAME := "matchestest";
declare variable $ftt:COLLECTION := "/db/" || $ftt:COLLECTION_NAME;

declare
    %test:setUp
function ftt:setup() {
    xmldb:create-collection("/db/system/config/db", $ftt:COLLECTION_NAME),
    xmldb:store("/db/system/config/db/" || $ftt:COLLECTION_NAME, "collection.xconf", $ftt:COLLECTION_CONFIG),
    xmldb:create-collection("/db", $ftt:COLLECTION_NAME),
    xmldb:store($ftt:COLLECTION, "test.xml", $ftt:DATA)
};

declare
    %test:tearDown
function ftt:cleanup() {
    xmldb:remove($ftt:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $ftt:COLLECTION_NAME)
};

(:~
 : Check match highlighting: because the inner div is set to "ignore" in the Lucene index,
 : the matching string "text" should not be highlighted.
 : 
 : It should be highlighted though if we look at the second result, which is the inner div.
 : The nested <hi> should never be highlighted.
 :)
declare
    %test:args("text")
    %test:assertEquals(1, 1)
function ftt:highlight($query as xs:string) {
    count(util:expand(collection($ftt:COLLECTION)//div[ft:query(., $query)][1])//exist:match),
    count(util:expand(collection($ftt:COLLECTION)//div[ft:query(., $query)][2])//exist:match)
};

(:~
 : Asserts that string proximity '"Introduction text"~1' and XML
 : &lt;near slop="1"&gt;&lt;term&gt;Introduction&lt;/term&gt;&lt;term&gt;text&lt;/term&gt;&lt;/near&gt;
 : return identical match counts (from util:expand//exist:match).
 :
 : @see https://github.com/eXist-db/exist/issues/833
 : @return xs:integer+ (match-count for string query, match-count for XML query)
 :)
declare
    %test:pending("Proximity/slop string vs XML match-count equality, see #833")
    %test:assertEquals(1, 1)
function ftt:slop-string-vs-xml-equality() {
    let $queries := (
        '"Introduction text"~1',
        <query><near slop="1"><term>Introduction</term><term>text</term></near></query>
    ),
    $results :=
        for $query in $queries
        let $hits := collection($ftt:COLLECTION)//div[ft:query(., $query)],
            $expanded := util:expand($hits),
            $match-count := count($expanded//exist:match)
        return $match-count
    return ($results[1], $results[2])
};