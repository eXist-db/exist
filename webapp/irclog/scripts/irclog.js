// Call init() after the document was loaded
window.onload = init;
window.onresize = resize;

// Register event handlers for various elements on the page.
// The behaviour library allows to register handlers via CSS
// selectors. This way, we can keep the HTML Javascript-clean.
var behaviourRules = {
	'#next' : function (element) {
			element.onclick = browseNext;
	},
	'#previous' : function (element) {
			element.onclick = browsePrevious;
	},
	'#send-query' : function (element) {
			element.onclick = query;
	},
	'#query-close' : function (element) {
			element.onclick = function () {
				Element.hide('query-result');
				resize();
			}
	},
	'#current-date' : function (element) {
			element.onchange = function () {
				displayLog(this.value);
			}
	},
    '#refresh' : function (element) {
            element.onchange = function() {
                var option = this.options[this.selectedIndex].value;
                if (timer) clearInterval(timer);
                if (option != 'off') {
                    refreshPeriod = option * 60 * 1000;
                    timer = setInterval('autoRefresh()', refreshPeriod);
                }
            }
    }
};
Behaviour.register(behaviourRules);

var colors = [
	'#9900cc',
	'#cc0099',
	'#cc9900',
	'#0099cc',
	'#00cc99',
	'#6666ff',
	'#339966',
	'#993366',
	'#669933',
	'#0033cc',
	'#ff99ff',
	'#9900cc',
	'#ffdd00'
]

var nickNames = new Object();
var lastColor = 0;

var currentDate;
var timer = null;

// refresh display every 60 seconds by default
var refreshPeriod = 60 * 1000;

/** onLoad handler to initialize display */
function init() {
	Element.hide('query-result');
    Calendar.setup(
    {
    	inputField : 'current-date',
    	ifFormat : '%Y-%m-%d',
    	button : 'set-date',
    	onUpdate : function (calendar) {
    		currentDate = calendar.date;
    	}
    }
    );
    resize();
    
    Behaviour.apply();	// we need to call behaviour again after this handler
    displayLog(new Date());
}

/** Resize the query result output div. We want this to have a fixed height,
 *  so it neatly fits into the browser window.
 */
function resize() {
	var output = $('log-output');
    output.style.height = (document.body.clientHeight - output.offsetTop - 20) + "px";
}

function displayLog(date, query) {
	function formatDayMonth(value) {
		if (value < 10)
			return '0' + value;
		else
			return value;
	}
	
	if (timer)
		clearInterval(timer);
	
	var dateStr;
	if (typeof date == 'string')
		dateStr = date;
	else
		dateStr = date.getFullYear() + '-' + formatDayMonth(date.getMonth() + 1) +
			'-' + formatDayMonth(date.getDate());
	var params = 'date=' + dateStr;
	if (query)
		params += '&query=' + query;
		
	var ajax = new Ajax.Request("irclog.xql", {
			method: 'post', parameters: params, 
			onComplete: displayResponse,
			onFailure: requestFailed
		});
	$('errors').innerHTML = 'Retrieving log ...';
	$('current-date').value = dateStr;
	currentDate = date;
	
	timer = setInterval('autoRefresh()', refreshPeriod);
}

function displayResponse(request) {
	$('errors').innerHTML = '';
	var output = $('log-output');
	output.innerHTML = request.responseText;
	colorify(output);
	var spans = output.getElementsByTagName('span');
	if (spans.length > 0)
		spans[0].scrollIntoView();
	else {
		var rows = output.getElementsByTagName('tr');
		rows[rows.length - 1].scrollIntoView();
	}
}

function autoRefresh() {
	$('errors').innerHTML = 'Refreshing ...';
	displayLog(currentDate);
}

function requestFailed(request) {
	$('log-output').innertHTML =
		"The request to the server failed.";
}

function browseNext() {
	var newMillis = currentDate.getTime() + (1000 * 60 * 60 * 24);
	displayLog(new Date(newMillis));
}

function browsePrevious() {
	var newMillis = currentDate.getTime() - (1000 * 60 * 60 * 24);
	displayLog(new Date(newMillis));
}

function query() {
	var qu = $F('query');
	if (!qu || qu.length == 0) {
		alert('Please enter a string to search for!');
		return;
	}
	
	var params = 'query=' + escape(qu);
	var ajax = new Ajax.Request("irclog.xql", {
			method: 'post', parameters: params, 
			onComplete: queryResponse,
			onFailure: requestFailed
		});
	$('errors').innerHTML = 'Query sent ...';
	Element.show('query-result');
	resize();
}

function queryResponse(request) {
	$('query-output').innerHTML = request.responseText;
	colorify($('query-output'));
	$('errors').innerHTML = '';
}

function showQueryResult(dateStr, query) {
	displayLog(dateStr, query);
}

function browseToDate(calendar) {
	alert(calendar.date.toString());
}

function colorify(element) {
	var rows = element.getElementsByTagName('tr');
	for (var i = 0; i < rows.length; i++) {
		var columns = rows[i].getElementsByTagName('td');
		if (columns.length == 3) {
			var nick = getElementValue(columns[1]);
			var color = pickColor(nick);
			columns[1].style.color = color;
		}
	}
}

function pickColor(nick) {
	var last = nickNames[nick];
	if (last)
		return last;
	if (lastColor > colors.length)
		last = 'black';
	else
		last = colors[lastColor++];
	nickNames[nick] = last;
	return last;
}

function getElementValue(node) {
	var val = '';
	var child = node.firstChild;
	while (child) {
		val += child.nodeValue;
		child = child.nextSibling;
	}
	return val;
}