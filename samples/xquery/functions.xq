xquery version "1.0";

(: prints a list of all built-in functions :)
declare namespace util="http://exist-db.org/xquery/util";

<!-- Currently known functions in eXist -->,
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
