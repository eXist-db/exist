xquery version "3.1";

import module namespace test = "http://exist-db.org/xquery/xqsuite"
    at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

declare variable $test-module-uri as xs:anyURI external;

(: hooks for sending external notifications about test events :)
declare variable $test-ignored-function as (function(xs:string) as empty-sequence())? external;
declare variable $test-started-function as (function(xs:string) as empty-sequence())? external;
declare variable $test-failure-function as (function(xs:string, map(xs:string, item()?), map(xs:string, item()?)?) as empty-sequence())? external;
declare variable $test-assumption-failed-function as (function(xs:string, map(xs:string, item()?)) as empty-sequence())? external;
declare variable $test-error-function as (function(xs:string, map(xs:string, item()?)?) as empty-sequence())? external;
declare variable $test-finished-function as (function(xs:string) as empty-sequence())? external;

test:suite(
    inspect:module-functions($test-module-uri),
    $test-ignored-function, $test-started-function, $test-failure-function,
    $test-assumption-failed-function, $test-error-function, $test-finished-function
)
