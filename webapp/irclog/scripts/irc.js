var DEFAULT_NICK_COLOR = '#6666CC';

var NICK_COLORS = [
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
];

var CHANNELS = [ '#existdb', '#testaabb'];

function newChat() {
	var chat = new Chat();
}

function dispatchEvent(sessionId, ev) {
	var args = new Array();
	for (var i = 2; i < arguments.length; i++)
		args.push(arguments[i]);
	Chat.__connections.each(function (chat) {
		if (chat.session == sessionId) {
			chat.dispatchEvent(ev, args);
			return;
		}
	});
}

var Chat = Class.create();

Chat.EV_MESSAGE = 'message';
Chat.EV_NOTICE = 'notice';
Chat.EV_USERS = 'users';

Chat.__connections = Array();
Chat.__connectionCnt = 0;

Chat.formatDayMonth = function (value) {
	if (value < 10)
		return '0' + value;
	else
		return value;
};

Chat.copyColors = function (input) {
	var colors = new Array();
	input.each(function (color) { colors.push(color); });
	return colors;
};

Chat.unload = function (event) {
	if (confirm('Moving away from this page will close all ' +
			'active connections. Are you sure?')) {
		Chat.__connections.each(function (chat) {
			chat.close();
		});
		return true;
	}
	return false;
};

Chat.prototype = {
	
	channel: null,
	handle: null,
	chatWindow: null,
	session: null,
	userMap: null,
	colorList: null,
	
	initialize: function () {
		this.handle = 'irc_' + Chat.__connectionCnt++;
		this.userMap = new Object();
		this.colorList = Chat.copyColors(NICK_COLORS);
		Chat.__connections.push(this);
		
		this.connect('Please choose a nick name and channel.');
	},
	
	connect: function (msg) {
		function channels() {
			var list = '';
			CHANNELS.each(function (channel) {
				list += '<option>' + channel + '</option>';
			});
			return list;
		}
		var content =
			'<p>' + msg + '</p>' +
			'<fieldset>' +
			'	<select id="channel">' + channels() + '</select>' +
			'	<label for="channel">Channel:</label>' +
			'</fieldset>' +	
			'<fieldset>' +
			'	<input type="text" id="nickname" value="alphatester"/>' +
			'	<label for="nickname">Nick:</label>' +
			'</fieldset>';
		var options = { width: 300, height: 200, top: 200, left: 100 };
		var modal = new ModalDialog("Login", content, options,
				this.gotNick.bind(this));
	},
	
	gotNick: function (dialog, result) {
		if (result == ModalDialog.OK) {
			var nick = $('nickname').value;
			var select = $('channel');
			this.channel = select.options[select.selectedIndex].value;
			dialog.close();
			var ajax = new Ajax.Request('../irc/IRCServlet', {
				method: 'post',
				parameters: 'nick=' + encodeURIComponent(nick) + 
					'&channel=' + encodeURIComponent(this.channel),
				onComplete: this.connected.bind(this),
				onFailure: this.connectFailed.bind(this)
			});
		} else {
			this.chatWindow.close();
		}
	},
	
	connectFailed: function (response) {
		this.connect(response.statusText);
	},
	
	connected: function (response) {
		this.session = response.getResponseHeader('X-IRC-Session');
		alert(this.session);
		this.mainWindow();
		$(this.handle + '_progress').style.display = 'none';
		$(this.handle).src = '../irc/IRCServlet?session=' + this.session;
	},
	
	mainWindow: function () {
		var html =
			'<div class="irc_main">' +
			'	<div class="irc_content" id="' + this.handle + '_o">' +
			'		<img id="' + this.handle + '_progress" src="images/indicator_medium.gif"/>' +
			'		<div class="irclog" id="' + this.handle + '_t">' +
			'		</div>' +
			'	</div>' +
			'	<div class="ircinput">' +
			'		<textarea id="' + this.handle + '_i"></textarea>' +
			'	</div>' +
			'</div>' +
			'<div class="irc_userlist" id="' + this.handle + '_users">' +
			'	<ul id="' + this.handle + '_users_l">' +
			'	</ul>' +
			'</div>';
		var options = { width: 800, height: 500, top: 100, left: 100,
				closeHandler: this.close.bind(this) };
		this.chatWindow = new FloatingWindow(this.nick + '@' + this.channel, html, options);
		var dim = this.chatWindow.getDimensions();
		
		this.chatWindow.display();
		
		var input = $(this.handle + '_i');
		var div = $(this.handle + '_o');
		div.style.height = 
			(dim.height - div.offsetTop - input.parentNode.offsetHeight - 25) + 'px';
		div.style.overflow = 'auto';
		
		Event.observe(input, 'keypress', this.sendMessage.bindAsEventListener(this), false);
		input.style.width = div.offsetWidth + 'px';
		
		div = $(this.handle + '_users');
		div.style.height = 
			(dim.height - div.offsetTop - input.parentNode.offsetHeight - 25) + 'px';
		div.style.overflow = 'auto';
		
		// create the hidden iframe that will keep our connection
		// with the server
		var iframe = 
			'<iframe class="irclog" id="' + this.handle + '" style="display: none;"></iframe>';
		new Insertion.Top(document.body, iframe);
	},
	
	sendMessage: function (event) {
		var keyCode = event.which ? event.which : event.keyCode;
		if (keyCode == Event.KEY_RETURN) {
			var msg = $F(this.handle + '_i');
			Event.stop(event);
			$(this.handle + '_i').value = '';
			var ajax = new Ajax.Request('../irc/IRCServlet', {
				method: 'post',
				parameters: 'session=' + this.session + 
					'&send=' + encodeURIComponent(msg),
				onComplete: function (response) { },
				onFailure: function (response) {
					alert('Request to server failed: ' + response.responseText);
				}
			});
			return false;
		}
		return true;
	},
	
	close: function (handle) {
		if (this.usersWindow)
			this.usersWindow.close();
		var ajax = new Ajax.Request('../irc/IRCServlet', {
			method: 'post',
			parameters: 'close=true&session=' + this.session,
			onComplete: function (response) {
			},
			onFailure: function (response) {
				alert('Request to server failed: ' + response.responseText);
			}
		});
	},
	
	dispatchEvent: function (ev, args) {
		switch (ev) {
			case Chat.EV_MESSAGE :
				var color = this.userMap[args[0]] ? this.userMap[args[0]] : DEFAULT_NICK_COLOR;
				var html =
					'<div class="irc_line">' +
					'	<span class="message">' + args[1] + '</span>' +
					'	<span class="nick" style="color: ' + color + '">' + args[0] + '</span>' +
					'</div>';
				new Insertion.Bottom($(this.handle + '_t'), html);
				break;
			case Chat.EV_NOTICE :
				var html =
					'<div class="notice">' + args[0] + '</div>';
				new Insertion.Bottom(this.handle + '_t', html);
				break;
			case Chat.EV_JOIN :
				this.colorList = Chat.copyColors(NICK_COLORS);
				var html =
					'<div class="join">' + args[1] + '</div>';
				new Insertion.Bottom(this.handle + '_t', html);
				this.userMap[args[0]] = this.colorList.pop();
				this.updateUserList();
				break;
			case Chat.EV_PART :
				var html =
					'<div class="part">' + args[1] + '</div>';
				new Insertion.Bottom(this.handle + '_t', html);
				delete this.userMap[args[0]];
				this.updateUserList();
				break;
			case Chat.EV_USERS :
				for (var i = 0; i < args.length; i++) {
					this.userMap[args[i]] = this.colorList.pop();
				}
				this.updateUserList();
				break;
		}
		var div = $(this.handle + '_o');
		div.scrollTop = div.scrollHeight;
	},
	
	updateUserList: function () {
		var userDiv = $(this.handle + '_users_l');
		userDiv.innerHTML = '';
		for (user in this.userMap) {
			var color = this.userMap[user];
			new Insertion.Bottom(userDiv, '<li style="color: ' + color + '">' + user + '</li>');
		}
	}
};

Event.observe(window, 'unload', Chat.unload, false);

var ModalDialog = Class.create();

ModalDialog.OK = 0;
ModalDialog.CANCEL = 1;

ModalDialog.prototype = {
	
	mResult: null,
	mCallback: null,
	
	initialize: function (title, content, options, callback) {
		this.mCallback = callback;
		var pageDim = getPageSize();
		var width = options.width;
		options.left = (pageDim.pageWidth - width) / 2;
		options.zIndex = 1001;
		var html =
			'<div class="modal">' +
			'	<div class="modal_content">' + content + '</div>' +
			'	<div class="modal_buttons">' +
			'		<button id="modal_btn_ok" type="button">Ok</button>' +
			'		<button id="modal_btn_cancel" type="button">Cancel</button>' +
			'	</div>' +
			'</div>';
		
		this.initOverlay(pageDim);
		this.win = new FloatingWindow(title, html, options);
		
		Event.observe($('modal_btn_ok'), 'click', this.btnClick.bindAsEventListener(this), false);
		Event.observe($('modal_btn_cancel'), 'click', this.btnClick.bindAsEventListener(this), false);
		
		this.win.display();
	},
	
	initOverlay: function (pageDim) {
		var objOverlay = $('overlay');
		if (objOverlay) {
			objOverlay.style.display = 'block';
		} else {
			new Insertion.Top(document.body, '<div id="overlay"/>');
			objOverlay = $('overlay');
			Element.setStyle(objOverlay, {
				position: 'absolute',
				top: '0', left: '0',
				zIndex: '1000', width: '100%',
				height: pageDim.pageHeight + 'px'
			});
		}
	},
	
	btnClick: function (event) {
		var button = Event.element(event);
		if (button.id == 'modal_btn_ok')
			this.mResult = ModalDialog.OK;
		else
			this.mResult = ModalDialog.CANCEL;
		this.mCallback(this, this.mResult);
	},
	
	close: function () {
		this.win.close();
		$('overlay').style.display = 'none';
	}
};

var FloatingWindow = Class.create();

FloatingWindow.__counter = 0;
FloatingWindow.__zIndex = 1;

FloatingWindow.prototype = {
	
	initialize: function (title, content, options) {
		this.handle = 'win' + FloatingWindow.__counter++;
		this.contentId = this.handle + '_content';
		if (options.closeHandler)
			// remember handler to be called upon close
			this.closeHandler = options.closeHandler;
			
		var html =
			'<div id="' + this.handle +'" class="window" style="display: none">' +
			'	<img class="window_closeButton" src="images/close.gif" />' +
			'	<h1 id="' + this.handle + '_title">' + title + '</h1>' +
			'	<div id="' + this.contentId + '" class="window_content">' + 
			content + '</div>' +
			'</div>';
		// insert the window div into the document
		new Insertion.Top(document.body, html);
		var win = $(this.handle);
		if (options.zIndex)
			win.style.zIndex = options.zIndex;
		else
			win.style.zIndex = FloatingWindow.__zIndex++;
		if (options.width)
			win.style.width = options.width + 'px';
		if (options.height)
			win.style.height = options.height + 'px';
		if (options.left)
			win.style.left = options.left + 'px';
		if (options.right)
			win.style.right = options.right + 'px';
		win.style.top = options.top + 'px';
		
		// attach a listener to the close image
		var img = win.getElementsByTagName('img');
		Event.observe(img[0], 'click', this.close.bind(this), false);
		
		var content = $(this.contentId);
		var dim = Element.getDimensions(win);
		content.style.height = (dim.height - content.offsetTop - 26) + 'px';
		new Draggable(win, { revert: false, handle: this.handle + '_title' });
	},
	
	display: function() {
		// new Effect.Appear(this.handle);
		$(this.handle).style.display = '';
	},
	
	close: function() {
		document.body.removeChild($(this.handle));
		if (this.closeHandler)
			this.closeHandler(this.handle);
	},
	
	getContentId: function() {
		return this.contentId;
	},
	
	getDimensions: function() {
		var win = $(this.handle);
		win.style.visibility = 'hidden';
		win.style.display = 'block';
		var dim = Element.getDimensions(this.contentId);
		win.style.display = 'none';
		win.style.visibility = '';
		return dim;
	}
};

function getPageSize(){
	
	var xScroll, yScroll;
	
	if (window.innerHeight && window.scrollMaxY) {	
		xScroll = document.body.scrollWidth;
		yScroll = window.innerHeight + window.scrollMaxY;
	} else if (document.body.scrollHeight > document.body.offsetHeight){ // all but Explorer Mac
		xScroll = document.body.scrollWidth;
		yScroll = document.body.scrollHeight;
	} else { // Explorer Mac...would also work in Explorer 6 Strict, Mozilla and Safari
		xScroll = document.body.offsetWidth;
		yScroll = document.body.offsetHeight;
	}
	
	var windowWidth, windowHeight;
	if (self.innerHeight) {	// all except Explorer
		windowWidth = self.innerWidth;
		windowHeight = self.innerHeight;
	} else if (document.documentElement && document.documentElement.clientHeight) { // Explorer 6 Strict Mode
		windowWidth = document.documentElement.clientWidth;
		windowHeight = document.documentElement.clientHeight;
	} else if (document.body) { // other Explorers
		windowWidth = document.body.clientWidth;
		windowHeight = document.body.clientHeight;
	}	
	
	// for small pages with total height less then height of the viewport
	if(yScroll < windowHeight){
		pageHeight = windowHeight;
	} else { 
		pageHeight = yScroll;
	}

	// for small pages with total width less then width of the viewport
	if(xScroll < windowWidth){	
		pageWidth = windowWidth;
	} else {
		pageWidth = xScroll;
	}

	return { pageWidth: pageWidth, pageHeight: pageHeight, windowWidth: windowWidth,
		windowHeight: windowHeight };
}