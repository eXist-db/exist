var Dom = YAHOO.util.Dom,
    Event = YAHOO.util.Event;

/**
 * Singleton class to search the XQuery function documentation.
 */
var DocQuery = function () {

    var form;
    var results;
    var timer = null;

    /**
     * Queries are triggered by typing into the input box
     */
    this.keyHandler = function () {
        if (timer) clearTimeout(timer);
        var self = this;
        timer = setTimeout(function () { self.autoQuery() }, 500);
    }

    /**
     * Received a key event, check if we should send a query.
     */
    this.autoQuery = function () {
        if (form.elements['q'].value.length > 1)
            this.doQuery();
        else
            results.innerHTML = '';
    }

    /**
     * Send query request to the server.
     * @param ev
     */
    this.doQuery = function (ev) {
        if (ev) Event.stopEvent(ev);
        results.innerHTML = '';

        var query = form.elements['q'].value;
        var callback = {
            success: this.queryResult,
            failure: function () {
                alert('An unknown error occurred while querying the server.');
                Dom.setStyle('f-loading', 'visibility', 'hidden');
            },
            scope: this
        }
        Dom.setStyle('f-loading', 'visibility', '');
        YAHOO.util.Connect.asyncRequest('POST', '#', callback, 'mode=ajax&q=' + query);
    }

    /**
     * Handle query results.
     */
    this.queryResult = function (response) {
        Dom.setStyle('f-loading', 'visibility', 'hidden');
        results.innerHTML = response.responseText;
        var descriptions = Dom.getElementsByClassName('f-description', 'div', results);
        for (var i = 0; i < descriptions.length; i++) {
            Dom.setStyle(descriptions[i], 'display', 'none');
            descriptions[i].parentNode.title = 'Click to toggle description';
            Event.addListener(descriptions[i].parentNode, 'click', function () {
                if (Dom.getStyle(this, 'display') == 'none')
                    Dom.setStyle(this, 'display', '');
                else
                    Dom.setStyle(this, 'display', 'none');
            }, descriptions[i], true);
        }
    }

    /**
     * React to window resize events. Result frame should nicely fit into the page.
     */
    this.resize = function () {
        if (!results)
            return;
        var h = (Dom.getViewportHeight() - results.offsetTop) - 18;
        Dom.setStyle(results, 'height', h + 'px');
    }

    // Setup
    Event.onDOMReady(function () {
        Dom.setStyle('f-loading', 'visibility', 'hidden');
        form = document.forms['f-query'];
        results = document.getElementById('f-result');
        Event.addListener(form, 'submit', this.doQuery, this, true);
        Event.addListener(window, 'resize', this.resize, this, true);
        var query = form.elements['q'];
        Event.addListener(query, 'keypress', this.keyHandler, this, true);
        this.resize();
    }, this, true);
}();