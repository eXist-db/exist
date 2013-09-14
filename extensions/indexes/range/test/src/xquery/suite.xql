xquery version "3.0";

import module namespace test="http://exist-db.org/xquery/xqsuite"
at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";
import module namespace rt="http://exist-db.org/xquery/range/test/fields" at "file:extensions/indexes/range/test/src/xquery/fields.xql";
import module namespace ot="http://exist-db.org/xquery/range/optimizer/test" at "file:extensions/indexes/range/test/src/xquery/optimizer.xql";
import module namespace rtt="http://exist-db.org/xquery/range/test" at "file:extensions/indexes/range/test/src/xquery/range.xql";

test:suite((
	util:list-functions(xs:anyURI("http://exist-db.org/xquery/range/test")),
	util:list-functions(xs:anyURI("http://exist-db.org/xquery/range/test/fields")),
	util:list-functions(xs:anyURI("http://exist-db.org/xquery/range/optimizer/test"))
))