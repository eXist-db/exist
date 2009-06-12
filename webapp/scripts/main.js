YAHOO.util.Event.onDOMReady(function () {
	Nifty("div.querybox", "transparent");
    Nifty("h1.chaptertitle", "transparent");
    Nifty("div.note", "transparent");
    Nifty("div.example", "transparent");
    Nifty("div.important", "transparent");
    Nifty("div.block div.head", "top");
    Nifty("div.block ul", "bottom");

    loadRss();
	loadTwitter();
});

function loadTwitter() {
	var script = document.createElement("script");
	script.setAttribute('src', 'xquery/twitter.xql?mode=json&user=existdb&max=3');
    document.getElementsByTagName('head')[0].appendChild(script);
}

function loadRss() {
    var script = document.createElement("script");
    script.setAttribute('src', 'feed.xql');
    document.getElementsByTagName('head')[0].appendChild(script);
}

function atomCallback(html) {
	document.getElementById('news_content').innerHTML = html;
}

function twitterCallback(html) {
	document.getElementById('twitter_content').innerHTML = html;
}
