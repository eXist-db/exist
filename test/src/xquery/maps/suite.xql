xquery version "3.0";

import module namespace test="http://exist-db.org/xquery/xqsuite" 
at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";
import module namespace mt="http://exist-db.org/xquery/test/maps" at "maps.xql";

test:suite(util:list-functions("http://exist-db.org/xquery/test/maps"))
