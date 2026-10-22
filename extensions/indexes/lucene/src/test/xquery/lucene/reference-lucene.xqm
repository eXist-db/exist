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
 : Reference checks: ft:query must find what its query means over the same values without an index, and highlight
 : what it found. The values and analyzers are chosen so that the analysis is known exactly: plain ASCII words with
 : the standard analyzer (lower-cased, split on spaces) in one collection, the keyword analyzer (the whole value, as
 : it is) in another, queried together so that each collection is searched with its own configuration. Each indexed
 : query is compiled with literal arguments, so that the optimizer rewrites it when it can.
 :)
module namespace rl = "http://exist-db.org/xquery/test/lucene/reference-lucene";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

declare variable $rl:COLLECTION := "/db/reference-lucene";

declare variable $rl:WORDS := ("alpha", "beta", "gamma", "delta", "alphabet", "betamax", "Alpha", "GAMMA", "al", "epsilon");

(: the words of value $i: one to four, picked by a fixed rule :)
declare %private function rl:words($i as xs:integer) as xs:string* {
    for $j in 0 to $i mod 4
    return $rl:WORDS[(($i * 7 + $j * 3) mod count($rl:WORDS)) + 1]
};

declare variable $rl:STD := (1 to 40) ! <p n="{ . }">{ string-join(rl:words(.), " ") }</p>;
declare variable $rl:KW := (1 to 40) ! <p n="{ 100 + . }">{ string-join(rl:words(.)[position() le 2], " ") }</p>;

declare
    %test:setUp
function rl:setup() {
    let $_ := xmldb:create-collection("/db", "reference-lucene")
    let $_ := (xmldb:create-collection($rl:COLLECTION, "std"), xmldb:create-collection($rl:COLLECTION, "kw"))
    let $_ := xmldb:create-collection("/db/system/config/db", "reference-lucene")
    let $_ := (xmldb:create-collection("/db/system/config" || $rl:COLLECTION, "std"),
        xmldb:create-collection("/db/system/config" || $rl:COLLECTION, "kw"))
    let $_ := xmldb:store("/db/system/config" || $rl:COLLECTION || "/std", "collection.xconf",
        <collection xmlns="http://exist-db.org/collection-config/1.0">
            <index><lucene><text qname="p"/></lucene></index>
        </collection>)
    let $_ := xmldb:store("/db/system/config" || $rl:COLLECTION || "/kw", "collection.xconf",
        <collection xmlns="http://exist-db.org/collection-config/1.0">
            <index><lucene>
                <analyzer id="kw" class="org.apache.lucene.analysis.core.KeywordAnalyzer"/>
                <text qname="p" analyzer="kw"/>
            </lucene></index>
        </collection>)
    return (
        xmldb:store($rl:COLLECTION || "/std", "values.xml", <values>{ $rl:STD }</values>),
        xmldb:store($rl:COLLECTION || "/kw", "values.xml", <values>{ $rl:KW }</values>)
    )
};

declare
    %test:tearDown
function rl:teardown() {
    xmldb:remove($rl:COLLECTION),
    xmldb:remove("/db/system/config" || $rl:COLLECTION)
};

(: the queries: kind, first word, second word :)
declare function rl:queries() as array(*)* {
    for $w in $rl:WORDS
    return (["term", $w, ()], ["prefix", substring($w, 1, 3), ()],
        if (string-length($w) ge 3) then ["single", $w, ()] else ()),
    for $w1 in $rl:WORDS[position() le 6], $w2 in $rl:WORDS[position() le 6][. ne $w1]
    return (["phrase", $w1, $w2], ["and", $w1, $w2], ["or", $w1, $w2], ["not", $w1, $w2])
};

declare %private function rl:syntax($q as array(*)) as xs:string {
    switch ($q(1))
        case "term" return $q(2)
        case "prefix" return $q(2) || "*"
        case "single" return substring($q(2), 1, 1) || "?" || substring($q(2), 3)
        case "phrase" return '"' || $q(2) || " " || $q(3) || '"'
        case "and" return $q(2) || " AND " || $q(3)
        case "or" return $q(2) || " OR " || $q(3)
        default return $q(2) || " NOT " || $q(3)
};

(: whether a value's tokens match a term of the query: $exact for the keyword analyzer :)
declare %private function rl:has($tokens as xs:string*, $kind as xs:string, $word as xs:string, $exact as xs:boolean) as xs:boolean {
    let $word := if ($exact) then $word else lower-case($word)
    return switch ($kind)
        case "prefix" return some $t in $tokens satisfies starts-with($t, $word)
        case "single" return some $t in $tokens satisfies
            matches($t, "^" || substring($word, 1, 1) || "." || replace(substring($word, 3), "(\W)", "\\$1") || "$")
        default return $tokens = $word
};

(: the values the query means, by @n :)
declare %private function rl:reference($q as array(*)) as xs:string {
    rl:show((
        for $p in $rl:STD
        let $tokens := tokenize(lower-case($p), " ")
        where rl:means($q, $tokens, false())
        return $p/@n,
        for $p in $rl:KW
        where rl:means($q, string($p), true())
        return $p/@n
    ))
};

declare %private function rl:means($q as array(*), $tokens as xs:string*, $exact as xs:boolean) as xs:boolean {
    switch ($q(1))
        case "phrase" return
            if ($exact) then $tokens = $q(2) || " " || $q(3)
            else some $i in 1 to count($tokens) - 1 satisfies
                ($tokens[$i] eq lower-case($q(2)) and $tokens[$i + 1] eq lower-case($q(3)))
        case "and" return rl:has($tokens, "term", $q(2), $exact) and rl:has($tokens, "term", $q(3), $exact)
        case "or" return rl:has($tokens, "term", $q(2), $exact) or rl:has($tokens, "term", $q(3), $exact)
        case "not" return rl:has($tokens, "term", $q(2), $exact) and not(rl:has($tokens, "term", $q(3), $exact))
        default return rl:has($tokens, $q(1), $q(2), $exact)
};

declare %private function rl:literal($s as xs:string) as xs:string {
    "'" || replace($s, "'", "''") || "'"
};

declare %private function rl:show($values as xs:anyAtomicType*) as xs:string {
    "[" || string-join(sort($values ! xs:integer(.)) ! string(.), ", ") || "]"
};

declare %private function rl:hits($q as array(*)) as element(p)* {
    util:eval("collection(" || rl:literal($rl:COLLECTION) || ")//p[ft:query(., " || rl:literal(rl:syntax($q)) || ")]")
};

declare %private function rl:indexed($q as array(*)) as xs:string {
    try { rl:show(rl:hits($q)/@n) } catch * { "error " || local-name-from-QName($err:code) }
};

(: the hits of every generated query: the differences, as "query | index | reference" :)
declare
    %test:assertEmpty
function rl:query-finds-what-it-means() {
    for $q in rl:queries()
    let $indexed := rl:indexed($q)
    let $reference := rl:reference($q)
    where $indexed ne $reference
    return string-join((rl:syntax($q), $indexed, $reference), " | ")
};

(: the highlighted spans of term and phrase queries in the standard collection: each occurrence, as written :)
declare
    %test:assertEmpty
function rl:highlight-marks-what-it-found() {
    for $q in rl:queries()[?1 = ("term", "phrase")]
    let $hits := rl:hits($q)[xs:integer(@n) le 40]
    for $hit in $hits
    let $marked := string-join(util:expand($hit)//exist:match ! string(.), "|")
    let $words := tokenize(string($hit), " ")
    let $expected := string-join(
        if ($q(1) eq "term") then $words[lower-case(.) eq lower-case($q(2))]
        else for $i in 1 to count($words) - 1
            where lower-case($words[$i]) eq lower-case($q(2)) and lower-case($words[$i + 1]) eq lower-case($q(3))
            return $words[$i] || " " || $words[$i + 1],
        "|")
    where $marked ne $expected
    return string-join((rl:syntax($q), string($hit/@n), $marked, $expected), " | ")
};

(: not vacuous :)
declare
    %test:assertTrue
function rl:the-reference-is-not-trivial() {
    let $answers := rl:queries() ! rl:reference(.)
    return count(distinct-values($answers)) gt 20 and count($answers[. ne "[]"]) gt 60
};
