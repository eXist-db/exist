xquery version "1.0";

declare option exist:serialize "method=html media-type=text/html";
let $contextPath := request:get-context-path()
return
<html xmlns="http://www.w3.org/1999/xhtml"
      xmlns:xf="http://www.w3.org/2002/xforms">
    <head>
        <title>Dojo DnD: simple drag handles</title>
        <style type="text/css">
            @import "http://ajax.googleapis.com/ajax/libs/dojo/1.4/dojo/resources/dojo.css";
			@import "http://ajax.googleapis.com/ajax/libs/dojo/1.4/dojox/grid/resources/Grid.css";
			@import "http://ajax.googleapis.com/ajax/libs/dojo/1.4/dojox/grid/resources/tundraGrid.css";

        </style>
        
        <script type="text/javascript" src="http://ajax.googleapis.com/ajax/libs/dojo/1.4/dojo/dojo.xd.js"
                djConfig="parseOnLoad: true"></script>
        <script type="text/javascript">
			<!--
                dojo.require("dojox.grid.DataGrid");
                dojo.require("dojox.data.XmlStore");

					var layoutTasks = [
					[{
						field: "date",
						name: "Date",
						width: 10,
						formatter: function(item) {
							return item.toString();
						}
					},
					{
						field: "project",
						name: "Project",
						width: 10,
						formatter: function(item) {
							return item.toString();
						}
					},
					{
						field: "hours",
						name: "Hours",
						width: 10,
						formatter: function(item) {
							return item.toString();
						}
					},
					{
						field: "minutes",
						name: "Minutes",
						width: 10,
						formatter: function(item) {
							return item.toString();
						}
					},
					{
						field: "who",
						name: "Who",
						width: 10,
						formatter: function(item) {
							return item.toString();
						}
					},
					{
						field: "what",
						name: "Description",
						width: 10,
						formatter: function(item) {
							return item.toString();
						}
					},
					{
						field: "status",
						name: "Status",
						width: 10,
						formatter: function(item) {
							return item.toString();
						}
					}
					]];

			function openTask(e){
				var item = e.grid.getItem(e.rowIndex);
				alert(taskStore.getValue(item,"created"));
			}


		   -->
        </script>
    </head>
    <body class="tundra">

        <div id="pagecontent">
            <div id="wrapper">
                <div dojoType="dojox.data.XmlStore"
                     url="{$contextPath}/rest/db/betterform/apps/timetracker/data/task?_query=//task&amp;_howmany=5&amp;_xsl=/db/betterform/apps/timetracker/views/flattenAttributes.xsl"
                     jsId="taskStore"
                     label="title"
                     attributeMap="{{'duration.@hours':'@hours'}}">
                </div>
                <div id="grid"
                     style="width: 100%; height: 100%;"
                     dojoType="dojox.grid.DataGrid"
                     store="taskStore"
                     structure="layoutTasks"
                     query="{{}}"
                     rowsPerPage="40"
                     rowSelector="20px">
                    <script type="dojo/method" event="onRowDblClick" args="e">
                        openTask(e);
                    </script>
                </div>

            </div>
        </div>
    </body>
</html> 
