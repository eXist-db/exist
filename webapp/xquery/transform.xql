xquery version "1.0";

declare namespace transform="http://exist-db.org/xquery/transform";
declare namespace util="http://exist-db.org/xquery/util";

declare function local:list-functions() as node()
{
	<builtin-functions>
		{
			for $f in util:builtin-functions()
            order by $f
			return
				util:describe-function($f)
		}
	</builtin-functions>
};

let	$params := 
		<parameters>
			<param name="now" value="{current-time()}"/>
		</parameters>
return
	transform:transform(
		<document>
			<title>Available XQuery/XPath functions</title>

			<para>The following tables show all built-in 
			functions available within the current 
			execution context.</para>
			
			{ local:list-functions() }
		</document>,
		"functions.xsl",
		$params
	)
