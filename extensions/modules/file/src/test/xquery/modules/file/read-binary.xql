xquery version "3.0";

module namespace read-binary="http://exist-db.org/testsuite/modules/file/read-binary";

import module namespace test="http://exist-db.org/xquery/xqsuite" at "resource:org/exist/xquery/lib/xqsuite/xqsuite.xql";
import module namespace file="http://exist-db.org/xquery/file";


declare
    %test:pending("TODO need to mechanism to setup a temporary file to work with")
    %test:assertEquals("<root>bla</root>")
function read-binary:without-serialization() {
  let $binData := file:read-binary("VERSION.txt")
  return
    element { "root" } { "bla" }
};