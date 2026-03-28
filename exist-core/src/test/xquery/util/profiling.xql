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
 : Tests for util:time(), util:memory(), and util:track() profiling functions.
 :)
module namespace prof = "http://exist-db.org/xquery/test/profiling";

declare namespace test = "http://exist-db.org/xquery/xqsuite";

(: === util:time tests === :)

declare
    %test:assertTrue
function prof:time-returns-result() {
    let $result := util:time(1 + 1)
    return $result eq 2
};

declare
    %test:assertEquals(5)
function prof:time-sequence() {
    count(util:time(1 to 5))
};

declare
    %test:assertEquals("hello")
function prof:time-with-label() {
    util:time("hello", "string test")
};

declare
    %test:assertTrue
function prof:time-empty-sequence() {
    empty(util:time(()))
};

(: === util:memory tests === :)

declare
    %test:assertTrue
function prof:memory-returns-result() {
    let $result := util:memory(1 + 1)
    return $result eq 2
};

declare
    %test:assertEquals("world")
function prof:memory-with-label() {
    util:memory("world", "memory test")
};

(: === util:track tests === :)

declare
    %test:assertTrue
function prof:track-returns-map() {
    let $result := util:track(1 + 1)
    return $result instance of map(*)
};

declare
    %test:assertTrue
function prof:track-has-time-key() {
    let $result := util:track(1 + 1)
    return map:contains($result, "time")
};

declare
    %test:assertTrue
function prof:track-has-memory-key() {
    let $result := util:track(1 + 1)
    return map:contains($result, "memory")
};

declare
    %test:assertTrue
function prof:track-has-value-key() {
    let $result := util:track(1 + 1)
    return map:contains($result, "value")
};

declare
    %test:assertEquals(2)
function prof:track-value-correct() {
    let $result := util:track(1 + 1)
    return $result?value
};

declare
    %test:assertTrue
function prof:track-time-is-duration() {
    let $result := util:track(1 to 100)
    return $result?time instance of xs:dayTimeDuration
};

declare
    %test:assertTrue
function prof:track-memory-is-integer() {
    let $result := util:track(1 to 100)
    return $result?memory instance of xs:integer
};

declare
    %test:assertTrue
function prof:track-with-label() {
    let $result := util:track(1 to 10, "range test")
    return map:contains($result, "label") and $result?label eq "range test"
};

declare
    %test:assertEquals(5)
function prof:track-sequence-value() {
    let $result := util:track(1 to 5)
    return count($result?value)
};

(: === util:explain tests === :)

declare
    %test:assertTrue
function prof:explain-returns-element() {
    let $result := util:explain('1 + 1')
    return $result instance of element()
};

declare
    %test:assertEquals("explain")
function prof:explain-root-element() {
    let $result := util:explain('1 + 1')
    return local-name($result)
};

declare
    %test:assertTrue
function prof:explain-for-has-for-element() {
    let $result := util:explain('for $x in 1 to 5 return $x')
    return exists($result//for)
};

declare
    %test:assertTrue
function prof:explain-let-has-let-element() {
    let $result := util:explain('let $x := 42 return $x')
    return exists($result//let)
};

declare
    %test:assertTrue
function prof:explain-path-has-step() {
    let $result := util:explain('//title')
    return exists($result//step)
};

declare
    %test:assertTrue
function prof:explain-function-call() {
    let $result := util:explain('count(1 to 10)')
    return exists($result//*[contains(@name, "count")])
};

declare
    %test:assertTrue
function prof:explain-conditional() {
    let $result := util:explain('if (true()) then 1 else 2')
    return exists($result//if)
};

(: === util:profile tests === :)

declare
    %test:assertTrue
function prof:profile-returns-map() {
    let $result := util:profile('1 + 1')
    return $result instance of map(*)
};

declare
    %test:assertTrue
function prof:profile-has-result-key() {
    let $result := util:profile('1 + 1')
    return map:contains($result, "result")
};

declare
    %test:assertTrue
function prof:profile-has-time-key() {
    let $result := util:profile('1 + 1')
    return map:contains($result, "time")
};

declare
    %test:assertTrue
function prof:profile-has-memory-key() {
    let $result := util:profile('1 + 1')
    return map:contains($result, "memory")
};

declare
    %test:assertTrue
function prof:profile-has-plan-key() {
    let $result := util:profile('1 + 1')
    return map:contains($result, "plan")
};

declare
    %test:assertTrue
function prof:profile-has-stats-key() {
    let $result := util:profile('1 + 1')
    return map:contains($result, "stats")
};

declare
    %test:assertEquals(2)
function prof:profile-result-correct() {
    let $result := util:profile('1 + 1')
    return $result?result
};

declare
    %test:assertTrue
function prof:profile-plan-is-element() {
    let $result := util:profile('for $x in 1 to 5 return $x')
    return $result?plan instance of element()
};

declare
    %test:assertTrue
function prof:profile-stats-is-element() {
    let $result := util:profile('1 + 1')
    return $result?stats instance of element()
};

(: === util:index-report tests === :)

declare
    %test:assertTrue
function prof:index-report-returns-element() {
    let $result := util:index-report('1 + 1')
    return $result instance of element()
};

declare
    %test:assertTrue
function prof:index-report-with-path() {
    let $result := util:index-report('//title')
    return $result instance of element()
};
