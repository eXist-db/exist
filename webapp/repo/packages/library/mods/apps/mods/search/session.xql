xquery version "1.0";

(:~
    Handles the actual display of the search result. The pagination jQuery plugin in jquery-utils.js
    will call this query to retrieve the next page of search results.
    
    The query returns a simple table with four columns: 
    1) the number of the current record, 
    2) a link to save the current record in "My Lists", 
    3) the type of resource (represented by an icon), and 
    4) the data to display.
:)

import module namespace mods="http://www.loc.gov/mods/v3" at "retrieve-mods.xql";
import module namespace jquery="http://exist-db.org/xquery/jquery" at "resource:org/exist/xquery/lib/jquery.xql";
import module namespace security="http://exist-db.org/mods/security" at "security.xqm";
import module namespace sharing="http://exist-db.org/mods/sharing" at "sharing.xqm";
import module namespace clean="http:/exist-db.org/xquery/mods/cleanup" at "cleanup.xql";

declare namespace bs="http://exist-db.org/xquery/biblio/session";
declare namespace functx = "http://www.functx.com";

declare option exist:serialize "media-type=application/xhtml+xml";

declare variable $bs:USER := security:get-user-credential-from-session()[1];

declare function functx:replace-first( $arg as xs:string?, $pattern as xs:string, $replacement as xs:string )  as xs:string {       
   replace($arg, concat('(^.*?)', $pattern),
             concat('$1',$replacement))
 } ;

declare function bs:collection-is-writable($collection as xs:string) {
    (:if ($collection eq $sharing:groups-collection) then:)
        true ()(:false()
    else
        security:can-write-collection($bs:USER, $collection):)
};

declare function bs:retrieve($start as xs:int, $count as xs:int) {
    let $cached := session:get-attribute("mods:cached")
    let $stored := session:get-attribute("mods-personal-list")
    let $total := count($cached)
    let $available :=
        if ($start + $count gt $total) then
            $total - $start + 1
        else
            $count
    let $home := security:get-home-collection-uri($bs:USER)
    return
        <table xmlns="http://www.w3.org/1999/xhtml">
        {
            for $item at $pos in subsequence($cached, $start, $available)
            let $currentPos := $start + $pos - 1
            (: Why does $currentPos have a final "."? Should be removed. :)
            let $id := concat(document-uri(root($item)), '#', util:node-id($item))
            let $saved := $stored//*[@id = $id]
            return
            if ($count eq 1) then
                <tr class="detail">
                    <td class="detail-number">{$currentPos}</td>
                    {
                    <td class="actions-cell">
                        <a id="save_{$id}" href="#{$currentPos}" class="save">
                            <img title="Save Record to My List" src="../../../resources/images/{if ($saved) then 'disk_gew.gif' else 'disk.gif'}" class="{if ($saved) then 'stored' else ''}"/>
                        </a>
                    </td>
                    }
                    <td class="detail-type">
                        <img title="{$item/mods:typeOfResource/string()}" src="../../../resources/images/{mods:return-type(string($currentPos), $item)}.png"/>
                    </td>
                    {
                    let $isWritable := bs:collection-is-writable(util:collection-name($item))
                        return
                            <td class="detail-xml">
                                <div class="actions-toolbar">
                                    <a target="_new" href="source.xql?id={$item/@ID}&amp;clean=yes">
                                        <img title="View XML Source of Record" src="../../../resources/images/script_code.png"/>
                                    </a>
                                    {
                                    (: if the item's collection is writable, display edit/delete and move buttons :)
                                    if ($isWritable) 
                                    then (
                                        <a href="../edit/edit.xq?id={$item/@ID}&amp;collection={util:collection-name($item)}">
                                            <img title="Edit Record" src="../../../resources/images/page_edit.png"/>
                                        </a>
                                        ,
                                        <a class="remove-resource" href="#{$id}"><img title="Delete Record" src="../../../resources/images/delete.png"/></a>,
                                        <a class="move-resource" href="#{$id}"><img title="Move Record" src="../../../resources/images/shape_move_front.png"/></a>
                                        )
                                    else ()
                                    }
                                    {
                                    (: button to add a related item :)
                                    if ($bs:USER ne "guest") 
                                    then
                                        <a class="add-related" href="#{if ($isWritable) then util:collection-name($item) else $home}#{$item/@ID}">
                                            <img title="Create Related Item" src="../../../resources/images/page_add.png"/>
                                        </a>
                                        else ()
                                    }
                                </div>
                                    {
                                    let $collection := util:collection-name($item)
                                    let $collection-short := functx:replace-first($collection, '/db', '')
                                    let $clean := clean:cleanup($item)
                                    return
                                        mods:format-full(string($currentPos), $clean, $collection-short)
                                        (: What is $currentPos used for? :)
                                    }
                                </td>
                    }
                </tr>
            else 
                <tr class="list">
                    <td class="list-number">{$currentPos}</td>
                    {
                    <td class="actions-cell">
                        <a id="save_{$id}" href="#{$currentPos}" class="save">
                            <img title="Save Record to My List" src="../../../resources/images/{if ($saved) then 'disk_gew.gif' else 'disk.gif'}" class="{if ($saved) then 'stored' else ''}"/>
                        </a>
                    </td>
                    }
                    <td class="list-type">
                        <img title="{$item/mods:typeOfResource/string()}" src="../../../resources/images/{mods:return-type(string($currentPos), $item)}.png"/>
                    </td>
                    {
                    <td class="pagination-toggle">
                        <a>
                        {
                            let $clean := clean:cleanup($item)
                                return
                                    mods:format-short(string($currentPos), $clean)
                                    (: Originally $item was passed to mods:format-short() - was there a reason for that? Performance? :)
                        }
                        </a>
                    </td>
                    }
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
