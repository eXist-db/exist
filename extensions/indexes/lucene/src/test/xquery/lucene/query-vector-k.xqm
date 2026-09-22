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
 : Regression tests for https://github.com/eXist-db/exist/issues/6738 :
 : `k` must be honored by both ft:query-vector and ft:query-field-vector,
 : whether used as a path predicate or called directly, and regardless of
 : whether each candidate node lives in its own document or several
 : candidates share one document -- and regardless of collection size
 : (a collection above ~341 documents used to blow Lucene's default
 : BooleanQuery clause-count cap when queried directly).
 :)
module namespace t="http://exist-db.org/xquery/test/query-vector-k";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace xmldb="http://exist-db.org/xquery/xmldb";

import module namespace ft="http://exist-db.org/xquery/lucene";

declare variable $t:N_LARGE := 342;
declare variable $t:N_SMALL := 30;
declare variable $t:K := 5;
declare variable $t:VEC := [1.0, 0.0, 0.0, 0.0];

declare variable $t:xconf :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <text qname="article">
                    <vector-field name="embedding" expression="embedding"
                        dimension="4" similarity="cosine" encoding="text"/>
                </text>
            </lucene>
        </index>
    </collection>;

declare function t:collection-path($n as xs:integer) as xs:string {
    "/db/test-query-vector-k-" || $n
};

declare function t:config-collection-path($n as xs:integer) as xs:string {
    "/db/system/config/db/" || substring-after(t:collection-path($n), "/db/")
};

declare function t:setup-collection-of-size($n as xs:integer) {
    let $coll := t:collection-path($n)
    let $confColl := t:config-collection-path($n)
    let $_ := xmldb:create-collection("/db/system", "config")
    let $_ := xmldb:create-collection("/db/system/config", "db")
    let $_ := xmldb:create-collection("/db", substring-after($coll, "/db/"))
    let $_ := xmldb:create-collection("/db/system/config/db", substring-after($coll, "/db/"))
    let $_ := xmldb:store($confColl, "collection.xconf", $t:xconf)
    let $_ :=
        for $i in 1 to $n
        return xmldb:store(
            $coll,
            "a" || $i || ".xml",
            <article><embedding>1.0 0.0 0.0 0.0</embedding></article>
        )
    return xmldb:reindex($coll)
};

declare function t:teardown-collection-of-size($n as xs:integer) {
    let $coll := t:collection-path($n)
    let $confColl := t:config-collection-path($n)
    return (
        if (xmldb:collection-available($coll)) then xmldb:remove($coll) else (),
        if (xmldb:collection-available($confColl)) then xmldb:remove($confColl) else ()
    )
};

(: Both collection sizes are created once per test run: the large one
 : (342 docs) exercises the 341-document BooleanQuery clause-count cap,
 : the small one (30 docs) isolates the path-predicate k-is-ignored bug
 : from that cap. :)
declare
    %test:setUp
function t:setup() {
    (t:setup-collection-of-size($t:N_LARGE), t:setup-collection-of-size($t:N_SMALL))
};

declare
    %test:tearDown
function t:teardown() {
    (t:teardown-collection-of-size($t:N_LARGE), t:teardown-collection-of-size($t:N_SMALL))
};

(: ================================================================
   Large collection (342 docs, one article per document): the direct
   call form used to throw "maxClauseCount is set to 1024" past 341
   documents, and the path-predicate form used to ignore k entirely
   and return every document.
   ================================================================ :)

declare
    %test:assertEquals(5)
function t:path-form-honors-k-large() {
    count(collection(t:collection-path($t:N_LARGE))//article[ft:query-vector(., $t:VEC, $t:K)])
};

declare
    %test:assertEquals(5)
function t:direct-call-honors-k-large() {
    count(ft:query-vector(collection(t:collection-path($t:N_LARGE))//article, $t:VEC, $t:K))
};

declare
    %test:assertEquals(5)
function t:field-path-form-honors-k-large() {
    count(collection(t:collection-path($t:N_LARGE))//article[ft:query-field-vector("embedding", $t:VEC, $t:K)])
};

(: ================================================================
   Small collection (30 docs, one article per document): isolates the
   path-predicate k-is-ignored bug from the 341-document clause-count
   cap, since 30 documents never came close to throwing.
   ================================================================ :)

declare
    %test:assertEquals(5)
function t:path-form-honors-k-small() {
    count(collection(t:collection-path($t:N_SMALL))//article[ft:query-vector(., $t:VEC, $t:K)])
};

declare
    %test:assertEquals(5)
function t:direct-call-honors-k-small() {
    count(ft:query-vector(collection(t:collection-path($t:N_SMALL))//article, $t:VEC, $t:K))
};

declare
    %test:assertEquals(5)
function t:field-path-form-honors-k-small() {
    count(collection(t:collection-path($t:N_SMALL))//article[ft:query-field-vector("embedding", $t:VEC, $t:K)])
};
