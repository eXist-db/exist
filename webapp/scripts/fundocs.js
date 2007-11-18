var Dom = YAHOO.util.Dom,
    Event = YAHOO.util.Event,
    log = YAHOO.log;

var DocQuery = function () {

    var form;
    var results;
    var timer = null;

    this.keyHandler = function (ev) {
        if (timer) clearTimeout(timer);
        var self = this;
        timer = setTimeout(function () { self.autoQuery() }, 500);
    }

    this.autoQuery = function () {
        if (form.elements['q'].value.length > 1)
            this.doQuery();
        else
            results.innerHTML = '';
    }

    this.doQuery = function (ev) {
        if (ev) Event.stopEvent(ev);
        results.innerHTML = '';

        var query = form.elements['q'].value;
        var callback = {
            success: this.queryResult,
            failure: function () {
                alert('An unknown error occurred while querying the server.');
            },
            scope: this
        }
        YAHOO.util.Connect.asyncRequest('POST', '.', callback, 'mode=ajax&q=' + query);
    }

    this.queryResult = function (response) {
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

    this.resize = function () {
        var h = (Dom.getViewportHeight() - results.offsetTop) -
                document.getElementById('f-search').offsetTop - 12;
        Dom.setStyle(results, 'height', h + 'px');
    }

    Event.onDOMReady(function () {
        form = document.forms['f-query'];
        results = document.getElementById('f-result');
        Event.addListener(form, 'submit', this.doQuery, this, true);
        Event.addListener(window, 'resize', this.resize, this, true);
        var query = form.elements['q'];
        Event.addListener(query, 'keypress', this.keyHandler, this, true);
        this.resize();
    }, this, true);
}();