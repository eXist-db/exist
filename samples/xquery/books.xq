xquery version "1.0";

declare default element namespace "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
declare namespace d="http://purl.org/dc/elements/1.1/";

(: Find all publication dates and print out a list of titles
   for each date :)
for $p in distinct-values(//d:date) 
let $books := //Description[d:date &= $p] 
return 
	<result>
		<year titles="{count($books)}">{$p}</year>
		{for $b in $books return $b/d:title}
	</result>
