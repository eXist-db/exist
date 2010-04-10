xquery version "1.0";

(:~
    Returns the list of distinct authors, dates etc. occurring in the result set.
    The query is called via AJAX when the user expands one of the headings in the
    "filter" box.
:)
import module namespace names="http://exist-db.org/xquery/biblio/names"
    at "names.xql";

declare namespace mods="http://www.loc.gov/mods/v3";

declare variable $local:MAX_RESULTS := 1000;
declare variable $local:MAX_RESULTS_NAMES := 2000;
declare variable $local:MAX_TERMS := 30;

declare function local:key($key, $options) {
    <li><a href="?filter=Title&amp;value={$key}&amp;query-tabs=advanced">{$key} ({$options[1]})</a></li>
};

declare function local:keywords($results as element()*) {
    let $max := 
        if (count($results) ge $local:MAX_RESULTS) then 
            $local:MAX_TERMS 
        else
            () (: unlimited :)
    let $prefixParam := request:get-parameter("prefix", "")
    let $prefix := if (empty($max)) then "" else $prefixParam
    let $callback := util:function(xs:QName("local:key"), 2)
    return
        <ul xmlns="http://www.w3.org/1999/xhtml" class="{if (empty($max)) then 'complete' else $max}">
        { util:index-keys($results//mods:titleInfo, $prefix, $callback, $max, "lucene-index") }
        </ul>
};

let $type := request:get-parameter("type", ())
let $cached := session:get-attribute("mods:cached")
return
    if ($type eq 'author') then
        <ul xmlns="http://www.w3.org/1999/xhtml">
        {
            let $names := $cached//mods:name
            return
                if (count($names) gt $local:MAX_RESULTS_NAMES) then
                    <li>Too many names. Please restrict the result set.</li>
                else
                    let $authors :=
                        for $author in $names
                        return names:format-name($author)
                    let $distinct := distinct-values($authors)
                    for $name in $distinct
                    order by $name
                    return
                        <li><a href="?filter=Author&amp;value={$name}&amp;query-tabs=advanced">{$name}</a></li>
        }
        </ul>
    else if ($type eq 'date') then
        <ul xmlns="http://www.w3.org/1999/xhtml">
        {
            let $dates := $cached/mods:originInfo
            return
                if (count($dates) gt $local:MAX_RESULTS_NAMES) then
                    <li>Too many dates. Please restrict the result set.</li>
                else
                    let $dates :=
                        for $info in $cached/mods:originInfo
                        return
                            ($info/mods:dateCreated | $info/mods:dateIssued)[1]
                    for $date in distinct-values($dates)
                    order by $date descending
                    return
                        <li><a href="?filter=Date&amp;value={$date}&amp;query-tabs=advanced">{$date}</a></li>
         }</ul>
    else if ($type eq 'keywords') then
        local:keywords($cached)
    else
        ()