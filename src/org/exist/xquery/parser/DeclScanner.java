// $ANTLR 2.7.7 (2006-11-01): "DeclScanner.g" -> "DeclScanner.java"$

	package org.exist.xquery.parser;
	
	import org.exist.xquery.XPathException;

import antlr.TokenBuffer;
import antlr.TokenStreamException;
import antlr.TokenStreamIOException;
import antlr.ANTLRException;
import antlr.LLkParser;
import antlr.Token;
import antlr.TokenStream;
import antlr.RecognitionException;
import antlr.NoViableAltException;
import antlr.MismatchedTokenException;
import antlr.SemanticException;
import antlr.ParserSharedInputState;
import antlr.collections.impl.BitSet;

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
		Token  prefix = null;
		Token  uri = null;
		
		{
		switch ( LA(1)) {
		case LITERAL_xquery:
		{
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
			case SEMICOLON:
			{
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			}
			match(SEMICOLON);
			
						version = v.getText();
					
			break;
		}
		case EOF:
		case LITERAL_module:
		{
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		}
		{
		switch ( LA(1)) {
		case LITERAL_module:
		{
			match(LITERAL_module);
			match(LITERAL_namespace);
			prefix = LT(1);
			match(NCNAME);
			match(EQ);
			uri = LT(1);
			match(STRING_LITERAL);
			match(SEMICOLON);
			
						modulePrefix = prefix.getText();
						moduleNamespace = uri.getText();
					
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
		"qname",
		"EQNAME",
		"PREDICATE",
		"FLWOR",
		"PARENTHESIZED",
		"ABSOLUTE_SLASH",
		"ABSOLUTE_DSLASH",
		"WILDCARD",
		"PREFIX_WILDCARD",
		"FUNCTION",
		"DYNAMIC_FCALL",
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
		"CONTEXT_ITEM_DECL",
		"ANNOT_DECL",
		"GLOBAL_VAR",
		"FUNCTION_DECL",
		"INLINE_FUNCTION_DECL",
		"FUNCTION_INLINE",
		"FUNCTION_TEST",
		"MAP",
		"MAP_TEST",
		"LOOKUP",
		"ARRAY",
		"ARRAY_TEST",
		"PROLOG",
		"OPTION",
		"ATOMIC_TYPE",
		"MODULE",
		"ORDER_BY",
		"GROUP_BY",
		"POSITIONAL_VAR",
		"CATCH_ERROR_CODE",
		"CATCH_ERROR_DESC",
		"CATCH_ERROR_VAL",
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
		"GTEQ",
		"SEQUENCE",
		"\"xpointer\"",
		"opening parenthesis '('",
		"closing parenthesis ')'",
		"ncname",
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
		"\"context\"",
		"\"item\"",
		"MOD",
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
		"\"external\"",
		"COLON",
		"opening curly brace '{'",
		"closing curly brace '}'",
		"\"schema\"",
		"braced uri literal",
		"\"as\"",
		"\"at\"",
		"\"empty-sequence\"",
		"question mark '?'",
		"wildcard '*'",
		"+",
		"\"map\"",
		"\"array\"",
		"\"for\"",
		"\"let\"",
		"\"try\"",
		"\"some\"",
		"\"every\"",
		"\"if\"",
		"\"switch\"",
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
		"\"catch\"",
		"union",
		"\"return\"",
		"\"where\"",
		"\"in\"",
		"\"allowing\"",
		"\"by\"",
		"\"stable\"",
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
		"BEFORE",
		"AFTER",
		"\"eq\"",
		"\"ne\"",
		"\"lt\"",
		"\"le\"",
		"\"gt\"",
		"\"ge\"",
		">",
		"!=",
		"<",
		"<=",
		"\"is\"",
		"\"isnot\"",
		"||",
		"\"to\"",
		"-",
		"\"div\"",
		"\"idiv\"",
		"\"mod\"",
		"BANG",
		"PRAGMA_START",
		"pragma expression",
		"\"union\"",
		"\"intersect\"",
		"\"except\"",
		"single slash '/'",
		"double slash '//'",
		"\"text\"",
		"\"node\"",
		"\"attribute\"",
		"\"comment\"",
		"\"namespace-node\"",
		"\"processing-instruction\"",
		"\"document-node\"",
		"\"document\"",
		"HASH",
		".",
		"XML comment",
		"processing instruction",
		"opening brace '['",
		"start of string constructor",
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
		"arrow operator",
		"INTEGER_LITERAL",
		"start of string constructor",
		"string constructor content",
		"start of interpolated expression",
		"end of interpolated expression",
		"DOUBLE_LITERAL",
		"DECIMAL_LITERAL",
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
		"WS",
		"XQuery XQDoc comment",
		"XQuery comment",
		"PREDEFINED_ENTITY_REF",
		"CHAR_REF",
		"S",
		"NEXT_TOKEN",
		"NAME_START_CHAR",
		"NAME_CHAR",
		"CHAR",
		"BASECHAR",
		"IDEOGRAPHIC",
		"COMBINING_CHAR",
		"DIGIT",
		"EXTENDER"
	};
	
	
	}
