xquery version "3.0";

import module namespace test="http://exist-db.org/xquery/xqsuite"
at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

import module namespace ct="http://exist-db.org/xquery/range/conditions/test" at "conditions.xql";

test:suite(util:list-functions(xs:anyURI("http://exist-db.org/xquery/range/conditions/test")))