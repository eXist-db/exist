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
 : A Lucene field or facet expression with a predicate on an attribute that also has a
 : range index (<range><create qname="@type"/></range>) must find the same nodes as
 : without the range index.
 :
 : The same configuration without the range index ($t:COLL_NO_RANGE) is the control.
 :)
module namespace t = "http://exist-db.org/xquery/lucene/field-range-predicate";

declare namespace test = "http://exist-db.org/xquery/xqsuite";
declare namespace xmldb = "http://exist-db.org/xquery/xmldb";

import module namespace ft = "http://exist-db.org/xquery/lucene";

declare variable $t:INDEX_LIB :=
    ``[xquery version "3.1";

module namespace frpl="http://exist-db.org/xquery/lucene/field-range-predicate-lib";

declare function frpl:type($entry as element()) as element()* {
    $entry/gram[@type = 'pos']
};

declare function frpl:type-param($entry as element(), $type as xs:string) as element()* {
    $entry/gram[@type = $type]
};

declare function frpl:subtype($entry as element()) as element()* {
    $entry/gram[@subtype = 'noun']
};

declare function frpl:lang($entry as element()) as element()* {
    $entry/def[@xml:lang = 'en']
};

declare function frpl:lang-self($entry as element()) as element()* {
    $entry[@xml:lang = 'cs']/form
};

declare function frpl:lang-value($entry as element()) as attribute()* {
    $entry/def/@xml:lang
};
]``;

declare variable $t:LIB_NAME := "frp-lib.xql";

declare variable $t:XML := document {
    <root>
        <entry xml:lang="cs">
            <form type="lemma">dům</form>
            <gram type="pos" subtype="noun" n="1">noun</gram>
            <def xml:lang="en">house</def>
        </entry>
    </root>
};

declare variable $t:COLL := "/db/test-field-range-predicate";
declare variable $t:COLL_NO_RANGE := "/db/test-field-range-predicate-no-range";

declare function t:conf-coll($coll as xs:string) as xs:string {
    "/db/system/config/db/" || substring-after($coll, "/db/")
};

declare function t:xconf($range as xs:boolean) as element() {
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <module uri="http://exist-db.org/xquery/lucene/field-range-predicate-lib" prefix="frpl" at="frp-lib.xql"/>
                <text qname="entry">
                    <!-- predicates on range-indexed attributes in the expression -->
                    <field name="inline-type"          expression="gram[@type = 'pos']"/>
                    <field name="inline-type-value"    expression="gram[@type eq 'pos']"/>
                    <field name="inline-type-sequence" expression="gram[@type = ('pos', 'x')]"/>
                    <field name="inline-subtype"       expression="gram[@subtype = 'noun']"/>
                    <field name="inline-lang"          expression="def[@xml:lang = 'en']"/>
                    <field name="inline-lang-self"     expression=".[@xml:lang = 'cs']/form"/>
                    <facet dimension="inline-type"     expression="gram[@type = 'pos']"/>
                    <facet dimension="inline-lang"     expression="def[@xml:lang = 'en']"/>
                    <!-- the same predicates in a library function -->
                    <field name="module-type"          expression="frpl:type(.)"/>
                    <field name="module-type-param"    expression="frpl:type-param(., 'pos')"/>
                    <field name="module-subtype"       expression="frpl:subtype(.)"/>
                    <field name="module-lang"          expression="frpl:lang(.)"/>
                    <field name="module-lang-self"     expression="frpl:lang-self(.)"/>
                    <facet dimension="module-type"     expression="frpl:type(.)"/>
                    <!-- baselines: no comparison on a range-indexed attribute -->
                    <field name="string-subtype"       expression="gram[string(@subtype) = 'noun']"/>
                    <field name="string-lang"          expression="def[string(@xml:lang) = 'en']"/>
                    <field name="lang-value"           expression="def/@xml:lang"/>
                    <field name="module-lang-value"    expression="frpl:lang-value(.)"/>
                    <field name="other-attr"           expression="gram[@n = '1']"/>
                    <field name="exists"               expression="gram[@type]"/>
                    <field name="no-predicate"         expression="gram"/>
                </text>
            </lucene>
            {
                if ($range) then
                    <range>
                        <create qname="@type" type="xs:string"/>
                        <create qname="@subtype" type="xs:string"/>
                        <create qname="@xml:lang" type="xs:string"/>
                    </range>
                else ()
            }
        </index>
    </collection>
};

declare
    %test:setUp
function t:setup() {
    let $_ := (xmldb:create-collection("/db/system", "config"), xmldb:create-collection("/db/system/config", "db"))
    for $coll in ($t:COLL, $t:COLL_NO_RANGE)
    let $_ := xmldb:create-collection("/db", substring-after($coll, "/db/"))
    let $_ := xmldb:create-collection("/db/system/config/db", substring-after($coll, "/db/"))
    return (
        xmldb:store($coll, $t:LIB_NAME, $t:INDEX_LIB, "application/xquery"),
        xmldb:store(t:conf-coll($coll), "collection.xconf", t:xconf($coll eq $t:COLL)),
        xmldb:store($coll, "test.xml", $t:XML)
    )
};

declare
    %test:tearDown
function t:tearDown() {
    for $coll in ($t:COLL, $t:COLL_NO_RANGE, t:conf-coll($t:COLL), t:conf-coll($t:COLL_NO_RANGE))
    return
        if (xmldb:collection-available($coll)) then xmldb:remove($coll) else ()
};

declare function t:field($coll as xs:string, $field as xs:string) as xs:string* {
    collection($coll)//entry[ft:query(., (), map { "fields": $field })] ! ft:field(., $field)
};

declare function t:facet($coll as xs:string, $dimension as xs:string) as xs:string {
    serialize(ft:facets(collection($coll)//entry[ft:query(., ())], $dimension, ()), map { "method": "json" })
};

(:~ --- predicates in the expression (range index) --- :)

declare %test:assertEquals("noun")  function t:inline-type()          { t:field($t:COLL, "inline-type") };
declare %test:assertEquals("noun")  function t:inline-type-value()    { t:field($t:COLL, "inline-type-value") };
declare %test:assertEquals("noun")  function t:inline-type-sequence() { t:field($t:COLL, "inline-type-sequence") };
declare %test:assertEquals("noun")  function t:inline-subtype()       { t:field($t:COLL, "inline-subtype") };
declare %test:assertEquals("house") function t:inline-lang()          { t:field($t:COLL, "inline-lang") };
declare %test:assertEquals("dům")   function t:inline-lang-self()     { t:field($t:COLL, "inline-lang-self") };
declare %test:assertEquals('{"noun":1}')  function t:inline-type-facet() { t:facet($t:COLL, "inline-type") };
declare %test:assertEquals('{"house":1}') function t:inline-lang-facet() { t:facet($t:COLL, "inline-lang") };

(:~ --- predicates in a library function (range index) --- :)

declare %test:assertEquals("noun")  function t:module-type()       { t:field($t:COLL, "module-type") };
declare %test:assertEquals("noun")  function t:module-type-param() { t:field($t:COLL, "module-type-param") };
declare %test:assertEquals("noun")  function t:module-subtype()    { t:field($t:COLL, "module-subtype") };
declare %test:assertEquals("house") function t:module-lang()       { t:field($t:COLL, "module-lang") };
declare %test:assertEquals("dům")   function t:module-lang-self()  { t:field($t:COLL, "module-lang-self") };
declare %test:assertEquals('{"noun":1}') function t:module-type-facet() { t:facet($t:COLL, "module-type") };

(:~ --- the same after xmldb:reindex --- :)

declare
    %test:assertEquals("noun", "house", "noun", "house")
function t:after-reindex() {
    if (xmldb:reindex($t:COLL)) then
        ("inline-type", "inline-lang", "module-type", "module-lang") ! t:field($t:COLL, .)
    else ()
};

(:~ --- baselines with the range index --- :)

declare %test:assertEquals("noun")  function t:string-subtype()    { t:field($t:COLL, "string-subtype") };
declare %test:assertEquals("house") function t:string-lang()       { t:field($t:COLL, "string-lang") };
declare %test:assertEquals("en")    function t:lang-value()        { t:field($t:COLL, "lang-value") };
declare %test:assertEquals("en")    function t:module-lang-value() { t:field($t:COLL, "module-lang-value") };
declare %test:assertEquals("noun")  function t:other-attribute()   { t:field($t:COLL, "other-attr") };
declare %test:assertEquals("noun")  function t:attribute-exists()  { t:field($t:COLL, "exists") };
declare %test:assertEquals("noun")  function t:no-predicate()      { t:field($t:COLL, "no-predicate") };

(:~ The range index is filled and used by ordinary queries. :)
declare %test:assertEquals(1, 1, 1) function t:queries-with-range-index() {
    count(collection($t:COLL)//gram[@type = 'pos']),
    count(collection($t:COLL)//gram[@subtype = 'noun']),
    count(collection($t:COLL)//def[@xml:lang = 'en'])
};

(:~ --- control: the same configuration without the range index --- :)

declare %test:assertEquals("noun")  function t:no-range-inline-type()      { t:field($t:COLL_NO_RANGE, "inline-type") };
declare %test:assertEquals("noun")  function t:no-range-inline-subtype()   { t:field($t:COLL_NO_RANGE, "inline-subtype") };
declare %test:assertEquals("house") function t:no-range-inline-lang()      { t:field($t:COLL_NO_RANGE, "inline-lang") };
declare %test:assertEquals("dům")   function t:no-range-inline-lang-self() { t:field($t:COLL_NO_RANGE, "inline-lang-self") };
declare %test:assertEquals("noun")  function t:no-range-module-type()      { t:field($t:COLL_NO_RANGE, "module-type") };
declare %test:assertEquals("noun")  function t:no-range-module-subtype()   { t:field($t:COLL_NO_RANGE, "module-subtype") };
declare %test:assertEquals("house") function t:no-range-module-lang()      { t:field($t:COLL_NO_RANGE, "module-lang") };
declare %test:assertEquals('{"noun":1}') function t:no-range-inline-type-facet() { t:facet($t:COLL_NO_RANGE, "inline-type") };
