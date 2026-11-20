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
 : A Lucene field expression is evaluated with the indexed node as the context item. A
 : filter expression on a parenthesized expression in it, e.g.
 : (ancestor::entry[@sortKey][1], ancestor::entry[1])[1], must apply its predicate to the
 : whole sequence, as head() does, and not count positions per context node.
 : See https://github.com/eXist-db/exist/issues/6831; the same expressions outside of
 : field definitions are tested in exist-core, filter-expression-persistent.xqm.
 :)
module namespace t = "http://exist-db.org/xquery/test/filter-context-item";

declare namespace test = "http://exist-db.org/xquery/xqsuite";
declare namespace xmldb = "http://exist-db.org/xquery/xmldb";

import module namespace ft = "http://exist-db.org/xquery/lucene";

declare variable $t:COLL := "/db/test-filter-context-item";
declare variable $t:CONF_COLL := "/db/system/config/db/test-filter-context-item";

declare variable $t:XML :=
    <root n="root">
        <entry n="outer" sortKey="o">
            <sense n="s1"><def n="d1">house</def></sense>
            <entry n="inner">
                <sense n="s2"><def n="d2">home</def></sense>
            </entry>
        </entry>
    </root>;

declare variable $t:XCONF :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <lucene>
                <text qname="sense">
                    <field name="filter" expression="(ancestor::entry[@sortKey][1], ancestor::entry[1])[1]/@n"/>
                    <field name="head"   expression="head((ancestor::entry[@sortKey][1], ancestor::entry[1]))/@n"/>
                    <field name="step"   expression="ancestor::entry[1]/@n"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare
    %test:setUp
function t:setup() {
    let $_ := (xmldb:create-collection("/db/system", "config"), xmldb:create-collection("/db/system/config", "db"))
    let $_ := xmldb:create-collection("/db/system/config/db", "test-filter-context-item")
    let $_ := xmldb:store($t:CONF_COLL, "collection.xconf", $t:XCONF)
    let $_ := xmldb:create-collection("/db", "test-filter-context-item")
    return xmldb:store($t:COLL, "test.xml", $t:XML)
};

declare
    %test:tearDown
function t:tearDown() {
    for $coll in ($t:COLL, $t:CONF_COLL)
    return
        if (xmldb:collection-available($coll)) then xmldb:remove($coll) else ()
};

declare function t:field($field as xs:string) as xs:string {
    string-join(collection($t:COLL)//sense[ft:query(., (), map { "fields": $field })] ! ft:field(., $field), "|")
};

(:~ --- Lucene field expressions (the context item is the indexed node) --- :)

declare %test:assertEquals("outer|outer") function t:field-filter() { t:field("filter") };
declare %test:assertEquals("outer|outer") function t:field-head()   { t:field("head") };
declare %test:assertEquals("outer|inner") function t:field-step()   { t:field("step") };
