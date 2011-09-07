function atomCallback(html) {
	document.getElementById('news_content').innerHTML = html;
	document.getElementById('news-box').style.display = "";
}

function twitterCallback(html) {
	document.getElementById('twitter_content').innerHTML = html;
	document.getElementById('twitter-box').style.display = "";
}
