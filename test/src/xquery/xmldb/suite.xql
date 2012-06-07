xquery version "1.0";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "xqsuite.xql";
import module namespace t="http://exist-db.org/testsuite/permissions" at "xmldb:exist:///db/permission-tests.xql";

test:suite(util:list-functions("http://exist-db.org/testsuite/permissions"))