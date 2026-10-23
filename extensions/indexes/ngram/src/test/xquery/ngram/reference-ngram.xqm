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
 : Reference checks: every ngram function must give the answer of its fn: counterpart over the same values without an
 : index. The ngram functions compare case-insensitively, so the reference compares lower-cased values; the wildcard
 : syntax of ngram:wildcard-contains is a subset of XPath's regular expressions, in which '.' is any character
 : (the s flag). The values cover case mappings, characters outside the BMP, CJK and strings shorter than an ngram.
 : Each indexed query is compiled with literal arguments, so that it uses the ngram index.
 :)
module namespace rn = "http://exist-db.org/xquery/test/ngram/reference-ngram";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

declare variable $rn:COLLECTION := "/db/reference-ngram";
declare variable $rn:CONFIG := "/db/system/config" || $rn:COLLECTION;

declare variable $rn:VALUES := (
    "ham", "Ham", "HAM", "hamburg", "ab", "a", "", "abc", "aaa", "abab", "x y z", "straße", "STRASSE",
    codepoints-to-string(304) || "stanbul", codepoints-to-string((305, 105)), "K", "k",
    codepoints-to-string(128512), "a" || codepoints-to-string(128512) || "b", "ab" || codepoints-to-string(128512),
    codepoints-to-string(128512) || "ab", codepoints-to-string(119964) || "bc", "日本語", "日本", "naïve", "NAÏVE",
    "tab" || codepoints-to-string(9) || "here", "a" || codepoints-to-string(10) || "b"
);

declare variable $rn:STRINGS := (
    "a", "ab", "abc", "ham", "HAM", "am", "b", codepoints-to-string(128512), "a" || codepoints-to-string(128512),
    codepoints-to-string(128512) || "b", "日本", "本語", "ï", "aï", "i", "ss", "ß", "x y",
    " ", "burg", "ab" || codepoints-to-string(128512)
);

declare variable $rn:LITERALS := ("a", "ab", "ham", codepoints-to-string(128512), "日本", "x y", "b");
declare variable $rn:WILDCARDS := (".", ".?", ".*", ".+", ".{1,2}", "[ab]");

declare function rn:patterns() as xs:string* {
    distinct-values((
        for $a in ($rn:LITERALS, $rn:WILDCARDS)
        return ($a, "^" || $a, $a || "$", "^" || $a || "$"),
        for $a in $rn:LITERALS, $w in $rn:WILDCARDS
        return ($a || $w, $w || $a, "^" || $a || $w || "$", "^" || $w || $a || "$"),
        for $a in $rn:LITERALS, $b in $rn:LITERALS
        return ($a || "." || $b, "^" || $a || ".*" || $b || "$")
    ))
};

declare
    %test:setUp
function rn:setup() {
    let $_ := xmldb:create-collection("/db", substring-after($rn:COLLECTION, "/db/"))
    let $_ := xmldb:create-collection("/db/system/config/db", substring-after($rn:COLLECTION, "/db/"))
    let $_ := xmldb:store($rn:CONFIG, "collection.xconf",
        <collection xmlns="http://exist-db.org/collection-config/1.0">
            <index><ngram qname="v"/></index>
        </collection>)
    return xmldb:store($rn:COLLECTION, "values.xml", <values>{ $rn:VALUES ! <v>{ . }</v> }</values>)
};

declare
    %test:tearDown
function rn:teardown() {
    xmldb:remove($rn:COLLECTION),
    xmldb:remove($rn:CONFIG)
};

declare %private function rn:literal($s as xs:string) as xs:string {
    "'" || replace(replace($s, "&amp;", "&amp;amp;"), "'", "''") || "'"
};

(: the values, in a stable order, with control characters visible :)
declare %private function rn:show($values as xs:string*) as xs:string {
    "[" || string-join(sort($values) ! replace(replace(., codepoints-to-string(10), "\\n"), codepoints-to-string(9), "\\t"),
        ", ") || "]"
};

declare %private function rn:indexed($function as xs:string, $arg as xs:string) as xs:string {
    try {
        (: string() outside the evaluated query: inside, a /string() step returns a supplementary character as the
           text of a character reference :)
        rn:show(util:eval("collection(" || rn:literal($rn:COLLECTION) || ")//v[ngram:" || $function || "(., "
            || rn:literal($arg) || ")]") ! string(.))
    } catch * {
        "error " || local-name-from-QName($err:code)
    }
};

declare %private function rn:reference($function as xs:string, $arg as xs:string) as xs:string {
    try {
        let $arg := lower-case($arg)
        return rn:show(
            for $v in $rn:VALUES
            let $value := lower-case($v)
            where
                if ($function eq "contains") then contains($value, $arg)
                else if ($function eq "starts-with") then starts-with($value, $arg)
                else if ($function eq "ends-with") then ends-with($value, $arg)
                else matches($value, $arg, "s")
            return $v
        )
    } catch * {
        "error " || local-name-from-QName($err:code)
    }
};

(: the differences, as "function | argument | index | reference" :)
declare %private function rn:differences($function as xs:string, $args as xs:string*) as xs:string* {
    for $arg in $args
    let $indexed := rn:indexed($function, $arg)
    let $reference := rn:reference($function, $arg)
    where $indexed ne $reference
    return string-join(($function, $arg, $indexed, $reference), " | ")
};

declare
    %test:assertEmpty
function rn:contains-like-fn-contains() {
    rn:differences("contains", $rn:STRINGS)
};

declare
    %test:assertEmpty
function rn:starts-with-like-fn-starts-with() {
    rn:differences("starts-with", $rn:STRINGS)
};

declare
    %test:assertEmpty
function rn:ends-with-like-fn-ends-with() {
    rn:differences("ends-with", $rn:STRINGS)
};

declare
    %test:assertEmpty
function rn:wildcard-contains-like-fn-matches() {
    rn:differences("wildcard-contains", rn:patterns())
};

(: the ngram index lower-cases one code point at a time, so İ (U+0130) becomes i, where fn:lower-case gives i and a
   combining dot above :)
declare
    %test:assertEmpty
function rn:case-mapping-like-fn-lower-case() {
    for $function in ("contains", "starts-with", "ends-with")
    return rn:differences($function, codepoints-to-string(304))
};

(: not vacuous: the patterns match different numbers of values, so the comparison tells them apart :)
declare
    %test:assertTrue
function rn:the-reference-is-not-trivial() {
    let $answers := rn:patterns() ! rn:reference("wildcard-contains", .)
    return count(distinct-values($answers)) gt 8 and count($answers[. ne "[]"]) gt 50
};

