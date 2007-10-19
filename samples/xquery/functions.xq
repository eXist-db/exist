xquery version "1.0";

(: prints a list of all built-in functions :)

declare namespace util="http://exist-db.org/xquery/util";

(: Define a local function :)
declare function local:afunction() as xs:double {
	local:afunction(2)
};

(: An overloaded function :)
declare function local:afunction($a as xs:int) as xs:double {
	$a * 2.5
};

<!-- Currently known functions in eXist -->,
<functions>
<builtin-functions>
{
	for $mod in util:registered-modules()
	return
		<module uri="{$mod}">
		{
			for $f in util:registered-functions($mod)
			return
				util:describe-function($f)
		}
		</module>
}
</builtin-functions>
<my-functions>
{
	util:describe-function("local:afunction")
}
</my-functions>
</functions>
