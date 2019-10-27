xquery version "3.1";

module namespace fn-rng="http://exist-db.org/xquery/test/fnRandomNumberGenerator";

declare namespace test="http://exist-db.org/xquery/xqsuite";

declare variable $fn-rng:long-seed := 123456789;
declare variable $fn-rng:text-seed := 'sample seed';
declare variable $fn-rng:date-seed := xs:date('1970-01-01');
declare variable $fn-rng:dateTime-seed := xs:dateTime('1970-01-01T00:00:00.000Z');

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
