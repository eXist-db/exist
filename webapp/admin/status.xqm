xquery version "1.0";
(: $Id$ :)
(:
    Module: display status information on the current database instance.
:)

module namespace status = "http://exist-db.org/xquery/admin-interface/status";

declare namespace system = "http://exist-db.org/xquery/system";
declare namespace util = "http://exist-db.org/xquery/util";

declare function status:status-line($key as xs:string, $value as xs:string) as element()
{
    <tr>
        <td class="key">{$key}:</td>
        <td>{$value}</td>
    </tr>
};

declare function status:main() as element()
{
    <div class="panel">
        <div class="panel-head">System Status</div>
        <table id="status" cellpadding="7">
            <tr><th colspan="2">General</th></tr>
            {
                status:status-line("eXist Version", system:get-version()),
                status:status-line("eXist Build", system:get-build()),
                status:status-line("eXist Home", system:get-exist-home()),
                status:status-line("SVN Revision", system:get-revision()),
                status:status-line("Operating System", 
                    concat(util:system-property("os.name"), " ", util:system-property("os.version"),
                        " ", util:system-property("os.arch"))
                ),
                status:status-line("File encoding", util:system-property("file.encoding"))
            }
            <tr><th colspan="2">Java</th></tr>
            {
                status:status-line("Vendor", util:system-property("java.vendor")),
                status:status-line("Version", util:system-property("java.version")),
                status:status-line("Implementation", util:system-property("java.vm.name")),
                status:status-line("Installation", util:system-property("java.home"))
            }
            <tr><th colspan="2">Memory Usage</th></tr>
            {
                    let $max := system:get-memory-max() idiv 1024,
                    $current := system:get-memory-total() idiv 1024,
                    $free := system:get-memory-free() idiv 1024
                return (
                    status:status-line("Max. Memory", concat($max, "K")),
                    status:status-line("Current Total", concat($current, "K")),
                    status:status-line("Free", concat($free, "K"))
                )
            }
        </table>
    </div>
};
