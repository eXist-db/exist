// $ANTLR 2.7.7 (2006-11-01): "XQuery.g" -> "XQueryLexer.java"$

	package org.exist.xquery.parser;

	import antlr.debug.misc.*;
	import java.io.StringReader;
	import java.io.BufferedReader;
	import java.io.InputStreamReader;
	import java.util.ArrayList;
	import java.util.List;
	import java.util.Iterator;
	import java.util.Stack;
	import org.exist.storage.BrokerPool;
	import org.exist.storage.DBBroker;
	import org.exist.storage.analysis.Tokenizer;
	import org.exist.EXistException;
	import org.exist.dom.DocumentSet;
	import org.exist.dom.DocumentImpl;
	import org.exist.dom.QName;
	import org.exist.security.PermissionDeniedException;
	import org.exist.security.User;
	import org.exist.xquery.*;
	import org.exist.xquery.value.*;
	import org.exist.xquery.functions.*;

import java.io.InputStream;
import antlr.TokenStreamException;
import antlr.TokenStreamIOException;
import antlr.TokenStreamRecognitionException;
import antlr.CharStreamException;
import antlr.CharStreamIOException;
import antlr.ANTLRException;
import java.io.Reader;
import java.util.Hashtable;
import antlr.CharScanner;
import antlr.InputBuffer;
import antlr.ByteBuffer;
import antlr.CharBuffer;
import antlr.Token;
import antlr.CommonToken;
import antlr.RecognitionException;
import antlr.NoViableAltForCharException;
import antlr.MismatchedCharException;
import antlr.TokenStream;
import antlr.ANTLRHashString;
import antlr.LexerSharedInputState;
import antlr.collections.impl.BitSet;
import antlr.SemanticException;

/**
 * The XQuery/XPath lexical analyzer.
 */
public class XQueryLexer extends antlr.CharScanner implements XQueryTokenTypes, TokenStream
 {

	protected boolean wsExplicit= false;
	protected boolean parseStringLiterals= true;
	protected boolean inElementContent= false;
	protected boolean inAttributeContent= false;
	protected char attrDelimChar = '"';
	protected boolean inComment= false;
	protected boolean inPragma = false;
	
	protected XQueryContext context = null;
	
	public XQueryLexer(XQueryContext context, Reader in) {
		this(in);
		this.context = context;
	}
	
	private void parseLinefeeds(String str) {
		char ch;
		for (int i = 0;  i < str.length(); i++) {
			ch = str.charAt(i);
			if (ch == '\n')
				newline();
		}
	}
public XQueryLexer(InputStream in) {
	this(new ByteBuffer(in));
}
public XQueryLexer(Reader in) {
	this(new CharBuffer(in));
}
public XQueryLexer(InputBuffer ib) {
	this(new LexerSharedInputState(ib));
}
public XQueryLexer(LexerSharedInputState state) {
	super(state);
	caseSensitiveLiterals = true;
	setCaseSensitive(true);
	literals = new Hashtable();
	literals.put(new ANTLRHashString("treat", this), new Integer(131));
	literals.put(new ANTLRHashString("typeswitch", this), new Integer(104));
	literals.put(new ANTLRHashString("copy-namespaces", this), new Integer(66));
	literals.put(new ANTLRHashString("xpointer", this), new Integer(49));
	literals.put(new ANTLRHashString("namespace", this), new Integer(57));
	literals.put(new ANTLRHashString("no-preserve", this), new Integer(83));
	literals.put(new ANTLRHashString("comment", this), new Integer(167));
	literals.put(new ANTLRHashString("case", this), new Integer(124));
	literals.put(new ANTLRHashString("option", this), new Integer(67));
	literals.put(new ANTLRHashString("delete", this), new Integer(109));
	literals.put(new ANTLRHashString("ne", this), new Integer(137));
	literals.put(new ANTLRHashString("validate", this), new Integer(203));
	literals.put(new ANTLRHashString("le", this), new Integer(139));
	literals.put(new ANTLRHashString("greatest", this), new Integer(76));
	literals.put(new ANTLRHashString("ancestor-or-self", this), new Integer(185));
	literals.put(new ANTLRHashString("descendant-or-self", this), new Integer(181));
	literals.put(new ANTLRHashString("insert", this), new Integer(108));
	literals.put(new ANTLRHashString("variable", this), new Integer(69));
	literals.put(new ANTLRHashString("where", this), new Integer(115));
	literals.put(new ANTLRHashString("stable", this), new Integer(119));
	literals.put(new ANTLRHashString("construction", this), new Integer(64));
	literals.put(new ANTLRHashString("then", this), new Integer(125));
	literals.put(new ANTLRHashString("preceding", this), new Integer(113));
	literals.put(new ANTLRHashString("document-node", this), new Integer(169));
	literals.put(new ANTLRHashString("collation", this), new Integer(72));
	literals.put(new ANTLRHashString("to", this), new Integer(151));
	literals.put(new ANTLRHashString("and", this), new Integer(128));
	literals.put(new ANTLRHashString("module", this), new Integer(56));
	literals.put(new ANTLRHashString("strip", this), new Integer(79));
	literals.put(new ANTLRHashString("return", this), new Integer(116));
	literals.put(new ANTLRHashString("inherit", this), new Integer(84));
	literals.put(new ANTLRHashString("schema", this), new Integer(91));
	literals.put(new ANTLRHashString("external", this), new Integer(90));
	literals.put(new ANTLRHashString("unordered", this), new Integer(81));
	literals.put(new ANTLRHashString("ordered", this), new Integer(80));
	literals.put(new ANTLRHashString("mod", this), new Integer(155));
	literals.put(new ANTLRHashString("following", this), new Integer(114));
	literals.put(new ANTLRHashString("except", this), new Integer(161));
	literals.put(new ANTLRHashString("text", this), new Integer(164));
	literals.put(new ANTLRHashString("preserve", this), new Integer(78));
	literals.put(new ANTLRHashString("function", this), new Integer(68));
	literals.put(new ANTLRHashString("with", this), new Integer(111));
	literals.put(new ANTLRHashString("gt", this), new Integer(140));
	literals.put(new ANTLRHashString("preceding-sibling", this), new Integer(186));
	literals.put(new ANTLRHashString("intersect", this), new Integer(160));
	literals.put(new ANTLRHashString("version", this), new Integer(54));
	literals.put(new ANTLRHashString("at", this), new Integer(93));
	literals.put(new ANTLRHashString("of", this), new Integer(130));
	literals.put(new ANTLRHashString("is", this), new Integer(147));
	literals.put(new ANTLRHashString("empty-sequence", this), new Integer(94));
	literals.put(new ANTLRHashString("or", this), new Integer(127));
	literals.put(new ANTLRHashString("if", this), new Integer(103));
	literals.put(new ANTLRHashString("least", this), new Integer(77));
	literals.put(new ANTLRHashString("as", this), new Integer(92));
	literals.put(new ANTLRHashString("by", this), new Integer(118));
	literals.put(new ANTLRHashString("union", this), new Integer(158));
	literals.put(new ANTLRHashString("order", this), new Integer(74));
	literals.put(new ANTLRHashString("xquery", this), new Integer(53));
	literals.put(new ANTLRHashString("following-sibling", this), new Integer(182));
	literals.put(new ANTLRHashString("node", this), new Integer(165));
	literals.put(new ANTLRHashString("collection", this), new Integer(202));
	literals.put(new ANTLRHashString("some", this), new Integer(101));
	literals.put(new ANTLRHashString("ge", this), new Integer(141));
	literals.put(new ANTLRHashString("declare", this), new Integer(60));
	literals.put(new ANTLRHashString("for", this), new Integer(99));
	literals.put(new ANTLRHashString("ascending", this), new Integer(120));
	literals.put(new ANTLRHashString("isnot", this), new Integer(148));
	literals.put(new ANTLRHashString("eq", this), new Integer(136));
	literals.put(new ANTLRHashString("element", this), new Integer(73));
	literals.put(new ANTLRHashString("default", this), new Integer(61));
	literals.put(new ANTLRHashString("descending", this), new Integer(121));
	literals.put(new ANTLRHashString("castable", this), new Integer(132));
	literals.put(new ANTLRHashString("idiv", this), new Integer(154));
	literals.put(new ANTLRHashString("boundary-space", this), new Integer(62));
	literals.put(new ANTLRHashString("ancestor", this), new Integer(184));
	literals.put(new ANTLRHashString("ordering", this), new Integer(63));
	literals.put(new ANTLRHashString("instance", this), new Integer(129));
	literals.put(new ANTLRHashString("replace", this), new Integer(106));
	literals.put(new ANTLRHashString("no-inherit", this), new Integer(85));
	literals.put(new ANTLRHashString("attribute", this), new Integer(166));
	literals.put(new ANTLRHashString("empty", this), new Integer(75));
	literals.put(new ANTLRHashString("cast", this), new Integer(133));
	literals.put(new ANTLRHashString("value", this), new Integer(107));
	literals.put(new ANTLRHashString("encoding", this), new Integer(71));
	literals.put(new ANTLRHashString("schema-element", this), new Integer(190));
	literals.put(new ANTLRHashString("into", this), new Integer(112));
	literals.put(new ANTLRHashString("rename", this), new Integer(110));
	literals.put(new ANTLRHashString("else", this), new Integer(126));
	literals.put(new ANTLRHashString("import", this), new Integer(70));
	literals.put(new ANTLRHashString("lt", this), new Integer(138));
	literals.put(new ANTLRHashString("document", this), new Integer(170));
	literals.put(new ANTLRHashString("self", this), new Integer(179));
	literals.put(new ANTLRHashString("in", this), new Integer(117));
	literals.put(new ANTLRHashString("let", this), new Integer(100));
	literals.put(new ANTLRHashString("base-uri", this), new Integer(65));
	literals.put(new ANTLRHashString("processing-instruction", this), new Integer(168));
	literals.put(new ANTLRHashString("parent", this), new Integer(183));
	literals.put(new ANTLRHashString("descendant", this), new Integer(180));
	literals.put(new ANTLRHashString("update", this), new Integer(105));
	literals.put(new ANTLRHashString("every", this), new Integer(102));
	literals.put(new ANTLRHashString("group", this), new Integer(122));
	literals.put(new ANTLRHashString("satisfies", this), new Integer(123));
	literals.put(new ANTLRHashString("div", this), new Integer(153));
	literals.put(new ANTLRHashString("item", this), new Integer(98));
	literals.put(new ANTLRHashString("child", this), new Integer(178));
}

public Token nextToken() throws TokenStreamException {
	Token theRetToken=null;
tryAgain:
	for (;;) {
		Token _token = null;
		int _ttype = Token.INVALID_TYPE;
		resetText();
		try {   // for char stream error handling
			try {   // for lexical error handling
				if (((LA(1) >= '\u0003' && LA(1) <= '\ufffe'))) {
					mNEXT_TOKEN(true);
					theRetToken=_returnToken;
				}
				else {
					if (LA(1)==EOF_CHAR) {uponEOF(); _returnToken = makeToken(Token.EOF_TYPE);}
				else {throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());}
				}
				
				if ( _returnToken==null ) continue tryAgain; // found SKIP token
				_ttype = _returnToken.getType();
				_returnToken.setType(_ttype);
				return _returnToken;
			}
			catch (RecognitionException e) {
				throw new TokenStreamRecognitionException(e);
			}
		}
		catch (CharStreamException cse) {
			if ( cse instanceof CharStreamIOException ) {
				throw new TokenStreamIOException(((CharStreamIOException)cse).io);
			}
			else {
				throw new TokenStreamException(cse.getMessage());
			}
		}
	}
}

	protected final void mSLASH(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = SLASH;
		int _saveIndex;
		
		match('/');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mDSLASH(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = DSLASH;
		int _saveIndex;
		
		match('/');
		match('/');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mCOLON(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = COLON;
		int _saveIndex;
		
		match(':');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mCOMMA(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = COMMA;
		int _saveIndex;
		
		match(',');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mSEMICOLON(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = SEMICOLON;
		int _saveIndex;
		
		match(';');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mSTAR(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = STAR;
		int _saveIndex;
		
		match('*');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mQUESTION(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = QUESTION;
		int _saveIndex;
		
		match('?');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mPLUS(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = PLUS;
		int _saveIndex;
		
		match('+');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mMINUS(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = MINUS;
		int _saveIndex;
		
		match('-');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mLPPAREN(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = LPPAREN;
		int _saveIndex;
		
		match('[');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mRPPAREN(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = RPPAREN;
		int _saveIndex;
		
		match(']');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mLPAREN(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = LPAREN;
		int _saveIndex;
		
		match('(');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mRPAREN(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = RPAREN;
		int _saveIndex;
		
		match(')');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mSELF(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = SELF;
		int _saveIndex;
		
		match('.');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mPARENT(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = PARENT;
		int _saveIndex;
		
		match("..");
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mUNION(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = UNION;
		int _saveIndex;
		
		match('|');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mAT(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = AT;
		int _saveIndex;
		
		match('@');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mDOLLAR(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = DOLLAR;
		int _saveIndex;
		
		match('$');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mANDEQ(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = ANDEQ;
		int _saveIndex;
		
		match("&=");
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mOREQ(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = OREQ;
		int _saveIndex;
		
		match("|=");
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mEQ(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = EQ;
		int _saveIndex;
		
		match('=');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mNEQ(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = NEQ;
		int _saveIndex;
		
		match("!=");
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mGT(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = GT;
		int _saveIndex;
		
		match('>');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mGTEQ(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = GTEQ;
		int _saveIndex;
		
		match(">=");
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mQUOT(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = QUOT;
		int _saveIndex;
		
		match('"');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mAPOS(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = APOS;
		int _saveIndex;
		
		match("'");
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mLTEQ(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = LTEQ;
		int _saveIndex;
		
		match("<=");
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mBEFORE(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = BEFORE;
		int _saveIndex;
		
		match("<<");
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mAFTER(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = AFTER;
		int _saveIndex;
		
		match(">>");
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mLT(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = LT;
		int _saveIndex;
		
		match('<');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mEND_TAG_START(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = END_TAG_START;
		int _saveIndex;
		
		match("</");
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mLCURLY(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = LCURLY;
		int _saveIndex;
		
		match('{');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mRCURLY(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = RCURLY;
		int _saveIndex;
		
		match('}');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mXML_COMMENT_END(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = XML_COMMENT_END;
		int _saveIndex;
		
		match("-->");
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mXML_PI_START(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = XML_PI_START;
		int _saveIndex;
		
		match("<?");
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mXML_PI_END(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = XML_PI_END;
		int _saveIndex;
		
		match("?>");
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mXML_CDATA_START(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = XML_CDATA_START;
		int _saveIndex;
		
		match("<![CDATA[");
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mXML_CDATA_END(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = XML_CDATA_END;
		int _saveIndex;
		
		match("]]>");
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mLETTER(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = LETTER;
		int _saveIndex;
		
		{
		if ((_tokenSet_0.member(LA(1)))) {
			mBASECHAR(false);
		}
		else if ((_tokenSet_1.member(LA(1)))) {
			mIDEOGRAPHIC(false);
		}
		else {
			throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());
		}
		
		}
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mBASECHAR(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = BASECHAR;
		int _saveIndex;
		
		{
		switch ( LA(1)) {
		case 'A':  case 'B':  case 'C':  case 'D':
		case 'E':  case 'F':  case 'G':  case 'H':
		case 'I':  case 'J':  case 'K':  case 'L':
		case 'M':  case 'N':  case 'O':  case 'P':
		case 'Q':  case 'R':  case 'S':  case 'T':
		case 'U':  case 'V':  case 'W':  case 'X':
		case 'Y':  case 'Z':
		{
			matchRange('\u0041','\u005a');
			break;
		}
		case 'a':  case 'b':  case 'c':  case 'd':
		case 'e':  case 'f':  case 'g':  case 'h':
		case 'i':  case 'j':  case 'k':  case 'l':
		case 'm':  case 'n':  case 'o':  case 'p':
		case 'q':  case 'r':  case 's':  case 't':
		case 'u':  case 'v':  case 'w':  case 'x':
		case 'y':  case 'z':
		{
			matchRange('\u0061','\u007a');
			break;
		}
		case '\u00c0':  case '\u00c1':  case '\u00c2':  case '\u00c3':
		case '\u00c4':  case '\u00c5':  case '\u00c6':  case '\u00c7':
		case '\u00c8':  case '\u00c9':  case '\u00ca':  case '\u00cb':
		case '\u00cc':  case '\u00cd':  case '\u00ce':  case '\u00cf':
		case '\u00d0':  case '\u00d1':  case '\u00d2':  case '\u00d3':
		case '\u00d4':  case '\u00d5':  case '\u00d6':
		{
			matchRange('\u00c0','\u00d6');
			break;
		}
		case '\u00d8':  case '\u00d9':  case '\u00da':  case '\u00db':
		case '\u00dc':  case '\u00dd':  case '\u00de':  case '\u00df':
		case '\u00e0':  case '\u00e1':  case '\u00e2':  case '\u00e3':
		case '\u00e4':  case '\u00e5':  case '\u00e6':  case '\u00e7':
		case '\u00e8':  case '\u00e9':  case '\u00ea':  case '\u00eb':
		case '\u00ec':  case '\u00ed':  case '\u00ee':  case '\u00ef':
		case '\u00f0':  case '\u00f1':  case '\u00f2':  case '\u00f3':
		case '\u00f4':  case '\u00f5':  case '\u00f6':
		{
			matchRange('\u00d8','\u00f6');
			break;
		}
		case '\u00f8':  case '\u00f9':  case '\u00fa':  case '\u00fb':
		case '\u00fc':  case '\u00fd':  case '\u00fe':  case '\u00ff':
		{
			matchRange('\u00f8','\u00ff');
			break;
		}
		case '\u0100':  case '\u0101':  case '\u0102':  case '\u0103':
		case '\u0104':  case '\u0105':  case '\u0106':  case '\u0107':
		case '\u0108':  case '\u0109':  case '\u010a':  case '\u010b':
		case '\u010c':  case '\u010d':  case '\u010e':  case '\u010f':
		case '\u0110':  case '\u0111':  case '\u0112':  case '\u0113':
		case '\u0114':  case '\u0115':  case '\u0116':  case '\u0117':
		case '\u0118':  case '\u0119':  case '\u011a':  case '\u011b':
		case '\u011c':  case '\u011d':  case '\u011e':  case '\u011f':
		case '\u0120':  case '\u0121':  case '\u0122':  case '\u0123':
		case '\u0124':  case '\u0125':  case '\u0126':  case '\u0127':
		case '\u0128':  case '\u0129':  case '\u012a':  case '\u012b':
		case '\u012c':  case '\u012d':  case '\u012e':  case '\u012f':
		case '\u0130':  case '\u0131':
		{
			matchRange('\u0100','\u0131');
			break;
		}
		case '\u0134':  case '\u0135':  case '\u0136':  case '\u0137':
		case '\u0138':  case '\u0139':  case '\u013a':  case '\u013b':
		case '\u013c':  case '\u013d':  case '\u013e':
		{
			matchRange('\u0134','\u013e');
			break;
		}
		case '\u0141':  case '\u0142':  case '\u0143':  case '\u0144':
		case '\u0145':  case '\u0146':  case '\u0147':  case '\u0148':
		{
			matchRange('\u0141','\u0148');
			break;
		}
		case '\u014a':  case '\u014b':  case '\u014c':  case '\u014d':
		case '\u014e':  case '\u014f':  case '\u0150':  case '\u0151':
		case '\u0152':  case '\u0153':  case '\u0154':  case '\u0155':
		case '\u0156':  case '\u0157':  case '\u0158':  case '\u0159':
		case '\u015a':  case '\u015b':  case '\u015c':  case '\u015d':
		case '\u015e':  case '\u015f':  case '\u0160':  case '\u0161':
		case '\u0162':  case '\u0163':  case '\u0164':  case '\u0165':
		case '\u0166':  case '\u0167':  case '\u0168':  case '\u0169':
		case '\u016a':  case '\u016b':  case '\u016c':  case '\u016d':
		case '\u016e':  case '\u016f':  case '\u0170':  case '\u0171':
		case '\u0172':  case '\u0173':  case '\u0174':  case '\u0175':
		case '\u0176':  case '\u0177':  case '\u0178':  case '\u0179':
		case '\u017a':  case '\u017b':  case '\u017c':  case '\u017d':
		case '\u017e':
		{
			matchRange('\u014a','\u017e');
			break;
		}
		case '\u0180':  case '\u0181':  case '\u0182':  case '\u0183':
		case '\u0184':  case '\u0185':  case '\u0186':  case '\u0187':
		case '\u0188':  case '\u0189':  case '\u018a':  case '\u018b':
		case '\u018c':  case '\u018d':  case '\u018e':  case '\u018f':
		case '\u0190':  case '\u0191':  case '\u0192':  case '\u0193':
		case '\u0194':  case '\u0195':  case '\u0196':  case '\u0197':
		case '\u0198':  case '\u0199':  case '\u019a':  case '\u019b':
		case '\u019c':  case '\u019d':  case '\u019e':  case '\u019f':
		case '\u01a0':  case '\u01a1':  case '\u01a2':  case '\u01a3':
		case '\u01a4':  case '\u01a5':  case '\u01a6':  case '\u01a7':
		case '\u01a8':  case '\u01a9':  case '\u01aa':  case '\u01ab':
		case '\u01ac':  case '\u01ad':  case '\u01ae':  case '\u01af':
		case '\u01b0':  case '\u01b1':  case '\u01b2':  case '\u01b3':
		case '\u01b4':  case '\u01b5':  case '\u01b6':  case '\u01b7':
		case '\u01b8':  case '\u01b9':  case '\u01ba':  case '\u01bb':
		case '\u01bc':  case '\u01bd':  case '\u01be':  case '\u01bf':
		case '\u01c0':  case '\u01c1':  case '\u01c2':  case '\u01c3':
		{
			matchRange('\u0180','\u01c3');
			break;
		}
		case '\u01cd':  case '\u01ce':  case '\u01cf':  case '\u01d0':
		case '\u01d1':  case '\u01d2':  case '\u01d3':  case '\u01d4':
		case '\u01d5':  case '\u01d6':  case '\u01d7':  case '\u01d8':
		case '\u01d9':  case '\u01da':  case '\u01db':  case '\u01dc':
		case '\u01dd':  case '\u01de':  case '\u01df':  case '\u01e0':
		case '\u01e1':  case '\u01e2':  case '\u01e3':  case '\u01e4':
		case '\u01e5':  case '\u01e6':  case '\u01e7':  case '\u01e8':
		case '\u01e9':  case '\u01ea':  case '\u01eb':  case '\u01ec':
		case '\u01ed':  case '\u01ee':  case '\u01ef':  case '\u01f0':
		{
			matchRange('\u01cd','\u01f0');
			break;
		}
		case '\u01f4':  case '\u01f5':
		{
			matchRange('\u01f4','\u01f5');
			break;
		}
		case '\u01fa':  case '\u01fb':  case '\u01fc':  case '\u01fd':
		case '\u01fe':  case '\u01ff':  case '\u0200':  case '\u0201':
		case '\u0202':  case '\u0203':  case '\u0204':  case '\u0205':
		case '\u0206':  case '\u0207':  case '\u0208':  case '\u0209':
		case '\u020a':  case '\u020b':  case '\u020c':  case '\u020d':
		case '\u020e':  case '\u020f':  case '\u0210':  case '\u0211':
		case '\u0212':  case '\u0213':  case '\u0214':  case '\u0215':
		case '\u0216':  case '\u0217':
		{
			matchRange('\u01fa','\u0217');
			break;
		}
		case '\u0250':  case '\u0251':  case '\u0252':  case '\u0253':
		case '\u0254':  case '\u0255':  case '\u0256':  case '\u0257':
		case '\u0258':  case '\u0259':  case '\u025a':  case '\u025b':
		case '\u025c':  case '\u025d':  case '\u025e':  case '\u025f':
		case '\u0260':  case '\u0261':  case '\u0262':  case '\u0263':
		case '\u0264':  case '\u0265':  case '\u0266':  case '\u0267':
		case '\u0268':  case '\u0269':  case '\u026a':  case '\u026b':
		case '\u026c':  case '\u026d':  case '\u026e':  case '\u026f':
		case '\u0270':  case '\u0271':  case '\u0272':  case '\u0273':
		case '\u0274':  case '\u0275':  case '\u0276':  case '\u0277':
		case '\u0278':  case '\u0279':  case '\u027a':  case '\u027b':
		case '\u027c':  case '\u027d':  case '\u027e':  case '\u027f':
		case '\u0280':  case '\u0281':  case '\u0282':  case '\u0283':
		case '\u0284':  case '\u0285':  case '\u0286':  case '\u0287':
		case '\u0288':  case '\u0289':  case '\u028a':  case '\u028b':
		case '\u028c':  case '\u028d':  case '\u028e':  case '\u028f':
		case '\u0290':  case '\u0291':  case '\u0292':  case '\u0293':
		case '\u0294':  case '\u0295':  case '\u0296':  case '\u0297':
		case '\u0298':  case '\u0299':  case '\u029a':  case '\u029b':
		case '\u029c':  case '\u029d':  case '\u029e':  case '\u029f':
		case '\u02a0':  case '\u02a1':  case '\u02a2':  case '\u02a3':
		case '\u02a4':  case '\u02a5':  case '\u02a6':  case '\u02a7':
		case '\u02a8':
		{
			matchRange('\u0250','\u02a8');
			break;
		}
		case '\u02bb':  case '\u02bc':  case '\u02bd':  case '\u02be':
		case '\u02bf':  case '\u02c0':  case '\u02c1':
		{
			matchRange('\u02bb','\u02c1');
			break;
		}
		case '\u0386':
		{
			match('\u0386');
			break;
		}
		case '\u0388':  case '\u0389':  case '\u038a':
		{
			matchRange('\u0388','\u038a');
			break;
		}
		case '\u038c':
		{
			match('\u038c');
			break;
		}
		case '\u038e':  case '\u038f':  case '\u0390':  case '\u0391':
		case '\u0392':  case '\u0393':  case '\u0394':  case '\u0395':
		case '\u0396':  case '\u0397':  case '\u0398':  case '\u0399':
		case '\u039a':  case '\u039b':  case '\u039c':  case '\u039d':
		case '\u039e':  case '\u039f':  case '\u03a0':  case '\u03a1':
		{
			matchRange('\u038e','\u03a1');
			break;
		}
		case '\u03a3':  case '\u03a4':  case '\u03a5':  case '\u03a6':
		case '\u03a7':  case '\u03a8':  case '\u03a9':  case '\u03aa':
		case '\u03ab':  case '\u03ac':  case '\u03ad':  case '\u03ae':
		case '\u03af':  case '\u03b0':  case '\u03b1':  case '\u03b2':
		case '\u03b3':  case '\u03b4':  case '\u03b5':  case '\u03b6':
		case '\u03b7':  case '\u03b8':  case '\u03b9':  case '\u03ba':
		case '\u03bb':  case '\u03bc':  case '\u03bd':  case '\u03be':
		case '\u03bf':  case '\u03c0':  case '\u03c1':  case '\u03c2':
		case '\u03c3':  case '\u03c4':  case '\u03c5':  case '\u03c6':
		case '\u03c7':  case '\u03c8':  case '\u03c9':  case '\u03ca':
		case '\u03cb':  case '\u03cc':  case '\u03cd':  case '\u03ce':
		{
			matchRange('\u03a3','\u03ce');
			break;
		}
		case '\u03d0':  case '\u03d1':  case '\u03d2':  case '\u03d3':
		case '\u03d4':  case '\u03d5':  case '\u03d6':
		{
			matchRange('\u03d0','\u03d6');
			break;
		}
		case '\u03da':
		{
			match('\u03da');
			break;
		}
		case '\u03dc':
		{
			match('\u03dc');
			break;
		}
		case '\u03de':
		{
			match('\u03de');
			break;
		}
		case '\u03e0':
		{
			match('\u03e0');
			break;
		}
		case '\u03e2':  case '\u03e3':  case '\u03e4':  case '\u03e5':
		case '\u03e6':  case '\u03e7':  case '\u03e8':  case '\u03e9':
		case '\u03ea':  case '\u03eb':  case '\u03ec':  case '\u03ed':
		case '\u03ee':  case '\u03ef':  case '\u03f0':  case '\u03f1':
		case '\u03f2':  case '\u03f3':
		{
			matchRange('\u03e2','\u03f3');
			break;
		}
		case '\u0401':  case '\u0402':  case '\u0403':  case '\u0404':
		case '\u0405':  case '\u0406':  case '\u0407':  case '\u0408':
		case '\u0409':  case '\u040a':  case '\u040b':  case '\u040c':
		{
			matchRange('\u0401','\u040c');
			break;
		}
		case '\u040e':  case '\u040f':  case '\u0410':  case '\u0411':
		case '\u0412':  case '\u0413':  case '\u0414':  case '\u0415':
		case '\u0416':  case '\u0417':  case '\u0418':  case '\u0419':
		case '\u041a':  case '\u041b':  case '\u041c':  case '\u041d':
		case '\u041e':  case '\u041f':  case '\u0420':  case '\u0421':
		case '\u0422':  case '\u0423':  case '\u0424':  case '\u0425':
		case '\u0426':  case '\u0427':  case '\u0428':  case '\u0429':
		case '\u042a':  case '\u042b':  case '\u042c':  case '\u042d':
		case '\u042e':  case '\u042f':  case '\u0430':  case '\u0431':
		case '\u0432':  case '\u0433':  case '\u0434':  case '\u0435':
		case '\u0436':  case '\u0437':  case '\u0438':  case '\u0439':
		case '\u043a':  case '\u043b':  case '\u043c':  case '\u043d':
		case '\u043e':  case '\u043f':  case '\u0440':  case '\u0441':
		case '\u0442':  case '\u0443':  case '\u0444':  case '\u0445':
		case '\u0446':  case '\u0447':  case '\u0448':  case '\u0449':
		case '\u044a':  case '\u044b':  case '\u044c':  case '\u044d':
		case '\u044e':  case '\u044f':
		{
			matchRange('\u040e','\u044f');
			break;
		}
		case '\u0451':  case '\u0452':  case '\u0453':  case '\u0454':
		case '\u0455':  case '\u0456':  case '\u0457':  case '\u0458':
		case '\u0459':  case '\u045a':  case '\u045b':  case '\u045c':
		{
			matchRange('\u0451','\u045c');
			break;
		}
		case '\u045e':  case '\u045f':  case '\u0460':  case '\u0461':
		case '\u0462':  case '\u0463':  case '\u0464':  case '\u0465':
		case '\u0466':  case '\u0467':  case '\u0468':  case '\u0469':
		case '\u046a':  case '\u046b':  case '\u046c':  case '\u046d':
		case '\u046e':  case '\u046f':  case '\u0470':  case '\u0471':
		case '\u0472':  case '\u0473':  case '\u0474':  case '\u0475':
		case '\u0476':  case '\u0477':  case '\u0478':  case '\u0479':
		case '\u047a':  case '\u047b':  case '\u047c':  case '\u047d':
		case '\u047e':  case '\u047f':  case '\u0480':  case '\u0481':
		{
			matchRange('\u045e','\u0481');
			break;
		}
		case '\u0490':  case '\u0491':  case '\u0492':  case '\u0493':
		case '\u0494':  case '\u0495':  case '\u0496':  case '\u0497':
		case '\u0498':  case '\u0499':  case '\u049a':  case '\u049b':
		case '\u049c':  case '\u049d':  case '\u049e':  case '\u049f':
		case '\u04a0':  case '\u04a1':  case '\u04a2':  case '\u04a3':
		case '\u04a4':  case '\u04a5':  case '\u04a6':  case '\u04a7':
		case '\u04a8':  case '\u04a9':  case '\u04aa':  case '\u04ab':
		case '\u04ac':  case '\u04ad':  case '\u04ae':  case '\u04af':
		case '\u04b0':  case '\u04b1':  case '\u04b2':  case '\u04b3':
		case '\u04b4':  case '\u04b5':  case '\u04b6':  case '\u04b7':
		case '\u04b8':  case '\u04b9':  case '\u04ba':  case '\u04bb':
		case '\u04bc':  case '\u04bd':  case '\u04be':  case '\u04bf':
		case '\u04c0':  case '\u04c1':  case '\u04c2':  case '\u04c3':
		case '\u04c4':
		{
			matchRange('\u0490','\u04c4');
			break;
		}
		case '\u04c7':  case '\u04c8':
		{
			matchRange('\u04c7','\u04c8');
			break;
		}
		case '\u04cb':  case '\u04cc':
		{
			matchRange('\u04cb','\u04cc');
			break;
		}
		case '\u04d0':  case '\u04d1':  case '\u04d2':  case '\u04d3':
		case '\u04d4':  case '\u04d5':  case '\u04d6':  case '\u04d7':
		case '\u04d8':  case '\u04d9':  case '\u04da':  case '\u04db':
		case '\u04dc':  case '\u04dd':  case '\u04de':  case '\u04df':
		case '\u04e0':  case '\u04e1':  case '\u04e2':  case '\u04e3':
		case '\u04e4':  case '\u04e5':  case '\u04e6':  case '\u04e7':
		case '\u04e8':  case '\u04e9':  case '\u04ea':  case '\u04eb':
		{
			matchRange('\u04d0','\u04eb');
			break;
		}
		case '\u04ee':  case '\u04ef':  case '\u04f0':  case '\u04f1':
		case '\u04f2':  case '\u04f3':  case '\u04f4':  case '\u04f5':
		{
			matchRange('\u04ee','\u04f5');
			break;
		}
		case '\u04f8':  case '\u04f9':
		{
			matchRange('\u04f8','\u04f9');
			break;
		}
		case '\u0531':  case '\u0532':  case '\u0533':  case '\u0534':
		case '\u0535':  case '\u0536':  case '\u0537':  case '\u0538':
		case '\u0539':  case '\u053a':  case '\u053b':  case '\u053c':
		case '\u053d':  case '\u053e':  case '\u053f':  case '\u0540':
		case '\u0541':  case '\u0542':  case '\u0543':  case '\u0544':
		case '\u0545':  case '\u0546':  case '\u0547':  case '\u0548':
		case '\u0549':  case '\u054a':  case '\u054b':  case '\u054c':
		case '\u054d':  case '\u054e':  case '\u054f':  case '\u0550':
		case '\u0551':  case '\u0552':  case '\u0553':  case '\u0554':
		case '\u0555':  case '\u0556':
		{
			matchRange('\u0531','\u0556');
			break;
		}
		case '\u0559':
		{
			match('\u0559');
			break;
		}
		case '\u0561':  case '\u0562':  case '\u0563':  case '\u0564':
		case '\u0565':  case '\u0566':  case '\u0567':  case '\u0568':
		case '\u0569':  case '\u056a':  case '\u056b':  case '\u056c':
		case '\u056d':  case '\u056e':  case '\u056f':  case '\u0570':
		case '\u0571':  case '\u0572':  case '\u0573':  case '\u0574':
		case '\u0575':  case '\u0576':  case '\u0577':  case '\u0578':
		case '\u0579':  case '\u057a':  case '\u057b':  case '\u057c':
		case '\u057d':  case '\u057e':  case '\u057f':  case '\u0580':
		case '\u0581':  case '\u0582':  case '\u0583':  case '\u0584':
		case '\u0585':  case '\u0586':
		{
			matchRange('\u0561','\u0586');
			break;
		}
		case '\u05d0':  case '\u05d1':  case '\u05d2':  case '\u05d3':
		case '\u05d4':  case '\u05d5':  case '\u05d6':  case '\u05d7':
		case '\u05d8':  case '\u05d9':  case '\u05da':  case '\u05db':
		case '\u05dc':  case '\u05dd':  case '\u05de':  case '\u05df':
		case '\u05e0':  case '\u05e1':  case '\u05e2':  case '\u05e3':
		case '\u05e4':  case '\u05e5':  case '\u05e6':  case '\u05e7':
		case '\u05e8':  case '\u05e9':  case '\u05ea':
		{
			matchRange('\u05d0','\u05ea');
			break;
		}
		case '\u05f0':  case '\u05f1':  case '\u05f2':
		{
			matchRange('\u05f0','\u05f2');
			break;
		}
		case '\u0621':  case '\u0622':  case '\u0623':  case '\u0624':
		case '\u0625':  case '\u0626':  case '\u0627':  case '\u0628':
		case '\u0629':  case '\u062a':  case '\u062b':  case '\u062c':
		case '\u062d':  case '\u062e':  case '\u062f':  case '\u0630':
		case '\u0631':  case '\u0632':  case '\u0633':  case '\u0634':
		case '\u0635':  case '\u0636':  case '\u0637':  case '\u0638':
		case '\u0639':  case '\u063a':
		{
			matchRange('\u0621','\u063a');
			break;
		}
		case '\u0641':  case '\u0642':  case '\u0643':  case '\u0644':
		case '\u0645':  case '\u0646':  case '\u0647':  case '\u0648':
		case '\u0649':  case '\u064a':
		{
			matchRange('\u0641','\u064a');
			break;
		}
		case '\u0671':  case '\u0672':  case '\u0673':  case '\u0674':
		case '\u0675':  case '\u0676':  case '\u0677':  case '\u0678':
		case '\u0679':  case '\u067a':  case '\u067b':  case '\u067c':
		case '\u067d':  case '\u067e':  case '\u067f':  case '\u0680':
		case '\u0681':  case '\u0682':  case '\u0683':  case '\u0684':
		case '\u0685':  case '\u0686':  case '\u0687':  case '\u0688':
		case '\u0689':  case '\u068a':  case '\u068b':  case '\u068c':
		case '\u068d':  case '\u068e':  case '\u068f':  case '\u0690':
		case '\u0691':  case '\u0692':  case '\u0693':  case '\u0694':
		case '\u0695':  case '\u0696':  case '\u0697':  case '\u0698':
		case '\u0699':  case '\u069a':  case '\u069b':  case '\u069c':
		case '\u069d':  case '\u069e':  case '\u069f':  case '\u06a0':
		case '\u06a1':  case '\u06a2':  case '\u06a3':  case '\u06a4':
		case '\u06a5':  case '\u06a6':  case '\u06a7':  case '\u06a8':
		case '\u06a9':  case '\u06aa':  case '\u06ab':  case '\u06ac':
		case '\u06ad':  case '\u06ae':  case '\u06af':  case '\u06b0':
		case '\u06b1':  case '\u06b2':  case '\u06b3':  case '\u06b4':
		case '\u06b5':  case '\u06b6':  case '\u06b7':
		{
			matchRange('\u0671','\u06b7');
			break;
		}
		case '\u06ba':  case '\u06bb':  case '\u06bc':  case '\u06bd':
		case '\u06be':
		{
			matchRange('\u06ba','\u06be');
			break;
		}
		case '\u06c0':  case '\u06c1':  case '\u06c2':  case '\u06c3':
		case '\u06c4':  case '\u06c5':  case '\u06c6':  case '\u06c7':
		case '\u06c8':  case '\u06c9':  case '\u06ca':  case '\u06cb':
		case '\u06cc':  case '\u06cd':  case '\u06ce':
		{
			matchRange('\u06c0','\u06ce');
			break;
		}
		case '\u06d0':  case '\u06d1':  case '\u06d2':  case '\u06d3':
		{
			matchRange('\u06d0','\u06d3');
			break;
		}
		case '\u06d5':
		{
			match('\u06d5');
			break;
		}
		case '\u06e5':  case '\u06e6':
		{
			matchRange('\u06e5','\u06e6');
			break;
		}
		case '\u0905':  case '\u0906':  case '\u0907':  case '\u0908':
		case '\u0909':  case '\u090a':  case '\u090b':  case '\u090c':
		case '\u090d':  case '\u090e':  case '\u090f':  case '\u0910':
		case '\u0911':  case '\u0912':  case '\u0913':  case '\u0914':
		case '\u0915':  case '\u0916':  case '\u0917':  case '\u0918':
		case '\u0919':  case '\u091a':  case '\u091b':  case '\u091c':
		case '\u091d':  case '\u091e':  case '\u091f':  case '\u0920':
		case '\u0921':  case '\u0922':  case '\u0923':  case '\u0924':
		case '\u0925':  case '\u0926':  case '\u0927':  case '\u0928':
		case '\u0929':  case '\u092a':  case '\u092b':  case '\u092c':
		case '\u092d':  case '\u092e':  case '\u092f':  case '\u0930':
		case '\u0931':  case '\u0932':  case '\u0933':  case '\u0934':
		case '\u0935':  case '\u0936':  case '\u0937':  case '\u0938':
		case '\u0939':
		{
			matchRange('\u0905','\u0939');
			break;
		}
		case '\u093d':
		{
			match('\u093d');
			break;
		}
		case '\u0958':  case '\u0959':  case '\u095a':  case '\u095b':
		case '\u095c':  case '\u095d':  case '\u095e':  case '\u095f':
		case '\u0960':  case '\u0961':
		{
			matchRange('\u0958','\u0961');
			break;
		}
		case '\u0985':  case '\u0986':  case '\u0987':  case '\u0988':
		case '\u0989':  case '\u098a':  case '\u098b':  case '\u098c':
		{
			matchRange('\u0985','\u098c');
			break;
		}
		case '\u098f':  case '\u0990':
		{
			matchRange('\u098f','\u0990');
			break;
		}
		case '\u0993':  case '\u0994':  case '\u0995':  case '\u0996':
		case '\u0997':  case '\u0998':  case '\u0999':  case '\u099a':
		case '\u099b':  case '\u099c':  case '\u099d':  case '\u099e':
		case '\u099f':  case '\u09a0':  case '\u09a1':  case '\u09a2':
		case '\u09a3':  case '\u09a4':  case '\u09a5':  case '\u09a6':
		case '\u09a7':  case '\u09a8':
		{
			matchRange('\u0993','\u09a8');
			break;
		}
		case '\u09aa':  case '\u09ab':  case '\u09ac':  case '\u09ad':
		case '\u09ae':  case '\u09af':  case '\u09b0':
		{
			matchRange('\u09aa','\u09b0');
			break;
		}
		case '\u09b2':
		{
			match('\u09b2');
			break;
		}
		case '\u09b6':  case '\u09b7':  case '\u09b8':  case '\u09b9':
		{
			matchRange('\u09b6','\u09b9');
			break;
		}
		case '\u09dc':  case '\u09dd':
		{
			matchRange('\u09dc','\u09dd');
			break;
		}
		case '\u09df':  case '\u09e0':  case '\u09e1':
		{
			matchRange('\u09df','\u09e1');
			break;
		}
		case '\u09f0':  case '\u09f1':
		{
			matchRange('\u09f0','\u09f1');
			break;
		}
		case '\u0a05':  case '\u0a06':  case '\u0a07':  case '\u0a08':
		case '\u0a09':  case '\u0a0a':
		{
			matchRange('\u0a05','\u0a0a');
			break;
		}
		case '\u0a0f':  case '\u0a10':
		{
			matchRange('\u0a0f','\u0a10');
			break;
		}
		case '\u0a13':  case '\u0a14':  case '\u0a15':  case '\u0a16':
		case '\u0a17':  case '\u0a18':  case '\u0a19':  case '\u0a1a':
		case '\u0a1b':  case '\u0a1c':  case '\u0a1d':  case '\u0a1e':
		case '\u0a1f':  case '\u0a20':  case '\u0a21':  case '\u0a22':
		case '\u0a23':  case '\u0a24':  case '\u0a25':  case '\u0a26':
		case '\u0a27':  case '\u0a28':
		{
			matchRange('\u0a13','\u0a28');
			break;
		}
		case '\u0a2a':  case '\u0a2b':  case '\u0a2c':  case '\u0a2d':
		case '\u0a2e':  case '\u0a2f':  case '\u0a30':
		{
			matchRange('\u0a2a','\u0a30');
			break;
		}
		case '\u0a32':  case '\u0a33':
		{
			matchRange('\u0a32','\u0a33');
			break;
		}
		case '\u0a35':  case '\u0a36':
		{
			matchRange('\u0a35','\u0a36');
			break;
		}
		case '\u0a38':  case '\u0a39':
		{
			matchRange('\u0a38','\u0a39');
			break;
		}
		case '\u0a59':  case '\u0a5a':  case '\u0a5b':  case '\u0a5c':
		{
			matchRange('\u0a59','\u0a5c');
			break;
		}
		case '\u0a5e':
		{
			match('\u0a5e');
			break;
		}
		case '\u0a72':  case '\u0a73':  case '\u0a74':
		{
			matchRange('\u0a72','\u0a74');
			break;
		}
		case '\u0a85':  case '\u0a86':  case '\u0a87':  case '\u0a88':
		case '\u0a89':  case '\u0a8a':  case '\u0a8b':
		{
			matchRange('\u0a85','\u0a8b');
			break;
		}
		case '\u0a8d':
		{
			match('\u0a8d');
			break;
		}
		case '\u0a8f':  case '\u0a90':  case '\u0a91':
		{
			matchRange('\u0a8f','\u0a91');
			break;
		}
		case '\u0a93':  case '\u0a94':  case '\u0a95':  case '\u0a96':
		case '\u0a97':  case '\u0a98':  case '\u0a99':  case '\u0a9a':
		case '\u0a9b':  case '\u0a9c':  case '\u0a9d':  case '\u0a9e':
		case '\u0a9f':  case '\u0aa0':  case '\u0aa1':  case '\u0aa2':
		case '\u0aa3':  case '\u0aa4':  case '\u0aa5':  case '\u0aa6':
		case '\u0aa7':  case '\u0aa8':
		{
			matchRange('\u0a93','\u0aa8');
			break;
		}
		case '\u0aaa':  case '\u0aab':  case '\u0aac':  case '\u0aad':
		case '\u0aae':  case '\u0aaf':  case '\u0ab0':
		{
			matchRange('\u0aaa','\u0ab0');
			break;
		}
		case '\u0ab2':  case '\u0ab3':
		{
			matchRange('\u0ab2','\u0ab3');
			break;
		}
		case '\u0ab5':  case '\u0ab6':  case '\u0ab7':  case '\u0ab8':
		case '\u0ab9':
		{
			matchRange('\u0ab5','\u0ab9');
			break;
		}
		case '\u0abd':
		{
			match('\u0abd');
			break;
		}
		case '\u0ae0':
		{
			match('\u0ae0');
			break;
		}
		case '\u0b05':  case '\u0b06':  case '\u0b07':  case '\u0b08':
		case '\u0b09':  case '\u0b0a':  case '\u0b0b':  case '\u0b0c':
		{
			matchRange('\u0b05','\u0b0c');
			break;
		}
		case '\u0b0f':  case '\u0b10':
		{
			matchRange('\u0b0f','\u0b10');
			break;
		}
		case '\u0b13':  case '\u0b14':  case '\u0b15':  case '\u0b16':
		case '\u0b17':  case '\u0b18':  case '\u0b19':  case '\u0b1a':
		case '\u0b1b':  case '\u0b1c':  case '\u0b1d':  case '\u0b1e':
		case '\u0b1f':  case '\u0b20':  case '\u0b21':  case '\u0b22':
		case '\u0b23':  case '\u0b24':  case '\u0b25':  case '\u0b26':
		case '\u0b27':  case '\u0b28':
		{
			matchRange('\u0b13','\u0b28');
			break;
		}
		case '\u0b2a':  case '\u0b2b':  case '\u0b2c':  case '\u0b2d':
		case '\u0b2e':  case '\u0b2f':  case '\u0b30':
		{
			matchRange('\u0b2a','\u0b30');
			break;
		}
		case '\u0b32':  case '\u0b33':
		{
			matchRange('\u0b32','\u0b33');
			break;
		}
		case '\u0b36':  case '\u0b37':  case '\u0b38':  case '\u0b39':
		{
			matchRange('\u0b36','\u0b39');
			break;
		}
		case '\u0b3d':
		{
			match('\u0b3d');
			break;
		}
		case '\u0b5c':  case '\u0b5d':
		{
			matchRange('\u0b5c','\u0b5d');
			break;
		}
		case '\u0b5f':  case '\u0b60':  case '\u0b61':
		{
			matchRange('\u0b5f','\u0b61');
			break;
		}
		case '\u0b85':  case '\u0b86':  case '\u0b87':  case '\u0b88':
		case '\u0b89':  case '\u0b8a':
		{
			matchRange('\u0b85','\u0b8a');
			break;
		}
		case '\u0b8e':  case '\u0b8f':  case '\u0b90':
		{
			matchRange('\u0b8e','\u0b90');
			break;
		}
		case '\u0b92':  case '\u0b93':  case '\u0b94':  case '\u0b95':
		{
			matchRange('\u0b92','\u0b95');
			break;
		}
		case '\u0b99':  case '\u0b9a':
		{
			matchRange('\u0b99','\u0b9a');
			break;
		}
		case '\u0b9c':
		{
			match('\u0b9c');
			break;
		}
		case '\u0b9e':  case '\u0b9f':
		{
			matchRange('\u0b9e','\u0b9f');
			break;
		}
		case '\u0ba3':  case '\u0ba4':
		{
			matchRange('\u0ba3','\u0ba4');
			break;
		}
		case '\u0ba8':  case '\u0ba9':  case '\u0baa':
		{
			matchRange('\u0ba8','\u0baa');
			break;
		}
		case '\u0bae':  case '\u0baf':  case '\u0bb0':  case '\u0bb1':
		case '\u0bb2':  case '\u0bb3':  case '\u0bb4':  case '\u0bb5':
		{
			matchRange('\u0bae','\u0bb5');
			break;
		}
		case '\u0bb7':  case '\u0bb8':  case '\u0bb9':
		{
			matchRange('\u0bb7','\u0bb9');
			break;
		}
		case '\u0c05':  case '\u0c06':  case '\u0c07':  case '\u0c08':
		case '\u0c09':  case '\u0c0a':  case '\u0c0b':  case '\u0c0c':
		{
			matchRange('\u0c05','\u0c0c');
			break;
		}
		case '\u0c0e':  case '\u0c0f':  case '\u0c10':
		{
			matchRange('\u0c0e','\u0c10');
			break;
		}
		case '\u0c12':  case '\u0c13':  case '\u0c14':  case '\u0c15':
		case '\u0c16':  case '\u0c17':  case '\u0c18':  case '\u0c19':
		case '\u0c1a':  case '\u0c1b':  case '\u0c1c':  case '\u0c1d':
		case '\u0c1e':  case '\u0c1f':  case '\u0c20':  case '\u0c21':
		case '\u0c22':  case '\u0c23':  case '\u0c24':  case '\u0c25':
		case '\u0c26':  case '\u0c27':  case '\u0c28':
		{
			matchRange('\u0c12','\u0c28');
			break;
		}
		case '\u0c2a':  case '\u0c2b':  case '\u0c2c':  case '\u0c2d':
		case '\u0c2e':  case '\u0c2f':  case '\u0c30':  case '\u0c31':
		case '\u0c32':  case '\u0c33':
		{
			matchRange('\u0c2a','\u0c33');
			break;
		}
		case '\u0c35':  case '\u0c36':  case '\u0c37':  case '\u0c38':
		case '\u0c39':
		{
			matchRange('\u0c35','\u0c39');
			break;
		}
		case '\u0c60':  case '\u0c61':
		{
			matchRange('\u0c60','\u0c61');
			break;
		}
		case '\u0c85':  case '\u0c86':  case '\u0c87':  case '\u0c88':
		case '\u0c89':  case '\u0c8a':  case '\u0c8b':  case '\u0c8c':
		{
			matchRange('\u0c85','\u0c8c');
			break;
		}
		case '\u0c8e':  case '\u0c8f':  case '\u0c90':
		{
			matchRange('\u0c8e','\u0c90');
			break;
		}
		case '\u0c92':  case '\u0c93':  case '\u0c94':  case '\u0c95':
		case '\u0c96':  case '\u0c97':  case '\u0c98':  case '\u0c99':
		case '\u0c9a':  case '\u0c9b':  case '\u0c9c':  case '\u0c9d':
		case '\u0c9e':  case '\u0c9f':  case '\u0ca0':  case '\u0ca1':
		case '\u0ca2':  case '\u0ca3':  case '\u0ca4':  case '\u0ca5':
		case '\u0ca6':  case '\u0ca7':  case '\u0ca8':
		{
			matchRange('\u0c92','\u0ca8');
			break;
		}
		case '\u0caa':  case '\u0cab':  case '\u0cac':  case '\u0cad':
		case '\u0cae':  case '\u0caf':  case '\u0cb0':  case '\u0cb1':
		case '\u0cb2':  case '\u0cb3':
		{
			matchRange('\u0caa','\u0cb3');
			break;
		}
		case '\u0cb5':  case '\u0cb6':  case '\u0cb7':  case '\u0cb8':
		case '\u0cb9':
		{
			matchRange('\u0cb5','\u0cb9');
			break;
		}
		case '\u0cde':
		{
			match('\u0cde');
			break;
		}
		case '\u0ce0':  case '\u0ce1':
		{
			matchRange('\u0ce0','\u0ce1');
			break;
		}
		case '\u0d05':  case '\u0d06':  case '\u0d07':  case '\u0d08':
		case '\u0d09':  case '\u0d0a':  case '\u0d0b':  case '\u0d0c':
		{
			matchRange('\u0d05','\u0d0c');
			break;
		}
		case '\u0d0e':  case '\u0d0f':  case '\u0d10':
		{
			matchRange('\u0d0e','\u0d10');
			break;
		}
		case '\u0d12':  case '\u0d13':  case '\u0d14':  case '\u0d15':
		case '\u0d16':  case '\u0d17':  case '\u0d18':  case '\u0d19':
		case '\u0d1a':  case '\u0d1b':  case '\u0d1c':  case '\u0d1d':
		case '\u0d1e':  case '\u0d1f':  case '\u0d20':  case '\u0d21':
		case '\u0d22':  case '\u0d23':  case '\u0d24':  case '\u0d25':
		case '\u0d26':  case '\u0d27':  case '\u0d28':
		{
			matchRange('\u0d12','\u0d28');
			break;
		}
		case '\u0d2a':  case '\u0d2b':  case '\u0d2c':  case '\u0d2d':
		case '\u0d2e':  case '\u0d2f':  case '\u0d30':  case '\u0d31':
		case '\u0d32':  case '\u0d33':  case '\u0d34':  case '\u0d35':
		case '\u0d36':  case '\u0d37':  case '\u0d38':  case '\u0d39':
		{
			matchRange('\u0d2a','\u0d39');
			break;
		}
		case '\u0d60':  case '\u0d61':
		{
			matchRange('\u0d60','\u0d61');
			break;
		}
		case '\u0e01':  case '\u0e02':  case '\u0e03':  case '\u0e04':
		case '\u0e05':  case '\u0e06':  case '\u0e07':  case '\u0e08':
		case '\u0e09':  case '\u0e0a':  case '\u0e0b':  case '\u0e0c':
		case '\u0e0d':  case '\u0e0e':  case '\u0e0f':  case '\u0e10':
		case '\u0e11':  case '\u0e12':  case '\u0e13':  case '\u0e14':
		case '\u0e15':  case '\u0e16':  case '\u0e17':  case '\u0e18':
		case '\u0e19':  case '\u0e1a':  case '\u0e1b':  case '\u0e1c':
		case '\u0e1d':  case '\u0e1e':  case '\u0e1f':  case '\u0e20':
		case '\u0e21':  case '\u0e22':  case '\u0e23':  case '\u0e24':
		case '\u0e25':  case '\u0e26':  case '\u0e27':  case '\u0e28':
		case '\u0e29':  case '\u0e2a':  case '\u0e2b':  case '\u0e2c':
		case '\u0e2d':  case '\u0e2e':
		{
			matchRange('\u0e01','\u0e2e');
			break;
		}
		case '\u0e30':
		{
			match('\u0e30');
			break;
		}
		case '\u0e32':  case '\u0e33':
		{
			matchRange('\u0e32','\u0e33');
			break;
		}
		case '\u0e40':  case '\u0e41':  case '\u0e42':  case '\u0e43':
		case '\u0e44':  case '\u0e45':
		{
			matchRange('\u0e40','\u0e45');
			break;
		}
		case '\u0e81':  case '\u0e82':
		{
			matchRange('\u0e81','\u0e82');
			break;
		}
		case '\u0e84':
		{
			match('\u0e84');
			break;
		}
		case '\u0e87':  case '\u0e88':
		{
			matchRange('\u0e87','\u0e88');
			break;
		}
		case '\u0e8a':
		{
			match('\u0e8a');
			break;
		}
		case '\u0e8d':
		{
			match('\u0e8d');
			break;
		}
		case '\u0e94':  case '\u0e95':  case '\u0e96':  case '\u0e97':
		{
			matchRange('\u0e94','\u0e97');
			break;
		}
		case '\u0e99':  case '\u0e9a':  case '\u0e9b':  case '\u0e9c':
		case '\u0e9d':  case '\u0e9e':  case '\u0e9f':
		{
			matchRange('\u0e99','\u0e9f');
			break;
		}
		case '\u0ea1':  case '\u0ea2':  case '\u0ea3':
		{
			matchRange('\u0ea1','\u0ea3');
			break;
		}
		case '\u0ea5':
		{
			match('\u0ea5');
			break;
		}
		case '\u0ea7':
		{
			match('\u0ea7');
			break;
		}
		case '\u0eaa':  case '\u0eab':
		{
			matchRange('\u0eaa','\u0eab');
			break;
		}
		case '\u0ead':  case '\u0eae':
		{
			matchRange('\u0ead','\u0eae');
			break;
		}
		case '\u0eb0':
		{
			match('\u0eb0');
			break;
		}
		case '\u0eb2':  case '\u0eb3':
		{
			matchRange('\u0eb2','\u0eb3');
			break;
		}
		case '\u0ebd':
		{
			match('\u0ebd');
			break;
		}
		case '\u0ec0':  case '\u0ec1':  case '\u0ec2':  case '\u0ec3':
		case '\u0ec4':
		{
			matchRange('\u0ec0','\u0ec4');
			break;
		}
		case '\u0f40':  case '\u0f41':  case '\u0f42':  case '\u0f43':
		case '\u0f44':  case '\u0f45':  case '\u0f46':  case '\u0f47':
		{
			matchRange('\u0f40','\u0f47');
			break;
		}
		case '\u0f49':  case '\u0f4a':  case '\u0f4b':  case '\u0f4c':
		case '\u0f4d':  case '\u0f4e':  case '\u0f4f':  case '\u0f50':
		case '\u0f51':  case '\u0f52':  case '\u0f53':  case '\u0f54':
		case '\u0f55':  case '\u0f56':  case '\u0f57':  case '\u0f58':
		case '\u0f59':  case '\u0f5a':  case '\u0f5b':  case '\u0f5c':
		case '\u0f5d':  case '\u0f5e':  case '\u0f5f':  case '\u0f60':
		case '\u0f61':  case '\u0f62':  case '\u0f63':  case '\u0f64':
		case '\u0f65':  case '\u0f66':  case '\u0f67':  case '\u0f68':
		case '\u0f69':
		{
			matchRange('\u0f49','\u0f69');
			break;
		}
		case '\u10a0':  case '\u10a1':  case '\u10a2':  case '\u10a3':
		case '\u10a4':  case '\u10a5':  case '\u10a6':  case '\u10a7':
		case '\u10a8':  case '\u10a9':  case '\u10aa':  case '\u10ab':
		case '\u10ac':  case '\u10ad':  case '\u10ae':  case '\u10af':
		case '\u10b0':  case '\u10b1':  case '\u10b2':  case '\u10b3':
		case '\u10b4':  case '\u10b5':  case '\u10b6':  case '\u10b7':
		case '\u10b8':  case '\u10b9':  case '\u10ba':  case '\u10bb':
		case '\u10bc':  case '\u10bd':  case '\u10be':  case '\u10bf':
		case '\u10c0':  case '\u10c1':  case '\u10c2':  case '\u10c3':
		case '\u10c4':  case '\u10c5':
		{
			matchRange('\u10a0','\u10c5');
			break;
		}
		case '\u10d0':  case '\u10d1':  case '\u10d2':  case '\u10d3':
		case '\u10d4':  case '\u10d5':  case '\u10d6':  case '\u10d7':
		case '\u10d8':  case '\u10d9':  case '\u10da':  case '\u10db':
		case '\u10dc':  case '\u10dd':  case '\u10de':  case '\u10df':
		case '\u10e0':  case '\u10e1':  case '\u10e2':  case '\u10e3':
		case '\u10e4':  case '\u10e5':  case '\u10e6':  case '\u10e7':
		case '\u10e8':  case '\u10e9':  case '\u10ea':  case '\u10eb':
		case '\u10ec':  case '\u10ed':  case '\u10ee':  case '\u10ef':
		case '\u10f0':  case '\u10f1':  case '\u10f2':  case '\u10f3':
		case '\u10f4':  case '\u10f5':  case '\u10f6':
		{
			matchRange('\u10d0','\u10f6');
			break;
		}
		case '\u1100':
		{
			match('\u1100');
			break;
		}
		case '\u1102':  case '\u1103':
		{
			matchRange('\u1102','\u1103');
			break;
		}
		case '\u1105':  case '\u1106':  case '\u1107':
		{
			matchRange('\u1105','\u1107');
			break;
		}
		case '\u1109':
		{
			match('\u1109');
			break;
		}
		case '\u110b':  case '\u110c':
		{
			matchRange('\u110b','\u110c');
			break;
		}
		case '\u110e':  case '\u110f':  case '\u1110':  case '\u1111':
		case '\u1112':
		{
			matchRange('\u110e','\u1112');
			break;
		}
		case '\u113c':
		{
			match('\u113c');
			break;
		}
		case '\u113e':
		{
			match('\u113e');
			break;
		}
		case '\u1140':
		{
			match('\u1140');
			break;
		}
		case '\u114c':
		{
			match('\u114c');
			break;
		}
		case '\u114e':
		{
			match('\u114e');
			break;
		}
		case '\u1150':
		{
			match('\u1150');
			break;
		}
		case '\u1154':  case '\u1155':
		{
			matchRange('\u1154','\u1155');
			break;
		}
		case '\u1159':
		{
			match('\u1159');
			break;
		}
		case '\u115f':  case '\u1160':  case '\u1161':
		{
			matchRange('\u115f','\u1161');
			break;
		}
		case '\u1163':
		{
			match('\u1163');
			break;
		}
		case '\u1165':
		{
			match('\u1165');
			break;
		}
		case '\u1167':
		{
			match('\u1167');
			break;
		}
		case '\u1169':
		{
			match('\u1169');
			break;
		}
		case '\u116d':  case '\u116e':
		{
			matchRange('\u116d','\u116e');
			break;
		}
		case '\u1172':  case '\u1173':
		{
			matchRange('\u1172','\u1173');
			break;
		}
		case '\u1175':
		{
			match('\u1175');
			break;
		}
		case '\u119e':
		{
			match('\u119e');
			break;
		}
		case '\u11a8':
		{
			match('\u11a8');
			break;
		}
		case '\u11ab':
		{
			match('\u11ab');
			break;
		}
		case '\u11ae':  case '\u11af':
		{
			matchRange('\u11ae','\u11af');
			break;
		}
		case '\u11b7':  case '\u11b8':
		{
			matchRange('\u11b7','\u11b8');
			break;
		}
		case '\u11ba':
		{
			match('\u11ba');
			break;
		}
		case '\u11bc':  case '\u11bd':  case '\u11be':  case '\u11bf':
		case '\u11c0':  case '\u11c1':  case '\u11c2':
		{
			matchRange('\u11bc','\u11c2');
			break;
		}
		case '\u11eb':
		{
			match('\u11eb');
			break;
		}
		case '\u11f0':
		{
			match('\u11f0');
			break;
		}
		case '\u11f9':
		{
			match('\u11f9');
			break;
		}
		case '\u1ea0':  case '\u1ea1':  case '\u1ea2':  case '\u1ea3':
		case '\u1ea4':  case '\u1ea5':  case '\u1ea6':  case '\u1ea7':
		case '\u1ea8':  case '\u1ea9':  case '\u1eaa':  case '\u1eab':
		case '\u1eac':  case '\u1ead':  case '\u1eae':  case '\u1eaf':
		case '\u1eb0':  case '\u1eb1':  case '\u1eb2':  case '\u1eb3':
		case '\u1eb4':  case '\u1eb5':  case '\u1eb6':  case '\u1eb7':
		case '\u1eb8':  case '\u1eb9':  case '\u1eba':  case '\u1ebb':
		case '\u1ebc':  case '\u1ebd':  case '\u1ebe':  case '\u1ebf':
		case '\u1ec0':  case '\u1ec1':  case '\u1ec2':  case '\u1ec3':
		case '\u1ec4':  case '\u1ec5':  case '\u1ec6':  case '\u1ec7':
		case '\u1ec8':  case '\u1ec9':  case '\u1eca':  case '\u1ecb':
		case '\u1ecc':  case '\u1ecd':  case '\u1ece':  case '\u1ecf':
		case '\u1ed0':  case '\u1ed1':  case '\u1ed2':  case '\u1ed3':
		case '\u1ed4':  case '\u1ed5':  case '\u1ed6':  case '\u1ed7':
		case '\u1ed8':  case '\u1ed9':  case '\u1eda':  case '\u1edb':
		case '\u1edc':  case '\u1edd':  case '\u1ede':  case '\u1edf':
		case '\u1ee0':  case '\u1ee1':  case '\u1ee2':  case '\u1ee3':
		case '\u1ee4':  case '\u1ee5':  case '\u1ee6':  case '\u1ee7':
		case '\u1ee8':  case '\u1ee9':  case '\u1eea':  case '\u1eeb':
		case '\u1eec':  case '\u1eed':  case '\u1eee':  case '\u1eef':
		case '\u1ef0':  case '\u1ef1':  case '\u1ef2':  case '\u1ef3':
		case '\u1ef4':  case '\u1ef5':  case '\u1ef6':  case '\u1ef7':
		case '\u1ef8':  case '\u1ef9':
		{
			matchRange('\u1ea0','\u1ef9');
			break;
		}
		case '\u1f00':  case '\u1f01':  case '\u1f02':  case '\u1f03':
		case '\u1f04':  case '\u1f05':  case '\u1f06':  case '\u1f07':
		case '\u1f08':  case '\u1f09':  case '\u1f0a':  case '\u1f0b':
		case '\u1f0c':  case '\u1f0d':  case '\u1f0e':  case '\u1f0f':
		case '\u1f10':  case '\u1f11':  case '\u1f12':  case '\u1f13':
		case '\u1f14':  case '\u1f15':
		{
			matchRange('\u1f00','\u1f15');
			break;
		}
		case '\u1f18':  case '\u1f19':  case '\u1f1a':  case '\u1f1b':
		case '\u1f1c':  case '\u1f1d':
		{
			matchRange('\u1f18','\u1f1d');
			break;
		}
		case '\u1f20':  case '\u1f21':  case '\u1f22':  case '\u1f23':
		case '\u1f24':  case '\u1f25':  case '\u1f26':  case '\u1f27':
		case '\u1f28':  case '\u1f29':  case '\u1f2a':  case '\u1f2b':
		case '\u1f2c':  case '\u1f2d':  case '\u1f2e':  case '\u1f2f':
		case '\u1f30':  case '\u1f31':  case '\u1f32':  case '\u1f33':
		case '\u1f34':  case '\u1f35':  case '\u1f36':  case '\u1f37':
		case '\u1f38':  case '\u1f39':  case '\u1f3a':  case '\u1f3b':
		case '\u1f3c':  case '\u1f3d':  case '\u1f3e':  case '\u1f3f':
		case '\u1f40':  case '\u1f41':  case '\u1f42':  case '\u1f43':
		case '\u1f44':  case '\u1f45':
		{
			matchRange('\u1f20','\u1f45');
			break;
		}
		case '\u1f48':  case '\u1f49':  case '\u1f4a':  case '\u1f4b':
		case '\u1f4c':  case '\u1f4d':
		{
			matchRange('\u1f48','\u1f4d');
			break;
		}
		case '\u1f50':  case '\u1f51':  case '\u1f52':  case '\u1f53':
		case '\u1f54':  case '\u1f55':  case '\u1f56':  case '\u1f57':
		{
			matchRange('\u1f50','\u1f57');
			break;
		}
		case '\u1f59':
		{
			match('\u1f59');
			break;
		}
		case '\u1f5b':
		{
			match('\u1f5b');
			break;
		}
		case '\u1f5d':
		{
			match('\u1f5d');
			break;
		}
		case '\u1f5f':  case '\u1f60':  case '\u1f61':  case '\u1f62':
		case '\u1f63':  case '\u1f64':  case '\u1f65':  case '\u1f66':
		case '\u1f67':  case '\u1f68':  case '\u1f69':  case '\u1f6a':
		case '\u1f6b':  case '\u1f6c':  case '\u1f6d':  case '\u1f6e':
		case '\u1f6f':  case '\u1f70':  case '\u1f71':  case '\u1f72':
		case '\u1f73':  case '\u1f74':  case '\u1f75':  case '\u1f76':
		case '\u1f77':  case '\u1f78':  case '\u1f79':  case '\u1f7a':
		case '\u1f7b':  case '\u1f7c':  case '\u1f7d':
		{
			matchRange('\u1f5f','\u1f7d');
			break;
		}
		case '\u1f80':  case '\u1f81':  case '\u1f82':  case '\u1f83':
		case '\u1f84':  case '\u1f85':  case '\u1f86':  case '\u1f87':
		case '\u1f88':  case '\u1f89':  case '\u1f8a':  case '\u1f8b':
		case '\u1f8c':  case '\u1f8d':  case '\u1f8e':  case '\u1f8f':
		case '\u1f90':  case '\u1f91':  case '\u1f92':  case '\u1f93':
		case '\u1f94':  case '\u1f95':  case '\u1f96':  case '\u1f97':
		case '\u1f98':  case '\u1f99':  case '\u1f9a':  case '\u1f9b':
		case '\u1f9c':  case '\u1f9d':  case '\u1f9e':  case '\u1f9f':
		case '\u1fa0':  case '\u1fa1':  case '\u1fa2':  case '\u1fa3':
		case '\u1fa4':  case '\u1fa5':  case '\u1fa6':  case '\u1fa7':
		case '\u1fa8':  case '\u1fa9':  case '\u1faa':  case '\u1fab':
		case '\u1fac':  case '\u1fad':  case '\u1fae':  case '\u1faf':
		case '\u1fb0':  case '\u1fb1':  case '\u1fb2':  case '\u1fb3':
		case '\u1fb4':
		{
			matchRange('\u1f80','\u1fb4');
			break;
		}
		case '\u1fb6':  case '\u1fb7':  case '\u1fb8':  case '\u1fb9':
		case '\u1fba':  case '\u1fbb':  case '\u1fbc':
		{
			matchRange('\u1fb6','\u1fbc');
			break;
		}
		case '\u1fbe':
		{
			match('\u1fbe');
			break;
		}
		case '\u1fc2':  case '\u1fc3':  case '\u1fc4':
		{
			matchRange('\u1fc2','\u1fc4');
			break;
		}
		case '\u1fc6':  case '\u1fc7':  case '\u1fc8':  case '\u1fc9':
		case '\u1fca':  case '\u1fcb':  case '\u1fcc':
		{
			matchRange('\u1fc6','\u1fcc');
			break;
		}
		case '\u1fd0':  case '\u1fd1':  case '\u1fd2':  case '\u1fd3':
		{
			matchRange('\u1fd0','\u1fd3');
			break;
		}
		case '\u1fd6':  case '\u1fd7':  case '\u1fd8':  case '\u1fd9':
		case '\u1fda':  case '\u1fdb':
		{
			matchRange('\u1fd6','\u1fdb');
			break;
		}
		case '\u1fe0':  case '\u1fe1':  case '\u1fe2':  case '\u1fe3':
		case '\u1fe4':  case '\u1fe5':  case '\u1fe6':  case '\u1fe7':
		case '\u1fe8':  case '\u1fe9':  case '\u1fea':  case '\u1feb':
		case '\u1fec':
		{
			matchRange('\u1fe0','\u1fec');
			break;
		}
		case '\u1ff2':  case '\u1ff3':  case '\u1ff4':
		{
			matchRange('\u1ff2','\u1ff4');
			break;
		}
		case '\u1ff6':  case '\u1ff7':  case '\u1ff8':  case '\u1ff9':
		case '\u1ffa':  case '\u1ffb':  case '\u1ffc':
		{
			matchRange('\u1ff6','\u1ffc');
			break;
		}
		case '\u2126':
		{
			match('\u2126');
			break;
		}
		case '\u212a':  case '\u212b':
		{
			matchRange('\u212a','\u212b');
			break;
		}
		case '\u212e':
		{
			match('\u212e');
			break;
		}
		case '\u2180':  case '\u2181':  case '\u2182':
		{
			matchRange('\u2180','\u2182');
			break;
		}
		case '\u3041':  case '\u3042':  case '\u3043':  case '\u3044':
		case '\u3045':  case '\u3046':  case '\u3047':  case '\u3048':
		case '\u3049':  case '\u304a':  case '\u304b':  case '\u304c':
		case '\u304d':  case '\u304e':  case '\u304f':  case '\u3050':
		case '\u3051':  case '\u3052':  case '\u3053':  case '\u3054':
		case '\u3055':  case '\u3056':  case '\u3057':  case '\u3058':
		case '\u3059':  case '\u305a':  case '\u305b':  case '\u305c':
		case '\u305d':  case '\u305e':  case '\u305f':  case '\u3060':
		case '\u3061':  case '\u3062':  case '\u3063':  case '\u3064':
		case '\u3065':  case '\u3066':  case '\u3067':  case '\u3068':
		case '\u3069':  case '\u306a':  case '\u306b':  case '\u306c':
		case '\u306d':  case '\u306e':  case '\u306f':  case '\u3070':
		case '\u3071':  case '\u3072':  case '\u3073':  case '\u3074':
		case '\u3075':  case '\u3076':  case '\u3077':  case '\u3078':
		case '\u3079':  case '\u307a':  case '\u307b':  case '\u307c':
		case '\u307d':  case '\u307e':  case '\u307f':  case '\u3080':
		case '\u3081':  case '\u3082':  case '\u3083':  case '\u3084':
		case '\u3085':  case '\u3086':  case '\u3087':  case '\u3088':
		case '\u3089':  case '\u308a':  case '\u308b':  case '\u308c':
		case '\u308d':  case '\u308e':  case '\u308f':  case '\u3090':
		case '\u3091':  case '\u3092':  case '\u3093':  case '\u3094':
		{
			matchRange('\u3041','\u3094');
			break;
		}
		case '\u30a1':  case '\u30a2':  case '\u30a3':  case '\u30a4':
		case '\u30a5':  case '\u30a6':  case '\u30a7':  case '\u30a8':
		case '\u30a9':  case '\u30aa':  case '\u30ab':  case '\u30ac':
		case '\u30ad':  case '\u30ae':  case '\u30af':  case '\u30b0':
		case '\u30b1':  case '\u30b2':  case '\u30b3':  case '\u30b4':
		case '\u30b5':  case '\u30b6':  case '\u30b7':  case '\u30b8':
		case '\u30b9':  case '\u30ba':  case '\u30bb':  case '\u30bc':
		case '\u30bd':  case '\u30be':  case '\u30bf':  case '\u30c0':
		case '\u30c1':  case '\u30c2':  case '\u30c3':  case '\u30c4':
		case '\u30c5':  case '\u30c6':  case '\u30c7':  case '\u30c8':
		case '\u30c9':  case '\u30ca':  case '\u30cb':  case '\u30cc':
		case '\u30cd':  case '\u30ce':  case '\u30cf':  case '\u30d0':
		case '\u30d1':  case '\u30d2':  case '\u30d3':  case '\u30d4':
		case '\u30d5':  case '\u30d6':  case '\u30d7':  case '\u30d8':
		case '\u30d9':  case '\u30da':  case '\u30db':  case '\u30dc':
		case '\u30dd':  case '\u30de':  case '\u30df':  case '\u30e0':
		case '\u30e1':  case '\u30e2':  case '\u30e3':  case '\u30e4':
		case '\u30e5':  case '\u30e6':  case '\u30e7':  case '\u30e8':
		case '\u30e9':  case '\u30ea':  case '\u30eb':  case '\u30ec':
		case '\u30ed':  case '\u30ee':  case '\u30ef':  case '\u30f0':
		case '\u30f1':  case '\u30f2':  case '\u30f3':  case '\u30f4':
		case '\u30f5':  case '\u30f6':  case '\u30f7':  case '\u30f8':
		case '\u30f9':  case '\u30fa':
		{
			matchRange('\u30a1','\u30fa');
			break;
		}
		case '\u3105':  case '\u3106':  case '\u3107':  case '\u3108':
		case '\u3109':  case '\u310a':  case '\u310b':  case '\u310c':
		case '\u310d':  case '\u310e':  case '\u310f':  case '\u3110':
		case '\u3111':  case '\u3112':  case '\u3113':  case '\u3114':
		case '\u3115':  case '\u3116':  case '\u3117':  case '\u3118':
		case '\u3119':  case '\u311a':  case '\u311b':  case '\u311c':
		case '\u311d':  case '\u311e':  case '\u311f':  case '\u3120':
		case '\u3121':  case '\u3122':  case '\u3123':  case '\u3124':
		case '\u3125':  case '\u3126':  case '\u3127':  case '\u3128':
		case '\u3129':  case '\u312a':  case '\u312b':  case '\u312c':
		{
			matchRange('\u3105','\u312c');
			break;
		}
		default:
			if (((LA(1) >= '\u1e00' && LA(1) <= '\u1e9b'))) {
				matchRange('\u1e00','\u1e9b');
			}
			else if (((LA(1) >= '\uac00' && LA(1) <= '\ud7a3'))) {
				matchRange('\uac00','\ud7a3');
			}
		else {
			throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());
		}
		}
		}
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mIDEOGRAPHIC(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = IDEOGRAPHIC;
		int _saveIndex;
		
		{
		switch ( LA(1)) {
		case '\u3007':
		{
			match('\u3007');
			break;
		}
		case '\u3021':  case '\u3022':  case '\u3023':  case '\u3024':
		case '\u3025':  case '\u3026':  case '\u3027':  case '\u3028':
		case '\u3029':
		{
			matchRange('\u3021','\u3029');
			break;
		}
		default:
			if (((LA(1) >= '\u4e00' && LA(1) <= '\u9fa5'))) {
				matchRange('\u4e00','\u9fa5');
			}
		else {
			throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());
		}
		}
		}
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mDIGITS(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = DIGITS;
		int _saveIndex;
		
		{
		int _cnt443=0;
		_loop443:
		do {
			if ((_tokenSet_2.member(LA(1)))) {
				mDIGIT(false);
			}
			else {
				if ( _cnt443>=1 ) { break _loop443; } else {throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());}
			}
			
			_cnt443++;
		} while (true);
		}
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mDIGIT(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = DIGIT;
		int _saveIndex;
		
		{
		switch ( LA(1)) {
		case '0':  case '1':  case '2':  case '3':
		case '4':  case '5':  case '6':  case '7':
		case '8':  case '9':
		{
			matchRange('\u0030','\u0039');
			break;
		}
		case '\u0660':  case '\u0661':  case '\u0662':  case '\u0663':
		case '\u0664':  case '\u0665':  case '\u0666':  case '\u0667':
		case '\u0668':  case '\u0669':
		{
			matchRange('\u0660','\u0669');
			break;
		}
		case '\u06f0':  case '\u06f1':  case '\u06f2':  case '\u06f3':
		case '\u06f4':  case '\u06f5':  case '\u06f6':  case '\u06f7':
		case '\u06f8':  case '\u06f9':
		{
			matchRange('\u06f0','\u06f9');
			break;
		}
		case '\u0966':  case '\u0967':  case '\u0968':  case '\u0969':
		case '\u096a':  case '\u096b':  case '\u096c':  case '\u096d':
		case '\u096e':  case '\u096f':
		{
			matchRange('\u0966','\u096f');
			break;
		}
		case '\u09e6':  case '\u09e7':  case '\u09e8':  case '\u09e9':
		case '\u09ea':  case '\u09eb':  case '\u09ec':  case '\u09ed':
		case '\u09ee':  case '\u09ef':
		{
			matchRange('\u09e6','\u09ef');
			break;
		}
		case '\u0a66':  case '\u0a67':  case '\u0a68':  case '\u0a69':
		case '\u0a6a':  case '\u0a6b':  case '\u0a6c':  case '\u0a6d':
		case '\u0a6e':  case '\u0a6f':
		{
			matchRange('\u0a66','\u0a6f');
			break;
		}
		case '\u0ae6':  case '\u0ae7':  case '\u0ae8':  case '\u0ae9':
		case '\u0aea':  case '\u0aeb':  case '\u0aec':  case '\u0aed':
		case '\u0aee':  case '\u0aef':
		{
			matchRange('\u0ae6','\u0aef');
			break;
		}
		case '\u0b66':  case '\u0b67':  case '\u0b68':  case '\u0b69':
		case '\u0b6a':  case '\u0b6b':  case '\u0b6c':  case '\u0b6d':
		case '\u0b6e':  case '\u0b6f':
		{
			matchRange('\u0b66','\u0b6f');
			break;
		}
		case '\u0be7':  case '\u0be8':  case '\u0be9':  case '\u0bea':
		case '\u0beb':  case '\u0bec':  case '\u0bed':  case '\u0bee':
		case '\u0bef':
		{
			matchRange('\u0be7','\u0bef');
			break;
		}
		case '\u0c66':  case '\u0c67':  case '\u0c68':  case '\u0c69':
		case '\u0c6a':  case '\u0c6b':  case '\u0c6c':  case '\u0c6d':
		case '\u0c6e':  case '\u0c6f':
		{
			matchRange('\u0c66','\u0c6f');
			break;
		}
		case '\u0ce6':  case '\u0ce7':  case '\u0ce8':  case '\u0ce9':
		case '\u0cea':  case '\u0ceb':  case '\u0cec':  case '\u0ced':
		case '\u0cee':  case '\u0cef':
		{
			matchRange('\u0ce6','\u0cef');
			break;
		}
		case '\u0d66':  case '\u0d67':  case '\u0d68':  case '\u0d69':
		case '\u0d6a':  case '\u0d6b':  case '\u0d6c':  case '\u0d6d':
		case '\u0d6e':  case '\u0d6f':
		{
			matchRange('\u0d66','\u0d6f');
			break;
		}
		case '\u0e50':  case '\u0e51':  case '\u0e52':  case '\u0e53':
		case '\u0e54':  case '\u0e55':  case '\u0e56':  case '\u0e57':
		case '\u0e58':  case '\u0e59':
		{
			matchRange('\u0e50','\u0e59');
			break;
		}
		case '\u0ed0':  case '\u0ed1':  case '\u0ed2':  case '\u0ed3':
		case '\u0ed4':  case '\u0ed5':  case '\u0ed6':  case '\u0ed7':
		case '\u0ed8':  case '\u0ed9':
		{
			matchRange('\u0ed0','\u0ed9');
			break;
		}
		case '\u0f20':  case '\u0f21':  case '\u0f22':  case '\u0f23':
		case '\u0f24':  case '\u0f25':  case '\u0f26':  case '\u0f27':
		case '\u0f28':  case '\u0f29':
		{
			matchRange('\u0f20','\u0f29');
			break;
		}
		default:
		{
			throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());
		}
		}
		}
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mHEX_DIGITS(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = HEX_DIGITS;
		int _saveIndex;
		
		{
		int _cnt446=0;
		_loop446:
		do {
			switch ( LA(1)) {
			case '0':  case '1':  case '2':  case '3':
			case '4':  case '5':  case '6':  case '7':
			case '8':  case '9':
			{
				matchRange('0','9');
				break;
			}
			case 'a':  case 'b':  case 'c':  case 'd':
			case 'e':  case 'f':
			{
				matchRange('a','f');
				break;
			}
			case 'A':  case 'B':  case 'C':  case 'D':
			case 'E':  case 'F':
			{
				matchRange('A','F');
				break;
			}
			default:
			{
				if ( _cnt446>=1 ) { break _loop446; } else {throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());}
			}
			}
			_cnt446++;
		} while (true);
		}
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mNMSTART(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = NMSTART;
		int _saveIndex;
		
		{
		if ((_tokenSet_3.member(LA(1)))) {
			mLETTER(false);
		}
		else if ((LA(1)=='_')) {
			match('_');
		}
		else {
			throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());
		}
		
		}
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mNMCHAR(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = NMCHAR;
		int _saveIndex;
		
		{
		switch ( LA(1)) {
		case '.':
		{
			match('.');
			break;
		}
		case '-':
		{
			match('-');
			break;
		}
		case '_':
		{
			match('_');
			break;
		}
		case '\u00b7':  case '\u02d0':  case '\u02d1':  case '\u0387':
		case '\u0640':  case '\u0e46':  case '\u0ec6':  case '\u3005':
		case '\u3031':  case '\u3032':  case '\u3033':  case '\u3034':
		case '\u3035':  case '\u309d':  case '\u309e':  case '\u30fc':
		case '\u30fd':  case '\u30fe':
		{
			mEXTENDER(false);
			break;
		}
		default:
			if ((_tokenSet_3.member(LA(1)))) {
				mLETTER(false);
			}
			else if ((_tokenSet_2.member(LA(1)))) {
				mDIGIT(false);
			}
			else if ((_tokenSet_4.member(LA(1)))) {
				mCOMBINING_CHAR(false);
			}
		else {
			throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());
		}
		}
		}
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mCOMBINING_CHAR(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = COMBINING_CHAR;
		int _saveIndex;
		
		{
		switch ( LA(1)) {
		case '\u0300':  case '\u0301':  case '\u0302':  case '\u0303':
		case '\u0304':  case '\u0305':  case '\u0306':  case '\u0307':
		case '\u0308':  case '\u0309':  case '\u030a':  case '\u030b':
		case '\u030c':  case '\u030d':  case '\u030e':  case '\u030f':
		case '\u0310':  case '\u0311':  case '\u0312':  case '\u0313':
		case '\u0314':  case '\u0315':  case '\u0316':  case '\u0317':
		case '\u0318':  case '\u0319':  case '\u031a':  case '\u031b':
		case '\u031c':  case '\u031d':  case '\u031e':  case '\u031f':
		case '\u0320':  case '\u0321':  case '\u0322':  case '\u0323':
		case '\u0324':  case '\u0325':  case '\u0326':  case '\u0327':
		case '\u0328':  case '\u0329':  case '\u032a':  case '\u032b':
		case '\u032c':  case '\u032d':  case '\u032e':  case '\u032f':
		case '\u0330':  case '\u0331':  case '\u0332':  case '\u0333':
		case '\u0334':  case '\u0335':  case '\u0336':  case '\u0337':
		case '\u0338':  case '\u0339':  case '\u033a':  case '\u033b':
		case '\u033c':  case '\u033d':  case '\u033e':  case '\u033f':
		case '\u0340':  case '\u0341':  case '\u0342':  case '\u0343':
		case '\u0344':  case '\u0345':
		{
			matchRange('\u0300','\u0345');
			break;
		}
		case '\u0360':  case '\u0361':
		{
			matchRange('\u0360','\u0361');
			break;
		}
		case '\u0483':  case '\u0484':  case '\u0485':  case '\u0486':
		{
			matchRange('\u0483','\u0486');
			break;
		}
		case '\u0591':  case '\u0592':  case '\u0593':  case '\u0594':
		case '\u0595':  case '\u0596':  case '\u0597':  case '\u0598':
		case '\u0599':  case '\u059a':  case '\u059b':  case '\u059c':
		case '\u059d':  case '\u059e':  case '\u059f':  case '\u05a0':
		case '\u05a1':
		{
			matchRange('\u0591','\u05a1');
			break;
		}
		case '\u05a3':  case '\u05a4':  case '\u05a5':  case '\u05a6':
		case '\u05a7':  case '\u05a8':  case '\u05a9':  case '\u05aa':
		case '\u05ab':  case '\u05ac':  case '\u05ad':  case '\u05ae':
		case '\u05af':  case '\u05b0':  case '\u05b1':  case '\u05b2':
		case '\u05b3':  case '\u05b4':  case '\u05b5':  case '\u05b6':
		case '\u05b7':  case '\u05b8':  case '\u05b9':
		{
			matchRange('\u05a3','\u05b9');
			break;
		}
		case '\u05bb':  case '\u05bc':  case '\u05bd':
		{
			matchRange('\u05bb','\u05bd');
			break;
		}
		case '\u05bf':
		{
			match('\u05bf');
			break;
		}
		case '\u05c1':  case '\u05c2':
		{
			matchRange('\u05c1','\u05c2');
			break;
		}
		case '\u05c4':
		{
			match('\u05c4');
			break;
		}
		case '\u064b':  case '\u064c':  case '\u064d':  case '\u064e':
		case '\u064f':  case '\u0650':  case '\u0651':  case '\u0652':
		{
			matchRange('\u064b','\u0652');
			break;
		}
		case '\u0670':
		{
			match('\u0670');
			break;
		}
		case '\u06d6':  case '\u06d7':  case '\u06d8':  case '\u06d9':
		case '\u06da':  case '\u06db':  case '\u06dc':
		{
			matchRange('\u06d6','\u06dc');
			break;
		}
		case '\u06dd':  case '\u06de':  case '\u06df':
		{
			matchRange('\u06dd','\u06df');
			break;
		}
		case '\u06e0':  case '\u06e1':  case '\u06e2':  case '\u06e3':
		case '\u06e4':
		{
			matchRange('\u06e0','\u06e4');
			break;
		}
		case '\u06e7':  case '\u06e8':
		{
			matchRange('\u06e7','\u06e8');
			break;
		}
		case '\u06ea':  case '\u06eb':  case '\u06ec':  case '\u06ed':
		{
			matchRange('\u06ea','\u06ed');
			break;
		}
		case '\u0901':  case '\u0902':  case '\u0903':
		{
			matchRange('\u0901','\u0903');
			break;
		}
		case '\u093c':
		{
			match('\u093c');
			break;
		}
		case '\u093e':  case '\u093f':  case '\u0940':  case '\u0941':
		case '\u0942':  case '\u0943':  case '\u0944':  case '\u0945':
		case '\u0946':  case '\u0947':  case '\u0948':  case '\u0949':
		case '\u094a':  case '\u094b':  case '\u094c':
		{
			matchRange('\u093e','\u094c');
			break;
		}
		case '\u094d':
		{
			match('\u094d');
			break;
		}
		case '\u0951':  case '\u0952':  case '\u0953':  case '\u0954':
		{
			matchRange('\u0951','\u0954');
			break;
		}
		case '\u0962':  case '\u0963':
		{
			matchRange('\u0962','\u0963');
			break;
		}
		case '\u0981':  case '\u0982':  case '\u0983':
		{
			matchRange('\u0981','\u0983');
			break;
		}
		case '\u09bc':
		{
			match('\u09bc');
			break;
		}
		case '\u09be':
		{
			match('\u09be');
			break;
		}
		case '\u09bf':
		{
			match('\u09bf');
			break;
		}
		case '\u09c0':  case '\u09c1':  case '\u09c2':  case '\u09c3':
		case '\u09c4':
		{
			matchRange('\u09c0','\u09c4');
			break;
		}
		case '\u09c7':  case '\u09c8':
		{
			matchRange('\u09c7','\u09c8');
			break;
		}
		case '\u09cb':  case '\u09cc':  case '\u09cd':
		{
			matchRange('\u09cb','\u09cd');
			break;
		}
		case '\u09d7':
		{
			match('\u09d7');
			break;
		}
		case '\u09e2':  case '\u09e3':
		{
			matchRange('\u09e2','\u09e3');
			break;
		}
		case '\u0a02':
		{
			match('\u0a02');
			break;
		}
		case '\u0a3c':
		{
			match('\u0a3c');
			break;
		}
		case '\u0a3e':
		{
			match('\u0a3e');
			break;
		}
		case '\u0a3f':
		{
			match('\u0a3f');
			break;
		}
		case '\u0a40':  case '\u0a41':  case '\u0a42':
		{
			matchRange('\u0a40','\u0a42');
			break;
		}
		case '\u0a47':  case '\u0a48':
		{
			matchRange('\u0a47','\u0a48');
			break;
		}
		case '\u0a4b':  case '\u0a4c':  case '\u0a4d':
		{
			matchRange('\u0a4b','\u0a4d');
			break;
		}
		case '\u0a70':  case '\u0a71':
		{
			matchRange('\u0a70','\u0a71');
			break;
		}
		case '\u0a81':  case '\u0a82':  case '\u0a83':
		{
			matchRange('\u0a81','\u0a83');
			break;
		}
		case '\u0abc':
		{
			match('\u0abc');
			break;
		}
		case '\u0abe':  case '\u0abf':  case '\u0ac0':  case '\u0ac1':
		case '\u0ac2':  case '\u0ac3':  case '\u0ac4':  case '\u0ac5':
		{
			matchRange('\u0abe','\u0ac5');
			break;
		}
		case '\u0ac7':  case '\u0ac8':  case '\u0ac9':
		{
			matchRange('\u0ac7','\u0ac9');
			break;
		}
		case '\u0acb':  case '\u0acc':  case '\u0acd':
		{
			matchRange('\u0acb','\u0acd');
			break;
		}
		case '\u0b01':  case '\u0b02':  case '\u0b03':
		{
			matchRange('\u0b01','\u0b03');
			break;
		}
		case '\u0b3c':
		{
			match('\u0b3c');
			break;
		}
		case '\u0b3e':  case '\u0b3f':  case '\u0b40':  case '\u0b41':
		case '\u0b42':  case '\u0b43':
		{
			matchRange('\u0b3e','\u0b43');
			break;
		}
		case '\u0b47':  case '\u0b48':
		{
			matchRange('\u0b47','\u0b48');
			break;
		}
		case '\u0b4b':  case '\u0b4c':  case '\u0b4d':
		{
			matchRange('\u0b4b','\u0b4d');
			break;
		}
		case '\u0b56':  case '\u0b57':
		{
			matchRange('\u0b56','\u0b57');
			break;
		}
		case '\u0b82':  case '\u0b83':
		{
			matchRange('\u0b82','\u0b83');
			break;
		}
		case '\u0bbe':  case '\u0bbf':  case '\u0bc0':  case '\u0bc1':
		case '\u0bc2':
		{
			matchRange('\u0bbe','\u0bc2');
			break;
		}
		case '\u0bc6':  case '\u0bc7':  case '\u0bc8':
		{
			matchRange('\u0bc6','\u0bc8');
			break;
		}
		case '\u0bca':  case '\u0bcb':  case '\u0bcc':  case '\u0bcd':
		{
			matchRange('\u0bca','\u0bcd');
			break;
		}
		case '\u0bd7':
		{
			match('\u0bd7');
			break;
		}
		case '\u0c01':  case '\u0c02':  case '\u0c03':
		{
			matchRange('\u0c01','\u0c03');
			break;
		}
		case '\u0c3e':  case '\u0c3f':  case '\u0c40':  case '\u0c41':
		case '\u0c42':  case '\u0c43':  case '\u0c44':
		{
			matchRange('\u0c3e','\u0c44');
			break;
		}
		case '\u0c46':  case '\u0c47':  case '\u0c48':
		{
			matchRange('\u0c46','\u0c48');
			break;
		}
		case '\u0c4a':  case '\u0c4b':  case '\u0c4c':  case '\u0c4d':
		{
			matchRange('\u0c4a','\u0c4d');
			break;
		}
		case '\u0c55':  case '\u0c56':
		{
			matchRange('\u0c55','\u0c56');
			break;
		}
		case '\u0c82':  case '\u0c83':
		{
			matchRange('\u0c82','\u0c83');
			break;
		}
		case '\u0cbe':  case '\u0cbf':  case '\u0cc0':  case '\u0cc1':
		case '\u0cc2':  case '\u0cc3':  case '\u0cc4':
		{
			matchRange('\u0cbe','\u0cc4');
			break;
		}
		case '\u0cc6':  case '\u0cc7':  case '\u0cc8':
		{
			matchRange('\u0cc6','\u0cc8');
			break;
		}
		case '\u0cca':  case '\u0ccb':  case '\u0ccc':  case '\u0ccd':
		{
			matchRange('\u0cca','\u0ccd');
			break;
		}
		case '\u0cd5':  case '\u0cd6':
		{
			matchRange('\u0cd5','\u0cd6');
			break;
		}
		case '\u0d02':  case '\u0d03':
		{
			matchRange('\u0d02','\u0d03');
			break;
		}
		case '\u0d3e':  case '\u0d3f':  case '\u0d40':  case '\u0d41':
		case '\u0d42':  case '\u0d43':
		{
			matchRange('\u0d3e','\u0d43');
			break;
		}
		case '\u0d46':  case '\u0d47':  case '\u0d48':
		{
			matchRange('\u0d46','\u0d48');
			break;
		}
		case '\u0d4a':  case '\u0d4b':  case '\u0d4c':  case '\u0d4d':
		{
			matchRange('\u0d4a','\u0d4d');
			break;
		}
		case '\u0d57':
		{
			match('\u0d57');
			break;
		}
		case '\u0e31':
		{
			match('\u0e31');
			break;
		}
		case '\u0e34':  case '\u0e35':  case '\u0e36':  case '\u0e37':
		case '\u0e38':  case '\u0e39':  case '\u0e3a':
		{
			matchRange('\u0e34','\u0e3a');
			break;
		}
		case '\u0e47':  case '\u0e48':  case '\u0e49':  case '\u0e4a':
		case '\u0e4b':  case '\u0e4c':  case '\u0e4d':  case '\u0e4e':
		{
			matchRange('\u0e47','\u0e4e');
			break;
		}
		case '\u0eb1':
		{
			match('\u0eb1');
			break;
		}
		case '\u0eb4':  case '\u0eb5':  case '\u0eb6':  case '\u0eb7':
		case '\u0eb8':  case '\u0eb9':
		{
			matchRange('\u0eb4','\u0eb9');
			break;
		}
		case '\u0ebb':  case '\u0ebc':
		{
			matchRange('\u0ebb','\u0ebc');
			break;
		}
		case '\u0ec8':  case '\u0ec9':  case '\u0eca':  case '\u0ecb':
		case '\u0ecc':  case '\u0ecd':
		{
			matchRange('\u0ec8','\u0ecd');
			break;
		}
		case '\u0f18':  case '\u0f19':
		{
			matchRange('\u0f18','\u0f19');
			break;
		}
		case '\u0f35':
		{
			match('\u0f35');
			break;
		}
		case '\u0f37':
		{
			match('\u0f37');
			break;
		}
		case '\u0f39':
		{
			match('\u0f39');
			break;
		}
		case '\u0f3e':
		{
			match('\u0f3e');
			break;
		}
		case '\u0f3f':
		{
			match('\u0f3f');
			break;
		}
		case '\u0f71':  case '\u0f72':  case '\u0f73':  case '\u0f74':
		case '\u0f75':  case '\u0f76':  case '\u0f77':  case '\u0f78':
		case '\u0f79':  case '\u0f7a':  case '\u0f7b':  case '\u0f7c':
		case '\u0f7d':  case '\u0f7e':  case '\u0f7f':  case '\u0f80':
		case '\u0f81':  case '\u0f82':  case '\u0f83':  case '\u0f84':
		{
			matchRange('\u0f71','\u0f84');
			break;
		}
		case '\u0f86':  case '\u0f87':  case '\u0f88':  case '\u0f89':
		case '\u0f8a':  case '\u0f8b':
		{
			matchRange('\u0f86','\u0f8b');
			break;
		}
		case '\u0f90':  case '\u0f91':  case '\u0f92':  case '\u0f93':
		case '\u0f94':  case '\u0f95':
		{
			matchRange('\u0f90','\u0f95');
			break;
		}
		case '\u0f97':
		{
			match('\u0f97');
			break;
		}
		case '\u0f99':  case '\u0f9a':  case '\u0f9b':  case '\u0f9c':
		case '\u0f9d':  case '\u0f9e':  case '\u0f9f':  case '\u0fa0':
		case '\u0fa1':  case '\u0fa2':  case '\u0fa3':  case '\u0fa4':
		case '\u0fa5':  case '\u0fa6':  case '\u0fa7':  case '\u0fa8':
		case '\u0fa9':  case '\u0faa':  case '\u0fab':  case '\u0fac':
		case '\u0fad':
		{
			matchRange('\u0f99','\u0fad');
			break;
		}
		case '\u0fb1':  case '\u0fb2':  case '\u0fb3':  case '\u0fb4':
		case '\u0fb5':  case '\u0fb6':  case '\u0fb7':
		{
			matchRange('\u0fb1','\u0fb7');
			break;
		}
		case '\u0fb9':
		{
			match('\u0fb9');
			break;
		}
		case '\u20d0':  case '\u20d1':  case '\u20d2':  case '\u20d3':
		case '\u20d4':  case '\u20d5':  case '\u20d6':  case '\u20d7':
		case '\u20d8':  case '\u20d9':  case '\u20da':  case '\u20db':
		case '\u20dc':
		{
			matchRange('\u20d0','\u20dc');
			break;
		}
		case '\u20e1':
		{
			match('\u20e1');
			break;
		}
		case '\u302a':  case '\u302b':  case '\u302c':  case '\u302d':
		case '\u302e':  case '\u302f':
		{
			matchRange('\u302a','\u302f');
			break;
		}
		case '\u3099':
		{
			match('\u3099');
			break;
		}
		case '\u309a':
		{
			match('\u309a');
			break;
		}
		default:
		{
			throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());
		}
		}
		}
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mEXTENDER(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = EXTENDER;
		int _saveIndex;
		
		{
		switch ( LA(1)) {
		case '\u00b7':
		{
			match('\u00b7');
			break;
		}
		case '\u02d0':
		{
			match('\u02d0');
			break;
		}
		case '\u02d1':
		{
			match('\u02d1');
			break;
		}
		case '\u0387':
		{
			match('\u0387');
			break;
		}
		case '\u0640':
		{
			match('\u0640');
			break;
		}
		case '\u0e46':
		{
			match('\u0e46');
			break;
		}
		case '\u0ec6':
		{
			match('\u0ec6');
			break;
		}
		case '\u3005':
		{
			match('\u3005');
			break;
		}
		case '\u3031':  case '\u3032':  case '\u3033':  case '\u3034':
		case '\u3035':
		{
			matchRange('\u3031','\u3035');
			break;
		}
		case '\u309d':  case '\u309e':
		{
			matchRange('\u309d','\u309e');
			break;
		}
		case '\u30fc':  case '\u30fd':  case '\u30fe':
		{
			matchRange('\u30fc','\u30fe');
			break;
		}
		default:
		{
			throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());
		}
		}
		}
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mNCNAME(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = NCNAME;
		int _saveIndex;
		
		mNMSTART(false);
		{
		_loop453:
		do {
			if ((_tokenSet_5.member(LA(1)))) {
				mNMCHAR(false);
			}
			else {
				break _loop453;
			}
			
		} while (true);
		}
		_ttype = testLiteralsTable(new String(text.getBuffer(),_begin,text.length()-_begin),_ttype);
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mWS(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = WS;
		int _saveIndex;
		
		{
		int _cnt456=0;
		_loop456:
		do {
			if ((LA(1)==' ') && (true) && (true) && (true)) {
				match(' ');
			}
			else if ((LA(1)=='\t') && (true) && (true) && (true)) {
				match('\t');
			}
			else if ((LA(1)=='\n') && (true) && (true) && (true)) {
				match('\n');
				if ( inputState.guessing==0 ) {
					newline();
				}
			}
			else if ((LA(1)=='\r') && (true) && (true) && (true)) {
				match('\r');
			}
			else {
				if ( _cnt456>=1 ) { break _loop456; } else {throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());}
			}
			
			_cnt456++;
		} while (true);
		}
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mEXPR_COMMENT(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = EXPR_COMMENT;
		int _saveIndex;
		
		match("(:");
		{
		_loop460:
		do {
			// nongreedy exit test
			if ((LA(1)==':') && (LA(2)==')') && (true) && (true)) break _loop460;
			if (((LA(1) >= '\u0003' && LA(1) <= '\ufffe')) && ((LA(2) >= '\u0003' && LA(2) <= '\ufffe')) && ((LA(3) >= '\u0003' && LA(3) <= '\ufffe')) && (true)) {
				{
				if ((LA(1)=='(') && (LA(2)==':') && ((LA(3) >= '\u0003' && LA(3) <= '\ufffe')) && ((LA(4) >= '\u0003' && LA(4) <= '\ufffe'))) {
					mEXPR_COMMENT(false);
				}
				else if (((LA(1) >= '\u0003' && LA(1) <= '\ufffe')) && ((LA(2) >= '\u0003' && LA(2) <= '\ufffe')) && ((LA(3) >= '\u0003' && LA(3) <= '\ufffe')) && (true)) {
					matchNot(EOF_CHAR);
				}
				else {
					throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());
				}
				
				}
			}
			else {
				break _loop460;
			}
			
		} while (true);
		}
		match(":)");
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mINTEGER_LITERAL(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = INTEGER_LITERAL;
		int _saveIndex;
		
		if (!( !(inElementContent || inAttributeContent) ))
		  throw new SemanticException(" !(inElementContent || inAttributeContent) ");
		mDIGITS(false);
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mDOUBLE_LITERAL(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = DOUBLE_LITERAL;
		int _saveIndex;
		
		if (!( !(inElementContent || inAttributeContent) ))
		  throw new SemanticException(" !(inElementContent || inAttributeContent) ");
		{
		if ((LA(1)=='.')) {
			{
			match('.');
			mDIGITS(false);
			}
		}
		else if ((_tokenSet_2.member(LA(1)))) {
			{
			mDIGITS(false);
			{
			switch ( LA(1)) {
			case '.':
			{
				match('.');
				{
				_loop468:
				do {
					if ((_tokenSet_2.member(LA(1)))) {
						mDIGIT(false);
					}
					else {
						break _loop468;
					}
					
				} while (true);
				}
				break;
			}
			case 'E':  case 'e':
			{
				break;
			}
			default:
			{
				throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());
			}
			}
			}
			}
		}
		else {
			throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());
		}
		
		}
		{
		switch ( LA(1)) {
		case 'e':
		{
			match('e');
			break;
		}
		case 'E':
		{
			match('E');
			break;
		}
		default:
		{
			throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());
		}
		}
		}
		{
		switch ( LA(1)) {
		case '+':
		{
			match('+');
			break;
		}
		case '-':
		{
			match('-');
			break;
		}
		default:
			if ((_tokenSet_2.member(LA(1)))) {
			}
		else {
			throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());
		}
		}
		}
		mDIGITS(false);
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mDECIMAL_LITERAL(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = DECIMAL_LITERAL;
		int _saveIndex;
		
		if (((LA(1)=='.'))&&( !(inElementContent || inAttributeContent) )) {
			{
			match('.');
			mDIGITS(false);
			}
		}
		else if ((_tokenSet_2.member(LA(1)))) {
			{
			mDIGITS(false);
			{
			if ((LA(1)=='.')) {
				match('.');
				{
				_loop476:
				do {
					if ((_tokenSet_2.member(LA(1)))) {
						mDIGIT(false);
					}
					else {
						break _loop476;
					}
					
				} while (true);
				}
			}
			else {
			}
			
			}
			}
		}
		else {
			throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());
		}
		
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mPREDEFINED_ENTITY_REF(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = PREDEFINED_ENTITY_REF;
		int _saveIndex;
		
		match('&');
		{
		switch ( LA(1)) {
		case 'l':
		{
			match("lt");
			break;
		}
		case 'g':
		{
			match("gt");
			break;
		}
		case 'q':
		{
			match("quot");
			break;
		}
		default:
			if ((LA(1)=='a') && (LA(2)=='m')) {
				match("amp");
			}
			else if ((LA(1)=='a') && (LA(2)=='p')) {
				match("apos");
			}
		else {
			throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());
		}
		}
		}
		match(';');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mCHAR_REF(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = CHAR_REF;
		int _saveIndex;
		
		match('&');
		match('#');
		{
		if ((_tokenSet_2.member(LA(1)))) {
			mDIGITS(false);
		}
		else if ((LA(1)=='x')) {
			{
			match('x');
			mHEX_DIGITS(false);
			}
		}
		else {
			throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());
		}
		
		}
		match(';');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mSTRING_LITERAL(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = STRING_LITERAL;
		int _saveIndex;
		
		switch ( LA(1)) {
		case '"':
		{
			_saveIndex=text.length();
			match('"');
			text.setLength(_saveIndex);
			{
			_loop486:
			do {
				if ((LA(1)=='&') && (LA(2)=='a'||LA(2)=='g'||LA(2)=='l'||LA(2)=='q')) {
					mPREDEFINED_ENTITY_REF(false);
				}
				else if ((LA(1)=='&') && (LA(2)=='#')) {
					mCHAR_REF(false);
				}
				else if ((LA(1)=='"') && (LA(2)=='"')) {
					{
					_saveIndex=text.length();
					match('"');
					text.setLength(_saveIndex);
					match('"');
					}
				}
				else if ((_tokenSet_6.member(LA(1)))) {
					{
					match(_tokenSet_6);
					}
				}
				else {
					break _loop486;
				}
				
			} while (true);
			}
			_saveIndex=text.length();
			match('"');
			text.setLength(_saveIndex);
			break;
		}
		case '\'':
		{
			_saveIndex=text.length();
			match('\'');
			text.setLength(_saveIndex);
			{
			_loop490:
			do {
				if ((LA(1)=='&') && (LA(2)=='a'||LA(2)=='g'||LA(2)=='l'||LA(2)=='q')) {
					mPREDEFINED_ENTITY_REF(false);
				}
				else if ((LA(1)=='&') && (LA(2)=='#')) {
					mCHAR_REF(false);
				}
				else if ((LA(1)=='\'') && (LA(2)=='\'')) {
					{
					_saveIndex=text.length();
					match('\'');
					text.setLength(_saveIndex);
					match('\'');
					}
				}
				else if ((_tokenSet_7.member(LA(1)))) {
					{
					match(_tokenSet_7);
					}
				}
				else {
					break _loop490;
				}
				
			} while (true);
			}
			_saveIndex=text.length();
			match('\'');
			text.setLength(_saveIndex);
			break;
		}
		default:
		{
			throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());
		}
		}
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mQUOT_ATTRIBUTE_CONTENT(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = QUOT_ATTRIBUTE_CONTENT;
		int _saveIndex;
		
		{
		int _cnt494=0;
		_loop494:
		do {
			if ((_tokenSet_8.member(LA(1)))) {
				{
				match(_tokenSet_8);
				}
			}
			else {
				if ( _cnt494>=1 ) { break _loop494; } else {throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());}
			}
			
			_cnt494++;
		} while (true);
		}
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
/**
 * The following definition differs from the spec by allowing the
 * '&' character, which is handled by the constructor classes.
 *
 * TODO: Allow escaped quotes in attribute content. Doesn't work.
 */
	protected final void mAPOS_ATTRIBUTE_CONTENT(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = APOS_ATTRIBUTE_CONTENT;
		int _saveIndex;
		
		{
		int _cnt498=0;
		_loop498:
		do {
			if ((_tokenSet_9.member(LA(1)))) {
				{
				match(_tokenSet_9);
				}
			}
			else {
				if ( _cnt498>=1 ) { break _loop498; } else {throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());}
			}
			
			_cnt498++;
		} while (true);
		}
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mESCAPE_APOS(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = ESCAPE_APOS;
		int _saveIndex;
		
		match('\'');
		match('\'');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mESCAPE_QUOT(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = ESCAPE_QUOT;
		int _saveIndex;
		
		match('"');
		match('"');
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
/**
 * The following definition differs from the spec by allowing the
 * '&' character, which is handled by the constructor classes.
 *
 * TODO: Allow escaped quotes in attribute content. Doesn't work.
 */
	protected final void mELEMENT_CONTENT(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = ELEMENT_CONTENT;
		int _saveIndex;
		
		{
		int _cnt503=0;
		_loop503:
		do {
			switch ( LA(1)) {
			case '\t':
			{
				match('\t');
				break;
			}
			case '\r':
			{
				match('\r');
				break;
			}
			case '\n':
			{
				match('\n');
				if ( inputState.guessing==0 ) {
					newline();
				}
				break;
			}
			case ' ':  case '!':  case '"':  case '#':
			case '$':  case '%':  case '&':  case '\'':
			case '(':  case ')':  case '*':  case '+':
			case ',':  case '-':  case '.':  case '/':
			case '0':  case '1':  case '2':  case '3':
			case '4':  case '5':  case '6':  case '7':
			case '8':  case '9':  case ':':  case ';':
			{
				matchRange('\u0020','\u003b');
				break;
			}
			case '=':  case '>':  case '?':  case '@':
			case 'A':  case 'B':  case 'C':  case 'D':
			case 'E':  case 'F':  case 'G':  case 'H':
			case 'I':  case 'J':  case 'K':  case 'L':
			case 'M':  case 'N':  case 'O':  case 'P':
			case 'Q':  case 'R':  case 'S':  case 'T':
			case 'U':  case 'V':  case 'W':  case 'X':
			case 'Y':  case 'Z':  case '[':  case '\\':
			case ']':  case '^':  case '_':  case '`':
			case 'a':  case 'b':  case 'c':  case 'd':
			case 'e':  case 'f':  case 'g':  case 'h':
			case 'i':  case 'j':  case 'k':  case 'l':
			case 'm':  case 'n':  case 'o':  case 'p':
			case 'q':  case 'r':  case 's':  case 't':
			case 'u':  case 'v':  case 'w':  case 'x':
			case 'y':  case 'z':
			{
				matchRange('\u003d','\u007a');
				break;
			}
			case '|':
			{
				match('\u007c');
				break;
			}
			default:
				if (((LA(1) >= '~' && LA(1) <= '\ufffd'))) {
					matchRange('\u007e','\uFFFD');
				}
			else {
				if ( _cnt503>=1 ) { break _loop503; } else {throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());}
			}
			}
			_cnt503++;
		} while (true);
		}
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mXML_COMMENT(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = XML_COMMENT;
		int _saveIndex;
		
		_saveIndex=text.length();
		match("<!--");
		text.setLength(_saveIndex);
		{
		_loop510:
		do {
			if ((_tokenSet_10.member(LA(1)))) {
				{
				match(_tokenSet_10);
				}
			}
			else {
				boolean synPredMatched509 = false;
				if (((LA(1)=='-'))) {
					int _m509 = mark();
					synPredMatched509 = true;
					inputState.guessing++;
					try {
						{
						match('-');
						{
						match(_tokenSet_10);
						}
						}
					}
					catch (RecognitionException pe) {
						synPredMatched509 = false;
					}
					rewind(_m509);
inputState.guessing--;
				}
				if ( synPredMatched509 ) {
					match('-');
				}
				else {
					break _loop510;
				}
				}
			} while (true);
			}
			if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
				_token = makeToken(_ttype);
				_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
			}
			_returnToken = _token;
		}
		
	protected final void mXML_PI(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = XML_PI;
		int _saveIndex;
		
		_saveIndex=text.length();
		mXML_PI_START(false);
		text.setLength(_saveIndex);
		mNCNAME(false);
		{
		if ((LA(1)==' ')) {
			match(' ');
			{
			_loop518:
			do {
				if ((_tokenSet_11.member(LA(1)))) {
					{
					match(_tokenSet_11);
					}
				}
				else {
					boolean synPredMatched517 = false;
					if (((LA(1)=='?'))) {
						int _m517 = mark();
						synPredMatched517 = true;
						inputState.guessing++;
						try {
							{
							match('?');
							{
							match(_tokenSet_12);
							}
							}
						}
						catch (RecognitionException pe) {
							synPredMatched517 = false;
						}
						rewind(_m517);
inputState.guessing--;
					}
					if ( synPredMatched517 ) {
						match('?');
					}
					else {
						break _loop518;
					}
					}
				} while (true);
				}
			}
			else {
			}
			
			}
			if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
				_token = makeToken(_ttype);
				_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
			}
			_returnToken = _token;
		}
		
	protected final void mXML_CDATA(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = XML_CDATA;
		int _saveIndex;
		
		_saveIndex=text.length();
		mXML_CDATA_START(false);
		text.setLength(_saveIndex);
		{
		_loop529:
		do {
			boolean synPredMatched524 = false;
			if (((LA(1)==']') && ((LA(2) >= '\u0003' && LA(2) <= '\ufffe')) && ((LA(3) >= '\u0003' && LA(3) <= '\ufffe')) && ((LA(4) >= '\u0003' && LA(4) <= '\ufffe')))) {
				int _m524 = mark();
				synPredMatched524 = true;
				inputState.guessing++;
				try {
					{
					match(']');
					{
					match(_tokenSet_13);
					}
					}
				}
				catch (RecognitionException pe) {
					synPredMatched524 = false;
				}
				rewind(_m524);
inputState.guessing--;
			}
			if ( synPredMatched524 ) {
				match(']');
			}
			else {
				boolean synPredMatched527 = false;
				if (((LA(1)==']') && (LA(2)==']') && ((LA(3) >= '\u0003' && LA(3) <= '\ufffe')) && ((LA(4) >= '\u0003' && LA(4) <= '\ufffe')))) {
					int _m527 = mark();
					synPredMatched527 = true;
					inputState.guessing++;
					try {
						{
						match(']');
						match(']');
						{
						match(_tokenSet_12);
						}
						}
					}
					catch (RecognitionException pe) {
						synPredMatched527 = false;
					}
					rewind(_m527);
inputState.guessing--;
				}
				if ( synPredMatched527 ) {
					{
					match(']');
					match(']');
					}
				}
				else if ((_tokenSet_13.member(LA(1)))) {
					{
					match(_tokenSet_13);
					}
				}
				else {
					break _loop529;
				}
				}
			} while (true);
			}
			_saveIndex=text.length();
			mXML_CDATA_END(false);
			text.setLength(_saveIndex);
			if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
				_token = makeToken(_ttype);
				_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
			}
			_returnToken = _token;
		}
		
	protected final void mS(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = S;
		int _saveIndex;
		
		{
		int _cnt533=0;
		_loop533:
		do {
			if ((LA(1)=='\t'||LA(1)=='\n'||LA(1)=='\r'||LA(1)==' ')) {
				{
				switch ( LA(1)) {
				case ' ':
				{
					match(' ');
					break;
				}
				case '\n':
				{
					match('\n');
					break;
				}
				case '\r':
				{
					match('\r');
					break;
				}
				case '\t':
				{
					match('\t');
					break;
				}
				default:
				{
					throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());
				}
				}
				}
			}
			else {
				if ( _cnt533>=1 ) { break _loop533; } else {throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());}
			}
			
			_cnt533++;
		} while (true);
		}
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mPRAGMA_START(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = PRAGMA_START;
		int _saveIndex;
		
		match("(#");
		{
		if ((LA(1)=='\t'||LA(1)=='\n'||LA(1)=='\r'||LA(1)==' ')) {
			mWS(false);
		}
		else {
		}
		
		}
		if ( inputState.guessing==0 ) {
			inPragma = true;
		}
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	protected final void mPRAGMA_END(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = PRAGMA_END;
		int _saveIndex;
		
		{
		switch ( LA(1)) {
		case '\t':  case '\n':  case '\r':  case ' ':
		{
			_saveIndex=text.length();
			mWS(false);
			text.setLength(_saveIndex);
			{
			_loop539:
			do {
				// nongreedy exit test
				if ((LA(1)=='#') && (LA(2)==')') && (true)) break _loop539;
				if (((LA(1) >= '\u0003' && LA(1) <= '\ufffe')) && ((LA(2) >= '\u0003' && LA(2) <= '\ufffe')) && ((LA(3) >= '\u0003' && LA(3) <= '\ufffe'))) {
					matchNot(EOF_CHAR);
				}
				else {
					break _loop539;
				}
				
			} while (true);
			}
			break;
		}
		case '#':
		{
			break;
		}
		default:
		{
			throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());
		}
		}
		}
		_saveIndex=text.length();
		match("#)");
		text.setLength(_saveIndex);
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
/**
 * Main method that decides which token to return next.
 * We need this as many things depend on the current
 * context.
 */
	public final void mNEXT_TOKEN(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = NEXT_TOKEN;
		int _saveIndex;
		Token ncname=null;
		
		switch ( LA(1)) {
		case '{':
		{
			mLCURLY(false);
			if ( inputState.guessing==0 ) {
				
						inElementContent= false;
						inAttributeContent= false;
						_ttype = LCURLY;
					
			}
			break;
		}
		case '}':
		{
			mRCURLY(false);
			if ( inputState.guessing==0 ) {
				_ttype = RCURLY;
			}
			break;
		}
		default:
			if ((LA(1)=='(') && (LA(2)==':') && ((LA(3) >= '\u0003' && LA(3) <= '\ufffe')) && ((LA(4) >= '\u0003' && LA(4) <= '\ufffe'))) {
				mEXPR_COMMENT(false);
				if ( inputState.guessing==0 ) {
					
							String comment = new String(text.getBuffer(),_begin,text.length()-_begin);
							for (int i = 0; i < comment.length(); i++) {
								if (comment.charAt(i) == '\n')
									newline();
							}
							_ttype = Token.SKIP;
						
				}
			}
			else if ((LA(1)=='<') && (LA(2)=='!') && (LA(3)=='-')) {
				mXML_COMMENT(false);
				if ( inputState.guessing==0 ) {
					
							String data = new String(text.getBuffer(),_begin,text.length()-_begin);
							parseLinefeeds(data);
							_ttype = XML_COMMENT; 
						
				}
			}
			else if ((LA(1)=='<') && (LA(2)=='!') && (LA(3)=='[')) {
				mXML_CDATA(false);
				if ( inputState.guessing==0 ) {
					_ttype = XML_CDATA;
				}
			}
			else {
				boolean synPredMatched547 = false;
				if (((_tokenSet_14.member(LA(1))) && (_tokenSet_15.member(LA(2))) && (_tokenSet_16.member(LA(3))) && (true))) {
					int _m547 = mark();
					synPredMatched547 = true;
					inputState.guessing++;
					try {
						{
						match('.');
						mINTEGER_LITERAL(false);
						{
						switch ( LA(1)) {
						case 'e':
						{
							match('e');
							break;
						}
						case 'E':
						{
							match('E');
							break;
						}
						default:
						{
							throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());
						}
						}
						}
						}
					}
					catch (RecognitionException pe) {
						synPredMatched547 = false;
					}
					rewind(_m547);
inputState.guessing--;
				}
				if ( synPredMatched547 ) {
					mDOUBLE_LITERAL(false);
					if ( inputState.guessing==0 ) {
						_ttype = DOUBLE_LITERAL;
					}
				}
				else {
					boolean synPredMatched556 = false;
					if (((_tokenSet_14.member(LA(1))) && (_tokenSet_15.member(LA(2))) && (_tokenSet_16.member(LA(3))) && (true))) {
						int _m556 = mark();
						synPredMatched556 = true;
						inputState.guessing++;
						try {
							{
							mINTEGER_LITERAL(false);
							{
							switch ( LA(1)) {
							case '.':
							{
								match('.');
								{
								if ((_tokenSet_2.member(LA(1)))) {
									mINTEGER_LITERAL(false);
								}
								else if ((LA(1)=='E'||LA(1)=='e')) {
								}
								else {
									throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());
								}
								
								}
								break;
							}
							case 'E':  case 'e':
							{
								break;
							}
							default:
							{
								throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());
							}
							}
							}
							{
							switch ( LA(1)) {
							case 'e':
							{
								match('e');
								break;
							}
							case 'E':
							{
								match('E');
								break;
							}
							default:
							{
								throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());
							}
							}
							}
							}
						}
						catch (RecognitionException pe) {
							synPredMatched556 = false;
						}
						rewind(_m556);
inputState.guessing--;
					}
					if ( synPredMatched556 ) {
						mDOUBLE_LITERAL(false);
						if ( inputState.guessing==0 ) {
							_ttype = DOUBLE_LITERAL;
						}
					}
					else if ((LA(1)=='-') && (LA(2)=='-') && (LA(3)=='>') && (true)) {
						mXML_COMMENT_END(false);
						if ( inputState.guessing==0 ) {
							_ttype = XML_COMMENT_END;
						}
					}
					else if ((LA(1)==']') && (LA(2)==']') && (LA(3)=='>') && (true)) {
						mXML_CDATA_END(false);
						if ( inputState.guessing==0 ) {
							_ttype = XML_CDATA_END;
						}
					}
					else {
						boolean synPredMatched542 = false;
						if (((LA(1)=='<') && (LA(2)=='?'))) {
							int _m542 = mark();
							synPredMatched542 = true;
							inputState.guessing++;
							try {
								{
								mXML_PI_START(false);
								}
							}
							catch (RecognitionException pe) {
								synPredMatched542 = false;
							}
							rewind(_m542);
inputState.guessing--;
						}
						if ( synPredMatched542 ) {
							mXML_PI(false);
							if ( inputState.guessing==0 ) {
								_ttype = XML_PI;
							}
						}
						else if ((LA(1)=='<') && (LA(2)=='/')) {
							mEND_TAG_START(false);
							if ( inputState.guessing==0 ) {
								
										inElementContent= false;
										wsExplicit= false;
										_ttype = END_TAG_START;
									
							}
						}
						else if ((LA(1)=='<') && (LA(2)=='<')) {
							mBEFORE(false);
							if ( inputState.guessing==0 ) {
								_ttype = BEFORE;
							}
						}
						else if ((LA(1)=='<') && (LA(2)=='=')) {
							mLTEQ(false);
							if ( inputState.guessing==0 ) {
								_ttype = LTEQ;
							}
						}
						else if (((LA(1)=='\'') && (LA(2)=='\'') && (true) && (true))&&( inAttributeContent && attrDelimChar == '\'' )) {
							mESCAPE_APOS(false);
							if ( inputState.guessing==0 ) {
								_ttype = ESCAPE_APOS;
							}
						}
						else if (((LA(1)=='"') && (LA(2)=='"') && (true) && (true))&&( inAttributeContent && attrDelimChar == '"' )) {
							mESCAPE_QUOT(false);
							if ( inputState.guessing==0 ) {
								_ttype = ESCAPE_QUOT;
							}
						}
						else if (((LA(1)=='"'||LA(1)=='\'') && ((LA(2) >= '\u0003' && LA(2) <= '\ufffe')) && (true) && (true))&&( parseStringLiterals && !inElementContent )) {
							mSTRING_LITERAL(false);
							if ( inputState.guessing==0 ) {
								_ttype = STRING_LITERAL;
							}
						}
						else {
							boolean synPredMatched544 = false;
							if ((((LA(1)=='.') && (LA(2)=='.') && (true) && (true))&&( !(inAttributeContent || inElementContent) ))) {
								int _m544 = mark();
								synPredMatched544 = true;
								inputState.guessing++;
								try {
									{
									match('.');
									match('.');
									}
								}
								catch (RecognitionException pe) {
									synPredMatched544 = false;
								}
								rewind(_m544);
inputState.guessing--;
							}
							if ( synPredMatched544 ) {
								mPARENT(false);
								if ( inputState.guessing==0 ) {
									_ttype = PARENT;
								}
							}
							else if (((LA(1)=='/') && (LA(2)=='/') && (true) && (true))&&( !(inAttributeContent || inElementContent) )) {
								mDSLASH(false);
								if ( inputState.guessing==0 ) {
									_ttype = DSLASH;
								}
							}
							else if (((LA(1)=='|') && (LA(2)=='=') && (true) && (true))&&( !(inAttributeContent || inElementContent) )) {
								mOREQ(false);
								if ( inputState.guessing==0 ) {
									_ttype = OREQ;
								}
							}
							else if (((LA(1)=='&') && (LA(2)=='=') && (true) && (true))&&( !(inAttributeContent || inElementContent) )) {
								mANDEQ(false);
								if ( inputState.guessing==0 ) {
									_ttype = ANDEQ;
								}
							}
							else if (((LA(1)=='!') && (LA(2)=='=') && (true) && (true))&&( !(inAttributeContent || inElementContent) )) {
								mNEQ(false);
								if ( inputState.guessing==0 ) {
									_ttype = NEQ;
								}
							}
							else if ((LA(1)=='>') && (LA(2)=='>') && (true) && (true)) {
								mAFTER(false);
								if ( inputState.guessing==0 ) {
									_ttype = AFTER;
								}
							}
							else if (((LA(1)=='>') && (LA(2)=='=') && (true) && (true))&&( !(inAttributeContent || inElementContent) )) {
								mGTEQ(false);
								if ( inputState.guessing==0 ) {
									_ttype = GTEQ;
								}
							}
							else if ((LA(1)=='?') && (LA(2)=='>') && (true) && (true)) {
								mXML_PI_END(false);
								if ( inputState.guessing==0 ) {
									_ttype = XML_PI_END;
								}
							}
							else if ((LA(1)=='(') && (LA(2)=='#') && (true) && (true)) {
								mPRAGMA_START(false);
								if ( inputState.guessing==0 ) {
									
											_ttype = PRAGMA_START;
										
								}
							}
							else if (((LA(1)=='\t'||LA(1)=='\n'||LA(1)=='\r'||LA(1)==' '||LA(1)=='#') && ((LA(2) >= '\u0003' && LA(2) <= '\ufffe')) && (true) && (true))&&( inPragma )) {
								mPRAGMA_END(false);
								if ( inputState.guessing==0 ) {
									
											inPragma = false; 
									//wsExplicit = true;
											_ttype = PRAGMA_END; 
										
								}
							}
							else if ((LA(1)=='<') && (true)) {
								mLT(false);
								if ( inputState.guessing==0 ) {
									
											inElementContent= false;
											_ttype = LT;
										
								}
							}
							else if (((_tokenSet_8.member(LA(1))) && (true) && (true) && (true))&&( inAttributeContent && attrDelimChar == '"' )) {
								mQUOT_ATTRIBUTE_CONTENT(false);
								if ( inputState.guessing==0 ) {
									_ttype = QUOT_ATTRIBUTE_CONTENT;
								}
							}
							else if (((_tokenSet_9.member(LA(1))) && (true) && (true) && (true))&&( inAttributeContent && attrDelimChar == '\'' )) {
								mAPOS_ATTRIBUTE_CONTENT(false);
								if ( inputState.guessing==0 ) {
									_ttype = APOS_ATTRIBUTE_CONTENT;
								}
							}
							else if (((LA(1)=='"') && (true) && (true) && (true))&&( !(parseStringLiterals || inElementContent) )) {
								mQUOT(false);
								if ( inputState.guessing==0 ) {
									_ttype = QUOT;
								}
							}
							else if (((LA(1)=='\'') && (true) && (true) && (true))&&( !(parseStringLiterals || inElementContent) )) {
								mAPOS(false);
								if ( inputState.guessing==0 ) {
									_ttype = APOS;
								}
							}
							else if (((_tokenSet_17.member(LA(1))) && (true) && (true) && (true))&&( inElementContent )) {
								mELEMENT_CONTENT(false);
								if ( inputState.guessing==0 ) {
									_ttype = ELEMENT_CONTENT;
								}
							}
							else if (((LA(1)=='\t'||LA(1)=='\n'||LA(1)=='\r'||LA(1)==' ') && (true) && (true) && (true))&&( !inPragma )) {
								mWS(false);
								if ( inputState.guessing==0 ) {
									
											if (wsExplicit) {
												_ttype = WS;
												text.setLength(_begin); text.append("WS");
											} else
												_ttype = Token.SKIP;
										
								}
							}
							else if ((_tokenSet_18.member(LA(1))) && (true) && (true) && (true)) {
								mNCNAME(true);
								ncname=_returnToken;
								if ( inputState.guessing==0 ) {
									_ttype = ncname.getType();
								}
							}
							else {
								boolean synPredMatched549 = false;
								if (((_tokenSet_14.member(LA(1))) && (true) && (true) && (true))) {
									int _m549 = mark();
									synPredMatched549 = true;
									inputState.guessing++;
									try {
										{
										match('.');
										mINTEGER_LITERAL(false);
										}
									}
									catch (RecognitionException pe) {
										synPredMatched549 = false;
									}
									rewind(_m549);
inputState.guessing--;
								}
								if ( synPredMatched549 ) {
									mDECIMAL_LITERAL(false);
									if ( inputState.guessing==0 ) {
										_ttype = DECIMAL_LITERAL;
									}
								}
								else {
									boolean synPredMatched551 = false;
									if (((LA(1)=='.') && (true) && (true) && (true))) {
										int _m551 = mark();
										synPredMatched551 = true;
										inputState.guessing++;
										try {
											{
											match('.');
											}
										}
										catch (RecognitionException pe) {
											synPredMatched551 = false;
										}
										rewind(_m551);
inputState.guessing--;
									}
									if ( synPredMatched551 ) {
										mSELF(false);
										if ( inputState.guessing==0 ) {
											_ttype = SELF;
										}
									}
									else {
										boolean synPredMatched558 = false;
										if (((_tokenSet_14.member(LA(1))) && (true) && (true) && (true))) {
											int _m558 = mark();
											synPredMatched558 = true;
											inputState.guessing++;
											try {
												{
												mINTEGER_LITERAL(false);
												match('.');
												}
											}
											catch (RecognitionException pe) {
												synPredMatched558 = false;
											}
											rewind(_m558);
inputState.guessing--;
										}
										if ( synPredMatched558 ) {
											mDECIMAL_LITERAL(false);
											if ( inputState.guessing==0 ) {
												_ttype = DECIMAL_LITERAL;
											}
										}
										else if ((_tokenSet_2.member(LA(1))) && (true) && (true) && (true)) {
											mINTEGER_LITERAL(false);
											if ( inputState.guessing==0 ) {
												_ttype = INTEGER_LITERAL;
											}
										}
										else if ((LA(1)=='/') && (true) && (true) && (true)) {
											mSLASH(false);
											if ( inputState.guessing==0 ) {
												_ttype = SLASH;
											}
										}
										else if ((LA(1)==':') && (true) && (true) && (true)) {
											mCOLON(false);
											if ( inputState.guessing==0 ) {
												_ttype = COLON;
											}
										}
										else if ((LA(1)==',') && (true) && (true) && (true)) {
											mCOMMA(false);
											if ( inputState.guessing==0 ) {
												_ttype = COMMA;
											}
										}
										else if ((LA(1)==';') && (true) && (true) && (true)) {
											mSEMICOLON(false);
											if ( inputState.guessing==0 ) {
												_ttype = SEMICOLON;
											}
										}
										else if ((LA(1)=='*') && (true) && (true) && (true)) {
											mSTAR(false);
											if ( inputState.guessing==0 ) {
												_ttype = STAR;
											}
										}
										else if ((LA(1)=='?') && (true) && (true) && (true)) {
											mQUESTION(false);
											if ( inputState.guessing==0 ) {
												_ttype = QUESTION;
											}
										}
										else if ((LA(1)=='+') && (true) && (true) && (true)) {
											mPLUS(false);
											if ( inputState.guessing==0 ) {
												_ttype = PLUS;
											}
										}
										else if ((LA(1)=='-') && (true) && (true) && (true)) {
											mMINUS(false);
											if ( inputState.guessing==0 ) {
												_ttype = MINUS;
											}
										}
										else if ((LA(1)=='[') && (true) && (true) && (true)) {
											mLPPAREN(false);
											if ( inputState.guessing==0 ) {
												_ttype = LPPAREN;
											}
										}
										else if ((LA(1)==']') && (true) && (true) && (true)) {
											mRPPAREN(false);
											if ( inputState.guessing==0 ) {
												_ttype = RPPAREN;
											}
										}
										else if ((LA(1)=='(') && (true) && (true) && (true)) {
											mLPAREN(false);
											if ( inputState.guessing==0 ) {
												_ttype = LPAREN;
											}
										}
										else if ((LA(1)==')') && (true) && (true) && (true)) {
											mRPAREN(false);
											if ( inputState.guessing==0 ) {
												_ttype = RPAREN;
											}
										}
										else if ((LA(1)=='|') && (true) && (true) && (true)) {
											mUNION(false);
											if ( inputState.guessing==0 ) {
												_ttype = UNION;
											}
										}
										else if ((LA(1)=='@') && (true) && (true) && (true)) {
											mAT(false);
											if ( inputState.guessing==0 ) {
												_ttype = AT;
											}
										}
										else if ((LA(1)=='$') && (true) && (true) && (true)) {
											mDOLLAR(false);
											if ( inputState.guessing==0 ) {
												_ttype = DOLLAR;
											}
										}
										else if ((LA(1)=='=') && (true) && (true) && (true)) {
											mEQ(false);
											if ( inputState.guessing==0 ) {
												_ttype = EQ;
											}
										}
										else if ((LA(1)=='>') && (true) && (true) && (true)) {
											mGT(false);
											if ( inputState.guessing==0 ) {
												_ttype = GT;
											}
										}
									else {
										throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());
									}
									}}}}}}}}
									if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
										_token = makeToken(_ttype);
										_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
									}
									_returnToken = _token;
								}
								
	protected final void mCHAR(boolean _createToken) throws RecognitionException, CharStreamException, TokenStreamException {
		int _ttype; Token _token=null; int _begin=text.length();
		_ttype = CHAR;
		int _saveIndex;
		
		{
		switch ( LA(1)) {
		case '\t':
		{
			match('\t');
			break;
		}
		case '\n':
		{
			match('\n');
			if ( inputState.guessing==0 ) {
				newline();
			}
			break;
		}
		case '\r':
		{
			match('\r');
			break;
		}
		case ' ':  case '!':  case '"':  case '#':
		case '$':  case '%':  case '&':  case '\'':
		case '(':  case ')':  case '*':  case '+':
		case ',':  case '-':  case '.':  case '/':
		case '0':  case '1':  case '2':  case '3':
		case '4':  case '5':  case '6':  case '7':
		case '8':  case '9':
		{
			matchRange('\u0020','\u0039');
			break;
		}
		default:
			if (((LA(1) >= ';' && LA(1) <= '\ud7ff'))) {
				matchRange('\u003B','\uD7FF');
			}
			else if (((LA(1) >= '\ue000' && LA(1) <= '\ufffd'))) {
				matchRange('\uE000','\uFFFD');
			}
		else {
			throw new NoViableAltForCharException((char)LA(1), getFilename(), getLine(), getColumn());
		}
		}
		}
		if ( _createToken && _token==null && _ttype!=Token.SKIP ) {
			_token = makeToken(_ttype);
			_token.setText(new String(text.getBuffer(), _begin, text.length()-_begin));
		}
		_returnToken = _token;
	}
	
	
	private static final long[] mk_tokenSet_0() {
		long[] data = new long[2756];
		data[1]=576460743847706622L;
		data[3]=-36028797027352577L;
		data[4]=9219994337134247935L;
		data[5]=9223372036854775294L;
		data[6]=-1L;
		data[7]=-274156627316187121L;
		data[8]=16777215L;
		data[9]=-65536L;
		data[10]=-576458553280167937L;
		data[11]=3L;
		data[14]=-17179879616L;
		data[15]=4503588160110591L;
		data[16]=-8194L;
		data[17]=-536936449L;
		data[18]=-65533L;
		data[19]=234134404065073567L;
		data[20]=-562949953421312L;
		data[21]=-8547991553L;
		data[22]=127L;
		data[23]=1979120929931264L;
		data[24]=576460743713488896L;
		data[25]=-562949953419266L;
		data[26]=9007199254740991999L;
		data[27]=412319973375L;
		data[36]=2594073385365405664L;
		data[37]=17163091968L;
		data[38]=271902628478820320L;
		data[39]=844440767823872L;
		data[40]=247132830528276448L;
		data[41]=7881300924956672L;
		data[42]=2589004636761075680L;
		data[43]=4294967296L;
		data[44]=2579997437506199520L;
		data[45]=15837691904L;
		data[46]=270153412153034720L;
		data[48]=283724577500946400L;
		data[49]=12884901888L;
		data[50]=283724577500946400L;
		data[51]=13958643712L;
		data[52]=288228177128316896L;
		data[53]=12884901888L;
		data[56]=3799912185593854L;
		data[57]=63L;
		data[58]=2309621682768192918L;
		data[59]=31L;
		data[61]=4398046510847L;
		data[66]=-4294967296L;
		data[67]=36028797018898495L;
		data[68]=5764607523034749677L;
		data[69]=12493387738468353L;
		data[70]=-756383734487318528L;
		data[71]=144405459145588743L;
		for (int i = 120; i<=121; i++) { data[i]=-1L; }
		data[122]=-4026531841L;
		data[123]=288230376151711743L;
		data[124]=-3233808385L;
		data[125]=4611686017001275199L;
		data[126]=6908521828386340863L;
		data[127]=2295745090394464220L;
		data[132]=83837761617920L;
		data[134]=7L;
		data[193]=-2L;
		data[194]=-8587837441L;
		data[195]=576460752303423487L;
		data[196]=35184372088800L;
		for (int i = 688; i<=861; i++) { data[i]=-1L; }
		data[862]=68719476735L;
		return data;
	}
	public static final BitSet _tokenSet_0 = new BitSet(mk_tokenSet_0());
	private static final long[] mk_tokenSet_1() {
		long[] data = new long[2504];
		data[192]=4389456576640L;
		for (int i = 312; i<=637; i++) { data[i]=-1L; }
		data[638]=274877906943L;
		return data;
	}
	public static final BitSet _tokenSet_1 = new BitSet(mk_tokenSet_1());
	private static final long[] mk_tokenSet_2() {
		long[] data = new long[1025];
		data[0]=287948901175001088L;
		data[25]=4393751543808L;
		data[27]=287948901175001088L;
		data[37]=281200098803712L;
		data[39]=281200098803712L;
		data[41]=281200098803712L;
		data[43]=281200098803712L;
		data[45]=281200098803712L;
		data[47]=280925220896768L;
		data[49]=281200098803712L;
		data[51]=281200098803712L;
		data[53]=281200098803712L;
		data[57]=67043328L;
		data[59]=67043328L;
		data[60]=4393751543808L;
		return data;
	}
	public static final BitSet _tokenSet_2 = new BitSet(mk_tokenSet_2());
	private static final long[] mk_tokenSet_3() {
		long[] data = new long[2756];
		data[1]=576460743847706622L;
		data[3]=-36028797027352577L;
		data[4]=9219994337134247935L;
		data[5]=9223372036854775294L;
		data[6]=-1L;
		data[7]=-274156627316187121L;
		data[8]=16777215L;
		data[9]=-65536L;
		data[10]=-576458553280167937L;
		data[11]=3L;
		data[14]=-17179879616L;
		data[15]=4503588160110591L;
		data[16]=-8194L;
		data[17]=-536936449L;
		data[18]=-65533L;
		data[19]=234134404065073567L;
		data[20]=-562949953421312L;
		data[21]=-8547991553L;
		data[22]=127L;
		data[23]=1979120929931264L;
		data[24]=576460743713488896L;
		data[25]=-562949953419266L;
		data[26]=9007199254740991999L;
		data[27]=412319973375L;
		data[36]=2594073385365405664L;
		data[37]=17163091968L;
		data[38]=271902628478820320L;
		data[39]=844440767823872L;
		data[40]=247132830528276448L;
		data[41]=7881300924956672L;
		data[42]=2589004636761075680L;
		data[43]=4294967296L;
		data[44]=2579997437506199520L;
		data[45]=15837691904L;
		data[46]=270153412153034720L;
		data[48]=283724577500946400L;
		data[49]=12884901888L;
		data[50]=283724577500946400L;
		data[51]=13958643712L;
		data[52]=288228177128316896L;
		data[53]=12884901888L;
		data[56]=3799912185593854L;
		data[57]=63L;
		data[58]=2309621682768192918L;
		data[59]=31L;
		data[61]=4398046510847L;
		data[66]=-4294967296L;
		data[67]=36028797018898495L;
		data[68]=5764607523034749677L;
		data[69]=12493387738468353L;
		data[70]=-756383734487318528L;
		data[71]=144405459145588743L;
		for (int i = 120; i<=121; i++) { data[i]=-1L; }
		data[122]=-4026531841L;
		data[123]=288230376151711743L;
		data[124]=-3233808385L;
		data[125]=4611686017001275199L;
		data[126]=6908521828386340863L;
		data[127]=2295745090394464220L;
		data[132]=83837761617920L;
		data[134]=7L;
		data[192]=4389456576640L;
		data[193]=-2L;
		data[194]=-8587837441L;
		data[195]=576460752303423487L;
		data[196]=35184372088800L;
		for (int i = 312; i<=637; i++) { data[i]=-1L; }
		data[638]=274877906943L;
		for (int i = 688; i<=861; i++) { data[i]=-1L; }
		data[862]=68719476735L;
		return data;
	}
	public static final BitSet _tokenSet_3 = new BitSet(mk_tokenSet_3());
	private static final long[] mk_tokenSet_4() {
		long[] data = new long[1025];
		data[12]=-1L;
		data[13]=12884901951L;
		data[18]=120L;
		data[22]=-4899916411759099904L;
		data[23]=22L;
		data[25]=281474977232896L;
		data[27]=67757399867392L;
		data[36]=-3458764513820540914L;
		data[37]=51541590015L;
		data[38]=-3458764513820540914L;
		data[39]=51548010911L;
		data[40]=-3458764513820540924L;
		data[41]=844424930146695L;
		data[42]=-3458764513820540914L;
		data[43]=15295L;
		data[44]=-3458764513820540914L;
		data[45]=12597647L;
		data[46]=-4611686018427387892L;
		data[47]=8404423L;
		data[48]=-4611686018427387890L;
		data[49]=6307295L;
		data[50]=-4611686018427387892L;
		data[51]=6307295L;
		data[52]=-4611686018427387892L;
		data[53]=8404431L;
		data[56]=572520102629474304L;
		data[57]=32640L;
		data[58]=2013671983388033024L;
		data[59]=16128L;
		data[60]=-4422534834027495424L;
		data[61]=-562949953421312L;
		data[62]=215680200883507167L;
		data[131]=9126739968L;
		data[192]=277076930199552L;
		data[194]=100663296L;
		return data;
	}
	public static final BitSet _tokenSet_4 = new BitSet(mk_tokenSet_4());
	private static final long[] mk_tokenSet_5() {
		long[] data = new long[2756];
		data[0]=288054454291267584L;
		data[1]=576460745995190270L;
		data[2]=36028797018963968L;
		data[3]=-36028797027352577L;
		data[4]=9219994337134247935L;
		data[5]=9223372036854775294L;
		data[6]=-1L;
		data[7]=-274156627316187121L;
		data[8]=16777215L;
		data[9]=-65536L;
		data[10]=-576458553280167937L;
		data[11]=196611L;
		data[12]=-1L;
		data[13]=12884901951L;
		data[14]=-17179879488L;
		data[15]=4503588160110591L;
		data[16]=-8194L;
		data[17]=-536936449L;
		data[18]=-65413L;
		data[19]=234134404065073567L;
		data[20]=-562949953421312L;
		data[21]=-8547991553L;
		data[22]=-4899916411759099777L;
		data[23]=1979120929931286L;
		data[24]=576460743713488896L;
		data[25]=-277081224642561L;
		data[26]=9007199254740991999L;
		data[27]=288017070894841855L;
		data[36]=-864691128455135250L;
		data[37]=281268803485695L;
		data[38]=-3186861885341720594L;
		data[39]=1125692414638495L;
		data[40]=-3211631683292264476L;
		data[41]=9006925953907079L;
		data[42]=-869759877059465234L;
		data[43]=281204393786303L;
		data[44]=-878767076314341394L;
		data[45]=281215949093263L;
		data[46]=-4341532606274353172L;
		data[47]=280925229301191L;
		data[48]=-4327961440926441490L;
		data[49]=281212990012895L;
		data[50]=-4327961440926441492L;
		data[51]=281214063754719L;
		data[52]=-4323457841299070996L;
		data[53]=281212992110031L;
		data[56]=576320014815068158L;
		data[57]=67076095L;
		data[58]=4323293666156225942L;
		data[59]=67059551L;
		data[60]=-4422530440275951616L;
		data[61]=-558551906910465L;
		data[62]=215680200883507167L;
		data[66]=-4294967296L;
		data[67]=36028797018898495L;
		data[68]=5764607523034749677L;
		data[69]=12493387738468353L;
		data[70]=-756383734487318528L;
		data[71]=144405459145588743L;
		for (int i = 120; i<=121; i++) { data[i]=-1L; }
		data[122]=-4026531841L;
		data[123]=288230376151711743L;
		data[124]=-3233808385L;
		data[125]=4611686017001275199L;
		data[126]=6908521828386340863L;
		data[127]=2295745090394464220L;
		data[131]=9126739968L;
		data[132]=83837761617920L;
		data[134]=7L;
		data[192]=17732914942836896L;
		data[193]=-2L;
		data[194]=-6876561409L;
		data[195]=8646911284551352319L;
		data[196]=35184372088800L;
		for (int i = 312; i<=637; i++) { data[i]=-1L; }
		data[638]=274877906943L;
		for (int i = 688; i<=861; i++) { data[i]=-1L; }
		data[862]=68719476735L;
		return data;
	}
	public static final BitSet _tokenSet_5 = new BitSet(mk_tokenSet_5());
	private static final long[] mk_tokenSet_6() {
		long[] data = new long[2048];
		data[0]=-292057776136L;
		for (int i = 1; i<=1022; i++) { data[i]=-1L; }
		data[1023]=9223372036854775807L;
		return data;
	}
	public static final BitSet _tokenSet_6 = new BitSet(mk_tokenSet_6());
	private static final long[] mk_tokenSet_7() {
		long[] data = new long[2048];
		data[0]=-824633720840L;
		for (int i = 1; i<=1022; i++) { data[i]=-1L; }
		data[1023]=9223372036854775807L;
		return data;
	}
	public static final BitSet _tokenSet_7 = new BitSet(mk_tokenSet_7());
	private static final long[] mk_tokenSet_8() {
		long[] data = new long[2048];
		data[0]=-1152921521786716168L;
		data[1]=-2882303761517117441L;
		for (int i = 2; i<=1022; i++) { data[i]=-1L; }
		data[1023]=9223372036854775807L;
		return data;
	}
	public static final BitSet _tokenSet_8 = new BitSet(mk_tokenSet_8());
	private static final long[] mk_tokenSet_9() {
		long[] data = new long[2048];
		data[0]=-1152922054362660872L;
		data[1]=-2882303761517117441L;
		for (int i = 2; i<=1022; i++) { data[i]=-1L; }
		data[1023]=9223372036854775807L;
		return data;
	}
	public static final BitSet _tokenSet_9 = new BitSet(mk_tokenSet_9());
	private static final long[] mk_tokenSet_10() {
		long[] data = new long[2048];
		data[0]=-35184372088840L;
		for (int i = 1; i<=1022; i++) { data[i]=-1L; }
		data[1023]=9223372036854775807L;
		return data;
	}
	public static final BitSet _tokenSet_10 = new BitSet(mk_tokenSet_10());
	private static final long[] mk_tokenSet_11() {
		long[] data = new long[2048];
		data[0]=9223372036854775800L;
		for (int i = 1; i<=1022; i++) { data[i]=-1L; }
		data[1023]=9223372036854775807L;
		return data;
	}
	public static final BitSet _tokenSet_11 = new BitSet(mk_tokenSet_11());
	private static final long[] mk_tokenSet_12() {
		long[] data = new long[2048];
		data[0]=-4611686018427387912L;
		for (int i = 1; i<=1022; i++) { data[i]=-1L; }
		data[1023]=9223372036854775807L;
		return data;
	}
	public static final BitSet _tokenSet_12 = new BitSet(mk_tokenSet_12());
	private static final long[] mk_tokenSet_13() {
		long[] data = new long[2048];
		data[0]=-8L;
		data[1]=-536870913L;
		for (int i = 2; i<=1022; i++) { data[i]=-1L; }
		data[1023]=9223372036854775807L;
		return data;
	}
	public static final BitSet _tokenSet_13 = new BitSet(mk_tokenSet_13());
	private static final long[] mk_tokenSet_14() {
		long[] data = new long[1025];
		data[0]=288019269919178752L;
		data[25]=4393751543808L;
		data[27]=287948901175001088L;
		data[37]=281200098803712L;
		data[39]=281200098803712L;
		data[41]=281200098803712L;
		data[43]=281200098803712L;
		data[45]=281200098803712L;
		data[47]=280925220896768L;
		data[49]=281200098803712L;
		data[51]=281200098803712L;
		data[53]=281200098803712L;
		data[57]=67043328L;
		data[59]=67043328L;
		data[60]=4393751543808L;
		return data;
	}
	public static final BitSet _tokenSet_14 = new BitSet(mk_tokenSet_14());
	private static final long[] mk_tokenSet_15() {
		long[] data = new long[1025];
		data[0]=288019269919178752L;
		data[1]=137438953504L;
		data[25]=4393751543808L;
		data[27]=287948901175001088L;
		data[37]=281200098803712L;
		data[39]=281200098803712L;
		data[41]=281200098803712L;
		data[43]=281200098803712L;
		data[45]=281200098803712L;
		data[47]=280925220896768L;
		data[49]=281200098803712L;
		data[51]=281200098803712L;
		data[53]=281200098803712L;
		data[57]=67043328L;
		data[59]=67043328L;
		data[60]=4393751543808L;
		return data;
	}
	public static final BitSet _tokenSet_15 = new BitSet(mk_tokenSet_15());
	private static final long[] mk_tokenSet_16() {
		long[] data = new long[1025];
		data[0]=288063250384289792L;
		data[1]=137438953504L;
		data[25]=4393751543808L;
		data[27]=287948901175001088L;
		data[37]=281200098803712L;
		data[39]=281200098803712L;
		data[41]=281200098803712L;
		data[43]=281200098803712L;
		data[45]=281200098803712L;
		data[47]=280925220896768L;
		data[49]=281200098803712L;
		data[51]=281200098803712L;
		data[53]=281200098803712L;
		data[57]=67043328L;
		data[59]=67043328L;
		data[60]=4393751543808L;
		return data;
	}
	public static final BitSet _tokenSet_16 = new BitSet(mk_tokenSet_16());
	private static final long[] mk_tokenSet_17() {
		long[] data = new long[2048];
		data[0]=-1152921508901804544L;
		data[1]=-2882303761517117441L;
		for (int i = 2; i<=1022; i++) { data[i]=-1L; }
		data[1023]=4611686018427387903L;
		return data;
	}
	public static final BitSet _tokenSet_17 = new BitSet(mk_tokenSet_17());
	private static final long[] mk_tokenSet_18() {
		long[] data = new long[2756];
		data[1]=576460745995190270L;
		data[3]=-36028797027352577L;
		data[4]=9219994337134247935L;
		data[5]=9223372036854775294L;
		data[6]=-1L;
		data[7]=-274156627316187121L;
		data[8]=16777215L;
		data[9]=-65536L;
		data[10]=-576458553280167937L;
		data[11]=3L;
		data[14]=-17179879616L;
		data[15]=4503588160110591L;
		data[16]=-8194L;
		data[17]=-536936449L;
		data[18]=-65533L;
		data[19]=234134404065073567L;
		data[20]=-562949953421312L;
		data[21]=-8547991553L;
		data[22]=127L;
		data[23]=1979120929931264L;
		data[24]=576460743713488896L;
		data[25]=-562949953419266L;
		data[26]=9007199254740991999L;
		data[27]=412319973375L;
		data[36]=2594073385365405664L;
		data[37]=17163091968L;
		data[38]=271902628478820320L;
		data[39]=844440767823872L;
		data[40]=247132830528276448L;
		data[41]=7881300924956672L;
		data[42]=2589004636761075680L;
		data[43]=4294967296L;
		data[44]=2579997437506199520L;
		data[45]=15837691904L;
		data[46]=270153412153034720L;
		data[48]=283724577500946400L;
		data[49]=12884901888L;
		data[50]=283724577500946400L;
		data[51]=13958643712L;
		data[52]=288228177128316896L;
		data[53]=12884901888L;
		data[56]=3799912185593854L;
		data[57]=63L;
		data[58]=2309621682768192918L;
		data[59]=31L;
		data[61]=4398046510847L;
		data[66]=-4294967296L;
		data[67]=36028797018898495L;
		data[68]=5764607523034749677L;
		data[69]=12493387738468353L;
		data[70]=-756383734487318528L;
		data[71]=144405459145588743L;
		for (int i = 120; i<=121; i++) { data[i]=-1L; }
		data[122]=-4026531841L;
		data[123]=288230376151711743L;
		data[124]=-3233808385L;
		data[125]=4611686017001275199L;
		data[126]=6908521828386340863L;
		data[127]=2295745090394464220L;
		data[132]=83837761617920L;
		data[134]=7L;
		data[192]=4389456576640L;
		data[193]=-2L;
		data[194]=-8587837441L;
		data[195]=576460752303423487L;
		data[196]=35184372088800L;
		for (int i = 312; i<=637; i++) { data[i]=-1L; }
		data[638]=274877906943L;
		for (int i = 688; i<=861; i++) { data[i]=-1L; }
		data[862]=68719476735L;
		return data;
	}
	public static final BitSet _tokenSet_18 = new BitSet(mk_tokenSet_18());
	
	}
