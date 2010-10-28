module namespace mods="http://www.loc.gov/mods/v3";

declare namespace mads="http://www.loc.gov/mads/";
declare namespace xlink="http://www.w3.org/1999/xlink";
declare namespace fo="http://www.w3.org/1999/XSL/Format";
declare namespace functx = "http://www.functx.com"; 

declare option exist:serialize "media-type=text/xml";

(: TODO: A lot of restrictions to the first item in a sequence ([1]) have been made; these must all be changed to for-structures. :)

(: ### general functions begin ###:)

 
declare function functx:replace-first( $arg as xs:string?, $pattern as xs:string, $replacement as xs:string )  as xs:string {       
   replace($arg, concat('(^.*?)', $pattern),
             concat('$1',$replacement))
 } ;
 
 declare function functx:camel-case-to-words( $arg as xs:string?, $delim as xs:string ) as xs:string? {
   concat(substring($arg,1,1), replace(substring($arg,2),'(\p{Lu})', concat($delim, '$1')))
};

declare function functx:capitalize-first( $arg as xs:string? ) as xs:string? {       
   concat(upper-case(substring($arg,1,1)),
             substring($arg,2))
};
 
declare function functx:trim 
  ( $arg as xs:string? )  as xs:string {
       
   replace(replace($arg,'\s+$',''),'^\s+','')
 } ;
 
 declare function mods:space-before($node as node()?) as xs:string? {
    if (exists($node)) then
        concat(' ', $node)
    else
        ()
};

declare function mods:get-collection($entry as element(mods:mods)) {
    let $collection := util:collection-name($entry)
    let $collection-short := functx:replace-first($collection, "/db/", "")
    return
                <tr>
                <td class="host">In Collection:</td>
                <td>
                {$collection-short}
                </td>
                </tr>            
};


(: ### general functions end ###:)

declare function mods:add-part($part, $sep as xs:string) {
    if (empty($part) or string-length($part[1]) eq 0) then
        ()
    else
        concat(string-join($part, " "), $sep)
};

declare function mods:get-publisher($publishers as element(mods:publisher)?) as xs:string? {
    string-join(
        for $publisher in $publishers
        return
            if ($publisher/mods:name) then
            (: the encoding of <publisher> with <name and <namePart> is not standard.:)
                let $name := $publisher/mods:name[1]
                return
                    string-join((
                        $name/mods:namePart[@transliteration],
                        $name/mods:namePart[not(@transliteration)]),
                        " ")
            else
                    string-join((
                        $publisher[@transliteration],
                        $publisher[not(@transliteration)]),
                        " "),
        ', '
    )
};

(: ### <originInfo> begins ### :)


(: <extent> belongs to <physicalDescription>, <part> as a top level element and <part> under <relatedItem>. 
Under <physicalDescription>, <extent> has no subelements.:)

declare function mods:get-extent($extent as element(mods:extent)?) {
    if ((functx:trim($extent/mods:start)) or (functx:trim($extent/mods:end))) then
      concat(
      if ($extent/@unit, ' ') then
      concat(($extent/@unit), ' ')
      else
      ()
      ,
      concat($extent/mods:start, "-", $extent/mods:end))
    else
    $extent/string()
    (:<total>, <list> and <extent> with no subelemet shoulld all give the string value of <extent>. They do not require unit.:)
        
};

(:
declare function mods:get-extent($extent as element(mods:extent)?) {
    if  ($extent/mods:list/string()) then
    $extent/mods:list
    else
    concat(
    if  (($extent/mods:start/string()) or ($extent/mods:end/string())) then
      concat($extent/@unit, ' ')
      else
      ()
       ,
    if (string-length($extent/mods:end) > 0 or string-length($extent/mods:end) > 0) then
        concat($extent/mods:start, "-", $extent/mods:end)
    else
        $extent/mods:total
      )
};
:)


(: ### <originInfo> begins ### :)

(: The DLF/Aquifer Implementation Guidelines for Shareable MODS Records require the use of at least one <originInfo> element with at least one date subelement in every record, one of which must be marked as a key date. <place>, <publisher>, and <edition> are recommended if applicable. These guidelines make no recommendation on the use of the elements <issuance> and <frequency>. This element is repeatable. :)
 (: Application: <titleInfo> is repeated for each type attribute value. If multiple titles are recorded, repeat <titleInfo><title> for each. The language of the title may be indicated if desired using the xml:lang (RFC3066) or lang (3-character ISO 639-2 code) attributes. :)
    (: Problem:  :)
(: Attributes: lang, xml:lang, script, transliteration. :)
    (: Unaccounted for:  :)
(: Subelements: <place> [RECOMMENDED IF APPLICABLE], <publisher> [RECOMMENDED IF APPLICABLE], <dateIssued> [AT LEAST ONE DATE ELEMENT IS REQUIRED], <dateCreated> [AT LEAST ONE DATE ELEMENT IS REQUIRED], <dateCaptured> [NOT RECOMMENDED], <dateValid> [NOT RECOMMENDED], <dateModified> [NOT RECOMMENDED], <copyrightDate> [AT LEAST ONE DATE ELEMENT IS REQUIRED], <dateOther> [AT LEAST ONE DATE ELEMENT IS REQUIRED], <edition> [RECOMMENDED IF APPLICABLE], <issuance> [OPTIONAL], <frequency> [OPTIONAL]. :)
    (: Unaccounted for: . :)
    (: <place> :)
        (: Repeat <place> for recording multiple places. :)
        (: Attributes: type [RECOMMENDED IF APPLICABLE] authority [RECOMMENDED IF APPLICABLE]. :)
            (: @type :)
                (: Values:  :)    
                    (: Unaccounted for:  :)
        (: Subelements: <placeTerm> [REQUIRED]. :)
            (: Attributes: type [REQUIRED]. :)
                (: Values: text, code. :)
    (: <publisher> :)
        (: Attributes: none. :)
    (: dates [AT LEAST ONE DATE ELEMENT IS REQUIRED] :)
        (: The MODS schema includes several date elements intended to record different events that may be important in the life of a resource. :)
    
declare function mods:get-place($places as element(mods:place)*) as xs:string? {
    string-join(
        for $place in $places
        return
            if ($place/mods:placeTerm[@type = 'text']) then
                let $place := $place[1]
                return
                    string-join((
                        $place/mods:placeTerm[@transliteration],
                        $place/mods:placeTerm[not(@transliteration)]),
                        ", ")
            else
                $place/mods:placeTerm[not(@type)]/string(),
        ' '
    )
};

(: <part> is found both as a top level element and under <relatedItem>.:)

declare function mods:get-part-and-origin($entry as element()) {
    let $part := $entry/mods:part
    let $origin := $entry/mods:originInfo
    return
        if (exists($part/mods:detail) and exists($part/mods:date)) then
            concat(
                $part/mods:detail[@type='volume']/mods:number,
                if ($part/mods:detail[@type='issue']) then
                    concat('/', $part/mods:detail[@type='issue']/mods:number)
                    (: concat((if ($part/mods:detail/mods:caption) then $part/mods:detail/mods:caption/string() else '/'), $part/mods:detail[@type='issue']/mods:number) :)
                else (),
                ' (', $part/mods:date, ')',
                if ($part/mods:extent) then
                    concat(": ", mods:get-extent($part/mods:extent[1]),'.')
                else ()
            )
        else (
            mods:add-part($origin/mods:place/mods:placeTerm, ": "),
            mods:add-part(mods:get-publisher($origin/mods:publisher[1]), ", "),
            if ($entry/mods:relatedItem/mods:originInfo/mods:dateCreated) then () else
            mods:add-part($origin/mods:dateCreated/string(),'.'), 
            if ($entry/mods:relatedItem/mods:originInfo/mods:dateIssued) then () else
            mods:add-part($origin/mods:dateIssued/string(), '.'))
};

(: ### <originInfo> ends ### :)

(: ### <relatedItem><part> begins ### :)

(: Application: "part" is used to provide detailed coding for physical parts of a resource. It may be used as a top level element to designate physical parts or under relatedItem. It may be used under relatedItem for generating citations about the location of a part within a host/parent item. When used with relatedItem type="host", <part> is roughly equivalent to MARC 21 field 773, subfields $g (Relationship information) and $q (Enumeration and first page), but allows for additional parsing of data. There is no MARC 21 equivalent to <part> at the <mods> level. :)
(: Attributes: type, order, ID. :)
    (: Unaccounted for: type, order, ID. :)
(: Suggested values for @type: volume, issue, chapter, section, paragraph, track. :)
    (: Unaccounted for: none. :)
(: Subelements: <detail>, <extent>, <date>, <text>. :)
    (: Unaccounted for: <text>. :)
        (: Problem: <date> does not generally occur in relatedItem. :)
        (: Subelement <extent>. :)
            (: Attribute: type. :)
                (: Suggested values for @type: page, minute. :)
            (: Subelements: <start>, <end>, <total>, <list>. :)
                (: Unaccounted for: <total>, <list>. :)

declare function mods:get-related-item-part($entry as element()) {

    let $part := $entry/mods:relatedItem[@type="host"][1]/mods:part
    let $volume := $part/mods:detail[@type='volume']/mods:number
    let $issue := $part/mods:detail[@type='issue']/mods:number
    let $date := $part/mods:date
    let $extent := mods:get-extent($part/mods:extent)

    return
    if ($part or $volume or $issue or $date or $extent) then
    (
            <tr>
                <td class="host">Place in Publication:</td>
                <td class="related"><hr/></td>
            </tr>
            ,
            if ($volume and $issue) then
            <tr>
                <td class="label">Volume/Issue</td>
                <td class="record">{string-join(($volume/string(), $issue/string()), '/')}</td>
            </tr>
            else if ($volume) then
            <tr>
                <td class="label">Volume</td>
                <td class="record">{$volume/string()}</td>
            </tr>
            else if ($issue) then
            <tr>
                <td class="label">Issue</td>
                <td class="record">{$issue/string()}</td>
            </tr>
            else
            ()
            ,
            if ($date) then
            <tr>
                <td class="label">Date</td>
                <td class="record">{$date/string()}</td>
            </tr>
            else
            ()
            ,
            if ($extent) then
            <tr>
                <td class="label">Extent</td>
                <td class="record">{$extent}</td>
            </tr>
            else
            ()
    )
    else
    ()
};

(: ### <name> begins ### :)

(: The DLF/Aquifer Implementation Guidelines for Shareable MODS Records requires the use of at least one <name> element to describe the creator of the intellectual content of the resource, if available. The guidelines recommend the use of the type attribute with all <name> elements whenever possible for greater control and interoperability. In addition, they require the use of <namePart> as a subelement of <name>. This element is repeatable. :)
 (: Application: <titleInfo> is repeated for each type attribute value. If multiple titles are recorded, repeat <titleInfo><title> for each. The language of the title may be indicated if desired using the xml:lang (RFC3066) or lang (3-character ISO 639-2 code) attributes. :)
    (: Problem:  :)
(: Attributes: type [RECOMMENDED], authority [RECOMMENDED], xlink, ID, lang, xml:lang, script, transliteration. :)
    (: Unaccounted for: authority, xlink, ID, (lang), xml:lang, script. :)
    (: @type :)
        (: Values: personal, corporate, conference. :)
            (: Unaccounted for: none. :)
(: Subelements: <namePart> [REQUIRED], <displayForm> [OPTIONAL], <affiliation> [OPTIONAL], <role> [RECOMMENDED], <description> [NOT RECOMMENDED]. :)
    (: Unaccounted for: <displayForm>, <affiliation>, <role>, <description>. :)
    (: <namePart> :)
    (: "namePart" includes each part of the name that is parsed. Parsing is used to indicate a date associated with the name, to parse the parts of a corporate name (MARC 21 fields X10 subfields $a and $b), or to parse parts of a personal name if desired (into family and given name). The latter is not done in MARC 21. Names are expected to be in a structured form (e.g. surname, forename). :)
        (: Attributes: type [RECOMMENDED IF APPLICABLE]. :)
            (: @type :)
                (: Values: date, family, given, termsOfAddress. :)    
                    (: Unaccounted for: date, termsOfAddress :)
        (: Subelements: none. :)
    (: <role> :)
        (: Attributes: none. :)
        (: Subelements: <roleTerm> [REQUIRED]. :)
            (: <roleTerm> :)
            (: Unaccounted for: none. :)
                (: Attributes: type [RECOMMENDED], authority [RECOMMENDED IF APPLICABLE]. :)
                (: Unaccounted for: type [RECOMMENDED], authority [RECOMMENDED IF APPLICABLE] :)
                    (: @type :)
                        (: Values: text, code. :)    
                            (: Unaccounted for: text, code :)

(: Both the name as given in the publication and the autority name should be rendered. :)

declare function mods:eastern-name-transliteration($name as element()) as xs:string? {
    if ($name/mods:namePart[@transliteration = ('pinyin', 'romaji')]) then
    let $family := $name/mods:namePart[@transliteration = ('pinyin', 'romaji')][@type = 'family'][1]
    (: The [1] takes care of cases where several transliterations (both Japanese and Chinese) are used. Such transliterations are irregular and we will only treat the first one. :)
    let $given := $name/mods:namePart[@transliteration = ('pinyin', 'romaji')][@type = 'given'][1]
    let $address := $name/mods:namePart[@transliteration = ('pinyin', 'romaji')][@type = 'termsOfAddress'][1]
    (: Should be moved to format-name. :)
    return
        string-join((
            $family, $given,
            if ($address) then concat(' ,', $address) else (),
            $name/mods:namePart[@transliteration][not(@type)]
            (: NB: What does the last line do??? :)
            ), ' ')
    else ()
};

(: NB! Dummy function!!!! :)
declare function mods:non-eastern-name-transliteration($name as element()) as xs:string? {
    if ($name/mods:namePart[@transliteration = ('pinyin', 'romaji')]) then
    let $family := $name/mods:namePart[@transliteration = ('pinyin', 'romaji')][@type = 'family'][1]
    (: The [1] takes care of cases where several transliterations (both Japanese and Chinese) are used. Such transliterations are irregular and we will only treat the first one. :)
    let $given := $name/mods:namePart[@transliteration = ('pinyin', 'romaji')][@type = 'given'][1]
    let $address := $name/mods:namePart[@transliteration = ('pinyin', 'romaji')][@type = 'termsOfAddress'][1]
    return
        string-join((
            functx:trim($family), functx:trim($given),
            if ($address) then concat(' ,', functx:trim($address)) else (),
            $name/mods:namePart[@transliteration][not(@type)]
            (: NB: What does the last line do??? :)
            ), ' ')
    else ()
};

declare function mods:get-conference-hitlist($entry as element(mods:mods)) {
    let $date := ($entry/mods:originInfo/mods:dateIssued/string()[1], $entry/mods:part/mods:date/string()[1],
            $entry/mods:originInfo/mods:dateCreated/string())[1]
    let $conference := $entry/mods:name[@type = 'conference']/mods:namePart
    return
    if ($conference) then
        concat("Paper presented at ", 
            mods:add-part($conference/string(), ", "),
            mods:add-part($entry/mods:originInfo/mods:place/mods:placeTerm, ", "),
            $date
        )
        else
        ()
};

declare function mods:get-conference-detail-view($entry as element()) {
    (:let $date := ($entry/mods:originInfo/mods:dateIssued/string()[1], $entry/mods:part/mods:date/string()[1],
            $entry/mods:originInfo/mods:dateCreated/string())[1]
    return:)
    let $conference := $entry/mods:name[@type = 'conference']/mods:namePart
    return
    if ($conference) then
        concat("Paper presented at ", $conference/string()
            (: , mods:add-part($entry/mods:originInfo/mods:place/mods:placeTerm, ", "), $date:)
            (: no need to duplicate placeinfo in detail view. :)
        )
    else
    ()
};

declare function mods:format-name($name as element(mods:name), $pos as xs:integer) {
    if ($name[not(@type)]) then
    concat(
    string-join($name/mods:namePart[@transliteration][not(@type)], ' '), ' ', string-join($name/mods:namePart[not(@transliteration)][not(@type)], ' ')
    )
    else
    	if ($name/@type = 'conference') then
    	(: get-conference-detail-view and get-conference-hitlist take care of conference. :)
    	()
    	else if ($name/@type = 'corporate') then
            concat(string-join($name/mods:namePart[@transliteration]/string(), ' '), ' ', string-join($name/mods:namePart[not(@transliteration)]/string(), ' '))
            (: Does not need manipulation, since it is never decomposed. :)
    	else
    	(: @type = 'personal'. :)
    		let $family := $name/mods:namePart[@type = 'family'][not(@transliteration)]
    		let $given := $name/mods:namePart[@type = 'given'][not(@transliteration)]
    		let $address := $name/mods:namePart[@type = 'termsOfAddress'][not(@transliteration)]
    		let $date := string-join($name/mods:namePart[@type = 'date'], ' ') 
    		(: Any type of date is represented here. :)
    		return
    		  concat(string-join(
    			if ($family and $given) then
    				if ($family/@transliteration = ('pinyin', 'romaji')) then
    				(: No matter which position they have, Japanese and Chinese names are formatted the same. :)
    				(: Problem: what if Westeners have Chinese names? Can one assume that the form in original script comes first? Can one assume that transliteration comes after original script? This is actually a fault in MODS. <namePart>s that belong together should be grouped. :) 
    				(: We assume that the name in native script occurs first, that the existence of a transliterated name implies the existence of a native-script name. :)
    				    (mods:eastern-name-transliteration($name), ' ',
        					(functx:trim(string-join($family[@lang = ('zh', 'ja')]/string(), ' ')), 
        					functx:trim(string-join($given[@lang= ('zh', 'ja')]/string()
        					, ' '))))
        					(: NB: Something is wrong here. The [1] should not ne necessary. :)
        					(: The string-joins are meant to capture multiple family and given names. :)
    				else if ($family[@transliteration]) then
    				(: Some other kind of transcription. :)
    				(: NB: Must be filled out for each value. :) 
    				    (mods:non-eastern-name-transliteration($name), ' ',
        					(functx:trim(string-join($family/string(), ' ')), 
        					functx:trim(string-join($given/string(), ' '))))
        					(: NB: Something is wrong here. The [1] should not ne necessary. :)
        					(: The string-joins are meant to capture multiple family and given names. :)
    				else if ($pos eq 1) then
    				(: If we have a non-Chinese, non-Japanese name occurring first. :)
    				    (functx:trim(string-join($family/string(), ' ')), 
    				    ', ', 
    				    functx:trim(string-join($given, ' ')),
    				    if ($address) then functx:trim(string-join($address, ', ')) else ()
    				    )
    				else
    				(: If we have a non-Chinese, non-Japanese name occurring elsewhere. :)
    					(functx:trim(string-join($given, ' ')), 
    					' ', 
    					functx:trim(string-join($family/string(), ' ')),
    				    if ($address) then functx:trim(string-join($address, ', ')) else ()
    				    )
                else
                    (: One could check for ($family or $given). :)
                    (functx:trim(mods:eastern-name-transliteration($name)))
                    (: If there is a transliteration, but no name in original script. :)
              , ''), 
              (: If there are any nameParts with @date, they are given last, without regard to transliteration or language. :)
              (if ($date) then concat(' (', functx:trim($date), ')') else ()))
              (: NB: Why is this part only shown in list-view? :)
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
         				    mods:eastern-name-transliteration($name), ' ',
         					($family/string(), $given/string())
         				)
     				else if ($pos eq 1) then
     				    ($family/string(), ', ', $given/string())
     				else
     					($given/string(), ' ', $family/string())
                 else string-join((
                     mods:eastern-name-transliteration($name), ' ',
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
            if (ends-with($names[1], ".")) then (: Places period after single author name, if it does not end in period. :)
            concat($names[1], ' ')
            else
            concat($names[1], '. ')
        else
            concat(
                string-join(subsequence($names, 1, $nameCount - 1), ", "),(: Places comma after single author name. :)
                ", and ",(: Is comma needed? :)
                $names[$nameCount],
                ". "(: Places period after last author name. :)
            )
    return
        $formatted
};

(: NB! Create function to render real names from abbreviations! :)
(:
declare function mods:get-language-name() {
};
:)

(: ### <typeOfResource> begins ### :)

declare function mods:return-type($id as xs:string, $entry as element(mods:mods)) {
let $type := $entry/mods:typeOfResource[1]/string()
    return
     <span>{ 
        replace(
        if($type) then
        $type
        else
        'text'
        ,' ','_')
        }
      </span>  
};

(: ### <typeOfResource> ends ### :)

(: ### <name> ends ### :)

(: NB! Create function to get <typeOfResource>! :)
(: The DLF/Aquifer Implementation Guidelines for Shareable MODS Records require the use in all records of at least one <typeOfResource> element using the required enumerated values. This element is repeatable. :)
    (: The values for <typeOfResource> are restricted to those in the following list: text, cartographic, notated music, sound recording [if not possible to specify "musical" or "nonmusical"], sound recording-musical, sound recording-nonmusical, still image, moving image, three dimensional object, (software, multimedia) [NB! comma in value], mixed material :)
    (: Subelements: none. :)
    (: Attributes: collection [RECOMMENDED IF APPLICABLE], manuscript [RECOMMENDED IF APPLICABLE]. :)
        (: @collection, @manuscript :)
            (: Values: yes, no. :)
(:
declare function mods:get-resource-type() {
};
:)

(: NB! Create function to get <genre>! :)
(: The DLF /Aquifer Implementation Guidelines for Shareable MODS Records recommend the use of at least one <genre> element in every MODS record and, if a value is provided, require the use of a value from a controlled list and the designation of this list in the authority attribute. This element is repeatable. :)
    (: The values for <typeOfResource> are restricted to those in the following list: text, cartographic, notated music, sound recording [if not possible to specify "musical" or "nonmusical"], sound recording-musical, sound recording-nonmusical, still image, moving image, three dimensional object, software, multimedia, mixed material :)
    (: Subelements: none. :)
    (: Attributes: type, authority [REQUIRED], lang, xml:lang, script, transliteration. :)
(:
declare function mods:get-genre() {
};
:)

(: ### <titleInfo> begins ### :)

(: The DLF/Aquifer Implementation Guidelines for Shareable MODS Records require the use in all records of at least one <titleInfo> element with one <title> subelement. Other subelements of <titleInfo> are recommended when they apply. This element is repeatable. :)
(: Application: <titleInfo> is repeated for each type attribute value. If multiple titles are recorded, repeat <titleInfo><title> for each. The language of the title may be indicated if desired using the xml:lang (RFC3066) or lang (3-character ISO 639-2 code) attributes. :)
    (: Problem: the wrong (2-character) language codes seem to be used in samples. :)
(: Attributes: type [RECOMMENDED IF APPLICABLE], authority [RECOMMENDED IF APPLICABLE], displayLabel [OPTIONAL], xlink, ID, lang, xml:lang, script, transliteration. :)
    (: All attributes are applied to the <titleInfo> element; none are used on any subelements. :)
    (: Unaccounted for: authority, displayLabel, xlink, ID, xml:lang, script. :)
    (: @type :)
        (: For the primary title of the resource, do not use the type attribute. For all additional titles, the guidelines recommend using this attribute to indicate the type of the title being recorded. :)
        (: Values: abbreviated, translated, alternative, uniform. :)
            (: Unaccounted for: none. :)
(: Subelements: <title> [REQUIRED], <subTitle> [RECOMMENDED IF APPLICABLE], <partNumber> [RECOMMENDED IF APPLICABLE], <partName> [RECOMMENDED IF APPLICABLE], <nonSort> [RECOMMENDED IF APPLICABLE]. :)
    (: Unaccounted for: <partNumber>, <partName>, <nonSort>. :)
    (: <nonSort> :)
        (: The guidelines strongly recommend the use of this element when non-sorting characters are present, rather than including them in the text of the <title> element. :)
    (: <partName> :)
        (: Multiple <partName> elements may be nested in a single <titleInfo> to describe a single part with multiple hierarchical levels. :)

(: !!! function mods:get-title-transliteration !!! :)
(: Constructs a transliterated/transcribed title for Japanese and Chinese. :)
    (: Problem: What if other languages than Chinese and Japanese occur in a MODS record? :)
    (: Problem: What if several languages with transcription occur in one MODS record? :)

declare function mods:get-title-transliteration($entry as element(), $titleInfo as element(mods:titleInfo)?) {
    let $titleInfo :=
        if ($titleInfo/@transliteration = 'romaji') then
            string-join(($entry/mods:titleInfo[@transliteration = 'romaji']/mods:title, $entry/mods:titleInfo[@transliteration = 'romaji']/mods:subTitle), ': ')
        else if ($titleInfo/@transliteration = 'pinyin') then
            string-join(($entry/mods:titleInfo[@transliteration = 'pinyin']/mods:title, $entry/mods:titleInfo[@transliteration = 'pinyin']/mods:subTitle), ': ')
        else
            ()
    return
        if ($titleInfo) then
        (: If there is a transliterated title. :) 
            <span class="title-transliteration">{string-join(($titleInfo/mods:title/string(), $titleInfo/mods:subTitle/string()),': ')}</span> 
        else ()
};
(: Why does this choke on:
    <titleInfo transliteration="romaji">
        <title>Chūgoku ni okeru hokkekyō no sekkoku</title>
    </titleInfo>
    <titleInfo lang="ja">
        <title>中国 における法華経の石刻</title>
    </titleInfo>
but not on:
    <titleInfo lang="ja">
        <title>中国 における法華経の石刻</title>
    </titleInfo>
    <titleInfo transliteration="romaji">
        <title>Chūgoku ni okeru hokkekyō no sekkoku</title>
    </titleInfo>
??? :)
(: Wolfgang's function:
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
:)


(: If there is a Japanese or Chinese title, any English title will be a translated title. :) 
    (: Problem: a variant or parallel title in English? :)

declare function mods:get-title-translated($entry as element(mods:mods), $titleInfo as element(mods:titleInfo)?) {
    let $titleInfo :=
        if ($titleInfo/@lang = 'ja' or $titleInfo/@lang = 'zh') then
            string-join(($entry/mods:titleInfo[@lang = 'en']/mods:title, $entry/mods:titleInfo[@lang = 'en']/mods:subTitle), ' ')
        else
            ()
    return
        if ($titleInfo) then
            <span class="title-translated">{string-join(($titleInfo/mods:title/string(), $titleInfo/mods:subTitle/string()), ' ') }</span>
        else ()
};

(: This constructs a short title for the hitlist. It uses the first titleInfo title/subtitle with transliteration followed by the first titleInfo title/subtitle without transliteration. :)
    (: One could change it to prefer a title in a non-European language, if there is one. :)

(: was:
    declare function mods:get-short-title($id as xs:string?, $entry as element()) {
    let $title := ($entry/mods:titleInfo[@lang = 'ja'], $entry/mods:titleInfo[@lang = 'zh'], 
        $entry/mods:titleInfo)[1]
    return
        <span><a class="pagination-toggle">{ mods:get-title-transliteration($entry, $title), $title/mods:title/string() }</a>. </span>
};
"mods:get-title-transliteration()" did nothing.
:)

declare function mods:get-short-title($id as xs:string?, $entry as element()) {
    let $titleTransliteration := $entry/mods:titleInfo[@transliteration][1]/mods:title/string()
    let $subTitleTransliteration := $entry/mods:titleInfo[@transliteration][1]/mods:subTitle/string()
    let $title := $entry/mods:titleInfo[not(@transliteration)][1]/mods:title/string()
    let $subTitle := $entry/mods:titleInfo[not(@transliteration)][1]/mods:subTitle/string()
    
    return
    (: If there is a Chinese/Japanese title with transliteration, only the transliteration should be in italics. :)
    if ($titleTransliteration) then
        <span class="pagination-toggle"><em>
        { 
        $titleTransliteration, 
        if ($subTitleTransliteration) then
            concat(': ', $subTitleTransliteration)
        else
            ()
        }
        </em>, 
        {
        ' ', $title, 
        if ($subTitle) then
            concat(': ', $subTitle)
        else
            () 
        }
        . </span>
        (: NB: This period should not appear after periodical titles. :)
        
        else
        
        <span class="pagination-toggle"><em>
        {  
        $title, 
        if ($subTitle) then
            concat(': ', $subTitle)
        else
            () 
        }. </em></span>
        (: NB: This period should not appear after periodical titles. :)

};

(: Constructs the full title for the detail view. :)
declare function mods:title-full($titleInfo as element(mods:titleInfo)) {
    <tr>
        <td class="label">Title
        {
            if ($titleInfo/@type = 'translated') then
                concat("(translated", mods:space-before($titleInfo/@lang), ")")
                (: can also have @transliteration without @lang :)
            else if ($titleInfo/@type = 'abbreviated') then
                concat("(abbreviated", mods:space-before($titleInfo/@lang), ")")
            else if ($titleInfo/@type = 'alternative') then
                concat("(alternative", mods:space-before($titleInfo/@lang), ")")
            else if ($titleInfo/@type = 'uniform') then
                concat("(uniform", mods:space-before($titleInfo/@lang), ")")
            else if ($titleInfo/@transliteration) then
                concat("(transliteration", mods:space-before($titleInfo/@transliteration), ")")
            (: was
            concat("(transliteration", mods:space-before($titleInfo/@lang), ")")
            :)
            else if ($titleInfo/@lang) then
                concat("(", $titleInfo/@lang, ")")
            else
                ()
        }
        </td>
        <td class="record">{ string-join(($titleInfo/mods:title, $titleInfo/mods:subTitle), ': ') }</td>
    </tr>
};

(: ### <titleInfo> ends ### :)

(: ### <relatedItem> begins ### :)

(: Application: relatedItem" includes a designation of the specific type of relationship as a value of the type attribute and is a controlled list of types enumerated in the schema. <relatedItem> is a container element under which any MODS element may be used as a subelement. It is thus fully recursive. :)
(: Attributes: type, xlink:href, displayLabel, ID. :)
(: Values for @type: preceding, succeeding, original, host, constituent, series, otherVersion, otherFormat, isReferencedBy. :)
    (: Unaccounted for: preceding, succeeding, original, constituent, series, otherVersion, otherFormat, isReferencedBy. :)
(: Subelements: any MODS element. :)
(: NB! This function is constructed differently from mods:entry-full; the two should be harmonised. :)

declare function mods:get-related($entry as element(mods:mods)) {
    let $related0 := $entry/mods:relatedItem[@type = 'host']
    let $collection := util:collection-name($entry)
    let $related :=
        if (($related0/@xlink:href) and (collection($collection)//mods:mods[@ID = $related0/@xlink:href])) then
        (: was:
        if ($related0/@xlink:href) then
        :)
            collection($collection)//mods:mods[@ID = $related0/@xlink:href][1]
        else
            $related0[1]
    return
        if ($related) then
            <span class="related"> In Publication:
            { 
                mods:get-names($related), 
                mods:get-short-title((), $related),
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

declare function mods:names-full($entry as element()) {
if (($entry/mods:name)) then
let $names := $entry/mods:name
    for $name in $names
    return
    if ($name[@type = 'personal']) then
        <tr>
            <td class="label">
            {
            if ($name/mods:role/mods:roleTerm[@type = 'text']) then
                functx:capitalize-first($name/mods:role/mods:roleTerm[@type = 'text'])
            else 'Author'
            }
            </td>
            <td class="record">
            {       
        let $family := $name/mods:namePart[@type = 'family']
        let $given := $name/mods:namePart[@type = 'given']
        let $address := $name/mods:namePart[@type = 'termsOfAddress']
        let $date := $name/mods:namePart[@type = 'date']
        return
            if ($family and $given) then (: If the namePart is split up into family and given. We assume that both will be present. :)
                if ($family/@lang = ('ja', 'zh')) then
                    (mods:eastern-name-transliteration($name), 
                    ' ', 
                    concat(
                    $family[not(@transliteration)][1]/string(),
                    (: NB: check! :)                    
                    $given[not(@transliteration)][1]/string()),
                    (: NB: check! :)                    
                    if ($address) then functx:trim(concat(', ', $address)) else (),
                    if ($date) then functx:trim(concat(' (', $date, ')')) else ()
                    )
                    (: Sometimes we have names in Chinese characters, in transliteration _and_ a Western name. :)
                else
                    string-join(($family, string-join($given, ' '), $address),', ')
            else (: If the namePart is not split up in family and given. :)
                if ($name/mods:namePart/@transliteration) then (: If there is transliteration. :)
                    ($name/mods:namePart[@transliteration], ' ' , $name/mods:namePart[not(@transliteration)]) 
                else
                    string-join(($family, string-join($given, ' '), $address),', ')
        }</td>
    </tr>
    else if ($name[@type = 'corporate']) then
        <tr>
            <td class="label">
            {
            if ($name/mods:role/mods:roleTerm) then
                functx:capitalize-first($name/mods:role/mods:roleTerm)
            else 'Corporation'
            }
            </td>
            <td class="record">
            { $name/mods:namePart[@transliteration]/string(), ' ', $name/mods:namePart[not(@transliteration)]/string() }
        </td>
    </tr>
    else
    ()
else
()
};

declare function mods:simple-row($data as item()?, $label as xs:string) as element(tr)? {
    for $d in $data
    where $data != ''
    return
        <tr>
            <td class="label">{$label}</td>
            <td class="record">{string($data)}</td>
        </tr>
};

declare function mods:url($entry as element()) as element(tr)* {
    for $url in $entry/mods:location/mods:url
    return
        <tr>
            <td class="label">URL {if ($url[@displayLabel]) then
            concat('(',($url/@displayLabel/string()),')')
        else ()}</td>
            <td class="record"><a href="{$url}">{$url/string()}</a></td>
        </tr>
};
        
(: Prepares for the recursive mods:format-full. :)
declare function mods:entry-full($entry as element()) 
    {
    
    mods:names-full($entry),
    for $titleInfo in (
        $entry/mods:titleInfo[not(@type)],
        $entry/mods:titleInfo[@type = 'abbreviated'],
        $entry/mods:titleInfo[@type = 'translated'],
        $entry/mods:titleInfo[@type = 'alternative'],
        $entry/mods:titleInfo[@type = 'uniform']
    )
    return 
    mods:title-full($titleInfo),
    mods:simple-row(mods:get-conference-detail-view($entry), "Conference"),
    
    mods:simple-row(mods:get-place($entry/mods:originInfo/mods:place), "Place"),
    mods:simple-row(mods:get-publisher($entry/mods:originInfo/mods:publisher[1]), "Publisher"),
    if ($entry/mods:relatedItem/mods:originInfo/mods:dateCreated) then () else
    mods:simple-row($entry/mods:originInfo/mods:dateCreated[1], "Date created"),
    if ($entry/mods:relatedItem/mods:originInfo/mods:dateIssued) then () else
    mods:simple-row($entry/mods:originInfo[1]/mods:dateIssued[1], "Date issued"),
    (: NB! [1] should not be necessary. :)
    mods:simple-row($entry/mods:originInfo/mods:dateOther, "Other date"),
    if ($entry/mods:extent) then mods:simple-row(mods:get-extent($entry/mods:extent), "Extent") else (),
    mods:simple-row($entry/mods:typeOfResource[1]/string(), "Type of Resource"),
    
    for $genre in ($entry/mods:genre)
    let $authority := $genre/@authority/string()
    return
    mods:simple-row($genre/string(), 
    concat('Genre', 
        if ($authority) then
        concat('(', $authority, ')') else ()
        )
    ),
    
    for $abstract in ($entry/mods:abstract)
    return
    mods:simple-row($abstract, "Abstract"),
    
    for $note in ($entry/mods:note)
    return
    mods:simple-row($note, "Note"),
    
    for $subject in ($entry/mods:subject)
    let $authority := if ($subject/@authority/string()) then concat('(', ($subject/@authority/string()), ')') else ()
    return
    <tr>
    <td class="label subject">Subject {$authority}</td>
    <td class="record">
    <table class="subject">
    {
    for $item in ($subject/mods:*)
    let $authority := if ($item/@authority/string()) then concat('(', ($item/@authority/string()), ')') else ()
    let $encoding := if ($item/@encoding/string()) then concat('(', ($item/@encoding/string()), ')') else ()
    return
        <tr>
        <td class="sublabel">
            {functx:capitalize-first(functx:camel-case-to-words($item/name(), ' ')),
            $authority, $encoding}
        </td>
        <td class="subrecord">
            {
            if ($item/mods:*) then
            for $subitem in ($item/mods:*)
            let $authority := if ($subitem/@authority/string()) then concat('(', ($subitem/@authority/string()), ')') else ()
            let $encoding := if ($subitem/@encoding/string()) then concat('(', ($subitem/@encoding/string()), ')') else ()
            return
            <table>
            <tr>
            <td class="sublabel">
                {functx:capitalize-first(functx:camel-case-to-words($subitem/name(), ' ')),
            $authority, $encoding}
            </td>
            <td>
                <td class="subrecord">                
                    {$subitem/string()}
                </td>
            </td>
            </tr>
            </table>
            else
            <table>
            <tr>
            <td class="subrecord" colspan="2">
            {$item/string()}
            </td>
            <td/>
            </tr>
            </table>
            }
            </td></tr>
    }
    </table></td>
    </tr>
    , 
    
    mods:simple-row($entry/mods:identifier[@type="isbn"][1], "ISBN"),
    mods:url($entry),
    (: If there is a related "host" item, get its contents from the related record through its href if it has one; otherwise process the information as given in the record itself. :)
        (: Problem: what if relatedItem is not "host"? :)
        (: There should be a function that formats @type. :)
    let $relatedItem := $entry/mods:relatedItem[@type = 'host']
    let $collection := util:collection-name($entry)
    return
        if ($relatedItem) then (
            <tr>
                <td class="host">In Publication:</td><td class="related"><hr/></td>
            </tr>,
            if (($relatedItem/@xlink:href) and (collection($collection)//mods:mods[@ID = $relatedItem/@xlink:href])) then            
                for $ref in collection($collection)//mods:mods[@ID = $relatedItem/@xlink:href]
                return
                    (mods:entry-full($ref), mods:get-related-item-part($entry))
            else
                    (mods:entry-full($relatedItem[@type = "host"][1]), mods:get-related-item-part($entry))
        ) else
            ()
};

(: Creates view for detail view. :)
declare function mods:format-full($id as xs:string, $entry as element(mods:mods), $original as element(mods:mods)) {
    let $log := util:log("DEBUG", $entry)
    return
    <table class="biblio-full">
    {
    mods:get-collection($original),
    mods:entry-full($entry)
    }
    </table>
};
(:(<span class="pagination-toggle"><a>{$formatted}</a></span>):)
(: Creates view for hitlist. :)
declare function mods:format-short($id as xs:string, $entry as element(mods:mods)) {
(: Was originally wrapped in <p>. :)
        mods:get-names($entry),
        mods:get-short-title($id, $entry),
        if ($entry/mods:name[@type = 'conference']) then
            mods:get-conference-hitlist($entry)
        else (
            mods:get-part-and-origin($entry),
            mods:get-related($entry)
        )
        (:
        , if ($entry/mods:location/mods:url[@displayLabel]) then
            <span> (<a href="{$entry/mods:location/mods:url}">{$entry/mods:location/mods:url/@displayLabel/string()}</a>)</span>
        else ()
        :)
};