header {
	package org.exist.xquery.parser;
	
	import org.exist.xquery.XPathException;
}

/**
 * Try to read the XQuery declaration. The purpose of this class is to determine
 * the content encoding of an XQuery. It just reads until it finds an XQuery declaration
 * and throws an XPathException afterwards. It also throws a RecognitionException
 * if something else than a comment, a pragma or an XQuery declaration is found.
 * 
 * The declared encoding can then be retrieved from getEncoding().
 */
class DeclScanner extends Parser;

options {
	defaultErrorHandler= false;
	k= 1;
	importVocab=XQuery;
}
{
	private String encoding = null;
	private String version = null;
	private String moduleNamespace = null;
	private String modulePrefix = null;

	public String getEncoding() {
		return encoding;
	}
	
	public String getVersion() {
		return  version;
	}

	public String getNamespace() {
		return moduleNamespace;
	}

	public String getPrefix() {
		return modulePrefix;
	}
}

versionDecl throws XPathException
:
	(
		"xquery" "version" v:STRING_LITERAL
		( 
			"encoding" enc:STRING_LITERAL
			{
				encoding = enc.getText();
			}
		)?
		SEMICOLON
		{
			version = v.getText();
		}
	)?
	( 
		"module" "namespace" prefix:NCNAME EQ uri:STRING_LITERAL SEMICOLON
		{
			modulePrefix = prefix.getText();
			moduleNamespace = uri.getText();
		}
	)?
	{
		throw new XPathException("Processing stopped");
	}
	;
