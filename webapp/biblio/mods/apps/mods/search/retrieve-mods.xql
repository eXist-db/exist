module namespace mods="http://www.loc.gov/mods/v3";

declare namespace mads="http://www.loc.gov/mads/";
declare namespace xlink="http://www.w3.org/1999/xlink";
declare namespace fo="http://www.w3.org/1999/XSL/Format";
declare namespace functx = "http://www.functx.com"; 

declare option exist:serialize "media-type=text/xml";



(: TODO: A lot of restrictions to the first item in a sequence ([1]) have been made; these must all be changed to for-structures or string-joins. :)

(: ### general functions begin ###:)

declare function local:remove-empty-attributes
(: derived from functx:remove-empty-attributes :)
($element as element()) as element() {
element { node-name($element)}
{ $element/@*[string-length(.) eq 0],
for $child in $element/node( )
return 
    if ($child instance of element())
    then local:remove-empty-attributes($child)
    else $child }
};

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
 
declare function functx:trim( $arg as xs:string? )  as xs:string {       
   replace(replace($arg,'\s+$',''),'^\s+','')
 } ;
 
(: not used :)
declare function mods:space-before($node as node()?) as xs:string? {
    if (exists($node)) then
        concat(' ', $node)
    else
        ()
};

(: ### general functions end ###:)

declare function mods:get-collection($entry as element(mods:mods)) {
    let $collection := util:collection-name($entry)
    let $collection-short := functx:replace-first($collection, "/db/", "")
    return
        <tr><td class="label">In Collection:</td><td>
        {$collection-short}
        </td></tr>            
};

declare function mods:add-part($part, $sep as xs:string) {
    if (empty($part) or string-length($part[1]) eq 0) 
    then ()
    else concat(string-join($part, " "), $sep)
};

declare function mods:get-publisher($publishers as element(mods:publisher)?) as xs:string? {
    string-join(
        for $publisher in $publishers
        let $order := $publisher[@transliteration]
        order by $order
        return
            if ($publisher/mods:name) 
            then
            (: the encoding of <publisher> with <name> and <namePart> is not standard.:)
                
                
                        $publisher/mods:name[1]/mods:namePart
            else
                
                    $publisher,
        ', '
    )
};
(:
declare function mods:get-publisher($publishers as element(mods:publisher)?) as xs:string? {
    string-join(
        for $publisher in $publishers
        let $order := $publisher[@transliteration]
        order by $order
        return
            if ($publisher/mods:name) 
            then
            (: the encoding of <publisher> with <name> and <namePart> is not standard.:)
                
                
                        $publisher/mods:name[1]/mods:namePart
            else
                
                    $publisher,
        ', '
    )
};
:)


(: ### <subject> begins ### :)

(: format subject :)
declare function mods:format-subjects($entry as element()) {
    for $subject in ($entry/mods:subject)
    let $authority := 
        if ($subject/@authority/string()) 
        then concat('(', ($subject/@authority/string()), ')') 
        else ()
    return
    <tr>
    <td class="label subject">Subject {$authority}</td>
    <td class="record"><table class="subject">
    {
    for $item in ($subject/mods:*)
    let $authority := 
        if ($item/@authority/string()) 
        then concat('(', ($item/@authority/string()), ')') 
        else ()
    let $encoding := 
        if ($item/@encoding/string()) 
        then concat('(', ($item/@encoding/string()), ')') 
        else ()
    return
        <tr><td class="sublabel">
            {replace(functx:capitalize-first(functx:capitalize-first(functx:camel-case-to-words($item/name(), ' '))),'Info',''),
            $authority, $encoding}
        </td><td class="subrecord">
            {
            if ($item/mods:*) 
            then
                if ($item/name() = 'name')
                then mods:format-untransliterated-name($item, 1) 
                else
                    if ($item/name() = 'titleInfo')
                    then mods:get-short-title('', $item/..)
                    else
                        for $subitem in ($item/mods:*)
                        let $authority := 
                            if ($subitem/@authority/string()) 
                            then concat('(', ($subitem/@authority/string()), ')') 
                            else ()
                        let $encoding := 
                            if ($subitem/@encoding/string()) 
                            then concat('(', ($subitem/@encoding/string()), ')') 
                            else ()
                        return
                        <table><tr><td class="sublabel">
                            {functx:capitalize-first(functx:camel-case-to-words($subitem/name(), ' ')),
                        $authority, $encoding}
                        </td><td><td class="subrecord">                
                            {$subitem/string()}
                        </td></td></tr></table>
            else
            <table><tr><td class="subrecord" colspan="2">
            {$item/string()}
            </td></tr></table>
            }
            </td></tr>
    }
    </table></td>
    </tr>
};

(: ### <subject> ends ### :)

(: ### <extent> begins ### :)

(: <extent> belongs to <physicalDescription>, to <part> as a top level element and to <part> under <relatedItem>. 
Under <physicalDescription>, <extent> has no subelements.:)

declare function mods:get-extent($extent as element(mods:extent)?) {
let $unit := functx:trim($extent/@unit)
let $start := functx:trim($extent/mods:start)
let $end := functx:trim($extent/mods:end)
let $total := functx:trim($extent/mods:total)
let $list := functx:trim($extent/mods:list)
return
    if (($start) or ($end)) 
    then 
        (: Chicago does not note units :)
        (:
        concat(
        if ($unit) 
        then concat($unit, ' ')
        else ()
        ,
        :)
        concat($start, '-', $end)
    else 
        if ($total) 
        then 
            (:
            concat(
            if ($unit) 
            then concat(($unit), ' ')
            else ()
            ,
            :)
            ($total)
        else
    (: <part><extent><list> and <physicalDescription><extent> give the of <extent> as string. It does not require unit. :)
    (: What if it has a @unit? :)
        $extent/string()    
};


(: ### <originInfo> begins ### :)

(: The DLF/Aquifer Implementation Guidelines for Shareable MODS Records require the use of at least one <originInfo> element with at least one date subelement in every record, one of which must be marked as a key date. <place>, <publisher>, and <edition> are recommended if applicable. These guidelines make no recommendation on the use of the elements <issuance> and <frequency>. This element is repeatable. :)
 (: Application: :)
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
        let $placeTerm := $place/mods:placeTerm
        let $order := $place/mods:placeTerm[@transliteration]
        order by $order
        return
            if ($placeTerm[@type = 'text']) 
            then string-join((
                $placeTerm[@transliteration],
                $placeTerm[not(@transliteration)]),
                ', ')
            else
                if ($placeTerm[@authority = 'marccountry']) 
                then
                    doc("/db/org/library/apps/mods/code-tables/marc-country-codes.xml")/code-table/items/item[value = $placeTerm]/label
                else 
                    if ($placeTerm[@authority = 'iso3166']) 
                    then
                        doc("/db/org/library/apps/mods/code-tables/iso3166-country-codes.xml")/code-table/items/item[value = $placeTerm]/label
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
        if (exists($part/mods:detail) and exists($part/mods:date)) 
        then concat(
            if (exists($part/mods:detail[@type='issue']) and exists($part/mods:detail[@type='volume']))
            then
                concat($part/mods:detail[@type='volume']/mods:number, ', no. ', $part/mods:detail[@type='issue']/mods:number)
                    (: concat((if ($part/mods:detail/mods:caption) then $part/mods:detail/mods:caption/string() else '/'), $part/mods:detail[@type='issue']/mods:number) :)
            else 
                if (not($part/mods:detail[@type='volume']) and exists($part/mods:detail[@type='issue']))
                then $part/mods:detail[@type='issue']
                else
                    if (exists($part/mods:detail[@type='volume']) and not($part/mods:detail[@type='issue']))
                    then $part/mods:detail[@type='volume']
            else ()
            ,
            ' (', $part/mods:date, ')'
            ,
            if ($part/mods:extent) 
            then
                concat(", ", mods:get-extent($part/mods:extent[1]),'.')
            else ()
            )
        else 
            if (exists($part/mods:date)) 
            then
                normalize-space(concat(' (', $part/mods:date, ')',
                if ($part/mods:extent) 
                then
                    concat(": ", mods:get-extent($part/mods:extent[1]),'.')
                else ()
            )
            )
            else
            (
                if ($origin/mods:place/mods:placeTerm[@transliteration][1])
                then
                    normalize-space(mods:add-part(concat($origin/mods:place/mods:placeTerm[@transliteration][1], ' ', $origin/mods:place/mods:placeTerm[not(@transliteration)][1]), ": "))
                else ()
            ,
                normalize-space(mods:add-part(mods:get-publisher($origin/mods:publisher[1]), ", ")
            )
            (:
            ,
            if ($entry/mods:relatedItem/mods:originInfo/mods:dateCreated) 
            then () 
            else
                normalize-space(mods:add-part($origin/mods:dateCreated/string(),'.'))
            :)
            , 
            if ($entry/mods:relatedItem/mods:originInfo/mods:dateIssued) 
            then () 
            else
                normalize-space(mods:add-part($origin/mods:dateIssued/string(), '.'))
                )
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
    if ($part or $volume or $issue or $date or $extent) 
    then
        (
            (:if ($volume and $issue) 
            then
                <tr>
                    <td class="label">Volume/Issue</td>
                    <td class="record">{string-join(($volume/string(), $issue/string()), '/')}</td>
                </tr>
            else:) 
            if ($volume) 
            then
                <tr>
                    <td class="label">Volume</td>
                    <td class="record">{$volume/string()}</td>
                </tr>
            else () 
            ,
            if ($issue) 
            then
                <tr>
                    <td class="label">Issue</td>
                    <td class="record">{$issue/string()}</td>
                </tr>
            else ()
            ,
            if ($date) 
            then
                <tr>
                    <td class="label">Date</td>
                    <td class="record">{$date/string()}</td>
                </tr>
            else ()
            ,
            if ($extent) 
            then
                <tr>
                    <td class="label">Extent</td>
                    <td class="record">{$extent}</td>
                </tr>
            else ()
        )
    else ()
};

(: ### <name> begins ### :)

(: The DLF/Aquifer Implementation Guidelines for Shareable MODS Records requires the use of at least one <name> element to describe the creator of the intellectual content of the resource, if available. The guidelines recommend the use of the type attribute with all <name> elements whenever possible for greater control and interoperability. In addition, they require the use of <namePart> as a subelement of <name>. This element is repeatable. :)
 (: Application:  :)
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

declare function mods:format-transliterated-eastern-name($name as element()) as xs:string? {
    if ($name/mods:namePart[@transliteration = ('pinyin', 'romaji')]) then
    let $family := string-join(($name/mods:namePart[@transliteration = ('pinyin', 'romaji')][@type = 'family']), ' ')
    (: What if several transliterations (both Japanese and Chinese) are used?  :)
    let $given := string-join(($name/mods:namePart[@transliteration = ('pinyin', 'romaji')][@type = 'given']), ' ')
    let $address := $name/mods:namePart[@transliteration = ('pinyin', 'romaji')][@type = 'termsOfAddress'][1]
    let $date := $name/mods:namePart[@transliteration = ('pinyin', 'romaji')][@type = 'date'][1]
    return
        string-join(
        (
        $family, 
        $given,
            if ($address) then $address else (),
            if ($date) then concat(' (', $date, ')') else (),
        $name/mods:namePart[@transliteration][not(@type)]
        )
         , ' ')
    else ()
};

(: NB! Dummy function!!!! :)
declare function mods:format-transliterated-non-eastern-name($name as element()) as xs:string? {
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

declare function mods:format-untransliterated-name($name as element(mods:name), $pos as xs:integer) {
    if ($name[not(@type)])
    (: if the name is (erroneously) not typed :)    
    then
        concat(
        mods:format-transliterated-eastern-name($name[mods:namePart/@transliteration][not(@type)]), 
        ' ', 
        string-join($name/mods:namePart[not(@transliteration)][not(@type)], ' ')
        )
    else
    	if ($name/@type = 'conference') 
    	then ()
    	(: get-conference-detail-view and get-conference-hitlist take care of conference. :)
        else
            if ($name/@type = 'corporate') 
            then
            concat(string-join($name/mods:namePart[@transliteration]/string(), ' '), ' ', string-join($name/mods:namePart[not(@transliteration)]/string(), ' '))
            (: Does not need manipulation, since it is never decomposed. :)
            else
            if ($name/@type = 'personal')
            then
            	let $family := $name/mods:namePart[@type = 'family'][not(@transliteration)]
            	let $given := $name/mods:namePart[@type = 'given'][not(@transliteration)]
            	let $address := $name/mods:namePart[@type = 'termsOfAddress'][not(@transliteration)]
            	let $date := string-join($name/mods:namePart[@type = 'date'][not(@transliteration)], ' ') 
            return
        	concat(
        	(:concat appends dates to name proper:)
        	string-join(
        	if ($family or $given) 
        	then
        	(: If one of the nameParts is properly typed :)
        	   if (($family/@transliteration = ('pinyin', 'romaji')) or ($given/@transliteration = ('pinyin', 'romaji'))) 
        	   then
    				(: If the name is transliterated and Eastern :)
    				(: No matter which position they have, Japanese and Chinese names are formatted the same. :)
    				(: Problem: what if Westeners have Chinese names? Can one assume that the form in original script comes first? Can one assume that transliteration comes after original script? This is actually a fault in MODS. <namePart>s that belong together should be grouped. :) 
    				(: We assume that the name in native script occurs first, that the existence of a transliterated name implies the existence of a native-script name. :)
    				(mods:format-transliterated-eastern-name($name), ' ',
        			(functx:trim(string-join($family[@lang = ('zh', 'ja')]/string(), ' ')),
        			functx:trim(string-join($given[@lang= ('zh', 'ja')]/string()
        			, ' '))))
        			(: The string-joins are meant to capture multiple family and given names. Needed?:)
                else
                    if (($family[@transliteration]) or ($given[@transliteration])) 
                    then
            		(: If the name is transliterated but not Eastern :)
            		(mods:format-transliterated-non-eastern-name($name), ' ',
            		(functx:trim(string-join($family, ' ')),
            		functx:trim(string-join($given, ' '))))
            		(: The string-joins are meant to capture multiple family and given names. :)
            		else
            		(: If the name is not transliterated :)        		 
                		if ($pos eq 1)
                		(: If it is the first name :)
                		then
                		(: If we have a non-Chinese, non-Japanese name occurring first. :)
                		(functx:trim(string-join($family/string(), ' ')), 
                		', ', 
                		functx:trim(string-join($given, ' ')),
                		    if ($address)
                		    then functx:trim(concat(', ',$address)) 
                		    else ()
            				    )
            		    else
            		    (: If we have a non-Chinese, non-Japanese name occurring elsewhere. :)
            		    (functx:trim(string-join($given, ' '))
            		    ,
            		    ' '
            		    , 
            		    functx:trim(string-join($family/string(), ' '))
            		    ,
            		      if ($address) 
            		      then functx:trim(string-join($address, ', ')) 
            		      else ()
            				    )
                else
                    if ($pos eq 1) 
                    then
                    (: If we have an untyped name occurring first. :)
                        (functx:trim(string-join($name/mods:namePart, ', ')),
                            if ($address) 
                            then functx:trim(string-join($address, ', ')) 
                            else ()
                    )
                    else
                    (: If we have an untyped name occurring later. :)
                    (functx:trim(string-join($name/mods:namePart, ' ')),
                        if ($address) 
                        then functx:trim(string-join($address, ', ')) 
                        else ()
                    )                    (: One could check for ($family or $given). :)
(:                    (functx:trim(mods:format-transliterated-eastern-name($name))):)
                    (: If there is a transliteration, but no name in original script. :)
              , ' '), 
              (: If there are any nameParts with @date, they are given last, without regard to transliteration or language. :)
              (if ($date) then concat(' (', functx:trim($date), ')') else ()))
              (: NB: Why is this part only shown in list-view? :)
        else()
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
         				    mods:format-transliterated-eastern-name($name), ' ',
         					($family/string(), $given/string())
         				)
     				else if ($pos eq 1) then
     				    ($family/string(), ', ', $given/string())
     				else
     					($given/string(), ' ', $family/string())
                 else string-join((
                     mods:format-transliterated-eastern-name($name), ' ',
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
    let $madsRef := replace($name/@xlink:href, "^#?(.*)$", "$1")
    let $mads :=
        if ($madsRef) 
        then collection("/db/biblio/authority")/mads:mads[@ID = $madsRef]
        else ()
    return
        if ($mads) 
        then mods:get-name-from-mads($mads, $pos)[1]
        else mods:format-untransliterated-name($name, $pos)[1]
};

(: retrieves author names that occur in the beginning in entries in hitlist and related items :)
declare function mods:retrieve-primary-names($entry as element()) {
    for $name at $pos in $entry/mods:name
    where $name/mods:role/mods:roleTerm = ('aut', 'author', 'Author')
    return
        mods:retrieve-name($name, $pos)
};

(: retrieves names other than author names that occur after the title in entries in hitlist and related items :)
declare function mods:retrieve-secondary-names($entry as element()) {
    for $name at $pos in $entry/mods:name
    where $name/mods:role/mods:roleTerm = ('edt', 'editor', 'Editor')
    return
        mods:retrieve-name($name, $pos)
};

(: formats author names for hitlist and related items :)
declare function mods:format-multiple-primary-names($entry as element()) {
    let $names := mods:retrieve-primary-names($entry)
    let $nameCount := count($names)
    let $formatted :=
        if ($nameCount eq 0) then
            ()
        else if ($nameCount eq 1) then
            if (ends-with($names, ".")) then
            (: Places period after single author name, if it does not end in period. :)
            (: NB: this should not be necessary:)
            concat($names, ' ')
            else
            concat($names, '. ')
        else
            concat(
                string-join(subsequence($names, 1, $nameCount - 1), ", "),
                (: Places commas after all names that do not come last. :)
                ", and ",
                (: Places comma and "and" after name that comes second-to-last. :)
                $names[$nameCount],
                ". "
                (: Places period after last name. :)
            )
    return
    normalize-space(
        $formatted
        )
};

declare function mods:format-multiple-secondary-names($entry as element()) 
    {
    let $names := mods:retrieve-secondary-names($entry)
    let $nameCount := count($names)
(:    for $role in $names/mods:role/mods:roleTerm/string()
    return    
    if (1) then
    () else
    ()
:)    let $formatted :=
        if ($nameCount eq 0) then
            ()
        else if ($nameCount eq 1) then
            concat($role, $names)
        else
            concat($role ,
                string-join(subsequence($names, 1, $nameCount - 1), ", "),
                (: Places commas after all names that do not come last. :)
                ", and ",
                (: Places comma and "and" after name that comes second-to-last. :)
                $names[$nameCount],
                ". "
                (: Places period after last name. :)
            )
    return
    normalize-space(
        ($formatted)
        )
};

(: formats author names for hitlist and related items :)
declare function mods:format-multiple-secondary-names($entry as element()) {
    let $names := mods:retrieve-primary-names($entry)
    let $nameCount := count($names)
    let $formatted :=
        if ($nameCount eq 0) then
            ()
        else if ($nameCount eq 1) then
            if (ends-with($names, ".")) then
            (: Places period after single author name, if it does not end in period. :)
            (: NB: this should not be necessary:)
            concat($names, ' ')
            else
            concat($names, '. ')
        else
            concat(
                string-join(subsequence($names, 1, $nameCount - 1), ", "),
                (: Places commas after all names that do not come last. :)
                ", and ",
                (: Places comma and "and" after name that comes second-to-last. :)
                $names[$nameCount],
                ". "
                (: Places period after last name. :)
            )
    return
    normalize-space(
        $formatted
        )
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
    (: Problem: the wrong (2-character) language codes seem to be used in Academy samples. :)
(: 3.3 Attributes: type [RECOMMENDED IF APPLICABLE], authority [RECOMMENDED IF APPLICABLE], displayLabel [OPTIONAL], xlink:simpleLink, ID, lang, xml:lang, script, transliteration. :)
    (: All 3.3 attributes are applied to the <titleInfo> element; none are used on any subelements. 
    In 3.4 all subelements have lang, xml:lang, script, transliteration. :)
    (: Unaccounted for: authority, displayLabel, xlink, ID, xml:lang, script. :)
    (: @type :)
        (: For the primary title of the resource, do not use the type attribute (NB: this does not mean that the attribute should be empty, but absent). For all additional titles, the guidelines recommend using this attribute to indicate the type of the title being recorded. :)
        (: Values: abbreviated, translated, alternative, uniform. :)
        (: NB: added value: transliterated. :)
            (: Unaccounted for: transliterated. :)
(: Subelements: <title> [REQUIRED], <subTitle> [RECOMMENDED IF APPLICABLE], <partNumber> [RECOMMENDED IF APPLICABLE], <partName> [RECOMMENDED IF APPLICABLE], <nonSort> [RECOMMENDED IF APPLICABLE]. :)
    (: Unaccounted for: <nonSort>. :)
    (: <nonSort> :)
        (: The guidelines strongly recommend the use of this element when non-sorting characters are present, rather than including them in the text of the <title> element. :)
    (: <partName> :)
        (: Multiple <partName> elements may be nested in a single <titleInfo> to describe a single part with multiple hierarchical levels. :)

(: !!! function mods:get-title-transliteration !!! :)
(: Constructs a transliterated/transcribed title for Japanese and Chinese. :)
    (: Problem: What if other languages than Chinese and Japanese occur in a MODS record? :)
    (: Problem: What if several languages with transcription occur in one MODS record? :)

(: NB: is not used!!!! :)
declare function mods:get-title-transliteration($entry as element(), $titleInfo as element(mods:titleInfo)?) {
    let $titleInfo :=
        if ($titleInfo/@transliteration = 'romaji') 
        then
            string-join(($entry/mods:titleInfo[@transliteration = 'romaji']/mods:title, $entry/mods:titleInfo[@transliteration = 'romaji']/mods:subTitle), ': ')
        else 
            if ($titleInfo/@transliteration = 'pinyin') 
            then
                string-join(($entry/mods:titleInfo[@transliteration = 'pinyin']/mods:title, $entry/mods:titleInfo[@transliteration = 'pinyin']/mods:subTitle), ': ')
            else ()
    return
        if ($titleInfo) 
        then
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
(: Constructs the full title for the hitlist view. :)
declare function mods:get-short-title($id as xs:string?, $entry as element()) {
    let $nonSortTransliteration := $entry/mods:titleInfo[@transliteration][1]/mods:nonSort/string()
    let $titleTransliteration := $entry/mods:titleInfo[@transliteration][1]/mods:title/string()
    let $subTitleTransliteration := $entry/mods:titleInfo[@transliteration][1]/mods:subTitle/string()
    let $partNumberTransliteration := $entry/mods:titleInfo[@transliteration][1]/mods:partNumber/string()
    let $partNameTransliteration := string-join(($entry/mods:titleInfo[@transliteration][1]/mods:partName/string()), ', ')
    let $nonSort := $entry/mods:titleInfo[not(@transliteration)][1]/mods:nonSort/string()
    let $title := $entry/mods:titleInfo[not(@transliteration)][1]/mods:title
    let $subTitle := $entry/mods:titleInfo[not(@transliteration)][1]/mods:subTitle/string()
    let $partNumber := $entry/mods:titleInfo[not(@transliteration)][1]/mods:partNumber/string()
    let $partName := string-join(($entry/mods:titleInfo[not(@transliteration)][1]/mods:partName[1]/string()), ':, ' )
    
    return
    normalize-space(
    (: If there is a Chinese/Japanese title with transliteration, only the transliteration should be in italics. :)
    if ($titleTransliteration) 
    then
        <span class="pagination-toggle"><em>
        { 
        concat($nonSortTransliteration, ' ' , $titleTransliteration)
        , 
        if ($subTitleTransliteration) 
        then concat(': ', $subTitleTransliteration)
        else ()
        ,
        if ($partNumberTransliteration) 
        then concat(': ', $partNumberTransliteration )
        else ()
        ,
        if ($partNameTransliteration) 
        then concat(': ', $partNameTransliteration)
        else ()         
        }
        </em>, 
        {
        ' '
        , 
        concat($nonSort, ' ' , $title)
        , 
        if ($subTitle) 
        then concat(': ', $subTitle)
        else ()
        ,
        if ($partNumber) 
        then concat(': ', $partNumber)
        else ()
        ,
        if ($partName) 
        then concat(': ', $partName)
        else ()
        ,
        if ($title/../../mods:originInfo/mods:issuance[. = 'continuing']) 
        then ()
        else '.'        
        }
        </span>
    else
        <span class="pagination-toggle"><em>
        {  
        if ($title/../../mods:relatedItem[@type = 'host']) 
        then '"'
        else ()
        ,
        $title
        ,
        if ($subTitle) 
        then concat(': ', $subTitle)
        else ()
        ,
        if ($title/../../mods:relatedItem[@type = 'host']) 
        then '"'
        else ()
        ,
        if ($title/../../mods:originInfo/mods:issuance[. = 'continuing']) 
        then ()
        else '.'
        }
        </em></span>
        )
};

(: Constructs title for the detail view. :)
declare function mods:title-full($titleInfo as element(mods:titleInfo)) {
if ($titleInfo)
    then
    <tr>
        <td class="label">
        {
            if ($titleInfo/@type = 'translated') 
            then "Translated Title"
            else 
                if ($titleInfo/@type = 'abbreviated') 
                then "Abbreviated Title"
                else 
                    if ($titleInfo/@type = 'alternative') 
                    then "Alternative Title"
                    else 
                        if ($titleInfo/@type = 'uniform') 
                        then "Uniform Title"
                        else 
                            if ($titleInfo[@transliteration]) 
                            then "Transliterated Title"
                            else "Title"
        }
        <span class="deemph">
        {
        let $lang := $titleInfo/@lang
        return
        if ($titleInfo/@lang)
        then        
        (<br/>,
        let $lang3 := doc("/db/org/library/apps/mods/code-tables/language-3-type-codes.xml")/code-table/items/item[value = $titleInfo/@lang]/label
        let $lang2 := doc("/db/org/library/apps/mods/code-tables/language-3-type-codes.xml")/code-table/items/item[valueTwo = $titleInfo/@lang]/label
        return
        if ($lang3) 
        then concat("(Language: ", $lang3)
        else 
            if ($lang2) 
            then concat("(Language: ", $lang2) 
            else $lang
        )
        else
        ()
        }
        {
        if ($titleInfo/@xml:lang)
        then
        (<br/>, concat("(Language: ", 
        doc("/db/org/library/apps/mods/code-tables/language-3-type-codes.xml")/code-table/items/item[valueTwo = $titleInfo/@xml:lang]/label
        , 
        ")")
        )
        else
        ()
        }
        {
        if ($titleInfo/@transliteration)
        then
            if (doc("/db/org/library/apps/mods/code-tables/transliteration-codes.xml")/code-table/items/item[value = $titleInfo/@transliteration]/label)
            then
            (<br/>, concat("(Transliteration: ", 
            doc("/db/org/library/apps/mods/code-tables/transliteration-codes.xml")/code-table/items/item[value = $titleInfo/@transliteration]/label
            , ")"))
            else
            (<br/>, concat("(Transliteration: ",($titleInfo/@transliteration)
        , ")"))
        else
        ()
        }
        {
        if ($titleInfo/@script)
        then
        (<br/>, concat("(Script: ", 
        doc("/db/org/library/apps/mods/code-tables/script-codes.xml")/code-table/items/item[value = $titleInfo/@script]/label
        , 
        ")")
        )
        else
        ()
        }
        </span>
        </td>
        <td class="record">
        {
        if ($titleInfo/mods:partNumber, $titleInfo/mods:partName)
        then
        concat(
        string-join((concat($titleInfo/mods:nonSort, ' ', $titleInfo/mods:title), $titleInfo/mods:subTitle), ': ')
        , ". ")
        else
        string-join((concat($titleInfo/mods:nonSort, $titleInfo/mods:title, ' '), $titleInfo/mods:subTitle), ': ')
        ,
        string-join(($titleInfo/mods:partNumber, $titleInfo/mods:partName), ': ')        
        }
        </td>
    </tr>
    else
    ()
};

(: ### <titleInfo> ends ### :)

(: ### <relatedItem> begins ### :)

(: Application: relatedItem" includes a designation of the specific type of relationship as a value of the type attribute and is a controlled list of types enumerated in the schema. <relatedItem> is a container element under which any MODS element may be used as a subelement. It is thus fully recursive. :)
(: Attributes: type, xlink:href, displayLabel, ID. :)
(: Values for @type: preceding, succeeding, original, host, constituent, series, otherVersion, otherFormat, isReferencedBy. :)
    (: Unaccounted for: preceding, succeeding, original, constituent, series, otherVersion, otherFormat, isReferencedBy. :)
(: Subelements: any MODS element. :)
(: NB! This function is constructed differently from mods:entry-full; the two should be harmonised. :)

declare function mods:get-related-item($entry as element(mods:mods)) {
    for $item at $pos in $entry/mods:relatedItem
    let $related0 := $entry/mods:relatedItem[$pos]
    let $collection := util:collection-name($entry)
    let $type := functx:capitalize-first(functx:camel-case-to-words($related0[1]/@type, ' '))
    let $related :=
        if (($related0/@xlink:href) and (collection($collection)//mods:mods[@ID = $related0/@xlink:href])) 
        then collection($collection)//mods:mods[@ID = $related0/@xlink:href][1]
        else $related0[1]
    return
        if ($related) 
        then
            <span class="related">            { 
                mods:format-multiple-primary-names($related), 
                mods:get-short-title((), $related),
                if ($related/mods:originInfo or $related/mods:part) 
                then mods:get-part-and-origin($related)
                else 
                    if ($related/mods:location/mods:url) 
                    then concat(" <", $related/mods:location/mods:url, ">")
                    else ()
            }
            </span>
        else ()
};

declare function mods:get-related-item-title($entry as element(mods:mods)) {
    for $item at $pos in $entry/mods:relatedItem
    let $related0 := $entry/mods:relatedItem[$pos]
    let $collection := util:collection-name($entry)
    let $type := functx:capitalize-first(functx:camel-case-to-words($related0[1]/@type, ' '))
    let $related :=
        if (($related0/@xlink:href) and (collection($collection)//mods:mods[@ID = $related0/@xlink:href])) 
        then collection($collection)//mods:mods[@ID = $related0/@xlink:href][1]
        else $related0[1]
    return
        if ($related) 
        then
            <tr><td class="label">
                {
                if ($related/@type ='host')
                then 'In:'
                else
                    if ($related/@type ='series')
                    then 'In Series:'
                    else
                        if ($related/@type ='preceding')
                        then 'Preceding:'
                        else
                            if ($related/@type ='succeeding')
                            then 'Succeeding:'
                            else
                                if ($related/@type ='original')
                                then 'Original:'
                                else
                                    if ($related/@type ='constituent')
                                    then 'Constituent:'
                                    else
                                        if ($related/@type ='otherVersion')
                                        then 'Other Version:'
                                        else
                                            if ($related/@type ='otherFormat')
                                            then 'Other Format:'
                                            else
                                                if ($related/@type ='isReferencedBy')
                                                then 'Is Referenced By:'
                                                else
                                                    if ($related/@type ='references')
                                                    then 'References:'
                                                    else ()
            }
            </td>
            <td class="record"><span class="related">
            { 
                mods:format-multiple-primary-names($related)
                , 
                mods:get-short-title((), $related)
                ,
                mods:format-multiple-secondary-names($related),
                if ($related/mods:originInfo or $related/mods:part) 
                then mods:get-part-and-origin($related)
                else 
                    if ($related/mods:location/mods:url) 
                    then concat(" <", $related/mods:location/mods:url, ">")
                    else ()    
            }
            </span></td>
        </tr>
        else ()
};
(:        <tr>
            <td class="label">{$label}</td>
            <td class="record">{string($data)}</td>
        </tr>
:)
(: ### <relatedItem> ends ### :)

declare function mods:names-full($entry as element()) {
if ($entry/mods:name) then
    let $names := $entry/mods:name
    for $name in $names
    return
    if ($name[not(@type) or @type = 'personal']) 
    then
        <tr><td class="label">
            {
            if ($name/mods:role/mods:roleTerm)
            then
                if ($name/mods:role/mods:roleTerm/@type)
                then
                    if ($name/mods:role/mods:roleTerm[@type = 'code']) 
                    then
                        doc("/db/org/library/apps/mods/code-tables/role-codes.xml")/code-table/items/item[value = $name/mods:role/mods:roleTerm/@type]/label
                    else 
                        functx:capitalize-first($name/mods:role/mods:roleTerm[@type = 'text'])                        
                else
                    'Author'
            else
            'Author'
            (: interpreting this as the default value for roleTerm. :)
            }
            </td><td class="record">
            {       
        let $family := $name/mods:namePart[@type = 'family']
        let $given := $name/mods:namePart[@type = 'given']
        let $address := $name/mods:namePart[@type = 'termsOfAddress']
        let $date := $name/mods:namePart[@type = 'date']
        let $untyped := $name/mods:namePart[not(@type)]
        return
            if ($family and $given) then (: If the namePart is split up into family and given. We assume that both will be present. :)
                if ($family/@lang = ('ja', 'zh')) then
                    (mods:format-transliterated-eastern-name($name), 
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
                else
                (: If the namePart is not split up in family and given. :)
                    if ($name/mods:namePart/@transliteration) then
                    (: If there is transliteration. :)
                        ($name/mods:namePart[@transliteration], ' ' , $name/mods:namePart[not(@transliteration)]) 
                    else
                    (: If there is not a transliteration. :)
                        string-join(($untyped),', ')
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
    let $entry := local:remove-empty-attributes($entry)
    return
    mods:names-full($entry),
    for $titleInfo in (
        $entry/mods:titleInfo[not(@type)],
        $entry/mods:titleInfo[@type = 'abbreviated'],
        $entry/mods:titleInfo[@type = 'translated'],
        $entry/mods:titleInfo[@type = 'alternative'],
        $entry/mods:titleInfo[@type = 'uniform']
    )
    return 
    mods:title-full($titleInfo)
    ,
    mods:simple-row(mods:get-conference-detail-view($entry), "Conference")
    ,
    mods:simple-row(mods:get-place($entry/mods:originInfo/mods:place), "Place")
    ,
    mods:simple-row(mods:get-publisher($entry/mods:originInfo/mods:publisher[1]), "Publisher")
    ,
    if ($entry/mods:relatedItem/mods:originInfo/mods:dateCreated) 
    then () 
    else mods:simple-row($entry/mods:originInfo/mods:dateCreated[1], "Date Created")
    ,
    if ($entry/mods:relatedItem/mods:originInfo/mods:dateIssued) 
    then () 
    else mods:simple-row($entry/mods:originInfo[1]/mods:dateIssued[1], "Date Issued")
    ,
    if ($entry/mods:relatedItem/mods:originInfo/mods:dateModified) 
    then () 
    else mods:simple-row($entry/mods:originInfo[1]/mods:dateModified[1], "Date Modified")
    ,
    (: NB! [1] should not be necessary. :)
    mods:simple-row($entry/mods:originInfo/mods:dateOther, "Other date"),
    if ($entry/mods:extent) 
    then mods:simple-row(mods:get-extent($entry/mods:extent), "Extent") 
    else ()
    ,
    
    (: URL :)
    mods:url($entry)
    ,
    
    (: relatedItem :)
    for $item in ($entry)
    return
    mods:get-related-item-title($item)
    ,
    
    (: typeOfResource :)
    mods:simple-row($entry/mods:typeOfResource[1]/string(), "Type of Resource"),
    
    (: internetMediaType :)
    mods:simple-row(
    (let $label := doc("/db/org/library/apps/mods/code-tables/internet-media-type-codes.xml")/code-table/items/item[value = $entry/mods:physicalDescription[1]/mods:internetMediaType]/label
    return
    if ($label) 
    then $label
    else $entry/mods:physicalDescription[1]/mods:internetMediaType)
    , 'Internet Media Type')
    ,
    
    (: language :)
    (: expand to render more than one language :)
    if ($entry/mods:language[1]/mods:languageTerm/@authority="iso639-2b")
    then
    mods:simple-row(
    doc("/db/org/library/apps/mods/code-tables/language-3-type-codes.xml")/code-table/items/item[value = $entry/mods:language[1]/mods:languageTerm]/label, "Language")
    else
    mods:simple-row(
        $entry/mods:language[1]/mods:languageTerm/string(), "Language")
    ,
    
    (: genre :)
    for $genre in ($entry/mods:genre)
    let $authority := $genre/@authority/string()
    return    
    mods:simple-row($genre/string()
    , 
    concat('Genre', 
        if ($authority)
        then
            if ($authority = 'marcgt')
            then
                concat(' (', doc("/db/org/library/apps/mods/code-tables/genre-authority-codes.xml")/code-table/items/item[value = $authority]/label, ')')
            else concat(' (', $authority, ')')
        else ()            
        ))
    ,
    
    (: abstract :)
    for $abstract in ($entry/mods:abstract)
    return
    mods:simple-row($abstract, "Abstract"),
    
    (: note :)
    for $note in ($entry/mods:note)
    return
        if (($entry/mods:note/@type="refbase") or ($entry/mods:note/@type="clusterPublication")) 
        then () 
        else mods:simple-row($note, "Note"),
    
    (: subject :)
    mods:format-subjects($entry), 
    
    (: ISBN :)
    mods:simple-row($entry/mods:identifier[@type="isbn"][1], "ISBN"),
    
    (: classification :)
    for $item in $entry/mods:classification
    let $authority := if ($item/@authority/string()) then concat('(', ($item/@authority/string()), ')') else ()
    return
    <tr>
    <td class="label subject">Classification {$authority}</td>
        <td>
            {$item}
        </td>
    </tr>        
};

(: Creates view for detail view. :)
declare function mods:format-full($id as xs:string, $entry as element(mods:mods), $original as element(mods:mods)) {
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
        mods:format-multiple-primary-names($entry),
        mods:get-short-title($id, $entry),
        (:NB: does not work as it should. :)
        (:mods:format-multiple-secondary-names($entry),:)
        if ($entry/mods:name[@type = 'conference']) 
        then
            mods:get-conference-hitlist($entry)
        else (
            mods:get-part-and-origin($entry),
            if ($entry/mods:relatedItem/@type = 'host') 
            then
                ('In ', mods:get-related-item($entry))
            else ()
        )
        (:
        , if ($entry/mods:location/mods:url[@displayLabel]) then
            <span> (<a href="{$entry/mods:location/mods:url}">{$entry/mods:location/mods:url/@displayLabel/string()}</a>)</span>
        else ()
        :)
};

