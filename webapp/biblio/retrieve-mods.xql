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
                        $name/mods:namePart/@transliteration,
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
    if ($name/*:namePart[@transliteration]) then
        string-join((
           $name/*:namePart[@transliteration][@type = 'family'],
           $name/*:namePart[@transliteration][@type = 'given'],
           $name/*:namePart[@transliteration][not(@type)]
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

declare function mods:get-transliteration($entry as element(), $title as element(mods:titleInfo)?) {
    let $title :=
        if ($title/@lang = 'ja') then
            $entry/mods:titleInfo[@transliteration = 'romaji']
        else if ($title/@lang = 'zh') then
            $entry/mods:titleInfo[@transliteration = 'pinyin']
        else
            ()
    return
        if ($title) then
            <span class="title-transliteration">{ $title/mods:title/string(), ' ' }</span> 
        else ()
};

declare function mods:get-translated($entry as element(mods:mods), $title as element(mods:titleInfo)?) {
    let $title :=
        if ($title/@lang = 'ja' or $title/@lang = 'zh') then
            $entry/mods:titleInfo[@lang = 'en']
        else
            ()
    return
        if ($title) then
            <span class="title-translated">{ $title/mods:title/string() }</span>
        else ()
};

declare function mods:get-title($id as xs:string?, $entry as element()) {
    let $title := ($entry/mods:titleInfo[@lang = 'ja'], $entry/mods:titleInfo[@lang = 'zh'], 
        $entry/mods:titleInfo)[1]
    return
        <span><a class="pagination-toggle">{ mods:get-transliteration($entry, $title), $title/mods:title/string() }</a>. </span>
};

declare function mods:get-related($entry as element(mods:mods)) {
    let $related0 := $entry/mods:relatedItem[@type = 'host']
    let $related :=
        if ($related0/@xlink:href) then
            //mods:mods[@ID = $related0/@xlink:href][1]
        else
            $related0[1]
    return
        if ($related) then
            <span>. In:
            { 
                mods:get-names($related), mods:get-title((), $related),
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

declare function mods:format-short($id as xs:string, $entry as element(mods:mods)) {
    <p>{ 
        mods:get-names($entry),
        mods:get-title($id, $entry),
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

declare function mods:title-full($title as element(mods:titleInfo)) {
    <tr>
        <td class="label">Title
        {
            if ($title/@type = 'translated') then
                "(translated)"
            else if ($title/@type = 'abbreviated') then
                concat("(abbrev.", mods:space-before($title/@lang), ")")
            else if ($title/@lang) then
                concat("(", $title/@lang, ")")
            else if ($title/@transliteration) then
                concat("(translit.", mods:space-before($title/@lang), ")")
            else
                ()
        }
        </td>
        <td>{$title/string()}</td>
    </tr>
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
    for $title in (
        $entry/mods:titleInfo[empty(@type)],
        $entry/mods:titleInfo[@type = 'abbreviated'],
        $entry/mods:titleInfo[@type = 'translated']
    )
    return mods:title-full($title),
    mods:simple-row($entry/mods:abstract, "Abstract"),
    mods:simple-row($entry/mods:originInfo/mods:dateCreated[1], "Date created"),
    mods:simple-row($entry/mods:originInfo/mods:dateIssued[1], "Date issued"),
    mods:simple-row($entry/mods:originInfo/mods:dateOther, "Other date"),
    mods:simple-row($entry/mods:originInfo/mods:place, "Place"),
    mods:simple-row(mods:get-publisher($entry/mods:originInfo/mods:publisher), "Publisher"),
    mods:location-full($entry),
    let $related := $entry/mods:relatedItem[@type = 'host']
    return
        if ($related) then (
            <tr class="related">
                <td colspan="2">In:</td>
            </tr>,
            if ($related/@xlink:href) then
                for $ref in //mods:mods[@ID = $related/@xlink:href]
                return
                    mods:entry-full($ref)
            else
                mods:entry-full($related)
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