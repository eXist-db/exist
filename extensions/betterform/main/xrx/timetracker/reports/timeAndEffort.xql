xquery version "1.0";

import module namespace request="http://exist-db.org/xquery/request";
import module namespace session="http://exist-db.org/xquery/session";
import module namespace util="http://exist-db.org/xquery/util";

declare option exist:serialize "method=html media-type=text/html";

(: fetch all tasks selected by the user :)
declare function local:getSelectedTasks()  as node() * {
    (: get the list of created keys that we get posted from the user :)
    let $post-data := tokenize(request:get-data(),'\s')

    for $created in $post-data
        let $task := collection('/db/betterform/apps/timetracker/data/task')//task[created=$created]
        return
        <tr>
            <td>{$task/date}</td>
            <td>{$task/what}</td>
            <td>{data($task/duration/@hours)}:{data($task/duration/@minutes)}</td>
            <td>{$task/note}</td>
        </tr>


};

<html>
   <head>
      <title>Time and Effort</title>
    </head>
    <body>
    	<div id="dataTable" style="display:block;">
    	    <table>
    	        <thead>
    	            <th>Date</th>
                    <th>What</th>
                    <th>Duration</th>
                    <th>Note</th>
    	        </thead>
    	        <tbody>
            	{local:getSelectedTasks()}
            	</tbody>
    	    </table>
	    </div>
    </body>
</html>
