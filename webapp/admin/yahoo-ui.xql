module namespace yui="http://exist-db.org/xquery/yui";

declare function yui:generate-tabs($config as element(tabset), $ids as xs:string*) {
    <ul class="yui-nav">
    {
        let $select := if ($config/@selected) then number($config/@selected) else 1
        for $tab at $n in $config/tab
        return
            <li>
                { if ($n eq $select) then attribute class { "selected" } else () }
                <a href="#{$ids[$n]}"><em>{$tab/@label/string()}</em></a>
            </li>
    }
    </ul>
};

declare function yui:generate-content($config as element(tabset), $ids as xs:string*) {
    <div class="yui-content">
    {
        for $tab at $n in $config/tab
        return
            <div id="{$ids[$n]}">{$tab/node()}</div>
    }
    </div>
};

declare function yui:generate-ids($config as element(tabset)) as xs:string* {
    let $uuid := util:uuid()
    for $tab at $n in $config/tab
    return
        concat($uuid, $n)
};

declare function yui:tabset($config as element(tabset)) {
    let $id := if ($config/@id) then $config/@id else util:uuid()
    let $ids := yui:generate-ids($config)
    return (
        <div id="{$id}" class="yui-navset">
        { yui:generate-tabs($config, $ids), yui:generate-content($config, $ids) }
        </div>,
        <script type="text/javascript">
            { if ($config/@var) then concat("var ", $config/@var/string(), " = ") else () }
            new YAHOO.widget.TabView("{$id}");
        </script>
    )
};