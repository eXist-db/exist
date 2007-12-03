YAHOO.util.Event.onDOMReady(function () {
    Nifty("h1.chaptertitle", "transparent");
    Nifty("div.note", "transparent");
    Nifty("div.example", "transparent");
    Nifty("div.important", "transparent");
    Nifty("div.block div.head", "top");
    Nifty("div.block ul", "bottom");

    loadRss();
});

function loadRss() {
    var div = document.getElementById('news_content');
	if (!div) return;
    var callback = {
        success: function (response) {
            div.innerHTML = response.responseText;
        },
        failure: function (response) {
			div.innerHTML = 'Failed to load news.';
		}
    }
    YAHOO.util.Connect.asyncRequest('GET', 'feed.xql', callback);
}
