xquery version "1.0";
(: $Id$ :)
(:
    Module: display and kill running xqueries
:)

module namespace xqueries="http://exist-db.org/xquery/admin-interface/xqueries";

declare namespace request="http://exist-db.org/xquery/request";
declare namespace xdb="http://exist-db.org/xquery/xmldb";
declare namespace util="http://exist-db.org/xquery/util";
declare namespace system="http://exist-db.org/xquery/system";

import module namespace date="http://exist-db.org/xquery/admin-interface/date" at "dates.xqm";

declare variable $xqueries:MAX-STRING-KEY-LENGTH					:= 1024;

(:
    Main function: outputs the page.
:)
declare function xqueries:main() as element()
{
    <div class="panel">
        { xqueries:process-action() }
        <div class="panel-head">Running XQueries</div>
        <form method="POST" enctype="multipart/form-data">
			<div id="xqueries-container">
            {
                xqueries:display-xqueries()
            }
			</div>
            <input type="submit" name="action" value="Kill Selected"/>
            <input type="hidden" name="panel" value="xqueries"/>
        </form>

		<div class="inner-panel">
			<div class="panel-head">Running Jobs</div>
			<div id="processes-container">
			{
				xqueries:display-processes()
			}
			</div>
			<form>
				<input type="hidden" name="panel" value="xqueries"/>
				<input type="submit" value="Refresh"/>
			</form>
		</div>
    </div>
};

declare function xqueries:display-processes() {
	let $processes := system:get-running-jobs()//system:job
	return
		if (empty($processes)) then
			<p>No long running jobs are active right now.</p>
		else
			<table cellspacing="0" cellpadding="5" width="100%">
				<tr>
					<th width="20%">ID</th>
					<th width="30%">Action</th>
					<th width="30%">Info</th>
					<th width="20%">Running Since</th>
				</tr>
				{
					for $proc in $processes
					return
						xqueries:display-process($proc)
				}
			</table>
};

declare function xqueries:display-process($proc as element(system:job)) {
	<tr>
		<td>{$proc/@id/string()}</td>
		<td>{$proc/@action/string()}</td>
		<td>{$proc/@info/string()}</td>
		<td>{$proc/@start/string()}</td>
	</tr>
};
(:
    Process an action.
:)
declare function xqueries:process-action() as element()*
{
    let $action := request:get-parameter( "action", () ) 
    
    return
        util:catch("java.lang.Exception",
            if( $action eq "Kill Selected" ) then (
                xqueries:kill-xquery()
            ) else(),
            
            <div class="error">
                An error occurred while processing the action:<br/>
                { $util:exception-message }
            </div>
        )
};

(:
    Kill a running XQuery
:)
declare function xqueries:kill-xquery() as element()* 
{
     let $xqueryIDs := request:get-parameter( "xqueryID", () ) 
     
     return
	     for $xqueryID in $xqueryIDs
	     return system:kill-running-xquery( xs:integer( $xqueryID ) )
                       
};

(:
    Display the running xqueries in a table view.
:)
declare function xqueries:display-xqueries() as element()
{
    <table cellspacing="0" cellpadding="5" id="xqueries" width="100%">
        <tr>
            <th/>
            <th>ID</th>
            <th>Type</th>
            <th>Source</th>
            <th>Status</th>
        </tr>
        {
            xqueries:display-xquery()
        }
    </table>
};

declare function xqueries:display-xquery() as element()*
{
    for $xquery in system:get-running-xqueries()//system:xquery
    
    let $id 	:= $xquery/@id
    let $type 	:= $xquery/@sourceType
    let $key 	:= $xquery/system:sourceKey/text()
    let $src    := if( $type != "String" or string-length( $key ) < $xqueries:MAX-STRING-KEY-LENGTH ) then $key else concat( substring( $key, 0, $xqueries:MAX-STRING-KEY-LENGTH - 1 ), "..." )
    let $status := if( $xquery/@terminating = "true" ) then "terminating" else "running"
        
    order by $id 
    return
        <tr>
            <td valign="top"><input type="checkbox" name="xqueryID" value="{ $id }"/></td>
            <td valign="top">{ xs:string( $id ) }</td>
            <td valign="top">{ xs:string( $type ) }</td>
            <td valign="top">{ $src }</td>
            <td valign="top">{ $status }</td>
        </tr>
};
