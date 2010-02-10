xquery version "1.0";
(: $Id$ :)
(:
    Packages management.
:)

module namespace pckg="http://exist-db.org/packages";

declare function pckg:firstRunImport() as xs:string* {
	let $collection := "/db/system/packages/" (: usage @xmldb:store-files-from-pattern give error, is it bug? :)
	return
	(: if (xmldb:collection-available("/db/system/packages/")) then () :)
	(: else :)
		xmldb:create-collection("/db/system","packages"),
		xmldb:store-files-from-pattern(
			"/db/system/packages",
			concat(system:get-exist-home(),"/webapp/administration/"),
			"**/descriptor.xml",
			'application/xml',
			true())
};