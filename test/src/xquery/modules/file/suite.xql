xquery version "3.0";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";

import module namespace uz="http://exist-db.org/testsuite/modules/file/serialization" at "serialize.xql";

test:suite((util:list-functions("http://exist-db.org/testsuite/modules/file/serialization")))
