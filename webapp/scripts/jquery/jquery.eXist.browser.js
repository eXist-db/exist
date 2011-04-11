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
	var classes = ['name', 'permissions', 'owner', 'group', 'lastModified'];
	var head = ['Name', 'Permissions', 'Owner', 'Group', 'Last Modified'];
	
    $.fn.collectionBrowser = function(opts) {
    	var container = this;
    	
    	var collection = this.data("collection");
    	var selection = this.data("selection");
    	
    	$.log("Collection browse");
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
        	$('.collection-view', container).dynatree({
                persist: false,
                rootVisible: false,
                initAjax: { url: "collections.xql" },
                clickFolderMode: 1,
                onActivate: function (dtnode) {
                    var key = dtnode.data.key;
                    var params = { root: key, view: "r" };
                    $.getJSON("collections.xql", params, updateResources);
                    
                    container.data("collection", key);
                },
                onPostInit: function(isReloading, isError) {
                	var prevKey = container.data("collection");
                	var dbNode;
                	if (prevKey) {
                		$.log("prevKey: %s", prevKey);
                		dbNode = this.getNodeByKey(prevKey);
                	} else {
                		dbNode = this.getNodeByKey("/db");
                	}
                	dbNode.activate();
                	dbNode.expand(true);
                }
            });
        	
        	resize();
        }
    	
    	function resize() {
    		$(".collection-view", container).css({position: 'absolute', left: '0', top: '0', 
    			width: Math.floor(container.innerWidth() * 0.4 - 10) + 'px',
    			bottom: '35px', overflow: 'auto'});
    		$(".resource-view", container).css({position: 'absolute', right: '0', top: '0', 
    			width: Math.floor(container.innerWidth() * 0.6 - 10) + 'px',
    			bottom: '35px', overflow: 'auto'});
    		$(".selection", container).css({position: 'absolute', left: '0', bottom: '0', 
    			width: '100%', height: '30px', overflow: 'hidden'});
    		$(".selection input", container).width(container.innerWidth() - 120);
    	}
    	
    	/**
		 * Check if browser supports HTML5 local storage
		 */
		function supportsHtml5Storage() {
			try {
				return 'localStorage' in window && window['localStorage'] !== null;
			} catch (e) {
				return false;
			}
		}
    	
    	function updateResources(data) {
    		var table = $(".resource-view table", container);
            var tbody = $("tbody", table);
            if (tbody.length == 0)
            	tbody = table.append("<tbody/>");
            else
            	tbody.empty();
            
            if (data) {
	            for (var i = 0; i < data.length; i++) {
	            	var style = i % 2 == 0 ? 'even' : 'uneven';
	            	var tr = document.createElement("tr");
	            	tr.className = style;
	            	for (var j = 0; j < head.length; j++) {
	            		var td = document.createElement("td");
	            		td.className = classes[j];
	            		td.appendChild(document.createTextNode(data[i][j]));
	            		tr.appendChild(td);
	            	}
					tbody.append(tr);
					tr.doc = {
							name: data[i][0],
							path: container.data("collection") + "/" + data[i][0],
							writable: data[i][5]
	        		};
					$(tr).click(function () {
						container.data("selection", this.doc);
						$(".selection input", container).val(this.doc.name);
					});
	            }
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
    		else if (opts == 'reload') {
    			var path = container.data("collection");
    			var tree = $('.collection-view', container).dynatree("getTree");
    			tree.reload();
    		} else if (opts == 'selection') {
//    			var selection = container.data("selection");
//    			if (selection)
//    				return selection;
    			var name = $(".selection input", container).val();
    			return {
    				name: name,
    				path: container.data("collection") + "/" + name,
    				writable: true
    			}
    		}
    	}
        return this;
    }
})(jQuery);