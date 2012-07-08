xquery version "3.0";

import module namespace test="http://exist-db.org/xquery/xqsuite" 
at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";
import module namespace bang="http://exist-db.org/xquery/test/bang" at "bang.xql";
import module namespace concat="http://exist-db.org/xquery/test/string-concatenation" at "concat.xql";

test:suite((
    util:list-functions("http://exist-db.org/xquery/test/bang"),
    util:list-functions("http://exist-db.org/xquery/test/string-concatenation")
))