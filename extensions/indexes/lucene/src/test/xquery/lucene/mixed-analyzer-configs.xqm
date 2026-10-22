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
 : A query over a parent collection whose sub-collections index the same element, or the same
 : field, with different analyzers. Each sub-collection's documents should be found as their own
 : analyzer indexed them. "treaties" is "treaties" for the standard analyzer and "treati" for the
 : English one, and stays "treati" when stemmed again (see #6907).
 :)
module namespace mac="http://exist-db.org/xquery/lucene/test/mixed-analyzer-configs";

declare namespace test="http://exist-db.org/xquery/xqsuite";

import module namespace ft="http://exist-db.org/xquery/lucene";

declare variable $mac:parent := "test-mixed-analyzer-configs";
declare variable $mac:config-parent := "/db/system/config/db/" || $mac:parent;

declare variable $mac:XCONF-STANDARD :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <lucene>
                <text qname="p"/>
                <text qname="div">
                    <field name="body" expression="."/>
                </text>
            </lucene>
        </index>
    </collection>;

declare variable $mac:XCONF-ENGLISH :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <lucene>
                <analyzer id="en" class="org.apache.lucene.analysis.en.EnglishAnalyzer"/>
                <text qname="p" analyzer="en"/>
                <text qname="div">
                    <field name="body" expression="." analyzer="en"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare variable $mac:DOC := <div><p>The treaties were signed.</p></div>;

declare
    %test:setUp
function mac:setup() {
    let $_ := (xmldb:create-collection("/db/system", "config"), xmldb:create-collection("/db/system/config", "db"))
    let $confParent := xmldb:create-collection("/db/system/config/db", $mac:parent)
    let $parent := xmldb:create-collection("/db", $mac:parent)
    for $sub in ("standard", "english")
    let $conf := xmldb:create-collection($confParent, $sub)
    let $col := xmldb:create-collection($parent, $sub)
    return (
        xmldb:store($conf, "collection.xconf", if ($sub = "standard") then $mac:XCONF-STANDARD else $mac:XCONF-ENGLISH),
        xmldb:store($col, "doc.xml", $mac:DOC),
        if ($sub = "standard") then xmldb:store($col, "run.xml", <div><p>They run, and keep running.</p></div>) else ()
    )
};

declare
    %test:tearDown
function mac:tearDown() {
    xmldb:remove("/db/" || $mac:parent),
    xmldb:remove($mac:config-parent)
};

declare
    %test:args("standard")
    %test:assertEquals(1)
    %test:args("english")
    %test:assertEquals(1)
function mac:qname-one-collection($sub as xs:string) {
    count(collection("/db/" || $mac:parent || "/" || $sub)//p[ft:query(., "treaties")])
};

declare
    %test:assertEquals(2)
function mac:qname-both-collections() {
    count(collection("/db/" || $mac:parent)//p[ft:query(., "treaties")])
};

declare
    %test:args("standard")
    %test:assertEquals(1)
    %test:args("english")
    %test:assertEquals(1)
function mac:field-one-collection($sub as xs:string) {
    count(collection("/db/" || $mac:parent || "/" || $sub)//div[ft:query(., "body:treaties")])
};

declare
    %test:assertEquals(2)
function mac:field-both-collections() {
    count(collection("/db/" || $mac:parent)//div[ft:query(., "body:treaties")])
};

declare
    %test:assertEquals(2)
function mac:xml-query-both-collections() {
    count(collection("/db/" || $mac:parent)//p[ft:query(., <query><term>treaties</term></query>)])
};

declare
    %test:assertEquals(2)
function mac:xml-query-field-both-collections() {
    count(collection("/db/" || $mac:parent)//div[ft:query-field("body", <query><term>treaties</term></query>)])
};

declare
    %test:assertEquals(2)
function mac:query-field-both-collections() {
    count(ft:query-field("body", "treaties")[util:collection-name(.) = ("/db/" || $mac:parent || "/standard", "/db/" || $mac:parent || "/english")])
};

(: a hit's match keeps the query of its own collection's configuration: the English clause's stem "run" is not
 : highlighted in the standard collection's text, where only "running" matched :)
declare
    %test:assertEquals("running")
function mac:highlight-with-the-hits-own-configuration() {
    let $hits := collection("/db/" || $mac:parent)//p[ft:query(., "running")]
    return util:expand($hits)//exist:match/string()
};
