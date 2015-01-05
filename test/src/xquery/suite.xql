xquery version "3.0";

import module namespace test="http://exist-db.org/xquery/xqsuite"
at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

test:suite((
    inspect:module-functions(xs:anyURI("last.xql")),
    inspect:module-functions(xs:anyURI("namespaces.xql")),
    inspect:module-functions(xs:anyURI("positional.xql")),
    inspect:module-functions(xs:anyURI("count.xql")),
    inspect:module-functions(xs:anyURI("serializer.xql"))
))