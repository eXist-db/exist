(:~
    Module to clean up a MODS record. Removes empty elements.
:)
module namespace clean="http:/exist-db.org/xquery/mods/cleanup";

declare namespace mods="http://www.loc.gov/mods/v3";
declare namespace xlink="http://www.w3.org/1999/xlink";

declare function clean:remove-if-empty-text($node as element()) {
    if (string-length(normalize-space($node/string())) eq 0) then
        ()
    else
        element { node-name($node) } {
            clean:remove-empty-attributes($node/@*), for $child in $node/node() return clean:cleanup($child)
        }
};

declare function clean:remove-if-empty-attribute($node as element(), $attr as attribute()?) {
    if (string-length($attr) eq 0 and empty($node//text())) then
        ()
    else
        $node
};

declare function clean:remove-empty-attributes($attribs as attribute()*) {
    for $attr in $attribs
    return
        if ($attr eq "") then
            ()
        else
            $attr
};

declare function clean:cleanup($node as node()) {
    typeswitch ($node)
        case element(mods:relatedItem) return
            clean:remove-if-empty-attribute($node, $node/@xlink:href)
        case element() return
            clean:remove-if-empty-text($node)
        default return
            $node
};