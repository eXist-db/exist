// $ANTLR 2.7.4: "DeclScanner.g" -> "DeclScanner.java"$

	package org.exist.xquery.parser;
	
	import org.exist.xquery.XPathException;

import antlr.NoViableAltException;
import antlr.ParserSharedInputState;
import antlr.RecognitionException;
import antlr.Token;
import antlr.TokenBuffer;
import antlr.TokenStream;
import antlr.TokenStreamException;

/**
 * Try to read the XQuery declaration. The purpose of this class is to determine
 * the content encoding of an XQuery. It just reads until it finds an XQuery declaration
 * and throws an XPathException afterwards. It also throws a RecognitionException
 * if something else than a comment, a pragma or an XQuery declaration is found.
 * 
 * The declared encoding can then be retrieved from getEncoding().
 */
public class DeclScanner extends antlr.LLkParser       implements DeclScannerTokenTypes
 {

	private String encoding = null;
	
	public String getEncoding() {
		return encoding;
	}

protected DeclScanner(TokenBuffer tokenBuf, int k) {
  super(tokenBuf,k);
  tokenNames = _tokenNames;
}

public DeclScanner(TokenBuffer tokenBuf) {
  this(tokenBuf,1);
}

protected DeclScanner(TokenStream lexer, int k) {
  super(lexer,k);
  tokenNames = _tokenNames;
}

public DeclScanner(TokenStream lexer) {
  this(lexer,1);
}

public DeclScanner(ParserSharedInputState state) {
  super(state,1);
  tokenNames = _tokenNames;
}

	public final void versionDecl() throws RecognitionException, TokenStreamException, XPathException {
		
		Token  v = null;
		Token  enc = null;
		
		match(LITERAL_xquery);
		match(LITERAL_version);
		v = LT(1);
		match(STRING_LITERAL);
		{
		switch ( LA(1)) {
		case LITERAL_encoding:
		{
			match(LITERAL_encoding);
			enc = LT(1);
			match(STRING_LITERAL);
			
						encoding = enc.getText();
					
			break;
		}
		case EOF:
		{
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		}
		
				throw new XPathException("Processing stopped");
			
	}
	
	
	public static final String[] _tokenNames = {
		"<0>",
		"EOF",
		"<2>",
		"NULL_TREE_LOOKAHEAD",
		"QNAME",
		"PREDICATE",
		"FLWOR",
		"PARENTHESIZED",
		"ABSOLUTE_SLASH",
		"ABSOLUTE_DSLASH",
		"WILDCARD",
		"PREFIX_WILDCARD",
		"FUNCTION",
		"UNARY_MINUS",
		"UNARY_PLUS",
		"XPOINTER",
		"XPOINTER_ID",
		"VARIABLE_REF",
		"VARIABLE_BINDING",
		"ELEMENT",
		"ATTRIBUTE",
		"ATTRIBUTE_CONTENT",
		"TEXT",
		"VERSION_DECL",
		"NAMESPACE_DECL",
		"DEF_NAMESPACE_DECL",
		"DEF_COLLATION_DECL",
		"DEF_FUNCTION_NS_DECL",
		"GLOBAL_VAR",
		"FUNCTION_DECL",
		"PROLOG",
		"OPTION",
		"ATOMIC_TYPE",
		"MODULE",
		"ORDER_BY",
		"GROUP_BY",
		"POSITIONAL_VAR",
		"BEFORE",
		"AFTER",
		"MODULE_DECL",
		"MODULE_IMPORT",
		"SCHEMA_IMPORT",
		"ATTRIBUTE_TEST",
		"COMP_ELEM_CONSTRUCTOR",
		"COMP_ATTR_CONSTRUCTOR",
		"COMP_TEXT_CONSTRUCTOR",
		"COMP_COMMENT_CONSTRUCTOR",
		"COMP_PI_CONSTRUCTOR",
		"COMP_NS_CONSTRUCTOR",
		"COMP_DOC_CONSTRUCTOR",
		"PRAGMA",
		"\"xpointer\"",
		"opening parenthesis '('",
		"closing parenthesis ')'",
		"name",
		"\"xquery\"",
		"\"version\"",
		"semicolon ';'",
		"\"module\"",
		"\"namespace\"",
		"=",
		"string literal",
		"\"declare\"",
		"\"default\"",
		"\"boundary-space\"",
		"\"ordering\"",
		"\"construction\"",
		"\"base-uri\"",
		"\"copy-namespaces\"",
		"\"option\"",
		"\"function\"",
		"\"variable\"",
		"\"import\"",
		"\"encoding\"",
		"\"collation\"",
		"\"element\"",
		"\"order\"",
		"\"empty\"",
		"\"greatest\"",
		"\"least\"",
		"\"preserve\"",
		"\"strip\"",
		"\"ordered\"",
		"\"unordered\"",
		"COMMA",
		"\"no-preserve\"",
		"\"inherit\"",
		"\"no-inherit\"",
		"dollar sign '$'",
		"opening curly brace '{'",
		"closing curly brace '{'",
		"COLON",
		"\"external\"",
		"\"at\"",
		"\"schema\"",
		"\"as\"",
		"\"empty-sequence\"",
		"question mark '?'",
		"wildcard '*'",
		"+",
		"\"item\"",
		"\"for\"",
		"\"let\"",
		"\"some\"",
		"\"every\"",
		"\"if\"",
		"\"typeswitch\"",
		"\"update\"",
		"\"replace\"",
		"\"value\"",
		"\"insert\"",
		"\"delete\"",
		"\"rename\"",
		"\"with\"",
		"\"into\"",
		"\"preceding\"",
		"\"following\"",
		"\"where\"",
		"\"return\"",
		"\"in\"",
		"\"by\"",
		"\"ascending\"",
		"\"descending\"",
		"\"group\"",
		"\"satisfies\"",
		"\"case\"",
		"\"then\"",
		"\"else\"",
		"\"or\"",
		"\"and\"",
		"\"instance\"",
		"\"of\"",
		"\"treat\"",
		"\"castable\"",
		"\"cast\"",
		"<",
		">",
		"\"eq\"",
		"\"ne\"",
		"\"lt\"",
		"\"le\"",
		"\"gt\"",
		"\"ge\"",
		"!=",
		">=",
		"<=",
		"\"is\"",
		"\"isnot\"",
		"fulltext operator '&='",
		"fulltext operator '|='",
		"\"to\"",
		"-",
		"\"div\"",
		"\"idiv\"",
		"\"mod\"",
		"PRAGMA_START",
		"pragma expression",
		"\"union\"",
		"union",
		"\"intersect\"",
		"\"except\"",
		"single slash '/'",
		"double slash '//'",
		"\"text\"",
		"\"node\"",
		"\"attribute\"",
		"\"comment\"",
		"\"processing-instruction\"",
		"\"document-node\"",
		"\"document\"",
		".",
		"XML comment",
		"processing instruction",
		"opening brace '['",
		"closing brace ']'",
		"@ char",
		"..",
		"\"child\"",
		"\"self\"",
		"\"descendant\"",
		"\"descendant-or-self\"",
		"\"following-sibling\"",
		"\"parent\"",
		"\"ancestor\"",
		"\"ancestor-or-self\"",
		"\"preceding-sibling\"",
		"DOUBLE_LITERAL",
		"DECIMAL_LITERAL",
		"INTEGER_LITERAL",
		"\"schema-element\"",
		"XML end tag",
		"double quote '\\\"'",
		"single quote '",
		"QUOT_ATTRIBUTE_CONTENT",
		"ESCAPE_QUOT",
		"APOS_ATTRIBUTE_CONTENT",
		"ESCAPE_APOS",
		"ELEMENT_CONTENT",
		"end of XML comment",
		"end of processing instruction",
		"CDATA section",
		"\"collection\"",
		"\"validate\"",
		"start of processing instruction",
		"CDATA section start",
		"end of CDATA section",
		"LETTER",
		"DIGITS",
		"HEX_DIGITS",
		"NMSTART",
		"NMCHAR",
		"WS",
		"XQuery comment",
		"PREDEFINED_ENTITY_REF",
		"CHAR_REF",
		"S",
		"NEXT_TOKEN",
		"CHAR",
		"BASECHAR",
		"IDEOGRAPHIC",
		"COMBINING_CHAR",
		"DIGIT",
		"EXTENDER"
	};
	
	
	}
