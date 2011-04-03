module namespace mods="http://www.loc.gov/mods/v3";

declare namespace mads="http://www.loc.gov/mads/";
declare namespace xlink="http://www.w3.org/1999/xlink";
declare namespace fo="http://www.w3.org/1999/XSL/Format";
declare namespace functx = "http://www.functx.com"; 

declare option exist:serialize "media-type=text/xml";

(: TODO: A lot of restrictions to the first item in a sequence ([1]) have been made; these must all be changed to for-structures or string-joins. :)
(: TODO: With the new cleanup script, the results received have no empty attributes and no empty elements. A lot of checking for these should be rolled back. :)

(: ### general functions begin ###:)
 
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

(: Function to clean up unintended punctuation. These should ideally be removed at the source. :)
declare function mods:clean-up-punctuation($input as xs:string) as xs:string? {
    replace(
    replace(
    replace(
    replace(
    replace(
    replace(
    replace(
    replace(
    replace(
    replace(
    replace(
    replace(
    replace(
    replace(
    replace(
    replace(
        $input
    , ' \.', '.')
    , '\s*,', ',')
    , ' :', ':')
    , ' ”', '”')
    , '\.\.', '.')
    , '“ ', '“')
    , '”\.', '.”')
    , '\. ,', ',') (: Fixes mistake in originInfo for periodicals. :)
    , ',\s*\.', '') (: Fixes mistake in originInfo for periodicals. :)
    , '\?\.', '?')
    , '!\.', '!')
    ,'\.” \.', '.”')
    ,' \)', ')')
    ,'\( ', '(')
    ,'\.\.', '.')
    ,'\.”,', ',”')
};

(: ### general functions end ###:)


(:~
: The <b>mods:get-language</b> function returns 
: the <b>human-readable label</b> of the language value passed to it.  
: This value can set in many mods elements and attributes. 
: languageTerm can have two types, text and code.
: Type code can use two different authorities, 
: recorded in the code tables language-2-type-codes.xml and language-3-type-codes.xml, 
: as well as the authority valueTerm noted in language-3-type-codes.xml.
: The most commonly used values are checked first, letting the function exit quickly.
: The function returns the human-readable label, based on searches in the code values and in the label.  
:
: @author Jens Østergaard Petersen
: @version 1.0
: @param $node A mods element or attribute recording a value, in textual or coded form
: @return The language label string
:)
declare function mods:get-language-term($language as node()?) as xs:string? {
        let $languageTerm :=
            let $languageTerm := doc('/db/org/library/apps/mods/code-tables/language-3-type-codes.xml')/code-table/items/item[value = $language[@type = 'code']][1]/label
            return
                if ($languageTerm)
                then $languageTerm
                else
                    let $languageTerm := doc('/db/org/library/apps/mods/code-tables/language-3-type-codes.xml')/code-table/items/item[valueTwo = $language[@type = 'code']][1]/label
                    return
                        if ($languageTerm)
                        then $languageTerm
                        else
                            let $languageTerm := doc('/db/org/library/apps/mods/code-tables/language-3-type-codes.xml')/code-table/items/item[valueTerm = $language[@type = 'code']][1]/label
                            return
                                if ($languageTerm)
                                then $languageTerm
                                else
                                    let $languageTerm := doc('/db/org/library/apps/mods/code-tables/language-3-type-codes.xml')/code-table/items/item[upper-case(label) = $language[@type = 'text']/upper-case(label)][1]/label
                                    return
                                        if ($languageTerm)
                                        then $languageTerm
                                        else
                                            let $languageTerm := doc('/db/org/library/apps/mods/code-tables/language-3-type-codes.xml')/code-table/items/item[upper-case(label) = upper-case($language[1])][1]/label
                                            return
                                                if ($languageTerm)
                                                then $languageTerm
                                                else ()
        return $languageTerm
};

declare function mods:get-script-term($language as node()?) as xs:string? {
        let $scriptTerm :=
            let $scriptTerm := doc('/db/org/library/apps/mods/code-tables/script-codes.xml')/code-table/items/item[value = $language/mods:scriptTerm[@authority]]/label
            return
                if ($scriptTerm)
                then $scriptTerm
                else
                    let $scriptTerm := doc('/db/org/library/apps/mods/code-tables/script-codes.xml')/code-table/items/item[value = $language/mods:scriptTerm]/label
                    return
                        if ($scriptTerm)
                        then $scriptTerm
                        else ()
        return $scriptTerm
};

(:~
: The <b>mods:language-of-resource</b> function returns 
: the <b>$string</b> value of the language for the resource.  
: This value is set in mods/language/languageTerm.
: The function feeds this value to the function mods:get-language.
: It is assumed that if two languageTerm's exist under one language, these are equivalent.
: It is possible to have multiple mods/language, for resources, just as it is possible to set the code value to 'mul', meaning Multiple languages.
: The value is set in the dialogue which leads to the creation of a new records.
:
: @author Jens Østergaard Petersen
: @version 1.0
: @see xqdoc/xqdoc-display;get-language
: @param $language The MODS languageTerm element, child of the top-level language element
: @return The language label string
:)
declare function mods:language-of-resource($language as element(mods:language)*) as xs:anyAtomicType? {
        let $languageTerm := $language/mods:languageTerm[1]
        return
            if ($languageTerm) 
            then
                mods:get-language-term($languageTerm)
            else ()
};

declare function mods:script-of-resource($language as element(mods:language)*) as xs:anyAtomicType? {
        let $scriptTerm := $language/mods:scriptTerm
        return
            if ($scriptTerm) 
            then
                mods:get-script-term($language)
            else ()
};


(:~
: The <b>mods:language-of-cataloging</b> function returns 
: the <b>$string</b> value of the language for cataloguing the resource.  
: This value is set in mods/recordInfo/languageOfCataloging.
: The function feeds this value to the function mods:get-language.
: It is assumed that if two languageTerm's exist under one language, these are equivalent.
: It is possible to have multiple mods/language, for resources, just as it is possible to set the code value to 'mul', meaning Multiple languages.
: The value is set in the dialogue which leads to the creation of a new records.
:
: @author Jens Østergaard Petersen
: @version 1.0
: @see xqdoc/xqdoc-display;get-language
: @param $entry The MODS languageOfCataloging element, child of the top-level recordInfo element
: @return The language label string
:)
declare function mods:language-of-cataloging($language as element(mods:languageOfCataloging)*) as xs:anyAtomicType? {
        let $languageTerm := $language/mods:languageTerm[1]
        return
            if ($languageTerm) 
            then
                mods:get-language-term($languageTerm)
            else ()
};

(:~
: The <em>mods:get-role-label-for-detail-view</em> function returns 
: the <em>human-readable value</em> of the roleTerm passed to it.
: Whereas mods:get-role-label-for-detail-view returns the author/creator roles that are placed in front of the title in detail view,
: mods:get-role-label-for-detail-view returns the secondary roles that are placed after the title in list view and in relatedItem in detail view.
: The value occurs in mods/name/role/roleTerm.
: It can have two types, text and code.
: Type code can use the marcrelator authority, recorded in the code table role-codes.xml.
: The most commonly used values are checked first, letting the function exit quickly.
: The function returns the human-readable label, based on searches in the code values and in the label values.  
:
: @author Jens Østergaard Petersen
: @version 1.0
: @param $node A mods element or attribute recording a role term value, in textual or coded form
: @return The role term label string
:)
declare function mods:get-role-label-for-detail-view($roleTerm as item()?) as item()? {        
        let $roleLabel :=
            (: Is the roleTerm a role label? :)
            let $roleLabel := doc('/db/org/library/apps/mods/code-tables/role-codes.xml')/code-table/items/item[upper-case(label) = upper-case($roleTerm)]/label
            (: Prefer the label proper, since it contains the form presented in the detail view, e.g. "Editor" instead of "edited by". :)
            return
                if ($roleLabel)
                then $roleLabel
                else
                    (: Is the roleTerm a role term @code? :)
                    let $roleLabel := doc('/db/org/library/apps/mods/code-tables/role-codes.xml')/code-table/items/item[value = $roleTerm]/label
                    return
                        if ($roleLabel)
                        then $roleLabel
                        else $roleTerm
        return  functx:capitalize-first($roleLabel)
};

declare function mods:get-roles-for-detail-view($name as element()*) as item()* {
    if ($name/mods:role/mods:roleTerm/text())
    then
        let $roles := $name/mods:role    
            for $role at $pos in $name/mods:role
            return
                distinct-values(
                    if ($pos eq 1)
                    then mods:get-role-terms-for-detail-view($role)
                    else (' and ', mods:get-role-terms-for-detail-view($role))
                )
    else
        (: Default values in the absence of $roleTerm. :)
        if ($name/@type = 'corporate')
        then 'Corporation'
        else 'Author'
};

declare function mods:get-role-terms-for-detail-view($role as element()*) as item()* {
    let $roleTerms := $role/mods:roleTerm
    for $roleTerm at $pos in distinct-values($roleTerms)
    
    return
    if ($roleTerm)
    then
        mods:get-role-label-for-detail-view($roleTerm)
        else ()

};

(:~
: The <em>mods:get-role-label-for-list-view</em> function returns 
: the <em>human-readable value</em> of the roleTerm passed to it.
: Whereas mods:get-role-label-for-detail-view returns the author/creator roles that are placed in front of the title in detail view,
: mods:get-role-label-for-detail-view returns the secondary roles that are placed after the title in list view and in relatedItem in detail view.: The value occurs in mods/name/role/roleTerm.
: It can have two types, text and code.
: Type code can use the marcrelator authority, recorded in the code table role-codes.xml.
: The most commonly used values are checked first, letting the function exit quickly.
: The function returns the human-readable label, based on searches in the code values and in the labelSecondary and label values.  
:
: @author Jens Østergaard Petersen
: @version 1.0
: @param $node A mods element or attribute recording a role term value, in textual or coded form
: @return The role term label string
:)
declare function mods:get-role-label-for-list-view($roleTerm as xs:string*) as xs:string* {
        let $roleLabel :=
            let $roleLabel := doc('/db/org/library/apps/mods/code-tables/role-codes.xml')/code-table/items/item[upper-case(label) = upper-case($roleTerm)]/labelSecondary
            (: Prefer labelSecondary, since it contains the form presented in the list view output, e.g. "edited by" instead of "editor". :)
            return
                if ($roleLabel)
                then $roleLabel
                else
                    let $roleLabel := doc('/db/org/library/apps/mods/code-tables/role-codes.xml')/code-table/items/item[value = $roleTerm]/labelSecondary
                    return
                        if ($roleLabel)
                        then $roleLabel
                        else
                            let $roleLabel := doc('/db/org/library/apps/mods/code-tables/role-codes.xml')/code-table/items/item[upper-case(label) = upper-case($roleTerm)]/label
                            (: If there is no labelSecondary, take the label. :)
                            return
                                if ($roleLabel)
                                then $roleLabel
                                else
                                    let $roleLabel := doc('/db/org/library/apps/mods/code-tables/role-codes.xml')/code-table/items/item[value = $roleTerm]/label
                                    return
                                        if ($roleLabel)
                                        then $roleLabel
                                            else $roleTerm
                                            (: Do not present default values in case of absence of $roleTerm, since primary roles are not displayed in list view. :)
        return $roleLabel
};

declare function mods:add-part($part, $sep as xs:string) {
    if (empty($part) or string-length($part[1]) eq 0) 
    then ()
    else concat(string-join($part, ' '), $sep)
};

declare function mods:get-publisher($publishers as element(mods:publisher)*) as xs:string? {
    string-join(
        for $publisher in $publishers
        let $order := 
            if ($publisher[@transliteration]) 
            then 0 
            else 1
        order by $order
        return
            if ($publisher/mods:name)
            then
                for $name at $pos in $publisher/mods:name
                return
                    mods:retrieve-primary-name($name, $pos)
            else
                $publisher
    , ', ')
};


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
    let $type := 
        if ($item/@type/string()) 
        then concat('(', ($item/@type/string()), ')') 
        else ()        
    return
        <tr><td class="sublabel">
            {
            replace(functx:capitalize-first(functx:capitalize-first(functx:camel-case-to-words($item/name(), ' '))),'Info',''),
            $authority, $encoding, $type
            }
        </td><td class="subrecord">
            {
            if ($item/mods:*) 
            then
                if ($item/name() = 'name')
                then 
                    mods:format-primary-name($item, 1)
                else
                    if ($item/name() = 'titleInfo')
                    then 
                        string-join(mods:get-short-title('', $item/.., ''), '')
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
                        let $type := 
                            if ($subitem/@type/string()) 
                            then concat('(', ($subitem/@type/string()), ')') 
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

declare function mods:get-extent($extent as element(mods:extent)?) as xs:string? {
let $unit := functx:trim($extent/@unit/string())
let $start := functx:trim($extent/mods:start/string())
let $end := functx:trim($extent/mods:end/string())
let $total := functx:trim($extent/mods:total/string())
let $list := functx:trim($extent/mods:list/string())
return
    if ($start and $end) 
    then 
        (: Chicago does not note units :)
        (:
        concat(
        if ($unit) 
        then concat($unit, ' ')
        else ()
        ,
        :)
        if ($start != $end)
        then
        concat($start, '-', $end)
        else
        $start        
    else 
        if ($start or $end) 
        then 
            if ($start)
            then $start
            else $end
        (: if not $start or $end. :)
        else
            if ($total) 
            then concat('(', $total, ')')
            else
                if ($list) 
                then $list
                else string-join($extent/string(), ' ')    
};

declare function mods:get-date($date as element(mods:date)?) as xs:string? {
    (: contains no subelements. :)
    (: has: encoding; point; qualifier. :)
    (: some dates have keyDate. :)
let $start := functx:trim($date[@point = 'start'])
let $end := functx:trim($date[@point = 'end'])
let $qualifier := functx:trim($date/@qualifier)
let $encoding := functx:trim($date/@encoding)
return
    (
    if ($start and $end) 
    then 
        if ($start != $end)
        then
        concat($start, '-', $end)
        else
        $start        
    else 
        if ($start or $end) 
        then 
            if ($start)
            then ($start, '-')
            else ('-', $end)
        (: if neither $start nor $end. :)
        else $date
    ,
    if ($qualifier) 
    then ('(', $qualifier, ')')
    else ()
    )
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
        let $order := 
            if ($placeTerm[@transliteration]) 
            then 0 
            else 1
        order by $order
        return
            if ($placeTerm[@type = 'text']) 
            then string-join((
                $placeTerm[@transliteration],
                $placeTerm[not(@transliteration)]),
                ' ')
            else
                if ($placeTerm[@authority = 'marccountry']) 
                then
                    doc('/db/org/library/apps/mods/code-tables/marc-country-codes.xml')/code-table/items/item[value = $placeTerm]/label
                else 
                    if ($placeTerm[@authority = 'iso3166']) 
                    then
                        doc('/db/org/library/apps/mods/code-tables/iso3166-country-codes.xml')/code-table/items/item[value = $placeTerm]/label
                    else
                        $place/mods:placeTerm[not(@type)]/string(),
        ' '
    )
};

(: <part> is found both as a top level element and under <relatedItem>. :)

declare function mods:get-part-and-origin($entry as element()) {
    let $originInfo := $entry/mods:originInfo
    (: contains: place, publisher, dateIssued, dateCreated, dateCaptured, dateValid, 
       dateModified, copyrightDate, dateOther, edition, issuance, frequency. :)
    (: has: lang; xml:lang; script; transliteration. :)
    let $place := $originInfo/mods:place
    (: contains: placeTerm. :)
    (: has no attributes. :)
    let $publisher := $originInfo/mods:publisher
    (: contains no subelements. :)
    (: has no attributes. :)
    let $dateIssued := $originInfo/mods:dateIssued
    (: contains no subelements. :)
    (: has: encoding; point; keyDate; qualifier. :)    
    
    let $part := $entry/mods:part
    (: contains: detail, extent, date, text. :)
    (: has: type, order, ID. :)
    let $detail := $part/mods:detail
    (: contains: number, caption, title. :)
    (: has: type, level. :)
        let $issue := $detail[@type=('issue', 'number')]/mods:number
        let $volume := $detail[@type='volume']/mods:number
        let $page := $detail[@type='page']/mods:number
        (: $page resembles list. :)
    let $extent := $part/mods:extent
    (: contains: start, end, title, list. :)
    (: has: unit. :)
    let $date := $part/mods:date
    (: contains no subelements. :)
    (: has: encoding; point; qualifier. :)
    return
        (: If there is a part with issue information and a date, i.e. if the publication is an article in a periodical. :)
        if ($detail/mods:number/text() and $date/text()) 
        then 
            concat(
            string-join(
            if ($issue and $volume)
            then
                concat($volume, ', no. ', $issue)
                (: concat((if ($part/mods:detail/mods:caption) then $part/mods:detail/mods:caption/string() else '/'), $part/mods:detail[@type='issue']/mods:number) :)
            else 
                if (not($volume) and ($issue))
                then (', ', $issue)
                else
                    if ($volume and not($issue))
                    then $volume
                    else ()
            , ' ')
            ,
            if ($page) 
            then
                concat(', ', $page)
            else ()
            ,
            if ($date/text())
            then
                concat(' (', mods:get-date($date), ')')
            else ()
            ,
            if ($extent) 
            then
                concat(', ', mods:get-extent($extent[1]), '.')
            else '.'
            )
        else
            (: If there is a dateIssued and a place or a publisher, i.e. if the publication is an an anthology. :)
            if ($dateIssued and ($place | $publisher)) 
            then
                (
                if ($volume) 
                then
                    concat(', Vol. ', $volume)
                else ()
                ,
                if ($extent)
                then
                    concat(', ', mods:get-extent($extent),'.')
                else ()
                ,
                if ($place)
                then
                    concat('. ', mods:get-place($place))
                else ()
                ,
                if ($publisher)
                then
                    (': ', mods:get-publisher($publisher))
                else ()
                ,
                if ($dateIssued)
                then
                concat(', ', $dateIssued[1], '.')
                else ()
                )
            (: If not a periodical and not an anthology, we don't know what it is and just try to extract the information. :)
            else
                (
                if ($place)
                then
                    mods:get-place($place)
                else ()
                ,
                normalize-space(mods:add-part(mods:get-publisher($publisher), ', ')
                )
                , 
                normalize-space(mods:add-part($dateIssued/string(), '.'))
                ,
                if ($extent)
                then
                    mods:get-extent($extent[1])            
                else ()
                )
};

(: ### <originInfo> ends ### :)

(: ### <relatedItem><part> begins ### :)

(: Application: 'part' is used to provide detailed coding for physical parts of a resource. It may be used as a top level element to designate physical parts or under relatedItem. It may be used under relatedItem for generating citations about the location of a part within a host/parent item. When used with relatedItem type="host", <part> is roughly equivalent to MARC 21 field 773, subfields $g (Relationship information) and $q (Enumeration and first page), but allows for additional parsing of data. There is no MARC 21 equivalent to <part> at the <mods> level. :)
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

(: not used. :)
declare function mods:get-related-item-part($entry as element()) {

    let $part := $entry/mods:relatedItem[@type='host'][1]/mods:part
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

declare function mods:format-transliterated-eastern-name($name as element()?) as xs:string? {
    if ($name/mods:namePart[@transliteration = ('pinyin', 'romaji')]) then
    let $family := string-join(($name/mods:namePart[@transliteration = ('pinyin', 'romaji')][@type = 'family']), ' ')
    (: What if several transliterations (both Japanese and Chinese) are used?  :)
    let $given := string-join(($name/mods:namePart[@transliteration = ('pinyin', 'romaji')][@type = 'given']), ' ')
    let $address := $name/mods:namePart[@transliteration = ('pinyin', 'romaji')][@type = 'termsOfAddress'][1]
    let $date := $name/mods:namePart[@transliteration = ('pinyin', 'romaji')][@type = 'date'][1]
    let $language := 
        if ($name/@lang)
        then
            mods:get-language-term($name/@lang)
        else
            mods:language-of-resource($name/../mods:language)
    let $languageType := doc('/db/org/library/apps/mods/code-tables/language-3-type-codes.xml')/code-table/items/item[value = $language]/nameOrder
    return
        string-join(
        (
        $family, 
        $given,
            if ($address) then $address else (),
            if ($date) then concat(' (', $date, ')') else (),
        $name/mods:namePart[@transliteration][not(@type) or @type = '']
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
            $name/mods:namePart[@transliteration][not(@type) or @type = '']
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
        concat('Paper presented at ', 
            mods:add-part($conference/string(), ', '),
            mods:add-part($entry/mods:originInfo/mods:place/mods:placeTerm, ', '),
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
        concat('Paper presented at ', $conference/string()
            (: , mods:add-part($entry/mods:originInfo/mods:place/mods:placeTerm, ', '), $date:)
            (: no need to duplicate placeinfo in detail view. :)
        )
    else
    ()
};

declare function mods:format-primary-name($name as element(mods:name), $pos as xs:integer) {
    (: If the name is (erroneously) not typed, then format the Eastern name and string-join the nameParts. :)
    let $language := 
        if ($name/@lang)
        then
            mods:get-language-term($name/@lang)
        else
            mods:language-of-resource($name/../mods:language)
    let $languageType := doc('/db/org/library/apps/mods/code-tables/language-3-type-codes.xml')/code-table/items/item[value = $language]/nameOrder
    return
    if ($name[not(@type) or @type = ''])
    then
        concat(
        mods:format-transliterated-eastern-name($name/mods:namePart[@transliteration][not(@type)]), 
        ' ', 
        string-join($name/mods:namePart[not(@transliteration)][not(@type) or @type = ''], ' ')
        )
    (: If the name is typed :)
    else
    	if ($name/@type = 'conference') 
    	then ()
    	(: get-conference-detail-view and get-conference-hitlist take care of @type = 'conference'. :)
        else
            if ($name/@type = 'corporate') 
            then
            concat(string-join($name/mods:namePart[@transliteration]/text(), ' '), ' ', string-join($name/mods:namePart[not(@transliteration)]/text(), ' '))
            (: The assumption is that any sequence of several namePart is meaningfully constructed, e.g. with more general term first. :)
            else
                if ($name/@type = 'personal')
                then
                	let $family := $name/mods:namePart[@type = 'family'][not(@transliteration/string())]
                	let $given := $name/mods:namePart[@type = 'given'][not(@transliteration/string())]
                	let $address := $name/mods:namePart[@type = 'termsOfAddress'][not(@transliteration/string())]
                	let $date := string-join($name/mods:namePart[@type = 'date'][not(@transliteration/string())], ' ') 
                    return
                	concat(
                	(: Concat appends dates to the name proper at the end. :)
                	string-join(
                	if ($family or $given) 
                	(: If one of the nameParts is properly typed :)
                	then
                	   if ($languageType = 'family-given') 
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
                    		      then 
                    		          functx:trim(string-join($address, ', ')) 
                    		      else ()
                    	)
                        else
                            if ($pos eq 1) 
                            then
                            (: If we have an untyped name occurring first. :)
                                (functx:trim(string-join($name/mods:namePart, ', ')),
                                    if ($address) 
                                    then 
                                        functx:trim(string-join($address, ', ')) 
                                    else ()
                            )
                    else
                    (: If we have an untyped name occurring later. :)
                    (functx:trim(string-join($name/mods:namePart, ' ')),
                        if ($address) 
                        then 
                            functx:trim(string-join($address, ', ')) 
                        else ()
                            )
                            (: One could check for ($family or $given). :)
                            (:(functx:trim(mods:format-transliterated-eastern-name($name))):)
                            (: If there is a transliteration, but no name in original script. :)
                      , ' '), 
                      (: If there are any nameParts with @date, they are given last, without regard to transliteration or language. :)
                      (if ($date) then concat(' (', functx:trim($date), ')') else ()))
                      (: NB: Why is this part only shown in list-view? :)
                else ()
        };

declare function mods:format-untransliterated-secondary-name($name as element(mods:name), $pos as xs:integer) {
    if ($name[not(@type) or @type = ''])
    (: if the name is (erroneously) not typed :)    
    then
        concat(
        mods:format-transliterated-eastern-name($name/mods:namePart[@transliteration][(not(@type)) or @type = ''][1]), 
        ' ', 
        string-join($name/mods:namePart[not(@transliteration)][not(@type) or @type = ''], ' ')
        )
    else
    	if ($name/@type = 'conference') 
    	then ()
    	(: Do nothing, because get-conference-detail-view and get-conference-hitlist take care of conference. :)
        else
            if ($name/@type = 'corporate') 
            then
            concat(
                string-join(
                    $name/mods:namePart[@transliteration/text()]/string()
                    , 
                    ' '
                )
                , 
                ', '
                , 
                string-join(
                    $name/mods:namePart[not(@transliteration/text())]/string()
                    , 
                    ' '
                )
            )
            (: The assumption is that any sequence of several namePart is meaningfully constructed, e.g. with more general term first. :)
            else
                (: The last option, personal. :)
                if ($name/@type = 'personal')
                then
                	let $family := $name/mods:namePart[@type = 'family'][not(@transliteration)]
                	let $given := $name/mods:namePart[@type = 'given'][not(@transliteration)]
                	let $address := $name/mods:namePart[@type = 'termsOfAddress'][not(@transliteration)]
                	let $date := string-join($name/mods:namePart[@type = 'date'][not(@transliteration)], ' ') 
                    return
                    	concat(
                    	(:concat appends dates to the name proper:)
                    	string-join(
                    	if ($family or $given) 
                    	(: If one of the nameParts is properly typed :)
                    	then
                    	   (: NB! Rewrite! :)
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
                                (: If the name is transliterated but not Eastern :)
                                then
                        		(mods:format-transliterated-non-eastern-name($name), ' ',
                        		(functx:trim(string-join($family, ' ')),
                        		functx:trim(string-join($given, ' '))))
                        		(: The string-joins are meant to capture multiple family and given names. :)
                        		else
                        		(: If the name is not transliterated :)        		 
                            		if ($pos eq 1)
                            		(: If it is the first name :)
                            		then
                            		(: If the name occurs first. :)
                            		(
                            		functx:trim(string-join($given, ' ')), 
                            		' ', 
                            		functx:trim(string-join($family/string(), ' '))
                            		,
                                    (:NB! The only difference between primary and secondary is the order of family and given names. :)
                                		if ($address)
                                		then functx:trim(concat(', ',$address)) 
                                		else ()
                        				    )
                        		    else
                        		    (: If the name does not occur first. :)
                            		    (functx:trim(string-join($given, ' '))
                            		    ,
                            		    ' '
                            		    , 
                            		    functx:trim(string-join($family, ' '))
                            		    ,
                            		    if ($address) 
                            		    then functx:trim(string-join($address, ', ')) 
                            		    else ()
                            		    )
                else
                (: if ($name/@type = 'personal') ?!? :)
                        (functx:trim(string-join($name/mods:namePart, ' ')),
                        if ($address) 
                        then functx:trim(string-join($address, ', ')) 
                        else ()
                        )                    
                        (: One could check for ($family or $given). :)
    (:                  (functx:trim(mods:format-transliterated-eastern-name($name))):)
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
     			if ($family and $given) 
     			then
     				if ($family/@lang = ('ja', 'zh')) 
     				then 
     				    (
         				    mods:format-transliterated-eastern-name($name), ' ',
         					($family/string(), $given/string())
         				)
     				else 
         				if ($pos eq 1) 
         				then
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
    (: if @lang is passed to the function, a variant name is retrieved. :)
    return string-join(
    (
        if ($lang = ('ja', 'zh')) then
            mods:get-authority($mads/mads:variant[@transliteration][1]/mads:name, $lang, $pos)
        else
            ()
        ,
        mods:get-authority($auth/mads:name, $lang, $pos)
    )
    , 
    ' ')
};

(: Wolfgang's script:
(: NB: used in search.xql :)
declare function mods:retrieve-primary-name($name as element(mods:name), $pos as xs:int) {
    let $madsRef := replace($name/@xlink:href, '^#?(.*)$', '$1')
    let $mads :=
        if ($madsRef) 
        then collection('/db/biblio/authority')/mads:mads[@ID = $madsRef]
        else ()
    return
        if ($mads) 
        then mods:get-name-from-mads($mads, $pos)
        else mods:format-primary-name($name, $pos)
};
:)

(: NB: used in search.xql :)
declare function mods:retrieve-primary-name($name as element(mods:name), $pos as xs:int) {
    let $madsRef := replace($name/@xlink:href, '^#?(.*)$', '$1')
    let $mads :=
        if ($madsRef) 
        then collection('/db/org/library/apps/mods/mads')/mads:mads[@ID = $madsRef]
        else ()
    return
        if ($mads) 
        then mods:get-name-from-mads($mads, $pos)
        else mods:format-primary-name($name, $pos)
};

declare function mods:retrieve-secondary-name($name as element(mods:name), $pos as xs:int) {
    let $madsRef := replace($name/@xlink:href, '^#?(.*)$', '$1')
    let $mads :=
        if ($madsRef) 
        then collection('/db/biblio/authority')/mads:mads[@ID = $madsRef]
        else ()
    return
        if ($mads) 
        then mods:get-name-from-mads($mads, $pos)
        else
            (        
            mods:format-untransliterated-secondary-name($name, $pos)
            )
};

(: retrieves author names that occur in the beginning of entries in hitlist and related items :)
(: Called from mods:format-multiple-primary-names() :)
declare function mods:retrieve-primary-names($entry as element()) {
    for $name at $pos in $entry/mods:name
    return
        mods:retrieve-primary-name($name, $pos)
};

declare function mods:retrieve-secondary-names($names as element()*) {
    for $name at $pos in $names
    return
        mods:retrieve-secondary-name($name, $pos)
};

(: formats author names for hitlist and related items :)
declare function mods:format-multiple-primary-names($entry as element()) {
    let $names := mods:retrieve-primary-names($entry)
    let $nameCount := count($names)
    let $formatted :=
        if ($nameCount eq 0) then
            ()
        else 
            if ($nameCount eq 1) 
            then
                if (ends-with($names, '.')) 
                then
                (: Places period after single author name, if it does not end in period, such as "Jr.". :)
                concat($names, ' ')
                else
                concat($names, '. ')
            else
                if ($nameCount eq 2)
                then
                concat(
                    subsequence($names, 1, $nameCount - 1),
                    ' and ',
                    (: Places "and" before last name. :)
                    $names[$nameCount],
                    '. '
                    (: Places period after last name. :)
                )
                else 
                    concat(
                        string-join(subsequence($names, 1, $nameCount - 1), ', '),
                        (: Places ", " after all names that do not come last. :)
                        ', and ',
                        (: Places ", and" before name that comes last. :)
                        $names[$nameCount],
                        '. '
                        (: Places period after last name. :)
                        )
    return
    normalize-space(
        $formatted
        )
};

(: Formats secondary names for hitlist and related items :)
(: NB! Difference from mods:format-multiple-primary-names(): no trailing period. Cound be modularised. :)
declare function mods:format-multiple-secondary-names($names as element()*, $caller as xs:string) {
    let $names := mods:retrieve-secondary-names($names[mods:namePart/text()])
    let $nameCount := count($names)
    let $formatted :=
            if ($nameCount eq 0) then
            ()
        else 
            if ($nameCount eq 1) 
            then
                $names
            else
                if ($nameCount eq 2)
                then
                concat(
                    subsequence($names, 1, $nameCount - 1),
                    ' and ',
                    (: Places "and" before last name. :)
                    $names[$nameCount])
                else 
                    concat(
                        string-join(subsequence($names, 1, $nameCount - 1), ', '),
                        (: Places ", " after all names that do not come last. :)
                        ', and ',
                        (: Places ", and" before name that comes last. :)
                        $names[$nameCount])
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

(: Constructs the title for the hitlist view. :)
declare function mods:get-short-title($id as xs:string?, $entry as element(), $type as xs:string?) {
    
    let $relatedItemType := $type
    
    let $titleInfo := $entry/mods:titleInfo
    let $titleInfoTransliteration := $titleInfo[@type='translated' and @transliteration]
    let $titleInfoTranslation := $titleInfo[@type='translated' and not(@transliteration)]
    
    (: NB: Should short title contain the following? :)
    (:
    let $titleInfoUniform := $titleInfo[@type='uniform']
    let $titleInfoAbbreviated := $titleInfo[@type='abbreviated']
    let $titleInfoAlternative := $titleInfo[@type='alternative']
    :)
    
    (: Repeated assignment of $titleInfo. :)
    let $titleInfo := $titleInfo[not(@type='abbreviated')][not(@type='uniform')][not(@type='alternative')][not(@type='translated')]
    
    (: NB: The string-joins below are not necessary. :)
    let $nonSort := string-join($titleInfo/mods:nonSort, ' ')
    let $title := string-join($titleInfo/mods:title, ' ')
    let $subTitle := string-join($titleInfo/mods:subTitle, ' ')
    let $partNumber := string-join($titleInfo/mods:partNumber, ' ')
    let $partName := string-join($titleInfo/mods:partName, ' ')
    
    let $nonSortTransliteration := string-join($titleInfoTransliteration/mods:nonSort, ' ')
    let $titleTransliteration := string-join($titleInfoTransliteration/mods:title, ' ')
    let $subTitleTransliteration := string-join($titleInfoTransliteration/mods:subTitle, ' ')
    let $partNumberTransliteration := string-join($titleInfoTransliteration/mods:partNumber, ' ')
    let $partNameTransliteration := string-join($titleInfoTransliteration/mods:partName, ' ')
    
    let $nonSortTranslation := string-join($titleInfoTranslation/mods:nonSort, ' ')
    let $titleTranslation := string-join($titleInfoTranslation/mods:title, ' ')
    let $subTitleTranslation := string-join($titleInfoTranslation/mods:subTitle, ' ')
    let $partNumberTranslation := string-join($titleInfoTranslation/mods:partNumber, ' ')
    let $partNameTranslation := string-join($titleInfoTranslation/mods:partName, ' ')
        
    let $titleFormat := 
        (
        if ($nonSort) 
        then concat($nonSort, ' ' , $title)
        else $title
        , 
        if ($subTitle) 
        then concat(': ', $subTitle)
        else ()
        ,
        if ($partNumber or $partName)
        then
            if ($partNumber and $partName) 
            then concat('. ', $partNumber, ': ', $partName)
            else
                if ($partNumber)
                then concat('. ', $partNumber)
                else
                    if ($partName)
                    then concat('. ', $partName)
            else ()
        else ()
        )
    let $titleTransliterationFormat := 
        (
        if ($nonSortTransliteration) 
        then 
            concat($nonSortTransliteration, ' ' , $titleTransliteration)
        else 
            $titleTransliteration
        , 
        if ($subTitleTransliteration) 
        then 
            concat(': ', $subTitleTransliteration)
        else ()
        ,
        if ($partNumberTransliteration or $partNameTransliteration)
        then
            if ($partNumberTransliteration and $partNameTransliteration) 
            then concat('. ', $partNumberTransliteration, ': ', $partNameTransliteration)
            else
                if ($partNumberTransliteration)
                then concat('. ', $partNumberTransliteration)
                else
                    if ($partNameTransliteration)
                    then concat('. ', $partNameTransliteration)
            else ()
        else ()
        )
    let $titleTranslationFormat := 
        (
        if ($nonSortTranslation) 
        then concat($nonSortTranslation, ' ' , $titleTranslation)
        else $titleTranslation
        , 
        if ($subTitleTranslation) 
        then concat(': ', $subTitleTranslation)
        else ()
        ,
        if ($partNumberTranslation or $partNameTranslation)
        then
            if ($partNumberTranslation and $partNameTranslation) 
            then concat('. ', $partNumberTranslation, ': ', $partNameTranslation)
            else
                if ($partNumberTranslation)
                then concat('. ', $partNumberTranslation)
                else
                    if ($partNameTranslation)
                    then concat('. ', $partNameTranslation)
            else ()
        else ()
        )
    return
        ( 
        if ($relatedItemType)
        (: If it is a related item it has a type, and related items are not enclosed by quotation marks. :)
        then ()
        else '“'
        ,
        (
        if ($titleTransliteration) 
        then
        (
        $titleTransliterationFormat         
        ,
        ' '
        )
        else ()
        , 
        $titleFormat
        ,
        if ($relatedItemType) 
        then ()
        else '.”'
        ,
        if ($titleTranslation)
        then ('(', $titleTranslationFormat,')')
        else ()
        (:
        ,
        (: If the publication is a periodical (continuing) or if it is an anthology, it should have no period attached. :)
        if (local-name($titleInfo/..) = 'relatedItem') 
        then ()
        else '.'
        :)        
        )
        )
};

(: Constructs title for the detail view. :)
declare function mods:title-full($titleInfo as element(mods:titleInfo)) {
if ($titleInfo)
    then
    <tr>
        <td class="label">
        {
            if (($titleInfo/@type = 'translated') and not($titleInfo/@transliteration)) 
            then 'Translated Title'
            else 
                if ($titleInfo/@type = 'abbreviated') 
                then 'Abbreviated Title'
                else 
                    if ($titleInfo/@type = 'alternative') 
                    then 'Alternative Title'
                    else 
                        if ($titleInfo/@type = 'uniform') 
                        then 'Uniform Title'
                        else 
                            if ($titleInfo[@transliteration]) 
                            then 'Transliterated Title'
                            else 'Title'
        }
        <span class="deemph">
        {
        let $lang := $titleInfo/@lang/string()
        let $xml-lang := $titleInfo/@xml:lang/string()
        return
            if ($lang or $xml-lang)
            then        
            (
            <br/>, 'Language: '
            ,
            let $lang3 := doc('/db/org/library/apps/mods/code-tables/language-3-type-codes.xml')/code-table/items/item[value = $lang]/label
            return
                if ($lang3)
                then $lang3
                else
                    let $lang2 := doc('/db/org/library/apps/mods/code-tables/language-3-type-codes.xml')/code-table/items/item[valueTwo = $lang]/label
                    return
                        if ($lang2) 
                        then $lang2
                        else
                            let $lang3 := doc('/db/org/library/apps/mods/code-tables/language-3-type-codes.xml')/code-table/items/item[valueTwo = $titleInfo/@xml:lang]/label
                            return
                                if ($lang3)
                                then $lang3
                                else
                                    if ($lang)
                                    then $lang
                                    else
                                        if ($xml-lang)
                                        then $xml-lang
                                        else ()
            ) 
            else ()
        }
        {
        let $transliteration := $titleInfo/@transliteration/string()
        return
        if ($transliteration)
        then
            (<br/>, 'Transliteration: ',
            let $transliteration-label := doc('/db/org/library/apps/mods/code-tables/transliteration-codes.xml')/code-table/items/item[value = $transliteration]/label
            return
                if ($transliteration-label)
                then $transliteration-label
                else $transliteration
            )
        else
        ()
        }
        {
        if ($titleInfo/@script/string())
        then
            (<br/>, 'Script: ', 
            doc('/db/org/library/apps/mods/code-tables/script-codes.xml')/code-table/items/item[value = $titleInfo/@script]/label
            )
        else
        ()
        }
        </span>
        </td>
        <td class='record'>
        {
        if ($titleInfo/mods:partNumber | $titleInfo/mods:partName)
        then
        concat(concat(concat($titleInfo/mods:nonSort, ' ', $titleInfo/mods:title), (if ($titleInfo/mods:subTitle) then ': ' else ()), string-join($titleInfo/mods:subTitle, '; ')), '. ', string-join(($titleInfo/mods:partNumber, $titleInfo/mods:partName), ': '))
        else
        concat(concat($titleInfo/mods:nonSort, ' ', $titleInfo/mods:title), (if ($titleInfo/mods:subTitle) then ': ' else ()), string-join($titleInfo/mods:subTitle, '; '))
                
        }
        </td>
    </tr>
    else
    ()
};

(: ### <titleInfo> ends ### :)

(: ### <relatedItem> begins ### :)

(: Application: relatedItem includes a designation of the specific type of relationship as a value of the type attribute and is a controlled list of types enumerated in the schema. <relatedItem> is a container element under which any MODS element may be used as a subelement. It is thus fully recursive. :)
(: Attributes: type, xlink:href, displayLabel, ID. :)
(: Values for @type: preceding, succeeding, original, host, constituent, series, otherVersion, otherFormat, isReferencedBy. :)
    (: Unaccounted for: preceding, succeeding, original, constituent, series, otherVersion, otherFormat, isReferencedBy. :)
(: Subelements: any MODS element. :)
(: NB! This function is constructed differently from mods:entry-full; the two should be harmonised. :)

declare function mods:get-related-items($entry as element(mods:mods), $caller as xs:string) {
    for $item at $pos in $entry/mods:relatedItem
    let $relatedItemPos := $item[$pos]
    let $collection := util:collection-name($entry)
    let $type := functx:capitalize-first(functx:camel-case-to-words($relatedItemPos/@type, ' '))
    let $relatedItem :=
        if (($relatedItemPos/@xlink:href) and (collection($collection)//mods:mods[@ID = $relatedItemPos/@xlink:href])) 
        then collection($collection)//mods:mods[@ID = $relatedItemPos/@xlink:href][1]
        else $relatedItemPos
    return
        if ($relatedItem/@type = ('host', 'series') and $relatedItem/mods:titleInfo/mods:title/text())
        then
            if ($caller = 'hitlist')
            then
                concat
                (
                    if ($relatedItem/mods:originInfo/mods:issuance != 'continuing')
                    then 'In '
                    else ''
                , 
                mods:clean-up-punctuation(mods:format-related-item($relatedItem))
                )
            else
                if ($caller = 'detail')
                then
                    mods:simple-row(
                        mods:clean-up-punctuation(mods:format-related-item($relatedItem))
                    , 'In:')
                else ()
        else ()
};

declare function mods:format-related-item($relatedItem as element()) {
                <span class="related">
                {
                if ($relatedItem/mods:name/mods:role/mods:roleTerm = ('aut', 'author', 'Author', '') or not($relatedItem/mods:name/mods:role/mods:roleTerm))
                then
                    if ($relatedItem/mods:name/mods:role/mods:namePart/text())
                    then
                        mods:format-multiple-primary-names($relatedItem)
                    else ()
                else ()
                }
                <span class="title">
                {
                mods:get-short-title('', $relatedItem, $relatedItem/@type)
                }
                </span>,
                {
        let $roleTerms := $relatedItem/mods:name/mods:role/mods:roleTerm
        return
            for $roleTerm in distinct-values($roleTerms)
                where $roleTerm = ('com', 'compiler', 'editor', 'edt', 'trl', 'translator', 'annotator', 'ann')        
                    return
                        let $names := $relatedItem/mods:name[mods:role/mods:roleTerm = $roleTerm]
                            return
                                if ($names/mods:namePart/text())
                                then
                                    (
                                    ', '
                                    ,
                                    mods:get-role-label-for-list-view($roleTerm)
                                    ,
                                    mods:format-multiple-secondary-names($names, 'secondary')
                                    )
                                else ()
                                }
                                {
                                if ($relatedItem/mods:originInfo/mods:issuance = 'monographic')
                                then ()
                                else '.'
                                }
                                {
                                if ($relatedItem/mods:originInfo or $relatedItem/mods:part) 
                                then
                                    (
                                    ' ',                
                                    mods:get-part-and-origin($relatedItem)
                                    ,                
                                    if ($relatedItem/mods:location/mods:url/text()) 
                                    then concat(' <', $relatedItem/mods:location/mods:url, '>')
                                    else ()
                                    )
                                else ()                
                                }
            </span>
};

(: ### <relatedItem> ends ### :)

declare function mods:names-full($entry as element()) {
    if ($entry/mods:name)
    then
        let $names := $entry/mods:name
        for $name in $names
        return
            if ($name[not(@type) or @type = 'personal']) 
            then
                <tr><td class="label">
                    {
                    mods:get-roles-for-detail-view($name)
                    }
                </td><td class="record">
                    {       
                    let $namePart := $name/mods:namePart
                    let $family := $namePart[@type = 'family']
                    let $given := $namePart[@type = 'given']
                    let $address := $namePart[@type = 'termsOfAddress']
                    let $date := $namePart[@type = 'date']
                    let $untyped := $namePart[not(@type)]
                    let $type := $name/@type
                    let $language := 
                        if ($name/@lang)
                        then
                            mods:get-language-term($name/@lang)
                        else
                            mods:language-of-resource($entry/mods:language)
                    let $nameOrder := doc('/db/org/library/apps/mods/code-tables/language-3-type-codes.xml')/code-table/items/item[value = $language]/nameOrder
                    order by $type
                    return
                        if ($family and $given) then (: If the namePart is split up into family and given. We assume that both will be present. :)
                            if ($nameOrder = 'family-given') then
                                (
                                    mods:format-transliterated-eastern-name($name)
                                    , 
                                    ' '
                                    ,
                                    if ($language = 'hun')
                                    then                        
                                    concat(
                                        string-join($family[not(@transliteration)], ' '),                    
                                        string-join($given[not(@transliteration)], ' ')
                                        (: space between Hungarian names. :)
                                    )
                                    else
                                    concat(
                                        string-join($family[not(@transliteration)], ''),                    
                                        string-join($given[not(@transliteration)], '')
                                        (: no space between Chinese and Japanese names. :)
                                    )
                                    ,
                                    if ($address) 
                                    then functx:trim(concat(', ', $address)) 
                                    else ()
                                    ,
                                    if ($date) 
                                    then functx:trim(concat(' (', $date, ')')) 
                                    else ()
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
                }
                </td></tr>
            else
                if ($name[@type = 'corporate']) 
                then
                    <tr>
                        <td class="label">
                        {                        
                        let $roles := $name/mods:role
                        for $role in $roles
                        return
                            mods:get-role-label-for-detail-view($role)
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


(: Prepares one or more rows for the detail view. :)
declare function mods:simple-row($data as item()?, $label as xs:string) as element(tr)? {
    for $d in $data
    where $d != ''
    return
        <tr>
            <td class="label">{$label}</td>
            <td class="record">{string($d)}</td>
        </tr>
};

(: Prepares the clickable url for mods:entry-full. :)
declare function mods:url($entry as element()) as element(tr)* {
    for $url in $entry/mods:location/mods:url
    return
        <tr>
            <td class="label"> 
            {
                if ($url/@displayLabel)
                then
                    $url/@displayLabel/string()
                else 'URL'
            }
            </td>
            <td class="record"><a href="{$url}">{$url}</a></td>
        </tr>
};
        
(: Prepares for mods:format-full. :)
declare function mods:entry-full($entry as element()) 
    {
    (: names :)
    mods:names-full($entry)
    ,
    
    (: titles :)
    for $titleInfo in $entry/mods:titleInfo
    return 
    mods:title-full($titleInfo)
    ,
    
    (: conferences :)
    mods:simple-row(mods:get-conference-detail-view($entry), 'Conference')
    ,

    (: place :)
    mods:simple-row(mods:get-place($entry/mods:originInfo/mods:place), 'Place')
    ,
    
    (: publisher :)
    mods:simple-row(mods:get-publisher($entry/mods:originInfo/mods:publisher), 'Publisher')
    ,
    
    (: dates :)
    if ($entry/mods:relatedItem/mods:originInfo/mods:dateCreated) 
    then () 
    else mods:simple-row($entry/mods:originInfo/mods:dateCreated[1], 'Date Created')
    ,
    if ($entry/mods:relatedItem/mods:originInfo/mods:dateIssued) 
    then () 
    else mods:simple-row($entry/mods:originInfo[1]/mods:dateIssued[1], 'Date Issued')
    ,
    if ($entry/mods:relatedItem/mods:originInfo/mods:dateModified) 
    then () 
    else mods:simple-row($entry/mods:originInfo[1]/mods:dateModified[1], 'Date Modified')
    ,
    mods:simple-row($entry/mods:originInfo/mods:dateOther, 'Other date')
    ,
    
    (: extent :)
    if ($entry/mods:extent) 
    then mods:simple-row(mods:get-extent($entry/mods:extent), 'Extent') 
    else ()
    ,
    
    (: URL :)
    mods:url($entry)
    ,
    
    (: relatedItem :)
    mods:get-related-items($entry, 'detail')
    ,
    
    (: typeOfResource :)
    mods:simple-row($entry/mods:typeOfResource[1]/string(), 'Type of Resource')
    ,
    
    (: internetMediaType :)
    mods:simple-row(
    (
    let $label := doc('/db/org/library/apps/mods/code-tables/internet-media-type-codes.xml')/code-table/items/item[value = $entry/mods:physicalDescription[1]/mods:internetMediaType]/label
    return
        if ($label) 
        then $label
        else $entry/mods:physicalDescription[1]/mods:internetMediaType)
    , 'Internet Media Type')
    ,
    
    (: language :)
    for $language in $entry/mods:language
    return
    mods:simple-row(mods:language-of-resource($language), 'Language of Resource') 
    ,

    (: script :)
    for $language in $entry/mods:language
    return
    mods:simple-row(mods:script-of-resource($language), 'Script of Resource') 
    ,

    (: languageOfCataloging :)
    for $language in ($entry/mods:recordInfo/mods:languageOfCataloging)
    let $languageTerm := $language/mods:languageTerm 
    return    
    if ($languageTerm)
    then
    mods:simple-row(mods:language-of-cataloging($language), 'Language of Cataloging')
    else ()
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
                concat(' (', replace(doc('/db/org/library/apps/mods/code-tables/genre-authority-codes.xml')/code-table/items/item[value = $authority]/label, '\*', ''), ')')
            else concat(' (', $authority, ')')
        else ()            
        )
    )
    ,
    
    (: abstract :)
    for $abstract in ($entry/mods:abstract)
    return
    mods:simple-row($abstract, 'Abstract')
    ,
    
    (: note :)
    for $note in ($entry/mods:note)
    let $displayLabel := $note/@displayLabel
    return    
    mods:simple-row($note
    , 
    concat('Note', 
        if ($displayLabel)
            then
                concat(' (', $displayLabel, ')')            
        else ()            
        )
    )
    ,
    
    (: subject :)
    (: We assume that there are not many subjects with the first element, topic, empty. :)
    if (normalize-space($entry/mods:subject[1]/string()))
    then
    mods:format-subjects($entry)    
    else ()
    , 
    
    (: ISBN :)
    mods:simple-row($entry/mods:identifier[@type='isbn'][1], 'ISBN'),
    
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
(: NB: "mods:format-full()" is referenced in session.xql. :)
declare function mods:format-full($id as xs:string, $clean as element(mods:mods), $collection-short as xs:string) {
    <table class="biblio-full">
    {
        <tr><td class="label">In Folder:</td><td>
        {$collection-short}
        </td></tr>
        ,
        mods:entry-full($clean)
    }
    </table>
};

(: Creates view for hitlist. :)
(: NB: "mods:format-short()" is referenced in session.xql. :)
declare function mods:format-short($id as xs:string, $entry as element(mods:mods)) {
    let $format :=
        (
        (: The author, etc. of the primary publication. :)
        if ($entry/mods:name/mods:namePart/text()) 
        then
            if ($entry/mods:name/mods:role/mods:roleTerm = ('aut', 'author', 'Author', '') or not($entry/mods:name/mods:role/mods:roleTerm))
            then
            (mods:format-multiple-primary-names($entry), ' ')
            else ()
        else ()
        ,
        (: The title of the primary publication. :)
        mods:get-short-title($id, $entry, '')
        ,
        
        (: The editor, etc. of the primary publication. :)
        let $roleTerms := $entry/mods:name/mods:role/mods:roleTerm
        return
        for $roleTerm in distinct-values($roleTerms)
        where $roleTerm = ('com', 'compiler', 'Compiler', 'editor', 'Editor', 'edt', 'trl', 'translator', 'Translator', 'annotator', 'Annotator', 'ann')        
            return
                let $names := $entry/mods:name[mods:role/mods:roleTerm = $roleTerm]
                return
                    if ($names/mods:namePart/text())
                    then
                        (
                        ', '
                        ,
                        mods:get-role-label-for-list-view($roleTerm)
                        ,
                        mods:format-multiple-secondary-names($names, 'secondary')
                        ,'.')
                    else ()
        , ' '
        ,
        (: The conference of the primary publication, containing originInfo and part information. :)
        if ($entry/mods:name[@type = 'conference']) 
        then
            mods:get-conference-hitlist($entry)
        else 
        (: If not a publication, get originInfo and part information for primary publication. :)
            mods:get-part-and-origin($entry)
        ,
        
        (: The periodical or anthology that the primary publication occurs in. :)    
        mods:get-related-items($entry, 'hitlist')
        ,
        
        (: The url of the primary publication. :)
        if ($entry/mods:location/mods:url/text())
        then
            for $url in $entry/mods:location/mods:url
                return
                    concat(' <', $url, '>')
        else ()
        )
    return
        (: The result. :)
        <div class="select">
        {
        mods:clean-up-punctuation(normalize-space(string-join($format,' ')))
        }
        </div>
};