xquery version "1.0";

import module namespace request="http://exist-db.org/xquery/request";
import module namespace session="http://exist-db.org/xquery/session";
import module namespace util="http://exist-db.org/xquery/util";

declare option exist:serialize "method=xhtml media-type=text/xml";

(: creates the output for all tasks matching the query :)
declare function local:main() as node() * {
    for $task in local:getMatchingTasks()
        return
            <tr>
                <td class="selectorCol"><input type="checkbox" dojotype="dijit.form.CheckBox" value="{$task/created}" /></td>
                <td class="dateCol">{$task/date}</td>
                <td>{$task/project}</td>
                <td>{$task/who}</td>
                <td>{data($task/duration/@hours)}:{data($task/duration/@minutes)}</td>
                <td>{$task/what}</td>
                <td>{$task/note}</td>
                <td>{$task/billable}</td>
                <td>{$task/status}</td>
                <td><a href="javascript:dojo.publish('/task/edit',['{$task/created}']);">edit</a></td>
                <td><a href="javascript:dojo.publish('/task/delete',['{$task/created}']);">delete</a></td>
            </tr>

};

(: fetch all tasks matching the query params passed from the search submission :)
declare function local:getMatchingTasks() as node() * {
    let $from := request:get-parameter("from", "1970-01-01")
    let $to := request:get-parameter("to", "2020-01-01")
    let $project := request:get-parameter("project","")
    let $billable := request:get-parameter("billable","")
    let $billed := request:get-parameter("billed","")

    for $task in collection('/db/betterform/apps/timetracker/data/task')//task
        let $task-date := $task/date
        let $task-project := $task/project
        let $task-billable := $task/billable
        let $task-billed := $task/billed
        let $task-created := $task/created

        let $search := concat("$task-date >= $from and $task-date <= $to",
                        if($project) then " and $task-project=$project" else "",
                        if($billable) then " and $task-billable=$billable" else "",
                        if($billed) then " and $task-billed=$billed" else ""
                        )

        where util:eval($search)
        order by $task-date descending
        return $task

};

(: convert all hours to minutes :)
declare function local:hours-in-minutes($tasks as node()*) as xs:integer
{
  let $sum := sum($tasks//duration/@hours)
  return  $sum * 60
};


(: produces a list of tasks filtered http parameters :)
declare function local:project() as element()?
{
  let $tasks             := local:getMatchingTasks()
  let $hoursInMinutes    := local:hours-in-minutes($tasks)
  let $totalMinutes      := $hoursInMinutes + sum($tasks//duration/@minutes)
  let $totalHours        := $totalMinutes idiv 60
  let $remainingMinutes  := $totalMinutes mod 60
  let $totalTime         := concat($totalHours,':',$remainingMinutes)
  let $days              := $totalHours div 8

  return
    <table id="summary" >
        <tr class="tableHeader">
            <td class="summaryLabel">Days</td>
            <td class="summaryValue">{$days}</td>
            <td class="summaryLabel">Total Time</td>
            <td class="summaryValue">{$totalTime}</td>
        </tr>
   </table>
};

let $contextPath := request:get-context-path()
return
<html   xmlns="http://www.w3.org/1999/xhtml"
        xmlns:ev="http://www.w3.org/2001/xml-events">
   <head>
      <title>All Tasks</title>
      <link rel="stylesheet" type="text/css"
                href="{$contextPath}/rest/db/betterform/apps/timetracker/resources/timetracker.css"/>

    </head>
    <body>
    	<div id="dataTable">
    	   <div id="checkBoxSelectors">
    	        Select: <a href="javascript:selectAll();">All</a> | <a href="javascript:selectNone();">None</a>
    	        <!--<button onclick="passValuesToXForms();" value="setSelected"/>-->
    	   </div>
		   <table id="taskTable">
			 <tr>
				<th></th>
				<th>Date</th>
				<th>Project</th>
				<th>Who</th>
				<th>Duration</th>
				<th>What</th>
				<th>Note</th>
				<th>Billable</th>
				<th>Status</th>
				<th colspan="2"> </th>
			 </tr>
			 {local:main()}
		 </table>
       {local:project()}
	 </div>
    </body>
</html>
