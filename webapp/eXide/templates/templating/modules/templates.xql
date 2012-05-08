xquery version "3.0";

module namespace templates="http://exist-db.org/xquery/templates";

(:~
 : HTML templating module
:)
import module namespace config="http://exist-db.org/xquery/apps/config" at "config.xqm";

declare variable $templates:NOT_FOUND := QName("http://exist-db.org/xquery/templates", "NotFound");

(:~
 : Start processing the provided content. Template functions are looked up by calling the
 : provided function $resolver. The function should take a name as a string
 : and return the corresponding function item. The simplest implementation of this function could
 : look like this:
 : 
 : <pre>function($functionName as xs:string) { function-lookup(xs:QName($functionName), 3) }</pre>
 :
 : @param $content the sequence of nodes which will be processed
 : @param $resolver a function which takes a name and returns a function with that name
 : @param $model a sequence of items which will be passed to all called template functions. Use this to pass
 : information between templating instructions.
:)
declare function templates:apply($content as node()+, $resolver as function(xs:string) as item()?, $model as item()*) {
    request:set-attribute("$templates:resolver", $resolver),
    for $root in $content
    return
        templates:process($root, $resolver, $model)
};

(:~
 : Continue template processing on the given set of nodes. Call this function from
 : within other template functions to enable recursive processing of templates.
 :
 : @param $nodes the nodes to process
 : @param $model a sequence of items which will be passed to all called template functions. Use this to pass
 : information between templating instructions.
:)
declare function templates:process($nodes as node()*, $model as item()*) {
    let $resolver := request:get-attribute("$templates:resolver")
    for $node in $nodes
    return
        templates:process($node, $resolver, $model)
};

declare %private function templates:process($node as node(), $resolver as function(xs:string) as item()?, $model as item()*) {
    typeswitch ($node)
        case document-node() return
            for $child in $node/node() return templates:process($child, $resolver, $model)
        case element() return
            let $instructions := templates:get-instructions($node/@class)
            return
                if ($instructions) then
                    for $instruction in $instructions
                    return
                        templates:call($instruction, $node, $model, $resolver)
                else
                    element { node-name($node) } {
                        $node/@*, for $child in $node/node() return templates:process($child, $resolver, $model)
                    }
        default return
            $node
};

declare %private function templates:get-instructions($class as xs:string?) as xs:string* {
    for $name in tokenize($class, "\s+")
    where templates:is-qname($name)
    return
        $name
};

declare %private function templates:call($class as xs:string, $node as element(), $model as item()*, $resolver as function(xs:string) as item()?) {
    let $paramStr := substring-after($class, "?")
    let $parameters := templates:parse-parameters($paramStr)
    let $func := if ($paramStr) then substring-before($class, "?") else $class
    let $call := $resolver($func)
    return
        if (exists($call)) then
            $call($node, $parameters, $model)
        else
            (: Templating function not found: just copy the element :)
            element { node-name($node) } {
                $node/@*, for $child in $node/node() return templates:process($child, $resolver, $model)
            }
};

declare %private function templates:parse-parameters($paramStr as xs:string?) as element(parameters) {
    <parameters>
    {
        for $param in tokenize($paramStr, "&amp;")
        let $key := substring-before($param, "=")
        let $value := substring-after($param, "=")
        where $key
        return
            <param name="{$key}" value="{$value}"/>
    }
    </parameters>
};

declare %private function templates:is-qname($class as xs:string) as xs:boolean {
    matches($class, "^[^:]+:[^:]+")
};

declare function templates:include($node as node(), $params as element(parameters)?, $model as item()*) {
    let $relPath := $params/param[@name = "path"]/@value
    let $path := concat($config:app-root, "/", $relPath)
    return
        templates:process(doc($path), $model)
};

declare function templates:surround($node as node(), $params as element(parameters)?, $model as item()*) {
    let $with := $params/param[@name = "with"]/@value
    let $at := $params/param[@name = "at"]/@value
    let $using := $params/param[@name = "using"]/@value
    let $path := concat($config:app-root, "/", $with)
    let $content :=
        if ($using) then
            doc($path)//*[@id = $using]
        else
            doc($path)
    let $merged := templates:process-surround($content, $node, $at)
    return
        templates:process($merged, $model)
};

declare function templates:process-surround($node as node(), $content as node(), $at as xs:string) {
    typeswitch ($node)
        case document-node() return
            for $child in $node/node() return templates:process-surround($child, $content, $at)
        case element() return
            if ($node/@id eq $at) then
                element { node-name($node) } {
                    $node/@*, $content/node()
                }
            else
                element { node-name($node) } {
                    $node/@*, for $child in $node/node() return templates:process-surround($child, $content, $at)
                }
        default return
            $node
};

declare function templates:if-parameter-set($node as node(), $params as element(parameters), $model as item()*) as node()* {
    let $paramName := $params/param[@name = "param"]/@value/string()
    let $param := request:get-parameter($paramName, ())
    return
        if ($param and string-length($param) gt 0) then
            templates:process($node/node(), $model)
        else
            ()
};

declare function templates:if-parameter-unset($node as node(), $params as element(parameters), $model as item()*) as node()* {
    let $paramName := $params/param[@name = "param"]/@value/string()
    let $param := request:get-parameter($paramName, ())
    return
        if (not($param) or string-length($param) eq 0) then
            $node
        else
            ()
};

declare function templates:if-module-missing($node as node(), $params as element(parameters)?, $model as item()*) {
    let $at := $params/param[@name = "at"]/@value/string()
    let $uri := $params/param[@name = "uri"]/@value/string()
    return
        try {
            util:import-module($uri, "testmod", $at)
        } catch * {
            (: Module was not found: process content :)
            templates:process($node/node(), $model)
        }
};

declare function templates:display-source($node as node(), $params as element(parameters)?, $model as item()*) {
    let $syntax := $params/param[@name = "lang"]/@value/string()
    let $source := replace($node/string(), "^\s*(.*)\s*$", "$1")
    let $context := request:get-context-path()
    let $eXidePath := if (doc-available("/db/eXide/index.html")) then "apps/eXide" else "eXide"
    return
        <div class="code">
            <pre class="brush: {if ($syntax) then $syntax else 'xquery'}">
            { $source }
            </pre>
            <a class="btn" href="{$context}/{$eXidePath}/index.html?snip={encode-for-uri($source)}" target="eXide"
                title="Opens the code in eXide in new tab or existing tab if it is already open.">Try it</a>
        </div>
};

declare function templates:load-source($node as node(), $params as element(parameters), $model as item()*) as node()* {
    let $href := $node/@href/string()
    let $context := request:get-context-path()
    let $eXidePath := if (doc-available("/db/eXide/index.html")) then "apps/eXide" else "eXide"
    return
        <a href="{$context}/{$eXidePath}/index.html?open={$config:app-root}/{$href}" target="eXide">{$node/node()}</a>
};

(:~
    Processes input and select form controls, setting their value/selection to
    values found in the request - if present.
 :)
declare function templates:form-control($node as node(), $params as element(parameters), $model as item()*) as node()* {
    typeswitch ($node)
        case element(input) return
            let $name := $node/@name
            let $value := request:get-parameter($name, ())
            return
                if ($value) then
                    element { node-name($node) } {
                        $node/@* except $node/@value,
                        attribute value { $value },
                        $node/node()
                    }
                else
                    $node
        case element(select) return
            let $value := request:get-parameter($node/@name/string(), ())
            return
                element { node-name($node) } {
                    $node/@* except $node/@class,
                    for $option in $node/option
                    return
                        <option>
                        {
                            $option/@*,
                            if ($option/@value = $value) then
                                attribute selected { "selected" }
                            else
                                (),
                            $option/node()
                        }
                        </option>
                }
        default return
            $node
};

declare function templates:error-description($node as node(), $params as element(parameters)?, $model as item()*) {
    let $input := request:get-attribute("org.exist.forward.error")
    return
        element { node-name($node) } {
            $node/@*,
            util:parse($input)//message/string()
        }
};

declare function templates:fix-links($node as node(), $params as element(parameters)?, $model as item()*) {
    let $root := $params/param[@name = "root"]/@value/string()
    let $prefix :=
        if ($root eq "context") then
            request:get-context-path()
        else
            concat(request:get-context-path(), request:get-attribute("$exist:prefix"), request:get-attribute("$exist:controller"))
    let $temp := 
        element { node-name($node) } {
            $node/@* except $node/@class,
            attribute class { replace($node/@class, "\s*templates:fix-links[^\s]*", "")},
            for $child in $node/node() return templates:fix-links($child, $prefix)
        }
    return
        templates:process($temp, $model)
};

declare %private function templates:fix-links($node as node(), $prefix as xs:string) {
    typeswitch ($node)
        case element(a) return
            let $href := $node/@href
            return
                if (starts-with($href, "/")) then
                    <a href="{$prefix}{$href}">
                    { $node/@* except $href, $node/node() }
                    </a>
                else
                    $node
        case element() return
            element { node-name($node) } {
                $node/@*, for $child in $node/node() return templates:fix-links($child, $prefix)
            }
        default return
            $node
};