var Dom = YAHOO.util.Dom,
    Event = YAHOO.util.Event;

/**
 * Singleton class to search the XQuery function documentation.
 */
var DocQuery = function () {

    var queryForm;
    var results;
    var timer = null;

    /**
     * Queries are triggered by typing into the input box
     */
    this.keyHandler = function (ev) {
        if (timer) clearTimeout(timer);
        if (ev.altKey || ev.shiftKey || ev.metaKey || ev.ctrlKey)
            return;
        switch (ev.keyCode) {
            case 9:
            case 13:
            case 27:
            case 37:
            case 38:
            case 39:
            case 40:
                return;
        }
        var self = this;
        timer = setTimeout(function () { self.autoQuery() }, 750);
    }

    /**
     * Received a key event, check if we should send a query.
     */
    this.autoQuery = function () {
        if (queryForm.elements['q'].value.length > 2)
            this.submit();
        else
            results.innerHTML = '';
    }

    /**
     * Send query request to the server.
     * @param ev
     */
    this.submit = function (ev) {
        if (ev) Event.stopEvent(ev);

        var action = 'search';
        if (ev) {
            action = Event.getTarget(ev).value;
        }

        var callback = {
            success: this.queryResult,
            failure: function () {
                alert('An unknown error occurred while querying the server.');
                Dom.setStyle('f-loading', 'visibility', 'hidden');
            },
            scope: this
        }
        Dom.setStyle('f-loading', 'visibility', 'visible');
        var params = this.getQuery() + '&action=' + action + '&mode=ajax';

        var animOut = new YAHOO.util.Anim(results, { opacity: { to: 0 } }, 0.3, YAHOO.util.Easing.easeNone);
        animOut.onComplete.subscribe(function () {
            results.innerHTML = '';
            YAHOO.util.Connect.asyncRequest('POST', '?', callback, params);
        });
        animOut.animate();

        // remember the last action. needed for printing.
        queryForm.elements['prev'].value = action;
    }

    this.getQuery = function () {
        var query = queryForm.elements['q'].value;
        var modSel = queryForm.elements['module'];
        var prev = queryForm.elements['prev'].value;
        var module = modSel.options.length > 0 ? modSel.options[modSel.selectedIndex].value : '';
        var typeSel = queryForm.elements['type'];

        return 'q=' + query +
            '&type=' + typeSel.options[typeSel.selectedIndex].value + '&module=' + module +
            '&prev=' + prev;
    }

    this.print = function (ev) {
        Event.stopEvent(ev);
        var params = '?action=Print&' + this.getQuery();
        window.open(params);
    }

    /**
     * Handle query results.
     */
    this.queryResult = function (response) {
        var element;
        
        Dom.setStyle('f-loading', 'visibility', 'hidden');
        results.innerHTML = response.responseText;
        
        var descriptions = Dom.getElementsByClassName('f-description', 'div', results);
        for (var i = 0; i < descriptions.length; i++) {
            Dom.setStyle(descriptions[i], 'display', 'none');
            descriptions[i].parentNode.title = 'Click to toggle display of description';
            Event.addListener(descriptions[i].parentNode, 'click', function () {
                if (Dom.getStyle(this, 'display') == 'none') {
                    Dom.setStyle(this, 'display', '');
                }  else {
                    Dom.setStyle(this, 'display', 'none');
                }
            }, descriptions[i], true);
        }
        
        var modules = Dom.getElementsByClassName('f-module-heading-section', 'div', results);
        for (var i = 0; i < modules.length; i++) {
            Dom.setStyle(modules[i], 'display', '');
            modules[i].parentNode.title = 'Click to toggle display of module';
            Event.addListener( Dom.getElementsByClassName('f-module-heading-table', 'table', modules[i].parentNode), 'click', function () {
                var hideshow = Dom.getElementsByClassName('f-module-heading-hideshow', 'td', this.parentNode);
                if (Dom.getStyle(this, 'display') == 'none') {
                    Dom.setStyle(this, 'display', '');
                    hideshow[0].innerHTML = "-";
                } else {
                    Dom.setStyle(this, 'display', 'none');
                    hideshow[0].innerHTML = "+";
                }
            }, modules[i], true);
        }

        element = document.getElementById( 'showAllModules' );
        Event.addListener( element, 'click', function () {
            var modules = Dom.getElementsByClassName('f-module-heading-section', 'div', results);
                for (var i = 0; i < modules.length; i++) {
                    Dom.setStyle( modules[i], 'display', '' );
                    Dom.getElementsByClassName('f-module-heading-hideshow', 'td', modules[i].parentNode)[0].innerHTML = "-";
                }
            }, element, true);
        
        element = document.getElementById( 'hideAllModules' );
        Event.addListener( element, 'click', function () {
            var modules = Dom.getElementsByClassName('f-module-heading-section', 'div', results);
                for (var i = 0; i < modules.length; i++) {
                    Dom.setStyle( modules[i], 'display', 'none' );
                    Dom.getElementsByClassName('f-module-heading-hideshow', 'td', modules[i].parentNode)[0].innerHTML = "+";
                }
            }, element, true);
        
        element = document.getElementById( 'showAllDescriptions' );
        Event.addListener( element, 'click', function () {
            var modules = Dom.getElementsByClassName('f-description', 'div', results);
                for (var i = 0; i < modules.length; i++) {
                    Dom.setStyle( modules[i], 'display', '' );
                }
            }, element, true);
        
        element = document.getElementById( 'hideAllDescriptions' );
        Event.addListener( element, 'click', function () {
            var modules = Dom.getElementsByClassName('f-description', 'div', results);
                for (var i = 0; i < modules.length; i++) {
                    Dom.setStyle( modules[i], 'display', 'none' );
                }
            }, element, true);

        var animIn = new YAHOO.util.Anim(results, { opacity: { to: 1 } }, 0.3, YAHOO.util.Easing.easeNone);
        animIn.animate();
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
        results = document.getElementById('f-result');

        Event.addListener('f-btn-browse', 'click', this.submit, this, true);
        Event.addListener('f-btn-search', 'click', this.submit, this, true);
        Event.addListener('f-btn-print', 'click', this.print, this, true);
        
//        Event.addListener(window, 'resize', this.resize, this, true);
        var query = queryForm.elements['q'];
        Event.addListener(query, 'keypress', this.keyHandler, this, true);
//        this.resize();
    }, this, true);
}();
