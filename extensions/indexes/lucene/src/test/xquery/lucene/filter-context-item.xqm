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
 : A filter expression on a parenthesized expression, e.g. (ancestor::entry)[1] or
 : (def, def)[2], must give the same result for a stored node as for the same node in
 : memory. For a stored context node it doesn't when the parenthesized expression is
 : evaluated relative to the context item (after "!" or "/", or in a Lucene field
 : expression): the predicate seems to be applied to the steps one context node at a
 : time instead of to the whole sequence. Positional predicates then find nothing on
 : the reverse and sibling axes, [2] and [last()] find nothing on the child axis, and
 : the sequence loses its order and duplicates. 6.4.1 and 7.0.0-SNAPSHOT behave the same.
 :
 : The same expressions on in-memory nodes, with the context node in a variable outside
 : the expression, or with head(), subsequence() and [position() = 1] work.
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

(:~ The senses of the stored document and of the same document in memory. :)
declare function t:stored() as element(sense)* {
    doc($t:COLL || "/test.xml")//sense
};

declare function t:memory() as element(sense)* {
    $t:XML//sense
};

(:~ @n of each node, joined with "|". :)
declare function t:n($nodes as node()*) as xs:string {
    string-join($nodes ! string(@n), "|")
};

declare function t:field($field as xs:string) as xs:string {
    string-join(collection($t:COLL)//sense[ft:query(., (), map { "fields": $field })] ! ft:field(., $field), "|")
};

(:~ --- stored nodes, parenthesized expression relative to the context item --- :)

declare %test:assertEquals("outer|inner") function t:stored-parent()            { t:n(t:stored() ! (parent::*)[1]) };
declare %test:assertEquals("outer|outer") function t:stored-ancestor()          { t:n(t:stored() ! (ancestor::entry)[1]) };
declare %test:assertEquals("outer|inner") function t:stored-ancestor-1()        { t:n(t:stored() ! (ancestor::entry[1])[1]) };
declare %test:assertEquals("outer|outer") function t:stored-sequence()          { t:n(t:stored() ! (ancestor::entry[@sortKey][1], ancestor::entry[1])[1]) };
declare %test:assertEquals("outer|inner") function t:stored-ancestor-last()     { t:n(t:stored() ! (ancestor::*)[last()]) };
declare %test:assertEquals("root|root")   function t:stored-ancestor-or-self()  { t:n(t:stored() ! (ancestor-or-self::*)[1]) };
declare %test:assertEquals("inner")       function t:stored-following-sibling() { t:n(t:stored() ! (following-sibling::*)[1]) };
declare %test:assertEquals("d1|d2")       function t:stored-child-2()           { t:n(t:stored() ! (def, def)[2]) };
declare %test:assertEquals("d1|d2")       function t:stored-child-last()        { t:n(t:stored() ! (def, def)[last()]) };
declare %test:assertEquals("d1|d2")       function t:stored-order()             { t:n(t:stored() ! (def, .)[1]) };
declare
    %test:pending("a non-positional predicate on a sequence of stored nodes is evaluated as a node set, losing duplicates and order; to be fixed separately")
    %test:assertEquals("d1|d1|d2|d2") function t:stored-duplicates()        { t:n(t:stored() ! (def, def)[@n]) };
declare %test:assertEquals("outer|inner") function t:stored-path-step()         { t:n(t:stored()/(ancestor::entry[1], ancestor::entry[1])[1]) };
declare %test:assertEquals("outer|inner") function t:stored-let()               { t:n(t:stored() ! (let $s := . return ($s/ancestor::entry[1], $s/ancestor::entry[1])[1])) };

(:~ --- the same expressions in memory (control) --- :)

declare %test:assertEquals("outer|inner") function t:memory-parent()            { t:n(t:memory() ! (parent::*)[1]) };
declare %test:assertEquals("outer|outer") function t:memory-ancestor()          { t:n(t:memory() ! (ancestor::entry)[1]) };
declare %test:assertEquals("outer|outer") function t:memory-sequence()          { t:n(t:memory() ! (ancestor::entry[@sortKey][1], ancestor::entry[1])[1]) };
declare %test:assertEquals("d1|d2")       function t:memory-child-2()           { t:n(t:memory() ! (def, def)[2]) };
declare %test:assertEquals("d1|d2")       function t:memory-order()             { t:n(t:memory() ! (def, .)[1]) };

(:~ --- stored nodes, variants that work --- :)

declare %test:assertEquals("outer|inner") function t:stored-no-parentheses()    { t:n(t:stored() ! ancestor::entry[1]) };
declare %test:assertEquals("outer|outer") function t:stored-head()              { t:n(t:stored() ! head((ancestor::entry[@sortKey][1], ancestor::entry[1]))) };
declare %test:assertEquals("outer|outer") function t:stored-subsequence()       { t:n(t:stored() ! subsequence((ancestor::entry[@sortKey][1], ancestor::entry[1]), 1, 1)) };
declare %test:assertEquals("outer|outer") function t:stored-position()          { t:n(t:stored() ! (ancestor::entry)[position() = 1]) };
declare %test:assertEquals("outer|outer") function t:stored-variable()          { t:n(for $s in t:stored() return ($s/ancestor::entry[@sortKey][1], $s/ancestor::entry[1])[1]) };
declare %test:assertEquals("d1|d2")       function t:stored-child-1()           { t:n(t:stored() ! (def, def)[1]) };

(:~ --- Lucene field expressions (the context item is the indexed node) --- :)

declare %test:assertEquals("outer|outer") function t:field-filter() { t:field("filter") };
declare %test:assertEquals("outer|outer") function t:field-head()   { t:field("head") };
declare %test:assertEquals("outer|inner") function t:field-step()   { t:field("step") };
