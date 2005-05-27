
package org.exist.util;

import java.io.FilenameFilter;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XMLFilenameFilter implements FilenameFilter {

    protected static String[] extensions = { "xml", "xsp", "xsl", "rdf" };
    protected String pathSep;
	protected Matcher matcher = null;

    public static void setExtensions(String[] extensionList) {
		extensions = extensionList;
    }
    
    public XMLFilenameFilter() {
		pathSep = System.getProperty("file.separator", "/");
    }

    public XMLFilenameFilter(String regexp) {
    	Pattern pattern = Pattern.compile(regexp);
    	matcher = pattern.matcher("");
    }

    public boolean accept(File dir, String name) {
	if(matcher == null) {
	    for(int i = 0; i < extensions.length; i++)
		if(name.endsWith("." + extensions[i]))
		    return true;
	} else {
        matcher.reset(name);
	    return matcher.matches();
    }
	return false;
    }
}
