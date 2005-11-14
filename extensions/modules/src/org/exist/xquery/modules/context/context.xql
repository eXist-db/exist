xquery version "1.0";

declare namespace context="http://exist-db.org/xquery/context";

let $null := context:set-serializer("xml", true(), false()) return

<context>
	<firstcontext>{context:get-var("test")}</firstcontext>
	{
	let $null := context:set-var("test", "testvalue") return
	<secondcontext>{context:get-var("test")}</secondcontext>
	}
	<thirdcontext>{context:get-var("test")}</thirdcontext>
</context>
