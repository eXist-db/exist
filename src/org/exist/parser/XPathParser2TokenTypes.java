// $ANTLR : "XPathParser2.g" -> "XPathParser2.java"$

	package org.exist.parser;
	
	import antlr.debug.misc.*;
	import java.io.StringReader;
	import java.io.BufferedReader;
	import java.io.InputStreamReader;
	import java.util.Vector;
	import java.util.ArrayList;
	import java.util.Iterator;
	import org.exist.storage.BrokerPool;
	import org.exist.storage.DBBroker;
	import org.exist.storage.analysis.Tokenizer;
	import org.exist.EXistException;
	import org.exist.dom.DocumentSet;
	import org.exist.dom.DocumentImpl;
	import org.exist.dom.QName;
    import org.exist.security.PermissionDeniedException;
    import org.exist.security.User;
	import org.exist.xpath.*;
	import org.exist.xpath.value.Type;

public interface XPathParser2TokenTypes {
	int EOF = 1;
	int NULL_TREE_LOOKAHEAD = 3;
	int QNAME = 4;
	int PREDICATE = 5;
	int PARENTHESIZED = 6;
	int ABSOLUTE_SLASH = 7;
	int ABSOLUTE_DSLASH = 8;
	int WILDCARD = 9;
	int PREFIX_WILDCARD = 10;
	int FUNCTION = 11;
	int UNARY_MINUS = 12;
	int UNARY_PLUS = 13;
	int XPOINTER = 14;
	int XPOINTER_ID = 15;
	int LITERAL_xpointer = 16;
	int LPAREN = 17;
	int RPAREN = 18;
	int NCNAME = 19;
	int LITERAL_or = 20;
	int LITERAL_and = 21;
	int EQ = 22;
	int NEQ = 23;
	int GT = 24;
	int GTEQ = 25;
	int LT = 26;
	int LTEQ = 27;
	int ANDEQ = 28;
	int OREQ = 29;
	int STRING_LITERAL = 30;
	int PLUS = 31;
	int MINUS = 32;
	int STAR = 33;
	int LITERAL_div = 34;
	int LITERAL_mod = 35;
	int UNION = 36;
	int SLASH = 37;
	int DSLASH = 38;
	int LITERAL_text = 39;
	int LITERAL_node = 40;
	int SELF = 41;
	int LPPAREN = 42;
	int RPPAREN = 43;
	int COLON = 44;
	int AT = 45;
	int PARENT = 46;
	int LITERAL_child = 47;
	int LITERAL_self = 48;
	int LITERAL_attribute = 49;
	int LITERAL_descendant = 50;
	// "descendant-or-self" = 51
	// "following-sibling" = 52
	int LITERAL_parent = 53;
	int LITERAL_ancestor = 54;
	// "ancestor-or-self" = 55
	// "preceding-sibling" = 56
	int DOUBLE_LITERAL = 57;
	int DECIMAL_LITERAL = 58;
	int INTEGER_LITERAL = 59;
	int COMMA = 60;
	int BASECHAR = 61;
	int IDEOGRAPHIC = 62;
	int DIGIT = 63;
	int DIGITS = 64;
	int NMSTART = 65;
	int NMCHAR = 66;
	int WS = 67;
	int INTEGER_DECIMAL_PARENT = 68;
	int VARIABLE = 69;
}
