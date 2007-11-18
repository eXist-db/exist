var Dom = YAHOO.util.Dom,
    Event = YAHOO.util.Event;

/**
 * Singleton class to search the XQuery function documentation.
 */
var DocQuery = function () {

    var queryForm;
    var moduleForm;
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
        if (queryForm.elements['q'].value.length > 1)
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

        var query = queryForm.elements['q'].value;
        var callback = {
            success: this.queryResult,
            failure: function () {
                alert('An unknown error occurred while querying the server.');
                Dom.setStyle('f-loading', 'visibility', 'hidden');
            },
            scope: this
        }
        var typeSel = queryForm.elements['type'];
        Dom.setStyle('f-loading', 'visibility', '');
        YAHOO.util.Connect.asyncRequest('POST', '#', callback, 'mode=ajax&q=' + query +
            '&type=' + typeSel.options[typeSel.selectedIndex].value);
    }

    this.doBrowse = function (ev) {
        Event.stopEvent(ev);
        results.innerHTML = '';
        var modSel = moduleForm.elements['module'];
        var module = modSel.options[modSel.selectedIndex].value;
        var callback = {
            success: this.queryResult,
            failure: function () {
                alert('An unknown error occurred while querying the server.');
                Dom.setStyle('f-loading', 'visibility', 'hidden');
            },
            scope: this
        }
        Dom.setStyle('f-loading', 'visibility', '');
        YAHOO.util.Connect.asyncRequest('POST', '#', callback, 'mode=ajax&module=' + module);
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
        queryForm = document.forms['f-query'];
        moduleForm = document.forms['f-browse'];
        results = document.getElementById('f-result');
        Event.addListener(queryForm, 'submit', this.doQuery, this, true);
        Event.addListener(moduleForm, 'submit', this.doBrowse, this, true);
        Event.addListener(window, 'resize', this.resize, this, true);
        var query = queryForm.elements['q'];
        Event.addListener(query, 'keypress', this.keyHandler, this, true);
        this.resize();
    }, this, true);
}();