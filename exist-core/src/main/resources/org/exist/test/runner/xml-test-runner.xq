xquery version "3.1";

import module namespace t = "http://exist-db.org/xquery/testing"
    at "resource:org/exist/xquery/lib/test.xq";

declare variable $doc external;
declare variable $id as xs:string? external;

(: hooks for sending external notifications about test events :)
declare variable $test-ignored-function as (function(xs:string) as empty-sequence())? external;
declare variable $test-started-function as (function(xs:string) as empty-sequence())? external;
declare variable $test-failure-function as (function(xs:string, map(xs:string, item()?), map(xs:string, item()?)?) as empty-sequence())? external;
declare variable $test-assumption-failed-function as (function(xs:string, map(xs:string, item()?)) as empty-sequence())? external;
declare variable $test-error-function as (function(xs:string, map(xs:string, item()?)?) as empty-sequence())? external;
declare variable $test-finished-function as (function(xs:string) as empty-sequence())? external;

let $testSet :=
    if ($doc instance of xs:string) then
    	doc(concat("/db/test/", $doc))/TestSet
    else
    	$doc/TestSet
return
    t:run-testSet($testSet, $id,
            $test-ignored-function, $test-started-function, $test-failure-function,
            $test-assumption-failed-function, $test-error-function, $test-finished-function)
