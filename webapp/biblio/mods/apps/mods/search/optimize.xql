(:~
    Create indexes to speed up the sorting of query results.
    In particular, ordering by author name requires a lot of
    pre-processing.
:)
declare namespace opt="http://exist-db.org/xquery/biblio/optimize";

declare namespace mods="http://www.loc.gov/mods/v3";

import module namespace names="http://exist-db.org/xquery/biblio/names"
    at "names.xql";

import module namespace sort="http://exist-db.org/xquery/sort"
	at "java:org.exist.xquery.modules.sort.SortModule";

(:~ Callback function to return the normalized name of the first author :) 
declare function opt:order-by-name($node as element()) as xs:string? {
    let $names :=
        for $name in $node/mods:name[1]
        return
            names:format-name($name)
    return
        (: 
            Attention: string-join returns the empty string "" if $names is empty.
            For correct ordering, we want an empty sequence to be returned
        :)
        if (empty($names)) then
            ()
        else
            string-join($names, "; ")
};

(:~ Callback function to index by date :)
(:
declare function opt:order-by-date($node as node()) as xs:integer* {
    for $year in $node//mods:dateCreated[1]
    return
        xs:integer($year)
        :)
        (:~ Callback function to index by date :)
declare function opt:order-by-date($node as node()) as xs:integer? {
    let $year :=
        for $date in $node/mods:relatedItem/mods:part/mods:date[1]
        return
            xs:integer(substring($date, 1, 4))
    return
        if (empty($year)) 
        then 
            for $date in $node/mods:originInfo/mods:dateIssued[1]
            return
            xs:integer(substring($date, 1, 4))
        else 
        if (empty($year)) 
        then 
            for $date in $node/mods:relatedItem/mods:originInfo/mods:dateIssued[1]
            return
            xs:integer(substring($date, 1, 4))
        else $year        
};

(:~ Callback function to return the normalized title :)
declare function opt:order-by-title($node as node()) as xs:string? {
    $node/mods:titleInfo[1]/mods:title[1]/string()
};

let $pass := request:get-parameter("pass", ())
return
    system:as-user("admin", $pass,
        let $optimize := ft:optimize()
        let $fn := util:function(xs:QName("opt:order-by-name"), 1)
        let $dateFn := util:function(xs:QName("opt:order-by-date"), 1)
        let $titleFn := util:function(xs:QName("opt:order-by-title"), 1)
        let $mods := //mods:mods
        let $nameIdx :=
        	sort:create-index-callback("mods:name", $mods, $fn, <options empty="greatest"/>)
        let $titleIdx :=
            sort:create-index-callback("mods:title", $mods, $titleFn, <options empty="greatest"/>)
        let $dateIdx :=
            sort:create-index-callback("mods:date", $mods, $dateFn, <options order="descending" empty="least"/>)
        return
            <p>Indexes created.</p>
    )