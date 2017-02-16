xquery version "3.0";

import module namespace test="http://exist-db.org/xquery/xqsuite"
at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

import module namespace qf="http://exist-db.org/xquery/lucene/test/query-field" at "query-field.xql";

test:suite(util:list-functions(xs:anyURI("http://exist-db.org/xquery/lucene/test/query-field")))