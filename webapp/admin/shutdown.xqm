xquery version "1.0";
(: $Id$ :)
(:
    Module: trigger database shutdown.
:)

module namespace shutdown="http://exist-db.org/xquery/admin-interface/shutdown";

declare namespace system="http://exist-db.org/xquery/system";
declare namespace xdb="http://exist-db.org/xquery/xmldb";
declare namespace request="http://exist-db.org/xquery/request";
declare namespace session="http://exist-db.org/xquery/session";

declare function shutdown:main() as element()
{
    <div class="panel">
        <div class="panel-head">Database Shutdown</div>
        {
            if(not(xdb:is-admin-user(xdb:get-current-user()))) then
            (
                <div class="error">
                    Only users of the "dba" group can shutdown the database.
                </div>
            )
            else
            (
                let $shutdown := request:get-parameter("action", ()) return
                    if($shutdown) then
                    (
                        <div class="actions">
                                Database shutdown starts in {request:get-parameter("delay", "2")} sec.
		    { system:shutdown(request:get-parameter("delay", "2") cast as xs:long * 1000) }
                        </div>
                    )
                    else
                    (
                        <form action="{session:encode-url(request:get-uri())}" method="POST">
                            <p>Clicking on the button below will trigger a database shutdown. If
                            you are running the database with the webserver included in the distribution,
                            this will also shut down the webserver. Otherwise, only the database will close
                            down.</p>
                            <table border="0">
                                <tr>
                                    <td><input type="submit" name="action" value="Shutdown"/></td>
                                    <td>Delay in seconds: <input type="text" size="6" name="delay" value="2"/></td>
                                </tr>
                            </table>
                            <input type="hidden" name="panel" value="shutdown"/>
                        </form>
                    )
            )
        }
    </div>
};