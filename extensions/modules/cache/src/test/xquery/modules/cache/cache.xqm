xquery version "3.1";

module namespace c="http://exist-db.org/xquery/cache/test";

import module namespace cache="http://exist-db.org/xquery/cache";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $c:cache-name := "test" ;
declare variable $c:simple-options := map {};
declare variable $c:maximumSize := 5;
declare variable $c:maximumSize-options := map { "maximumSize": $c:maximumSize };
declare variable $c:expireAfterAccess := 1000;
declare variable $c:expireAfterAccess-options := map { "expireAfterAccess": $c:expireAfterAccess };

declare function c:_create-simple() {
    cache:create($c:cache-name, $c:simple-options)
};

declare function c:_create-maximumSize() {
    cache:create($c:cache-name, $c:maximumSize-options)
};

declare function c:_create-expireAfterAccess() {
    cache:create($c:cache-name, $c:expireAfterAccess-options)
};

declare function c:_populate($size as xs:integer) {
    (1 to $size) ! cache:put($c:cache-name, "foo" || ., "bar" || .)
};

declare function c:_keys() {
    cache:keys($c:cache-name)
};

declare function c:_cleanup() {
    cache:cleanup($c:cache-name)
};

declare function c:_destroy() {
    cache:destroy($c:cache-name)
};

declare
    %test:assertTrue
function c:create-simple-success() {
    let $setup := c:_destroy()
    return
        c:_create-simple()
};

declare
    %test:assertTrue
function c:create-maximumSize-success() {
    let $setup := c:_destroy()
    return
        c:_create-maximumSize()
};

declare
    %test:assertFalse
function c:create-already-exists() {
    let $setup := 
        (
            c:_destroy(),
            c:_create-simple()
        )
    return
        c:_create-simple()
};

declare
    %test:assertTrue
function c:names() {
    let $setup := 
        (
            c:_destroy(),
            c:_create-simple()
        )
    return
        cache:names() = $c:cache-name
};

declare
    %test:assertEquals("foo1 foo2")
function c:keys() {
    let $setup := 
        (
            c:_destroy(),
            c:_create-simple(),
            c:_populate(2)
        )
    return
        c:_keys() => sort() => string-join(" ")
};

declare
    %test:assertEquals("bar1 bar2")
function c:list-all-values() {
    let $setup := 
        (
            c:_destroy(),
            c:_create-simple(),
            c:_populate(2)
        )
    return
        cache:list($c:cache-name, ()) => sort() => string-join(" ")
};

declare
    %test:assertEquals("bar2")
function c:list-one-value() {
    let $setup := 
        (
            c:_destroy(),
            c:_create-simple(),
            c:_populate(2)
        )
    return
        cache:list($c:cache-name, "foo2")
};

declare
    %test:args(10)
    %test:assertEquals(5)
function c:exercise-maximumSize($size as xs:integer) {
    let $setup := 
        (
            c:_destroy(),
            c:_create-maximumSize(),
            c:_populate($size),
            c:_cleanup()
        )
    return
        count(c:_keys())
};

declare
    %test:assertEquals(0)
function c:exercise-expireAfterAccess() {
    let $setup := 
        (
            c:_destroy(),
            c:_create-expireAfterAccess(),
            c:_populate(5),
            util:wait($c:expireAfterAccess * 1.1),
            c:_cleanup()
        )
    return
        count(c:_keys())
};

declare
    %test:assertEquals("bar5")
function c:get() {
    let $setup := 
        (
            c:_destroy(),
            c:_create-simple(),
            c:_populate(5)
        )
    return
        cache:get($c:cache-name, "foo5")
};

declare
    %test:assertEmpty
function c:remove() {
    let $setup := 
        (
            c:_destroy(),
            c:_create-simple(),
            c:_populate(5),
            cache:remove($c:cache-name, "foo5")
        )
    return
        cache:get($c:cache-name, "foo5")
};

declare
    %test:assertEquals("bar5")
function c:remove-returns-previous-value() {
    let $setup := 
        (
            c:_destroy(),
            c:_create-simple(),
            c:_populate(5)
        )
    return
        cache:remove($c:cache-name, "foo5")
};
