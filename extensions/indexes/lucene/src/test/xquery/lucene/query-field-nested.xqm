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
 : ft:query-field finds a context node through a match at any depth. Here only the nested sec's field holds the term:
 : the sec around it is found through that match, and neither hides the other when both are context nodes. A context
 : node of another name is found only if it is the nearest, so that //*[...] does not give all the ancestors of a match.
 :)
module namespace qfn="http://exist-db.org/xquery/lucene/query-field-nested/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $qfn:COLLECTION_NAME := "test-query-field-nested";
declare variable $qfn:COLLECTION := "/db/" || $qfn:COLLECTION_NAME;
declare variable $qfn:CONF := "/db/system/config/db/" || $qfn:COLLECTION_NAME;

declare variable $qfn:XCONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <lucene>
                <text qname="sec">
                    <field name="qfn-title" expression="title"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare
    %test:setUp
function qfn:setUp() {
    xmldb:create-collection("/db/system", "config"),
    xmldb:create-collection("/db/system/config", "db"),
    xmldb:create-collection("/db/system/config/db", $qfn:COLLECTION_NAME),
    xmldb:store($qfn:CONF, "collection.xconf", $qfn:XCONF),
    xmldb:create-collection("/db", $qfn:COLLECTION_NAME),
    xmldb:store($qfn:COLLECTION, "nested.xml",
        <root>
            <sec n="outer"><title>other</title><sec n="inner"><title>term</title></sec></sec>
            <sec n="unrelated"><title>other</title></sec>
        </root>)
};

declare
    %test:tearDown
function qfn:tearDown() {
    xmldb:remove($qfn:COLLECTION),
    xmldb:remove($qfn:CONF)
};

declare
    %test:assertEquals("inner", "outer")
function qfn:descendant-step() {
    sort(collection($qfn:COLLECTION)//sec[ft:query-field("qfn-title", "term")]/@n/string())
};

declare
    %test:assertEquals("inner", "outer")
function qfn:descendant-step-after-child() {
    sort(collection($qfn:COLLECTION)/root//sec[ft:query-field("qfn-title", "term")]/@n/string())
};

declare
    %test:assertEquals("inner", "outer")
function qfn:filtering-a-variable() {
    let $secs := collection($qfn:COLLECTION)//sec
    return sort($secs[ft:query-field("qfn-title", "term")]/@n/string())
};

declare
    %test:assertEquals("outer")
function qfn:child-step() {
    collection($qfn:COLLECTION)/root/sec[ft:query-field("qfn-title", "term")]/@n/string()
};

declare
    %test:assertEquals("inner")
function qfn:innermost-step() {
    collection($qfn:COLLECTION)/root/sec/sec[ft:query-field("qfn-title", "term")]/@n/string()
};

(: a wildcard step is evaluated as written, with or without the optimizer :)
declare
    %test:assertEquals("outer")
function qfn:wildcard-step() {
    collection($qfn:COLLECTION)/root/*[ft:query-field("qfn-title", "term")]/@n/string()
};

declare
    %test:assertEquals("outer")
function qfn:node-test-step() {
    collection($qfn:COLLECTION)/root/node()[ft:query-field("qfn-title", "term")]/@n/string()
};

declare
    %test:assertEquals(2, "inner", "outer")
function qfn:wildcard-descendant-step() {
    let $found := collection($qfn:COLLECTION)//*[ft:query-field("qfn-title", "term")]
    return (count($found), sort($found/@n/string()))
};

declare
    %test:assertEquals(2, "inner", "outer")
function qfn:self-step-over-all-nodes() {
    let $found := collection($qfn:COLLECTION)//.[ft:query-field("qfn-title", "term")]
    return (count($found), sort($found/@n/string()))
};

(: asked from an ancestor of another name, which is the nearest context node :)
declare
    %test:assertEquals("root")
function qfn:asking-from-an-ancestor() {
    collection($qfn:COLLECTION)/root[ft:query-field("qfn-title", "term")]/name()
};
