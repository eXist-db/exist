header {
	package org.exist.xquery.xqdoc.parser;

	import org.exist.xquery.xqdoc.XQDocHelper;
}

class XQDocParser extends Parser;

options {
	k = 1;
}

xqdocComment[XQDocHelper doc]
{ String c; }
: 
	XQDOC_START
	(
		( TAG ) => taggedContents[doc]
		|
		c=contents {
			doc.addDescription(c);
		}
	)*
	XQDOC_END;

taggedContents[XQDocHelper doc]
{
	String c;
}
: 
	t:TAG
	c=contents
	{
		doc.setTag(t.getText(), c);
	}
	;

contents returns [String content]
{
	content = null;
	StringBuilder buf = new StringBuilder();
}:
	(
		TRIM { buf.append('\n'); }
		|
		SIMPLE_COLON { 
			if (buf.length()>0 && buf.charAt(buf.length() - 1) != '\n')
				buf.append(':');
		}
		|
		c:CHARS { buf.append(c.getText()); }
	)+
	{ content = buf.toString(); }
	;

class XQDocLexer extends Lexer;
options {
	k = 4;
	testLiterals = true;
	charVocabulary = '\u0003'..'\uFFFE';
}

XQDOC_START: "(:~";
XQDOC_END: ":)";

AT: '@';

CHARS: (~('\n' | '@' | ':'))+;

TRIM: '\n' ('\t' | ' ')*;

SIMPLE_COLON: (':' ~(')')) => ':';

TAG: '@' ('A'..'Z' | 'a'..'z' | '0'..'9')+;