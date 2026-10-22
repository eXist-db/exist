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
 : ft:highlight-field-matches on a field that isn't stored: its text is computed from the node as indexing computes
 : it, and highlighted as the same field stored. The fields' names are this test's own, as all collections share one
 : index.
 :)
module namespace hu="http://exist-db.org/xquery/lucene/highlight-unstored/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace exist="http://exist.sourceforge.net/NS/exist";

declare variable $hu:COLLECTION_NAME := "lucene-test-highlight-unstored";
declare variable $hu:COLLECTION := "/db/" || $hu:COLLECTION_NAME;

declare variable $hu:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <analyzer class="org.apache.lucene.analysis.standard.StandardAnalyzer"/>
                <text qname="div">
                    <ignore qname="note"/>
                    <inline qname="hi"/>
                    <field name="hu-stored"/>
                    <field name="hu-unstored" store="no"/>
                    <field name="hu-head-stored" expression="head"/>
                    <field name="hu-head-unstored" expression="head" store="no"/>
                    <field name="hu-dated-unstored" if="@when" expression="head" store="no"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare variable $hu:XML as document-node() :=
    document {
        <volume>
            <div id="d1" when="1783">
                <head>Treaty of Paris</head>
                <p>The <hi>tre</hi>aty was signed<note>A note on the treaty</note> in Paris.</p>
                <p>Both parties agreed.</p>
            </div>
            <div id="d2">
                <head>Peace of Paris</head>
                <p>Another treaty.</p>
            </div>
        </volume>
    };

declare
    %test:setUp
function hu:setUp() {
    xmldb:create-collection("/db/system/config", "db"),
    xmldb:create-collection("/db/system/config/db", $hu:COLLECTION_NAME),
    xmldb:store("/db/system/config/db/" || $hu:COLLECTION_NAME, "collection.xconf", $hu:XCONF),
    xmldb:create-collection("/db", $hu:COLLECTION_NAME),
    xmldb:store($hu:COLLECTION, "volume.xml", $hu:XML)
};

declare
    %test:tearDown
function hu:tearDown() {
    xmldb:remove($hu:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $hu:COLLECTION_NAME)
};

(:~ The highlighted field of the first div found, serialized. :)
declare %private function hu:highlight($query as xs:string, $field as xs:string) as xs:string* {
    for $div in collection($hu:COLLECTION)//div[ft:query(., $query)]
    order by $div/@id
    return ft:highlight-field-matches($div, $field) ! serialize(.)
};

declare
    %test:args("hu-stored:treaty OR hu-unstored:treaty")
    %test:assertTrue
    %test:args('hu-stored:"signed in paris" OR hu-unstored:"signed in paris"')
    %test:assertTrue
    %test:args("hu-stored:trea* OR hu-unstored:trea*")
    %test:assertTrue
    %test:args("hu-stored:agreed OR hu-unstored:agreed")
    %test:assertTrue
    %test:pending("a field that is not stored cannot be highlighted yet; added in a following change")
function hu:unstored-as-stored($query as xs:string) {
    let $stored := hu:highlight($query, "hu-stored")
    let $unstored := hu:highlight($query, "hu-unstored")
    return exists($stored) and deep-equal($stored, $unstored)
};

declare
    %test:assertEquals(
        "<exist:field xmlns:exist=""http://exist.sourceforge.net/NS/exist""><exist:match>Treaty</exist:match> of Paris The <exist:match>treaty</exist:match> was signed in Paris. Both parties agreed.</exist:field>",
        "<exist:field xmlns:exist=""http://exist.sourceforge.net/NS/exist"">Peace of Paris Another <exist:match>treaty</exist:match>.</exist:field>")
    %test:pending("a field that is not stored cannot be highlighted yet; added in a following change")
function hu:ignored-and-inline-elements() {
    (: the note is ignored, and the inline hi is part of its word; the head's "Treaty" is a match too :)
    hu:highlight("hu-unstored:treaty", "hu-unstored")
};

declare
    %test:args("hu-head-stored:paris OR hu-head-unstored:paris")
    %test:assertTrue
    %test:pending("a field that is not stored cannot be highlighted yet; added in a following change")
function hu:unstored-expression-as-stored($query as xs:string) {
    let $stored := hu:highlight($query, "hu-head-stored")
    let $unstored := hu:highlight($query, "hu-head-unstored")
    return exists($stored) and deep-equal($stored, $unstored)
};

declare
    %test:assertEquals(
        "<exist:field xmlns:exist=""http://exist.sourceforge.net/NS/exist"">Treaty of <exist:match>Paris</exist:match></exist:field>",
        "<exist:field xmlns:exist=""http://exist.sourceforge.net/NS/exist"">Peace of <exist:match>Paris</exist:match></exist:field>")
    %test:pending("a field that is not stored cannot be highlighted yet; added in a following change")
function hu:unstored-expression() {
    hu:highlight("hu-head-unstored:paris", "hu-head-unstored")
};

declare
    %test:assertEquals(1, 0)
    %test:pending("a field that is not stored cannot be highlighted yet; added in a following change")
function hu:unstored-condition() {
    (: the field has a value for the div with a date, as when indexed, and none for the other :)
    for $div in collection($hu:COLLECTION)//div[ft:query(., "hu-unstored:paris")]
    order by $div/@id
    return count(ft:highlight-field-matches($div, "hu-dated-unstored"))
};

declare
    %test:assertEmpty
function hu:node-without-a-match() {
    ft:highlight-field-matches(collection($hu:COLLECTION)//div, "hu-unstored")
};
