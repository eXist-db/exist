(function($) {
    $.fn.repeat = function(trigger, opts) {
        var options = $.extend({
            deleteTrigger: null,
            onReady: function() { }
        }, opts || {});
        var container = this;
        var selected = null;

        $('.repeat', container).each(function() {
            addEvent($(this));
        });
        $(trigger).click(function(ev) {
        	ev.preventDefault();
            var last = $('.repeat:last', container);
            var newNode = last.clone();
            last.after(newNode);
            newNode.each(function () {
                $(':input', this).each(function() {
                    var name = $(this).attr('name');
                    var n = /(.*)(\d+)$/.exec(name);
                    $(this).attr('name', n[1] + (Number(n[2]) + 1));
                    if (this.value != '')
                        this.value = '';
                });
            });
            addEvent(newNode);
            $('.repeat', container).removeClass('repeat-selected');
            options.onReady.call(newNode);
        });
        if (options.deleteTrigger != null)
            $(options.deleteTrigger).click(function(ev) {
                deleteCurrent();
                ev.preventDefault();
            });
        function addEvent(repeat) {
            repeat.click(function() {
                selected = repeat;
                $('.repeat', container).removeClass('repeat-selected');
                repeat.addClass('repeat-selected');
            });
        }

        function deleteCurrent() {
            if (selected) {
                selected.remove();
            }
        }
    }
})(jQuery);

/**
 * jQuery pagination plugin. Used by the jquery.xql XQuery library.
 * The passed URL should return an HTML table with one row for each
 * record to display. Each row should have two or more columns: the first one
 * contains the record number, the remaining columns the data to be displayed.
 *
 * @param url the URL to call to retrieve a page.
 * @param opts option object to configure the plugin.
 */
(function($) {
    $.fn.pagination = function(url, opts) {
        var target = this;
        var options = $.extend({
            totalItems: 0,
            itemsPerPage: 10,
            startParam: "start",
            countParam: "count",
            navContainer: null,
            readyCallback: function() { },
            itemCallback: function () { }
        }, opts || {});
        if (options.totalItems == 1)
            options.itemsPerPage = 1;
        if (options.totalItems > 0)
            retrievePage(1);
        return target;

        function displayPage(data) {
            target.empty();
            navbar(target, Number($('tr:first > td:first', data).text()));
            var table = $('<table class="pagination"></table>');
            target.append(table);
            $('> tr', data).each(function (i) {
                var current = $('> td:first', this).text();
                var tr = $('<tr></tr>');
                var td = $('<td class="pagination-n"></td>');
                td.text(current + ".");
                tr.append(td);
                $('> td:gt(0)', this).each(function () {
                    tr.append($(this).clone().addClass('pagination-data'));
                });
                table.append(tr);
                $('.pagination-toggle', tr).click(function (ev) {
                    ev.preventDefault();
                    options.itemsPerPage = 1;
                    retrievePage(current);
                });
                options.itemCallback.call(target, current, tr);
            });
            options.readyCallback.call(target, options);
        }

        function navbar(targetDiv, currentItem) {
            var div;
            if (options.navContainer) {
                div = $(options.navContainer);
            } else {
                div = $('<div></div>');
                targetDiv.append(div);
            }
            div.empty().addClass('pagination-links');
            if (options.itemsPerPage == 1) {

                var span = $('<span class="pagination-first">|&lt;</span>');
                div.append(span);
                if (currentItem <= 1) {
                    span.addClass("inactive");
                } else {
                    span.click(function () {
                        retrievePage(1);
                        return false;
                    });
                }
                
                var span = $('<span class="pagination-previous">&lt;</span>');
                div.append(span);
                if (currentItem <= 1) {
                    span.addClass("inactive");
                } else {
                    span.click(function () {
                        retrievePage(currentItem - 1);
                        return false;
                    });
                }
				
				span = $('<span class="pagination-info"></span>');
                span.text('Record ' + currentItem);
                div.append(span);

                span = $('<span class="pagination-next">&gt;</span>');
                div.append(span);
                if (currentItem >= options.totalItems) {
                    span.addClass("inactive");
                } else {
                    span.click(function () {
                        retrievePage(currentItem + 1);
                        return false;
                    });
                }

                var span = $('<span class="pagination-last">&gt;|</span>');
                div.append(span);
                if (currentItem == options.totalItems) {
                    span.addClass("inactive");
                } else {
                    span.click(function () {
                        retrievePage(options.totalItems);
                        return false;
                    });
                }
                
                span = $('<span class="pagination-info"></span>');
                var listLink = $('<a class="pagination-list-view" href="#">List view</a>');
                listLink.click(function (ev) {
                    ev.preventDefault();
                    options.itemsPerPage = 10;
                    var currentPage = Math.floor(currentItem / options.itemsPerPage);
                    currentItem = currentPage * options.itemsPerPage;
                    if (currentItem == 0)
                        currentItem = 1;
                    retrievePage(currentItem);
                });
                span.append(listLink);
                div.append(span);
            } else {
            
                var span = $('<span class="pagination-first">|&lt;</span>');
                div.append(span);
                if (currentItem <= 1) {
                    span.addClass("inactive");
                } else {
                    span.click(function () {
                        retrievePage(1);
                        return false;
                    });
                }
                
                var span = $('<span class="pagination-previous">&lt;</span>');
                div.append(span);
                if (currentItem <= options.itemsPerPage) {
                    span.addClass("inactive");
                } else {
                    span.click(function () {
                        retrievePage(currentItem - options.itemsPerPage);
                        return false;
                    });
                }
                                
                span = $('<span class="pagination-info"></span>');
                if (options.totalItems == currentItem)
                	recordSpan = ('Record ' + currentItem)
                else if (options.totalItems < (currentItem + options.itemsPerPage - 1))
                	recordSpan = ('Records ' + currentItem + ' to ' + options.totalItems)
                else recordSpan = ('Records ' + currentItem + ' to ' + ((currentItem + options.itemsPerPage - 1)))
                span.text(recordSpan);
                div.append(span);

				span = $('<span class="pagination-next">&gt;</span>');
                div.append(span);
                if (options.totalItems - options.itemsPerPage < currentItem ) {
                    span.addClass("inactive");
                } else {
                    span.click(function () {
                        retrievePage(currentItem + options.itemsPerPage);
                        return false;
                    });
                }

				var span = $('<span class="pagination-last">&gt;|</span>');
                div.append(span);
                if (options.totalItems < (currentItem + options.itemsPerPage - 1)) {
                    span.addClass("inactive");
                } else {
                    span.click(function () {
                        retrievePage(options.totalItems);
                        return false;
                    });
                }

            }
            return div;
        }

        function appendPageLink(div, start, end) {
            var span = $('<span class="pagination-link"></span>');
            span.text(start + '-' + end);
            div.append(span);
            span.click(function () {
                retrievePage(start);
            });
        }

        function retrievePage(start) {
            $.ajax({
                type: "GET",
                url: url,
                data: {
                    start: start,
                    count: options.itemsPerPage
                },
                dataType: "xml",
                success: function(data) { displayPage(data.documentElement); },
                error: function (req, status, errorThrown) {
                    alert(status);
                }
            });
        }
    }

})(jQuery);

(function($) {
    $.fn.form = function(opts) {
    	var options = $.extend({
            done: function() { },
            cancel: function () { }
        }, opts || {});
    	var container = this;
    	var pages = container.find("fieldset");
    	var currentPage = 0;
    	
    	// append back and next buttons to container
    	var panel = document.createElement("div");
    	panel.className = "eXist_wizard_buttons";
    	panel.style.position = "absolute";
    	panel.style.bottom = "0";
    	panel.style.right = "0";
    	
    	var btn = document.createElement("button");
    	btn.className = "eXist_wizard_back";
    	btn.appendChild(document.createTextNode("Back"));
    	panel.appendChild(btn);
    	
    	btn = document.createElement("button");
    	btn.className = "eXist_wizard_next";
    	btn.appendChild(document.createTextNode("Next"));
    	panel.appendChild(btn);
    	
    	btn = document.createElement("button");
    	btn.className = "eXist_wizard_cancel";
    	btn.appendChild(document.createTextNode("Cancel"));
    	panel.appendChild(btn);
    	
    	btn = document.createElement("button");
    	btn.className = "eXist_wizard_done";
    	btn.appendChild(document.createTextNode("Done"));
    	panel.appendChild(btn);
    	container.append(panel);
    	
    	$("button", container).button();
    	
    	for (var i = 1; i < pages.length; i++) {
    		$(pages[i]).css("display", "none");
    	}
    	$(".eXist_wizard_back", container).button("disable");
    	
    	$(".eXist_wizard_next", container).click(function () {
    		if (currentPage == pages.length - 1)
    			return;
    		$(pages[currentPage]).css("display", "none");
    		$(pages[++currentPage]).css("display", "");
    		if (currentPage == 1) {
    			$(".eXist_wizard_back", container).button("enable");
    		} else if (currentPage == pages.length - 1) {
    			$(this).button("disable");
    		}
    	});
    	$(".eXist_wizard_back", container).click(function () {
    		if (currentPage == 0)
    			return;
    		if (currentPage == pages.length - 1) {
    			$(".eXist_wizard_next", container).button("enable");
    		}
    		$(pages[currentPage]).css("display", "none");
    		$(pages[--currentPage]).css("display", "");
    		if (currentPage == 0) {
    			$(this).button("disable");
    		}
    	});
    	$(".eXist_wizard_cancel", container).click(function () {
    		// Cancel.
    		options.cancel.call(container);
    	});
    	$(".eXist_wizard_done", container).click(function () {
    		options.done.call(container);
    	});
    	return container;
    }
})(jQuery);