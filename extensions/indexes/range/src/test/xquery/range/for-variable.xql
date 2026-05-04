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
 : Regression tests for GH-2204: range:eq() and range:field-eq() must not
 : raise XPTY0004 when a for-bound variable is used as the query argument.
 :
 : The bug triggers when:
 : 1. The for-expression iterates over a persistent node set from the database
 :    (WhereClause.preEval() requires in.isPersistentSet()).
 : 2. The where clause path starts from a variable (not collection() directly),
 :    so the where expression does not have a CONTEXT_ITEM dependency (which
 :    would prevent preEval from running).
 :)
module namespace fv="http://exist-db.org/xquery/range/for-variable/test";

import module namespace range="http://exist-db.org/xquery/range" at "java:org.exist.xquery.modules.range.RangeIndexModule";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $fv:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <range>
                <create qname="name" type="xs:string"/>
                <create match="//item">
                    <field name="item-name" match="name" type="xs:string"/>
                </create>
            </range>
        </index>
    </collection>;

declare variable $fv:XML as document-node() :=
    document {
        <test>
            <item id="1"><name>Chair</name></item>
            <item id="2"><name>Table</name></item>
            <item id="3"><name>Cabinet</name></item>
        </test>
    };

(: A second document whose elements serve as for-iteration input (persistent nodes). :)
declare variable $fv:QUERIES as document-node() :=
    document {
        <queries>
            <q>Chair</q>
            <q>Table</q>
            <q>NoMatch</q>
        </queries>
    };

declare variable $fv:COLLECTION_NAME := "range-for-var-test";
declare variable $fv:COLLECTION := "/db/" || $fv:COLLECTION_NAME;

(: Module-level variable holding the collection — mirrors the original GH-2204
 : pattern. Using a variable instead of collection() directly is critical:
 : collection() is a Function whose getDependencies() returns CONTEXT_ITEM,
 : which would prevent WhereClause.preEval() from running. :)
declare variable $fv:ITEMS := collection($fv:COLLECTION);

declare
    %test:setUp
function fv:setUp() {
    ( xmldb:create-collection("/db/system", "config"),
      xmldb:create-collection("/db/system/config", "db"),
      xmldb:create-collection("/db", $fv:COLLECTION_NAME),
      xmldb:create-collection("/db/system/config/db", $fv:COLLECTION_NAME),
      xmldb:store("/db/system/config/db/" || $fv:COLLECTION_NAME, "collection.xconf", $fv:XCONF),
      xmldb:store($fv:COLLECTION, "test.xml", $fv:XML),
      xmldb:store($fv:COLLECTION, "queries.xml", $fv:QUERIES),
      xmldb:reindex($fv:COLLECTION) )
};

declare
    %test:tearDown
function fv:tearDown() {
    ( xmldb:remove($fv:COLLECTION),
      xmldb:remove("/db/system/config/db/" || $fv:COLLECTION_NAME) )
};

(:~
 : GH-2204: range:eq() with a for-bound variable in a where clause.
 :)
declare
    %test:assertEquals("Chair", "Table")
function fv:for-variable-in-where-clause() {
    for $q in doc($fv:COLLECTION || "/queries.xml")//q
    where $fv:ITEMS//item[range:eq(name, $q)]
    return string($q)
};

(:~
 : GH-2204: range:field-eq() with a for-bound variable in a where clause.
 :)
declare
    %test:assertTrue
function fv:for-variable-in-field-eq() {
    let $results :=
        for $q in doc($fv:COLLECTION || "/queries.xml")//q
        where $fv:ITEMS//range:field-eq("item-name", $q)
        return $q
    return count($results) > 0
};

(:~
 : Sanity check: let-bound variable should continue to work (optimization path).
 :)
declare
    %test:assertEquals(1)
function fv:let-variable-in-range-eq() {
    let $q := "Chair"
    return count($fv:ITEMS//item[range:eq(name, $q)])
};
