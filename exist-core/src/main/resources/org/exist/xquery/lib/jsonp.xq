module namespace jsonp="http://www.jsonp.org";

(:~
 :   Helper function: output element content for elements with more than one child node or attribute.
 :
 :   Special cases:
 :
 :       <p>Child elements with the same name are added to an array.</p>
 :       <p>If an element has attribute and text content, the text content becomes a
 :       property, e.g. '#text': 'my text'.</p>
 :       <p>In mixed content nodes, text nodes will be dropped.</p>
 :)
declare function jsonp:element-helper($attribs as attribute()*, $children as node()*) {
    string-join((
        (: Output the attributes :)
        for $attr in $attribs
        return
            jsonp:node-to-jsonp($attr),
        if ($children instance of text()+) then
            for $text in $children
            return
                if($text/parent::node()/@jsonp:literal)then
                (
                    $text
                )else(
                    concat('"#text": "', $text, '"')
                )
        else
            for $name in distinct-values(for $c in $children return node-name($c))
            return
                jsonp:node-to-jsonp($children[node-name(.) = $name])
    ),
    ', ')
};

(:~
 :   Helper function: output the contents of a node or attribute.
 :
 :   Special cases:
 :
 :       <p>An empty element becomes 'null', i.e. &lt;e/&gt; becomes {"e": null}.</p>
 :       <p>An element with a single text child becomes a property with the value of the text child, i.e.
 :       &lt;e&gt;text&lt;/e&gt; becomes {"e": "text"}</p>
 :)
declare function jsonp:contents-to-jsonp($node as node()) {
    typeswitch ($node)
        case $elem as element() return
            let $children := $elem/node()
            let $attribs := $elem/@*
            return
                (: XML: <e/> JSONP: "e": null :) 
                if (count($children) eq 0 and count($attribs) eq 0) then
                    'null'
                (: XML: <e>text</e> JSONP: "e": "text" :)
                else if (count($children) eq 1 and (count($attribs) eq 0 or $attribs[name() eq "jsonp:literal"]) 
                    and $children[1] instance of text()) then
                    jsonp:node-to-jsonp($children[1])
                else
                    concat('{', jsonp:element-helper($attribs, $children), '}')
        case $attr as attribute() return
            concat('"', string($attr), '"')
        case $text as text() return
            if($text/parent::node()/@jsonp:literal)then(
                $text
            ) else (
                concat('"', $text, '"')
            )
        default return ()
};
(:~
 :    Helper function: convert a node into JSON.
 :)   
declare function jsonp:node-to-jsonp($node as node()+) {
    typeswitch ($node)
        case $elem as element() return
            concat('"', node-name($elem), '" :  ', jsonp:contents-to-jsonp($node))
        (: sequence of nodes passed from jsonp:element-helper(), treat as array :)
        case $elements as element()+ return
            concat(
                '"', node-name($elements[1]), '" : [ ',
                string-join(
                    for $e in $elements return jsonp:contents-to-jsonp($e), 
                ', '),
                ']'
            )
        case $attr as attribute() return
            if(name($attr) ne "jsonp:literal")then(
                concat('"@', node-name($attr), '": ', jsonp:contents-to-jsonp($attr))
            )else()
        default return
            jsonp:contents-to-jsonp($node)
};

declare function jsonp:node-to-jsonp-entry($node as node()+) {
    typeswitch ($node)
        case $elements as element()* return
            concat(
                '"', node-name($elements[1]), '" : [ ',
                string-join(
                    for $e in $elements return jsonp:contents-to-jsonp($e), 
                ', '),
                ']'
            )
        case $attr as attribute() return
            if(name($attr) ne "jsonp:literal")then(
                concat('"@', node-name($attr), '": ', jsonp:contents-to-jsonp($attr))
            )else()
        default return
            jsonp:contents-to-jsonp($node)
};

(:~
 :    Main entry point of the module. Convert node into JSONP.
 :)
declare function jsonp:xml-to-jsonp($node as node()+, $call-back as xs:string?) {
    let $res := jsonp:node-to-jsonp-entry($node)
    return
        concat(
            $call-back,
            '({"totalResultsCount":',
            count($node),
            ',',
            $res,
            '})'
        )
};