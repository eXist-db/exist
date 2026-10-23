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
 : An ngram query's matches are carried by the nodes of its result, never by the nodes it was given: after
 : $c[ngram:contains(., ...)], the nodes of $c carry no matches of that query, and a second query over $c is not mixed
 : with the first.
 :)
module namespace nqli = "http://exist-db.org/xquery/test/ngram/query-leaves-its-inputs";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

import module namespace ngram = "http://exist-db.org/xquery/ngram";

declare variable $nqli:COLLECTION := "/db/test-ngram-query-leaves-its-inputs";
declare variable $nqli:CONFIG := "/db/system/config" || $nqli:COLLECTION;

declare
    %test:setUp
function nqli:setup() {
    let $_ := xmldb:create-collection("/db", substring-after($nqli:COLLECTION, "/db/"))
    let $_ := xmldb:create-collection("/db/system/config/db", substring-after($nqli:COLLECTION, "/db/"))
    let $_ := xmldb:store($nqli:CONFIG, "collection.xconf",
        <collection xmlns="http://exist-db.org/collection-config/1.0">
            <index><ngram qname="p"/></index>
        </collection>)
    return xmldb:store($nqli:COLLECTION, "doc.xml", <body><p>alpha beta</p><p>gamma</p></body>)
};

declare
    %test:tearDown
function nqli:teardown() {
    xmldb:remove($nqli:COLLECTION),
    xmldb:remove($nqli:CONFIG)
};

declare %private function nqli:matches($nodes as node()*) as xs:string {
    string-join(util:expand($nodes)//exist:match ! string(.), "|")
};

(: the hits are highlighted, the context they came from is not :)
declare
    %test:assertEquals("alp", "")
function nqli:contains-leaves-the-context() {
    let $c := collection($nqli:COLLECTION)//p
    let $hits := $c[ngram:contains(., "alp")]
    return (nqli:matches($hits), nqli:matches($c))
};

(: a second query over the same context highlights its own term only :)
declare
    %test:assertEquals("bet")
function nqli:a-second-query-is-not-mixed-with-the-first() {
    let $c := collection($nqli:COLLECTION)//p
    let $first := $c[ngram:contains(., "alp")]
    let $second := $c[ngram:contains(., "bet")]
    return nqli:matches($second)
};

(: a wildcard-only pattern scans the indexed nodes: the context it found them in is not marked :)
declare
    %test:assertEquals(2, "")
function nqli:wildcard-only-leaves-the-context() {
    let $c := collection($nqli:COLLECTION)//p
    let $hits := $c[ngram:wildcard-contains(., "^.+$")]
    return (count($hits), nqli:matches($c))
};

(: a pattern of parts put together by a wildcard: the context is not marked :)
declare
    %test:assertEquals(1, "")
function nqli:wildcard-parts-leave-the-context() {
    let $c := collection($nqli:COLLECTION)//p
    let $hits := $c[ngram:wildcard-contains(., "alp.a")]
    return (count($hits), nqli:matches($c))
};

(: ngram:add-match returns the node with the match; the node it was given stays as it was :)
declare
    %test:assertEquals(1, 0)
function nqli:add-match-leaves-its-argument() {
    let $n := collection($nqli:COLLECTION)//p[1]
    let $marked := ngram:add-match($n)
    return (count(util:expand($marked)//exist:match), count(util:expand($n)//exist:match))
};
