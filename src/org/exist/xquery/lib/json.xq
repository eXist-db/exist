(:~
 :    Transform XML fragments into <a href="http://www.json.org">JSON</a>. The target of this module
 :    is to create a straight-forward Javasript representation of data-centric XML. It does not try to handle
 :    mixed content nodes (with a mix of elements and text).
 :    
 :    <h2>Special rules</h2>
 :       <p>The root element will be absorbed, i.e. &lt;root&gt;text&lt;/root&gt; becomes "text".</p>
 :       <p>Child elements with the same name are added to an array.</p>
 :       <p>If an element has attribute and text content, the text content becomes a
 :       property, e.g. '#text': 'my text'.</p>
 :       <p>In mixed content nodes, text nodes will be dropped.</p>
 :       <p>An empty element becomes 'null', i.e. &lt;e/&gt; becomes {"e": null}.</p>
 :       <p>An element with a single text child becomes a property with the value of the text child, i.e.
 :       &lt;e&gt;text&lt;/e&gt; becomes {"e": "text"}</p>
 :)
module namespace json="http://www.json.org";

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
declare function json:element-helper($attribs as attribute()*, $children as node()*) {
    string-join((
        (: Output the attributes :)
        for $attr in $attribs
        return
            json:node-to-json($attr),
        if ($children instance of text()+) then
            for $text in $children
            return
                concat('"#text": "', $text, '"')
        else
            for $name in distinct-values(for $c in $children return node-name($c))
            return
                json:node-to-json($children[node-name(.) = $name])
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
declare function json:contents-to-json($node as node()) {
    typeswitch ($node)
        case $elem as element() return
            let $children := $elem/node()
            let $attribs := $elem/@*
            return
                (: XML: <e/> JSON: "e": null :) 
                if (count($children) eq 0 and count($attribs) eq 0) then
                    'null'
                (: XML: <e>text</e> JSON: "e": "text" :)
                else if (count($children) eq 1 and count($attribs) eq 0 
                    and $children[1] instance of text()) then
                    json:node-to-json($children[1])
                else
                    concat('{', json:element-helper($attribs, $children), '}')
        case $attr as attribute() return
            concat('"', string($attr), '"')
        case $text as text() return
            concat('"', $text, '"')
        default return ()
};
(:~
 :    Helper function: convert a node into JSON.
 :)   
declare function json:node-to-json($node as node()+) {
    typeswitch ($node)
        case $elem as element() return
            concat('"', node-name($elem), '" : ', json:contents-to-json($node))
        (: sequence of nodes passed from json:element-helper(), treat as array :)
        case $elements as element()+ return
            concat(
                '"', node-name($elements[1]), '" : [ ',
                string-join(
                    for $e in $elements return json:contents-to-json($e), 
                ', '),
                ']'
            )
        case $attr as attribute() return
            concat('"@', node-name($attr), '": ', json:contents-to-json($attr))
        default return
            json:contents-to-json($node)
};

(:~
 :    Main entry point of the module. Convert a single node into JSON.
 :    The root element will be absorbed.
 :)
declare function json:xml-to-json($node as node()) {
    json:contents-to-json($node)
};