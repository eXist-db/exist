xquery version "1.0";

import module namespace test="http://exist-db.org/xquery/xqsuite" 
at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

test:suite((
    inspect:module-functions(xs:anyURI("permission-tests.xql")),
    inspect:module-functions(xs:anyURI("last-modified.xql"))
))
