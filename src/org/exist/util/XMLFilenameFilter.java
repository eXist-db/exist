
package org.exist.util;

import java.io.FilenameFilter;
import java.io.File;

import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.PatternCompiler;
import org.apache.oro.text.regex.PatternMatcher;
import org.apache.oro.text.regex.Perl5Compiler;
import org.apache.oro.text.regex.Perl5Matcher;

public class XMLFilenameFilter implements FilenameFilter {

    protected static String[] extensions = { "xml", "xsp", "xsl", "rdf" };
    protected String pathSep;
	protected Pattern pattern;
	protected PatternMatcher matcher = new Perl5Matcher();

    public static void setExtensions(String[] extensionList) {
		extensions = extensionList;
    }
    
    public XMLFilenameFilter() {
		pathSep = System.getProperty("file.separator", "/");
    }

    public XMLFilenameFilter(String regexp) 
    	throws MalformedPatternException {
    	PatternCompiler compiler = new Perl5Compiler();
    	PatternMatcher matcher = new Perl5Matcher();
    	pattern = compiler.compile( regexp);
    }

    public boolean accept(File dir, String name) {
	if(pattern == null) {
	    for(int i = 0; i < extensions.length; i++)
		if(name.endsWith("." + extensions[i]))
		    return true;
	} else
	    return matcher.matches(name, pattern);
	return false;
    }
}
