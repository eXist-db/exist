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
 : A value comparison in a predicate over stored nodes checks its operand's cardinality for the context nodes it
 : selects: XQuery 3.1 §2.3.4 requires XPTY0004 when a selected node's operand has several items, and allows skipping
 : nodes that cannot match. Checked where the predicate is evaluated as written; where a range index answers it, the
 : check is still missing (pending tests below). See #7002.
 :)
module namespace eqc="http://exist-db.org/xquery/range/eq-cardinality/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

(: a n=1 has two b, one of them 'v' :)
declare variable $eqc:INCLUDES := <doc><a n="1"><b>v</b><b>w</b></a><a n="2"><b>v</b></a></doc>;
(: a a inside a a, one b each: no operand has several items for a context node :)
declare variable $eqc:NESTED := <doc><a n="1"><b>v</b><a n="2"><b>v</b></a></a></doc>;
(: a n=1 has two b, neither 'v' :)
declare variable $eqc:EXCLUDES := <doc><a n="1"><b>x</b><b>y</b></a><a n="2"><b>v</b></a></doc>;

declare variable $eqc:CONFIGS := map {
    "range": <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema"><range><create qname="b" type="xs:string"/></range></index>
    </collection>,
    "legacy": <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema"><create qname="b" type="xs:string"/></index>
    </collection>,
    "field": <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema"><range><create match="//a"><field name="eqc-b" match="b" type="xs:string"/></create></range></index>
    </collection>,
    "none": ()
};

declare
    %test:setUp
function eqc:setup() {
    xmldb:create-collection("/db/system", "config"),
    xmldb:create-collection("/db/system/config", "db"),
    for $index in map:keys($eqc:CONFIGS)
    for $data in ("includes", "excludes", "nested")
    let $name := "eqc-" || $data || "-" || $index
    return (
        if (exists($eqc:CONFIGS($index))) then (
            xmldb:create-collection("/db/system/config/db", $name),
            xmldb:store("/db/system/config/db/" || $name, "collection.xconf", $eqc:CONFIGS($index))
        ) else (),
        xmldb:create-collection("/db", $name),
        xmldb:store("/db/" || $name, "d.xml", switch ($data) case "includes" return $eqc:INCLUDES case "nested" return $eqc:NESTED default return $eqc:EXCLUDES)
    )
};

declare
    %test:tearDown
function eqc:teardown() {
    for $index in map:keys($eqc:CONFIGS)
    for $data in ("includes", "excludes", "nested")
    let $name := "eqc-" || $data || "-" || $index
    return (
        xmldb:remove("/db/" || $name),
        if (xmldb:collection-available("/db/system/config/db/" || $name)) then xmldb:remove("/db/system/config/db/" || $name) else ()
    )
};

declare
    %test:pending("#7002: a range index answers the comparison without the check")
    %test:assertError("XPTY0004")
function eqc:includes-range() {
    collection("/db/eqc-includes-range")//a[b eq 'v']/@n/string()
};

declare
    %test:pending("#7002: the legacy index pre-selects the matches, and the check sees only those")
    %test:assertError("XPTY0004")
function eqc:includes-legacy() {
    collection("/db/eqc-includes-legacy")//a[b eq 'v']/@n/string()
};

declare %test:assertError("XPTY0004") function eqc:includes-no-index() {
    collection("/db/eqc-includes-none")//a[b eq 'v']/@n/string()
};

declare
    %test:pending("#7002: the field rewrite answers the comparison with a field lookup, without the check")
    %test:assertError("XPTY0004")
function eqc:includes-field() {
    collection("/db/eqc-includes-field")//a[b eq 'v']/@n/string()
};

(: a where clause is evaluated in one step over the for clause's nodes, like a predicate :)
declare %test:assertError("XPTY0004") function eqc:includes-no-index-where() {
    for $a in collection("/db/eqc-includes-none")//a where $a/b eq 'v' return $a/@n/string()
};

declare %test:assertError("XPTY0004") function eqc:includes-no-index-per-item() {
    for $a in collection("/db/eqc-includes-none")//a return $a[b eq 'v']/@n/string()
};

declare %test:assertError("XPTY0004") function eqc:includes-in-memory() {
    $eqc:INCLUDES//a[b eq 'v']/@n/string()
};

(: a n=1 cannot match: it is skipped, which §2.3.4 allows :)
declare %test:assertEquals("2") function eqc:excludes-range() {
    collection("/db/eqc-excludes-range")//a[b eq 'v']/@n/string()
};

declare %test:assertEquals("2") function eqc:excludes-legacy() {
    collection("/db/eqc-excludes-legacy")//a[b eq 'v']/@n/string()
};

declare %test:assertEquals("2") function eqc:excludes-no-index() {
    collection("/db/eqc-excludes-none")//a[b eq 'v']/@n/string()
};

(: a predicate before the comparison narrows its context to a with one b: no error :)
declare %test:assertEquals("2") function eqc:single-value-after-narrowing() {
    collection("/db/eqc-includes-range")//a[count(b) eq 1][b eq 'v']/@n/string()
};

(: the other relations check as well, for the context nodes they select :)
declare %test:assertError("XPTY0004") function eqc:ne-several-no-index() {
    collection("/db/eqc-includes-none")//a[b ne 'z']/@n/string()
};

declare %test:assertError("XPTY0004") function eqc:lt-several-no-index() {
    collection("/db/eqc-includes-none")//a[b lt 'z']/@n/string()
};

(: a context node inside another has its own operand: one item each, no error, both selected :)
declare %test:assertEquals("1", "2") function eqc:nested-context-nodes-no-index() {
    collection("/db/eqc-nested-none")//a[b eq 'v']/@n/string()
};
