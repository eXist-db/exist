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

(:~
    Outputs the HTML script and link tags which are required by jQuery.
    You need to call this function once in your HTML head.
    
    <pre><![CDATA[<jquery:header base="scripts/jquery" cssbase="scripts/jquery/css"/>]]></pre>
:)
declare function jquery:header($config as element(jquery:header)) as element()* {
    let $base := $config/@base/string()
    let $cssbase := if ($config/@cssbase) then $config/@cssbase/string() else $base
    return (
        <script type="text/javascript" src="{$base}/jquery-1.4.2.min.js"/>,
        <script type="text/javascript" src="{$base}/jquery-ui-1.8rc3.dev.min.js"/>,
        <script type="text/javascript" src="jquery-utils.js"/>,
        <link rel="stylesheet" type="text/css" href="{$cssbase}/autoSuggest.css"/>,
        <link rel="stylesheet" type="text/css" href="{$cssbase}/smoothness/jquery-ui-1.8rc2.custom.css"/>
    )
};

(:~
    Generates a jQuery UI tabset from the given configuration element.
    
    <pre>
    &lt;jquery:tabset&gt;
        &lt;jquery:tab id="simple" label="Simple Search"&gt;
            Tab content 1
        &lt;/jquery:tab&gt;
        &lt;jquery:tab id="complex" label="Advanced Search"&gt;
            Tab content 2
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
                for $tab in $config/jquery:tab
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
                    <script type="text/javascript">
                    $(function() {{
                        { $options }
                        var tabs = $("#{$id}").tabs(options);
                        { 
                            if ($selectedId) then 
                                concat("tabs.tabs('select', '", $selectedId, "');")
                            else
                                ()
                        }
                    }});
                    </script>
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
    let $onReady := $config/@on-ready/string()
    return
        <script type="text/javascript">
        $(document).ready(function() {{
            $('{$trigger}').click(function(ev) {{
                var last = $('{$formSelector} .repeat:last');
                var newNode = last.clone();
                last.after(newNode);
                newNode.each(function () {{
                    $(':input', this).each(function() {{
                        var name = $(this).attr('name');
                        var n = /(.*)(\d+)$/.exec(name);
                        $(this).attr('name', n[1] + (Number(n[2]) + 1));
                        if (this.value != '')
                            this.value = '';
                    }});
                }});
                {
                    if ($onReady) then
                        concat($onReady, ".call(newNode);")
                    else
                        ()
                }
                ev.preventDefault();
            }});
        }});
        </script>
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
    let $id := util:uuid()
    let $trigger := $config/@trigger/string()
    let $href := $config/@href/string()
    let $callback := $config/@callback/string()
    return (
        <div id="{$id}" class="include-target"></div>,
        <script type="text/javascript">
        $(document).ready(function(ev) {{
            $('#{$id}').css('display', 'none');
            $('{$trigger}').click(function(ev) {{
                if ($(this).hasClass('expanded')) {{
                    $('#{$id}').css('display', 'none');
                }} else {{
                    var trigger = this;
                    $('#{$id}').css('display', 'block').load('{$href}', null, function() {{
                        {
                            if ($callback) then
                                concat($callback, ".call(trigger, '", $id, "');")
                            else
                                ()
                        }
                    }});
                }}
                $(this).toggleClass('expanded');
                ev.preventDefault();
            }});
        }});
        </script>
    )
};

(:~
    <jquery:paginate id="results" attribute="results"
        href="retrieve" navcontainer="#results-head .navbar"/>
:)
declare function jquery:paginate($config as element(jquery:paginate)) as element()+ {
    let $hits := session:get-attribute($config/@attribute)
    let $id := if ($config/@id) then $config/@id else 'jquery-paginate'
    let $onReady := $config/@on-ready/string()
    return (
        <div id="{$id}"/>,
        <script type="text/javascript">
        $(document).ready(function(ev) {{
            $('#{$id/string()}').pagination('{$config/@href/string()}', {{
                    totalItems: {count($hits)},
                    itemsPerPage: 10,
                    navContainer: '{$config/@navcontainer/string()}'
                    { if ($onReady) then (", readyCallback: ", $onReady) else () }
            }});
        }});
        </script>
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
            return
                <script type="text/javascript">
                    $(function() {{
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
                            minLength: 3
                        }});
                    }});
                </script>
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
                { if ($option/@value eq $value) then attribute selected { "selected" } else () }
                { if ($option/text()) then $option/text() else $option/@value/string() }
                </option>
        }
        </select>
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
        case element(jquery:header) return
            jquery:header($node)
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