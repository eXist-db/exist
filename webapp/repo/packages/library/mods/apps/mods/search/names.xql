module namespace nameutil="http://exist-db.org/xquery/biblio/names";

declare namespace mods="http://www.loc.gov/mods/v3";

(:~
    If there's a transliteration, use it for sorting.
:)
declare function nameutil:name-transliteration($name as element()) as xs:string? {
    if ($name/mods:namePart[@transliteration]) then
        concat(' ',
            string-join((
               $name/mods:namePart[@transliteration][@type = 'family'],
               $name/mods:namePart[@transliteration][@type = 'given'],
               $name/mods:namePart[@transliteration][not(@type)]
            ), ' ')
        )
    else ()
};

(:~
    Try to extract the last name if author names come as a single string.
:)
declare function nameutil:normalize-name($name as xs:string) as xs:string {
    if (contains($name, ",")) then
        $name
    else
        let $split := text:groups($name, "^(.*)\s+([^\s]+)$")
        return
            concat($split[3], ", ", $split[2])
};

(:~
    Extract a name string from the given mods:name element.
    Chinese and Japanese names are handled differently here.
:)
declare function nameutil:format-name($name as element(mods:name)) as xs:string* {
	if ($name/@type = 'corporate') then
        $name/mods:namePart[@lang]/string()
	else
		let $family := $name/mods:namePart[@type = 'family'][not(@transliteration)][1]
		let $given := $name/mods:namePart[@type = 'given'][not(@transliteration)][1]
		return
		  string-join(
     			if ($family and $given) then
     				if ($family/@lang = ('ja', 'zh')) then 
     				    (
         				    nameutil:name-transliteration($name),
         					$family/string(), $given/string()
         				)
     				else
     				    ($family/string(), ', ', $given/string())
                 else string-join((
                     nameutil:name-transliteration($name),
                     nameutil:normalize-name(($name/mods:namePart, $name)[1])
                 ), ' ')
        , '')
};