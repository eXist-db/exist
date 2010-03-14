package org.exist.gate.desktop;

public class WindowsDesktop extends Desktop {

	protected String openFileCmd() {
		return  "cmd /c start";
	}

}
