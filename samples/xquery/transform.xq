xquery version "1.0";

declare namespace transform="http://exist-db.org/xquery/transform";
declare namespace util="http://exist-db.org/xquery/util";

declare function local:list-functions() as node()
{
	<builtin-functions>
		{
			for $f in util:builtin-functions()
			return
				util:describe-function($f)
		}
    </builtin-functions>
};

let $fn := local:list-functions()
return
	<html>
		<head><title>Available XQuery/XPath functions</title></head>
		<body>
			<h1>Builtin XQuery/XPath functions</h1>
			
			{ transform:transform($fn, "file:samples/xquery/functions.xsl", ()) }
		</body>
	</html>
