(:~
    jQuery module: a set of functions to extend HTML forms with AJAX
    functionality, based on the jQuery javascript library.
    
    Use: each function takes an XML configuration element as parameter.
    You can either call functions directly from another XQuery module
    or pass a template page to the jquery:process-templates function.
    jquery:process-templates will parse the template page and expand all
    jquery:* elements it knows.
    
:)
module namespace jquery="http://exist-db.org/xquery/jquery";

import module namespace util="http://exist-db.org/xquery/util";
import module namespace request="http://exist-db.org/xquery/request";
import module namespace session="http://exist-db.org/xquery/session";

declare function jquery:script($node as element()?) as xs:string? {
    string-join($node/node(), "")
};

(:~
    Outputs the HTML script and link tags which are required by jQuery.
    You need to call this function once in your HTML head.
    
    <pre><![CDATA[<jquery:header base="scripts/jquery" cssbase="scripts/jquery/css"/>]]></pre>
:)
declare function jquery:header($config as element(jquery:header)) as element()* {
    let $base := $config/@base/string()
    let $cssbase := if ($config/@cssbase) then $config/@cssbase/string() else $base
    let $hasSource := root($config)//jquery:source
    return (
        <script type="text/javascript" src="{$base}/jquery-1.7.1.min.js"></script>,
        <script type="text/javascript" src="{$base}/jquery-ui-1.8.custom.min.js"></script>,
        <script type="text/javascript" src="{$base}/jquery-utils.js"></script>,
        <link rel="stylesheet" type="text/css" href="{$cssbase}/smoothness/jquery.ui.all.css"/>,
        if ($hasSource) then jquery:syntax-highlighting-libs($base, $cssbase) else ()
    )
};

declare function jquery:syntax-highlighting-libs($base as xs:string, $cssbase as xs:string) {
    <script type="text/javascript" src="{$base}/../syntax/shCore.js"></script>,
    <script type="text/javascript" src="{$base}/../syntax/shBrushCss.js"></script>,
    <script type="text/javascript" src="{$base}/../syntax/shBrushJScript.js"></script>,
    <script type="text/javascript" src="{$base}/../syntax/shBrushPlain.js"></script>,
    <script type="text/javascript" src="{$base}/../syntax/shBrushXml.js"></script>,
    <script type="text/javascript" src="{$base}/../syntax/shBrushXQuery.js"></script>,
    <link type="text/css" rel="stylesheet" href="{$base}/../../styles/syntax/shCore.css" />,
    <link type="text/css" rel="Stylesheet" href="{$base}/../../styles/syntax/shThemeDefault.css" id="theme" />,
    <jquery:script>
        SyntaxHighlighter.config.stripBrs = true;
        SyntaxHighlighter.defaults[ 'auto-links'] = false;
        SyntaxHighlighter.defaults[ 'wrap-lines'] = true;
        SyntaxHighlighter.all();
    </jquery:script>
};

(:~
    Generates a jQuery UI tabset from the given configuration element.
    
    <pre>
    &lt;jquery:tabset&gt;
        &lt;jquery:tab id="simple" label="Simple Search"&gt;
            Tab content 1
        &lt;/jquery:tab&gt;
        &lt;jquery:tab id="complex" label="Advanced Search"&gt;
            Tab content 
        &lt;/jquery:tab&gt;
    &lt;/jquery:tabset&gt;
    </pre>
:)
declare function jquery:tabset($config as element(jquery:tabset)) as element() {
    let $id := if ($config/@id) then $config/@id/string() else util:uuid()
    let $selected := request:get-parameter($id, ())
    let $selectedId := 
        if ($selected) then $config/jquery:tab[@id = $selected]/@id/string() else ()
    return
        <div id="{$id}">
            <ul>
            {
                for $tab in $config/jquery:tab
                return
                    if ($tab/@href) then
                        <li><a href="{$tab/@href}">{$tab/@label/string()}</a></li>
                    else
                        <li><a href="#{$tab/@id}">{$tab/@label/string()}</a></li>
            }
            </ul>
            {
                for $tab in $config/jquery:tab[@id]
                return
                    <div id="{$tab/@id}">
                    {
                        for $child in $tab/node()
                        return
                            jquery:process-templates($child)
                    }
                    </div>
            }
            {
                let $options :=
                    if ($config/@on-select) then
                        concat("var options = { select: ", $config/@on-select, " };")
                    else
                        "var options = null;"
                return
                    <jquery:script>
                        { $options }
                        var tabs = $("#{$id}").tabs(options);
                        { 
                            if ($selectedId) then 
                                concat("tabs.tabs('select', '", $selectedId, "');")
                            else
                                ()
                        }
                    </jquery:script>
            }
        </div>
};

(:~
    Creates a repeatable set of form fields. Clicking the trigger will
    clone a given part of the form. The new element is inserted immediately
    after the cloned element.
    
    The element to be cloned should have a class 'repeat'. It can be an arbitrary
    HTML node. Form fields within the repeated element should have a name attribute
    ending with a number (usually 1), e.g. "input1", "phone1". After inserting a new 
    clone, the script will change the names for all form elements in the inserted
    element. So "phone1" will become "phone2" and so on.
    
    The function expects a configuration element jquery:form-repeat as parameter.
    Two attributes should be specified:
    
    <ul>
       <li>form: a jQuery selector pointing to the HTML form element</li>
       <li>trigger: a jQuery selector pointing to the HTML element on which
       the user should click to insert a new row into the form</li>
    </ul>
    
    Example:
    <pre>&lt;jquery:form-repeat trigger="#add-field" form="#search-form"/&gt;</pre>
    
    @param $config the jquery:form-repeat configuration element
:)
declare function jquery:form-repeat($config as element(jquery:form-repeat)) as element() {
    let $formSelector := $config/@form/string()
    let $trigger := $config/@trigger/string()
    let $delete := $config/@delete/string()
    let $onReady := $config/@on-ready/string()
    return
        <jquery:script>
            var options = {{
                deleteTrigger: '{$delete}',
                onReady: { if ($onReady) then $onReady else 'new function() {}' }
            }};
            $('{$formSelector}').repeat('{$trigger}', options);
        </jquery:script>
};

(:~
    Toggles an HTML block upon the click on a link or button.
    The contents of the HTML block are retrieved via AJAX from
    the URL given in attribute "href".
    
    <pre><![CDATA[
        <jquery:toggle href="filters.xql?type=date"
            trigger="#filters li:nth-child(3) .expand"/>
    ]]></pre>
    
    <ul>
        <li>href: the URL to load the HTML content block from</li>
        <li>trigger: jQuery selector pointing to the trigger element
            on which the user should click</li>
        <li>callback: optional callback javascript function which will
            be called once the contents of the toggle block are loaded.
            The function receives the trigger element as first parameter,
            the id of the toggle block as second.</li>
    </ul>
:)
declare function jquery:toggle($config as element(jquery:toggle)) as element()+ {
    let $id0 := $config/@id/string()
    let $id := if ($id0) then $id0 else util:uuid()
    let $trigger := $config/@trigger/string()
    let $href := $config/@href/string()
    let $callback := $config/@callback/string()
    let $beforeCallback := $config/@beforeCallback/string()
    return (
        if (empty($id0)) then
            <div id="{$id}" class="include-target"></div>
        else (),
        <jquery:script>
            $('#{$id}').css('display', 'none');
            $('{$trigger}').click(function(ev) {{
                if ($(this).hasClass('expanded')) {{
                    $('#{$id}').css('display', 'none');
                }} else {{
                    var trigger = this;
                    {
                        if ($beforeCallback) then
                            jquery:script(<s>
                                var r = {$beforeCallback}.call(trigger, '{$id}');
                                if (!r) return false;
                            </s>)
                        else
                            ()
                    }
                    $('#{$id}').css('display', 'block');
                    {
                        if ($href) then
                            jquery:script(<s>$('#{$id}').load('{$href}', null, function() {{
                                {
                                    if ($callback) then
                                        concat($callback, ".call(trigger, '", $id, "');")
                                    else
                                        ()
                                }
                            }});</s>)
                        else
                            ()
                    }
                }}
                $(this).toggleClass('expanded');
                ev.preventDefault();
            }});
        </jquery:script>
    )
};

(:~
    <jquery:paginate id="results" attribute="results"
        href="retrieve" navcontainer="#results-head .navbar"/>
:)
declare function jquery:paginate($config as element(jquery:paginate)) as element()+ {
    let $hits := session:get-attribute($config/@attribute)
    let $id := if ($config/@id) then $config/@id/string() else util:uuid()
    let $onReady := $config/@on-ready/string()
    return (
        <div id="{$id}"/>,
        <jquery:script>
            $('#{$id}').pagination('{$config/@href/string()}', {{
                    totalItems: {count($hits)},
                    itemsPerPage: 10,
                    navContainer: '{$config/@navcontainer/string()}'
                    { if ($onReady) then (", readyCallback: ", $onReady) else () }
            }});
        </jquery:script>
    )
};

(:~
    Helper function for jquery:input-field()
:)
declare function jquery:extra-params($id as xs:string, $auto as element(jquery:autocomplete)) {
    if ($auto/jquery:param) then
        string-join(
            for $param in $auto/jquery:param
            return
                concat($param/@name, ': function () { ', 
                    " return ", $param/@function, '.call($("#', $id, '"));}'),
            ', '
        )
    else
        ()
};

(:~
    Create an HTML input element. Copies all attributes of the jquery:input
    element. If no value attribute is present, the function will look up the
    parameter in the HTTP request, and if the parameter is set, add it as value 
    attribute.
    
    <pre><![CDATA[<jquery:input name="input1"/>]]></pre>
:)
declare function jquery:input-field($node as element(jquery:input)) as element()+ {
    let $name := $node/@name/string()
    let $id := if ($node/@id) then $node/@id/string() else util:uuid()
    return (
        if ($node/jquery:autocomplete) then
            let $auto := $node/jquery:autocomplete
            let $width := if ($auto/@width) then $auto/@width/string() else 200
            let $multiple := if ($auto/@multiple) then $auto/@multiple/string() else "false"
            let $matchContains := if ($auto/@matchContains) then $auto/@matchContains/string() else "false"
            let $minLength := if ($auto/@minLength) then $auto/@minLength/string() else "3"
            return
                <jquery:script>
                        $('#{$id}').autocomplete({{
                            source: function(request, response) {{
                                var data = {{ term: request.term }};
                                { 
                                    for $cb in $auto/@paramsCallback
                                    return
                                        concat($cb, '($("#', $id, '"), data);')
                                }
                                $.ajax({{
                                    url: "{$auto/@url/string()}",
                                    dataType: "json",
                   					data: data,
                                    success: function(data) {{
                                        response(data);
                                    }}
                                }});
                            }},
                            minLength: { $minLength },
                            delay: 700
                        }});
                </jquery:script>
        else
            (),
        <input>
        { $node/@* }
        { if (empty($node/@id)) then attribute id { $id } else () }
        {
            if (empty($node/@value)) then
                attribute value { request:get-parameter($name, "") }
            else
                ()
        }
        </input>
    )
};

(:~
    { jquery:extra-params($id, $auto) }
                        width: { $width },
                        minChars: 3
    Create an HTML select element. Copies all attributes of the jquery:select
    element. jquery:select should contain zero or more jquery:option elements
    which are transformed into HTML option elements. Each option must have a
    value attribute and may have an optional text content. Both are copied to
    the generated HTML option.
    
    For each option, the HTTP request is checked for a corresponding parameter
    value. If present, the option will be selected.
    
    <pre><![CDATA[
        <jquery:select name="sort">
            <jquery:option value="Author"/>
            <jquery:option value="Title"/>
            <jquery:option value="Date Issued"/>
        </jquery:select>
    ]]></pre>
:)
declare function jquery:select-field($node as element(jquery:select)) as element() {
    let $name := $node/@name/string()
    let $value := request:get-parameter($name, "")
    return
        <select>
        { $node/@* }
        {
            for $option in $node/jquery:option
            return
                <option>
                { $option/@value }
                { if ($option/@value eq $value) then attribute selected { "selected" } else () }
                { if ($option/text()) then $option/text() else $option/@value/string() }
                </option>
        }
        </select>
};

declare function jquery:button($node as element(jquery:button)) as element()+ {
    let $id := if ($node/@id) then $node/@id/string() else util:uuid()
    return (
        <button id="{$id}">{$node/@*[not(local-name(.) = 'id')], $node/node()}</button>,
        <jquery:script>
        $('#{$id}').button();
        </jquery:script>
    )
};

declare function jquery:accordion($config as element(jquery:ajax-accordion)) as element()+ {
    let $id := if ($config/@id) then $config/@id/string() else concat('N', util:uuid())
    let $panels :=
        for $panel in $config/jquery:panel
        let $panelId := if ($panel/@id) then $panel/@id/string() else concat('N', util:uuid()) 
        return
            <div>
                <h3 id="{$panelId}"><a href="#">{$panel/@title/string()}</a></h3>
                <div>
                {
                    if ($panel/node()) then
                        for $child in $panel/node() return
                            jquery:process-templates($child)
                    else
                        <p>Loading ...</p>
                }
                </div>
            </div>
    return (
        <div id="{$id}">
        {   $config/@class, $panels/* }
        </div>,
        <jquery:script>
            var actions = {{}};
            {
                for $panel at $p in $config/jquery:panel
                return
                    if ($panel/@href) then
                        jquery:script(<s>actions["{$panels[$p]/h3/@id/string()}"] = "{$panel/@href/string()}";</s>)
                    else if ($panel/@on-select) then
                        jquery:script(<s>actions["{$panels[$p]/h3/@id/string()}"] = {$panel/@on-select/string()};</s>)
                    else ()
            }
            $('#{$id}').accordion({{
                autoHeight: { if ($config/@autoHeight eq "true") then "true" else "false" },
                fillSpace: { if ($config/@fillSpace eq "true") then "true" else "false" },
                collapsible: true,
                active: { if ($config/@active eq "true") then "true" else "false" },
                change: function (event, ui) {{
                    var id = ui.newHeader.attr('id');
                    var panel = ui.newHeader.next();
                    var action = actions[id];
                    if (typeof action == "function")
                        action.call();
                    else
                        panel.load(action);
                }}
            }});
        </jquery:script>
    )
};

declare function jquery:dialog($config as element(jquery:dialog)) as node()* {
    let $trigger := $config/@trigger
    let $id := $config/@id
    let $modal := $config/@modal = 'true'
    let $buttons :=
        string-join(
            for $button in $config/jquery:button
            return
                if ($button/@id eq 'cancel') then
                    string-join(('"', $button/@label, '": function() { $(this).dialog("close"); }'), "")
                else if ($button/@id eq 'submit') then
                    string-join(('"', $button/@label, '": function() { $("#', $id/string(), ' form").submit(); }'), "")
                else if ($button/@function) then
                    string-join(('"', $button/@label, '": function () { ', $button/@function, '($(this)); }'), "")
                else
                    string-join(('"', $button/@label, '": function() {', $button/node(), '}'), "")
            , ", ")
    return
        <div>
            { $id, for $child in $config/*[not(self::jquery:*)] return jquery:process-templates($child) }
            <jquery:script>
                    $('#{$id/string()}').dialog({{
                        modal: { if ($modal) then 'true' else 'false' },
                        autoOpen: false,
                        {
                            if($config/@onOpen)then(
                                fn:concat("open: function(event, ui){ ",  string($config/@onOpen), "(); },")
                            )else()
                        }
                        buttons: {{ { $buttons } }}
                        {
                            let $attribs :=
                                for $attr in $config/@*
                                return
                                    if ($attr/local-name() = ("height", "minHeight", "maxHeight", 
                                        "minWidth", "maxWidth", "width")) then
                                        concat($attr/local-name(), ": ", $attr/string())
                                    else if ($attr/local-name() = ("title", "position", 
                                        "dialogClass", "closeText")) then
                                        concat($attr/local-name(), ': "', $attr/string(), '"')
                                    else
                                        ()
                            return
                                if (exists($attribs)) then
                                    concat(", ", string-join($attribs, ", "))
                                else
                                    ()
                        }
                    }});
            </jquery:script>
            {
                if($trigger)then
                (
                    <jquery:script>
                        var trigger = '{$trigger/string()}';
                        if (trigger != '')
                            $(trigger).click(function() {{
                                $('#{$id/string()}').dialog('open');
                                return false;
                            }});
                    </jquery:script>
                )else()
            }
        </div>
};

declare function jquery:escape-xml($node as node()) as xs:string* {
    typeswitch ($node)
        case element() return
            string-join(
                (
                    "&lt;", node-name($node),
                    for $attr in $node/@* return concat(" ", node-name($attr), '="', $attr/string(), '"'),
                    if ($node/node()) then
                        ("&gt;", for $child in $node/node() return jquery:escape-xml($child), "&lt;/", node-name($node), "&gt;")
                    else
                        "/&gt;"
                ),
                ""
            )
        default return
            $node
};

declare function jquery:source($node as node()) as node()* {
    let $id := $node/@ref
    let $code := root($node)//*[@id = $id][1]
    return
        <div class="programlisting">
            <pre class="brush: xml;">
            { jquery:escape-xml($code) }
            </pre>
        </div>
};

(:~
    Main function to process an HTML template. All jquery:* elements
    in the template are expanded, if known.
:)
declare function jquery:process-templates($node as node()) as node()* {
    typeswitch ($node)
        case element(jquery:form-repeat) return
            jquery:form-repeat($node)
        case element(jquery:tabset) return
            jquery:tabset($node)
        case element(jquery:toggle) return
            jquery:toggle($node)
        case element(jquery:paginate) return
            jquery:paginate($node)
        case element(jquery:input) return
            jquery:input-field($node)
        case element(jquery:select) return
            jquery:select-field($node)
        case element(jquery:button) return
            jquery:button($node)
        case element(jquery:ajax-accordion) return
            jquery:accordion($node)
        case element(jquery:dialog) return
            jquery:dialog($node)
        case element(jquery:header) return
            jquery:header($node)
        case element(jquery:source) return
            jquery:source($node)
        case element() return
            element { node-name($node) } {
                $node/@*,
                for $child in $node/node()
                return
                    jquery:process-templates($child)
            }
        default return
            $node
};

declare function jquery:process($node as node()) as node()* {
     jquery:cleanup-javascript( 
        jquery:process-templates($node)
    )
};

declare function jquery:cleanup-javascript($node as node(), $scripts as element()*) {
    typeswitch ($node)
        case element(body) return
            <body>{
                $node/@*, 
                for $child in $node/node() return jquery:cleanup-javascript($child, $scripts),
                <script type="text/javascript">
                    /*
                    * Generated code
                    * by jQuery XQuery module
                    */
                    $(function() {{
                      { $scripts/text() }
                    }});
                </script>
            }</body>
        case element(jquery:script) return
            ()
        case element() return
            element { node-name($node) } {
                $node/@*,
                for $child in $node/node() return jquery:cleanup-javascript($child, $scripts)
            }
        default return
            $node
};

declare function jquery:cleanup-javascript($root as element()) {
    let $scripts := $root//jquery:script
    return
        jquery:cleanup-javascript($root, $scripts)
};