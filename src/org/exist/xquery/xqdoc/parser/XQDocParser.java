// $ANTLR 2.7.7 (2006-11-01): "XQDocParser.g" -> "XQDocParser.java"$

	package org.exist.xquery.xqdoc.parser;

	import org.exist.xquery.xqdoc.XQDocHelper;

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

public class XQDocParser extends antlr.LLkParser       implements XQDocParserTokenTypes
 {

protected XQDocParser(TokenBuffer tokenBuf, int k) {
  super(tokenBuf,k);
  tokenNames = _tokenNames;
}

public XQDocParser(TokenBuffer tokenBuf) {
  this(tokenBuf,1);
}

protected XQDocParser(TokenStream lexer, int k) {
  super(lexer,k);
  tokenNames = _tokenNames;
}

public XQDocParser(TokenStream lexer) {
  this(lexer,1);
}

public XQDocParser(ParserSharedInputState state) {
  super(state,1);
  tokenNames = _tokenNames;
}

	public final void xqdocComment(
		XQDocHelper doc
	) throws RecognitionException, TokenStreamException {
		
		String c;
		
		try {      // for error handling
			match(XQDOC_START);
			{
			_loop5:
			do {
				switch ( LA(1)) {
				case TAG:
				{
					taggedContents(doc);
					break;
				}
				case TRIM:
				case SIMPLE_COLON:
				case CHARS:
				{
					c=contents();
					if ( inputState.guessing==0 ) {
						
									doc.addDescription(c);
								
					}
					break;
				}
				default:
				{
					break _loop5;
				}
				}
			} while (true);
			}
			match(XQDOC_END);
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_0);
			} else {
			  throw ex;
			}
		}
	}
	
	public final void taggedContents(
		XQDocHelper doc
	) throws RecognitionException, TokenStreamException {
		
		Token  t = null;
		
			String c;
		
		
		try {      // for error handling
			t = LT(1);
			match(TAG);
			c=contents();
			if ( inputState.guessing==0 ) {
				
						doc.setTag(t.getText(), c);
					
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_1);
			} else {
			  throw ex;
			}
		}
	}
	
	public final String  contents() throws RecognitionException, TokenStreamException {
		String content;
		
		Token  c = null;
		
			content = null;
			StringBuilder buf = new StringBuilder();
		
		
		try {      // for error handling
			{
			int _cnt9=0;
			_loop9:
			do {
				if ((LA(1)==TRIM)) {
					match(TRIM);
					if ( inputState.guessing==0 ) {
						buf.append('\n');
					}
				}
				else if ((LA(1)==SIMPLE_COLON)) {
					match(SIMPLE_COLON);
					if ( inputState.guessing==0 ) {
						
									if (buf.length()>0 && buf.charAt(buf.length() - 1) != '\n')
										buf.append(':');
								
					}
				}
				else if ((LA(1)==CHARS)) {
					c = LT(1);
					match(CHARS);
					if ( inputState.guessing==0 ) {
						buf.append(c.getText());
					}
				}
				else {
					if ( _cnt9>=1 ) { break _loop9; } else {throw new NoViableAltException(LT(1), getFilename());}
				}
				
				_cnt9++;
			} while (true);
			}
			if ( inputState.guessing==0 ) {
				content = buf.toString();
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				reportError(ex);
				recover(ex,_tokenSet_1);
			} else {
			  throw ex;
			}
		}
		return content;
	}
	
	
	public static final String[] _tokenNames = {
		"<0>",
		"EOF",
		"<2>",
		"NULL_TREE_LOOKAHEAD",
		"XQDOC_START",
		"TAG",
		"XQDOC_END",
		"TRIM",
		"SIMPLE_COLON",
		"CHARS",
		"AT"
	};
	
	private static final long[] mk_tokenSet_0() {
		long[] data = { 2L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_0 = new BitSet(mk_tokenSet_0());
	private static final long[] mk_tokenSet_1() {
		long[] data = { 992L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_1 = new BitSet(mk_tokenSet_1());
	
	}
