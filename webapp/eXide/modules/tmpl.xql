module namespace tmpl="http://exist-db.org/xquery/template";

(:~
    Expand the XML fragment given in $input and replace all occurrences of template
    parameters within text strings or attributes with the replacements given in
    parameter $map. Template parameters are delimited with double $: $$parameter$$.
    
    The parameter map contains a mapping of template parameter names to values:
    
    <parameters>
        <param name="param1" value="value1"/>
    </parameters>
:)
declare function tmpl:expand-template($input as item(), $map as element(parameters)?) {
    typeswitch ($input)
        case element() return
            element { node-name($input) } {
                for $child in ($input/@*, $input/node()) return tmpl:expand-template($child, $map)
            }
        case attribute() return
            attribute { node-name($input) } {
                tmpl:parse($input/string(), $map)
            }
        case text() return
            tmpl:parse($input/string(), $map)
        default return
            $input
};

(:~
 : Helper function: recursively replace parameters in the given string.
 :)
declare function tmpl:parse($str as xs:string, $map as element(parameters)?) {
    if (contains($str, "$$")) then
        let $after := substring-after($str, "$$")
        let $param := substring-before($after, "$$")
        let $replacement := $map/param[@name = $param]/@value
        return
            tmpl:parse(concat(substring-before($str, "$$"), $replacement, substring-after($after, "$$")), $map)
    else
        $str
};