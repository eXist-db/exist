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
	int NCNAME = 7;
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
	int SLASH = 34;
	int DSLASH = 35;
	int AT = 36;
	int ATTRIB_STAR = 37;
	int LITERAL_node = 38;
	int PARENT = 39;
	int SELF = 40;
	int COLON = 41;
	int LITERAL_descendant = 42;
	// "descendant-or-self" = 43
	int LITERAL_child = 44;
	int LITERAL_parent = 45;
	int LITERAL_self = 46;
	int LITERAL_attribute = 47;
	int LITERAL_ancestor = 48;
	// "ancestor-or-self" = 49
	// "following-sibling" = 50
	// "preceding-sibling" = 51
	int LPPAREN = 52;
	int RPPAREN = 53;
	int WS = 54;
	int BASECHAR = 55;
	int IDEOGRAPHIC = 56;
	int DIGIT = 57;
	int NMSTART = 58;
	int NMCHAR = 59;
	int VARIABLE = 60;
}
