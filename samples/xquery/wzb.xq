xquery version "1.0";

declare namespace xmldb="http://exist-db.org/xquery/xmldb";
declare namespace util="http://exist-db.org/xquery/util";
declare namespace f="http://my-namespaces.org";

declare function f:create($n as element()) as node() {
	<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
		{$n}
	</rdf:RDF>
};

declare function f:docname($url as xs:string) as xs:string {
	concat(util:md5($url), ".xml")
};

declare function f:init($collection as xs:string) {
	let $rootColl := xmldb:collection("xmldb:exist:///db", "admin", "")
	return
		if ($rootColl) then
			xmldb:create-collection($rootColl, $collection)
		else
			false()
};

xmldb:register-database("org.exist.xmldb.DatabaseImpl", true()),
let $collection := f:init("output")
return
	if($collection) then
		for $rec in /rdf:RDF/sn:* return
			xmldb:store($collection, f:docname($rec/@rdf:about),
				f:create($rec))
	else
		<error>Could not create output collection</error>
