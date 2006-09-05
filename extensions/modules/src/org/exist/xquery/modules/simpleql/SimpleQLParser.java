// $ANTLR 2.7.4: "SimpleQLParser.g" -> "SimpleQLParser.java"$

	package org.exist.xquery.modules.simpleql;

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

public class SimpleQLParser extends antlr.LLkParser       implements SimpleQLParserTokenTypes
 {

protected SimpleQLParser(TokenBuffer tokenBuf, int k) {
  super(tokenBuf,k);
  tokenNames = _tokenNames;
}

public SimpleQLParser(TokenBuffer tokenBuf) {
  this(tokenBuf,1);
}

protected SimpleQLParser(TokenStream lexer, int k) {
  super(lexer,k);
  tokenNames = _tokenNames;
}

public SimpleQLParser(TokenStream lexer) {
  this(lexer,1);
}

public SimpleQLParser(ParserSharedInputState state) {
  super(state,1);
  tokenNames = _tokenNames;
}

	public final String  expr() throws RecognitionException, TokenStreamException {
		String str;
		
		
			StringBuffer buf = new StringBuffer();
			String s1, s2;
		
		
		s1=orExpr();
		buf.append(s1);
		{
		_loop3:
		do {
			if ((LA(1)==LITERAL_and)) {
				match(LITERAL_and);
				s2=orExpr();
				
							buf.append(" and ");
							buf.append(s2);
						
			}
			else {
				break _loop3;
			}
			
		} while (true);
		}
		str = buf.toString();
		return str;
	}
	
	public final String  orExpr() throws RecognitionException, TokenStreamException {
		String str;
		
		
			StringBuffer buf = new StringBuffer();
			String s1, s2;
		
		
		s1=queryArg();
		buf.append(s1);
		{
		_loop6:
		do {
			if ((LA(1)==LITERAL_or)) {
				match(LITERAL_or);
				s2=queryArg();
				
							buf.append(" or ");
							buf.append(s2);
						
			}
			else {
				break _loop6;
			}
			
		} while (true);
		}
		str = buf.toString();
		return str;
	}
	
	public final String  queryArg() throws RecognitionException, TokenStreamException {
		String arg;
		
		Token  l = null;
		Token  w = null;
		
			StringBuffer buf = new StringBuffer();
		
		
		switch ( LA(1)) {
		case STRING_LITERAL:
		{
			l = LT(1);
			match(STRING_LITERAL);
				
					buf.append("near(., \"");
					buf.append(l.getText());
					buf.append("\")");
					arg = buf.toString();
				
			break;
		}
		case WORD:
		{
			w = LT(1);
			match(WORD);
			
					buf.append(". &= \"");
					buf.append(w.getText());
					buf.append('"');
					arg = buf.toString();
				
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		return arg;
	}
	
	
	public static final String[] _tokenNames = {
		"<0>",
		"EOF",
		"<2>",
		"NULL_TREE_LOOKAHEAD",
		"\"and\"",
		"\"or\"",
		"string literal",
		"WORD",
		"WS",
		"BASECHAR",
		"IDEOGRAPHIC",
		"COMBINING_CHAR",
		"DIGIT",
		"EXTENDER"
	};
	
	
	}
