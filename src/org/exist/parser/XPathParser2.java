// $ANTLR 2.7.2: "XPathParser2.g" -> "XPathParser2.java"$

	package org.exist.parser;

	import antlr.debug.misc.*;
	import java.io.StringReader;
	import java.io.BufferedReader;
	import java.io.InputStreamReader;
	import java.util.ArrayList;
	import java.util.List;
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
	import org.exist.xpath.value.*;
	import org.exist.xpath.functions.*;

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

	protected ArrayList exceptions= new ArrayList(2);
	protected boolean foundError= false;
	protected XPathLexer2 lexer;

	public XPathParser2(XPathLexer2 lexer, boolean dummy) {
		this((TokenStream) lexer);
		this.lexer= lexer;
	}

	public boolean foundErrors() {
		return foundError;
	}

	public String getErrorMessage() {
		StringBuffer buf= new StringBuffer();
		for (Iterator i= exceptions.iterator(); i.hasNext();) {
			buf.append(((Exception) i.next()).toString());
			buf.append('\n');
		}
		return buf.toString();
	}

	protected void handleException(Exception e) {
		foundError= true;
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
		
		AST tmp58_AST = null;
		tmp58_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp58_AST);
		match(QNAME);
		AST tmp59_AST = null;
		tmp59_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp59_AST);
		match(PREDICATE);
		AST tmp60_AST = null;
		tmp60_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp60_AST);
		match(FLWOR);
		AST tmp61_AST = null;
		tmp61_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp61_AST);
		match(PARENTHESIZED);
		AST tmp62_AST = null;
		tmp62_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp62_AST);
		match(ABSOLUTE_SLASH);
		AST tmp63_AST = null;
		tmp63_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp63_AST);
		match(ABSOLUTE_DSLASH);
		AST tmp64_AST = null;
		tmp64_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp64_AST);
		match(WILDCARD);
		AST tmp65_AST = null;
		tmp65_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp65_AST);
		match(PREFIX_WILDCARD);
		AST tmp66_AST = null;
		tmp66_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp66_AST);
		match(FUNCTION);
		AST tmp67_AST = null;
		tmp67_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp67_AST);
		match(UNARY_MINUS);
		AST tmp68_AST = null;
		tmp68_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp68_AST);
		match(UNARY_PLUS);
		AST tmp69_AST = null;
		tmp69_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp69_AST);
		match(XPOINTER);
		AST tmp70_AST = null;
		tmp70_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp70_AST);
		match(XPOINTER_ID);
		AST tmp71_AST = null;
		tmp71_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp71_AST);
		match(VARIABLE_REF);
		AST tmp72_AST = null;
		tmp72_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp72_AST);
		match(VARIABLE_BINDING);
		AST tmp73_AST = null;
		tmp73_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp73_AST);
		match(ELEMENT);
		AST tmp74_AST = null;
		tmp74_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp74_AST);
		match(ATTRIBUTE);
		AST tmp75_AST = null;
		tmp75_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp75_AST);
		match(TEXT);
		AST tmp76_AST = null;
		tmp76_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp76_AST);
		match(VERSION_DECL);
		AST tmp77_AST = null;
		tmp77_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp77_AST);
		match(NAMESPACE_DECL);
		AST tmp78_AST = null;
		tmp78_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp78_AST);
		match(DEF_NAMESPACE_DECL);
		AST tmp79_AST = null;
		tmp79_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp79_AST);
		match(DEF_FUNCTION_NS_DECL);
		AST tmp80_AST = null;
		tmp80_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp80_AST);
		match(GLOBAL_VAR);
		AST tmp81_AST = null;
		tmp81_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp81_AST);
		match(FUNCTION_DECL);
		AST tmp82_AST = null;
		tmp82_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp82_AST);
		match(PROLOG);
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
			AST tmp83_AST = null;
			tmp83_AST = astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp83_AST);
			match(LITERAL_xpointer);
			match(LPAREN);
			expr();
			ex_AST = (AST)returnAST;
			astFactory.addASTChild(currentAST, returnAST);
			match(RPAREN);
			if ( inputState.guessing==0 ) {
				xpointer_AST = (AST)currentAST.root;
				xpointer_AST= (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(XPOINTER,"xpointer")).add(ex_AST));
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
				xpointer_AST= (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(XPOINTER_ID,"id")).add(nc_AST));
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
		
		exprSingle();
		astFactory.addASTChild(currentAST, returnAST);
		{
		_loop42:
		do {
			if ((LA(1)==COMMA)) {
				AST tmp86_AST = null;
				tmp86_AST = astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp86_AST);
				match(COMMA);
				exprSingle();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop42;
			}
			
		} while (true);
		}
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
			case LITERAL_declare:
			case LITERAL_xquery:
			case STRING_LITERAL:
			case DOLLAR:
			case STAR:
			case PLUS:
			case LITERAL_item:
			case LITERAL_for:
			case LITERAL_let:
			case LITERAL_if:
			case LITERAL_or:
			case LITERAL_and:
			case LT:
			case MINUS:
			case LITERAL_div:
			case LITERAL_mod:
			case SLASH:
			case DSLASH:
			case LITERAL_text:
			case LITERAL_node:
			case SELF:
			case XML_COMMENT:
			case AT:
			case PARENT:
			case LITERAL_child:
			case LITERAL_self:
			case LITERAL_attribute:
			case LITERAL_descendant:
			case 91:
			case 92:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 95:
			case 96:
			case DOUBLE_LITERAL:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
			case XML_PI:
			{
				module();
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
			AST tmp87_AST = null;
			tmp87_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp87_AST);
			match(Token.EOF_TYPE);
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
	
	public final void module() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST module_AST = null;
		
		mainModule();
		astFactory.addASTChild(currentAST, returnAST);
		module_AST = (AST)currentAST.root;
		returnAST = module_AST;
	}
	
	public final void mainModule() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST mainModule_AST = null;
		
		prolog();
		astFactory.addASTChild(currentAST, returnAST);
		queryBody();
		astFactory.addASTChild(currentAST, returnAST);
		mainModule_AST = (AST)currentAST.root;
		returnAST = mainModule_AST;
	}
	
	public final void prolog() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST prolog_AST = null;
		
		{
		switch ( LA(1)) {
		case LITERAL_xquery:
		{
			version();
			astFactory.addASTChild(currentAST, returnAST);
			match(SEMICOLON);
			break;
		}
		case LPAREN:
		case NCNAME:
		case LITERAL_declare:
		case STRING_LITERAL:
		case DOLLAR:
		case STAR:
		case PLUS:
		case LITERAL_item:
		case LITERAL_for:
		case LITERAL_let:
		case LITERAL_if:
		case LITERAL_or:
		case LITERAL_and:
		case LT:
		case MINUS:
		case LITERAL_div:
		case LITERAL_mod:
		case SLASH:
		case DSLASH:
		case LITERAL_text:
		case LITERAL_node:
		case SELF:
		case XML_COMMENT:
		case AT:
		case PARENT:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_attribute:
		case LITERAL_descendant:
		case 91:
		case 92:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 95:
		case 96:
		case DOUBLE_LITERAL:
		case DECIMAL_LITERAL:
		case INTEGER_LITERAL:
		case XML_PI:
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
		_loop17:
		do {
			if ((LA(1)==LITERAL_declare)) {
				{
				boolean synPredMatched12 = false;
				if (((LA(1)==LITERAL_declare))) {
					int _m12 = mark();
					synPredMatched12 = true;
					inputState.guessing++;
					try {
						{
						match(LITERAL_declare);
						match(LITERAL_namespace);
						}
					}
					catch (RecognitionException pe) {
						synPredMatched12 = false;
					}
					rewind(_m12);
					inputState.guessing--;
				}
				if ( synPredMatched12 ) {
					namespaceDecl();
					astFactory.addASTChild(currentAST, returnAST);
				}
				else {
					boolean synPredMatched14 = false;
					if (((LA(1)==LITERAL_declare))) {
						int _m14 = mark();
						synPredMatched14 = true;
						inputState.guessing++;
						try {
							{
							match(LITERAL_declare);
							match(LITERAL_default);
							}
						}
						catch (RecognitionException pe) {
							synPredMatched14 = false;
						}
						rewind(_m14);
						inputState.guessing--;
					}
					if ( synPredMatched14 ) {
						defaultNamespaceDecl();
						astFactory.addASTChild(currentAST, returnAST);
					}
					else {
						boolean synPredMatched16 = false;
						if (((LA(1)==LITERAL_declare))) {
							int _m16 = mark();
							synPredMatched16 = true;
							inputState.guessing++;
							try {
								{
								match(LITERAL_declare);
								match(LITERAL_function);
								}
							}
							catch (RecognitionException pe) {
								synPredMatched16 = false;
							}
							rewind(_m16);
							inputState.guessing--;
						}
						if ( synPredMatched16 ) {
							functionDecl();
							astFactory.addASTChild(currentAST, returnAST);
						}
						else if ((LA(1)==LITERAL_declare)) {
							varDecl();
							astFactory.addASTChild(currentAST, returnAST);
						}
						else {
							throw new NoViableAltException(LT(1), getFilename());
						}
						}}
						}
						match(SEMICOLON);
					}
					else {
						break _loop17;
					}
					
				} while (true);
				}
				prolog_AST = (AST)currentAST.root;
				returnAST = prolog_AST;
			}
			
	public final void queryBody() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST queryBody_AST = null;
		
		expr();
		astFactory.addASTChild(currentAST, returnAST);
		queryBody_AST = (AST)currentAST.root;
		returnAST = queryBody_AST;
	}
	
	public final void version() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST version_AST = null;
		Token  v = null;
		AST v_AST = null;
		
		AST tmp90_AST = null;
		tmp90_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp90_AST);
		match(LITERAL_xquery);
		AST tmp91_AST = null;
		tmp91_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp91_AST);
		match(LITERAL_version);
		v = LT(1);
		v_AST = astFactory.create(v);
		astFactory.addASTChild(currentAST, v_AST);
		match(STRING_LITERAL);
		if ( inputState.guessing==0 ) {
			version_AST = (AST)currentAST.root;
			
					version_AST = (AST)astFactory.make( (new ASTArray(1)).add(astFactory.create(VERSION_DECL,v.getText())));
				
			currentAST.root = version_AST;
			currentAST.child = version_AST!=null &&version_AST.getFirstChild()!=null ?
				version_AST.getFirstChild() : version_AST;
			currentAST.advanceChildToEnd();
		}
		version_AST = (AST)currentAST.root;
		returnAST = version_AST;
	}
	
	public final void namespaceDecl() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST namespaceDecl_AST = null;
		Token  prefix = null;
		AST prefix_AST = null;
		Token  uri = null;
		AST uri_AST = null;
		
		AST tmp92_AST = null;
		tmp92_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp92_AST);
		match(LITERAL_declare);
		AST tmp93_AST = null;
		tmp93_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp93_AST);
		match(LITERAL_namespace);
		prefix = LT(1);
		prefix_AST = astFactory.create(prefix);
		astFactory.addASTChild(currentAST, prefix_AST);
		match(NCNAME);
		match(EQ);
		uri = LT(1);
		uri_AST = astFactory.create(uri);
		astFactory.addASTChild(currentAST, uri_AST);
		match(STRING_LITERAL);
		if ( inputState.guessing==0 ) {
			namespaceDecl_AST = (AST)currentAST.root;
			
					namespaceDecl_AST = (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(NAMESPACE_DECL,prefix.getText())).add(uri_AST));
				
			currentAST.root = namespaceDecl_AST;
			currentAST.child = namespaceDecl_AST!=null &&namespaceDecl_AST.getFirstChild()!=null ?
				namespaceDecl_AST.getFirstChild() : namespaceDecl_AST;
			currentAST.advanceChildToEnd();
		}
		namespaceDecl_AST = (AST)currentAST.root;
		returnAST = namespaceDecl_AST;
	}
	
	public final void defaultNamespaceDecl() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST defaultNamespaceDecl_AST = null;
		Token  defu = null;
		AST defu_AST = null;
		Token  deff = null;
		AST deff_AST = null;
		
		AST tmp95_AST = null;
		tmp95_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp95_AST);
		match(LITERAL_declare);
		AST tmp96_AST = null;
		tmp96_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp96_AST);
		match(LITERAL_default);
		{
		switch ( LA(1)) {
		case LITERAL_element:
		{
			AST tmp97_AST = null;
			tmp97_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp97_AST);
			match(LITERAL_element);
			AST tmp98_AST = null;
			tmp98_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp98_AST);
			match(LITERAL_namespace);
			defu = LT(1);
			defu_AST = astFactory.create(defu);
			astFactory.addASTChild(currentAST, defu_AST);
			match(STRING_LITERAL);
			if ( inputState.guessing==0 ) {
				defaultNamespaceDecl_AST = (AST)currentAST.root;
				
							defaultNamespaceDecl_AST = (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(DEF_NAMESPACE_DECL,"defaultNamespaceDecl")).add(defu_AST));
						
				currentAST.root = defaultNamespaceDecl_AST;
				currentAST.child = defaultNamespaceDecl_AST!=null &&defaultNamespaceDecl_AST.getFirstChild()!=null ?
					defaultNamespaceDecl_AST.getFirstChild() : defaultNamespaceDecl_AST;
				currentAST.advanceChildToEnd();
			}
			break;
		}
		case LITERAL_function:
		{
			AST tmp99_AST = null;
			tmp99_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp99_AST);
			match(LITERAL_function);
			AST tmp100_AST = null;
			tmp100_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp100_AST);
			match(LITERAL_namespace);
			deff = LT(1);
			deff_AST = astFactory.create(deff);
			astFactory.addASTChild(currentAST, deff_AST);
			match(STRING_LITERAL);
			if ( inputState.guessing==0 ) {
				defaultNamespaceDecl_AST = (AST)currentAST.root;
				
							defaultNamespaceDecl_AST = (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(DEF_FUNCTION_NS_DECL,"defaultFunctionNSDecl")).add(deff_AST));
						
				currentAST.root = defaultNamespaceDecl_AST;
				currentAST.child = defaultNamespaceDecl_AST!=null &&defaultNamespaceDecl_AST.getFirstChild()!=null ?
					defaultNamespaceDecl_AST.getFirstChild() : defaultNamespaceDecl_AST;
				currentAST.advanceChildToEnd();
			}
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		}
		defaultNamespaceDecl_AST = (AST)currentAST.root;
		returnAST = defaultNamespaceDecl_AST;
	}
	
	public final void functionDecl() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST functionDecl_AST = null;
		AST params_AST = null;
		AST body_AST = null;
		String name = null;
		
		match(LITERAL_declare);
		match(LITERAL_function);
		name=qName();
		astFactory.addASTChild(currentAST, returnAST);
		if ( inputState.guessing==0 ) {
			functionDecl_AST = (AST)currentAST.root;
			
					functionDecl_AST = (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(FUNCTION_DECL,name)).add(body_AST));
				
			currentAST.root = functionDecl_AST;
			currentAST.child = functionDecl_AST!=null &&functionDecl_AST.getFirstChild()!=null ?
				functionDecl_AST.getFirstChild() : functionDecl_AST;
			currentAST.advanceChildToEnd();
		}
		match(LPAREN);
		{
		switch ( LA(1)) {
		case DOLLAR:
		{
			paramList();
			params_AST = (AST)returnAST;
			astFactory.addASTChild(currentAST, returnAST);
			if ( inputState.guessing==0 ) {
				functionDecl_AST = (AST)currentAST.root;
				
							functionDecl_AST = (AST)astFactory.make( (new ASTArray(2)).add(functionDecl_AST).add(params_AST));
						
				currentAST.root = functionDecl_AST;
				currentAST.child = functionDecl_AST!=null &&functionDecl_AST.getFirstChild()!=null ?
					functionDecl_AST.getFirstChild() : functionDecl_AST;
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
		functionBody();
		body_AST = (AST)returnAST;
		astFactory.addASTChild(currentAST, returnAST);
		functionDecl_AST = (AST)currentAST.root;
		returnAST = functionDecl_AST;
	}
	
	public final void varDecl() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST varDecl_AST = null;
		AST ex_AST = null;
		String varName = null;
		
		AST tmp105_AST = null;
		tmp105_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp105_AST);
		match(LITERAL_declare);
		AST tmp106_AST = null;
		tmp106_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp106_AST);
		match(LITERAL_variable);
		match(DOLLAR);
		varName=qName();
		astFactory.addASTChild(currentAST, returnAST);
		AST tmp108_AST = null;
		tmp108_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp108_AST);
		match(LCURLY);
		expr();
		ex_AST = (AST)returnAST;
		astFactory.addASTChild(currentAST, returnAST);
		AST tmp109_AST = null;
		tmp109_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp109_AST);
		match(RCURLY);
		if ( inputState.guessing==0 ) {
			varDecl_AST = (AST)currentAST.root;
			
					varDecl_AST = (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(GLOBAL_VAR,varName)).add(ex_AST));
				
			currentAST.root = varDecl_AST;
			currentAST.child = varDecl_AST!=null &&varDecl_AST.getFirstChild()!=null ?
				varDecl_AST.getFirstChild() : varDecl_AST;
			currentAST.advanceChildToEnd();
		}
		varDecl_AST = (AST)currentAST.root;
		returnAST = varDecl_AST;
	}
	
	public final String  qName() throws RecognitionException, TokenStreamException {
		String name;
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST qName_AST = null;
		
			name= null;
			String name2;
		
		
		boolean synPredMatched141 = false;
		if (((_tokenSet_0.member(LA(1))))) {
			int _m141 = mark();
			synPredMatched141 = true;
			inputState.guessing++;
			try {
				{
				ncnameOrKeyword();
				match(COLON);
				ncnameOrKeyword();
				}
			}
			catch (RecognitionException pe) {
				synPredMatched141 = false;
			}
			rewind(_m141);
			inputState.guessing--;
		}
		if ( synPredMatched141 ) {
			name=ncnameOrKeyword();
			astFactory.addASTChild(currentAST, returnAST);
			AST tmp110_AST = null;
			tmp110_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp110_AST);
			match(COLON);
			name2=ncnameOrKeyword();
			astFactory.addASTChild(currentAST, returnAST);
			if ( inputState.guessing==0 ) {
				name= name + ':' + name2;
			}
			qName_AST = (AST)currentAST.root;
		}
		else if ((_tokenSet_0.member(LA(1)))) {
			name=ncnameOrKeyword();
			astFactory.addASTChild(currentAST, returnAST);
			qName_AST = (AST)currentAST.root;
		}
		else {
			throw new NoViableAltException(LT(1), getFilename());
		}
		
		returnAST = qName_AST;
		return name;
	}
	
	public final void paramList() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST paramList_AST = null;
		
		param();
		astFactory.addASTChild(currentAST, returnAST);
		{
		_loop28:
		do {
			if ((LA(1)==COMMA)) {
				match(COMMA);
				param();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop28;
			}
			
		} while (true);
		}
		paramList_AST = (AST)currentAST.root;
		returnAST = paramList_AST;
	}
	
	public final void functionBody() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST functionBody_AST = null;
		
		match(LCURLY);
		expr();
		astFactory.addASTChild(currentAST, returnAST);
		match(RCURLY);
		functionBody_AST = (AST)currentAST.root;
		returnAST = functionBody_AST;
	}
	
	public final void param() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST param_AST = null;
		AST t_AST = null;
		String varName = null;
		
		match(DOLLAR);
		varName=qName();
		astFactory.addASTChild(currentAST, returnAST);
		if ( inputState.guessing==0 ) {
			param_AST = (AST)currentAST.root;
			
					param_AST = (AST)astFactory.make( (new ASTArray(1)).add(astFactory.create(VARIABLE_BINDING,varName)));
				
			currentAST.root = param_AST;
			currentAST.child = param_AST!=null &&param_AST.getFirstChild()!=null ?
				param_AST.getFirstChild() : param_AST;
			currentAST.advanceChildToEnd();
		}
		{
		switch ( LA(1)) {
		case LITERAL_as:
		{
			typeDeclaration();
			t_AST = (AST)returnAST;
			astFactory.addASTChild(currentAST, returnAST);
			if ( inputState.guessing==0 ) {
				param_AST = (AST)currentAST.root;
				
							param_AST = (AST)astFactory.make( (new ASTArray(2)).add(param_AST).add(t_AST));
						
				currentAST.root = param_AST;
				currentAST.child = param_AST!=null &&param_AST.getFirstChild()!=null ?
					param_AST.getFirstChild() : param_AST;
				currentAST.advanceChildToEnd();
			}
			break;
		}
		case RPAREN:
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
		param_AST = (AST)currentAST.root;
		returnAST = param_AST;
	}
	
	public final void typeDeclaration() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST typeDeclaration_AST = null;
		
		AST tmp115_AST = null;
		tmp115_AST = astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp115_AST);
		match(LITERAL_as);
		sequenceType();
		astFactory.addASTChild(currentAST, returnAST);
		typeDeclaration_AST = (AST)currentAST.root;
		returnAST = typeDeclaration_AST;
	}
	
	public final void sequenceType() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST sequenceType_AST = null;
		
		switch ( LA(1)) {
		case NCNAME:
		case LITERAL_item:
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
		case 91:
		case 92:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 95:
		case 96:
		{
			itemType();
			astFactory.addASTChild(currentAST, returnAST);
			{
			switch ( LA(1)) {
			case 50:
			case STAR:
			case PLUS:
			{
				occurrenceIndicator();
				astFactory.addASTChild(currentAST, returnAST);
				break;
			}
			case RPAREN:
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
			sequenceType_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_empty:
		{
			AST tmp116_AST = null;
			tmp116_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp116_AST);
			match(LITERAL_empty);
			match(LPAREN);
			match(RPAREN);
			sequenceType_AST = (AST)currentAST.root;
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		returnAST = sequenceType_AST;
	}
	
	public final void itemType() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST itemType_AST = null;
		
		boolean synPredMatched37 = false;
		if (((LA(1)==LITERAL_item))) {
			int _m37 = mark();
			synPredMatched37 = true;
			inputState.guessing++;
			try {
				{
				match(LITERAL_item);
				match(LPAREN);
				}
			}
			catch (RecognitionException pe) {
				synPredMatched37 = false;
			}
			rewind(_m37);
			inputState.guessing--;
		}
		if ( synPredMatched37 ) {
			AST tmp119_AST = null;
			tmp119_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp119_AST);
			match(LITERAL_item);
			match(LPAREN);
			match(RPAREN);
			itemType_AST = (AST)currentAST.root;
		}
		else if ((_tokenSet_0.member(LA(1)))) {
			atomicType();
			astFactory.addASTChild(currentAST, returnAST);
			itemType_AST = (AST)currentAST.root;
		}
		else {
			throw new NoViableAltException(LT(1), getFilename());
		}
		
		returnAST = itemType_AST;
	}
	
	public final void occurrenceIndicator() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST occurrenceIndicator_AST = null;
		
		switch ( LA(1)) {
		case 50:
		{
			AST tmp122_AST = null;
			tmp122_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp122_AST);
			match(50);
			occurrenceIndicator_AST = (AST)currentAST.root;
			break;
		}
		case STAR:
		{
			AST tmp123_AST = null;
			tmp123_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp123_AST);
			match(STAR);
			occurrenceIndicator_AST = (AST)currentAST.root;
			break;
		}
		case PLUS:
		{
			AST tmp124_AST = null;
			tmp124_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp124_AST);
			match(PLUS);
			occurrenceIndicator_AST = (AST)currentAST.root;
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		returnAST = occurrenceIndicator_AST;
	}
	
	public final void atomicType() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST atomicType_AST = null;
		
		qName();
		astFactory.addASTChild(currentAST, returnAST);
		atomicType_AST = (AST)currentAST.root;
		returnAST = atomicType_AST;
	}
	
	public final void exprSingle() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST exprSingle_AST = null;
		
		switch ( LA(1)) {
		case LPAREN:
		case NCNAME:
		case STRING_LITERAL:
		case DOLLAR:
		case STAR:
		case PLUS:
		case LITERAL_item:
		case LITERAL_or:
		case LITERAL_and:
		case LT:
		case MINUS:
		case LITERAL_div:
		case LITERAL_mod:
		case SLASH:
		case DSLASH:
		case LITERAL_text:
		case LITERAL_node:
		case SELF:
		case XML_COMMENT:
		case AT:
		case PARENT:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_attribute:
		case LITERAL_descendant:
		case 91:
		case 92:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 95:
		case 96:
		case DOUBLE_LITERAL:
		case DECIMAL_LITERAL:
		case INTEGER_LITERAL:
		case XML_PI:
		{
			orExpr();
			astFactory.addASTChild(currentAST, returnAST);
			exprSingle_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_for:
		case LITERAL_let:
		{
			flworExpr();
			astFactory.addASTChild(currentAST, returnAST);
			exprSingle_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_if:
		{
			ifExpr();
			astFactory.addASTChild(currentAST, returnAST);
			exprSingle_AST = (AST)currentAST.root;
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		returnAST = exprSingle_AST;
	}
	
	public final void orExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST orExpr_AST = null;
		
		andExpr();
		astFactory.addASTChild(currentAST, returnAST);
		{
		_loop59:
		do {
			if ((LA(1)==LITERAL_or)) {
				AST tmp125_AST = null;
				tmp125_AST = astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp125_AST);
				match(LITERAL_or);
				andExpr();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop59;
			}
			
		} while (true);
		}
		orExpr_AST = (AST)currentAST.root;
		returnAST = orExpr_AST;
	}
	
	public final void flworExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST flworExpr_AST = null;
		
		{
		int _cnt46=0;
		_loop46:
		do {
			switch ( LA(1)) {
			case LITERAL_for:
			{
				forClause();
				astFactory.addASTChild(currentAST, returnAST);
				break;
			}
			case LITERAL_let:
			{
				letClause();
				astFactory.addASTChild(currentAST, returnAST);
				break;
			}
			default:
			{
				if ( _cnt46>=1 ) { break _loop46; } else {throw new NoViableAltException(LT(1), getFilename());}
			}
			}
			_cnt46++;
		} while (true);
		}
		{
		switch ( LA(1)) {
		case LITERAL_where:
		{
			AST tmp126_AST = null;
			tmp126_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp126_AST);
			match(LITERAL_where);
			expr();
			astFactory.addASTChild(currentAST, returnAST);
			break;
		}
		case LITERAL_return:
		{
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		}
		AST tmp127_AST = null;
		tmp127_AST = astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp127_AST);
		match(LITERAL_return);
		exprSingle();
		astFactory.addASTChild(currentAST, returnAST);
		flworExpr_AST = (AST)currentAST.root;
		returnAST = flworExpr_AST;
	}
	
	public final void ifExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST ifExpr_AST = null;
		
		AST tmp128_AST = null;
		tmp128_AST = astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp128_AST);
		match(LITERAL_if);
		match(LPAREN);
		expr();
		astFactory.addASTChild(currentAST, returnAST);
		match(RPAREN);
		match(LITERAL_then);
		exprSingle();
		astFactory.addASTChild(currentAST, returnAST);
		match(LITERAL_else);
		exprSingle();
		astFactory.addASTChild(currentAST, returnAST);
		ifExpr_AST = (AST)currentAST.root;
		returnAST = ifExpr_AST;
	}
	
	public final void forClause() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST forClause_AST = null;
		
		AST tmp133_AST = null;
		tmp133_AST = astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp133_AST);
		match(LITERAL_for);
		inVarBinding();
		astFactory.addASTChild(currentAST, returnAST);
		{
		_loop50:
		do {
			if ((LA(1)==COMMA)) {
				match(COMMA);
				inVarBinding();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop50;
			}
			
		} while (true);
		}
		forClause_AST = (AST)currentAST.root;
		returnAST = forClause_AST;
	}
	
	public final void letClause() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST letClause_AST = null;
		
		AST tmp135_AST = null;
		tmp135_AST = astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp135_AST);
		match(LITERAL_let);
		letVarBinding();
		astFactory.addASTChild(currentAST, returnAST);
		{
		_loop53:
		do {
			if ((LA(1)==COMMA)) {
				match(COMMA);
				letVarBinding();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop53;
			}
			
		} while (true);
		}
		letClause_AST = (AST)currentAST.root;
		returnAST = letClause_AST;
	}
	
	public final void inVarBinding() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST inVarBinding_AST = null;
		AST expr_AST = null;
		String varName;
		
		match(DOLLAR);
		varName=qName();
		astFactory.addASTChild(currentAST, returnAST);
		AST tmp138_AST = null;
		tmp138_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp138_AST);
		match(LITERAL_in);
		exprSingle();
		expr_AST = (AST)returnAST;
		astFactory.addASTChild(currentAST, returnAST);
		if ( inputState.guessing==0 ) {
			inVarBinding_AST = (AST)currentAST.root;
			inVarBinding_AST= (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(VARIABLE_BINDING,varName)).add(expr_AST));
			currentAST.root = inVarBinding_AST;
			currentAST.child = inVarBinding_AST!=null &&inVarBinding_AST.getFirstChild()!=null ?
				inVarBinding_AST.getFirstChild() : inVarBinding_AST;
			currentAST.advanceChildToEnd();
		}
		inVarBinding_AST = (AST)currentAST.root;
		returnAST = inVarBinding_AST;
	}
	
	public final void letVarBinding() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST letVarBinding_AST = null;
		AST expr_AST = null;
		String varName;
		
		match(DOLLAR);
		varName=qName();
		astFactory.addASTChild(currentAST, returnAST);
		match(COLON);
		match(EQ);
		exprSingle();
		expr_AST = (AST)returnAST;
		astFactory.addASTChild(currentAST, returnAST);
		if ( inputState.guessing==0 ) {
			letVarBinding_AST = (AST)currentAST.root;
			letVarBinding_AST= (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(VARIABLE_BINDING,varName)).add(expr_AST));
			currentAST.root = letVarBinding_AST;
			currentAST.child = letVarBinding_AST!=null &&letVarBinding_AST.getFirstChild()!=null ?
				letVarBinding_AST.getFirstChild() : letVarBinding_AST;
			currentAST.advanceChildToEnd();
		}
		letVarBinding_AST = (AST)currentAST.root;
		returnAST = letVarBinding_AST;
	}
	
	public final void andExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST andExpr_AST = null;
		
		comparisonExpr();
		astFactory.addASTChild(currentAST, returnAST);
		{
		_loop62:
		do {
			if ((LA(1)==LITERAL_and)) {
				AST tmp142_AST = null;
				tmp142_AST = astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp142_AST);
				match(LITERAL_and);
				comparisonExpr();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop62;
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
		
		rangeExpr();
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
				AST tmp143_AST = null;
				tmp143_AST = astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp143_AST);
				match(EQ);
				break;
			}
			case NEQ:
			{
				AST tmp144_AST = null;
				tmp144_AST = astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp144_AST);
				match(NEQ);
				break;
			}
			case GT:
			{
				AST tmp145_AST = null;
				tmp145_AST = astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp145_AST);
				match(GT);
				break;
			}
			case GTEQ:
			{
				AST tmp146_AST = null;
				tmp146_AST = astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp146_AST);
				match(GTEQ);
				break;
			}
			case LT:
			{
				AST tmp147_AST = null;
				tmp147_AST = astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp147_AST);
				match(LT);
				break;
			}
			case LTEQ:
			{
				AST tmp148_AST = null;
				tmp148_AST = astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp148_AST);
				match(LTEQ);
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			}
			rangeExpr();
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
				AST tmp149_AST = null;
				tmp149_AST = astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp149_AST);
				match(ANDEQ);
				break;
			}
			case OREQ:
			{
				AST tmp150_AST = null;
				tmp150_AST = astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp150_AST);
				match(OREQ);
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			}
			rangeExpr();
			astFactory.addASTChild(currentAST, returnAST);
			}
			break;
		}
		case EOF:
		case RPAREN:
		case RCURLY:
		case COMMA:
		case LITERAL_where:
		case LITERAL_return:
		case LITERAL_for:
		case LITERAL_let:
		case LITERAL_else:
		case LITERAL_or:
		case LITERAL_and:
		case RPPAREN:
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
	
	public final void rangeExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST rangeExpr_AST = null;
		
		additiveExpr();
		astFactory.addASTChild(currentAST, returnAST);
		{
		switch ( LA(1)) {
		case LITERAL_to:
		{
			AST tmp151_AST = null;
			tmp151_AST = astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp151_AST);
			match(LITERAL_to);
			additiveExpr();
			astFactory.addASTChild(currentAST, returnAST);
			break;
		}
		case EOF:
		case RPAREN:
		case EQ:
		case RCURLY:
		case COMMA:
		case LITERAL_where:
		case LITERAL_return:
		case LITERAL_for:
		case LITERAL_let:
		case LITERAL_else:
		case LITERAL_or:
		case LITERAL_and:
		case NEQ:
		case GT:
		case GTEQ:
		case LT:
		case LTEQ:
		case ANDEQ:
		case OREQ:
		case RPPAREN:
		{
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		}
		rangeExpr_AST = (AST)currentAST.root;
		returnAST = rangeExpr_AST;
	}
	
	public final void additiveExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST additiveExpr_AST = null;
		
		multiplicativeExpr();
		astFactory.addASTChild(currentAST, returnAST);
		{
		_loop74:
		do {
			if ((LA(1)==PLUS||LA(1)==MINUS)) {
				{
				switch ( LA(1)) {
				case PLUS:
				{
					AST tmp152_AST = null;
					tmp152_AST = astFactory.create(LT(1));
					astFactory.makeASTRoot(currentAST, tmp152_AST);
					match(PLUS);
					break;
				}
				case MINUS:
				{
					AST tmp153_AST = null;
					tmp153_AST = astFactory.create(LT(1));
					astFactory.makeASTRoot(currentAST, tmp153_AST);
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
				break _loop74;
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
		_loop78:
		do {
			if ((LA(1)==STAR||LA(1)==LITERAL_div||LA(1)==LITERAL_mod)) {
				{
				switch ( LA(1)) {
				case STAR:
				{
					AST tmp154_AST = null;
					tmp154_AST = astFactory.create(LT(1));
					astFactory.makeASTRoot(currentAST, tmp154_AST);
					match(STAR);
					break;
				}
				case LITERAL_div:
				{
					AST tmp155_AST = null;
					tmp155_AST = astFactory.create(LT(1));
					astFactory.makeASTRoot(currentAST, tmp155_AST);
					match(LITERAL_div);
					break;
				}
				case LITERAL_mod:
				{
					AST tmp156_AST = null;
					tmp156_AST = astFactory.create(LT(1));
					astFactory.makeASTRoot(currentAST, tmp156_AST);
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
				break _loop78;
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
			AST tmp157_AST = null;
			tmp157_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp157_AST);
			match(MINUS);
			unionExpr();
			expr_AST = (AST)returnAST;
			astFactory.addASTChild(currentAST, returnAST);
			if ( inputState.guessing==0 ) {
				unaryExpr_AST = (AST)currentAST.root;
				unaryExpr_AST= (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(UNARY_MINUS,"-")).add(expr_AST));
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
			AST tmp158_AST = null;
			tmp158_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp158_AST);
			match(PLUS);
			unionExpr();
			expr2_AST = (AST)returnAST;
			astFactory.addASTChild(currentAST, returnAST);
			if ( inputState.guessing==0 ) {
				unaryExpr_AST = (AST)currentAST.root;
				unaryExpr_AST= (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(UNARY_PLUS,"+")).add(expr2_AST));
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
		case STRING_LITERAL:
		case DOLLAR:
		case STAR:
		case LITERAL_item:
		case LITERAL_or:
		case LITERAL_and:
		case LT:
		case LITERAL_div:
		case LITERAL_mod:
		case SLASH:
		case DSLASH:
		case LITERAL_text:
		case LITERAL_node:
		case SELF:
		case XML_COMMENT:
		case AT:
		case PARENT:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_attribute:
		case LITERAL_descendant:
		case 91:
		case 92:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 95:
		case 96:
		case DOUBLE_LITERAL:
		case DECIMAL_LITERAL:
		case INTEGER_LITERAL:
		case XML_PI:
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
			AST tmp159_AST = null;
			tmp159_AST = astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp159_AST);
			match(UNION);
			pathExpr();
			astFactory.addASTChild(currentAST, returnAST);
			break;
		}
		case EOF:
		case RPAREN:
		case EQ:
		case RCURLY:
		case COMMA:
		case STAR:
		case PLUS:
		case LITERAL_where:
		case LITERAL_return:
		case LITERAL_for:
		case LITERAL_let:
		case LITERAL_else:
		case LITERAL_or:
		case LITERAL_and:
		case NEQ:
		case GT:
		case GTEQ:
		case LT:
		case LTEQ:
		case ANDEQ:
		case OREQ:
		case LITERAL_to:
		case MINUS:
		case LITERAL_div:
		case LITERAL_mod:
		case RPPAREN:
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
		case STRING_LITERAL:
		case DOLLAR:
		case STAR:
		case LITERAL_item:
		case LITERAL_or:
		case LITERAL_and:
		case LT:
		case LITERAL_div:
		case LITERAL_mod:
		case LITERAL_text:
		case LITERAL_node:
		case SELF:
		case XML_COMMENT:
		case AT:
		case PARENT:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_attribute:
		case LITERAL_descendant:
		case 91:
		case 92:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 95:
		case 96:
		case DOUBLE_LITERAL:
		case DECIMAL_LITERAL:
		case INTEGER_LITERAL:
		case XML_PI:
		{
			relativePathExpr();
			astFactory.addASTChild(currentAST, returnAST);
			pathExpr_AST = (AST)currentAST.root;
			break;
		}
		case DSLASH:
		{
			AST tmp160_AST = null;
			tmp160_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp160_AST);
			match(DSLASH);
			relativePathExpr();
			relPath2_AST = (AST)returnAST;
			astFactory.addASTChild(currentAST, returnAST);
			if ( inputState.guessing==0 ) {
				pathExpr_AST = (AST)currentAST.root;
				pathExpr_AST= (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(ABSOLUTE_DSLASH,"AbsoluteSlashSlash")).add(relPath2_AST));
				currentAST.root = pathExpr_AST;
				currentAST.child = pathExpr_AST!=null &&pathExpr_AST.getFirstChild()!=null ?
					pathExpr_AST.getFirstChild() : pathExpr_AST;
				currentAST.advanceChildToEnd();
			}
			pathExpr_AST = (AST)currentAST.root;
			break;
		}
		default:
			boolean synPredMatched84 = false;
			if (((LA(1)==SLASH))) {
				int _m84 = mark();
				synPredMatched84 = true;
				inputState.guessing++;
				try {
					{
					match(SLASH);
					relativePathExpr();
					}
				}
				catch (RecognitionException pe) {
					synPredMatched84 = false;
				}
				rewind(_m84);
				inputState.guessing--;
			}
			if ( synPredMatched84 ) {
				AST tmp161_AST = null;
				tmp161_AST = astFactory.create(LT(1));
				astFactory.addASTChild(currentAST, tmp161_AST);
				match(SLASH);
				relativePathExpr();
				relPath_AST = (AST)returnAST;
				astFactory.addASTChild(currentAST, returnAST);
				if ( inputState.guessing==0 ) {
					pathExpr_AST = (AST)currentAST.root;
					pathExpr_AST= (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(ABSOLUTE_SLASH,"AbsoluteSlash")).add(relPath_AST));
					currentAST.root = pathExpr_AST;
					currentAST.child = pathExpr_AST!=null &&pathExpr_AST.getFirstChild()!=null ?
						pathExpr_AST.getFirstChild() : pathExpr_AST;
					currentAST.advanceChildToEnd();
				}
				pathExpr_AST = (AST)currentAST.root;
			}
			else if ((LA(1)==SLASH)) {
				AST tmp162_AST = null;
				tmp162_AST = astFactory.create(LT(1));
				astFactory.addASTChild(currentAST, tmp162_AST);
				match(SLASH);
				if ( inputState.guessing==0 ) {
					pathExpr_AST = (AST)currentAST.root;
					pathExpr_AST= astFactory.create(ABSOLUTE_SLASH,"AbsoluteSlash");
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
		_loop88:
		do {
			if ((LA(1)==SLASH||LA(1)==DSLASH)) {
				{
				switch ( LA(1)) {
				case SLASH:
				{
					AST tmp163_AST = null;
					tmp163_AST = astFactory.create(LT(1));
					astFactory.makeASTRoot(currentAST, tmp163_AST);
					match(SLASH);
					break;
				}
				case DSLASH:
				{
					AST tmp164_AST = null;
					tmp164_AST = astFactory.create(LT(1));
					astFactory.makeASTRoot(currentAST, tmp164_AST);
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
				break _loop88;
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
		
		boolean synPredMatched92 = false;
		if (((_tokenSet_1.member(LA(1))))) {
			int _m92 = mark();
			synPredMatched92 = true;
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
				synPredMatched92 = false;
			}
			rewind(_m92);
			inputState.guessing--;
		}
		if ( synPredMatched92 ) {
			axisStep();
			astFactory.addASTChild(currentAST, returnAST);
			stepExpr_AST = (AST)currentAST.root;
		}
		else {
			boolean synPredMatched95 = false;
			if (((_tokenSet_2.member(LA(1))))) {
				int _m95 = mark();
				synPredMatched95 = true;
				inputState.guessing++;
				try {
					{
					switch ( LA(1)) {
					case DOLLAR:
					{
						match(DOLLAR);
						break;
					}
					case NCNAME:
					case LITERAL_item:
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
					case 91:
					case 92:
					case LITERAL_parent:
					case LITERAL_ancestor:
					case 95:
					case 96:
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
					case LT:
					{
						match(LT);
						break;
					}
					case XML_COMMENT:
					{
						match(XML_COMMENT);
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
					synPredMatched95 = false;
				}
				rewind(_m95);
				inputState.guessing--;
			}
			if ( synPredMatched95 ) {
				filterStep();
				astFactory.addASTChild(currentAST, returnAST);
				stepExpr_AST = (AST)currentAST.root;
			}
			else if ((_tokenSet_1.member(LA(1)))) {
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
	
	public final void literal() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST literal_AST = null;
		
		switch ( LA(1)) {
		case STRING_LITERAL:
		{
			AST tmp165_AST = null;
			tmp165_AST = astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp165_AST);
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
		
		boolean synPredMatched104 = false;
		if ((((LA(1) >= LITERAL_child && LA(1) <= 92)))) {
			int _m104 = mark();
			synPredMatched104 = true;
			inputState.guessing++;
			try {
				{
				forwardAxisSpecifier();
				match(COLON);
				}
			}
			catch (RecognitionException pe) {
				synPredMatched104 = false;
			}
			rewind(_m104);
			inputState.guessing--;
		}
		if ( synPredMatched104 ) {
			forwardAxis();
			astFactory.addASTChild(currentAST, returnAST);
			nodeTest();
			astFactory.addASTChild(currentAST, returnAST);
			forwardOrReverseStep_AST = (AST)currentAST.root;
		}
		else {
			boolean synPredMatched106 = false;
			if ((((LA(1) >= LITERAL_parent && LA(1) <= 96)))) {
				int _m106 = mark();
				synPredMatched106 = true;
				inputState.guessing++;
				try {
					{
					reverseAxisSpecifier();
					match(COLON);
					}
				}
				catch (RecognitionException pe) {
					synPredMatched106 = false;
				}
				rewind(_m106);
				inputState.guessing--;
			}
			if ( synPredMatched106 ) {
				reverseAxis();
				astFactory.addASTChild(currentAST, returnAST);
				nodeTest();
				astFactory.addASTChild(currentAST, returnAST);
				forwardOrReverseStep_AST = (AST)currentAST.root;
			}
			else if ((_tokenSet_1.member(LA(1)))) {
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
		_loop100:
		do {
			if ((LA(1)==LPPAREN)) {
				predicate();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop100;
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
			predicate_AST= (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(PREDICATE,"Pred")).add(predExpr_AST));
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
			AST tmp168_AST = null;
			tmp168_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp168_AST);
			match(LITERAL_child);
			forwardAxisSpecifier_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_self:
		{
			AST tmp169_AST = null;
			tmp169_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp169_AST);
			match(LITERAL_self);
			forwardAxisSpecifier_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_attribute:
		{
			AST tmp170_AST = null;
			tmp170_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp170_AST);
			match(LITERAL_attribute);
			forwardAxisSpecifier_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_descendant:
		{
			AST tmp171_AST = null;
			tmp171_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp171_AST);
			match(LITERAL_descendant);
			forwardAxisSpecifier_AST = (AST)currentAST.root;
			break;
		}
		case 91:
		{
			AST tmp172_AST = null;
			tmp172_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp172_AST);
			match(91);
			forwardAxisSpecifier_AST = (AST)currentAST.root;
			break;
		}
		case 92:
		{
			AST tmp173_AST = null;
			tmp173_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp173_AST);
			match(92);
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
		
		boolean synPredMatched116 = false;
		if (((LA(1)==LITERAL_text||LA(1)==LITERAL_node))) {
			int _m116 = mark();
			synPredMatched116 = true;
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
				synPredMatched116 = false;
			}
			rewind(_m116);
			inputState.guessing--;
		}
		if ( synPredMatched116 ) {
			kindTest();
			astFactory.addASTChild(currentAST, returnAST);
			nodeTest_AST = (AST)currentAST.root;
		}
		else if ((_tokenSet_3.member(LA(1)))) {
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
			AST tmp176_AST = null;
			tmp176_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp176_AST);
			match(LITERAL_parent);
			reverseAxisSpecifier_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_ancestor:
		{
			AST tmp177_AST = null;
			tmp177_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp177_AST);
			match(LITERAL_ancestor);
			reverseAxisSpecifier_AST = (AST)currentAST.root;
			break;
		}
		case 95:
		{
			AST tmp178_AST = null;
			tmp178_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp178_AST);
			match(95);
			reverseAxisSpecifier_AST = (AST)currentAST.root;
			break;
		}
		case 96:
		{
			AST tmp179_AST = null;
			tmp179_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp179_AST);
			match(96);
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
		case STAR:
		case LITERAL_item:
		case LITERAL_or:
		case LITERAL_and:
		case LITERAL_div:
		case LITERAL_mod:
		case LITERAL_text:
		case LITERAL_node:
		case AT:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_attribute:
		case LITERAL_descendant:
		case 91:
		case 92:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 95:
		case 96:
		{
			{
			switch ( LA(1)) {
			case AT:
			{
				AST tmp182_AST = null;
				tmp182_AST = astFactory.create(LT(1));
				astFactory.addASTChild(currentAST, tmp182_AST);
				match(AT);
				break;
			}
			case NCNAME:
			case STAR:
			case LITERAL_item:
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
			case 91:
			case 92:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 95:
			case 96:
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
			AST tmp183_AST = null;
			tmp183_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp183_AST);
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
		String name= null;
		
		boolean synPredMatched120 = false;
		if (((LA(1)==NCNAME||LA(1)==STAR))) {
			int _m120 = mark();
			synPredMatched120 = true;
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
				synPredMatched120 = false;
			}
			rewind(_m120);
			inputState.guessing--;
		}
		if ( synPredMatched120 ) {
			wildcard();
			astFactory.addASTChild(currentAST, returnAST);
			nameTest_AST = (AST)currentAST.root;
		}
		else if ((_tokenSet_0.member(LA(1)))) {
			name=qName();
			astFactory.addASTChild(currentAST, returnAST);
			if ( inputState.guessing==0 ) {
				nameTest_AST = (AST)currentAST.root;
				nameTest_AST= astFactory.create(QNAME,name);
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
		
		boolean synPredMatched123 = false;
		if (((LA(1)==STAR))) {
			int _m123 = mark();
			synPredMatched123 = true;
			inputState.guessing++;
			try {
				{
				match(STAR);
				match(COLON);
				}
			}
			catch (RecognitionException pe) {
				synPredMatched123 = false;
			}
			rewind(_m123);
			inputState.guessing--;
		}
		if ( synPredMatched123 ) {
			match(STAR);
			match(COLON);
			nc1 = LT(1);
			nc1_AST = astFactory.create(nc1);
			astFactory.addASTChild(currentAST, nc1_AST);
			match(NCNAME);
			if ( inputState.guessing==0 ) {
				wildcard_AST = (AST)currentAST.root;
				wildcard_AST= (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(PREFIX_WILDCARD,"*")).add(nc1_AST));
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
				wildcard_AST= (AST)astFactory.make( (new ASTArray(2)).add(nc2_AST).add(astFactory.create(WILDCARD,"*")));
				currentAST.root = wildcard_AST;
				currentAST.child = wildcard_AST!=null &&wildcard_AST.getFirstChild()!=null ?
					wildcard_AST.getFirstChild() : wildcard_AST;
				currentAST.advanceChildToEnd();
			}
			wildcard_AST = (AST)currentAST.root;
		}
		else if ((LA(1)==STAR)) {
			AST tmp188_AST = null;
			tmp188_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp188_AST);
			match(STAR);
			if ( inputState.guessing==0 ) {
				wildcard_AST = (AST)currentAST.root;
				
						// make this distinct from multiplication
						wildcard_AST= astFactory.create(WILDCARD,"*");
					
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
		String varName= null;
		
		switch ( LA(1)) {
		case NCNAME:
		case LITERAL_item:
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
		case 91:
		case 92:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 95:
		case 96:
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
		case DOLLAR:
		{
			match(DOLLAR);
			varName=qName();
			astFactory.addASTChild(currentAST, returnAST);
			if ( inputState.guessing==0 ) {
				primaryExpr_AST = (AST)currentAST.root;
				primaryExpr_AST= astFactory.create(VARIABLE_REF,varName);
				currentAST.root = primaryExpr_AST;
				currentAST.child = primaryExpr_AST!=null &&primaryExpr_AST.getFirstChild()!=null ?
					primaryExpr_AST.getFirstChild() : primaryExpr_AST;
				currentAST.advanceChildToEnd();
			}
			primaryExpr_AST = (AST)currentAST.root;
			break;
		}
		case LT:
		case XML_COMMENT:
		case XML_PI:
		{
			constructor();
			astFactory.addASTChild(currentAST, returnAST);
			primaryExpr_AST = (AST)currentAST.root;
			break;
		}
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
		String fnName= null;
		
		fnName=qName();
		astFactory.addASTChild(currentAST, returnAST);
		match(LPAREN);
		if ( inputState.guessing==0 ) {
			functionCall_AST = (AST)currentAST.root;
			functionCall_AST= astFactory.create(FUNCTION,fnName);
			currentAST.root = functionCall_AST;
			currentAST.child = functionCall_AST!=null &&functionCall_AST.getFirstChild()!=null ?
				functionCall_AST.getFirstChild() : functionCall_AST;
			currentAST.advanceChildToEnd();
		}
		{
		switch ( LA(1)) {
		case LPAREN:
		case NCNAME:
		case STRING_LITERAL:
		case DOLLAR:
		case STAR:
		case PLUS:
		case LITERAL_item:
		case LITERAL_for:
		case LITERAL_let:
		case LITERAL_if:
		case LITERAL_or:
		case LITERAL_and:
		case LT:
		case MINUS:
		case LITERAL_div:
		case LITERAL_mod:
		case SLASH:
		case DSLASH:
		case LITERAL_text:
		case LITERAL_node:
		case SELF:
		case XML_COMMENT:
		case AT:
		case PARENT:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_attribute:
		case LITERAL_descendant:
		case 91:
		case 92:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 95:
		case 96:
		case DOUBLE_LITERAL:
		case DECIMAL_LITERAL:
		case INTEGER_LITERAL:
		case XML_PI:
		{
			functionParameters();
			params_AST = (AST)returnAST;
			astFactory.addASTChild(currentAST, returnAST);
			if ( inputState.guessing==0 ) {
				functionCall_AST = (AST)currentAST.root;
				functionCall_AST= (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(FUNCTION,fnName)).add(params_AST));
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
		
		AST tmp192_AST = null;
		tmp192_AST = astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp192_AST);
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
		{
		switch ( LA(1)) {
		case LPAREN:
		case NCNAME:
		case STRING_LITERAL:
		case DOLLAR:
		case STAR:
		case PLUS:
		case LITERAL_item:
		case LITERAL_for:
		case LITERAL_let:
		case LITERAL_if:
		case LITERAL_or:
		case LITERAL_and:
		case LT:
		case MINUS:
		case LITERAL_div:
		case LITERAL_mod:
		case SLASH:
		case DSLASH:
		case LITERAL_text:
		case LITERAL_node:
		case SELF:
		case XML_COMMENT:
		case AT:
		case PARENT:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_attribute:
		case LITERAL_descendant:
		case 91:
		case 92:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 95:
		case 96:
		case DOUBLE_LITERAL:
		case DECIMAL_LITERAL:
		case INTEGER_LITERAL:
		case XML_PI:
		{
			expr();
			e_AST = (AST)returnAST;
			astFactory.addASTChild(currentAST, returnAST);
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
		if ( inputState.guessing==0 ) {
			parenthesizedExpr_AST = (AST)currentAST.root;
			parenthesizedExpr_AST= (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(PARENTHESIZED,"Parenthesized")).add(e_AST));
			currentAST.root = parenthesizedExpr_AST;
			currentAST.child = parenthesizedExpr_AST!=null &&parenthesizedExpr_AST.getFirstChild()!=null ?
				parenthesizedExpr_AST.getFirstChild() : parenthesizedExpr_AST;
			currentAST.advanceChildToEnd();
		}
		parenthesizedExpr_AST = (AST)currentAST.root;
		returnAST = parenthesizedExpr_AST;
	}
	
	public final void constructor() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST constructor_AST = null;
		
		switch ( LA(1)) {
		case LT:
		{
			elementConstructor();
			astFactory.addASTChild(currentAST, returnAST);
			constructor_AST = (AST)currentAST.root;
			break;
		}
		case XML_COMMENT:
		{
			xmlComment();
			astFactory.addASTChild(currentAST, returnAST);
			constructor_AST = (AST)currentAST.root;
			break;
		}
		case XML_PI:
		{
			xmlPI();
			astFactory.addASTChild(currentAST, returnAST);
			constructor_AST = (AST)currentAST.root;
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		returnAST = constructor_AST;
	}
	
	public final void numericLiteral() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST numericLiteral_AST = null;
		
		switch ( LA(1)) {
		case DOUBLE_LITERAL:
		{
			AST tmp195_AST = null;
			tmp195_AST = astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp195_AST);
			match(DOUBLE_LITERAL);
			numericLiteral_AST = (AST)currentAST.root;
			break;
		}
		case DECIMAL_LITERAL:
		{
			AST tmp196_AST = null;
			tmp196_AST = astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp196_AST);
			match(DECIMAL_LITERAL);
			numericLiteral_AST = (AST)currentAST.root;
			break;
		}
		case INTEGER_LITERAL:
		{
			AST tmp197_AST = null;
			tmp197_AST = astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp197_AST);
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
		
		exprSingle();
		astFactory.addASTChild(currentAST, returnAST);
		{
		_loop134:
		do {
			if ((LA(1)==COMMA)) {
				match(COMMA);
				exprSingle();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop134;
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
		
		AST tmp199_AST = null;
		tmp199_AST = astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp199_AST);
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
		
		AST tmp202_AST = null;
		tmp202_AST = astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp202_AST);
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
		name= null;
		
		switch ( LA(1)) {
		case NCNAME:
		{
			n1 = LT(1);
			n1_AST = astFactory.create(n1);
			astFactory.addASTChild(currentAST, n1_AST);
			match(NCNAME);
			if ( inputState.guessing==0 ) {
				name= n1.getText();
			}
			ncnameOrKeyword_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_item:
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
		case 91:
		case 92:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 95:
		case 96:
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
	
	public final void elementConstructor() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST elementConstructor_AST = null;
		
			String name= null;
			lexer.wsExplicit= true;
		
		
		boolean synPredMatched146 = false;
		if (((LA(1)==LT))) {
			int _m146 = mark();
			synPredMatched146 = true;
			inputState.guessing++;
			try {
				{
				match(LT);
				match(NCNAME);
				{
				switch ( LA(1)) {
				case COLON:
				{
					match(COLON);
					match(NCNAME);
					break;
				}
				case WS:
				{
					break;
				}
				default:
				{
					throw new NoViableAltException(LT(1), getFilename());
				}
				}
				}
				match(WS);
				}
			}
			catch (RecognitionException pe) {
				synPredMatched146 = false;
			}
			rewind(_m146);
			inputState.guessing--;
		}
		if ( synPredMatched146 ) {
			elementWithAttributes();
			astFactory.addASTChild(currentAST, returnAST);
			elementConstructor_AST = (AST)currentAST.root;
		}
		else if ((LA(1)==LT)) {
			elementWithoutAttributes();
			astFactory.addASTChild(currentAST, returnAST);
			elementConstructor_AST = (AST)currentAST.root;
		}
		else {
			throw new NoViableAltException(LT(1), getFilename());
		}
		
		returnAST = elementConstructor_AST;
	}
	
	public final void xmlComment() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST xmlComment_AST = null;
		
		AST tmp205_AST = null;
		tmp205_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp205_AST);
		match(XML_COMMENT);
		match(XML_COMMENT_END);
		xmlComment_AST = (AST)currentAST.root;
		returnAST = xmlComment_AST;
	}
	
	public final void xmlPI() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST xmlPI_AST = null;
		
		AST tmp207_AST = null;
		tmp207_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp207_AST);
		match(XML_PI);
		match(XML_PI_END);
		xmlPI_AST = (AST)currentAST.root;
		returnAST = xmlPI_AST;
	}
	
	public final void elementWithAttributes() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST elementWithAttributes_AST = null;
		AST attrs_AST = null;
		AST content_AST = null;
		String name= null;
		
		match(LT);
		name=qName();
		astFactory.addASTChild(currentAST, returnAST);
		attributeList();
		attrs_AST = (AST)returnAST;
		astFactory.addASTChild(currentAST, returnAST);
		{
		switch ( LA(1)) {
		case SLASH:
		{
			{
			match(SLASH);
			match(GT);
			if ( inputState.guessing==0 ) {
				elementWithAttributes_AST = (AST)currentAST.root;
				elementWithAttributes_AST= (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(ELEMENT,name)).add(attrs_AST));
				currentAST.root = elementWithAttributes_AST;
				currentAST.child = elementWithAttributes_AST!=null &&elementWithAttributes_AST.getFirstChild()!=null ?
					elementWithAttributes_AST.getFirstChild() : elementWithAttributes_AST;
				currentAST.advanceChildToEnd();
			}
			}
			break;
		}
		case GT:
		{
			{
			match(GT);
			if ( inputState.guessing==0 ) {
				lexer.inElementContent= true;
			}
			mixedElementContent();
			content_AST = (AST)returnAST;
			astFactory.addASTChild(currentAST, returnAST);
			match(END_TAG_START);
			name=qName();
			match(GT);
			if ( inputState.guessing==0 ) {
				elementWithAttributes_AST = (AST)currentAST.root;
				elementWithAttributes_AST= (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(ELEMENT,name)).add(attrs_AST));
				currentAST.root = elementWithAttributes_AST;
				currentAST.child = elementWithAttributes_AST!=null &&elementWithAttributes_AST.getFirstChild()!=null ?
					elementWithAttributes_AST.getFirstChild() : elementWithAttributes_AST;
				currentAST.advanceChildToEnd();
			}
			}
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		}
		elementWithAttributes_AST = (AST)currentAST.root;
		returnAST = elementWithAttributes_AST;
	}
	
	public final void elementWithoutAttributes() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST elementWithoutAttributes_AST = null;
		AST content_AST = null;
		String name= null;
		
		AST tmp215_AST = null;
		tmp215_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp215_AST);
		match(LT);
		name=qName();
		astFactory.addASTChild(currentAST, returnAST);
		{
		switch ( LA(1)) {
		case SLASH:
		{
			{
			match(SLASH);
			match(GT);
			if ( inputState.guessing==0 ) {
				elementWithoutAttributes_AST = (AST)currentAST.root;
				elementWithoutAttributes_AST= astFactory.create(ELEMENT,name);
				currentAST.root = elementWithoutAttributes_AST;
				currentAST.child = elementWithoutAttributes_AST!=null &&elementWithoutAttributes_AST.getFirstChild()!=null ?
					elementWithoutAttributes_AST.getFirstChild() : elementWithoutAttributes_AST;
				currentAST.advanceChildToEnd();
			}
			}
			break;
		}
		case GT:
		{
			{
			match(GT);
			if ( inputState.guessing==0 ) {
				lexer.inElementContent= true;
			}
			mixedElementContent();
			content_AST = (AST)returnAST;
			astFactory.addASTChild(currentAST, returnAST);
			match(END_TAG_START);
			name=qName();
			match(GT);
			if ( inputState.guessing==0 ) {
				elementWithoutAttributes_AST = (AST)currentAST.root;
				elementWithoutAttributes_AST= (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(ELEMENT,name)).add(content_AST));
				currentAST.root = elementWithoutAttributes_AST;
				currentAST.child = elementWithoutAttributes_AST!=null &&elementWithoutAttributes_AST.getFirstChild()!=null ?
					elementWithoutAttributes_AST.getFirstChild() : elementWithoutAttributes_AST;
				currentAST.advanceChildToEnd();
			}
			}
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		}
		elementWithoutAttributes_AST = (AST)currentAST.root;
		returnAST = elementWithoutAttributes_AST;
	}
	
	public final void mixedElementContent() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST mixedElementContent_AST = null;
		
		{
		_loop164:
		do {
			if ((_tokenSet_4.member(LA(1)))) {
				elementContent();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop164;
			}
			
		} while (true);
		}
		mixedElementContent_AST = (AST)currentAST.root;
		returnAST = mixedElementContent_AST;
	}
	
	public final void attributeList() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST attributeList_AST = null;
		
		{
		int _cnt157=0;
		_loop157:
		do {
			if ((LA(1)==WS)) {
				attributeDef();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				if ( _cnt157>=1 ) { break _loop157; } else {throw new NoViableAltException(LT(1), getFilename());}
			}
			
			_cnt157++;
		} while (true);
		}
		attributeList_AST = (AST)currentAST.root;
		returnAST = attributeList_AST;
	}
	
	public final void attributeDef() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST attributeDef_AST = null;
		AST value_AST = null;
		
			String name= null;
			lexer.parseStringLiterals= false;
		
		
		match(WS);
		name=qName();
		match(EQ);
		match(QUOT);
		if ( inputState.guessing==0 ) {
			lexer.inAttributeContent= true;
		}
		attributeValue();
		value_AST = (AST)returnAST;
		astFactory.addASTChild(currentAST, returnAST);
		if ( inputState.guessing==0 ) {
			lexer.inAttributeContent= false;
		}
		match(QUOT);
		if ( inputState.guessing==0 ) {
			lexer.parseStringLiterals= true;
		}
		if ( inputState.guessing==0 ) {
			attributeDef_AST = (AST)currentAST.root;
			attributeDef_AST= (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(ATTRIBUTE,name)).add(value_AST));
			currentAST.root = attributeDef_AST;
			currentAST.child = attributeDef_AST!=null &&attributeDef_AST.getFirstChild()!=null ?
				attributeDef_AST.getFirstChild() : attributeDef_AST;
			currentAST.advanceChildToEnd();
		}
		attributeDef_AST = (AST)currentAST.root;
		returnAST = attributeDef_AST;
	}
	
	public final void attributeValue() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST attributeValue_AST = null;
		
		{
		int _cnt161=0;
		_loop161:
		do {
			switch ( LA(1)) {
			case ATTRIBUTE_CONTENT:
			{
				AST tmp225_AST = null;
				tmp225_AST = astFactory.create(LT(1));
				astFactory.addASTChild(currentAST, tmp225_AST);
				match(ATTRIBUTE_CONTENT);
				break;
			}
			case LCURLY:
			{
				attributeEnclosedExpr();
				astFactory.addASTChild(currentAST, returnAST);
				break;
			}
			default:
			{
				if ( _cnt161>=1 ) { break _loop161; } else {throw new NoViableAltException(LT(1), getFilename());}
			}
			}
			_cnt161++;
		} while (true);
		}
		attributeValue_AST = (AST)currentAST.root;
		returnAST = attributeValue_AST;
	}
	
	public final void attributeEnclosedExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST attributeEnclosedExpr_AST = null;
		
		AST tmp226_AST = null;
		tmp226_AST = astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp226_AST);
		match(LCURLY);
		if ( inputState.guessing==0 ) {
			
					lexer.inAttributeContent= false;
					lexer.wsExplicit= false;
				
		}
		expr();
		astFactory.addASTChild(currentAST, returnAST);
		match(RCURLY);
		if ( inputState.guessing==0 ) {
			
					lexer.inAttributeContent= true;
					lexer.wsExplicit= true;
				
		}
		attributeEnclosedExpr_AST = (AST)currentAST.root;
		returnAST = attributeEnclosedExpr_AST;
	}
	
	public final void elementContent() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST elementContent_AST = null;
		Token  content = null;
		AST content_AST = null;
		
		switch ( LA(1)) {
		case LT:
		{
			elementConstructor();
			astFactory.addASTChild(currentAST, returnAST);
			elementContent_AST = (AST)currentAST.root;
			break;
		}
		case ELEMENT_CONTENT:
		{
			content = LT(1);
			content_AST = astFactory.create(content);
			astFactory.addASTChild(currentAST, content_AST);
			match(ELEMENT_CONTENT);
			if ( inputState.guessing==0 ) {
				elementContent_AST = (AST)currentAST.root;
				elementContent_AST= astFactory.create(TEXT,content.getText());
				currentAST.root = elementContent_AST;
				currentAST.child = elementContent_AST!=null &&elementContent_AST.getFirstChild()!=null ?
					elementContent_AST.getFirstChild() : elementContent_AST;
				currentAST.advanceChildToEnd();
			}
			elementContent_AST = (AST)currentAST.root;
			break;
		}
		case XML_COMMENT:
		{
			xmlComment();
			astFactory.addASTChild(currentAST, returnAST);
			elementContent_AST = (AST)currentAST.root;
			break;
		}
		case XML_PI:
		{
			xmlPI();
			astFactory.addASTChild(currentAST, returnAST);
			elementContent_AST = (AST)currentAST.root;
			break;
		}
		case LCURLY:
		{
			enclosedExpr();
			astFactory.addASTChild(currentAST, returnAST);
			elementContent_AST = (AST)currentAST.root;
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		returnAST = elementContent_AST;
	}
	
	public final void enclosedExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST enclosedExpr_AST = null;
		
		AST tmp228_AST = null;
		tmp228_AST = astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp228_AST);
		match(LCURLY);
		if ( inputState.guessing==0 ) {
			
					lexer.inElementContent= false;
					lexer.wsExplicit= false;
				
		}
		expr();
		astFactory.addASTChild(currentAST, returnAST);
		match(RCURLY);
		if ( inputState.guessing==0 ) {
			
					lexer.inElementContent= true;
					lexer.wsExplicit= true;
				
		}
		enclosedExpr_AST = (AST)currentAST.root;
		returnAST = enclosedExpr_AST;
	}
	
	public final String  reservedKeywords() throws RecognitionException, TokenStreamException {
		String name;
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST reservedKeywords_AST = null;
		name= null;
		
		switch ( LA(1)) {
		case LITERAL_div:
		{
			AST tmp230_AST = null;
			tmp230_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp230_AST);
			match(LITERAL_div);
			if ( inputState.guessing==0 ) {
				name= "div";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_mod:
		{
			AST tmp231_AST = null;
			tmp231_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp231_AST);
			match(LITERAL_mod);
			if ( inputState.guessing==0 ) {
				name= "mod";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_text:
		{
			AST tmp232_AST = null;
			tmp232_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp232_AST);
			match(LITERAL_text);
			if ( inputState.guessing==0 ) {
				name= "text";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_node:
		{
			AST tmp233_AST = null;
			tmp233_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp233_AST);
			match(LITERAL_node);
			if ( inputState.guessing==0 ) {
				name= "node";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_or:
		{
			AST tmp234_AST = null;
			tmp234_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp234_AST);
			match(LITERAL_or);
			if ( inputState.guessing==0 ) {
				name= "or";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_and:
		{
			AST tmp235_AST = null;
			tmp235_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp235_AST);
			match(LITERAL_and);
			if ( inputState.guessing==0 ) {
				name= "and";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_child:
		{
			AST tmp236_AST = null;
			tmp236_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp236_AST);
			match(LITERAL_child);
			if ( inputState.guessing==0 ) {
				name= "child";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_parent:
		{
			AST tmp237_AST = null;
			tmp237_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp237_AST);
			match(LITERAL_parent);
			if ( inputState.guessing==0 ) {
				name= "parent";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_self:
		{
			AST tmp238_AST = null;
			tmp238_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp238_AST);
			match(LITERAL_self);
			if ( inputState.guessing==0 ) {
				name= "self";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_attribute:
		{
			AST tmp239_AST = null;
			tmp239_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp239_AST);
			match(LITERAL_attribute);
			if ( inputState.guessing==0 ) {
				name= "attribute";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_ancestor:
		{
			AST tmp240_AST = null;
			tmp240_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp240_AST);
			match(LITERAL_ancestor);
			if ( inputState.guessing==0 ) {
				name= "ancestor";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_descendant:
		{
			AST tmp241_AST = null;
			tmp241_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp241_AST);
			match(LITERAL_descendant);
			if ( inputState.guessing==0 ) {
				name= "descendant";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case 91:
		{
			AST tmp242_AST = null;
			tmp242_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp242_AST);
			match(91);
			if ( inputState.guessing==0 ) {
				name= "descendant-or-self";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case 95:
		{
			AST tmp243_AST = null;
			tmp243_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp243_AST);
			match(95);
			if ( inputState.guessing==0 ) {
				name= "ancestor-or-self";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case 96:
		{
			AST tmp244_AST = null;
			tmp244_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp244_AST);
			match(96);
			if ( inputState.guessing==0 ) {
				name= "preceding-sibling";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case 92:
		{
			AST tmp245_AST = null;
			tmp245_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp245_AST);
			match(92);
			if ( inputState.guessing==0 ) {
				name= "following-sibling";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_item:
		{
			AST tmp246_AST = null;
			tmp246_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp246_AST);
			match(LITERAL_item);
			if ( inputState.guessing==0 ) {
				name = "item";
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
		"TEXT",
		"VERSION_DECL",
		"NAMESPACE_DECL",
		"DEF_NAMESPACE_DECL",
		"DEF_FUNCTION_NS_DECL",
		"GLOBAL_VAR",
		"FUNCTION_DECL",
		"PROLOG",
		"\"xpointer\"",
		"LPAREN",
		"RPAREN",
		"NCNAME",
		"SEMICOLON",
		"\"declare\"",
		"\"namespace\"",
		"\"default\"",
		"\"function\"",
		"\"xquery\"",
		"\"version\"",
		"STRING_LITERAL",
		"EQ",
		"\"element\"",
		"\"variable\"",
		"DOLLAR",
		"LCURLY",
		"RCURLY",
		"COMMA",
		"\"as\"",
		"\"empty\"",
		"\"?\"",
		"STAR",
		"PLUS",
		"\"item\"",
		"\"where\"",
		"\"return\"",
		"\"for\"",
		"\"let\"",
		"\"in\"",
		"COLON",
		"\"if\"",
		"\"then\"",
		"\"else\"",
		"\"or\"",
		"\"and\"",
		"NEQ",
		"GT",
		"GTEQ",
		"LT",
		"LTEQ",
		"ANDEQ",
		"OREQ",
		"\"to\"",
		"MINUS",
		"\"div\"",
		"\"mod\"",
		"UNION",
		"SLASH",
		"DSLASH",
		"\"text\"",
		"\"node\"",
		"SELF",
		"XML_COMMENT",
		"LPPAREN",
		"RPPAREN",
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
		"WS",
		"END_TAG_START",
		"QUOT",
		"ATTRIBUTE_CONTENT",
		"ELEMENT_CONTENT",
		"XML_COMMENT_END",
		"XML_PI",
		"XML_PI_END",
		"XML_PI_START",
		"CHAR",
		"BASECHAR",
		"IDEOGRAPHIC",
		"COMBINING_CHAR",
		"DIGIT",
		"EXTENDER",
		"LETTER",
		"DIGITS",
		"HEX_DIGITS",
		"NMSTART",
		"NMCHAR",
		"EXPR_COMMENT",
		"PREDEFINED_ENTITY_REF",
		"CHAR_REF",
		"NEXT_TOKEN"
	};
	
	protected void buildTokenTypeASTClassMap() {
		tokenTypeToASTClassMap=null;
	};
	
	private static final long[] mk_tokenSet_0() {
		long[] data = { -9214364833305067520L, 8581647361L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_0 = new BitSet(mk_tokenSet_0());
	private static final long[] mk_tokenSet_1() {
		long[] data = { -9212113033491382272L, 8587938817L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_1 = new BitSet(mk_tokenSet_1());
	private static final long[] mk_tokenSet_2() {
		long[] data = { -9214346140533653504L, 4466758093841L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_2 = new BitSet(mk_tokenSet_2());
	private static final long[] mk_tokenSet_3() {
		long[] data = { -9212113033491382272L, 8581647361L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_3 = new BitSet(mk_tokenSet_3());
	private static final long[] mk_tokenSet_4() {
		long[] data = { 35184372088832L, 5497558401040L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_4 = new BitSet(mk_tokenSet_4());
	
	}
