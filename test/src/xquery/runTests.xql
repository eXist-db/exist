xquery version "1.0";

import module namespace t="http://exist-db.org/xquery/testing";

declare variable $doc external;
declare variable $id external;

let $testSet :=
    if ($doc instance of xs:string) then
    	doc(concat("/db/test/", $doc))/TestSet
    else
    	$doc/TestSet
return
    t:run-testSet($testSet, $id)