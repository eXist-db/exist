xquery version "3.0";

import module namespace test="http://exist-db.org/xquery/xqsuite" 
at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";
import module namespace ot="http://exist-db.org/xquery/optimizer/test" at "optimizer.xql";

test:suite(util:list-functions("http://exist-db.org/xquery/optimizer/test"))