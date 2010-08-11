/**
 * Plugin designed for test prupose. It add a button (that manage an alert) and a select (that allow to insert tags) in the toolbar.
 * This plugin also disable the "f" key in the editarea, and load a CSS and a JS file
 */  
var EditArea_sandbox= {
	/**
	 * Get called once this file is loaded (editArea still not initialized)
	 *
	 * @return nothing	 
	 */	 	 	
	init: function() {
		editArea.load_css(this.baseURL+"css/sandbox.css");
		
		this.selection = null;
	}

	/**
	 * Returns the HTML code for a specific control string or false if this plugin doesn't have that control.
	 * A control can be a button, select list or any other HTML item to present in the EditArea user interface.
	 * Language variables such as {$lang_somekey} will also be replaced with contents from
	 * the language packs.
	 * 
	 * @param {string} ctrl_name: the name of the control to add	  
	 * @return HTML code for a specific control or false.
	 * @type string	or boolean
	 */	
	,get_control_html: function(ctrl_name){
		switch(ctrl_name){
			case "sandbox_exec":
				// Control id, button img, command
				return parent.editAreaLoader.get_button_html('sandbox_exec', 'transmit_go.png', 'sandbox_exec', false, this.baseURL);
			case "sandbox_new":
				return parent.editAreaLoader.get_button_html('sandbox_new', 'page_white.png', 'sandbox_new', false, this.baseURL);
			case "sandbox_compile":
				return parent.editAreaLoader.get_button_html('sandbox_compile', 'tick.png', 'sandbox_compile', false, this.baseURL);
		}
		return false;
	}
	
	/**
	 * Get called once EditArea is fully loaded and initialised
	 *	 
	 * @return nothing
	 */	 	 	
	,onload: function(){
		this.container	= document.createElement('div');
		this.container.id	= "sandbox_auto_completion_area";
		var div = document.getElementById("result");
		div.insertBefore(this.container, div.firstChild);
		parent.editAreaLoader.add_event(document, "click", function() { editArea.plugins['sandbox']._hide();});
	}
	
	/**
	 * Is called each time the user touch a keyboard key.
	 *	 
	 * @param (event) e: the keydown event
	 * @return true - pass to next handler in chain, false - stop chain execution
	 * @type boolean	 
	 */
	,onkeydown: function(e) {
		if (EA_keys[e.keyCode])
			letter=EA_keys[e.keyCode];
		else
			letter=String.fromCharCode(e.keyCode);
		if (this._isShown()) {
			e.preventDefault();
		    if (letter == "Esc") {
		    	this._hide();
		    	return false;
		    } else if (letter == "Down") {
		    	this._selectNext();
		    	return false;
		    } else if (letter == "Up") {
                this._selectPrevious();
                return false;
            } else if (letter == "Entrer") {
            	this.selection.click();
            	return false;
            }
		}
		
		if (CtrlPressed(e)) {
		    if (letter == "Entrer") {
		    	top.runQuery();
		    	return false;
		    } else if(letter== "Space") {
		    	this._autocomplete();
		    	return false;
		    } else if (letter == "S") {
		    	e.preventDefault();
		    	top.saveDocument();
		    	return false;
		    }
		}
		
		if (AltPressed(e) && letter == "Entrer") {
			top.checkQuery();
			return false;
		}
		
		return true;
	}
	
	/**
	 * Executes a specific command, this function handles plugin commands.
	 *
	 * @param {string} cmd: the name of the command being executed
	 * @param {unknown} param: the parameter of the command	 
	 * @return true - pass to next handler in chain, false - stop chain execution
	 * @type boolean	
	 */
	,execCommand: function(cmd, param){
		// Handle commands
		switch(cmd){
			case "sandbox_exec":
				top.runQuery();
				return false;
			case "sandbox_new":
				top.newDocument();
				return false;
			case "sandbox_compile":
				top.checkQuery();
				return false;
		}
		// Pass to next handler in chain
		return true;
	}
	
	,_autocomplete: function () {
		editArea.getIESelection();
		var start = editArea.textarea.selectionStart - 1;
		var str	= editArea.textarea.value;
		var end = start;
		while (start > 0) {
			var ch = str.substring(start, end + 1);
			if (!ch.match(/^[\w:\-_\.]+$/)) {
				start++;
				break;
			}
			start--;
		}
		if (start == end + 1)
			return;
		var token = str.substring(start, end + 1);
		top.message(token);
		this.startOffset = start;
		
		var cursor	= _$("cursor_pos");
		
		this.container.style.top		= ( cursor.cursor_top + editArea.lineHeight ) +"px";
		this.container.style.left		= ( cursor.cursor_left + 8 ) +"px";
		
		var self = this;
		top.functionLookup(token, function (data) {
			self.container.innerHTML = data;
			top.$("ul li", self.container).click(function () {
				self._select(top.$(this).text());
			});
			self.selection = top.$("ul li:first", self.container).addClass('selection');
		});
		
		this._show();
	}
	
	// hide the suggested box
	,_hide: function(){
		this.container.style.display = "none";
		this.shown	= false;
	}
	
	// display the suggested box
	,_show: function() {
		if(!this._isShown()) {
			this.container.style.display="block";
			this.shown = true;
		}
	}
	
	// is the suggested box displayed?
	,_isShown: function() {
		return this.shown;
	}
	
	,_selectNext: function () {
		if (this.selection == null)
			return;
		var next = this.selection.next();
		if (next.length > 0) {
		  this.selection.removeClass("selection");
		  next.addClass("selection");
		  this.selection = next;
		  
		  if (next.position().top + next.height() >= top.$(this.container).height())
			  top.$(this.container).scrollTop(top.$(this.container).scrollTop() + next.height() * 2);
        }
	}
	
	,_selectPrevious: function() {
        if (this.selection == null)
            return;
        var prev = this.selection.prev();
		if (prev.length > 0) {
            this.selection.removeClass("selection");
            prev.addClass("selection");
            this.selection = prev;
            
            if (prev.position().top + prev.height() <= 0)
            	top.$(this.container).scrollTop(top.$(this.container).scrollTop() - prev.height() * 2);
        }
	}
	
	,_select: function (content) {
		content = content.substring(0, content.indexOf('(')) + "()";
		
		editArea.getIESelection();
		
		parent.editAreaLoader.setSelectionRange(editArea.id, this.startOffset, 
				editArea.textarea.selectionEnd);
		
		parent.editAreaLoader.setSelectedText(editArea.id, content );
		range = parent.editAreaLoader.getSelectionRange(editArea.id);
		
		new_pos	= range["end"] - 1;
		parent.editAreaLoader.setSelectionRange(editArea.id, new_pos, new_pos);
		this._hide();
	}
	
	,_gotoLine: function (line) {
		alert(line);
	}
};

// Adds the plugin class to the list of available EditArea plugins
editArea.add_plugin("sandbox", EditArea_sandbox);