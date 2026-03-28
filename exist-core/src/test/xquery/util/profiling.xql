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
