module namespace status="http://exist-db.org/xquery/admin-interface/status";

declare namespace util="http://exist-db.org/xquery/util";
declare namespace rt="java:java.lang.Runtime";

declare function status:status-line($key as xs:string, $value as xs:string) as element() {
    <tr>
        <td class="key">{$key}:</td>
        <td>{$value}</td>
    </tr>
};

declare function status:main() as element() {
    <div class="panel">
        <div class="panel-head">System Status</div>
        <table id="status" cellpadding="7">
            <tr><th colspan="2">General</th></tr>
            {
                status:status-line("eXist Version", util:system-property("product-version")),
                status:status-line("eXist Build", util:system-property("product-build")),
                status:status-line("Java Vendor", util:system-property("java.vendor")),
                status:status-line("Java Version", util:system-property("java.version")),
                status:status-line("Operating System", 
                    concat(util:system-property("os.name"), " ", util:system-property("os.version"),
                        " ", util:system-property("os.arch"))
                )
            }
            <tr><th colspan="2">Memory Usage</th></tr>
            {
                let $rt := rt:getRuntime(),
                    $max := rt:maxMemory($rt) idiv 1024,
                    $current := rt:totalMemory($rt) idiv 1024,
                    $free := rt:freeMemory($rt) idiv 1024
                return (
                    status:status-line("Max. Memory", concat($max, "K")),
                    status:status-line("Current Total", concat($current, "K")),
                    status:status-line("Free", concat($free, "K"))
                )
            }
        </table>
    </div>
};
