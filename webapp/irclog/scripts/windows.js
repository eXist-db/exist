var FloatingWindow = Class.create();

FloatingWindow.__counter = 0;
FloatingWindow.__zIndex = 1;

FloatingWindow.prototype = {
	
	initialize: function (title, options) {
		this.handle = 'win' + FloatingWindow.__counter++;
		this.contentId = this.handle + '_content';
		if (options.closeHandler)
			// remember handler to be called upon close
			this.closeHandler = options.closeHandler;
		
		if (options.resizeHandler)
			// handler to be called after resize
			this.resizeHandler = options.resizeHandler;
			
		var html =
			'<div id="' + this.handle +'" class="window" style="display: none">' +
			'	<img class="window_close" src="images/close.gif" />' +
			'	<table cellspacing="0" cellpadding="0">' +
			'		<tr>' +
			'			<td class="window_topleft"></td>' +
			'			<td id="' + this.handle + '_title" class="window_title">' + title + '</td>' +
			'			<td class="window_topright"></td>' +
			'		</tr>' + 
			'		<tr>' +
			'			<td class="window_midleft"></td>' +
			'			<td id="' + this.contentId + '" class="window_content"></td>' +
			'			<td class="window_midright"></td>' +
			'		</tr>' +
			'		<tr>' +
			'			<td class="window_bottomleft"></td>' +
			'			<td class="window_bottom">' +
			'				' +
			'			</td>' +
			'			<td class="window_bottomright">' +
			'				<img class="window_resize" src="images/sizer.gif" />' +
			'			</td>' +
			'		</tr>' +
			'	</table>' +
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
		win.style.top = options.top + 'px';
		
		// attach a listener to the close image
		var img = $$('#' + this.handle + ' .window_close');
		Event.observe(img[0], 'click', this.close.bind(this), false);
		
		this.mouseDownHandler = this.mouseDown.bindAsEventListener(this);
		this.mouseMoveHandler = this.mouseMove.bindAsEventListener(this);
		this.mouseUpHandler = this.mouseUp.bindAsEventListener(this);
		
		this.resizer = $$('#' + this.handle + ' .window_resize')[0];
		this.title = $(this.handle + '_title');
		Event.observe(this.resizer, 'mousedown', this.mouseDownHandler);
		Event.observe(this.title, 'mousedown', this.mouseDownHandler);
//		new Draggable(this.handle, { handle: $(this.handle + '_title') });
	},
	
	setContent: function (content) {
		$(this.contentId).innerHTML = content;
		
		var win = $(this.handle);
		win.style.visibility = 'hidden';
		win.style.display = '';
		this.resize(Element.getDimensions(win));
		win.style.display = 'none';
		win.style.visibility = '';
	},
	
	resize: function(dim) {
		var content = $(this.contentId);
		var newHeight = dim.height - content.offsetTop - 24;
		if (this.resizeHandler)
			this.resizeHandler({width: content.offsetWidth, height: newHeight});
		content.style.height = newHeight  + 'px';
	},
	
	display: function() {
		new Effect.Appear(this.handle);
	},
	
	close: function() {
		document.body.removeChild($(this.handle));
		if (this.closeHandler)
			this.closeHandler(this.handle);
	},
	
	getContentId: function() {
		return this.contentId;
	},
	
	mouseDown: function (event) {
		var source = Event.element(event);
		if (source == this.title)
			this.doMove = true;
		else
			this.doMove = false;
		$(this.handle).style.zIndex = FloatingWindow.__zIndex++;
		this.pointer = [Event.pointerX(event), Event.pointerY(event)];
		Event.observe(document, 'mousemove', this.mouseMoveHandler);
		Event.observe(document, 'mouseup', this.mouseUpHandler);
		Event.stop(event);
	},
	
	mouseMove: function (event) {
		var pointer = [Event.pointerX(event), Event.pointerY(event)];
		var dx = pointer[0] - this.pointer[0];
		var dy = pointer[1] - this.pointer[1];

		this.pointer = pointer;
		var win = $(this.handle);
		if (this.doMove) {
			var left = parseFloat(Element.getStyle(win, 'left'));
			var top = parseFloat(Element.getStyle(win, 'top'));
			left += dx;
			top += dy;
			win.style.left = left + 'px';
			win.style.top = top + 'px';
		} else {
			var width = parseFloat(Element.getStyle(win, 'width'));
			var height = parseFloat(Element.getStyle(win, 'height'));
			
			width += dx;
			height += dy;
			
			win.style.width = width + 'px';
			win.style.height = height + 'px';
			
			this.resize({height: height, width: width});
		}
		Event.stop(event);
	},
	
	mouseUp: function (event) {
		Event.stopObserving(document, 'mousemove', this.mouseMoveHandler);
		Event.stopObserving(document, 'mouseup', this.mouseUpHandler);
		var pointer = [Event.pointerX(event), Event.pointerY(event)];
		Event.stop(event);
	}
};

var ModalDialog = Class.create();

ModalDialog.OK = 0;
ModalDialog.CANCEL = 1;

ModalDialog.prototype = {
	
	mResult: null,
	mCallback: null,
	
	initialize: function (title, content, options, callback) {
		this.mCallback = callback;
		var pageDim = window.getPageSize();
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
		this.win = new FloatingWindow(title, options);
		this.win.setContent(html);
		
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

Object.extend(window, {
	getPageSize: function () {
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
});