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
	
	public String getEncoding() {
		return encoding;
	}
}

versionDecl throws XPathException
:
	"xquery" "version" v:STRING_LITERAL
	( 
		"encoding" enc:STRING_LITERAL!
		{
			encoding = enc.getText();
		}
	)?
	{
		throw new XPathException("Processing stopped");
	}
	;