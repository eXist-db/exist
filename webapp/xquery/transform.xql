xquery version "1.0";
(: $Id$ :)

declare namespace transform="http://exist-db.org/xquery/transform";
declare namespace util="http://exist-db.org/xquery/util";

declare function local:list-functions() as node()
{
     <builtin-functions>
     {
     	for $mod in util:registered-modules()
		let $functions := util:registered-functions($mod)
		order by $mod descending
        return
			<module namespace="{$mod}">
				<description>{util:get-module-description($mod)}</description>
				{
					for $f in $functions 
					return
                    	util:describe-function($f)
				}
			</module>
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
