xquery version "1.0";

declare option exist:serialize "type=xml media-type=text/xml";

import module namespace xqueries = "http://exist-db.org/xquery/admin-interface/xqueries" at "xqueries.xqm";

let $mode := request:get-parameter("mode", "q")
return
	<html>
		<body>
		{
	if ($mode eq "q") then
		xqueries:display-xqueries()
	else
		xqueries:display-processes()
		}
		</body>
	</html>
