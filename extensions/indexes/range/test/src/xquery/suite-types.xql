xquery version "3.0";

import module namespace test="http://exist-db.org/xquery/xqsuite"
at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

import module namespace rtt="http://exist-db.org/xquery/range/types/test" at "file:extensions/indexes/range/test/src/xquery/types.xql";

test:suite(util:list-functions(xs:anyURI("http://exist-db.org/xquery/range/types/test")))