xquery version "1.0";

let
	$initialized := 
		xmldb:register-database("org.exist.xmldb.DatabaseImpl", true()),
	$rootColl := 
		xmldb:collection("xmldb:exist:///db", "admin", ""),
	$targetColl :=
		xmldb:create-collection($rootColl, "speakers"),
	$speakers :=
		<speakers>
			{
				for $speaker in //SPEECH[SPEAKER="HAMLET"] return $speaker
			}
		</speakers>
	return
		xmldb:store($targetColl, concat(util:md5("speakers"), ".xml"), $speakers)
