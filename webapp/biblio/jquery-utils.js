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
                    tr.append($(this).addClass('pagination-data'));
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
                    retrievePage(1);
                });
                span.append(listLink);
                div.append(span);
            } else {
                var pages = Math.floor(options.totalItems / options.itemsPerPage);
                for (var i = 0; i < pages; i++) {
                    appendPageLink(div, (i * options.itemsPerPage + 1), (i * options.itemsPerPage + options.itemsPerPage));
                }
                if (pages % options.itemsPerPage > 0) {
                    appendPageLink(div, pages * options.itemsPerPage + 1,
                            pages * options.itemsPerPage + pages % options.itemsPerPage);
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