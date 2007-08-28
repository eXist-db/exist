xquery version "1.0";
(: $Id$ :)

declare namespace f="http://exist-db.org/xquery/test";

declare option exist:serialize "media-type=text/xml";

declare function f:term-callback($term, $data as xs:int+) as element() {
	<entry>
		<frequency>{$data[1]}</frequency>
		<documents>{$data[2]}</documents>
		<term>{$term}</term>
	</entry>
};

<entries>
	<cities>
	{
		util:index-keys(//city/name, "T", util:function("f:term-callback", 2), 1000)
	}
	</cities>
	<population>
	{
		util:index-keys(//city/population, 1, util:function("f:term-callback", 2), 1000)
	}
	</population>
</entries>