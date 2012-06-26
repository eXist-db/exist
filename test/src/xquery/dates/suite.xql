xquery version "3.0";

import module namespace test="http://exist-db.org/xquery/xqsuite" 
at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";
import module namespace fd="http://exist-db.org/xquery/test/format-dates" at "format-dates.xql";

test:suite(util:list-functions("http://exist-db.org/xquery/test/format-dates"))
