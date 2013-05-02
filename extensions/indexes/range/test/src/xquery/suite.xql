xquery version "3.0";

import module namespace test="http://exist-db.org/xquery/xqsuite"
at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

test:suite(inspect:module-functions(xs:anyURI("file:extensions/indexes/range/test/src/xquery/range.xql")))
