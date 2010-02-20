xquery version "1.0";
(: $Id$ :)
(:
    Packages management.
:)

module namespace pckg="http://exist-db.org/packages";

declare function pckg:firstRunImport() as xs:string* {
	if (xmldb:is-admin-user(xmldb:get-current-user())) then
		let $collection := "/db/packages/" (: usage @xmldb:store-files-from-pattern give error, is it bug? :)
		return
		if (xmldb:collection-available("/db/packages/")) then ()
		else
			let $temp := xmldb:create-collection("/db","packages")
			let $temp := xmldb:set-collection-permissions("/db/packages/","admin","dba",509)
			return
			xmldb:store-files-from-pattern(
				"/db/packages",
				concat(system:get-exist-home(),"/webapp/administration/"),
				"**/descriptor.xml",
				'application/xml',
				true())
	else ""
};