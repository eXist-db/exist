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
declare variable $mac:parent-more := "test-mixed-analyzer-configs-more";

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

declare variable $mac:XCONF-WHITESPACE :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <lucene>
                <analyzer id="ws" class="org.apache.lucene.analysis.core.WhitespaceAnalyzer"/>
                <text qname="p" analyzer="ws"/>
            </lucene>
        </index>
    </collection>;

(: lowercased when indexed, not in a query :)
declare variable $mac:XCONF-SPLIT :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <lucene>
                <analyzer id="split">
                    <index>
                        <tokenizer name="standard"/>
                        <filter name="lowercase"/>
                    </index>
                    <query>
                        <tokenizer name="standard"/>
                    </query>
                </analyzer>
                <text qname="p" analyzer="split"/>
            </lucene>
        </index>
    </collection>;

declare variable $mac:DOC := <div><p>The treaties were signed.</p></div>;

(: more sub-collections, apart from those of the other tests: "unconfigured" has lost its configuration, with its
 : documents still in the index :)
declare %private function mac:setup-more() {
    let $conf := xmldb:create-collection("/db/system/config/db", $mac:parent-more)
    let $col := xmldb:create-collection("/db", $mac:parent-more)
    for $sub in ("standard", "english", "whitespace", "split", "unconfigured")
    let $subConf := xmldb:create-collection($conf, $sub)
    let $_ := xmldb:store($subConf, "collection.xconf",
        switch ($sub)
            case "english" return $mac:XCONF-ENGLISH
            case "whitespace" return $mac:XCONF-WHITESPACE
            case "split" return $mac:XCONF-SPLIT
            default return $mac:XCONF-STANDARD)
    let $_ := xmldb:store(xmldb:create-collection($col, $sub), "doc.xml", $mac:DOC)
    return if ($sub = "unconfigured") then xmldb:remove($subConf, "collection.xconf") else ()
};

declare
    %test:setUp
function mac:setup() {
    let $_ := (xmldb:create-collection("/db/system", "config"), xmldb:create-collection("/db/system/config", "db"))
    let $_ := mac:setup-more()
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
    xmldb:remove($mac:config-parent),
    xmldb:remove("/db/" || $mac:parent-more),
    xmldb:remove("/db/system/config/db/" || $mac:parent-more)
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

declare
    %test:assertEquals(3)
function mac:three-configurations() {
    count((("standard", "english", "whitespace") ! collection("/db/" || $mac:parent-more || "/" || .))//p[ft:query(., "treaties")])
};

(: a collection without a configuration is searched as well, however many other configurations the query has :)
declare
    %test:assertEquals(3)
function mac:collection-without-a-configuration-over-two-others() {
    count((("standard", "english", "unconfigured") ! collection("/db/" || $mac:parent-more || "/" || .))//p[ft:query(., "treaties")])
};

(: an analyzer named by the query and defined in another configuration queries with its query chain :)
declare
    %test:assertEquals(0, 2)
function mac:query-analyzer-of-another-configuration() {
    for $query in ("TREATIES", "treaties")
    return count((("standard", "split") ! collection("/db/" || $mac:parent-more || "/" || .))//p[ft:query(., $query, map { "query-analyzer-id": "split" })])
};
