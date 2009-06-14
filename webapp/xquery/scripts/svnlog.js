var Dom = YAHOO.util.Dom,
    Event = YAHOO.util.Event;

Event.onDOMReady(function () {
    Event.addListener(Dom.getElementsByClassName('svn-show-files', 'img'), 'click', function (ev) {
        Event.stopEvent(ev);
        var src = Event.getTarget(ev);
        var entry = src.parentNode;
        var ul = Dom.getElementsByClassName('svnpaths', 'ul', entry)[0];
        if (Dom.getStyle(ul, 'display') == 'none') {
            Dom.setStyle(ul, 'display', '');
        }  else {
            Dom.setStyle(ul, 'display', 'none');
        }
    });
});
