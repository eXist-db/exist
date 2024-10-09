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
                    <field name="pub-year" expression="date"/>
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

declare variable $ftt:FIELD_SAMPLE :=
<group>
    <div>
        <date>1972</date>
        <title>Study 1</title>
        <p>Forwarded to President Nixon Nov. 9, 1970</p>
    </div>
    <div>
        <date>1976</date>
        <title>Study 2</title>
        <p>Early in January 1971, President Nixon, during a radio-TV...</p>
    </div>
</group>;
    
declare variable $ftt:COLLECTION_NAME := "matchestest";
declare variable $ftt:COLLECTION := "/db/" || $ftt:COLLECTION_NAME;

declare
    %test:setUp
function ftt:setup() {
    xmldb:create-collection("/db/system/config/db", $ftt:COLLECTION_NAME),
    xmldb:store("/db/system/config/db/" || $ftt:COLLECTION_NAME, "collection.xconf", $ftt:COLLECTION_CONFIG),
    xmldb:create-collection("/db", $ftt:COLLECTION_NAME),
    xmldb:store($ftt:COLLECTION, "test.xml", $ftt:DATA),
    xmldb:store($ftt:COLLECTION, "testFields.xml", $ftt:FIELD_SAMPLE)
};

declare
    %test:tearDown
function ftt:cleanup() {
    xmldb:remove($ftt:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $ftt:COLLECTION_NAME)
};

(:~
 : Check match highlighting: matches for the criteria specified for a field should *not*
 : be highlighted elsewhere with util:expand.
 : Currently:
 : * the first case returns 2 matches for 'Nixon' (correct)
 : * 2nd returns 2 matches for 'Nixon' (correct) and 4 additional for '1972', '1976', '1970' and '1971' in date and p (incorrect)
 : * 3rd returns only the 4 number matches in date and p (incorrect)
 :)
declare
    %test:assertEquals(2, 2, 0, 0)
function ftt:field-highlight() {
    count(util:expand(collection($ftt:COLLECTION)//div[ft:query(., 'Nixon')])//exist:match),
    count(util:expand(collection($ftt:COLLECTION)//div[ft:query(., 'Nixon AND pub-year:[1970 TO 1980]')])//exist:match),
    count(util:expand(collection($ftt:COLLECTION)//div[ft:query(., 'pub-year:[1970 TO 1980]')])//exist:match),
    count(util:expand(collection($ftt:COLLECTION)//div[ft:query(., 'pub-year:[1970 TO 1973]')])//exist:match)
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