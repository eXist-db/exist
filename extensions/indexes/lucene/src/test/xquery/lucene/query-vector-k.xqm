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

declare variable $t:NONSELF_COLL := "/db/test-query-vector-k-nonself";
declare variable $t:NONSELF_CONF_COLL := "/db/system/config/db/test-query-vector-k-nonself";

declare function t:setup-nonself-collection() {
    let $_ := xmldb:create-collection("/db/system", "config")
    let $_ := xmldb:create-collection("/db/system/config", "db")
    let $_ := xmldb:create-collection("/db", "test-query-vector-k-nonself")
    let $_ := xmldb:create-collection("/db/system/config/db", "test-query-vector-k-nonself")
    let $_ := xmldb:store($t:NONSELF_CONF_COLL, "collection.xconf", $t:xconf)
    let $_ := xmldb:store($t:NONSELF_COLL, "a.xml",
        <article><title>A</title><embedding>1.0 0.0 0.0 0.0</embedding></article>)
    let $_ := xmldb:store($t:NONSELF_COLL, "b.xml",
        <article><title>B</title><embedding>0.0 1.0 0.0 0.0</embedding></article>)
    let $_ := xmldb:store($t:NONSELF_COLL, "c.xml",
        <article><title>C</title><embedding>0.0 0.0 1.0 0.0</embedding></article>)
    return xmldb:reindex($t:NONSELF_COLL)
};

declare function t:teardown-nonself-collection() {
    if (xmldb:collection-available($t:NONSELF_COLL)) then xmldb:remove($t:NONSELF_COLL) else (),
    if (xmldb:collection-available($t:NONSELF_CONF_COLL)) then xmldb:remove($t:NONSELF_CONF_COLL) else ()
};

(: All three collections are created once per test run: the large one (342
 : docs) exercises the 341-document BooleanQuery clause-count cap, the small
 : one (30 docs) isolates the path-predicate k-is-ignored bug from that cap,
 : and the "nonself" one has distinct per-article embeddings for the
 : non-self "nodes" argument regression test below. A single setUp/tearDown
 : pair (rather than one per collection) avoids relying on XQSuite running
 : more than one %test:setUp function per module. :)
declare
    %test:setUp
function t:setup() {
    (
        t:setup-collection-of-size($t:N_LARGE),
        t:setup-collection-of-size($t:N_SMALL),
        t:setup-nonself-collection()
    )
};

declare
    %test:tearDown
function t:teardown() {
    (
        t:teardown-collection-of-size($t:N_LARGE),
        t:teardown-collection-of-size($t:N_SMALL),
        t:teardown-nonself-collection()
    )
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

(: ================================================================
   Regression: a "nodes" argument to ft:query-vector that is NOT `.`
   must not have the predicate's own candidate set silently
   substituted for it. Distinct articles (distinct embeddings) so a
   wrong substitution is observably different from the correct result.
   ================================================================ :)

(:~
 : $probe is a fixed 1-node set (article A), unrelated to whichever article
 : the enclosing predicate is currently testing. Since $probe never changes
 : per candidate, ft:query-vector($probe, ..., 1) always finds the same
 : (non-empty) result, so the predicate's truth value is the same for every
 : candidate -- all 3 articles pass, not just A (which a buggy
 : canOptimizeSequence() that always claims optimizability would produce, by
 : silently substituting the predicate's own 3-candidate set for $probe and
 : returning only the one actually nearest to the query vector).
 :
 : Wrapped in boolean(): a bare node-returning predicate (executionMode NODE)
 : requires eXist's Predicate.selectByNodeSet to trace each result node's
 : match context back to the SAME predicate's own candidate set -- a
 : pre-existing eXist requirement, unrelated to this fix, that a search over
 : an unrelated domain like $probe can't satisfy (see t:diag-bare-variable-
 : predicate, which hits the identical "context is missing" constraint with
 : no ft:query-vector involved at all). boolean() forces BOOLEAN-mode
 : evaluation (plain effective-boolean-value, no context-chain requirement),
 : which is the correct idiom whenever "nodes" isn't `.`.
 :)
declare
    %test:assertEquals(3)
function t:path-form-honors-explicit-nonself-nodes() {
    let $probe := collection($t:NONSELF_COLL)//article[title = "A"]
    return count(collection($t:NONSELF_COLL)//article[boolean(ft:query-vector($probe, [1.0, 0.0, 0.0, 0.0], 1))])
};

(:~ The direct-call form (arg0 fully evaluated, no predicate/context-chain
 : involved at all) already worked correctly before this test existed; kept
 : as a control alongside the predicate-form test above. :)
declare
    %test:assertEquals(1)
function t:direct-call-honors-explicit-nonself-nodes() {
    let $probe := collection($t:NONSELF_COLL)//article[title = "A"]
    return count(ft:query-vector($probe, [1.0, 0.0, 0.0, 0.0], 1))
};

(:~ Control, with ft:query-vector entirely out of the picture: proves the
 : boolean()-wrapping requirement above is a general eXist predicate-engine
 : characteristic (NODE-mode Predicate.selectByNodeSet's context-chain
 : requirement vs. BOOLEAN-mode's plain effective-boolean-value), not
 : something specific to this fix. Without boolean(), this throws "Internal
 : evaluation error: context is missing for node" -- $probe's nodes carry no
 : context chain linking them to this predicate's own candidate set. :)
declare
    %test:assertEquals(3)
function t:bare-variable-predicate-needs-boolean-wrapping() {
    let $probe := collection($t:NONSELF_COLL)//article[title = "A"]
    return count(collection($t:NONSELF_COLL)//article[boolean($probe)])
};
