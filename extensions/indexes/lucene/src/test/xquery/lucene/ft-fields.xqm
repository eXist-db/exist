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
 : Tests for ft:fields — schema introspection of the configured Lucene fields/facets in a scope.
 :)
module namespace ff = "http://exist-db.org/xquery/lucene/test/fields";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

import module namespace ft = "http://exist-db.org/xquery/lucene";

declare variable $ff:COLLECTION := "/db/lucene-test-fields";
declare variable $ff:CONFIG := "/db/system/config/db/lucene-test-fields";

declare variable $ff:XCONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <lucene>
                <analyzer class="org.apache.lucene.analysis.standard.StandardAnalyzer"/>
                <analyzer id="simple" class="org.apache.lucene.analysis.core.SimpleAnalyzer"/>
                <text qname="para">
                    <field name="content" expression="."/>
                    <field name="heading" expression="ancestor::article/title" store="no"/>
                    <field name="year" type="xs:integer" expression="ancestor::article/@year"/>
                    <field name="ident" analyzer="simple" expression="ancestor::article/@year"/>
                    <facet dimension="kind" expression="'para'"/>
                </text>
                <text qname="caption">
                    <field name="content" expression="."/>
                    <facet dimension="kind" expression="'caption'"/>
                </text>
                <text qname="bare"/>
            </lucene>
        </index>
    </collection>;

declare variable $ff:DOC :=
    <article year="2024">
        <title>Working with arrays</title>
        <section><para>some content</para></section>
        <figure><caption>a caption</caption></figure>
    </article>;

(: two sub-collections, each with its OWN config on its own data collection (the real producer layout),
   to exercise cross-collection aggregation of ft:fields over a parent scope. :)
declare variable $ff:MULTI := "/db/lucene-test-fields-multi";
declare variable $ff:MULTI_CONFIG := "/db/system/config/db/lucene-test-fields-multi";

declare variable $ff:XCONF_A :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index><lucene><text qname="row"><field name="alpha-field" expression="."/></text></lucene></index>
    </collection>;
declare variable $ff:XCONF_B :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index><lucene><text qname="row"><field name="beta-field" expression="."/></text></lucene></index>
    </collection>;

declare
    %test:setUp
function ff:setup() {
    let $_ := (xmldb:create-collection("/db/system", "config"), xmldb:create-collection("/db/system/config", "db"))
    let $conf := xmldb:create-collection("/db/system/config/db", "lucene-test-fields")
    let $col := xmldb:create-collection("/db", "lucene-test-fields")
    (: multi-collection fixture :)
    let $mConfRoot := xmldb:create-collection("/db/system/config/db", "lucene-test-fields-multi")
    let $mConfA := xmldb:create-collection($ff:MULTI_CONFIG, "a")
    let $mConfB := xmldb:create-collection($ff:MULTI_CONFIG, "b")
    let $mRoot := xmldb:create-collection("/db", "lucene-test-fields-multi")
    let $mA := xmldb:create-collection($ff:MULTI, "a")
    let $mB := xmldb:create-collection($ff:MULTI, "b")
    return (
        xmldb:store($conf, "collection.xconf", $ff:XCONF),
        xmldb:store($col, "doc.xml", $ff:DOC),
        xmldb:reindex($col),
        xmldb:store($mConfA, "collection.xconf", $ff:XCONF_A),
        xmldb:store($mConfB, "collection.xconf", $ff:XCONF_B),
        xmldb:store($ff:MULTI || "/a", "a.xml", <rows><row>alpha content</row></rows>),
        xmldb:store($ff:MULTI || "/b", "b.xml", <rows><row>beta content</row></rows>),
        xmldb:reindex($ff:MULTI || "/a"),
        xmldb:reindex($ff:MULTI || "/b")
    )
};

declare
    %test:tearDown
function ff:tearDown() {
    if (xmldb:collection-available($ff:COLLECTION)) then xmldb:remove($ff:COLLECTION) else (),
    if (xmldb:collection-available($ff:CONFIG)) then xmldb:remove($ff:CONFIG) else (),
    if (xmldb:collection-available($ff:MULTI)) then xmldb:remove($ff:MULTI) else (),
    if (xmldb:collection-available($ff:MULTI_CONFIG)) then xmldb:remove($ff:MULTI_CONFIG) else ()
};

(: every entry is a map carrying at least field/element/kind :)
declare
    %test:assertTrue
function ff:entries-are-shaped-maps() {
    let $entries := ft:fields($ff:COLLECTION)
    return exists($entries) and (every $e in $entries satisfies
        $e instance of map(*) and map:contains($e, "field") and map:contains($e, "element") and map:contains($e, "kind"))
};

(: 5 configured fields: para/{content,heading,year,ident} + caption/content :)
declare
    %test:assertEquals(5)
function ff:field-count() {
    count(ft:fields($ff:COLLECTION)[?kind = "field"])
};

(: 2 configured facets: kind on para and on caption :)
declare
    %test:assertEquals(2)
function ff:facet-count() {
    count(ft:fields($ff:COLLECTION)[?kind = "facet"])
};

(: a string field reports its element, type and that it is returnable (stored) :)
declare
    %test:assertEquals("xs:string")
function ff:content-field-type() {
    ft:fields($ff:COLLECTION)[?field = "content" and ?element = "para"]?type
};

declare
    %test:assertTrue
function ff:content-field-returnable() {
    ft:fields($ff:COLLECTION)[?field = "content" and ?element = "para"]?returnable
};

(: store="no" is reported as not returnable :)
declare
    %test:assertTrue
function ff:heading-not-returnable() {
    not(ft:fields($ff:COLLECTION)[?field = "heading"]?returnable)
};

(: a typed field reports its declared XDM type :)
declare
    %test:assertEquals("xs:integer")
function ff:typed-field-type() {
    ft:fields($ff:COLLECTION)[?field = "year"]?type
};

(: the effective analyzer is reported (inherited from the index when the field has none) :)
declare
    %test:assertTrue
function ff:field-reports-analyzer() {
    contains(ft:fields($ff:COLLECTION)[?field = "content" and ?element = "para"]?analyzer, "StandardAnalyzer")
};

(: a field's analyzer-id (analyzer="simple") is resolved to the concrete class, not reported verbatim,
   and is distinct from the default-analyzer fields on the same element (R3a / per-element variance) :)
declare
    %test:assertTrue
function ff:analyzer-id-resolved-to-class() {
    contains(ft:fields($ff:COLLECTION)[?field = "ident" and ?element = "para"]?analyzer, "SimpleAnalyzer")
};

(: a facet entry is kind=facet with its dimension as the field name; no type/returnable :)
declare
    %test:assertTrue
function ff:facet-entry-shape() {
    let $facet := ft:fields($ff:COLLECTION)[?kind = "facet" and ?element = "para"]
    return $facet?field = "kind" and not(map:contains($facet, "type"))
};

(: R1: permission-agnostic. The config lives under admin-only /db/system/config, but ft:fields reads
   the resolved config via the broker, so a non-dba (guest) sees the configured fields
   — existdb-openapi applies field-level security on top. :)
declare
    %test:assertEquals(5)
function ff:permission-agnostic-guest() {
    system:as-user("guest", "guest", count(ft:fields($ff:COLLECTION)[?kind = "field"]))
};

(: follow-up 1: ft:fields aggregates across every collection in scope — a parent scope returns the
   UNION of each sub-collection's own config (alpha-field from a/, beta-field from b/) :)
declare
    %test:assertTrue
function ff:cross-collection-union() {
    let $fields := ft:fields($ff:MULTI)[?kind = "field"]?field
    return ("alpha-field" = $fields) and ("beta-field" = $fields)
};

(: follow-up 2: a plain <text qname="bare"/> with no named field/facet contributes NO ft:fields record
   — it indexes the element content but exposes no named, queryable field. (Non-field/facet config
   entries that DO exist, e.g. vector fields, are emitted and self-distinguished by kind, so every
   emitted map carries field + kind — no consumer special-casing of a missing key.) :)
declare
    %test:assertEquals(0)
function ff:bare-text-no-record() {
    count(ft:fields($ff:COLLECTION)[?element = "bare"])
};

(: every emitted map carries both field and kind (no "absence of a key" to special-case) :)
declare
    %test:assertTrue
function ff:every-map-has-field-and-kind() {
    every $e in ft:fields($ff:COLLECTION) satisfies map:contains($e, "field") and map:contains($e, "kind")
};

(: empty/unknown scope yields no entries (no error) :)
declare
    %test:assertEquals(0)
function ff:unknown-scope-empty() {
    count(ft:fields("/db/lucene-test-fields-nonexistent"))
};
