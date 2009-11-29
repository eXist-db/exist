xquery version "1.0";

declare namespace xqdoc="http://www.xqdoc.org/1.0";

declare variable $xqdoc:doc external;

declare function xqdoc:normalize($node as node()) {
    typeswitch ($node)
        case element(xqdoc:signature) return
            element xqdoc:signature {
                $node/@*, replace($node/string(), "\s*declare function\s+", "")
            }
        case element() return
            element { node-name($node) } {
                $node/@*, for $child in $node/node() return xqdoc:normalize($child)
            }
        default return
            $node
};

xqdoc:normalize($xqdoc:doc)