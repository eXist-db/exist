xquery version "1.0";

declare namespace xmldb="http://exist-db.org/xquery/xmldb";
declare namespace util="http://exist-db.org/xquery/util";

xmldb:register-database("org.exist.xmldb.DatabaseImpl", true()),
let
	$rootColl := xmldb:collection("xmldb:exist:///db", "admin", ""),
	$targetColl := xmldb:create-collection($rootColl, "output")	
for $item in /rdf:RDF/sn:*
return
	xmldb:store($targetColl, concat(util:md5($item/@rdf:about), ".xml"),
		<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
			{$item}
		</rdf:RDF>
	)
