// $ANTLR : "XPathParser.g" -> "XPathLexer.java"$

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
	// "starts-with" = 29
	// "ends-with" = 30
	int LITERAL_contains = 31;
	int LITERAL_match = 32;
	int LITERAL_near = 33;
	int NCNAME = 34;
	int SLASH = 35;
	int DSLASH = 36;
	int AT = 37;
	int ATTRIB_STAR = 38;
	int LITERAL_node = 39;
	int PARENT = 40;
	int SELF = 41;
	int COLON = 42;
	int LITERAL_ancestor = 43;
	int LPPAREN = 44;
	int RPPAREN = 45;
	int WS = 46;
	int BASECHAR = 47;
	int IDEOGRAPHIC = 48;
	int DIGIT = 49;
	int NMSTART = 50;
	int NMCHAR = 51;
	int VARIABLE = 52;
}
