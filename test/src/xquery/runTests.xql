xquery version "1.0";

import module namespace t="http://exist-db.org/xquery/testing";

declare variable $doc external;

if ($doc instance of xs:string) then
	t:run-testSet(doc(concat("/db/test/", $doc))/TestSet)
else
	t:run-testSet($doc/TestSet)