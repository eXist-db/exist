xquery version "1.0";
declare namespace rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#";
declare namespace dc="http://purl.org/dc/elements/1.1/";

for $p in distinct-values(//dc:creator)
let $books := //rdf:Description[dc:creator&=$p]
return
	<result>
		<creator titles="{count($books)}">{$p}</creator>
		{for $b in $books return $b/dc:title}
	</result>
