function load() {
	$("#collectionsTree").dynatree({
		title: "modules",
		rootVisible: true,
		fx: { height: "toggle", duration: 200 },
//		clickFolderMode: 2,

		initAjax: {
			url: "script.xql",
			data: {
				action: "showSubCollections",
				collection: "modules"
			},
			
			success: function(dtnode) {
				$.get("script.xql", {action:"showCollectionResources", collection: "modules"},
					function(data){
						$("#resourcesTable").html(data);
						renderToDataTable();
					}, "text");
			}
		},

		onActivate: function(dtnode) {
			dtnode.appendAjax({
				url: "script.xql",
				data: {
					action: "showSubCollections",
					collection: dtnode.data.key
				},
				
				success: function(dtnode) {
					$.get("script.xql", {action:"showCollectionResources", collection: dtnode.data.key},
					function(data){
						$("#resourcesTable").html(data);
						renderToDataTable();
					}, "text");
				}
			});
		},

		onLazyRead: function(dtnode){
			dtnode.appendAjax({
				url: "script.xql",
				data: {
					action: "showSubCollections",
					collection: dtnode.data.key
				},
				
				success: function(dtnode) {
					$.get("script.xql", {action:"showCollectionResources", collection: dtnode.data.key},
					function(data){
						$("#resourcesTable").html(data);
						renderToDataTable();
					}, "text");
				}
			});
		}
	});
}

function renderToDataTable() {
	var oTable;
	var giRedraw = false;
	
	oTable = $('#dataTable').dataTable({
		"bPaginate": false,
		"bLengthChange": false,
		"bFilter": true,
		"bSort": true,
		"bInfo": false,
		"bAutoWidth": false
	});
	
	$("#dataTable tbody").click(function(event) {
		$(oTable.fnSettings().aoData).each(function (){
			$(this.nTr).removeClass('row_selected');
		});
		$(event.target.parentNode).addClass('row_selected');
	});
}
