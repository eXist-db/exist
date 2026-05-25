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
 : Higher-order function tests, originally authored by Wolfgang Meier.
 : Converted from the legacy <TestSet> XML format to an XQSuite module
 : per duncdrum's review on PR #6352 so the entire suite uses a single
 : annotation-based assertion format. Each scenario is wrapped in
 : util:eval to preserve the per-test compilation isolation of the
 : original format (each test had its own xquery version prologue and
 : local:/ex: module-level function declarations).
 :)
module namespace ho = "http://exist-db.org/xquery/test/higher-order";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

declare
    %test:setUp
function ho:setup() {
    let $col := xmldb:create-collection("/db", "xq3-test")
    return
        xmldb:store($col, "test1.xql",
            'module namespace ex2="http://exist-db.org/xquery/ex2";

(: copied from the XQuery spec :)
declare function ex2:fold-left(
        $f as function(item()*, item()) as item()*,
        $zero as item()*,
        $seq as item()*) as item()* {
  if (fn:empty($seq)) then $zero
  else ex2:fold-left($f, $f($zero, $seq[1]), subsequence($seq, 2))
};',
            "application/xquery")
};

declare
    %test:tearDown
function ho:cleanup() {
    xmldb:remove("/db/xq3-test")
};

declare
    %test:assertEquals("70")
function ho:named-function-reference() {
    util:eval('xquery version "3.0";

declare namespace ex="http://exist-db.org/xquery/ex";

declare function ex:add($a, $b) {
    $a + $b
};

let $f1 := ex:add#2
return
       $f1(20, 50)')
};

declare
    %test:assertEquals("70")
function ho:named-function-reference-local-namespace() {
    util:eval('xquery version "3.0";

declare function local:add($a, $b) {
    $a + $b
};

let $f1 := local:add#2
return
       $f1(20, 50)')
};

declare
    %test:assertEquals("3")
function ho:named-function-reference-internal-standard-function() {
    util:eval('xquery version "1.0";

let $f := sum#1
return
    $f((1, 2))')
};

declare
    %test:assertEquals("70")
function ho:backwards-compatibility-1() {
    util:eval('xquery version "3.0";

declare function local:add($a, $b) {
    $a + $b
};

let $f1 := util:function(xs:QName("local:add"), 2)
return
       $f1(20, 50)')
};

declare
    %test:assertEquals("70")
function ho:backwards-compatibility-2() {
    util:eval('xquery version "3.0";

declare function local:add($a, $b) {
    $a + $b
};

let $f1 := local:add#2
return
       util:call($f1, 20, 50)')
};

declare
    %test:assertEquals("70")
function ho:annotation-support() {
    util:eval('xquery version "3.0";
declare namespace ex="http://exist-db.org/xquery/ex";

declare %ex:annotest function ex:add($a, $b) {
    $a + $b
};

let $f1 := ex:add#2
return
       $f1(20, 50)')
};

declare
    %test:assertEquals("HELLO", "WORLD!")
function ho:inline-function() {
    util:eval('xquery version "3.0";

declare namespace ex="http://exist-db.org/xquery/ex";

declare function ex:apply($func, $list) {
    for $item in $list return $func($item)
};

let $f2 := function($a) { upper-case($a) }
return
       ex:apply($f2, ("Hello", "world!"))')
};

declare
    %test:assertEquals("HELLO", "WORLD!")
function ho:inline-function-with-annotation() {
    util:eval('xquery version "3.0";

declare namespace ex="http://exist-db.org/xquery/ex";

declare function ex:apply($func, $list) {
    for $item in $list return $func($item)
};

let $f2 := %ex:annoex function($a) { upper-case($a) }
return
       ex:apply($f2, ("Hello", "world!"))')
};

declare
    %test:assertEquals("HELLO", "WORLD!")
function ho:inline-function-as-parameter() {
    util:eval('xquery version "3.0";

declare namespace ex="http://exist-db.org/xquery/ex";

declare function ex:apply($func, $list) {
    for $item in $list return $func($item)
};

ex:apply(function($a) { upper-case($a) }, ("Hello", "world!"))')
};

declare
    %test:assertEquals("Hello")
function ho:immediate-execution-of-inline-function() {
    util:eval('xquery version "3.0";

function($x) { $x }("Hello")')
};

declare
    %test:assertEquals("HELLO", "WORLD!")
function ho:combine-named-reference-dynamic-calls-inline() {
    util:eval('xquery version "3.0";

declare namespace ex="http://exist-db.org/xquery/ex";

declare function ex:apply($func, $list) {
    for $item in $list return $func($item)
};

let $f1 := ex:apply#2
return
       $f1(function($a) { upper-case($a) }, ("Hello", "world!"))')
};

declare
    %test:assertEquals("HELLO", "WORLD!")
function ho:function-sequence-type-any-function() {
    util:eval('xquery version "3.0";

declare namespace ex="http://exist-db.org/xquery/ex";

declare function ex:apply($func as function(*), $list) {
    for $item in $list return $func($item)
};

ex:apply(function($a) { upper-case($a) }, ("Hello", "world!"))')
};

declare
    %test:assertEquals("HELLO", "WORLD!")
function ho:function-sequence-type-one-parameter() {
    util:eval('xquery version "3.0";

declare namespace ex="http://exist-db.org/xquery/ex";

declare function ex:apply($func as function(xs:string*) as xs:string*, $list) {
    for $item in $list return $func($item)
};

ex:apply(function($a) { upper-case($a) }, ("Hello", "world!"))')
};

declare
    %test:assertEquals("15")
function ho:function-sequence-type-two-parameters-1() {
    util:eval('
(: copied from the XQuery spec :)
declare function local:fold-left(
        $f as function(item()*, item()) as item()*,
        $zero as item()*,
        $seq as item()*) as item()* {
  if (fn:empty($seq)) then $zero
  else local:fold-left($f, $f($zero, $seq[1]), subsequence($seq, 2))
};

local:fold-left(function($a, $b) { $a + $b }, 0, 1 to 5)')
};

declare
    %test:assertEquals("210")
function ho:function-sequence-type-two-parameters-2() {
    util:eval('
declare function local:fold-left(
        $f as function(item()*, item()) as item()*,
        $zero as item()*,
        $seq as item()*) as item()* {
  if (fn:empty($seq)) then $zero
  else local:fold-left($f, $f($zero, $seq[1]), subsequence($seq, 2))
};

local:fold-left(function($a, $b) { $a * $b }, 1, (2,3,5,7))')
};

declare
    %test:assertEquals("1", "4", "9", "16", "25")
function ho:function-sequence-type-two-parameters-3() {
    util:eval('declare function local:map($f, $seq) {
  if (fn:empty($seq))
  then ()
  else ($f($seq[1]), local:map($f, subsequence($seq, 2)))
};

local:map(function($a) { $a * $a }, 1 to 5)')
};

declare
    %test:assertEquals("OK")
function ho:function-sequence-type-parenthesized-type() {
    util:eval('
declare function local:test(
    $f as (function(item()*) as item()*)?) {
  "OK"
};

local:test(())')
};

declare
    %test:assertEquals("210")
function ho:calling-function-in-imported-module() {
    util:eval('
import module namespace ex2="http://exist-db.org/xquery/ex2"
at "xmldb:exist:///db/xq3-test/test1.xql";

let $f1 := ex2:fold-left#3
return
    $f1(function($a, $b) { $a * $b }, 1, (2,3,5,7))')
};

declare
    %test:assertEquals("2")
function ho:dynfcall1() {
    util:eval('xquery version "3.0";

declare function local:seq() {
    (1, 2, 3)
};

let $seq := local:seq#0
return
    $seq()[2]')
};

declare
    %test:assertEquals("3")
function ho:dynfcall2() {
    util:eval('xquery version "3.0";

declare function local:seq() {
    (1, 2, 3)
};

let $seq := local:seq#0
return
    $seq[1]()[3]')
};

declare
    %test:assertEquals("1")
function ho:dynfcall3() {
    util:eval('xquery version "3.0";

declare function local:seq() {
    (1, 2, 3)
};

let $seq := local:seq#0
return
    ($seq)()[1]')
};

declare
    %test:assertEquals("2", "3")
function ho:dynfcall4() {
    util:eval('xquery version "3.0";

declare function local:seq() {
    (1, 2, 3)
};

let $seq := local:seq#0
return
    $seq()[. > 1]')
};

declare
    %test:assertEquals("Hello you")
function ho:dynfcall5() {
    util:eval('xquery version "3.0";
(: Immediate function evaluation :)
(function($a) { "Hello " || $a })("you")')
};

declare
    %test:assertEquals("HELLO")
function ho:dynfcall6() {
    util:eval('xquery version "3.0";
declare function local:get-function() as function(xs:string) as xs:string {
	function($str as xs:string) { upper-case($str) }
};

local:get-function()("Hello")')
};

declare
    %test:assertEquals("70")
function ho:fn-function-lookup() {
    util:eval('xquery version "3.0";

declare namespace ex="http://exist-db.org/xquery/ex";

declare function ex:add($a, $b) {
    $a + $b
};

let $f1 := function-lookup(xs:QName("ex:add"), 2)
return
       $f1(20, 50)')
};

declare
    %test:assertEmpty
function ho:lookup-unknown() {
    util:eval('xquery version "3.0";

declare namespace ex="http://exist-db.org/xquery/ex";

let $f1 := function-lookup(xs:QName("ex:add"), 2)
return
    if (exists($f1)) then
       $f1(20, 50)
    else
        ()')
};

declare
    %test:assertEquals("210")
function ho:fn-function-lookup-on-imported-module() {
    util:eval('xquery version "3.0";

import module namespace ex2="http://exist-db.org/xquery/ex2"
at "xmldb:exist:///db/xq3-test/test1.xql";

let $f1 := function-lookup(xs:QName("ex2:fold-left"), 3)
return
    $f1(function($a, $b) { $a * $b }, 1, (2,3,5,7))')
};

declare
    %test:assertEquals("2")
function ho:fn-function-arity() {
    util:eval('xquery version "3.0";

declare namespace ex="http://exist-db.org/xquery/ex";

declare function ex:add($a, $b) {
    $a + $b
};

let $f1 := ex:add#2
return
       function-arity($f1)')
};

declare
    %test:assertEquals("ex:add")
function ho:fn-function-name() {
    util:eval('xquery version "3.0";

declare namespace ex="http://exist-db.org/xquery/ex";

declare function ex:add($a, $b) {
    $a + $b
};

let $f1 := ex:add#2
return
       xs:string(function-name($f1))')
};

declare
    %test:assertEmpty
function ho:fn-function-name-anonymous() {
    util:eval('xquery version "3.0";

declare namespace ex="http://exist-db.org/xquery/ex";

let $f1 := function($a, $b) {
    $a + $b
}
return
       function-name($f1)')
};

declare
    %test:assertEquals("1", "4", "9", "16", "25")
function ho:fn-for-each-function() {
    util:eval("fn:for-each(1 to 5, function($a) { $a * $a })")
};

declare
    %test:assertEquals("106", "111", "104", "110", "106", "97", "110", "101")
function ho:fn-for-each-on-strings() {
    util:eval('fn:for-each(("john", "jane"), fn:string-to-codepoints#1)')
};

declare
    %test:assertEquals("23", "29")
function ho:fn-for-each-with-cast() {
    util:eval('fn:for-each(("23", "29"), xs:int#1)')
};

declare
    %test:assertError("err:XPTY0004")
function ho:fn-filter-wrong-argument-order-deprecated() {
    util:eval("fn:filter(function($a) {$a mod 2 = 0}, (1 to 10))")
};

declare
    %test:assertEquals("2", "4", "6", "8", "10")
function ho:fn-filter-function() {
    util:eval("fn:filter(1 to 10, function($a) {$a mod 2 = 0})")
};

declare
    %test:assertEquals("5", "4", "3", "2", "1")
function ho:fn-fold-left-1() {
    util:eval("fn:fold-left(1 to 5, (), function($a, $b) {($b, $a)})")
};

declare
    %test:assertEquals("210")
function ho:fn-fold-left-2() {
    util:eval("fold-left((2,3,5,7), 1, function($a, $b) { $a * $b })")
};

declare
    %test:assertEquals("$f($f($f($f($f($zero, 1), 2), 3), 4), 5)")
function ho:fn-fold-left-3() {
    util:eval('fn:fold-left(1 to 5, "$zero", fn:concat("$f(", ?, ", ", ?, ")"))')
};

declare
    %test:assertEquals(".1.2.3.4.5")
function ho:fn-fold-left-with-partial-application() {
    util:eval('fn:fold-left(1 to 5, "", fn:concat(?, ".", ?))')
};

declare
    %test:assertEquals("0.000001")
function ho:fn-fold-left-non-associative() {
    util:eval("fold-left(1 to 1000000, 10, function($a, $b){ ($a - ($a - 1)) div $b })")
};

declare
    %test:assertEquals("15")
function ho:fn-fold-right-1() {
    util:eval("fn:fold-right(1 to 5, 0, function($a, $b) { $a + $b })")
};

declare
    %test:assertEquals("1.2.3.4.5.")
function ho:fn-fold-right-2() {
    util:eval('fn:fold-right(1 to 5, "", function($a, $b) { concat($a, ".", $b) })')
};

declare
    %test:assertEquals("$f(1, $f(2, $f(3, $f(4, $f(5, $zero)))))")
function ho:fn-fold-right-3() {
    util:eval('fn:fold-right(1 to 5, "$zero", concat("$f(", ?, ", ", ?, ")"))')
};

declare
    %test:assertEquals("10")
function ho:fn-fold-right-non-associative() {
    util:eval("fold-right(1 to 1000000, 10, function($a, $b){ ($a - ($a - 1)) div $b })")
};

declare
    %test:assertEquals("11", "22", "33", "44", "55")
function ho:fn-for-each-pair-function() {
    util:eval("fn:for-each-pair(1 to 5, 1 to 5, function($a, $b){10*$a + $b})")
};

declare
    %test:assertEquals("ax", "by", "cz")
function ho:fn-for-each-pair-with-strings() {
    util:eval('fn:for-each-pair(("a", "b", "c"), ("x", "y", "z"), concat#2)')
};

declare
    %test:assertEquals("10", "20", "30", "40", "50", "60", "70", "80", "90", "100")
function ho:partial1() {
    util:eval('xquery version "3.0";

declare namespace ex="http://exist-db.org/xquery/ex";

declare function ex:multiply($base, $number) {
    $base * $number
};

(: Use function reference literal to find function at compile time :)
let $fMultiply := ex:multiply(10, ?)
return
    for-each(1 to 10, $fMultiply)')
};

declare
    %test:assertEquals("10", "20", "30", "40", "50", "60", "70", "80", "90", "100")
function ho:partial2() {
    util:eval('xquery version "3.0";

declare namespace ex="http://exist-db.org/xquery/ex";

declare function ex:multiply($base, $number) {
    $base * $number
};

(: Use function reference literal to find function at compile time :)
let $fMultiply := ex:multiply(10, ?)
for $i in 1 to 10
return
    $fMultiply($i)')
};

declare
    %test:assertEquals("13")
function ho:partial3() {
    util:eval('xquery version "1.0";

declare function local:func($a, $b, $c) {
    $a + $b + $c
};

let $f := local:func#3
let $ff := $f(5, ?, ?)
return
    $ff(5, 3)')
};

declare
    %test:assertEquals("true")
function ho:partial4() {
    util:eval('xquery version "3.0";

declare variable $a := function($x as item()*, $f as function(item()*) as
xs:boolean) as xs:boolean {
   $f($x)
};

let $list := (
   <test/>
)

let $f := $a(?, function($x){ true() })

return $f($list)')
};

declare
    %test:assertEquals("18")
function ho:partiald1() {
    util:eval('xquery version "1.0";

declare function local:func($a, $b, $c) {
    $a + $b + $c
};

let $f := local:func(10, ?, ?)
let $ff := $f(5, ?)
return
    $ff(3)')
};

declare
    %test:assertEquals("5", "10")
function ho:partiald2() {
    util:eval('xquery version "1.0";

let $f := function ($a, $b) { $a * $b }
let $ff := $f(5, ?)
return
    for-each((1, 2), $ff)')
};

declare
    %test:assertEquals("a:b")
function ho:internal1() {
    util:eval('xquery version "1.0";

let $f := string-join(?, ":")
return
    $f(("a", "b"))')
};

declare
    %test:assertEquals("Hello world!", "Hello Wolf!")
function ho:internal2() {
    util:eval('xquery version "1.0";

let $f := concat("Hello ", ?)
return
    for-each(("world!", "Wolf!"), $f)')
};

declare
    %test:assertEquals("<p>Hello world!</p>")
function ho:simple-closure() {
    util:eval('xquery version "3.0";

let $a := "Hello"
let $b := "Hello" || " world!"
let $f := function ($p) { <p>{$p}</p> }
return
$f($b)')
};

declare
    %test:assertEquals("<p>Hello world!</p>")
function ho:closure-function-passed-to-other-function() {
    util:eval('xquery version "3.0";

declare function local:test($f as function() as element()) {
    $f()
};

let $a := "Hello"
let $b := "Hello" || " world!"
let $f := function () { <p>{$b}</p> }
return
    local:test($f)')
};

declare
    %test:assertEquals("<p>Hello world!</p>")
function ho:closure-inline-function-created-in-other-function() {
    util:eval('xquery version "3.0";

declare function local:test($f as function() as element()) {
    $f()
};

declare function local:create() {
    let $a := "Hello"
    let $b := "Hello" || " world!"
    return function () { <p>{ $b }</p> }
};

let $f := local:create()
return
    local:test($f)')
};

declare
    %test:assertEquals("<p>Hello world!</p>")
function ho:closure-variable-dependencies() {
    util:eval('xquery version "3.0";

declare function local:test($f as function() as element()) {
    $f()
};

declare function local:create($a) {
    let $b := $a || " world!"
    return function () { <p>{$b}</p> }
};

let $f := local:create("Hello")
return
    local:test($f)')
};

declare
    %test:assertEquals("1", "2", "3", "4", "5", "6", "7", "8", "9", "10")
function ho:closure-variables-properly-copied() {
    util:eval('xquery version "3.0";

declare function local:create() {
    for $i in 1 to 10
    return
        function () { $i }
};

for $f in local:create()
return
    $f()')
};

declare
    %test:assertEquals("root")
function ho:lookup-captures-focus-node-name() {
    util:eval('xquery version "3.0";
let $doc := document { <root/> }
return
    $doc/root/function-lookup(xs:QName(''fn:node-name''), 0)()')
};

declare
    %test:assertEquals("root")
function ho:lookup-captures-focus-xqts-002-shape() {
    util:eval('xquery version "3.0";
let $doc := document { <root/> }
return
    ($doc/root)/function-lookup(xs:QName(''fn:node-name''), 0)()')
};

declare
    %test:assertEquals("hello")
function ho:lookup-captures-focus-string() {
    util:eval('xquery version "3.0";
let $doc := document { <root><child>hello</child></root> }
return
    $doc/root/child/function-lookup(xs:QName(''fn:string''), 0)()')
};

declare
    %test:assertEquals("true")
function ho:lookup-captures-focus-lang() {
    util:eval('xquery version "3.0";
let $doc := document { <root xml:lang="en"/> }
return
    $doc/root/function-lookup(xs:QName(''fn:lang''), 1)(''en'')')
};

declare
    %test:assertEquals("true")
function ho:lookup-captures-focus-has-children() {
    util:eval('xquery version "3.0";
let $doc := document { <root><child/></root> }
return
    $doc/function-lookup(xs:QName(''fn:has-children''), 0)()')
};

declare
    %test:assertEquals("0")
function ho:lookup-captures-focus-id-1-arg() {
    util:eval('xquery version "3.0";
let $doc := document { <root/> }
return
    count($doc/function-lookup(xs:QName(''fn:id''), 1)(''nope''))')
};

declare
    %test:assertEquals("ex:greeting")
function ho:lookup-captures-focus-name() {
    util:eval('xquery version "3.0";
declare namespace ex="http://exist-db.org/xquery/ex";
let $doc := document { <ex:greeting xmlns:ex="http://exist-db.org/xquery/ex"/> }
return
    $doc/*/function-lookup(xs:QName(''fn:name''), 0)()')
};

declare
    %test:assertEquals("child")
function ho:lookup-captures-focus-local-name() {
    util:eval('xquery version "3.0";
let $doc := document { <root><child/></root> }
return
    $doc/root/child/function-lookup(xs:QName(''fn:local-name''), 0)()')
};

declare
    %test:assertEquals("true")
function ho:lookup-captures-focus-base-uri() {
    util:eval('xquery version "3.0";
let $doc := document { <root/> }
let $direct := $doc/root/fn:base-uri()
let $viaLookup := $doc/root/function-lookup(xs:QName(''fn:base-uri''), 0)()
return
    ($direct = $viaLookup) or (empty($direct) and empty($viaLookup))')
};

declare
    %test:assertEquals("/Q{}root[1]/Q{}child[1]")
function ho:lookup-captures-focus-path() {
    util:eval('xquery version "3.0";
let $doc := document { <root><child/></root> }
return
    $doc/root/child/function-lookup(xs:QName(''fn:path''), 0)()')
};

declare
    %test:assertEquals("http://www.w3.org/2005/xpath-functions/collation/codepoint")
function ho:lookup-default-collation-closes-over-static-context() {
    util:eval('xquery version "3.0";
function-lookup(xs:QName(''fn:default-collation''), 0)()')
};

declare
    %test:assertEquals("true")
function ho:lookup-static-base-uri-closes-over-static-context() {
    util:eval('xquery version "3.0";
let $direct := fn:static-base-uri()
let $viaLookup := function-lookup(xs:QName(''fn:static-base-uri''), 0)()
return
    ($direct = $viaLookup) or (empty($direct) and empty($viaLookup))')
};

declare
    %test:assertEquals("hello world")
function ho:lookup-fn-concat-not-context-dependent() {
    util:eval('xquery version "3.0";
function-lookup(xs:QName(''fn:concat''), 2)(''hello'', '' world'')')
};

declare
    %test:assertEquals("5")
function ho:lookup-fn-string-length-1-arg-not-context-dependent() {
    util:eval('xquery version "3.0";
function-lookup(xs:QName(''fn:string-length''), 1)(''hello'')')
};

declare
    %test:assertEquals("5")
function ho:lookup-named-function-reference-no-captured-focus() {
    util:eval('xquery version "3.0";
declare namespace ex="http://exist-db.org/xquery/ex";
declare function ex:add($a, $b) { $a + $b };
let $f := function-lookup(xs:QName(''ex:add''), 2)
return $f(2, 3)')
};

declare
    %test:assertEquals("b")
function ho:closure-redefined-variable() {
    util:eval('xquery version "3.0";

let $a := ()
let $a := if ($a) then $a else "b"
return
    function() { $a }()')
};
