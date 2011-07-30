xquery version "1.0";

(: $Id$ :)

declare namespace style="http://exist-db.org/xquery/style";
declare namespace nav="http://exist-db.org/NS/sidebar";

declare option exist:serialize "method=xhtml";

declare function style:page-head($node) {
    <div id="page-head">
        <a href="{$node/@base}" style="text-decoration: none">
            <img src="{$node/@base}/logo.jpg" title="eXist-db: Open Source Native XML Database" style="border-style: none;text-decoration: none"/>
        </a>
        <div id="navbar">
            <ul id="menu">
            {
                for $link in $node//nav:toolbar/nav:link
                let $href :=
                    if (matches($link/@href, "^\w+://")) then
                        $link/@href/string()
                    else
                        concat($node/@base, "/", $link/@href)
                return
                    <li><a href="{$href}">{$link/string()}</a></li>
            }
            </ul>
            <h1>Open Source Native XML Database</h1>
        </div>
    </div>,
    style:sidebar($node)
};

declare function style:sidebar($node) {
    <div id="sidebar">
    {
        for $group in $node//nav:group
        return
            <div class="block">
                <div class="head rounded-top">
                    <h3>{$group/@name/string()}</h3>
                </div>
                <ul class="rounded-bottom">
                { 
                    for $item in $group/nav:item
                    let $link := $item/nav:link
                    let $href :=
                        if (matches($link/@href, "^\w+://")) then
                            $link/@href/string()
                        else
                            concat($node/@base, "/", ($link/@href)[1])
                    return
                        <li><a href="{$href}">{$link/string()}</a></li>
                }
                </ul>
          </div>
    }
    </div>
};

declare function style:default-styles() {
    <link rel="stylesheet" type="text/css" href="../styles/default-style2.css"/>,
    <script language="Javascript" type="text/javascript" src="../styles/curvycorners.js"></script>
};

declare function style:transform($node) {
    typeswitch ($node)
        case element(style:page-head) return
            style:page-head($node)
        case element(style:default-styles) return
            style:default-styles()
        case element() return
            element { node-name($node) } {
                $node/@*, for $child in $node/node() return style:transform($child)
            }
        default return
            $node
};

let $input := request:get-data()
let $output :=
    style:transform(util:expand($input))
return
    $output