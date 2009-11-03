xquery version "1.0";

declare namespace sb="http://exist-db.org/NS/sidebar";

(:~
 # Traverse the document and search for sidebar links.
 # Make the relative links absolute by prepending $base.
:)
declare function sb:links($node as node(), $base as xs:string) {
    typeswitch ($node)
        case $elem as element(sb:link) return
            if (matches($elem/@href, '^\w+:')) then
                $elem
            else
                <sb:link href="{$base}/{$elem/@href}">{$elem/node()}</sb:link>
        case $elem as element() return
            element { node-name($elem) } {
                $elem/@*,
                for $child in $elem/node() return sb:links($child, $base)
            }
        default return
            $node
};

let $sb := request:get-attribute('model')
let $base := request:get-attribute('base')
let $expanded := util:expand($sb, "expand-xincludes=yes")
return 
    sb:links($expanded, $base)