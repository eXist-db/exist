/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
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
	// True while nothing but the line prefix (" : ") has been seen on the current line.
	// An xqdoc tag is only a tag at that position; an '@' anywhere else is ordinary prose.
	boolean lineStart = true;
}:
	(
		TRIM { buf.append('\n'); lineStart = true; }
		|
		SIMPLE_COLON { 
			if (buf.length()>0 && buf.charAt(buf.length() - 1) != '\n')
				buf.append(':');
		}
		|
		c:CHARS {
			buf.append(c.getText());
			if (!c.getText().isBlank()) {
				lineStart = false;
			}
		}
		|
		// A tag only opens at the start of a line AND only for a tag xqDoc defines.
		// Anything else -- '@name' mid-prose, or a line opening with prose like
		// "@home is where the heart is." -- is ordinary text.
		{ !lineStart || !XQDocHelper.isKnownTag(LT(1).getText()) }?
		t:TAG { buf.append(t.getText()); lineStart = false; }
		|
		// a bare '@' is never a tag; no other rule accepts this token
		AT { buf.append('@'); lineStart = false; }
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