// $ANTLR 2.7.2: "XPathParser2.g" -> "XPathParser2.java"$

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
import antlr.collections.AST;
import java.util.Hashtable;
import antlr.ASTFactory;
import antlr.ASTPair;
import antlr.collections.impl.ASTArray;

public class XPathParser2 extends antlr.LLkParser       implements XPathParser2TokenTypes
 {

	protected ArrayList exceptions = new ArrayList(2);
	protected boolean foundError = false;
	
	public boolean foundErrors() {
		return foundError;
	}
	
	public String getErrorMessage() {
		StringBuffer buf = new StringBuffer();
		for(Iterator i = exceptions.iterator(); i.hasNext(); ) {
			buf.append(((Exception)i.next()).toString());
			buf.append('\n');
		}
		return buf.toString();
	}
	
	protected void handleException(Exception e) {
		foundError = true;
		exceptions.add(e);
	}

protected XPathParser2(TokenBuffer tokenBuf, int k) {
  super(tokenBuf,k);
  tokenNames = _tokenNames;
  buildTokenTypeASTClassMap();
  astFactory = new ASTFactory(getTokenTypeToASTClassMap());
}

public XPathParser2(TokenBuffer tokenBuf) {
  this(tokenBuf,1);
}

protected XPathParser2(TokenStream lexer, int k) {
  super(lexer,k);
  tokenNames = _tokenNames;
  buildTokenTypeASTClassMap();
  astFactory = new ASTFactory(getTokenTypeToASTClassMap());
}

public XPathParser2(TokenStream lexer) {
  this(lexer,1);
}

public XPathParser2(ParserSharedInputState state) {
  super(state,1);
  tokenNames = _tokenNames;
  buildTokenTypeASTClassMap();
  astFactory = new ASTFactory(getTokenTypeToASTClassMap());
}

	public final void imaginaryTokenDefinitions() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST imaginaryTokenDefinitions_AST = null;
		
		AST tmp47_AST = null;
		tmp47_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp47_AST);
		match(QNAME);
		AST tmp48_AST = null;
		tmp48_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp48_AST);
		match(PREDICATE);
		AST tmp49_AST = null;
		tmp49_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp49_AST);
		match(PARENTHESIZED);
		AST tmp50_AST = null;
		tmp50_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp50_AST);
		match(ABSOLUTE_SLASH);
		AST tmp51_AST = null;
		tmp51_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp51_AST);
		match(ABSOLUTE_DSLASH);
		AST tmp52_AST = null;
		tmp52_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp52_AST);
		match(WILDCARD);
		AST tmp53_AST = null;
		tmp53_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp53_AST);
		match(PREFIX_WILDCARD);
		AST tmp54_AST = null;
		tmp54_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp54_AST);
		match(FUNCTION);
		AST tmp55_AST = null;
		tmp55_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp55_AST);
		match(UNARY_MINUS);
		AST tmp56_AST = null;
		tmp56_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp56_AST);
		match(UNARY_PLUS);
		AST tmp57_AST = null;
		tmp57_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp57_AST);
		match(XPOINTER);
		AST tmp58_AST = null;
		tmp58_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp58_AST);
		match(XPOINTER_ID);
		imaginaryTokenDefinitions_AST = (AST)currentAST.root;
		returnAST = imaginaryTokenDefinitions_AST;
	}
	
	public final void xpointer() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST xpointer_AST = null;
		AST ex_AST = null;
		Token  nc = null;
		AST nc_AST = null;
		
		switch ( LA(1)) {
		case LITERAL_xpointer:
		{
			AST tmp59_AST = null;
			tmp59_AST = astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp59_AST);
			match(LITERAL_xpointer);
			match(LPAREN);
			expr();
			ex_AST = (AST)returnAST;
			astFactory.addASTChild(currentAST, returnAST);
			match(RPAREN);
			if ( inputState.guessing==0 ) {
				xpointer_AST = (AST)currentAST.root;
				
							xpointer_AST = (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(XPOINTER,"xpointer")).add(ex_AST));
						
				currentAST.root = xpointer_AST;
				currentAST.child = xpointer_AST!=null &&xpointer_AST.getFirstChild()!=null ?
					xpointer_AST.getFirstChild() : xpointer_AST;
				currentAST.advanceChildToEnd();
			}
			xpointer_AST = (AST)currentAST.root;
			break;
		}
		case NCNAME:
		{
			nc = LT(1);
			nc_AST = astFactory.create(nc);
			astFactory.addASTChild(currentAST, nc_AST);
			match(NCNAME);
			if ( inputState.guessing==0 ) {
				xpointer_AST = (AST)currentAST.root;
				
							xpointer_AST = (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(XPOINTER_ID,"id")).add(nc_AST));
						
				currentAST.root = xpointer_AST;
				currentAST.child = xpointer_AST!=null &&xpointer_AST.getFirstChild()!=null ?
					xpointer_AST.getFirstChild() : xpointer_AST;
				currentAST.advanceChildToEnd();
			}
			xpointer_AST = (AST)currentAST.root;
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		returnAST = xpointer_AST;
	}
	
	public final void expr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST expr_AST = null;
		
		orExpr();
		astFactory.addASTChild(currentAST, returnAST);
		expr_AST = (AST)currentAST.root;
		returnAST = expr_AST;
	}
	
	public final void xpath() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST xpath_AST = null;
		
		try {      // for error handling
			{
			switch ( LA(1)) {
			case LPAREN:
			case NCNAME:
			case LITERAL_or:
			case LITERAL_and:
			case STRING_LITERAL:
			case PLUS:
			case MINUS:
			case STAR:
			case LITERAL_div:
			case LITERAL_mod:
			case SLASH:
			case DSLASH:
			case LITERAL_text:
			case LITERAL_node:
			case SELF:
			case AT:
			case PARENT:
			case LITERAL_child:
			case LITERAL_self:
			case LITERAL_attribute:
			case LITERAL_descendant:
			case 51:
			case 52:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 55:
			case 56:
			case DOUBLE_LITERAL:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
			{
				expr();
				astFactory.addASTChild(currentAST, returnAST);
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
			xpath_AST = (AST)currentAST.root;
		}
		catch (RecognitionException e) {
			if (inputState.guessing==0) {
				
						handleException(e);
					
			} else {
				throw e;
			}
		}
		returnAST = xpath_AST;
	}
	
	public final void orExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST orExpr_AST = null;
		
		andExpr();
		astFactory.addASTChild(currentAST, returnAST);
		{
		_loop8:
		do {
			if ((LA(1)==LITERAL_or)) {
				AST tmp62_AST = null;
				tmp62_AST = astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp62_AST);
				match(LITERAL_or);
				andExpr();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop8;
			}
			
		} while (true);
		}
		orExpr_AST = (AST)currentAST.root;
		returnAST = orExpr_AST;
	}
	
	public final void andExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST andExpr_AST = null;
		
		comparisonExpr();
		astFactory.addASTChild(currentAST, returnAST);
		{
		_loop11:
		do {
			if ((LA(1)==LITERAL_and)) {
				AST tmp63_AST = null;
				tmp63_AST = astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp63_AST);
				match(LITERAL_and);
				comparisonExpr();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop11;
			}
			
		} while (true);
		}
		andExpr_AST = (AST)currentAST.root;
		returnAST = andExpr_AST;
	}
	
	public final void comparisonExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST comparisonExpr_AST = null;
		
		additiveExpr();
		astFactory.addASTChild(currentAST, returnAST);
		{
		switch ( LA(1)) {
		case EQ:
		case NEQ:
		case GT:
		case GTEQ:
		case LT:
		case LTEQ:
		{
			{
			{
			switch ( LA(1)) {
			case EQ:
			{
				AST tmp64_AST = null;
				tmp64_AST = astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp64_AST);
				match(EQ);
				break;
			}
			case NEQ:
			{
				AST tmp65_AST = null;
				tmp65_AST = astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp65_AST);
				match(NEQ);
				break;
			}
			case GT:
			{
				AST tmp66_AST = null;
				tmp66_AST = astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp66_AST);
				match(GT);
				break;
			}
			case GTEQ:
			{
				AST tmp67_AST = null;
				tmp67_AST = astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp67_AST);
				match(GTEQ);
				break;
			}
			case LT:
			{
				AST tmp68_AST = null;
				tmp68_AST = astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp68_AST);
				match(LT);
				break;
			}
			case LTEQ:
			{
				AST tmp69_AST = null;
				tmp69_AST = astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp69_AST);
				match(LTEQ);
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			}
			additiveExpr();
			astFactory.addASTChild(currentAST, returnAST);
			}
			break;
		}
		case ANDEQ:
		case OREQ:
		{
			{
			{
			switch ( LA(1)) {
			case ANDEQ:
			{
				AST tmp70_AST = null;
				tmp70_AST = astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp70_AST);
				match(ANDEQ);
				break;
			}
			case OREQ:
			{
				AST tmp71_AST = null;
				tmp71_AST = astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp71_AST);
				match(OREQ);
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			}
			AST tmp72_AST = null;
			tmp72_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp72_AST);
			match(STRING_LITERAL);
			}
			break;
		}
		case EOF:
		case RPAREN:
		case LITERAL_or:
		case LITERAL_and:
		case RPPAREN:
		case COMMA:
		{
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		}
		comparisonExpr_AST = (AST)currentAST.root;
		returnAST = comparisonExpr_AST;
	}
	
	public final void additiveExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST additiveExpr_AST = null;
		
		multiplicativeExpr();
		astFactory.addASTChild(currentAST, returnAST);
		{
		_loop21:
		do {
			if ((LA(1)==PLUS||LA(1)==MINUS)) {
				{
				switch ( LA(1)) {
				case PLUS:
				{
					AST tmp73_AST = null;
					tmp73_AST = astFactory.create(LT(1));
					astFactory.makeASTRoot(currentAST, tmp73_AST);
					match(PLUS);
					break;
				}
				case MINUS:
				{
					AST tmp74_AST = null;
					tmp74_AST = astFactory.create(LT(1));
					astFactory.makeASTRoot(currentAST, tmp74_AST);
					match(MINUS);
					break;
				}
				default:
				{
					throw new NoViableAltException(LT(1), getFilename());
				}
				}
				}
				multiplicativeExpr();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop21;
			}
			
		} while (true);
		}
		additiveExpr_AST = (AST)currentAST.root;
		returnAST = additiveExpr_AST;
	}
	
	public final void multiplicativeExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST multiplicativeExpr_AST = null;
		
		unaryExpr();
		astFactory.addASTChild(currentAST, returnAST);
		{
		_loop25:
		do {
			if (((LA(1) >= STAR && LA(1) <= LITERAL_mod))) {
				{
				switch ( LA(1)) {
				case STAR:
				{
					AST tmp75_AST = null;
					tmp75_AST = astFactory.create(LT(1));
					astFactory.makeASTRoot(currentAST, tmp75_AST);
					match(STAR);
					break;
				}
				case LITERAL_div:
				{
					AST tmp76_AST = null;
					tmp76_AST = astFactory.create(LT(1));
					astFactory.makeASTRoot(currentAST, tmp76_AST);
					match(LITERAL_div);
					break;
				}
				case LITERAL_mod:
				{
					AST tmp77_AST = null;
					tmp77_AST = astFactory.create(LT(1));
					astFactory.makeASTRoot(currentAST, tmp77_AST);
					match(LITERAL_mod);
					break;
				}
				default:
				{
					throw new NoViableAltException(LT(1), getFilename());
				}
				}
				}
				unaryExpr();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop25;
			}
			
		} while (true);
		}
		multiplicativeExpr_AST = (AST)currentAST.root;
		returnAST = multiplicativeExpr_AST;
	}
	
	public final void unaryExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST unaryExpr_AST = null;
		AST expr_AST = null;
		AST expr2_AST = null;
		
		switch ( LA(1)) {
		case MINUS:
		{
			AST tmp78_AST = null;
			tmp78_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp78_AST);
			match(MINUS);
			unionExpr();
			expr_AST = (AST)returnAST;
			astFactory.addASTChild(currentAST, returnAST);
			if ( inputState.guessing==0 ) {
				unaryExpr_AST = (AST)currentAST.root;
				
							unaryExpr_AST = (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(UNARY_MINUS,"-")).add(expr_AST));
						
				currentAST.root = unaryExpr_AST;
				currentAST.child = unaryExpr_AST!=null &&unaryExpr_AST.getFirstChild()!=null ?
					unaryExpr_AST.getFirstChild() : unaryExpr_AST;
				currentAST.advanceChildToEnd();
			}
			unaryExpr_AST = (AST)currentAST.root;
			break;
		}
		case PLUS:
		{
			AST tmp79_AST = null;
			tmp79_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp79_AST);
			match(PLUS);
			unionExpr();
			expr2_AST = (AST)returnAST;
			astFactory.addASTChild(currentAST, returnAST);
			if ( inputState.guessing==0 ) {
				unaryExpr_AST = (AST)currentAST.root;
				
							unaryExpr_AST = (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(UNARY_PLUS,"+")).add(expr2_AST));
						
				currentAST.root = unaryExpr_AST;
				currentAST.child = unaryExpr_AST!=null &&unaryExpr_AST.getFirstChild()!=null ?
					unaryExpr_AST.getFirstChild() : unaryExpr_AST;
				currentAST.advanceChildToEnd();
			}
			unaryExpr_AST = (AST)currentAST.root;
			break;
		}
		case LPAREN:
		case NCNAME:
		case LITERAL_or:
		case LITERAL_and:
		case STRING_LITERAL:
		case STAR:
		case LITERAL_div:
		case LITERAL_mod:
		case SLASH:
		case DSLASH:
		case LITERAL_text:
		case LITERAL_node:
		case SELF:
		case AT:
		case PARENT:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_attribute:
		case LITERAL_descendant:
		case 51:
		case 52:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 55:
		case 56:
		case DOUBLE_LITERAL:
		case DECIMAL_LITERAL:
		case INTEGER_LITERAL:
		{
			unionExpr();
			astFactory.addASTChild(currentAST, returnAST);
			unaryExpr_AST = (AST)currentAST.root;
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		returnAST = unaryExpr_AST;
	}
	
	public final void unionExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST unionExpr_AST = null;
		
		pathExpr();
		astFactory.addASTChild(currentAST, returnAST);
		{
		switch ( LA(1)) {
		case UNION:
		{
			AST tmp80_AST = null;
			tmp80_AST = astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp80_AST);
			match(UNION);
			pathExpr();
			astFactory.addASTChild(currentAST, returnAST);
			break;
		}
		case EOF:
		case RPAREN:
		case LITERAL_or:
		case LITERAL_and:
		case EQ:
		case NEQ:
		case GT:
		case GTEQ:
		case LT:
		case LTEQ:
		case ANDEQ:
		case OREQ:
		case PLUS:
		case MINUS:
		case STAR:
		case LITERAL_div:
		case LITERAL_mod:
		case RPPAREN:
		case COMMA:
		{
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		}
		unionExpr_AST = (AST)currentAST.root;
		returnAST = unionExpr_AST;
	}
	
	public final void pathExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST pathExpr_AST = null;
		AST relPath_AST = null;
		AST relPath2_AST = null;
		
		switch ( LA(1)) {
		case LPAREN:
		case NCNAME:
		case LITERAL_or:
		case LITERAL_and:
		case STRING_LITERAL:
		case STAR:
		case LITERAL_div:
		case LITERAL_mod:
		case LITERAL_text:
		case LITERAL_node:
		case SELF:
		case AT:
		case PARENT:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_attribute:
		case LITERAL_descendant:
		case 51:
		case 52:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 55:
		case 56:
		case DOUBLE_LITERAL:
		case DECIMAL_LITERAL:
		case INTEGER_LITERAL:
		{
			relativePathExpr();
			astFactory.addASTChild(currentAST, returnAST);
			pathExpr_AST = (AST)currentAST.root;
			break;
		}
		case DSLASH:
		{
			AST tmp81_AST = null;
			tmp81_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp81_AST);
			match(DSLASH);
			relativePathExpr();
			relPath2_AST = (AST)returnAST;
			astFactory.addASTChild(currentAST, returnAST);
			if ( inputState.guessing==0 ) {
				pathExpr_AST = (AST)currentAST.root;
				
							pathExpr_AST = (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(ABSOLUTE_DSLASH,"AbsoluteSlashSlash")).add(relPath2_AST));
						
				currentAST.root = pathExpr_AST;
				currentAST.child = pathExpr_AST!=null &&pathExpr_AST.getFirstChild()!=null ?
					pathExpr_AST.getFirstChild() : pathExpr_AST;
				currentAST.advanceChildToEnd();
			}
			pathExpr_AST = (AST)currentAST.root;
			break;
		}
		default:
			boolean synPredMatched31 = false;
			if (((LA(1)==SLASH))) {
				int _m31 = mark();
				synPredMatched31 = true;
				inputState.guessing++;
				try {
					{
					match(SLASH);
					relativePathExpr();
					}
				}
				catch (RecognitionException pe) {
					synPredMatched31 = false;
				}
				rewind(_m31);
				inputState.guessing--;
			}
			if ( synPredMatched31 ) {
				AST tmp82_AST = null;
				tmp82_AST = astFactory.create(LT(1));
				astFactory.addASTChild(currentAST, tmp82_AST);
				match(SLASH);
				relativePathExpr();
				relPath_AST = (AST)returnAST;
				astFactory.addASTChild(currentAST, returnAST);
				if ( inputState.guessing==0 ) {
					pathExpr_AST = (AST)currentAST.root;
					
								pathExpr_AST = (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(ABSOLUTE_SLASH,"AbsoluteSlash")).add(relPath_AST));
							
					currentAST.root = pathExpr_AST;
					currentAST.child = pathExpr_AST!=null &&pathExpr_AST.getFirstChild()!=null ?
						pathExpr_AST.getFirstChild() : pathExpr_AST;
					currentAST.advanceChildToEnd();
				}
				pathExpr_AST = (AST)currentAST.root;
			}
			else if ((LA(1)==SLASH)) {
				AST tmp83_AST = null;
				tmp83_AST = astFactory.create(LT(1));
				astFactory.addASTChild(currentAST, tmp83_AST);
				match(SLASH);
				if ( inputState.guessing==0 ) {
					pathExpr_AST = (AST)currentAST.root;
					
								pathExpr_AST = astFactory.create(ABSOLUTE_SLASH,"AbsoluteSlash");
							
					currentAST.root = pathExpr_AST;
					currentAST.child = pathExpr_AST!=null &&pathExpr_AST.getFirstChild()!=null ?
						pathExpr_AST.getFirstChild() : pathExpr_AST;
					currentAST.advanceChildToEnd();
				}
				pathExpr_AST = (AST)currentAST.root;
			}
		else {
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		returnAST = pathExpr_AST;
	}
	
	public final void relativePathExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST relativePathExpr_AST = null;
		
		stepExpr();
		astFactory.addASTChild(currentAST, returnAST);
		{
		_loop35:
		do {
			if ((LA(1)==SLASH||LA(1)==DSLASH)) {
				{
				switch ( LA(1)) {
				case SLASH:
				{
					AST tmp84_AST = null;
					tmp84_AST = astFactory.create(LT(1));
					astFactory.makeASTRoot(currentAST, tmp84_AST);
					match(SLASH);
					break;
				}
				case DSLASH:
				{
					AST tmp85_AST = null;
					tmp85_AST = astFactory.create(LT(1));
					astFactory.makeASTRoot(currentAST, tmp85_AST);
					match(DSLASH);
					break;
				}
				default:
				{
					throw new NoViableAltException(LT(1), getFilename());
				}
				}
				}
				stepExpr();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop35;
			}
			
		} while (true);
		}
		relativePathExpr_AST = (AST)currentAST.root;
		returnAST = relativePathExpr_AST;
	}
	
	public final void stepExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST stepExpr_AST = null;
		
		boolean synPredMatched39 = false;
		if (((_tokenSet_0.member(LA(1))))) {
			int _m39 = mark();
			synPredMatched39 = true;
			inputState.guessing++;
			try {
				{
				{
				switch ( LA(1)) {
				case LITERAL_text:
				{
					match(LITERAL_text);
					break;
				}
				case LITERAL_node:
				{
					match(LITERAL_node);
					break;
				}
				default:
				{
					throw new NoViableAltException(LT(1), getFilename());
				}
				}
				}
				match(LPAREN);
				}
			}
			catch (RecognitionException pe) {
				synPredMatched39 = false;
			}
			rewind(_m39);
			inputState.guessing--;
		}
		if ( synPredMatched39 ) {
			axisStep();
			astFactory.addASTChild(currentAST, returnAST);
			stepExpr_AST = (AST)currentAST.root;
		}
		else {
			boolean synPredMatched42 = false;
			if (((_tokenSet_1.member(LA(1))))) {
				int _m42 = mark();
				synPredMatched42 = true;
				inputState.guessing++;
				try {
					{
					switch ( LA(1)) {
					case NCNAME:
					case LITERAL_or:
					case LITERAL_and:
					case LITERAL_div:
					case LITERAL_mod:
					case LITERAL_text:
					case LITERAL_node:
					case LITERAL_child:
					case LITERAL_self:
					case LITERAL_attribute:
					case LITERAL_descendant:
					case 51:
					case 52:
					case LITERAL_parent:
					case LITERAL_ancestor:
					case 55:
					case 56:
					{
						{
						qName();
						match(LPAREN);
						}
						break;
					}
					case SELF:
					{
						match(SELF);
						break;
					}
					case LPAREN:
					{
						match(LPAREN);
						break;
					}
					case STRING_LITERAL:
					case DOUBLE_LITERAL:
					case DECIMAL_LITERAL:
					case INTEGER_LITERAL:
					{
						literal();
						break;
					}
					default:
					{
						throw new NoViableAltException(LT(1), getFilename());
					}
					}
					}
				}
				catch (RecognitionException pe) {
					synPredMatched42 = false;
				}
				rewind(_m42);
				inputState.guessing--;
			}
			if ( synPredMatched42 ) {
				filterStep();
				astFactory.addASTChild(currentAST, returnAST);
				stepExpr_AST = (AST)currentAST.root;
			}
			else if ((_tokenSet_0.member(LA(1)))) {
				axisStep();
				astFactory.addASTChild(currentAST, returnAST);
				stepExpr_AST = (AST)currentAST.root;
			}
			else {
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			returnAST = stepExpr_AST;
		}
		
	public final void axisStep() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST axisStep_AST = null;
		
		{
		forwardOrReverseStep();
		astFactory.addASTChild(currentAST, returnAST);
		}
		predicates();
		astFactory.addASTChild(currentAST, returnAST);
		axisStep_AST = (AST)currentAST.root;
		returnAST = axisStep_AST;
	}
	
	public final String  qName() throws RecognitionException, TokenStreamException {
		String name;
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST qName_AST = null;
		
			name = null;
			String name2;
		
		
		name=ncnameOrKeyword();
		astFactory.addASTChild(currentAST, returnAST);
		{
		switch ( LA(1)) {
		case COLON:
		{
			AST tmp86_AST = null;
			tmp86_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp86_AST);
			match(COLON);
			name2=ncnameOrKeyword();
			astFactory.addASTChild(currentAST, returnAST);
			if ( inputState.guessing==0 ) {
				
							name = name + ':' + name2;
						
			}
			break;
		}
		case EOF:
		case LPAREN:
		case RPAREN:
		case LITERAL_or:
		case LITERAL_and:
		case EQ:
		case NEQ:
		case GT:
		case GTEQ:
		case LT:
		case LTEQ:
		case ANDEQ:
		case OREQ:
		case PLUS:
		case MINUS:
		case STAR:
		case LITERAL_div:
		case LITERAL_mod:
		case UNION:
		case SLASH:
		case DSLASH:
		case LPPAREN:
		case RPPAREN:
		case COMMA:
		{
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		}
		qName_AST = (AST)currentAST.root;
		returnAST = qName_AST;
		return name;
	}
	
	public final void literal() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST literal_AST = null;
		
		switch ( LA(1)) {
		case STRING_LITERAL:
		{
			AST tmp87_AST = null;
			tmp87_AST = astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp87_AST);
			match(STRING_LITERAL);
			literal_AST = (AST)currentAST.root;
			break;
		}
		case DOUBLE_LITERAL:
		case DECIMAL_LITERAL:
		case INTEGER_LITERAL:
		{
			numericLiteral();
			astFactory.addASTChild(currentAST, returnAST);
			literal_AST = (AST)currentAST.root;
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		returnAST = literal_AST;
	}
	
	public final void filterStep() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST filterStep_AST = null;
		
		primaryExpr();
		astFactory.addASTChild(currentAST, returnAST);
		predicates();
		astFactory.addASTChild(currentAST, returnAST);
		filterStep_AST = (AST)currentAST.root;
		returnAST = filterStep_AST;
	}
	
	public final void forwardOrReverseStep() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST forwardOrReverseStep_AST = null;
		
		boolean synPredMatched51 = false;
		if ((((LA(1) >= LITERAL_child && LA(1) <= 52)))) {
			int _m51 = mark();
			synPredMatched51 = true;
			inputState.guessing++;
			try {
				{
				forwardAxisSpecifier();
				match(COLON);
				}
			}
			catch (RecognitionException pe) {
				synPredMatched51 = false;
			}
			rewind(_m51);
			inputState.guessing--;
		}
		if ( synPredMatched51 ) {
			forwardAxis();
			astFactory.addASTChild(currentAST, returnAST);
			nodeTest();
			astFactory.addASTChild(currentAST, returnAST);
			forwardOrReverseStep_AST = (AST)currentAST.root;
		}
		else {
			boolean synPredMatched53 = false;
			if ((((LA(1) >= LITERAL_parent && LA(1) <= 56)))) {
				int _m53 = mark();
				synPredMatched53 = true;
				inputState.guessing++;
				try {
					{
					reverseAxisSpecifier();
					match(COLON);
					}
				}
				catch (RecognitionException pe) {
					synPredMatched53 = false;
				}
				rewind(_m53);
				inputState.guessing--;
			}
			if ( synPredMatched53 ) {
				reverseAxis();
				astFactory.addASTChild(currentAST, returnAST);
				nodeTest();
				astFactory.addASTChild(currentAST, returnAST);
				forwardOrReverseStep_AST = (AST)currentAST.root;
			}
			else if ((_tokenSet_0.member(LA(1)))) {
				abbrevStep();
				astFactory.addASTChild(currentAST, returnAST);
				forwardOrReverseStep_AST = (AST)currentAST.root;
			}
			else {
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			returnAST = forwardOrReverseStep_AST;
		}
		
	public final void predicates() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST predicates_AST = null;
		
		{
		_loop47:
		do {
			if ((LA(1)==LPPAREN)) {
				predicate();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop47;
			}
			
		} while (true);
		}
		predicates_AST = (AST)currentAST.root;
		returnAST = predicates_AST;
	}
	
	public final void predicate() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST predicate_AST = null;
		AST predExpr_AST = null;
		
		match(LPPAREN);
		expr();
		predExpr_AST = (AST)returnAST;
		astFactory.addASTChild(currentAST, returnAST);
		match(RPPAREN);
		if ( inputState.guessing==0 ) {
			predicate_AST = (AST)currentAST.root;
			
						predicate_AST = (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(PREDICATE,"Pred")).add(predExpr_AST));
					
			currentAST.root = predicate_AST;
			currentAST.child = predicate_AST!=null &&predicate_AST.getFirstChild()!=null ?
				predicate_AST.getFirstChild() : predicate_AST;
			currentAST.advanceChildToEnd();
		}
		predicate_AST = (AST)currentAST.root;
		returnAST = predicate_AST;
	}
	
	public final void forwardAxisSpecifier() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST forwardAxisSpecifier_AST = null;
		
		switch ( LA(1)) {
		case LITERAL_child:
		{
			AST tmp90_AST = null;
			tmp90_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp90_AST);
			match(LITERAL_child);
			forwardAxisSpecifier_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_self:
		{
			AST tmp91_AST = null;
			tmp91_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp91_AST);
			match(LITERAL_self);
			forwardAxisSpecifier_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_attribute:
		{
			AST tmp92_AST = null;
			tmp92_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp92_AST);
			match(LITERAL_attribute);
			forwardAxisSpecifier_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_descendant:
		{
			AST tmp93_AST = null;
			tmp93_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp93_AST);
			match(LITERAL_descendant);
			forwardAxisSpecifier_AST = (AST)currentAST.root;
			break;
		}
		case 51:
		{
			AST tmp94_AST = null;
			tmp94_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp94_AST);
			match(51);
			forwardAxisSpecifier_AST = (AST)currentAST.root;
			break;
		}
		case 52:
		{
			AST tmp95_AST = null;
			tmp95_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp95_AST);
			match(52);
			forwardAxisSpecifier_AST = (AST)currentAST.root;
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		returnAST = forwardAxisSpecifier_AST;
	}
	
	public final void forwardAxis() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST forwardAxis_AST = null;
		
		forwardAxisSpecifier();
		astFactory.addASTChild(currentAST, returnAST);
		match(COLON);
		match(COLON);
		forwardAxis_AST = (AST)currentAST.root;
		returnAST = forwardAxis_AST;
	}
	
	public final void nodeTest() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST nodeTest_AST = null;
		
		boolean synPredMatched63 = false;
		if (((LA(1)==LITERAL_text||LA(1)==LITERAL_node))) {
			int _m63 = mark();
			synPredMatched63 = true;
			inputState.guessing++;
			try {
				{
				{
				switch ( LA(1)) {
				case LITERAL_text:
				{
					match(LITERAL_text);
					break;
				}
				case LITERAL_node:
				{
					match(LITERAL_node);
					break;
				}
				default:
				{
					throw new NoViableAltException(LT(1), getFilename());
				}
				}
				}
				match(LPAREN);
				}
			}
			catch (RecognitionException pe) {
				synPredMatched63 = false;
			}
			rewind(_m63);
			inputState.guessing--;
		}
		if ( synPredMatched63 ) {
			kindTest();
			astFactory.addASTChild(currentAST, returnAST);
			nodeTest_AST = (AST)currentAST.root;
		}
		else if ((_tokenSet_2.member(LA(1)))) {
			nameTest();
			astFactory.addASTChild(currentAST, returnAST);
			nodeTest_AST = (AST)currentAST.root;
		}
		else {
			throw new NoViableAltException(LT(1), getFilename());
		}
		
		returnAST = nodeTest_AST;
	}
	
	public final void reverseAxisSpecifier() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST reverseAxisSpecifier_AST = null;
		
		switch ( LA(1)) {
		case LITERAL_parent:
		{
			AST tmp98_AST = null;
			tmp98_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp98_AST);
			match(LITERAL_parent);
			reverseAxisSpecifier_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_ancestor:
		{
			AST tmp99_AST = null;
			tmp99_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp99_AST);
			match(LITERAL_ancestor);
			reverseAxisSpecifier_AST = (AST)currentAST.root;
			break;
		}
		case 55:
		{
			AST tmp100_AST = null;
			tmp100_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp100_AST);
			match(55);
			reverseAxisSpecifier_AST = (AST)currentAST.root;
			break;
		}
		case 56:
		{
			AST tmp101_AST = null;
			tmp101_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp101_AST);
			match(56);
			reverseAxisSpecifier_AST = (AST)currentAST.root;
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		returnAST = reverseAxisSpecifier_AST;
	}
	
	public final void reverseAxis() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST reverseAxis_AST = null;
		
		reverseAxisSpecifier();
		astFactory.addASTChild(currentAST, returnAST);
		match(COLON);
		match(COLON);
		reverseAxis_AST = (AST)currentAST.root;
		returnAST = reverseAxis_AST;
	}
	
	public final void abbrevStep() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST abbrevStep_AST = null;
		
		switch ( LA(1)) {
		case NCNAME:
		case LITERAL_or:
		case LITERAL_and:
		case STAR:
		case LITERAL_div:
		case LITERAL_mod:
		case LITERAL_text:
		case LITERAL_node:
		case AT:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_attribute:
		case LITERAL_descendant:
		case 51:
		case 52:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 55:
		case 56:
		{
			{
			switch ( LA(1)) {
			case AT:
			{
				AST tmp104_AST = null;
				tmp104_AST = astFactory.create(LT(1));
				astFactory.addASTChild(currentAST, tmp104_AST);
				match(AT);
				break;
			}
			case NCNAME:
			case LITERAL_or:
			case LITERAL_and:
			case STAR:
			case LITERAL_div:
			case LITERAL_mod:
			case LITERAL_text:
			case LITERAL_node:
			case LITERAL_child:
			case LITERAL_self:
			case LITERAL_attribute:
			case LITERAL_descendant:
			case 51:
			case 52:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 55:
			case 56:
			{
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			}
			nodeTest();
			astFactory.addASTChild(currentAST, returnAST);
			abbrevStep_AST = (AST)currentAST.root;
			break;
		}
		case PARENT:
		{
			AST tmp105_AST = null;
			tmp105_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp105_AST);
			match(PARENT);
			abbrevStep_AST = (AST)currentAST.root;
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		returnAST = abbrevStep_AST;
	}
	
	public final void kindTest() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST kindTest_AST = null;
		
		switch ( LA(1)) {
		case LITERAL_text:
		{
			textTest();
			astFactory.addASTChild(currentAST, returnAST);
			kindTest_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_node:
		{
			anyKindTest();
			astFactory.addASTChild(currentAST, returnAST);
			kindTest_AST = (AST)currentAST.root;
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		returnAST = kindTest_AST;
	}
	
	public final void nameTest() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST nameTest_AST = null;
		
			String name = null;
		
		
		boolean synPredMatched67 = false;
		if (((LA(1)==NCNAME||LA(1)==STAR))) {
			int _m67 = mark();
			synPredMatched67 = true;
			inputState.guessing++;
			try {
				{
				switch ( LA(1)) {
				case NCNAME:
				{
					{
					match(NCNAME);
					match(COLON);
					match(STAR);
					}
					break;
				}
				case STAR:
				{
					match(STAR);
					break;
				}
				default:
				{
					throw new NoViableAltException(LT(1), getFilename());
				}
				}
				}
			}
			catch (RecognitionException pe) {
				synPredMatched67 = false;
			}
			rewind(_m67);
			inputState.guessing--;
		}
		if ( synPredMatched67 ) {
			wildcard();
			astFactory.addASTChild(currentAST, returnAST);
			nameTest_AST = (AST)currentAST.root;
		}
		else if ((_tokenSet_3.member(LA(1)))) {
			name=qName();
			astFactory.addASTChild(currentAST, returnAST);
			if ( inputState.guessing==0 ) {
				nameTest_AST = (AST)currentAST.root;
				
							nameTest_AST = astFactory.create(QNAME,name);
						
				currentAST.root = nameTest_AST;
				currentAST.child = nameTest_AST!=null &&nameTest_AST.getFirstChild()!=null ?
					nameTest_AST.getFirstChild() : nameTest_AST;
				currentAST.advanceChildToEnd();
			}
			nameTest_AST = (AST)currentAST.root;
		}
		else {
			throw new NoViableAltException(LT(1), getFilename());
		}
		
		returnAST = nameTest_AST;
	}
	
	public final void wildcard() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST wildcard_AST = null;
		Token  nc1 = null;
		AST nc1_AST = null;
		Token  nc2 = null;
		AST nc2_AST = null;
		
		boolean synPredMatched70 = false;
		if (((LA(1)==STAR))) {
			int _m70 = mark();
			synPredMatched70 = true;
			inputState.guessing++;
			try {
				{
				match(STAR);
				match(COLON);
				}
			}
			catch (RecognitionException pe) {
				synPredMatched70 = false;
			}
			rewind(_m70);
			inputState.guessing--;
		}
		if ( synPredMatched70 ) {
			match(STAR);
			match(COLON);
			nc1 = LT(1);
			nc1_AST = astFactory.create(nc1);
			astFactory.addASTChild(currentAST, nc1_AST);
			match(NCNAME);
			if ( inputState.guessing==0 ) {
				wildcard_AST = (AST)currentAST.root;
				
							wildcard_AST = (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(PREFIX_WILDCARD,"*")).add(nc1_AST));
						
				currentAST.root = wildcard_AST;
				currentAST.child = wildcard_AST!=null &&wildcard_AST.getFirstChild()!=null ?
					wildcard_AST.getFirstChild() : wildcard_AST;
				currentAST.advanceChildToEnd();
			}
			wildcard_AST = (AST)currentAST.root;
		}
		else if ((LA(1)==NCNAME)) {
			nc2 = LT(1);
			nc2_AST = astFactory.create(nc2);
			astFactory.addASTChild(currentAST, nc2_AST);
			match(NCNAME);
			match(COLON);
			match(STAR);
			if ( inputState.guessing==0 ) {
				wildcard_AST = (AST)currentAST.root;
				
							wildcard_AST = (AST)astFactory.make( (new ASTArray(2)).add(nc2_AST).add(astFactory.create(WILDCARD,"*")));
						
				currentAST.root = wildcard_AST;
				currentAST.child = wildcard_AST!=null &&wildcard_AST.getFirstChild()!=null ?
					wildcard_AST.getFirstChild() : wildcard_AST;
				currentAST.advanceChildToEnd();
			}
			wildcard_AST = (AST)currentAST.root;
		}
		else if ((LA(1)==STAR)) {
			AST tmp110_AST = null;
			tmp110_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp110_AST);
			match(STAR);
			if ( inputState.guessing==0 ) {
				wildcard_AST = (AST)currentAST.root;
				
							// make this distinct from multiplication
							wildcard_AST = astFactory.create(WILDCARD,"*");
						
				currentAST.root = wildcard_AST;
				currentAST.child = wildcard_AST!=null &&wildcard_AST.getFirstChild()!=null ?
					wildcard_AST.getFirstChild() : wildcard_AST;
				currentAST.advanceChildToEnd();
			}
			wildcard_AST = (AST)currentAST.root;
		}
		else {
			throw new NoViableAltException(LT(1), getFilename());
		}
		
		returnAST = wildcard_AST;
	}
	
	public final void primaryExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST primaryExpr_AST = null;
		
		switch ( LA(1)) {
		case STRING_LITERAL:
		case DOUBLE_LITERAL:
		case DECIMAL_LITERAL:
		case INTEGER_LITERAL:
		{
			literal();
			astFactory.addASTChild(currentAST, returnAST);
			primaryExpr_AST = (AST)currentAST.root;
			break;
		}
		case NCNAME:
		case LITERAL_or:
		case LITERAL_and:
		case LITERAL_div:
		case LITERAL_mod:
		case LITERAL_text:
		case LITERAL_node:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_attribute:
		case LITERAL_descendant:
		case 51:
		case 52:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 55:
		case 56:
		{
			functionCall();
			astFactory.addASTChild(currentAST, returnAST);
			primaryExpr_AST = (AST)currentAST.root;
			break;
		}
		case SELF:
		{
			contextItemExpr();
			astFactory.addASTChild(currentAST, returnAST);
			primaryExpr_AST = (AST)currentAST.root;
			break;
		}
		case LPAREN:
		{
			parenthesizedExpr();
			astFactory.addASTChild(currentAST, returnAST);
			primaryExpr_AST = (AST)currentAST.root;
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		returnAST = primaryExpr_AST;
	}
	
	public final void functionCall() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST functionCall_AST = null;
		AST params_AST = null;
		
			String fnName = null;
		
		
		fnName=qName();
		astFactory.addASTChild(currentAST, returnAST);
		match(LPAREN);
		if ( inputState.guessing==0 ) {
			functionCall_AST = (AST)currentAST.root;
			
						functionCall_AST = astFactory.create(FUNCTION,fnName);
					
			currentAST.root = functionCall_AST;
			currentAST.child = functionCall_AST!=null &&functionCall_AST.getFirstChild()!=null ?
				functionCall_AST.getFirstChild() : functionCall_AST;
			currentAST.advanceChildToEnd();
		}
		{
		switch ( LA(1)) {
		case LPAREN:
		case NCNAME:
		case LITERAL_or:
		case LITERAL_and:
		case STRING_LITERAL:
		case PLUS:
		case MINUS:
		case STAR:
		case LITERAL_div:
		case LITERAL_mod:
		case SLASH:
		case DSLASH:
		case LITERAL_text:
		case LITERAL_node:
		case SELF:
		case AT:
		case PARENT:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_attribute:
		case LITERAL_descendant:
		case 51:
		case 52:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 55:
		case 56:
		case DOUBLE_LITERAL:
		case DECIMAL_LITERAL:
		case INTEGER_LITERAL:
		{
			functionParameters();
			params_AST = (AST)returnAST;
			astFactory.addASTChild(currentAST, returnAST);
			if ( inputState.guessing==0 ) {
				functionCall_AST = (AST)currentAST.root;
				
							functionCall_AST = (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(FUNCTION,fnName)).add(params_AST));
						
				currentAST.root = functionCall_AST;
				currentAST.child = functionCall_AST!=null &&functionCall_AST.getFirstChild()!=null ?
					functionCall_AST.getFirstChild() : functionCall_AST;
				currentAST.advanceChildToEnd();
			}
			break;
		}
		case RPAREN:
		{
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		}
		match(RPAREN);
		functionCall_AST = (AST)currentAST.root;
		returnAST = functionCall_AST;
	}
	
	public final void contextItemExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST contextItemExpr_AST = null;
		
		AST tmp113_AST = null;
		tmp113_AST = astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp113_AST);
		match(SELF);
		contextItemExpr_AST = (AST)currentAST.root;
		returnAST = contextItemExpr_AST;
	}
	
	public final void parenthesizedExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST parenthesizedExpr_AST = null;
		AST e_AST = null;
		
		match(LPAREN);
		expr();
		e_AST = (AST)returnAST;
		astFactory.addASTChild(currentAST, returnAST);
		match(RPAREN);
		if ( inputState.guessing==0 ) {
			parenthesizedExpr_AST = (AST)currentAST.root;
			
						parenthesizedExpr_AST = (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(PARENTHESIZED,"Parenthesized")).add(e_AST));
					
			currentAST.root = parenthesizedExpr_AST;
			currentAST.child = parenthesizedExpr_AST!=null &&parenthesizedExpr_AST.getFirstChild()!=null ?
				parenthesizedExpr_AST.getFirstChild() : parenthesizedExpr_AST;
			currentAST.advanceChildToEnd();
		}
		parenthesizedExpr_AST = (AST)currentAST.root;
		returnAST = parenthesizedExpr_AST;
	}
	
	public final void numericLiteral() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST numericLiteral_AST = null;
		
		switch ( LA(1)) {
		case DOUBLE_LITERAL:
		{
			AST tmp116_AST = null;
			tmp116_AST = astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp116_AST);
			match(DOUBLE_LITERAL);
			numericLiteral_AST = (AST)currentAST.root;
			break;
		}
		case DECIMAL_LITERAL:
		{
			AST tmp117_AST = null;
			tmp117_AST = astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp117_AST);
			match(DECIMAL_LITERAL);
			numericLiteral_AST = (AST)currentAST.root;
			break;
		}
		case INTEGER_LITERAL:
		{
			AST tmp118_AST = null;
			tmp118_AST = astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp118_AST);
			match(INTEGER_LITERAL);
			numericLiteral_AST = (AST)currentAST.root;
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		returnAST = numericLiteral_AST;
	}
	
	public final void functionParameters() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST functionParameters_AST = null;
		
		expr();
		astFactory.addASTChild(currentAST, returnAST);
		{
		_loop80:
		do {
			if ((LA(1)==COMMA)) {
				match(COMMA);
				expr();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop80;
			}
			
		} while (true);
		}
		functionParameters_AST = (AST)currentAST.root;
		returnAST = functionParameters_AST;
	}
	
	public final void textTest() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST textTest_AST = null;
		
		AST tmp120_AST = null;
		tmp120_AST = astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp120_AST);
		match(LITERAL_text);
		match(LPAREN);
		match(RPAREN);
		textTest_AST = (AST)currentAST.root;
		returnAST = textTest_AST;
	}
	
	public final void anyKindTest() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST anyKindTest_AST = null;
		
		AST tmp123_AST = null;
		tmp123_AST = astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp123_AST);
		match(LITERAL_node);
		match(LPAREN);
		match(RPAREN);
		anyKindTest_AST = (AST)currentAST.root;
		returnAST = anyKindTest_AST;
	}
	
	public final String  ncnameOrKeyword() throws RecognitionException, TokenStreamException {
		String name;
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST ncnameOrKeyword_AST = null;
		Token  n1 = null;
		AST n1_AST = null;
		
			name = null;
		
		
		switch ( LA(1)) {
		case NCNAME:
		{
			n1 = LT(1);
			n1_AST = astFactory.create(n1);
			astFactory.addASTChild(currentAST, n1_AST);
			match(NCNAME);
			if ( inputState.guessing==0 ) {
				name = n1.getText();
			}
			ncnameOrKeyword_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_or:
		case LITERAL_and:
		case LITERAL_div:
		case LITERAL_mod:
		case LITERAL_text:
		case LITERAL_node:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_attribute:
		case LITERAL_descendant:
		case 51:
		case 52:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 55:
		case 56:
		{
			name=reservedKeywords();
			astFactory.addASTChild(currentAST, returnAST);
			ncnameOrKeyword_AST = (AST)currentAST.root;
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		returnAST = ncnameOrKeyword_AST;
		return name;
	}
	
	public final String  reservedKeywords() throws RecognitionException, TokenStreamException {
		String name;
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST reservedKeywords_AST = null;
		
			name = null;
		
		
		switch ( LA(1)) {
		case LITERAL_div:
		{
			AST tmp126_AST = null;
			tmp126_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp126_AST);
			match(LITERAL_div);
			if ( inputState.guessing==0 ) {
				name = "div";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_mod:
		{
			AST tmp127_AST = null;
			tmp127_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp127_AST);
			match(LITERAL_mod);
			if ( inputState.guessing==0 ) {
				name = "mod";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_text:
		{
			AST tmp128_AST = null;
			tmp128_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp128_AST);
			match(LITERAL_text);
			if ( inputState.guessing==0 ) {
				name = "text";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_node:
		{
			AST tmp129_AST = null;
			tmp129_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp129_AST);
			match(LITERAL_node);
			if ( inputState.guessing==0 ) {
				name = "node";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_or:
		{
			AST tmp130_AST = null;
			tmp130_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp130_AST);
			match(LITERAL_or);
			if ( inputState.guessing==0 ) {
				name = "or";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_and:
		{
			AST tmp131_AST = null;
			tmp131_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp131_AST);
			match(LITERAL_and);
			if ( inputState.guessing==0 ) {
				name = "and";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_child:
		{
			AST tmp132_AST = null;
			tmp132_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp132_AST);
			match(LITERAL_child);
			if ( inputState.guessing==0 ) {
				name = "child";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_parent:
		{
			AST tmp133_AST = null;
			tmp133_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp133_AST);
			match(LITERAL_parent);
			if ( inputState.guessing==0 ) {
				name = "parent";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_self:
		{
			AST tmp134_AST = null;
			tmp134_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp134_AST);
			match(LITERAL_self);
			if ( inputState.guessing==0 ) {
				name = "self";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_attribute:
		{
			AST tmp135_AST = null;
			tmp135_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp135_AST);
			match(LITERAL_attribute);
			if ( inputState.guessing==0 ) {
				name = "attribute";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_ancestor:
		{
			AST tmp136_AST = null;
			tmp136_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp136_AST);
			match(LITERAL_ancestor);
			if ( inputState.guessing==0 ) {
				name = "ancestor";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_descendant:
		{
			AST tmp137_AST = null;
			tmp137_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp137_AST);
			match(LITERAL_descendant);
			if ( inputState.guessing==0 ) {
				name = "descendant";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case 51:
		{
			AST tmp138_AST = null;
			tmp138_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp138_AST);
			match(51);
			if ( inputState.guessing==0 ) {
				name = "descendant-or-self";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case 55:
		{
			AST tmp139_AST = null;
			tmp139_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp139_AST);
			match(55);
			if ( inputState.guessing==0 ) {
				name = "ancestor-or-self";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case 56:
		{
			AST tmp140_AST = null;
			tmp140_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp140_AST);
			match(56);
			if ( inputState.guessing==0 ) {
				name = "preceding-sibling";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case 52:
		{
			AST tmp141_AST = null;
			tmp141_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp141_AST);
			match(52);
			if ( inputState.guessing==0 ) {
				name = "following-sibling";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		returnAST = reservedKeywords_AST;
		return name;
	}
	
	
	public static final String[] _tokenNames = {
		"<0>",
		"EOF",
		"<2>",
		"NULL_TREE_LOOKAHEAD",
		"QNAME",
		"PREDICATE",
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
		"\"xpointer\"",
		"LPAREN",
		"RPAREN",
		"NCNAME",
		"\"or\"",
		"\"and\"",
		"EQ",
		"NEQ",
		"GT",
		"GTEQ",
		"LT",
		"LTEQ",
		"ANDEQ",
		"OREQ",
		"STRING_LITERAL",
		"PLUS",
		"MINUS",
		"STAR",
		"\"div\"",
		"\"mod\"",
		"UNION",
		"SLASH",
		"DSLASH",
		"\"text\"",
		"\"node\"",
		"SELF",
		"LPPAREN",
		"RPPAREN",
		"COLON",
		"AT",
		"PARENT",
		"\"child\"",
		"\"self\"",
		"\"attribute\"",
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
		"COMMA",
		"BASECHAR",
		"IDEOGRAPHIC",
		"DIGIT",
		"DIGITS",
		"NMSTART",
		"NMCHAR",
		"WS",
		"INTEGER_DECIMAL_PARENT",
		"VARIABLE"
	};
	
	protected void buildTokenTypeASTClassMap() {
		tokenTypeToASTClassMap=null;
	};
	
	private static final long[] mk_tokenSet_0() {
		long[] data = { 144081713104420864L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_0 = new BitSet(mk_tokenSet_0());
	private static final long[] mk_tokenSet_1() {
		long[] data = { 1152784668026339328L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_1 = new BitSet(mk_tokenSet_1());
	private static final long[] mk_tokenSet_2() {
		long[] data = { 143976159988154368L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_2 = new BitSet(mk_tokenSet_2());
	private static final long[] mk_tokenSet_3() {
		long[] data = { 143976151398219776L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_3 = new BitSet(mk_tokenSet_3());
	
	}
