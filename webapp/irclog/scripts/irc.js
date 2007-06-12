var DEFAULT_NICK_COLOR = '#6666CC';

var NICK_COLORS = [
	'#9900cc',
	'#cc9900',
	'#0099cc',
	'#00cc99',
	'#6666ff',
	'#339966',
	'#993366',
    '#9900cc',
    '#669933',
	'#0033cc',
	'#ff99ff',
	'#ffdd00',
    '#442255',
    '#ddff00',
	'#00ffdd',
	'#224455',
    '#cc0099'
];

YAHOO.util.Event.addListener(window, 'load', documentLoad);
YAHOO.util.Event.addListener(window, 'unload', documentUnload);
YAHOO.util.Event.addListener('login-link', 'click', login);

var loginDialog;
var connectDialog;

function documentLoad() {
    soundManager.url = 'soundmanager2.swf'; // override default SWF url
    soundManager.debugMode = false;
    soundManager.consoleOnly = false;

    soundManager.onload = function() {
        // soundManager is initialised, ready to use. Create a sound for this demo page.
        soundManager.createSound('sendSound','sounds/msg_out.mp3');
        soundManager.createSound('recvSound','sounds/msg_in.mp3');
        soundManager.createSound('splash', 'sounds/SPLASH_1.mp3');
    }
    
    loginDialog = new Ext.BasicDialog('login-dialog', {
        modal: true,
        width: 300,
        height: 200,
        resizable: false,
        closable: false,
        shadow: true
    });
    loginDialog.addButton('Cancel', function () { this.hide(); }, loginDialog);
    loginDialog.addButton('Connect', newChat);
    
    connectDialog = connectDialog = new Ext.BasicDialog('connect-dialog', {
        width: 250,
        height: 150,
        resizable: false,
        closable: false,
        shadow: true
    });
}

function documentUnload() {
	if (confirm('Moving away from this page will close all ' +
			'active connections. Are you sure?')) {
        for (var i = 0; i < Chat.__connections.length; i++) {
            Chat.__connections[i].close();
        }
		return true;
	}
	return false;
}

function login() {
    loginDialog.show('login-link');
}

function newChat() {
    loginDialog.hide();
    var nick = document.getElementById('nickname').value;
    var channelSelect = document.getElementById('channel');
    var channel = channelSelect.options[channelSelect.selectedIndex].value;
    connect(channel, nick);
}

function connect(channel, nick) {
    if (!nick || nick == '') {
        Ext.MessageBox.alert('Login Error', 'Please choose a nickname');
        return false;
    }

    connectDialog.show();
    var callback = {
        success: connected,
        failure: function (response) {
            Ext.MessageBox.alert('Login Failed', response.responseText, function() {
                connectDialog.hide();
                login();
            });
        },
        argument: { nick: nick, channel: channel },
        scope: this
    };
    var params = 'nick=' + nick + '&channel=' + channel;
    YAHOO.util.Connect.asyncRequest('POST', '../irc/IRCServlet', callback, params);
    return true;
}

function connected(response) {
    connectDialog.hide();
    session = trimStr(response.getResponseHeader['X-IRC-Session']);
    nick = trimStr(response.getResponseHeader['X-IRC-Nick']);
    var chat = new Chat(response.argument.channel, nick, session);
}

function trimStr(s) {
    return s.replace(/\s+$/,'');
}

function dispatchEvent(sessionId, ev) {
    var args = new Array();
    for (var i = 2; i < arguments.length; i++)
        args.push(arguments[i]);
    for (var i = 0; i < Chat.__connections.length; i++) {
        var chat = Chat.__connections[i];
        if (chat.session == sessionId) {
            chat.dispatchEvent(ev, args); return;
        }
    }
}

var Chat = function (connectionTitle, nick, session) {
    this.connectionTitle = connectionTitle;
    this.nick = nick;
    this.session = session;
    this.handle = 'irc_' + Chat.__connectionCnt++;
    Chat.__connections.push(this);
    
    this.createDialog();
    
    this.userMap = new Object();
    this.colorList = new Array();
    for (var i = 0; i < NICK_COLORS.length; i++) {
        this.colorList.push(NICK_COLORS[i]);
    }
    
    // create the hidden iframe that will keep our connection
    var iframe =
        Ext.DomHelper.append(document.body, {
            tag: 'iframe', id: this.handle, style: 'visibility: hidden'});
    iframe.src = '../irc/IRCServlet?session=' + this.session;
    Ext.EventManager.on(iframe, 'load', function (ev) {
        iframe.src = '../irc/IRCServlet?session=' + this.session;
    });
    var thisObj = this;
    setTimeout(function () { thisObj.aliveCheck(); }, 60000);
};

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

Chat.prototype = {
	
	handle: null,
	session: null,
	userMap: null,
	colorList: null,
    input: null,
    nick: null,
    dialog: null,
	serverAlive: false,
    connecting: false,
    
    createDialog: function () {
        this.dialog = new Ext.LayoutDialog(this.handle + '_win', {
            autoCreate: true,
            title: this.connectionTitle,
            autoTabs:false,
            x: 50,
            y: 50,
            width:750,
            height:500,
            shadow:true,
            minWidth:300,
            minHeight:300,
            closable: false,
            east: {
                split: true,
                initialSize: 150,
                minSize: 100,
                maxSize: 250,
                collapsible: true,
                titlebar: true
            },
            south: {
                split: true,
                initialSize: 80,
                minSize: 60,
                maxSize: 300
            },
            center: {
                autoScroll: false
            }
        });
    
        var layout = this.dialog.getLayout();
        layout.beginUpdate();

        var east = Ext.DomHelper.append(document.body, { tag: 'div', cls: 'irc_userlist' });
        this.userList = Ext.DomHelper.append(east, { tag: 'ul' }, true);
        layout.add('east', new Ext.ContentPanel(east, { title: 'User List' }));
        
        var center = Ext.DomHelper.append(document.body, {
            tag: 'div', children: [
                { tag: 'div', id: this.handle + '_mtb' },
                { tag: 'div', id: this.handle + '_div', cls: 'messages', children: [
                    { tag: 'table', children: [ { tag: 'tbody' } ] }
                ]}
            ]
        }, true);
        this.messages = center.child('.messages tbody');

        var tb = new Ext.Toolbar(this.handle + '_mtb');
        tb.addButton({id: 'disconnect-btn', cls: 'x-btn-text-icon',
            icon: 'images/connect.png', text: 'Disconnect', handler: this.disconnect, scope: this });
        tb.addButton({ cls: 'x-btn-text-icon', icon: 'images/arrow_refresh.png',
            text: 'Reconnect to proxy', handler: this.refresh, scope: this});
        
        layout.add('center', new Ext.ContentPanel(center, { title: 'Chat', toolbar: tb, resizeEl: this.handle + '_div', fitToFrame: true }));

        var south = Ext.DomHelper.append(document.body, {
            tag: 'div', children: [
                { tag: 'div', id: 'input-toolbar' }
            ]
        });
        this.input = Ext.DomHelper.append(south, { tag: 'textarea', cls: 'input-area' }, true);
        this.input.on('keypress', this.keyPressed, this);
        
        tb = new Ext.Toolbar('input-toolbar');
        tb.addButton({id: 'send-btn', text: 'Send',
            cls: 'x-btn-text-icon', icon: 'images/keyboard_add.png', handler: this.sendMessage, scope: this });
        tb.addButton({id: 'clear-btn', text: 'Clear', handler: function () {
            this.input.dom.value = '';
        }, scope: this});
        tb.addSeparator();
        var store = new Ext.data.SimpleStore({
            fields: [ 'name', 'command' ],
            data: [ ['nick', 'nick'], ['msg', 'msg'] ]
        });
        var commands = new Ext.form.ComboBox({
            store: store,
            displayField: 'name',
            typeAhead: true,
            mode: 'local',
            emptyText: 'Select a command ...'
        });
        commands.on('select', function (combo) { this.input.dom.value = '/' + combo.getValue() + ' '; }, this);
        tb.addField(commands);
        
        layout.add('south', new Ext.ContentPanel(south, { title: 'Text Input', toolbar: tb }));
        layout.endUpdate();
    
        this.dialog.show();
	},

    keyPressed: function (event) {
        if (event.getKey() == Ext.EventObject.ENTER) {
            this.sendMessage();
            event.stopEvent();
            return false;
        }
        return true;
    },

    sendMessage: function (message) {
        var msg = message || this.input.dom.value;
        this.input.dom.value = '';

        var callback = {
            success: function (response) {
            },
            failure: function (response) {
                Ext.MessageBox.alert('Request to server failed!', response.responseText);
            }
        };
        var params = 'session=' + this.session + '&send=' + encodeURIComponent(msg);
        var txn = YAHOO.util.Connect.asyncRequest('POST', '../irc/IRCServlet', callback, params);
        soundManager.play('sendSound');
	},
	
	close: function (handle) {
		this.closeChannel();
	},
	
	closeChannel: function () {
        var callback = {
            success: function (response) {
                Ext.MessageBox.alert('Closed', 'Connection closed.');
            },
            failure: function (response) {
                Ext.MessageBox.alert('Login Failed', response.responseText);
            }
        };
        var params = 'close=true&session=' + this.session;
        var txn = YAHOO.util.Connect.asyncRequest('POST', '../irc/IRCServlet', callback, params);
	},
	
	dispatchEvent: function (ev, args) {
        switch (ev) {
			case Chat.EV_PING :
				// server sent a ping and waits for a response
                var params = 'pong=true&session=' + this.session;
                var txn = YAHOO.util.Connect.asyncRequest('POST', '../irc/IRCServlet', null, params);
				break;
			case Chat.EV_MESSAGE :
                if (args[0] != this.nick)
                    soundManager.play('splash');
				this.writeLine('message', args[1], args[0]);
				break;
			case Chat.EV_NOTICE :
				this.writeLine('notice', args[0]);
				break;
			case Chat.EV_JOIN :
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
        this.serverAlive = true;
    },
	
	writeLine: function (className, message, nick) {
		var time = new Date();
		var timeStr = Chat.formatDayMonth(time.getHours()) + ':' + 
			Chat.formatDayMonth(time.getMinutes()) + ':' + 
			Chat.formatDayMonth(time.getSeconds());

        var html =
                '<tr class="irc_line">' +
                '   <td class="time">{time}</td>' +
                '   <td class="{nickCls}" style="color: {color}">{nick}</td>' +
                '   <td class="{cls}">{message}</td>' +
                '</tr>';
        var tpl = new Ext.DomHelper.Template(html);
        var node = tpl.append(this.messages, { time: timeStr, nick: nick, cls: className, message: message,
            nickCls: nick ? 'nick' : '',
            color: this.userMap[nick] ? this.userMap[nick] : DEFAULT_NICK_COLOR }, true);
        var div = document.getElementById(this.handle + '_div');
        div.scrollTop = div.scrollHeight;
        node.setVisible(true, true);
    },
	
	updateUserList: function () {
        this.userList.dom.innerHTML = '';
        for (user in this.userMap) {
			var color = this.userMap[user];
            var u = Ext.DomHelper.append(this.userList, { tag: 'li', html: user }, true);
            u.setStyle('color', color);
		}
	},
	
	refresh: function () {
        var iframe = Ext.get(this.handle);
        iframe.dom.src = '';
        iframe.remove();
        
        iframe = Ext.DomHelper.append(document.body, { tag: 'iframe', id: this.handle, style: 'visibility: hidden' });
        iframe.src = '../irc/IRCServlet?session=' + this.session;
        Ext.EventManager.on(iframe, 'load', function (ev) {
            iframe.src = '../irc/IRCServlet?session=' + this.session;
        });
	},
	
	disconnect: function () {
		this.closeChannel();
        var iframe = Ext.get(this.handle);
		iframe.dom.src = '';
        iframe.remove();
        this.dialog.hide();
        this.dialog.destroy(true);
    },

    tryReconnect: function () {
        var callback = {
            success: this.reconnected,
            failure: function (response) {
                this.writeLine('notice', 'Failed to reconnect. Giving up.');
            },
            scope: this
        };
        var params = 'nick=' + this.nick + '&channel=' + this.channel;
        YAHOO.util.Connect.asyncRequest('POST', '../irc/IRCServlet', callback, params);
    },

    reconnected: function (response) {
        this.connecting = false;
        this.session = trimStr(response.getResponseHeader['X-IRC-Session']);
        this.nick = trimStr(response.getResponseHeader['X-IRC-Nick']);
        this.refresh();
    },

    aliveCheck: function () {
        if (this.connecting)
            return;
        var thisObj = this;
        if (!this.serverAlive) {
            this.writeLine('notice', 'Connection to server lost! Trying to reconnect in 60 seconds.......');
            this.connecting = true;
            setTimeout(function() { thisObj.tryReconnect(); }, 60000);
            return;
        }
        this.serverAlive = false;
        setTimeout(function () { thisObj.aliveCheck(); }, 60000);
    }
};