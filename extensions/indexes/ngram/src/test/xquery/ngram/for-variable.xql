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
 : Regression tests for GH-2204: ngram:contains() must not raise XPTY0004
 : when a for-bound variable is used as the query string argument.
 :
 : The bug triggers when:
 : 1. The for-expression iterates over a persistent node set from the database
 :    (WhereClause.preEval() requires in.isPersistentSet()).
 : 2. The where clause path starts from a variable (not collection() directly),
 :    so the where expression does not have a CONTEXT_ITEM dependency (which
 :    would prevent preEval from running).
 :
 : This mirrors the original issue's query pattern which uses a module-level
 : variable for the collection.
 :)
module namespace fv="http://exist-db.org/xquery/ngram/for-variable/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $fv:XCONF as element(collection) :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index>
            <ngram qname="item"/>
        </index>
    </collection>;

declare variable $fv:XML as document-node() :=
    document {
        <test>
            <item id="1"><description>Chair</description></item>
            <item id="2"><description>Table</description></item>
            <item id="3"><description>Cabinet</description></item>
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

declare variable $fv:COLLECTION_NAME := "ngram-for-var-test";
declare variable $fv:COLLECTION := "/db/" || $fv:COLLECTION_NAME;

(: Module-level variable holding the collection — mirrors the original GH-2204
 : pattern. Using a variable instead of collection() directly is critical:
 : collection() is a Function whose getDependencies() returns CONTEXT_ITEM,
 : which would prevent WhereClause.preEval() from running. A variable
 : reference does NOT include CONTEXT_ITEM. :)
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
 : GH-2204: for-bound variable in a where clause using a module-level
 : collection variable. This is the pattern from the original bug report.
 :)
declare
    %test:pending
    %test:assertEquals("Chair", "Table")
function fv:for-variable-in-where-clause() {
    for $q in doc($fv:COLLECTION || "/queries.xml")//q
    where $fv:ITEMS//item[ngram:contains(., $q)]
    return string($q)
};

(:~
 : GH-2204: ngram:contains() with a for-bound variable (from a persistent
 : node set) as the query string must not raise XPTY0004.
 :)
declare
    %test:assertEquals(2)
function fv:for-variable-in-contains() {
    let $items := $fv:ITEMS
    return count(
        for $q in doc($fv:COLLECTION || "/queries.xml")//q
        return $items//item[ngram:contains(., $q)]
    )
};

(:~
 : Sanity check: let-bound variable should continue to work (optimization path).
 :)
declare
    %test:assertEquals(1)
function fv:let-variable-in-contains() {
    let $q := "Chair"
    return count($fv:ITEMS//item[ngram:contains(., $q)])
};
