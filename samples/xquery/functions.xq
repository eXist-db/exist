xquery version "1.0";

(: prints a list of all built-in functions :)
declare namespace util="http://exist-db.org/xquery/util";

<!-- Currently known functions in eXist -->,
<builtin-functions>
{
	for $f in util:builtin-functions()
	return
		util:describe-function($f)
}
</builtin-functions>
