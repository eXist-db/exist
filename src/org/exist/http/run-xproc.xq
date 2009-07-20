xquery version "1.0";

import module namespace xproc = "http://xproc.net/xproc"; 

(:
declare variable $stdin as item()? external;
declare variable $debug as xs:integer external;
:)

declare variable $stdin := ();
declare variable $debug := 0;

xproc:run(/*, $stdin, $debug)



