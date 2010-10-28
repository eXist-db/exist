(:~
    Module to clean up a MODS record. Removes empty elements.
:)
module namespace clean="http:/exist-db.org/xquery/mods/cleanup";

declare namespace mods="http://www.loc.gov/mods/v3";
declare namespace xlink="http://www.w3.org/1999/xlink";

declare function clean:remove-if-empty-text($node as element()) {
    if (empty($node//text())) then
        ()
    else
        element { node-name($node) } {
            $node/@*, for $child in $node/node() return clean:cleanup($child)
        }
};

declare function clean:remove-if-empty-attribute($node as element(), $attr as attribute()?) {
    if (string-length($attr) eq 0 and empty($node//text())) then
        ()
    else
        $node
};

declare function clean:cleanup($node as node()) {
    typeswitch ($node)
        case element(mods:subject) return
            clean:remove-if-empty-text($node)
        case element(mods:place) return
            clean:remove-if-empty-text($node)
        case element(mods:placeTerm) return
            clean:remove-if-empty-text($node)
        case element(mods:dateIssued) return
            clean:remove-if-empty-text($node)
        case element(mods:publisher) return
            clean:remove-if-empty-text($node)
        case element(mods:edition) return
            clean:remove-if-empty-text($node)
        case element(mods:language) return
            clean:remove-if-empty-text($node)
        case element(mods:abstract) return
            clean:remove-if-empty-text($node)
        case element(mods:tableOfContents) return
            clean:remove-if-empty-text($node)
        case element(mods:note) return
            clean:remove-if-empty-text($node)
        case element(mods:identifier) return
            clean:remove-if-empty-text($node)
        case element(mods:location) return
            clean:remove-if-empty-text($node)
        case element(mods:part) return
            clean:remove-if-empty-text($node)
        case element(mods:classification) return
            clean:remove-if-empty-text($node)
        case element(mods:nonSort) return
            clean:remove-if-empty-text($node)
        case element(mods:subTitle) return
            clean:remove-if-empty-text($node)
        case element(mods:relatedItem) return
            clean:remove-if-empty-attribute($node, $node/@xlink:href)
        case element() return
            element { node-name($node) } {
                $node/@*, for $child in $node/node() return clean:cleanup($child)
            }
        default return
            $node
};