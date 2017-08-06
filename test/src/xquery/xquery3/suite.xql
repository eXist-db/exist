xquery version "3.0";

import module namespace test="http://exist-db.org/xquery/xqsuite"
at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

test:suite((
    inspect:module-functions(xs:anyURI("serialize.xql")),
    inspect:module-functions(xs:anyURI("bang.xql")),
    inspect:module-functions(xs:anyURI("concat.xql")),
    inspect:module-functions(xs:anyURI("groupby.xql")),
    inspect:module-functions(xs:anyURI("flwor.xql")),
    inspect:module-functions(xs:anyURI("typeswitch.xql")),
    inspect:module-functions(xs:anyURI("fn.xql")),
    inspect:module-functions(xs:anyURI("fnRefs.xql")),
    inspect:module-functions(xs:anyURI("fnSort.xql")),
    inspect:module-functions(xs:anyURI("arrowop.xql")),
    inspect:module-functions(xs:anyURI("strconstr.xql")),
    inspect:module-functions(xs:anyURI("load-xquery-module.xql"))
))
