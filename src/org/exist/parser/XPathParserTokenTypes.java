// $ANTLR 2.7.2rc2 (20030105): "XPathParser.g" -> "XPathLexer.java"$

	package org.exist.parser;
	
	import org.exist.xpath.*;
	import org.exist.*;
	import org.exist.util.*;
	import org.exist.storage.*;
	import org.exist.dom.*;
    import org.exist.storage.analysis.Tokenizer;
    import org.exist.security.User;
    import org.exist.security.PermissionDeniedException;
	import org.w3c.dom.*;
	import java.util.ArrayList;
	import java.util.Iterator;
	import java.util.StringTokenizer;
	import org.apache.log4j.BasicConfigurator;
	import java.io.StringReader;

public interface XPathParserTokenTypes {
	int EOF = 1;
	int NULL_TREE_LOOKAHEAD = 3;
	int LITERAL_xpointer = 4;
	int LPAREN = 5;
	int RPAREN = 6;
	int ID = 7;
	int LITERAL_or = 8;
	int LITERAL_and = 9;
	int CONST = 10;
	int ANDEQ = 11;
	int OREQ = 12;
	int EQ = 13;
	int NEQ = 14;
	int UNION = 15;
	int LT = 16;
	int GT = 17;
	int LTEQ = 18;
	int GTEQ = 19;
	int PLUS = 20;
	int LITERAL_doctype = 21;
	int LITERAL_document = 22;
	int STAR = 23;
	int COMMA = 24;
	int LITERAL_collection = 25;
	int LITERAL_xcollection = 26;
	int INT = 27;
	int LITERAL_text = 28;
	int SLASH = 29;
	int DSLASH = 30;
	int LPPAREN = 31;
	// "starts-with" = 32
	// "ends-with" = 33
	int LITERAL_contains = 34;
	int LITERAL_match = 35;
	int LITERAL_near = 36;
	int FUNC = 37;
	int ATTRIB = 38;
	int ATTRIB_STAR = 39;
	int LITERAL_node = 40;
	int PARENT = 41;
	int SELF = 42;
	int RPPAREN = 43;
	int WS = 44;
	int BASECHAR = 45;
	int IDEOGRAPHIC = 46;
	int DIGIT = 47;
	int NMSTART = 48;
	int NMCHAR = 49;
	int NCNAME = 50;
	int ID_OR_FUNC = 51;
	int VARIABLE = 52;
}
