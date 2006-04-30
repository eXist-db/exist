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
	'#ffdd00',
	'#ddff00',
	'#00ffdd',
	'#224455',
	'#442255'
];

var CHANNELS = [ '#existdb', '#testaabb'];
	
function newChat() {
	var chat = new Chat();
	chat.connect();
//	chat.mainWindow();
//	chat.dispatchEvent(Chat.EV_MESSAGE, ['wolf', 'Lorem ipsum dolor sit amet, consectetuer adipiscing elit. Fusce purus. Mauris sed orci. Sed feugiat hendrerit justo. Fusce eu mauris a urna lobortis cursus. Quisque fermentum, diam quis semper scelerisque, purus nibh mollis nibh, euismod ultrices tortor pede ac risus. Cras vitae metus. Maecenas ut libero. Maecenas non justo. Pellentesque suscipit ullamcorper leo. Maecenas ipsum. Mauris velit dui, feugiat quis, bibendum sed, eleifend iaculis, diam. Sed nunc velit, venenatis non, scelerisque ut, porttitor ut, nisl.']);
//	chat.dispatchEvent(Chat.EV_MESSAGE, ['hans', 'Lorem ipsum dolor sit amet, consectetuer adipiscing elit. Fusce purus. Mauris sed orci. Sed feugiat hendrerit justo. Fusce eu mauris a urna lobortis cursus. Quisque fermentum, diam quis semper scelerisque, purus nibh mollis nibh, euismod ultrices tortor pede ac risus. Cras vitae metus. Maecenas ut libero. Maecenas non justo. Pellentesque suscipit ullamcorper leo. Maecenas ipsum. Mauris velit dui, feugiat quis, bibendum sed, eleifend iaculis, diam. Sed nunc velit, venenatis non, scelerisque ut, porttitor ut, nisl.']);
//	chat.dispatchEvent(Chat.EV_NOTICE, ["This is a notice"]);
//	chat.dispatchEvent(Chat.EV_JOIN, ["wolf", "joined the chat"]);
//	chat.dispatchEvent(Chat.EV_MESSAGE, ['rudi', 'Lorem ipsum dolor sit amet, consectetuer adipiscing elit. Fusce purus. Mauris sed orci. Sed feugiat hendrerit justo. Fusce eu mauris a urna lobortis cursus. Quisque fermentum, diam quis semper scelerisque, purus nibh mollis nibh, euismod ultrices tortor pede ac risus. Cras vitae metus. Maecenas ut libero. Maecenas non justo. Pellentesque suscipit ullamcorper leo. Maecenas ipsum. Mauris velit dui, feugiat quis, bibendum sed, eleifend iaculis, diam. Sed nunc velit, venenatis non, scelerisque ut, porttitor ut, nisl.']);
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
Chat.EV_JOIN = 'join';
Chat.EV_PART = 'part';
Chat.EV_PING = 'ping';

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
	},
	
	connect: function (msg) {
		function channels() {
			var list = '';
			CHANNELS.each(function (channel) {
				list += '<option>' + channel + '</option>';
			});
			return list;
		}
		if (!msg)
			msg = 'Please choose a nick and channel.';
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
		new ModalDialog("Login", content, options,
				this.gotNick.bind(this));
	},
	
	gotNick: function (dialog, result) {
		if (result == ModalDialog.OK) {
		    this.nick = $('nickname').value;
			var select = $('channel');
			this.channel = select.options[select.selectedIndex].value;
			dialog.close();
			this.mainWindow();
			var ajax = new Ajax.Request('../irc/IRCServlet', {
				method: 'post',
				parameters: 'nick=' + encodeURIComponent(this.nick) + 
					'&channel=' + encodeURIComponent(this.channel),
				onComplete: this.connected.bind(this),
				onFailure: this.connectFailed.bind(this)
			});
		}
	},
	
	connectFailed: function (response) {
		this.connect(response.statusText);
	},
	
	connected: function (response) {
		this.session = response.getResponseHeader('X-IRC-Session');
		$(this.handle + '_progress').style.display = 'none';
		
		// create the hidden iframe that will keep our connection
		// with the server
		new Insertion.Top(document.body, 
			'<iframe class="irclog" id="' + this.handle + '" style="display: none;"></iframe>');
		var iframe = $(this.handle);
		iframe.src = '../irc/IRCServlet?session=' + this.session;
		Event.observe(iframe.window, 'load', function (ev) {
			alert('Reloading ...');
			iframe.src = '../irc/IRCServlet?session=' + this.session;
		}, false);
	},
	
	mainWindow: function () {
		var html =
			'<div class="irc">' +
			'	<div class="irc_main">' +
			'		<div class="irc_content" id="' + this.handle + '_o">' +
			'			<img id="' + this.handle + '_progress" src="images/indicator_medium.gif"/>' +
			'			<table class="irclog" id="' + this.handle + '_t">' +
			'				<tbody><tr><td></td></tr></tbody>' +
			'			</table>' +
			'		</div>' +
			'		<div class="irc_actions">' +
			'			<button type="button" id="' + this.handle + '_btn_reload">Refresh Connection</button>' +
			'			<button type="button" id="' + this.handle + '_btn_disconnect">Disconnect</button>' +
			'		</div>' +
			'		<div class="ircinput">' +
			'			<textarea id="' + this.handle + '_i"></textarea>' +
			'		</div>' +
			'	</div>' +
			'	<div class="irc_userlist" id="' + this.handle + '_users">' +
			'		<ul id="' + this.handle + '_users_l">' +
			'		</ul>' +
			'	</div>' +
			'</div>';
		var options = { width: 800, height: 500, top: 100, left: 100,
				closeHandler: this.close.bind(this),
				resizeHandler: this.resize.bind(this) 
		};
		this.chatWindow = new FloatingWindow(this.nick + '@' + this.channel, options);
		this.chatWindow.setContent(html);
		var input = $(this.handle + '_i');
		
		Event.observe(input, 'keypress', this.sendMessage.bindAsEventListener(this), false);
		
		Event.observe(this.handle + '_btn_reload', 'click', this.refresh.bindAsEventListener(this), false);
		Event.observe(this.handle + '_btn_disconnect', 'click', this.disconnect.bindAsEventListener(this), false);
		
		this.chatWindow.display();
	},
	
	resize: function (dimensions) {
		var input = $(this.handle + '_i');
		var div = $(this.handle + '_o');
		div.style.height = 
			(dimensions.height - div.offsetTop - input.parentNode.offsetHeight - 44) + 'px';
		div.style.overflow = 'auto';
		
		input.style.width = div.offsetWidth + 'px';
		
		div = $(this.handle + '_users');
		div.style.height = (dimensions.height - div.offsetTop - 10) + 'px';
		div.style.overflow = 'auto';
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
		this.closeChannel();
	},
	
	closeChannel: function () {
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
			case Chat.EV_PING :
				// server sent a ping and waits for a response
				new Ajax.Request('../irc/IRCServlet', {
					method: 'post',
					parameters: 'pong=true&session=' + this.session,
					onFailure: function (response) {
						alert('Connection to server lost?');
					}
				});
				break;
			case Chat.EV_MESSAGE :
				this.writeLine('message', args[1], args[0]);
				break;
			case Chat.EV_NOTICE :
				this.writeLine('notice', args[0]);
				break;
			case Chat.EV_JOIN :
				this.colorList = Chat.copyColors(NICK_COLORS);
				this.writeLine('join', args[1]);
				this.userMap[args[0]] = this.colorList.pop();
				this.updateUserList();
				break;
			case Chat.EV_PART :
				this.writeLine('part', args[1]);
				this.colorList.push(this.userMap[args[0]]);
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
		if (div)
			div.scrollTop = div.scrollHeight;
	},
	
	writeLine: function (className, message, nick) {
		var tbody = $(this.handle + '_t').tBodies[0];
		var time = new Date();
		var timeStr = Chat.formatDayMonth(time.getHours()) + ':' + 
			Chat.formatDayMonth(time.getMinutes()) + ':' + 
			Chat.formatDayMonth(time.getSeconds());
				
		var tr = document.createElement('tr');
		tr.className = 'irc_line';
		tbody.appendChild(tr);
		
		var td = document.createElement('td');
		td.className = 'time';
		tr.appendChild(td);
		td.appendChild(document.createTextNode(timeStr));
		
		td = document.createElement('td');
		if (nick) {
			var color = this.userMap[nick] ? this.userMap[nick] : DEFAULT_NICK_COLOR;
			td.style.color = color;
			td.className = 'nick';
			td.appendChild(document.createTextNode(nick));
		}
		tr.appendChild(td);
		
		td = document.createElement('td');
		tr.appendChild(td);
		td.appendChild(document.createTextNode(message));
		td.className = className;
	},
	
	updateUserList: function () {
		var userDiv = $(this.handle + '_users_l');
		userDiv.innerHTML = '';
		for (user in this.userMap) {
			var color = this.userMap[user];
			new Insertion.Bottom(userDiv, '<li style="color: ' + color + '">' + user + '</li>');
		}
	},
	
	refresh: function () {
		new Ajax.Request('../irc/IRCServlet', {
			method: 'post',
			parameters: 'refresh=true&session=' + this.session,
			onComplete: this.refreshDone.bind(this),
			onFailure: function (response) {
				alert('Connection to server lost?');
			}
		});
		new Insertion.Bottom(this.handle + '_t', '<div class="notice">refreshing connection ...</div>');
	},
	
	refreshDone: function (response) {
		var iframe = $(this.handle);
		iframe.src = '../irc/IRCServlet?session=' + this.session;
		new Insertion.Bottom(this.handle + '_t', '<div class="notice">refresh done.</div>');
	},
	
	disconnect: function () {
		this.closeChannel();
		var iframe = $(this.handle);
		iframe.src = '';
		new Insertion.Bottom(this.handle + '_t', '<div class="notice">refresh done.</div>');
	}
};

Event.observe(window, 'unload', documentUnload, false);

function documentUnload() {
	if (confirm('Moving away from this page will close all ' +
			'active connections. Are you sure?')) {
		Chat.__connections.each(function (chat) {
			chat.close();
		});
		return true;
	}
	return false;
}