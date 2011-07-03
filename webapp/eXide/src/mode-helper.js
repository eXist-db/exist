/**
 * 
 */
eXide.namespace("eXide.edit.ModeHelper");

/**
 * Base class for helper methods needed by specific editing modes (like XQuery, XML...)
 */
eXide.edit.ModeHelper = (function () {
	
	Constr = function(editor) {
		this.parent = editor;
		this.editor = this.parent.editor;
		
		this.commands = {};
	}
	
	Constr.prototype = {

		/**
		 * Add a command which can be invoked dynamically by the editor
		 */
		addCommand: function (name, func) {
			if (!this.commands) {
				this.commands = {};
			}
			this.commands[name] = func;
		},
		
		/**
		 * Dynamically call a method of this class.
		 */
		exec: function (command, doc, args) {
			if (this.commands && this.commands[command]) {
				var nargs = [doc];
				for (var i = 0; i < args.length; i++) {
					nargs.push(args[i]);
				}
				$.log("Calling command %s ...", command);
				this.commands[command].apply(this, nargs);
			}
		}
	};
	
	return Constr;
}());