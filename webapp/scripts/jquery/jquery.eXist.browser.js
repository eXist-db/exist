/**
 * jQuery plugin for eXist: displays a collection browser (collection tree to the left,
 * a table of resources to the right.
 * 
 * Usage:
 * 
 * $("#collection-browser").collectionBrowser();
 * 
 * Transforms the given element into a collection browser.
 *  
 * var path = $("#collection-browser").collectionBrowser("selection");
 * 
 * Retrieves the currently selected path.
 */
(function($) {
	var classes = ['name', 'permissions', 'owner', 'group', 'created', 'lastModified'];
	var head = ['Name', 'Permissions', 'Owner', 'Group', 'Created', 'Last Modified'];
	
    $.fn.collectionBrowser = function(opts) {
    	var container = this;
    	
    	function init() {
    		container.css('position', 'relative');
    		container.html('<div class="collection-view"></div>' +
    			'<div class="resource-view"><table></table></div>' +
    			'<div class="selection">Resource: <input type="text" name="resource"/></div>');
    		
    		var headers = '<thead><tr>';
    		for (var i = 0; i < head.length; i++) {
    			headers += '<th>' + head[i] + '</th>';
    		}
    		headers += '</tr></thead>';
    		$(".resource-view table").html(headers);
    		$(".resource-view", container).data('collection', "/db");
        	$('.collection-view', container).dynatree({
                persist: false,
                rootVisible: false,
                initAjax: {url: "collections.xql" },
                clickFolderMode: 1,
                onActivate: function (dtnode) {
                    var key = dtnode.data.key;
                    var params = { root: key, view: "r" };
                    $.getJSON("collections.xql", params, updateResources);
                    $(".resource-view", container).data('collection', key);
                },
                onPostInit: function(isReloading, isError) {
                	var dbNode = this.getNodeByKey("/db");
                	dbNode.activate();
                	dbNode.expand(true);
                }
            });
        	resize();
//        	$(window).resize(resize);
        }
    	
    	function resize() {
    		$(".collection-view", container).css({position: 'absolute', left: '0', top: '0', 
    			width: Math.floor(container.innerWidth() * 0.25 - 10) + 'px',
    			bottom: '35px', overflow: 'auto'});
    		$(".resource-view", container).css({position: 'absolute', right: '0', top: '0', 
    			width: Math.floor(container.innerWidth() * 0.75 - 10) + 'px',
    			bottom: '35px', overflow: 'auto'});
    		$(".selection", container).css({position: 'absolute', left: '0', bottom: '0', 
    			width: '100%', height: '30px', overflow: 'hidden'});
    		$(".selection input", container).width(container.innerWidth() - 120);
    	}
    	
    	function updateResources(data) {
    		var collection = $(".resource-view", container).data("collection");
    		var table = $(".resource-view table", container);
            var tbody = $("tbody", table);
            if (tbody.length == 0)
            	tbody = table.append("<tbody/>");
            else
            	tbody.empty();
            
            for (var i = 0; i < data.length; i++) {
            	var style = i % 2 == 0 ? 'even' : 'uneven';
            	var tr = document.createElement("tr");
            	tr.className = style;
            	for (var j = 0; j < data[i].length; j++) {
            		var td = document.createElement("td");
            		td.className = classes[j];
            		td.appendChild(document.createTextNode(data[i][j]));
            		tr.appendChild(td);
            	}
				tbody.append(tr);
				$(tr).click(function () {
					var resource = $("td:first", this).text();
					$(".selection input", container).val(resource);
				});
            }
    	}
    	
    	if (opts == null || typeof(opts) == 'object') {
	        var options = $.extend({
	        	type: "open",
	            readyCallback: function() { },
	            itemCallback: function () { }
	        }, opts || {});
	        init();
    	} else {
    		if (opts == 'resize')
    			resize();
    		else if (opts == 'selection') {
    			var collection = $(".resource-view", container).data('collection');
    			return collection + "/" + $(".selection input", container).val();
    		}
    	}
        return this;
    }
})(jQuery);