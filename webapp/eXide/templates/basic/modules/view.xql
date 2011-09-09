xquery version "3.0";

import module namespace config="http://exist-db.org/xquery/apps/config" at "config.xqm";

declare namespace t="http://exist-db.org/xquery/apps/transform";

declare option exist:serialize "method=html5 media-type=text/html";

declare function t:transform($node as node()) {
    typeswitch ($node)
    case element() return
        switch ($node/@id)
            case "app-info" return
                config:app-info($node)
            default return
                element { node-name($node) } {
                    $node/@*, for $child in $node/node() return t:transform($child)
                }
    default return
        $node
};

let $input := request:get-data()/element()
return
    t:transform($input)