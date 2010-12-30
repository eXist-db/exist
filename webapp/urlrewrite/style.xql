xquery version "1.0";

declare namespace style="http://exist-db.org/xquery/style";

declare function style:page-head() {
    <div id="page-head">
        <a href="../" style="text-decoration: none">
            <img src="../logo.jpg" title="eXist-db: Open Source Native XML Database" style="border-style: none;text-decoration: none"/>
        </a>
        <div id="navbar">
            <h1>Open Source Native XML Database</h1>
        </div>
    </div>
};

declare function style:default-styles() {
    <link rel="stylesheet" type="text/css" href="../styles/default-style2.css"/>,
    <script language="Javascript" type="text/javascript" src="../styles/curvycorners.js"></script>
};

declare function style:transform($node) {
    typeswitch ($node)
        case element(style:page-head) return
            style:page-head()
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
return
    style:transform($input)