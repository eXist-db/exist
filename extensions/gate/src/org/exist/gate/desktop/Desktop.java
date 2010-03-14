package org.exist.gate.desktop;

import java.io.File;
import java.io.IOException;

public abstract class Desktop {
	
    Desktop(){}

    public static Desktop getDesktop(){

        String os = System.getProperty("os.name").toLowerCase();

        Desktop desktop;

        if ( os.indexOf("windows") != -1 || os.indexOf("nt") != -1){

            desktop = new WindowsDesktop();

        } else if ( os.indexOf("mac") != -1 ) {

            desktop = new OSXDesktop();

        } else desktop = new XDesktop();

        return desktop;
    }
    
    protected abstract String openFileCmd();
    	
    public void open(File file) throws IOException{
    	String cmd = String.format(openFileCmd() + " %s", file.getAbsolutePath());
    	Runtime.getRuntime().exec(cmd);
    }

}
