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
	int LITERAL_false = 26;
	int LITERAL_true = 27;
	int INT = 28;
	int LITERAL_text = 29;
	int SLASH = 30;
	int DSLASH = 31;
	int LPPAREN = 32;
	// "starts-with" = 33
	// "ends-with" = 34
	int LITERAL_contains = 35;
	int LITERAL_match = 36;
	int LITERAL_near = 37;
	int FUNC = 38;
	int ATTRIB = 39;
	int ATTRIB_STAR = 40;
	int LITERAL_node = 41;
	int PARENT = 42;
	int SELF = 43;
	int RPPAREN = 44;
	int WS = 45;
	int BASECHAR = 46;
	int IDEOGRAPHIC = 47;
	int DIGIT = 48;
	int NMSTART = 49;
	int NMCHAR = 50;
	int NCNAME = 51;
	int ID_OR_FUNC = 52;
	int VARIABLE = 53;
}
