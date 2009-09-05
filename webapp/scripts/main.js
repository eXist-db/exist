YAHOO.util.Event.onDOMReady(function () {
    loadRss();
	loadTwitter();
});

function loadTwitter() {
    if (document.getElementById('news_content')) {
    	var script = document.createElement("script");
    	script.setAttribute('src', 'xquery/twitter.xql?mode=json&user=existdb&max=3');
        document.getElementsByTagName('head')[0].appendChild(script);
    }
}

function loadRss() {
    if (document.getElementById('news_content')) {
        var script = document.createElement("script");
        script.setAttribute('src', 'feed.xql');
        document.getElementsByTagName('head')[0].appendChild(script);
    }
}

function atomCallback(html) {
	document.getElementById('news_content').innerHTML = html;
	YAHOO.util.Dom.setStyle('news-box', 'display', '');
}

function twitterCallback(html) {
	document.getElementById('twitter_content').innerHTML = html;
	YAHOO.util.Dom.setStyle('twitter-box', 'display', '');
}
