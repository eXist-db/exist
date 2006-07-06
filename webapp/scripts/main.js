Event.observe(window, 'load', initialize, false);

function initialize() {
	Nifty("h1.chaptertitle", "transparent");
    Nifty("div.note", "transparent");
    Nifty("div.example", "transparent");
    Nifty("div.important", "transparent");
    Nifty("div.block div.head", "top");
    Nifty("div.block ul", "bottom");
    
    loadRss();
}

function loadRss() {
	var div = $('news_content');
	if (!div) return;
	var ajax = new Ajax.Request("rss_import.xql", {
		method: 'get',
		onFailure: function (response) {
			div.innerHTML = 'Failed to load news.';
		},
		onComplete: function (response) {
			div.innerHTML = response.responseText;
		}
	});
}