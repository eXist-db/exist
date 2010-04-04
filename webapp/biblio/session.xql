xquery version "1.0";

(:~
    Handles the actual display of the search result. The pagination plugin
    will call this query to retrieve the next page of search results.
    
    The query returns a simple table with two rows: 1) the number of the current
    record, 2) the data to display.
:)
import module namespace mods="http://www.loc.gov/mods/v3" at "retrieve-mods.xql";

declare namespace bs="http://exist-db.org/xquery/biblio/session";

declare option exist:serialize "media-type=application/xhtml+xml";

declare function bs:retrieve($start as xs:int, $count as xs:int) {
    let $cached := session:get-attribute("cached")
    let $stored := session:get-attribute("personal-list")
    let $total := count($cached)
    let $available :=
        if ($start + $count gt $total) then
            $total - $start + 1
        else
            $count
    return
        <table xmlns="http://www.w3.org/1999/xhtml">
        {
            for $item at $pos in subsequence($cached, $start, $available)
            let $currentPos := $start + $pos - 1
            let $id := concat(document-uri(root($item)), '#', util:node-id($item))
            let $saved := $stored//*[@id = $id]
            return
                <tr>
                    <td class="current">{$currentPos}</td>
                    <td class="actions">
                        <a id="{$id}" href="#{$currentPos}" class="save">
                            <img title="save to my list" 
                                src="{if ($saved) then 'disk_gew.gif' else 'disk.gif'}"
                                class="{if ($saved) then 'stored' else ''}"/>
                        </a>
                    </td>
                    <td class="data">{
                        if ($count eq 1) then
                            mods:format-full(string($currentPos), $item)
                        else
                            mods:format-short(string($currentPos), $item)
                    }</td>
                </tr>
        }
        </table>
};

session:create(),
let $start0 := request:get-parameter("start", ())
let $start := xs:int(if ($start0) then $start0 else 1)
let $count0 := request:get-parameter("count", ())
let $count := xs:int(if ($count0) then $count0 else 10)
return
    bs:retrieve($start, $count)