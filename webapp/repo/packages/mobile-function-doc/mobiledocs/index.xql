xquery version "1.0";

declare namespace mfd="http://exist-db.org/xquery/functions/mobile";
declare namespace xq="http://www.xqdoc.org/1.0";

declare option exist:serialize "method=html5 indent=yes media-type=text/html";

declare function mfd:render-module($moduleURIs as xs:string*) {
    for $moduleName in $moduleURIs
	let $moduleDocs := collection("/db")//xq:xqdoc[xq:module/xq:name eq $moduleName]
	let $moduleDescription := $moduleDocs/xq:module/xq:comment/xq:description/node()
	order by $moduleName
	return
	   <div>
            <h1>{$moduleName}</h1>
            <p class="uri">{$moduleDocs/xq:module/xq:uri/text()}</p>
            
            <p class="description">{$moduleDescription}</p>
            
            <ul data-role="listview" data-inset="true" data-theme="c" data-dividertheme="b">
            {
                mfd:render-functions($moduleDocs, $moduleDocs/xq:functions/xq:function)
            }
            </ul>
        </div>
};

declare function mfd:render-functions($moduleDocs as element(xq:xqdoc), $functions as element(xq:function)*) {
    let $firstChars :=
        distinct-values(
            for $name in $functions/xq:name
            order by $name
            return substring($name, 1, 1)
        )
    for $firstChar in $firstChars
    let $funcNames :=
        distinct-values(
            for $name in $functions[starts-with(xq:name, $firstChar)]
            return
                $name/xq:name
        )
    return (
        <li data-role="list-divider">
            <h2>{$firstChar}</h2>
        </li>,
        for $funcName in $funcNames
        return
            mfd:render-function-link($moduleDocs//xq:function[xq:name eq $funcName])
    )
};

declare function mfd:render-function-link($functionNodes as element(xq:function)+) {
    let $name := $functionNodes[1]/xq:name/string()
    return
        <li>
            <h2>
                <a href="{$name}/">{$name}</a>
            </h2>
            {
                for $signature in $functionNodes/xq:signature/string()
                return
                    <p>{$signature}</p>
            }
        </li>
};

declare function mfd:list-modules() {
    <ul data-role="listview" data-inset="true" data-theme="c" data-dividertheme="b">
    {
        let $modules := collection("/db")/xq:xqdoc/xq:module
        for $module in $modules
        let $name := if ($module/xq:uri eq 'http://www.w3.org/2005/xpath-functions') then "fn" else $module/xq:name/string()
        order by $name ascending
        return
            <li>
                <h2>
                    <a href="{$name}/">{$name}</a> 
                    <span class="ui-li-count">{count($module/../xq:functions/xq:function)}</span>
                </h2>
                <p>{$module/xq:comment/xq:description/string()}</p>
            </li>
    }
    </ul>
};

declare function mfd:arity($signature as xs:string) as xs:integer {
    if (contains($signature, ",")) then
        count(tokenize($signature, ","))
    else if (matches($signature, "^[^\(]+\(\s*\).*")) then
        0
    else
        1
};

declare function mfd:render-function($moduleName as xs:string, $functionName as xs:string) {
    let $moduleDocs := collection("/db")//xq:xqdoc[xq:module/xq:name eq $moduleName]
    let $functions := $moduleDocs//xq:function[xq:name eq $functionName]
    for $funDocs in $functions
    let $functionName := $funDocs/xq:name/text()
    let $functionSignature := $funDocs/xq:signature/text()
    let $arity := mfd:arity($functionSignature)
    let $functionDeprecated := $funDocs/xq:comment/xq:deprecated/text()
    let $functionDescription := $funDocs/xq:comment/xq:description/text()
    let $functionVersion := $funDocs/xq:comment/xq:version/text()
    let $functionParameters := $funDocs/xq:comment/xq:param
    let $functionSees := $funDocs/xq:comment/xq:see
    let $functionReturns := $funDocs/xq:comment/xq:return/text()
    let $authors := $funDocs/xq:comment/xq:author
    let $sinces := $funDocs/xq:comment/xq:since
    order by $arity
    return
        <div class="function">
            <h1>{$functionName}/{$arity}</h1>
            
            <p class="signature">{$functionSignature}</p>
            <p class="version">{$functionVersion}</p>
            <ul>
            {
                for $param in $functionParameters
                return
                    <li>{$param}</li>
            }
            </ul>
            <p class="description">{$functionDescription}</p>
        </div>
};

declare function mfd:main() {
    let $module := request:get-parameter("module", ())
    let $function := request:get-parameter("function", ())
    
    let $moduleName := if ($module eq "fn") then "" else $module
    return
        if ($function) then
            mfd:render-function($moduleName, $function)
        else if ($module) then
            mfd:render-module($moduleName)
        else
            mfd:list-modules()
};

<html>
    <head>
        <title>Mobile XQuery Function Docs</title>
        <link rel="stylesheet" href="http://code.jquery.com/mobile/1.0a2/jquery.mobile-1.0a2.min.css" />
        <script type="text/javascript" src="http://code.jquery.com/jquery-1.4.4.min.js"></script>
        <script type="text/javascript" src="http://code.jquery.com/mobile/1.0a2/jquery.mobile-1.0a2.min.js"></script>
    </head>
    <body>
        <div data-role="page" data-theme="b" id="docs-home"> 
            <div data-role="header">  
                <h1>Mobile XQuery Function Docs</h1>  
            </div> 
            
            <div data-role="content">
                { mfd:main() }
            </div>
        </div>
    </body>
</html>