xquery version "1.0";

declare namespace t="http://exist-db.org/xquery/debug/test";

declare variable $t:XML :=
	<root>
	   	<a id="a1"/><b id="b1" type="t"/>
		<c id="c1">text</c>
		<d id="d1">
			<e>text</e>
		</d>
	</root>
;

declare function t:test() {
	<test/>
};

<result>
	<var>{$t:XML}</var>
	{ t:test() }
</result>
