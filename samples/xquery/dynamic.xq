declare namespace x="http://www.x.de";

declare namespace f="urn:test";

declare function f:copyChildren($node as node()) as node()+ {
	($node/@*, for $n in $node/* return f:copy($n))
};

declare function f:copy($node as node()) as node()+ {
	if($node instance of element()) then (
		if(local-name($node) eq "para") then
			element p { f:copyChildren($node) }
		else
			element { node-name($node) } { f:copyChildren($node) }
	) else
		$node
};

<html>
	{f:copy(root(doc("/db/test/test.xml")))}
</html>
