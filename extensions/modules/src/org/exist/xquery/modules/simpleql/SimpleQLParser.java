// $ANTLR 2.7.7 (2006-11-01): "SimpleQLParser.g" -> "SimpleQLParser.java"$

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
			if ((LA(1)==LITERAL_AND||LA(1)==LITERAL_UND)) {
				and();
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
		
		
		s1=notExpr();
		buf.append(s1);
		{
		_loop6:
		do {
			if ((LA(1)==LITERAL_OR||LA(1)==LITERAL_ODER)) {
				or();
				s2=notExpr();
				
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
	
	public final void and() throws RecognitionException, TokenStreamException {
		
		
		switch ( LA(1)) {
		case LITERAL_AND:
		{
			match(LITERAL_AND);
			break;
		}
		case LITERAL_UND:
		{
			match(LITERAL_UND);
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
	}
	
	public final String  notExpr() throws RecognitionException, TokenStreamException {
		String str;
		
		
			StringBuffer buf = new StringBuffer();
			String s;
		
		
		s=queryArg();
		buf.append(s);
		{
		_loop9:
		do {
			if ((LA(1)==LITERAL_NOT||LA(1)==LITERAL_NICHT)) {
				not();
				s=queryArg();
				
							buf.append(" and not(").append(s).append(")");
						
			}
			else {
				break _loop9;
			}
			
		} while (true);
		}
		str = buf.toString();
		return str;
	}
	
	public final void or() throws RecognitionException, TokenStreamException {
		
		
		switch ( LA(1)) {
		case LITERAL_OR:
		{
			match(LITERAL_OR);
			break;
		}
		case LITERAL_ODER:
		{
			match(LITERAL_ODER);
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
	}
	
	public final String  queryArg() throws RecognitionException, TokenStreamException {
		String arg;
		
		Token  l = null;
		Token  r = null;
		Token  w2 = null;
		
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
		case REGEXP:
		{
			r = LT(1);
			match(REGEXP);
			
					buf.append("match-all(., \"").append(r.getText()).append("\")"); 
					arg = buf.toString();
				
			break;
		}
		case EOF:
		case WORD:
		case LITERAL_AND:
		case LITERAL_UND:
		case LITERAL_OR:
		case LITERAL_ODER:
		case LITERAL_NOT:
		case LITERAL_NICHT:
		{
			{
			_loop12:
			do {
				if ((LA(1)==WORD)) {
					w2 = LT(1);
					match(WORD);
					
								if (buf.length() > 0) buf.append(' ');
								buf.append(w2.getText());
							
				}
				else {
					break _loop12;
				}
				
			} while (true);
			}
			
					buf.insert(0, ". &= \"");
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
	
	public final void not() throws RecognitionException, TokenStreamException {
		
		
		switch ( LA(1)) {
		case LITERAL_NOT:
		{
			match(LITERAL_NOT);
			break;
		}
		case LITERAL_NICHT:
		{
			match(LITERAL_NICHT);
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
	}
	
	
	public static final String[] _tokenNames = {
		"<0>",
		"EOF",
		"<2>",
		"NULL_TREE_LOOKAHEAD",
		"string literal",
		"regular expression",
		"WORD",
		"\"AND\"",
		"\"UND\"",
		"\"OR\"",
		"\"ODER\"",
		"\"NOT\"",
		"\"NICHT\"",
		"WS",
		"BASECHAR",
		"IDEOGRAPHIC",
		"COMBINING_CHAR",
		"DIGIT",
		"EXTENDER"
	};
	
	
	}
