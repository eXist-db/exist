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
            ev.preventDefault();
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

                var span = $('<span class="pagination-back">&lt;&lt;</span>');
                div.append(span);
                if (currentItem <= 1) {
                    span.addClass("inactive");
                } else {
                    span.click(function () {
                        retrievePage(currentItem - 1);
                        return false;
                    });
                }

                span = $('<span class="pagination-next">&gt;&gt;</span>');
                div.append(span);
                if (currentItem >= options.totalItems) {
                    span.addClass("inactive");
                } else {
                    span.click(function () {
                        retrievePage(currentItem + 1);
                        return false;
                    });
                }

                span = $('<span class="pagination-info"></span>');
                span.text('Showing hit ' + currentItem + ' of ' + options.totalItems);
                var listLink = $('<a class="pagination-list-view" href="#">[Switch to list view]</a>');
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
                var pages = Math.ceil(options.totalItems / options.itemsPerPage);
                var currentPage = Math.floor(currentItem / options.itemsPerPage);
                var startPage = currentPage > 2 ? currentPage - 3 : 0;
                var endPage = 0;
                if (currentPage + 3 >= pages)
                    endPage = pages - 1;
                else if (currentPage < 3)
                    endPage = pages > 6 ? 6 : pages - 1;
                else
                    endPage = currentPage + 3;
                
                for (var i = startPage; i <= endPage; i++) {
                    var end = (i * options.itemsPerPage + options.itemsPerPage);
                    if (end > options.totalItems)
                        end = options.totalItems;
                    appendPageLink(div, (i * options.itemsPerPage + 1), end);
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