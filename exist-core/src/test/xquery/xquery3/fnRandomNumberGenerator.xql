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

module namespace fn-rng="http://exist-db.org/xquery/test/fnRandomNumberGenerator";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $fn-rng:long-seed := 123456789;
declare variable $fn-rng:text-seed := 'sample seed';
declare variable $fn-rng:date-seed := xs:date('1970-01-01');
declare variable $fn-rng:dateTime-seed := xs:dateTime('1970-01-01T00:00:00.000Z');
declare variable $fn-rng:number-as-string-seed := "1234567890";
declare variable $fn-rng:long-string-seed := "When life hands you dirt plant seeds.";
declare variable $fn-rng:big-number-seed := 9223372036854775808;
declare variable $fn-rng:zero-seed := 0;

(: special seeds  :)
declare
    %test:assertTrue
function fn-rng:seed-number-equals-number-in-string-seed () {
    fn:random-number-generator($fn-rng:long-seed)?number eq
    fn:random-number-generator($fn-rng:number-as-string-seed)?number
};

(:~
 : the probability of the first one hundred consecutive zeros in a sequence of random
 : decimals between 0 and 1 is very low
 :)
declare
    %test:assertNotEquals(0)
function fn-rng:seed-zero-yields-random-values () {
    let $random := fn:random-number-generator($fn-rng:zero-seed)
    return
        fold-left(1 to 100,
            map {"sequence": (), "generator": $random},
            function ($result, $ignore) {
                map {
                    "sequence": ($result?sequence, $result?generator?number),
                    "generator": $result?generator?next()
                }
            })
        => map:get("sequence")
        => sum()
};

(:~
 : This test documents an implementation detail.
 : It is an interesting fact to know that 123456789 and "123456789" are treated as the same seed.
 : It is also important to know, if the behaviour of the implementation changes in this regard.
 :)
declare
    %test:assertTrue
function fn-rng:seed-number () {
    fn:random-number-generator($fn-rng:long-seed)?number eq
    fn:random-number-generator($fn-rng:number-as-string-seed)?number
};

declare 
    %test:assertExists
function fn-rng:seed-number () {
    fn:random-number-generator($fn-rng:long-seed)
};

declare 
    %test:assertExists
function fn-rng:seed-text () {
    fn:random-number-generator($fn-rng:text-seed)
};

declare
    %test:assertExists
function fn-rng:seed-date () {
    fn:random-number-generator($fn-rng:date-seed)
};

declare
    %test:assertExists
function fn-rng:seed-dateTime () {
    fn:random-number-generator($fn-rng:dateTime-seed)
};

declare
    %test:assertExists
function fn-rng:seed-current-dateTime () {
    fn:random-number-generator(fn:current-dateTime())
};

declare
    %test:assertTrue
function fn-rng:deterministic () {
    fn:random-number-generator($fn-rng:long-seed)?number eq 
    fn:random-number-generator($fn-rng:long-seed)?number
};

declare
    %test:assertTrue
function fn-rng:deterministic-next () {
    fn:random-number-generator($fn-rng:long-seed)?next()?number eq 
    fn:random-number-generator($fn-rng:long-seed)?next()?number
};

declare
    %test:assertTrue
function fn-rng:deterministic-long-string-seed () {
    fn:random-number-generator($fn-rng:long-string-seed)?number eq
    fn:random-number-generator($fn-rng:long-string-seed)?number
};

declare
    %test:assertTrue
function fn-rng:deterministic-big-number-seed () {
    fn:random-number-generator($fn-rng:big-number-seed)?number eq
    fn:random-number-generator($fn-rng:big-number-seed)?number
};

declare
    %test:assertTrue
function fn-rng:deterministic-big-number-seed () {
    fn:random-number-generator($fn-rng:big-number-seed)?number eq
    fn:random-number-generator($fn-rng:big-number-seed)?number
};

declare
    %test:assertTrue
function fn-rng:deterministic-big-number-seed-minus-one () {
    fn:random-number-generator($fn-rng:big-number-seed -1)?number eq
    fn:random-number-generator($fn-rng:big-number-seed -1)?number
};

declare
    %test:assertTrue
function fn-rng:deterministic-decimal-seed () {
    fn:random-number-generator(1.1)?number eq
    fn:random-number-generator(1.1)?number
};

declare
    %test:assertTrue
function fn-rng:decimal-seeds-yield-other-results () {
    (
        fn:random-number-generator(1.1)?number,
        fn:random-number-generator(1.11)?number,
        fn:random-number-generator(1.111)?number,
        fn:random-number-generator(1.1111)?number
    )
    != fn:random-number-generator(1)?number
};

declare
    %private
function fn-rng:get-generator-reference () {
    fn:random-number-generator($fn-rng:long-seed)
};

declare
    %test:assertTrue
function fn-rng:deterministic-reference () {
    let $fn := fn-rng:get-generator-reference()
    return
        $fn?number eq 
        $fn?number
};

declare
    %test:assertTrue
function fn-rng:deterministic-reference-next () {
    let $fn := fn-rng:get-generator-reference()
    return
        $fn?next()?number eq 
        $fn?next()?number
};

declare variable $fn-rng:generator-reference := fn-rng:get-generator-reference();

declare
    %private
function fn-rng:number-from-generator-reference () {
    $fn-rng:generator-reference?next()?number
};

declare
    %test:assertTrue
function fn-rng:deterministic-side-effect () {
    let $call := fn-rng:number-from-generator-reference() 

    return
        fn-rng:number-from-generator-reference() eq 
        $fn-rng:generator-reference?next()?number
};
