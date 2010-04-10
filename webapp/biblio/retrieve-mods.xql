module namespace mods="http://www.loc.gov/mods/v3";

declare namespace mads="http://www.loc.gov/mads/";
declare namespace xlink="http://www.w3.org/1999/xlink";
declare namespace fo="http://www.w3.org/1999/XSL/Format";

declare option exist:serialize "media-type=text/xml";

declare function mods:add-part($part, $sep as xs:string) {
    if (empty($part) or string-length($part[1]) eq 0) then
        ()
    else
        concat(string-join($part, " "), $sep)
};

declare function mods:get-publisher($publishers as element(mods:publisher)*) as xs:string? {
    string-join(
        for $publisher in $publishers
        return
            if ($publisher/mods:name) then
                let $name := $publisher/mods:name[1]
                return
                    string-join((
                        $name/mods:namePart[@transliteration],
                        $name/mods:namePart[not(@transliteration)]),
                        " ")
            else
                $publisher/string(),
        ', '
    )
};

declare function mods:get-extent($extent as element(mods:extent)) {
    if ($extent/mods:end) then
        concat($extent/mods:start, " - ", $extent/mods:end)
    else
        $extent/mods:start
};

declare function mods:get-part-and-origin($entry as element()) {
    let $part := $entry/mods:part
    let $origin := $entry/mods:originInfo
    return
        if (exists($part/mods:detail) and exists($part/mods:date)) then
            concat(
                $part/mods:detail[@type='volume']/mods:number,
                if ($part/mods:detail[@type='issue']) then
                    concat('/', $part/mods:detail[@type='issue']/mods:number)
                else (),
                '(', $part/mods:date, ')',
                if ($part/mods:extent) then
                    concat(": ", mods:get-extent($part/mods:extent[1]))
                else ()
            )
        else (
            mods:add-part($origin/mods:place/mods:placeTerm, ": "),
            mods:add-part(mods:get-publisher($origin/mods:publisher[1]), ", "),
            ($origin/mods:dateCreated/string(), $origin/mods:dateIssued/string(), $part/mods:date/string())[1]
        )
};

(: ### <relatedItem><part> begins ### :)

(: Application: "part" is used to provide detailed coding for physical parts of a resource. It may be used as a top level element to designate physical parts or under relatedItem. It may be used under relatedItem for generating citations about the location of a part within a host/parent item. When used with relatedItem type="host", <part> is roughly equivalent to MARC 21 field 773, subfields $g (Relationship information) and $q (Enumeration and first page), but allows for additional parsing of data. There is no MARC 21 equivalent to <part> at the <mods> level. :)
(: Attributes: type, order, ID. :)
    (: Unaccounted for: type, order, ID. :)
(: Suggested values for @type: volume, issue, chapter, section, paragraph, track. :)
    (: Unaccounted for: none. :)
(: Subelements: <detail>, <extent>, <date>, <text>. :)
    (: Unaccounted for: <detail>, <date>, <text>. :)
        (: Problem: <date> does not generally occur in relatedItem. :)
        (: Subelement <extent>. :)
            (: Attribute: type. :)
                (: Suggested values for @type: page, minute. :)
            (: Subelements: <start>, <end>, <total>, <list>. :)
                (: Unaccounted for: <total>, <list>. :)
declare function mods:get-related-part($entry as element()) {
    let $part := $entry/mods:relatedItem/mods:part
    return
        if ($part/mods:extent/mods:start) then
            (
            <tr>
                <td class="label">Pages</td>
            <td>{string-join(($part/mods:extent/mods:start, $part/mods:extent/mods:end), '-')}</td> </tr>
            )
        else $part/mods:extent/text()
};

declare function mods:get-conference($entry as element(mods:mods)) {
    let $date := ($entry/mods:originInfo/mods:dateIssued/string(), $entry/mods:part/mods:date/string(),
            $entry/mods:originInfo/mods:dateCreated/string())[1]
    return
        concat("Paper presented at ", 
            mods:add-part($entry/mods:name[@type = 'conference']/mods:namePart, ", "),
            mods:add-part($entry/mods:originInfo/mods:place/mods:placeTerm, ", "),
            $date
        )
};

declare function mods:name-transliteration($name as element()) as xs:string? {
    if ($name/mods:namePart[@transliteration]) then
        string-join((
           $name/mods:namePart[@transliteration][@type = 'family'],
           $name/mods:namePart[@transliteration][@type = 'given'],
           $name/mods:namePart[@transliteration][not(@type)]
        ), ' ')
    else ()
};

declare function mods:format-name($name as element(mods:name), $pos as xs:integer) {
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
    				    mods:name-transliteration($name), ' ',
    					($family/string(), $given/string())
    				)
				else if ($pos eq 1) then
				    ($family/string(), ', ', $given/string())
				else
					($given/string(), ' ', $family/string())
            else string-join((
                mods:name-transliteration($name), ' ',
                ($name/mods:namePart, $name)[1]
            ), ' ')
            , '')
};

declare function mods:get-authority($name as element(mads:name)?, $lang as xs:string?, $pos as xs:integer) {
    if ($name/@type = 'corporate') then
        $name/mads:namePart/text()
    else if ($name) then
    	let $family := $name/mads:namePart[@type = 'family']
    	let $given := $name/mads:namePart[@type = 'given']
    	return
    		string-join(
     			if ($family and $given) then
     				if ($family/@lang = ('ja', 'zh')) then 
     				    (
         				    mods:name-transliteration($name), ' ',
         					($family/string(), $given/string())
         				)
     				else if ($pos eq 1) then
     				    ($family/string(), ', ', $given/string())
     				else
     					($given/string(), ' ', $family/string())
                 else string-join((
                     mods:name-transliteration($name), ' ',
                     ($name/mads:namePart, $name)[1]
                 ), ' ')
            , '')
    else ()
};

declare function mods:get-name-from-mads($mads as element(mads:mads), $pos as xs:integer) {
    let $auth := $mads/mads:authority
    let $lang := $auth/@lang
    return string-join((
        if ($lang = ('ja', 'zh')) then
            mods:get-authority($mads/mads:variant[@transliteration][1]/mads:name, $lang, $pos)
        else
            (),
        mods:get-authority($auth/mads:name, $lang, $pos)
    ), " ")
};

declare function mods:retrieve-name($name as element(mods:name), $pos as xs:int) {
    let $madRef := replace($name/@xlink:href, "^#?(.*)$", "$1")
    let $mad :=
        if ($madRef) then
            collection("/db/biblio/authority")/mads:mads[@ID = $madRef]
        else
            ()
    return
        if ($mad) then
            mods:get-name-from-mads($mad, $pos)[1]
        else
            mods:format-name($name, $pos)[1]
};

declare function mods:retrieve-names($entry as element()) {
    for $name at $pos in $entry/mods:name
    return
        mods:retrieve-name($name, $pos)
};

declare function mods:get-names($entry as element()) {
    let $names := mods:retrieve-names($entry)
    let $nameCount := count($names)
    let $formatted :=
        if ($nameCount eq 0) then
            ()
        else if ($nameCount eq 1) then
            concat($names[1], '. ')
        else
            concat(
                string-join(subsequence($names, 1, $nameCount - 1), ", "),
                ", and ",
                $names[$nameCount],
                ". "
            )
    return
        $formatted
};

(: ### <titleInfo> begins ### :)

(: The DLF/Aquifer Implementation Guidelines for Shareable MODS Records require the use in all records of at least one <titleInfo> element with one <title> subelement. Other subelements of <titleInfo> are recommended when they apply. This element is repeatable. :)
(: Application: <titleInfo> is repeated for each type attribute value. If multiple titles are recorded, repeat <titleInfo><title> for each. The language of the title may be indicated if desired using the xml:lang (RFC3066) or lang (3-character ISO 639-2 code) attributes. :)
    (: Problem: the wrong language codes seem to be used. :)
(: Attributes: type, authority, displayLabel, xlink, ID, lang, xml:lang, script, transliteration. :)
    (: Unaccounted for: authority, displayLabel, xlink, ID, xml:lang, script. :)
(: Values for @type: abbreviated, translated, alternative, uniform. :)
    (: Unaccounted for: none. :)
(: Subelements: <title>, <subTitle>, <partNumber>, <partName>, <nonSort>. :)
    (: Unaccounted for: <partNumber>, <partName>, <nonSort>. :)

(: Constructs a transliterated/transcribed title for Japanese and Chinese. :)
    (: Problem: What if other languages than Chinese and Japanese occur in a MODS record? :)
    (: Problem: What if several languages with transcription occur in one MODS record? :)
declare function mods:get-title-transliteration($entry as element(), $titleInfo as element(mods:titleInfo)?) {
    let $titleInfo :=
        if ($titleInfo/@lang = 'ja') then
            string-join(($entry/mods:titleInfo[@transliteration = 'romaji']/mods:title, $entry/mods:titleInfo[@transliteration = 'romaji']/mods:subtitle), ' 1: ')
        else if ($titleInfo/@lang = 'zh') then
            string-join(($entry/mods:titleInfo[@transliteration = 'pinyin']/mods:title, $entry/mods:titleInfo[@transliteration = 'pinyin']/mods:subtitle), ' 2: ')
        else
            ()
    return
        if ($titleInfo) then
        (: Since titleInfo is obligatory, this is not needed. :) 
            <span class="title-transliteration">{string-join(($titleInfo/mods:title/string(), $titleInfo/mods:subtitle/string()),' x:x ')}</span> 
        else ()
};

(: If there is a Japanese or Chinese title, any English title will be a translated title. :) 
    (: Problem: a variant or parallel title in English. :)
declare function mods:get-title-translated($entry as element(mods:mods), $titleInfo as element(mods:titleInfo)?) {
    let $titleInfo :=
        if ($titleInfo/@lang = 'ja' or $titleInfo/@lang = 'zh') then
            string-join(($entry/mods:titleInfo[@lang = 'en']/mods:title, $entry/mods:titleInfo[@lang = 'en']/mods:subtitle), ' 3: ')
        else
            ()
    return
        if ($titleInfo) then
            <span class="title-translated">{string-join(($titleInfo/mods:title/string(), $titleInfo/mods:subtitle/string()), ' 4: ') }</span>
        else ()
};

(: This constructs a short title for the hitlist. It uses the first titleInfo title. :)
    (: One could prefer a title in a non-European language, if there was one. :)
    (: This abbreviates Wolfgang's function. :)
declare function mods:get-short-title($id as xs:string?, $entry as element()) {
    let $titleInfo := $entry/mods:titleInfo[1]
    return
        <span><a class="pagination-toggle">{ string-join((mods:get-title-transliteration($entry, $titleInfo), $titleInfo/mods:title/string(), $titleInfo/mods:subtitle/string()), ' : ') }</a>. </span>
};

(: Constructs the full title for the detail view. :)
declare function mods:title-full($titleInfo as element(mods:titleInfo)) {
    <tr>
        <td class="label">Title
        {
            if ($titleInfo/@type = 'translated') then
                concat("(translated", mods:space-before($titleInfo/@lang), ")")
            else if ($titleInfo/@type = 'abbreviated') then
                concat("(abbreviation", mods:space-before($titleInfo/@lang), ")")
            else if ($titleInfo/@type = 'alternative') then
                concat("(alternative", mods:space-before($titleInfo/@lang), ")")
            else if ($titleInfo/@type = 'uniform') then
                concat("(uniform", mods:space-before($titleInfo/@lang), ")")
            else if ($titleInfo/@transliteration) then
                concat("(transliteration", mods:space-before($titleInfo/@lang), ")")
            else if ($titleInfo/@lang) then
                concat("(", $titleInfo/@lang, ")")
            else
                ()
        }
        </td>
        <td>{ string-join(($titleInfo/mods:title, $titleInfo/mods:subtitle), ' : ') }</td>
    </tr>
};

(: ### <titleInfo> ends ### :)

(: ### <relatedItem> begins ### :)
(: Application: relatedItem" includes a designation of the specific type of relationship as a value of the type attribute and is a controlled list of types enumerated in the schema. <relatedItem> is a container element under which any MODS element may be used as a subelement. It is thus fully recursive. :)
(: Attributes: type, xlink:href, displayLabel, ID. :)
(: Values for @type: preceding, succeeding, original, host, constituent, series, otherVersion, otherFormat, isReferencedBy. :)
    (: Unaccounted for: preceding, succeeding, original, constituent, series, otherVersion, otherFormat, isReferencedBy. :)
(: Subelements: any MODS element. :)
(: NB! Is this function used at all? It occurs in mods:format-short, but mods:format-short is not used. The actual function used is mods:entry-full. :)
declare function mods:get-related($entry as element(mods:mods)) {
    let $related0 := $entry/mods:relatedItem[@type = 'host']
    let $collection := util:collection-name($entry)
    let $related :=
        if ($related0/@xlink:href) then
            collection($collection)//mods:mods[@ID = $related0/@xlink:href][1]
        else
            $related0[1]
    return
        if ($related) then
            <span>. In:
            { 
                mods:get-names($related), mods:get-short-title((), $related),
                if ($related/mods:originInfo or $related/mods:part) then
                    mods:get-part-and-origin($related)
                else if ($related/mods:location/mods:url) then
                    concat(", ", $related/mods:location/mods:url)
                else 
                    ()
            }
            </span>
        else
            ()
};

(: ### <relatedItem> ends ### :)

declare function mods:format-short($id as xs:string, $entry as element(mods:mods)) {
    <p>{ 
        mods:get-names($entry),
        mods:get-short-title($id, $entry),
        if ($entry/mods:name[@type = 'conference']) then
            mods:get-conference($entry)
        else (
            mods:get-part-and-origin($entry),
            mods:get-related($entry) 
        ),
        if ($entry/mods:location/mods:url[@displayLabel]) then
            <span> (<a href="{$entry/mods:location/mods:url}">{$entry/mods:location/mods:url/@displayLabel/string()}</a>)</span>
        else ()
    }</p>
};

declare function mods:space-before($node as node()?) as xs:string? {
    if (exists($node)) then
        concat(' ', $node)
    else
        ()
};


declare function mods:names-full($entry as element()) {
    <tr>
        <td class="label">Author</td>
        <td>{
            let $names := mods:retrieve-names($entry)
            for $name in $names
            return
                ($name, <br/>)
        }</td>
    </tr>
};

declare function mods:simple-row($data as item()?, $label as xs:string) as element(tr)? {
    for $d in $data
    where $data != ''
    return
        <tr>
            <td class="label">{$label}</td>
            <td>{string($data)}</td>
        </tr>
};

declare function mods:location-full($entry as element()) as element(tr)* {
    for $d in $entry/mods:location/mods:url
    return
        <tr>
            <td class="label">URL</td>
            <td><a href="{$d}">{$d/string()}</a></td>
        </tr>
};


declare function mods:entry-full($entry as element()) {
    mods:names-full($entry), 
    for $titleInfo in (
        $entry/mods:titleInfo[empty(@type)],
        $entry/mods:titleInfo[@type = 'abbreviated'],
        $entry/mods:titleInfo[@type = 'translated'],
        $entry/mods:titleInfo[@type = 'alternative'],
        $entry/mods:titleInfo[@type = 'uniform']
    )
    return 
    mods:title-full($titleInfo),
    mods:simple-row(mods:get-place($entry/mods:originInfo/mods:place), "Place"),
    mods:simple-row(mods:get-publisher($entry/mods:originInfo/mods:publisher), "Publisher"),
    mods:simple-row($entry/mods:originInfo/mods:dateCreated[1], "Date created"),
    mods:simple-row($entry/mods:originInfo/mods:dateIssued[1], "Date issued"),
    mods:simple-row($entry/mods:originInfo/mods:dateOther, "Other date"),
    mods:simple-row($entry/mods:abstract, "Abstract"),
    mods:location-full($entry),
    (: If there is a related item, get its contents from the related record through its href if it has one; otherwise process the information as given in the record itself:)
    let $relatedItem := $entry/mods:relatedItem[@type = 'host']
    return
        if ($relatedItem) then (
            <tr class="related">
                <td colspan="2">In:</td>
            </tr>,
            if ($relatedItem/@xlink:href) then
                let $collection := util:collection-name($entry)
                for $ref in collection($collection)//mods:mods[@ID = $relatedItem/@xlink:href]
                return
                    (mods:entry-full($ref), mods:get-related-part($entry))
            else
                    (mods:entry-full($relatedItem), mods:get-related-part($entry))
        ) else
            ()
};

declare function mods:format-full($id as xs:string, $entry as element(mods:mods)) {
    let $log := util:log("DEBUG", $entry)
    return
    <table class="biblio-full">
    { mods:entry-full($entry) }
    </table>
};

declare function mods:get-place($places as element(mods:place)*) as xs:string? {
    string-join(
        for $place in $places
        return
            if ($place/mods:placeTerm) then
                let $place := $place[1]
                return
                    string-join((
                        $place/mods:placeTerm[@transliteration],
                        $place/mods:placeTerm[not(@transliteration)]),
                        " ")
            else
                $place/string(),
        ', '
    )
};
