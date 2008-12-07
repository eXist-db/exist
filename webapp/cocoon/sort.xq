xquery version "1.0";
(: $Id: sort.xq 6434 2007-08-28 18:59:23Z ellefj $ :)

module namespace sort="http://exist-db.org/biblio/sort";

declare namespace mods="http://www.loc.gov/mods/v3";

declare function sort:orderByName($m as element()) as xs:string?
{
    let $name := $m/mods:name[1],
        $order :=
            if(exists($name/mods:namePart[@type='family'])) then
                concat($name/mods:namePart[@type='family'], ", ", $name/mods:namePart[@type='given'])
            else if(exists($name/mods:namePart)) then
                xs:string($name/mods:namePart)
            else
                ""
    return
        ($order)
};
    
(: Map order parameter to xpath for order by clause :)
declare function sort:orderExpr($field as xs:string) as xs:string
{
    if ($field = "creator") then
        "sort:orderByName($m)"
    else if ($field = "title") then
        "$m/mods:titleInfo[1]/mods:title[1]"
    else
        "$m/mods:originInfo/mods:dateCreated[1] descending"
};
