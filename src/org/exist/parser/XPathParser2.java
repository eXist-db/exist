// $ANTLR 2.7.2: "XPathParser2.g" -> "XPathParser2.java"$

	package org.exist.parser;

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
	protected Stack globalStack= new Stack();
	protected Stack elementStack= new Stack();
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
		
		AST tmp103_AST = null;
		tmp103_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp103_AST);
		match(QNAME);
		AST tmp104_AST = null;
		tmp104_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp104_AST);
		match(PREDICATE);
		AST tmp105_AST = null;
		tmp105_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp105_AST);
		match(FLWOR);
		AST tmp106_AST = null;
		tmp106_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp106_AST);
		match(PARENTHESIZED);
		AST tmp107_AST = null;
		tmp107_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp107_AST);
		match(ABSOLUTE_SLASH);
		AST tmp108_AST = null;
		tmp108_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp108_AST);
		match(ABSOLUTE_DSLASH);
		AST tmp109_AST = null;
		tmp109_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp109_AST);
		match(WILDCARD);
		AST tmp110_AST = null;
		tmp110_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp110_AST);
		match(PREFIX_WILDCARD);
		AST tmp111_AST = null;
		tmp111_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp111_AST);
		match(FUNCTION);
		AST tmp112_AST = null;
		tmp112_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp112_AST);
		match(UNARY_MINUS);
		AST tmp113_AST = null;
		tmp113_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp113_AST);
		match(UNARY_PLUS);
		AST tmp114_AST = null;
		tmp114_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp114_AST);
		match(XPOINTER);
		AST tmp115_AST = null;
		tmp115_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp115_AST);
		match(XPOINTER_ID);
		AST tmp116_AST = null;
		tmp116_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp116_AST);
		match(VARIABLE_REF);
		AST tmp117_AST = null;
		tmp117_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp117_AST);
		match(VARIABLE_BINDING);
		AST tmp118_AST = null;
		tmp118_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp118_AST);
		match(ELEMENT);
		AST tmp119_AST = null;
		tmp119_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp119_AST);
		match(ATTRIBUTE);
		AST tmp120_AST = null;
		tmp120_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp120_AST);
		match(TEXT);
		AST tmp121_AST = null;
		tmp121_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp121_AST);
		match(VERSION_DECL);
		AST tmp122_AST = null;
		tmp122_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp122_AST);
		match(NAMESPACE_DECL);
		AST tmp123_AST = null;
		tmp123_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp123_AST);
		match(DEF_NAMESPACE_DECL);
		AST tmp124_AST = null;
		tmp124_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp124_AST);
		match(DEF_FUNCTION_NS_DECL);
		AST tmp125_AST = null;
		tmp125_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp125_AST);
		match(GLOBAL_VAR);
		AST tmp126_AST = null;
		tmp126_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp126_AST);
		match(FUNCTION_DECL);
		AST tmp127_AST = null;
		tmp127_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp127_AST);
		match(PROLOG);
		AST tmp128_AST = null;
		tmp128_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp128_AST);
		match(ATOMIC_TYPE);
		AST tmp129_AST = null;
		tmp129_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp129_AST);
		match(MODULE);
		AST tmp130_AST = null;
		tmp130_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp130_AST);
		match(ORDER_BY);
		AST tmp131_AST = null;
		tmp131_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp131_AST);
		match(POSITIONAL_VAR);
		AST tmp132_AST = null;
		tmp132_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp132_AST);
		match(BEFORE);
		AST tmp133_AST = null;
		tmp133_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp133_AST);
		match(AFTER);
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
			AST tmp134_AST = null;
			tmp134_AST = astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp134_AST);
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
		_loop55:
		do {
			if ((LA(1)==COMMA)) {
				AST tmp137_AST = null;
				tmp137_AST = astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp137_AST);
				match(COMMA);
				exprSingle();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop55;
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
			case XQUERY:
			case VERSION:
			case LITERAL_declare:
			case LITERAL_namespace:
			case LITERAL_default:
			case LITERAL_function:
			case LITERAL_variable:
			case STRING_LITERAL:
			case LITERAL_element:
			case DOLLAR:
			case LITERAL_as:
			case LITERAL_empty:
			case STAR:
			case PLUS:
			case LITERAL_item:
			case LITERAL_for:
			case LITERAL_let:
			case LITERAL_some:
			case LITERAL_every:
			case LITERAL_if:
			case LITERAL_order:
			case LITERAL_by:
			case LITERAL_then:
			case LITERAL_else:
			case LITERAL_or:
			case LITERAL_and:
			case LT:
			case LITERAL_is:
			case LITERAL_isnot:
			case MINUS:
			case LITERAL_div:
			case LITERAL_mod:
			case LITERAL_union:
			case LITERAL_intersect:
			case LITERAL_except:
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
			case 120:
			case 121:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 124:
			case 125:
			case DOUBLE_LITERAL:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
			case LITERAL_comment:
			case 130:
			case 131:
			case XML_PI:
			case LITERAL_document:
			case LITERAL_collection:
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
			AST tmp138_AST = null;
			tmp138_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp138_AST);
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
		AST v_AST = null;
		AST nd_AST = null;
		AST dnd_AST = null;
		AST fd_AST = null;
		
		{
		boolean synPredMatched10 = false;
		if (((LA(1)==XQUERY))) {
			int _m10 = mark();
			synPredMatched10 = true;
			inputState.guessing++;
			try {
				{
				match(XQUERY);
				match(VERSION);
				}
			}
			catch (RecognitionException pe) {
				synPredMatched10 = false;
			}
			rewind(_m10);
			inputState.guessing--;
		}
		if ( synPredMatched10 ) {
			version();
			v_AST = (AST)returnAST;
			astFactory.addASTChild(currentAST, returnAST);
			match(SEMICOLON);
		}
		else if ((_tokenSet_0.member(LA(1)))) {
		}
		else {
			throw new NoViableAltException(LT(1), getFilename());
		}
		
		}
		{
		_loop21:
		do {
			if ((LA(1)==LITERAL_declare)) {
				{
				boolean synPredMatched14 = false;
				if (((LA(1)==LITERAL_declare))) {
					int _m14 = mark();
					synPredMatched14 = true;
					inputState.guessing++;
					try {
						{
						match(LITERAL_declare);
						match(LITERAL_namespace);
						}
					}
					catch (RecognitionException pe) {
						synPredMatched14 = false;
					}
					rewind(_m14);
					inputState.guessing--;
				}
				if ( synPredMatched14 ) {
					namespaceDecl();
					nd_AST = (AST)returnAST;
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
							match(LITERAL_default);
							}
						}
						catch (RecognitionException pe) {
							synPredMatched16 = false;
						}
						rewind(_m16);
						inputState.guessing--;
					}
					if ( synPredMatched16 ) {
						defaultNamespaceDecl();
						dnd_AST = (AST)returnAST;
						astFactory.addASTChild(currentAST, returnAST);
					}
					else {
						boolean synPredMatched18 = false;
						if (((LA(1)==LITERAL_declare))) {
							int _m18 = mark();
							synPredMatched18 = true;
							inputState.guessing++;
							try {
								{
								match(LITERAL_declare);
								match(LITERAL_function);
								}
							}
							catch (RecognitionException pe) {
								synPredMatched18 = false;
							}
							rewind(_m18);
							inputState.guessing--;
						}
						if ( synPredMatched18 ) {
							functionDecl();
							fd_AST = (AST)returnAST;
							astFactory.addASTChild(currentAST, returnAST);
						}
						else {
							boolean synPredMatched20 = false;
							if (((LA(1)==LITERAL_declare))) {
								int _m20 = mark();
								synPredMatched20 = true;
								inputState.guessing++;
								try {
									{
									match(LITERAL_declare);
									match(LITERAL_variable);
									}
								}
								catch (RecognitionException pe) {
									synPredMatched20 = false;
								}
								rewind(_m20);
								inputState.guessing--;
							}
							if ( synPredMatched20 ) {
								varDecl();
								astFactory.addASTChild(currentAST, returnAST);
							}
							else {
								throw new NoViableAltException(LT(1), getFilename());
							}
							}}}
							}
							match(SEMICOLON);
						}
						else {
							break _loop21;
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
		
		AST tmp141_AST = null;
		tmp141_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp141_AST);
		match(XQUERY);
		AST tmp142_AST = null;
		tmp142_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp142_AST);
		match(VERSION);
		v = LT(1);
		v_AST = astFactory.create(v);
		astFactory.addASTChild(currentAST, v_AST);
		match(STRING_LITERAL);
		if ( inputState.guessing==0 ) {
			version_AST = (AST)currentAST.root;
			version_AST= (AST)astFactory.make( (new ASTArray(1)).add(astFactory.create(VERSION_DECL,v.getText())));
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
		
		AST tmp143_AST = null;
		tmp143_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp143_AST);
		match(LITERAL_declare);
		AST tmp144_AST = null;
		tmp144_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp144_AST);
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
			namespaceDecl_AST= (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(NAMESPACE_DECL,prefix.getText())).add(uri_AST));
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
		
		AST tmp146_AST = null;
		tmp146_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp146_AST);
		match(LITERAL_declare);
		AST tmp147_AST = null;
		tmp147_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp147_AST);
		match(LITERAL_default);
		{
		switch ( LA(1)) {
		case LITERAL_element:
		{
			AST tmp148_AST = null;
			tmp148_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp148_AST);
			match(LITERAL_element);
			AST tmp149_AST = null;
			tmp149_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp149_AST);
			match(LITERAL_namespace);
			defu = LT(1);
			defu_AST = astFactory.create(defu);
			astFactory.addASTChild(currentAST, defu_AST);
			match(STRING_LITERAL);
			if ( inputState.guessing==0 ) {
				defaultNamespaceDecl_AST = (AST)currentAST.root;
				defaultNamespaceDecl_AST= (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(DEF_NAMESPACE_DECL,"defaultNamespaceDecl")).add(defu_AST));
				currentAST.root = defaultNamespaceDecl_AST;
				currentAST.child = defaultNamespaceDecl_AST!=null &&defaultNamespaceDecl_AST.getFirstChild()!=null ?
					defaultNamespaceDecl_AST.getFirstChild() : defaultNamespaceDecl_AST;
				currentAST.advanceChildToEnd();
			}
			break;
		}
		case LITERAL_function:
		{
			AST tmp150_AST = null;
			tmp150_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp150_AST);
			match(LITERAL_function);
			AST tmp151_AST = null;
			tmp151_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp151_AST);
			match(LITERAL_namespace);
			deff = LT(1);
			deff_AST = astFactory.create(deff);
			astFactory.addASTChild(currentAST, deff_AST);
			match(STRING_LITERAL);
			if ( inputState.guessing==0 ) {
				defaultNamespaceDecl_AST = (AST)currentAST.root;
				defaultNamespaceDecl_AST= (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(DEF_FUNCTION_NS_DECL,"defaultFunctionNSDecl")).add(deff_AST));
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
		String name= null;
		
		match(LITERAL_declare);
		match(LITERAL_function);
		name=qName();
		match(LPAREN);
		{
		switch ( LA(1)) {
		case DOLLAR:
		{
			paramList();
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
		{
		switch ( LA(1)) {
		case LITERAL_as:
		{
			returnType();
			astFactory.addASTChild(currentAST, returnAST);
			break;
		}
		case LCURLY:
		{
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		}
		functionBody();
		astFactory.addASTChild(currentAST, returnAST);
		if ( inputState.guessing==0 ) {
			functionDecl_AST = (AST)currentAST.root;
			functionDecl_AST= (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(FUNCTION_DECL,name)).add(functionDecl_AST));
			currentAST.root = functionDecl_AST;
			currentAST.child = functionDecl_AST!=null &&functionDecl_AST.getFirstChild()!=null ?
				functionDecl_AST.getFirstChild() : functionDecl_AST;
			currentAST.advanceChildToEnd();
		}
		functionDecl_AST = (AST)currentAST.root;
		returnAST = functionDecl_AST;
	}
	
	public final void varDecl() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST varDecl_AST = null;
		AST ex_AST = null;
		String varName= null;
		
		match(LITERAL_declare);
		match(LITERAL_variable);
		match(DOLLAR);
		varName=qName();
		{
		switch ( LA(1)) {
		case LITERAL_as:
		{
			typeDeclaration();
			astFactory.addASTChild(currentAST, returnAST);
			break;
		}
		case LCURLY:
		{
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		}
		match(LCURLY);
		expr();
		ex_AST = (AST)returnAST;
		astFactory.addASTChild(currentAST, returnAST);
		match(RCURLY);
		if ( inputState.guessing==0 ) {
			varDecl_AST = (AST)currentAST.root;
			varDecl_AST= (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(GLOBAL_VAR,varName)).add(varDecl_AST));
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
		
		
		boolean synPredMatched201 = false;
		if (((_tokenSet_1.member(LA(1))))) {
			int _m201 = mark();
			synPredMatched201 = true;
			inputState.guessing++;
			try {
				{
				ncnameOrKeyword();
				match(COLON);
				ncnameOrKeyword();
				}
			}
			catch (RecognitionException pe) {
				synPredMatched201 = false;
			}
			rewind(_m201);
			inputState.guessing--;
		}
		if ( synPredMatched201 ) {
			name=ncnameOrKeyword();
			astFactory.addASTChild(currentAST, returnAST);
			AST tmp161_AST = null;
			tmp161_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp161_AST);
			match(COLON);
			name2=ncnameOrKeyword();
			astFactory.addASTChild(currentAST, returnAST);
			if ( inputState.guessing==0 ) {
				name= name + ':' + name2;
			}
			qName_AST = (AST)currentAST.root;
		}
		else if ((_tokenSet_1.member(LA(1)))) {
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
	
	public final void typeDeclaration() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST typeDeclaration_AST = null;
		
		AST tmp162_AST = null;
		tmp162_AST = astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp162_AST);
		match(LITERAL_as);
		sequenceType();
		astFactory.addASTChild(currentAST, returnAST);
		typeDeclaration_AST = (AST)currentAST.root;
		returnAST = typeDeclaration_AST;
	}
	
	public final void paramList() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST paramList_AST = null;
		AST p1_AST = null;
		
		param();
		astFactory.addASTChild(currentAST, returnAST);
		{
		_loop35:
		do {
			if ((LA(1)==COMMA)) {
				match(COMMA);
				param();
				p1_AST = (AST)returnAST;
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop35;
			}
			
		} while (true);
		}
		paramList_AST = (AST)currentAST.root;
		returnAST = paramList_AST;
	}
	
	public final void returnType() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST returnType_AST = null;
		
		AST tmp164_AST = null;
		tmp164_AST = astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp164_AST);
		match(LITERAL_as);
		sequenceType();
		astFactory.addASTChild(currentAST, returnAST);
		returnType_AST = (AST)currentAST.root;
		returnAST = returnType_AST;
	}
	
	public final void functionBody() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST functionBody_AST = null;
		AST e_AST = null;
		
		AST tmp165_AST = null;
		tmp165_AST = astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp165_AST);
		match(LCURLY);
		expr();
		e_AST = (AST)returnAST;
		astFactory.addASTChild(currentAST, returnAST);
		match(RCURLY);
		functionBody_AST = (AST)currentAST.root;
		returnAST = functionBody_AST;
	}
	
	public final void sequenceType() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST sequenceType_AST = null;
		
		boolean synPredMatched41 = false;
		if (((LA(1)==LITERAL_empty))) {
			int _m41 = mark();
			synPredMatched41 = true;
			inputState.guessing++;
			try {
				{
				match(LITERAL_empty);
				match(LPAREN);
				}
			}
			catch (RecognitionException pe) {
				synPredMatched41 = false;
			}
			rewind(_m41);
			inputState.guessing--;
		}
		if ( synPredMatched41 ) {
			AST tmp167_AST = null;
			tmp167_AST = astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp167_AST);
			match(LITERAL_empty);
			match(LPAREN);
			match(RPAREN);
			sequenceType_AST = (AST)currentAST.root;
		}
		else if ((_tokenSet_2.member(LA(1)))) {
			itemType();
			astFactory.addASTChild(currentAST, returnAST);
			{
			switch ( LA(1)) {
			case QUESTION:
			case STAR:
			case PLUS:
			{
				occurrenceIndicator();
				astFactory.addASTChild(currentAST, returnAST);
				break;
			}
			case RPAREN:
			case LCURLY:
			case COMMA:
			case LITERAL_in:
			case LITERAL_at:
			case COLON:
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
		}
		else {
			throw new NoViableAltException(LT(1), getFilename());
		}
		
		returnAST = sequenceType_AST;
	}
	
	public final void param() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST param_AST = null;
		AST t_AST = null;
		String varName= null;
		
		match(DOLLAR);
		varName=qName();
		astFactory.addASTChild(currentAST, returnAST);
		{
		switch ( LA(1)) {
		case LITERAL_as:
		{
			typeDeclaration();
			t_AST = (AST)returnAST;
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
		if ( inputState.guessing==0 ) {
			param_AST = (AST)currentAST.root;
			param_AST= (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(VARIABLE_BINDING,varName)).add(t_AST));
			currentAST.root = param_AST;
			currentAST.child = param_AST!=null &&param_AST.getFirstChild()!=null ?
				param_AST.getFirstChild() : param_AST;
			currentAST.advanceChildToEnd();
		}
		param_AST = (AST)currentAST.root;
		returnAST = param_AST;
	}
	
	public final void itemType() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST itemType_AST = null;
		
		boolean synPredMatched46 = false;
		if (((LA(1)==LITERAL_item))) {
			int _m46 = mark();
			synPredMatched46 = true;
			inputState.guessing++;
			try {
				{
				match(LITERAL_item);
				match(LPAREN);
				}
			}
			catch (RecognitionException pe) {
				synPredMatched46 = false;
			}
			rewind(_m46);
			inputState.guessing--;
		}
		if ( synPredMatched46 ) {
			AST tmp171_AST = null;
			tmp171_AST = astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp171_AST);
			match(LITERAL_item);
			match(LPAREN);
			match(RPAREN);
			itemType_AST = (AST)currentAST.root;
		}
		else {
			boolean synPredMatched48 = false;
			if (((_tokenSet_3.member(LA(1))))) {
				int _m48 = mark();
				synPredMatched48 = true;
				inputState.guessing++;
				try {
					{
					matchNot(EOF);
					match(LPAREN);
					}
				}
				catch (RecognitionException pe) {
					synPredMatched48 = false;
				}
				rewind(_m48);
				inputState.guessing--;
			}
			if ( synPredMatched48 ) {
				kindTest();
				astFactory.addASTChild(currentAST, returnAST);
				itemType_AST = (AST)currentAST.root;
			}
			else if ((_tokenSet_1.member(LA(1)))) {
				atomicType();
				astFactory.addASTChild(currentAST, returnAST);
				itemType_AST = (AST)currentAST.root;
			}
			else {
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			returnAST = itemType_AST;
		}
		
	public final void occurrenceIndicator() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST occurrenceIndicator_AST = null;
		
		switch ( LA(1)) {
		case QUESTION:
		{
			AST tmp174_AST = null;
			tmp174_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp174_AST);
			match(QUESTION);
			occurrenceIndicator_AST = (AST)currentAST.root;
			break;
		}
		case STAR:
		{
			AST tmp175_AST = null;
			tmp175_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp175_AST);
			match(STAR);
			occurrenceIndicator_AST = (AST)currentAST.root;
			break;
		}
		case PLUS:
		{
			AST tmp176_AST = null;
			tmp176_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp176_AST);
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
		case LITERAL_element:
		{
			elementTest();
			astFactory.addASTChild(currentAST, returnAST);
			kindTest_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_attribute:
		{
			attributeTest();
			astFactory.addASTChild(currentAST, returnAST);
			kindTest_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_comment:
		{
			commentTest();
			astFactory.addASTChild(currentAST, returnAST);
			kindTest_AST = (AST)currentAST.root;
			break;
		}
		case 130:
		{
			piTest();
			astFactory.addASTChild(currentAST, returnAST);
			kindTest_AST = (AST)currentAST.root;
			break;
		}
		case 131:
		{
			documentTest();
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
	
	public final void atomicType() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST atomicType_AST = null;
		String name= null;
		
		name=qName();
		astFactory.addASTChild(currentAST, returnAST);
		if ( inputState.guessing==0 ) {
			atomicType_AST = (AST)currentAST.root;
			atomicType_AST= astFactory.create(ATOMIC_TYPE,name);
			currentAST.root = atomicType_AST;
			currentAST.child = atomicType_AST!=null &&atomicType_AST.getFirstChild()!=null ?
				atomicType_AST.getFirstChild() : atomicType_AST;
			currentAST.advanceChildToEnd();
		}
		atomicType_AST = (AST)currentAST.root;
		returnAST = atomicType_AST;
	}
	
	public final void singleType() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST singleType_AST = null;
		
		atomicType();
		astFactory.addASTChild(currentAST, returnAST);
		{
		switch ( LA(1)) {
		case QUESTION:
		{
			AST tmp177_AST = null;
			tmp177_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp177_AST);
			match(QUESTION);
			break;
		}
		case EOF:
		case RPAREN:
		case RCURLY:
		case COMMA:
		case LITERAL_empty:
		case LITERAL_for:
		case LITERAL_let:
		case LITERAL_where:
		case LITERAL_return:
		case LITERAL_order:
		case LITERAL_ascending:
		case LITERAL_descending:
		case LITERAL_satisfies:
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
		singleType_AST = (AST)currentAST.root;
		returnAST = singleType_AST;
	}
	
	public final void exprSingle() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST exprSingle_AST = null;
		
		boolean synPredMatched59 = false;
		if (((LA(1)==LITERAL_for||LA(1)==LITERAL_let))) {
			int _m59 = mark();
			synPredMatched59 = true;
			inputState.guessing++;
			try {
				{
				{
				switch ( LA(1)) {
				case LITERAL_for:
				{
					match(LITERAL_for);
					break;
				}
				case LITERAL_let:
				{
					match(LITERAL_let);
					break;
				}
				default:
				{
					throw new NoViableAltException(LT(1), getFilename());
				}
				}
				}
				match(DOLLAR);
				}
			}
			catch (RecognitionException pe) {
				synPredMatched59 = false;
			}
			rewind(_m59);
			inputState.guessing--;
		}
		if ( synPredMatched59 ) {
			flworExpr();
			astFactory.addASTChild(currentAST, returnAST);
			exprSingle_AST = (AST)currentAST.root;
		}
		else {
			boolean synPredMatched62 = false;
			if (((LA(1)==LITERAL_some||LA(1)==LITERAL_every))) {
				int _m62 = mark();
				synPredMatched62 = true;
				inputState.guessing++;
				try {
					{
					{
					switch ( LA(1)) {
					case LITERAL_some:
					{
						match(LITERAL_some);
						break;
					}
					case LITERAL_every:
					{
						match(LITERAL_every);
						break;
					}
					default:
					{
						throw new NoViableAltException(LT(1), getFilename());
					}
					}
					}
					match(DOLLAR);
					}
				}
				catch (RecognitionException pe) {
					synPredMatched62 = false;
				}
				rewind(_m62);
				inputState.guessing--;
			}
			if ( synPredMatched62 ) {
				quantifiedExpr();
				astFactory.addASTChild(currentAST, returnAST);
				exprSingle_AST = (AST)currentAST.root;
			}
			else {
				boolean synPredMatched64 = false;
				if (((LA(1)==LITERAL_if))) {
					int _m64 = mark();
					synPredMatched64 = true;
					inputState.guessing++;
					try {
						{
						match(LITERAL_if);
						match(LPAREN);
						}
					}
					catch (RecognitionException pe) {
						synPredMatched64 = false;
					}
					rewind(_m64);
					inputState.guessing--;
				}
				if ( synPredMatched64 ) {
					ifExpr();
					astFactory.addASTChild(currentAST, returnAST);
					exprSingle_AST = (AST)currentAST.root;
				}
				else if ((_tokenSet_4.member(LA(1)))) {
					orExpr();
					astFactory.addASTChild(currentAST, returnAST);
					exprSingle_AST = (AST)currentAST.root;
				}
				else {
					throw new NoViableAltException(LT(1), getFilename());
				}
				}}
				returnAST = exprSingle_AST;
			}
			
	public final void flworExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST flworExpr_AST = null;
		
		{
		int _cnt67=0;
		_loop67:
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
				if ( _cnt67>=1 ) { break _loop67; } else {throw new NoViableAltException(LT(1), getFilename());}
			}
			}
			_cnt67++;
		} while (true);
		}
		{
		switch ( LA(1)) {
		case LITERAL_where:
		{
			AST tmp178_AST = null;
			tmp178_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp178_AST);
			match(LITERAL_where);
			expr();
			astFactory.addASTChild(currentAST, returnAST);
			break;
		}
		case LITERAL_return:
		case LITERAL_order:
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
		case LITERAL_order:
		{
			orderByClause();
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
		AST tmp179_AST = null;
		tmp179_AST = astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp179_AST);
		match(LITERAL_return);
		exprSingle();
		astFactory.addASTChild(currentAST, returnAST);
		flworExpr_AST = (AST)currentAST.root;
		returnAST = flworExpr_AST;
	}
	
	public final void quantifiedExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST quantifiedExpr_AST = null;
		
		{
		switch ( LA(1)) {
		case LITERAL_some:
		{
			AST tmp180_AST = null;
			tmp180_AST = astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp180_AST);
			match(LITERAL_some);
			break;
		}
		case LITERAL_every:
		{
			AST tmp181_AST = null;
			tmp181_AST = astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp181_AST);
			match(LITERAL_every);
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		}
		quantifiedInVarBinding();
		astFactory.addASTChild(currentAST, returnAST);
		{
		_loop94:
		do {
			if ((LA(1)==COMMA)) {
				match(COMMA);
				quantifiedInVarBinding();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop94;
			}
			
		} while (true);
		}
		match(LITERAL_satisfies);
		exprSingle();
		astFactory.addASTChild(currentAST, returnAST);
		quantifiedExpr_AST = (AST)currentAST.root;
		returnAST = quantifiedExpr_AST;
	}
	
	public final void ifExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST ifExpr_AST = null;
		
		AST tmp184_AST = null;
		tmp184_AST = astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp184_AST);
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
	
	public final void orExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST orExpr_AST = null;
		
		andExpr();
		astFactory.addASTChild(currentAST, returnAST);
		{
		_loop100:
		do {
			if ((LA(1)==LITERAL_or)) {
				AST tmp189_AST = null;
				tmp189_AST = astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp189_AST);
				match(LITERAL_or);
				andExpr();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop100;
			}
			
		} while (true);
		}
		orExpr_AST = (AST)currentAST.root;
		returnAST = orExpr_AST;
	}
	
	public final void forClause() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST forClause_AST = null;
		
		AST tmp190_AST = null;
		tmp190_AST = astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp190_AST);
		match(LITERAL_for);
		inVarBinding();
		astFactory.addASTChild(currentAST, returnAST);
		{
		_loop72:
		do {
			if ((LA(1)==COMMA)) {
				match(COMMA);
				inVarBinding();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop72;
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
		
		AST tmp192_AST = null;
		tmp192_AST = astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp192_AST);
		match(LITERAL_let);
		letVarBinding();
		astFactory.addASTChild(currentAST, returnAST);
		{
		_loop75:
		do {
			if ((LA(1)==COMMA)) {
				match(COMMA);
				letVarBinding();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop75;
			}
			
		} while (true);
		}
		letClause_AST = (AST)currentAST.root;
		returnAST = letClause_AST;
	}
	
	public final void orderByClause() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST orderByClause_AST = null;
		
		match(LITERAL_order);
		match(LITERAL_by);
		orderSpecList();
		astFactory.addASTChild(currentAST, returnAST);
		if ( inputState.guessing==0 ) {
			orderByClause_AST = (AST)currentAST.root;
			orderByClause_AST= (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(ORDER_BY,"order by")).add(orderByClause_AST));
			currentAST.root = orderByClause_AST;
			currentAST.child = orderByClause_AST!=null &&orderByClause_AST.getFirstChild()!=null ?
				orderByClause_AST.getFirstChild() : orderByClause_AST;
			currentAST.advanceChildToEnd();
		}
		orderByClause_AST = (AST)currentAST.root;
		returnAST = orderByClause_AST;
	}
	
	public final void inVarBinding() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST inVarBinding_AST = null;
		String varName;
		
		match(DOLLAR);
		varName=qName();
		{
		switch ( LA(1)) {
		case LITERAL_as:
		{
			typeDeclaration();
			astFactory.addASTChild(currentAST, returnAST);
			break;
		}
		case LITERAL_in:
		case LITERAL_at:
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
		case LITERAL_at:
		{
			positionalVar();
			astFactory.addASTChild(currentAST, returnAST);
			break;
		}
		case LITERAL_in:
		{
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		}
		match(LITERAL_in);
		exprSingle();
		astFactory.addASTChild(currentAST, returnAST);
		if ( inputState.guessing==0 ) {
			inVarBinding_AST = (AST)currentAST.root;
			inVarBinding_AST= (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(VARIABLE_BINDING,varName)).add(inVarBinding_AST));
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
		String varName;
		
		match(DOLLAR);
		varName=qName();
		{
		switch ( LA(1)) {
		case LITERAL_as:
		{
			typeDeclaration();
			astFactory.addASTChild(currentAST, returnAST);
			break;
		}
		case COLON:
		{
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		}
		match(COLON);
		match(EQ);
		exprSingle();
		astFactory.addASTChild(currentAST, returnAST);
		if ( inputState.guessing==0 ) {
			letVarBinding_AST = (AST)currentAST.root;
			letVarBinding_AST= (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(VARIABLE_BINDING,varName)).add(letVarBinding_AST));
			currentAST.root = letVarBinding_AST;
			currentAST.child = letVarBinding_AST!=null &&letVarBinding_AST.getFirstChild()!=null ?
				letVarBinding_AST.getFirstChild() : letVarBinding_AST;
			currentAST.advanceChildToEnd();
		}
		letVarBinding_AST = (AST)currentAST.root;
		returnAST = letVarBinding_AST;
	}
	
	public final void positionalVar() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST positionalVar_AST = null;
		String varName;
		
		AST tmp201_AST = null;
		tmp201_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp201_AST);
		match(LITERAL_at);
		match(DOLLAR);
		varName=qName();
		astFactory.addASTChild(currentAST, returnAST);
		if ( inputState.guessing==0 ) {
			positionalVar_AST = (AST)currentAST.root;
			positionalVar_AST= astFactory.create(POSITIONAL_VAR,varName);
			currentAST.root = positionalVar_AST;
			currentAST.child = positionalVar_AST!=null &&positionalVar_AST.getFirstChild()!=null ?
				positionalVar_AST.getFirstChild() : positionalVar_AST;
			currentAST.advanceChildToEnd();
		}
		positionalVar_AST = (AST)currentAST.root;
		returnAST = positionalVar_AST;
	}
	
	public final void orderSpecList() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST orderSpecList_AST = null;
		
		orderSpec();
		astFactory.addASTChild(currentAST, returnAST);
		{
		_loop85:
		do {
			if ((LA(1)==COMMA)) {
				match(COMMA);
				orderSpec();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop85;
			}
			
		} while (true);
		}
		orderSpecList_AST = (AST)currentAST.root;
		returnAST = orderSpecList_AST;
	}
	
	public final void orderSpec() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST orderSpec_AST = null;
		
		exprSingle();
		astFactory.addASTChild(currentAST, returnAST);
		orderModifier();
		astFactory.addASTChild(currentAST, returnAST);
		orderSpec_AST = (AST)currentAST.root;
		returnAST = orderSpec_AST;
	}
	
	public final void orderModifier() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST orderModifier_AST = null;
		
		{
		switch ( LA(1)) {
		case LITERAL_ascending:
		{
			AST tmp204_AST = null;
			tmp204_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp204_AST);
			match(LITERAL_ascending);
			break;
		}
		case LITERAL_descending:
		{
			AST tmp205_AST = null;
			tmp205_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp205_AST);
			match(LITERAL_descending);
			break;
		}
		case COMMA:
		case LITERAL_empty:
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
		{
		switch ( LA(1)) {
		case LITERAL_empty:
		{
			AST tmp206_AST = null;
			tmp206_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp206_AST);
			match(LITERAL_empty);
			{
			switch ( LA(1)) {
			case LITERAL_greatest:
			{
				AST tmp207_AST = null;
				tmp207_AST = astFactory.create(LT(1));
				astFactory.addASTChild(currentAST, tmp207_AST);
				match(LITERAL_greatest);
				break;
			}
			case LITERAL_least:
			{
				AST tmp208_AST = null;
				tmp208_AST = astFactory.create(LT(1));
				astFactory.addASTChild(currentAST, tmp208_AST);
				match(LITERAL_least);
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			}
			break;
		}
		case COMMA:
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
		orderModifier_AST = (AST)currentAST.root;
		returnAST = orderModifier_AST;
	}
	
	public final void quantifiedInVarBinding() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST quantifiedInVarBinding_AST = null;
		String varName;
		
		match(DOLLAR);
		varName=qName();
		{
		switch ( LA(1)) {
		case LITERAL_as:
		{
			typeDeclaration();
			astFactory.addASTChild(currentAST, returnAST);
			break;
		}
		case LITERAL_in:
		{
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		}
		match(LITERAL_in);
		exprSingle();
		astFactory.addASTChild(currentAST, returnAST);
		if ( inputState.guessing==0 ) {
			quantifiedInVarBinding_AST = (AST)currentAST.root;
			quantifiedInVarBinding_AST = (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(VARIABLE_BINDING,varName)).add(quantifiedInVarBinding_AST));
			currentAST.root = quantifiedInVarBinding_AST;
			currentAST.child = quantifiedInVarBinding_AST!=null &&quantifiedInVarBinding_AST.getFirstChild()!=null ?
				quantifiedInVarBinding_AST.getFirstChild() : quantifiedInVarBinding_AST;
			currentAST.advanceChildToEnd();
		}
		quantifiedInVarBinding_AST = (AST)currentAST.root;
		returnAST = quantifiedInVarBinding_AST;
	}
	
	public final void andExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST andExpr_AST = null;
		
		castExpr();
		astFactory.addASTChild(currentAST, returnAST);
		{
		_loop103:
		do {
			if ((LA(1)==LITERAL_and)) {
				AST tmp211_AST = null;
				tmp211_AST = astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp211_AST);
				match(LITERAL_and);
				castExpr();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop103;
			}
			
		} while (true);
		}
		andExpr_AST = (AST)currentAST.root;
		returnAST = andExpr_AST;
	}
	
	public final void castExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST castExpr_AST = null;
		
		comparisonExpr();
		astFactory.addASTChild(currentAST, returnAST);
		{
		switch ( LA(1)) {
		case LITERAL_cast:
		{
			AST tmp212_AST = null;
			tmp212_AST = astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp212_AST);
			match(LITERAL_cast);
			match(LITERAL_as);
			singleType();
			astFactory.addASTChild(currentAST, returnAST);
			break;
		}
		case EOF:
		case RPAREN:
		case RCURLY:
		case COMMA:
		case LITERAL_empty:
		case LITERAL_for:
		case LITERAL_let:
		case LITERAL_where:
		case LITERAL_return:
		case LITERAL_order:
		case LITERAL_ascending:
		case LITERAL_descending:
		case LITERAL_satisfies:
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
		castExpr_AST = (AST)currentAST.root;
		returnAST = castExpr_AST;
	}
	
	public final void comparisonExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST comparisonExpr_AST = null;
		
		rangeExpr();
		astFactory.addASTChild(currentAST, returnAST);
		{
		switch ( LA(1)) {
		case LITERAL_eq:
		case LITERAL_ne:
		case LITERAL_lt:
		case LITERAL_le:
		case LITERAL_gt:
		case LITERAL_ge:
		{
			{
			{
			switch ( LA(1)) {
			case LITERAL_eq:
			{
				AST tmp214_AST = null;
				tmp214_AST = astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp214_AST);
				match(LITERAL_eq);
				break;
			}
			case LITERAL_ne:
			{
				AST tmp215_AST = null;
				tmp215_AST = astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp215_AST);
				match(LITERAL_ne);
				break;
			}
			case LITERAL_lt:
			{
				AST tmp216_AST = null;
				tmp216_AST = astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp216_AST);
				match(LITERAL_lt);
				break;
			}
			case LITERAL_le:
			{
				AST tmp217_AST = null;
				tmp217_AST = astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp217_AST);
				match(LITERAL_le);
				break;
			}
			case LITERAL_gt:
			{
				AST tmp218_AST = null;
				tmp218_AST = astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp218_AST);
				match(LITERAL_gt);
				break;
			}
			case LITERAL_ge:
			{
				AST tmp219_AST = null;
				tmp219_AST = astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp219_AST);
				match(LITERAL_ge);
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
		case LITERAL_is:
		case LITERAL_isnot:
		{
			{
			{
			switch ( LA(1)) {
			case LITERAL_is:
			{
				AST tmp220_AST = null;
				tmp220_AST = astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp220_AST);
				match(LITERAL_is);
				break;
			}
			case LITERAL_isnot:
			{
				AST tmp221_AST = null;
				tmp221_AST = astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp221_AST);
				match(LITERAL_isnot);
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
				AST tmp222_AST = null;
				tmp222_AST = astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp222_AST);
				match(ANDEQ);
				break;
			}
			case OREQ:
			{
				AST tmp223_AST = null;
				tmp223_AST = astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp223_AST);
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
		case LITERAL_empty:
		case LITERAL_for:
		case LITERAL_let:
		case LITERAL_where:
		case LITERAL_return:
		case LITERAL_order:
		case LITERAL_ascending:
		case LITERAL_descending:
		case LITERAL_satisfies:
		case LITERAL_else:
		case LITERAL_or:
		case LITERAL_and:
		case LITERAL_cast:
		case RPPAREN:
		{
			break;
		}
		default:
			boolean synPredMatched109 = false;
			if (((LA(1)==LT))) {
				int _m109 = mark();
				synPredMatched109 = true;
				inputState.guessing++;
				try {
					{
					match(LT);
					match(LT);
					}
				}
				catch (RecognitionException pe) {
					synPredMatched109 = false;
				}
				rewind(_m109);
				inputState.guessing--;
			}
			if ( synPredMatched109 ) {
				match(LT);
				match(LT);
				rangeExpr();
				astFactory.addASTChild(currentAST, returnAST);
				if ( inputState.guessing==0 ) {
					comparisonExpr_AST = (AST)currentAST.root;
					
									comparisonExpr_AST = (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(BEFORE,"<<")).add(comparisonExpr_AST));
								
					currentAST.root = comparisonExpr_AST;
					currentAST.child = comparisonExpr_AST!=null &&comparisonExpr_AST.getFirstChild()!=null ?
						comparisonExpr_AST.getFirstChild() : comparisonExpr_AST;
					currentAST.advanceChildToEnd();
				}
			}
			else {
				boolean synPredMatched111 = false;
				if (((LA(1)==GT))) {
					int _m111 = mark();
					synPredMatched111 = true;
					inputState.guessing++;
					try {
						{
						match(GT);
						match(GT);
						}
					}
					catch (RecognitionException pe) {
						synPredMatched111 = false;
					}
					rewind(_m111);
					inputState.guessing--;
				}
				if ( synPredMatched111 ) {
					match(GT);
					match(GT);
					rangeExpr();
					astFactory.addASTChild(currentAST, returnAST);
					if ( inputState.guessing==0 ) {
						comparisonExpr_AST = (AST)currentAST.root;
						
										comparisonExpr_AST = (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(AFTER,">>")).add(comparisonExpr_AST));
									
						currentAST.root = comparisonExpr_AST;
						currentAST.child = comparisonExpr_AST!=null &&comparisonExpr_AST.getFirstChild()!=null ?
							comparisonExpr_AST.getFirstChild() : comparisonExpr_AST;
						currentAST.advanceChildToEnd();
					}
				}
				else if ((_tokenSet_5.member(LA(1)))) {
					{
					{
					switch ( LA(1)) {
					case EQ:
					{
						AST tmp228_AST = null;
						tmp228_AST = astFactory.create(LT(1));
						astFactory.makeASTRoot(currentAST, tmp228_AST);
						match(EQ);
						break;
					}
					case NEQ:
					{
						AST tmp229_AST = null;
						tmp229_AST = astFactory.create(LT(1));
						astFactory.makeASTRoot(currentAST, tmp229_AST);
						match(NEQ);
						break;
					}
					case GT:
					{
						AST tmp230_AST = null;
						tmp230_AST = astFactory.create(LT(1));
						astFactory.makeASTRoot(currentAST, tmp230_AST);
						match(GT);
						break;
					}
					case GTEQ:
					{
						AST tmp231_AST = null;
						tmp231_AST = astFactory.create(LT(1));
						astFactory.makeASTRoot(currentAST, tmp231_AST);
						match(GTEQ);
						break;
					}
					case LT:
					{
						AST tmp232_AST = null;
						tmp232_AST = astFactory.create(LT(1));
						astFactory.makeASTRoot(currentAST, tmp232_AST);
						match(LT);
						break;
					}
					case LTEQ:
					{
						AST tmp233_AST = null;
						tmp233_AST = astFactory.create(LT(1));
						astFactory.makeASTRoot(currentAST, tmp233_AST);
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
				}
			else {
				throw new NoViableAltException(LT(1), getFilename());
			}
			}}
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
			AST tmp234_AST = null;
			tmp234_AST = astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp234_AST);
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
		case LITERAL_empty:
		case LITERAL_for:
		case LITERAL_let:
		case LITERAL_where:
		case LITERAL_return:
		case LITERAL_order:
		case LITERAL_ascending:
		case LITERAL_descending:
		case LITERAL_satisfies:
		case LITERAL_else:
		case LITERAL_or:
		case LITERAL_and:
		case LITERAL_cast:
		case LT:
		case GT:
		case LITERAL_eq:
		case LITERAL_ne:
		case LITERAL_lt:
		case LITERAL_le:
		case LITERAL_gt:
		case LITERAL_ge:
		case NEQ:
		case GTEQ:
		case LTEQ:
		case LITERAL_is:
		case LITERAL_isnot:
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
		_loop125:
		do {
			if ((LA(1)==PLUS||LA(1)==MINUS)) {
				{
				switch ( LA(1)) {
				case PLUS:
				{
					AST tmp235_AST = null;
					tmp235_AST = astFactory.create(LT(1));
					astFactory.makeASTRoot(currentAST, tmp235_AST);
					match(PLUS);
					break;
				}
				case MINUS:
				{
					AST tmp236_AST = null;
					tmp236_AST = astFactory.create(LT(1));
					astFactory.makeASTRoot(currentAST, tmp236_AST);
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
				break _loop125;
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
		_loop129:
		do {
			if ((_tokenSet_6.member(LA(1)))) {
				{
				switch ( LA(1)) {
				case STAR:
				{
					AST tmp237_AST = null;
					tmp237_AST = astFactory.create(LT(1));
					astFactory.makeASTRoot(currentAST, tmp237_AST);
					match(STAR);
					break;
				}
				case LITERAL_div:
				{
					AST tmp238_AST = null;
					tmp238_AST = astFactory.create(LT(1));
					astFactory.makeASTRoot(currentAST, tmp238_AST);
					match(LITERAL_div);
					break;
				}
				case LITERAL_idiv:
				{
					AST tmp239_AST = null;
					tmp239_AST = astFactory.create(LT(1));
					astFactory.makeASTRoot(currentAST, tmp239_AST);
					match(LITERAL_idiv);
					break;
				}
				case LITERAL_mod:
				{
					AST tmp240_AST = null;
					tmp240_AST = astFactory.create(LT(1));
					astFactory.makeASTRoot(currentAST, tmp240_AST);
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
				break _loop129;
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
			AST tmp241_AST = null;
			tmp241_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp241_AST);
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
			AST tmp242_AST = null;
			tmp242_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp242_AST);
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
		case XQUERY:
		case VERSION:
		case LITERAL_namespace:
		case LITERAL_default:
		case LITERAL_function:
		case LITERAL_variable:
		case STRING_LITERAL:
		case LITERAL_element:
		case DOLLAR:
		case LITERAL_as:
		case LITERAL_empty:
		case STAR:
		case LITERAL_item:
		case LITERAL_for:
		case LITERAL_let:
		case LITERAL_some:
		case LITERAL_every:
		case LITERAL_if:
		case LITERAL_order:
		case LITERAL_by:
		case LITERAL_then:
		case LITERAL_else:
		case LITERAL_or:
		case LITERAL_and:
		case LT:
		case LITERAL_is:
		case LITERAL_isnot:
		case LITERAL_div:
		case LITERAL_mod:
		case LITERAL_union:
		case LITERAL_intersect:
		case LITERAL_except:
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
		case 120:
		case 121:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 124:
		case 125:
		case DOUBLE_LITERAL:
		case DECIMAL_LITERAL:
		case INTEGER_LITERAL:
		case LITERAL_comment:
		case 130:
		case 131:
		case XML_PI:
		case LITERAL_document:
		case LITERAL_collection:
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
		
		intersectExceptExpr();
		astFactory.addASTChild(currentAST, returnAST);
		{
		switch ( LA(1)) {
		case LITERAL_union:
		case UNION:
		{
			{
			switch ( LA(1)) {
			case LITERAL_union:
			{
				match(LITERAL_union);
				break;
			}
			case UNION:
			{
				match(UNION);
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			}
			intersectExceptExpr();
			astFactory.addASTChild(currentAST, returnAST);
			if ( inputState.guessing==0 ) {
				unionExpr_AST = (AST)currentAST.root;
				
							unionExpr_AST = (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(UNION,"union")).add(unionExpr_AST));
						
				currentAST.root = unionExpr_AST;
				currentAST.child = unionExpr_AST!=null &&unionExpr_AST.getFirstChild()!=null ?
					unionExpr_AST.getFirstChild() : unionExpr_AST;
				currentAST.advanceChildToEnd();
			}
			break;
		}
		case EOF:
		case RPAREN:
		case EQ:
		case RCURLY:
		case COMMA:
		case LITERAL_empty:
		case STAR:
		case PLUS:
		case LITERAL_for:
		case LITERAL_let:
		case LITERAL_where:
		case LITERAL_return:
		case LITERAL_order:
		case LITERAL_ascending:
		case LITERAL_descending:
		case LITERAL_satisfies:
		case LITERAL_else:
		case LITERAL_or:
		case LITERAL_and:
		case LITERAL_cast:
		case LT:
		case GT:
		case LITERAL_eq:
		case LITERAL_ne:
		case LITERAL_lt:
		case LITERAL_le:
		case LITERAL_gt:
		case LITERAL_ge:
		case NEQ:
		case GTEQ:
		case LTEQ:
		case LITERAL_is:
		case LITERAL_isnot:
		case ANDEQ:
		case OREQ:
		case LITERAL_to:
		case MINUS:
		case LITERAL_div:
		case LITERAL_idiv:
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
	
	public final void intersectExceptExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST intersectExceptExpr_AST = null;
		
		pathExpr();
		astFactory.addASTChild(currentAST, returnAST);
		{
		_loop137:
		do {
			if ((LA(1)==LITERAL_intersect||LA(1)==LITERAL_except)) {
				{
				switch ( LA(1)) {
				case LITERAL_intersect:
				{
					AST tmp245_AST = null;
					tmp245_AST = astFactory.create(LT(1));
					astFactory.makeASTRoot(currentAST, tmp245_AST);
					match(LITERAL_intersect);
					break;
				}
				case LITERAL_except:
				{
					AST tmp246_AST = null;
					tmp246_AST = astFactory.create(LT(1));
					astFactory.makeASTRoot(currentAST, tmp246_AST);
					match(LITERAL_except);
					break;
				}
				default:
				{
					throw new NoViableAltException(LT(1), getFilename());
				}
				}
				}
				pathExpr();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop137;
			}
			
		} while (true);
		}
		intersectExceptExpr_AST = (AST)currentAST.root;
		returnAST = intersectExceptExpr_AST;
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
		case XQUERY:
		case VERSION:
		case LITERAL_namespace:
		case LITERAL_default:
		case LITERAL_function:
		case LITERAL_variable:
		case STRING_LITERAL:
		case LITERAL_element:
		case DOLLAR:
		case LITERAL_as:
		case LITERAL_empty:
		case STAR:
		case LITERAL_item:
		case LITERAL_for:
		case LITERAL_let:
		case LITERAL_some:
		case LITERAL_every:
		case LITERAL_if:
		case LITERAL_order:
		case LITERAL_by:
		case LITERAL_then:
		case LITERAL_else:
		case LITERAL_or:
		case LITERAL_and:
		case LT:
		case LITERAL_is:
		case LITERAL_isnot:
		case LITERAL_div:
		case LITERAL_mod:
		case LITERAL_union:
		case LITERAL_intersect:
		case LITERAL_except:
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
		case 120:
		case 121:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 124:
		case 125:
		case DOUBLE_LITERAL:
		case DECIMAL_LITERAL:
		case INTEGER_LITERAL:
		case LITERAL_comment:
		case 130:
		case 131:
		case XML_PI:
		case LITERAL_document:
		case LITERAL_collection:
		{
			relativePathExpr();
			astFactory.addASTChild(currentAST, returnAST);
			pathExpr_AST = (AST)currentAST.root;
			break;
		}
		case DSLASH:
		{
			AST tmp247_AST = null;
			tmp247_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp247_AST);
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
			boolean synPredMatched140 = false;
			if (((LA(1)==SLASH))) {
				int _m140 = mark();
				synPredMatched140 = true;
				inputState.guessing++;
				try {
					{
					match(SLASH);
					relativePathExpr();
					}
				}
				catch (RecognitionException pe) {
					synPredMatched140 = false;
				}
				rewind(_m140);
				inputState.guessing--;
			}
			if ( synPredMatched140 ) {
				AST tmp248_AST = null;
				tmp248_AST = astFactory.create(LT(1));
				astFactory.addASTChild(currentAST, tmp248_AST);
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
				AST tmp249_AST = null;
				tmp249_AST = astFactory.create(LT(1));
				astFactory.addASTChild(currentAST, tmp249_AST);
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
		_loop144:
		do {
			if ((LA(1)==SLASH||LA(1)==DSLASH)) {
				{
				switch ( LA(1)) {
				case SLASH:
				{
					AST tmp250_AST = null;
					tmp250_AST = astFactory.create(LT(1));
					astFactory.makeASTRoot(currentAST, tmp250_AST);
					match(SLASH);
					break;
				}
				case DSLASH:
				{
					AST tmp251_AST = null;
					tmp251_AST = astFactory.create(LT(1));
					astFactory.makeASTRoot(currentAST, tmp251_AST);
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
				break _loop144;
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
		
		boolean synPredMatched148 = false;
		if (((_tokenSet_7.member(LA(1))))) {
			int _m148 = mark();
			synPredMatched148 = true;
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
				case LITERAL_element:
				{
					match(LITERAL_element);
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
				synPredMatched148 = false;
			}
			rewind(_m148);
			inputState.guessing--;
		}
		if ( synPredMatched148 ) {
			axisStep();
			astFactory.addASTChild(currentAST, returnAST);
			stepExpr_AST = (AST)currentAST.root;
		}
		else {
			boolean synPredMatched151 = false;
			if (((_tokenSet_8.member(LA(1))))) {
				int _m151 = mark();
				synPredMatched151 = true;
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
					case XQUERY:
					case VERSION:
					case LITERAL_namespace:
					case LITERAL_default:
					case LITERAL_function:
					case LITERAL_variable:
					case LITERAL_as:
					case LITERAL_empty:
					case LITERAL_item:
					case LITERAL_for:
					case LITERAL_let:
					case LITERAL_some:
					case LITERAL_every:
					case LITERAL_if:
					case LITERAL_order:
					case LITERAL_by:
					case LITERAL_then:
					case LITERAL_else:
					case LITERAL_or:
					case LITERAL_and:
					case LITERAL_is:
					case LITERAL_isnot:
					case LITERAL_div:
					case LITERAL_mod:
					case LITERAL_union:
					case LITERAL_intersect:
					case LITERAL_except:
					case LITERAL_text:
					case LITERAL_node:
					case LITERAL_child:
					case LITERAL_self:
					case LITERAL_attribute:
					case LITERAL_descendant:
					case 120:
					case 121:
					case LITERAL_parent:
					case LITERAL_ancestor:
					case 124:
					case 125:
					case LITERAL_comment:
					case LITERAL_document:
					case LITERAL_collection:
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
					case XML_COMMENT:
					{
						match(XML_COMMENT);
						break;
					}
					case LT:
					{
						match(LT);
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
					synPredMatched151 = false;
				}
				rewind(_m151);
				inputState.guessing--;
			}
			if ( synPredMatched151 ) {
				filterStep();
				astFactory.addASTChild(currentAST, returnAST);
				stepExpr_AST = (AST)currentAST.root;
			}
			else if ((_tokenSet_7.member(LA(1)))) {
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
			AST tmp252_AST = null;
			tmp252_AST = astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp252_AST);
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
		
		boolean synPredMatched160 = false;
		if ((((LA(1) >= LITERAL_child && LA(1) <= 121)))) {
			int _m160 = mark();
			synPredMatched160 = true;
			inputState.guessing++;
			try {
				{
				forwardAxisSpecifier();
				match(COLON);
				}
			}
			catch (RecognitionException pe) {
				synPredMatched160 = false;
			}
			rewind(_m160);
			inputState.guessing--;
		}
		if ( synPredMatched160 ) {
			forwardAxis();
			astFactory.addASTChild(currentAST, returnAST);
			nodeTest();
			astFactory.addASTChild(currentAST, returnAST);
			forwardOrReverseStep_AST = (AST)currentAST.root;
		}
		else {
			boolean synPredMatched162 = false;
			if ((((LA(1) >= LITERAL_parent && LA(1) <= 125)))) {
				int _m162 = mark();
				synPredMatched162 = true;
				inputState.guessing++;
				try {
					{
					reverseAxisSpecifier();
					match(COLON);
					}
				}
				catch (RecognitionException pe) {
					synPredMatched162 = false;
				}
				rewind(_m162);
				inputState.guessing--;
			}
			if ( synPredMatched162 ) {
				reverseAxis();
				astFactory.addASTChild(currentAST, returnAST);
				nodeTest();
				astFactory.addASTChild(currentAST, returnAST);
				forwardOrReverseStep_AST = (AST)currentAST.root;
			}
			else if ((_tokenSet_7.member(LA(1)))) {
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
		_loop156:
		do {
			if ((LA(1)==LPPAREN)) {
				predicate();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop156;
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
			AST tmp255_AST = null;
			tmp255_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp255_AST);
			match(LITERAL_child);
			forwardAxisSpecifier_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_self:
		{
			AST tmp256_AST = null;
			tmp256_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp256_AST);
			match(LITERAL_self);
			forwardAxisSpecifier_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_attribute:
		{
			AST tmp257_AST = null;
			tmp257_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp257_AST);
			match(LITERAL_attribute);
			forwardAxisSpecifier_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_descendant:
		{
			AST tmp258_AST = null;
			tmp258_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp258_AST);
			match(LITERAL_descendant);
			forwardAxisSpecifier_AST = (AST)currentAST.root;
			break;
		}
		case 120:
		{
			AST tmp259_AST = null;
			tmp259_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp259_AST);
			match(120);
			forwardAxisSpecifier_AST = (AST)currentAST.root;
			break;
		}
		case 121:
		{
			AST tmp260_AST = null;
			tmp260_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp260_AST);
			match(121);
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
		
		boolean synPredMatched171 = false;
		if (((_tokenSet_3.member(LA(1))))) {
			int _m171 = mark();
			synPredMatched171 = true;
			inputState.guessing++;
			try {
				{
				matchNot(EOF);
				match(LPAREN);
				}
			}
			catch (RecognitionException pe) {
				synPredMatched171 = false;
			}
			rewind(_m171);
			inputState.guessing--;
		}
		if ( synPredMatched171 ) {
			kindTest();
			astFactory.addASTChild(currentAST, returnAST);
			nodeTest_AST = (AST)currentAST.root;
		}
		else if ((_tokenSet_9.member(LA(1)))) {
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
			AST tmp263_AST = null;
			tmp263_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp263_AST);
			match(LITERAL_parent);
			reverseAxisSpecifier_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_ancestor:
		{
			AST tmp264_AST = null;
			tmp264_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp264_AST);
			match(LITERAL_ancestor);
			reverseAxisSpecifier_AST = (AST)currentAST.root;
			break;
		}
		case 124:
		{
			AST tmp265_AST = null;
			tmp265_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp265_AST);
			match(124);
			reverseAxisSpecifier_AST = (AST)currentAST.root;
			break;
		}
		case 125:
		{
			AST tmp266_AST = null;
			tmp266_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp266_AST);
			match(125);
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
		case XQUERY:
		case VERSION:
		case LITERAL_namespace:
		case LITERAL_default:
		case LITERAL_function:
		case LITERAL_variable:
		case LITERAL_element:
		case LITERAL_as:
		case LITERAL_empty:
		case STAR:
		case LITERAL_item:
		case LITERAL_for:
		case LITERAL_let:
		case LITERAL_some:
		case LITERAL_every:
		case LITERAL_if:
		case LITERAL_order:
		case LITERAL_by:
		case LITERAL_then:
		case LITERAL_else:
		case LITERAL_or:
		case LITERAL_and:
		case LITERAL_is:
		case LITERAL_isnot:
		case LITERAL_div:
		case LITERAL_mod:
		case LITERAL_union:
		case LITERAL_intersect:
		case LITERAL_except:
		case LITERAL_text:
		case LITERAL_node:
		case AT:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_attribute:
		case LITERAL_descendant:
		case 120:
		case 121:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 124:
		case 125:
		case LITERAL_comment:
		case 130:
		case 131:
		case LITERAL_document:
		case LITERAL_collection:
		{
			{
			switch ( LA(1)) {
			case AT:
			{
				AST tmp269_AST = null;
				tmp269_AST = astFactory.create(LT(1));
				astFactory.addASTChild(currentAST, tmp269_AST);
				match(AT);
				break;
			}
			case NCNAME:
			case XQUERY:
			case VERSION:
			case LITERAL_namespace:
			case LITERAL_default:
			case LITERAL_function:
			case LITERAL_variable:
			case LITERAL_element:
			case LITERAL_as:
			case LITERAL_empty:
			case STAR:
			case LITERAL_item:
			case LITERAL_for:
			case LITERAL_let:
			case LITERAL_some:
			case LITERAL_every:
			case LITERAL_if:
			case LITERAL_order:
			case LITERAL_by:
			case LITERAL_then:
			case LITERAL_else:
			case LITERAL_or:
			case LITERAL_and:
			case LITERAL_is:
			case LITERAL_isnot:
			case LITERAL_div:
			case LITERAL_mod:
			case LITERAL_union:
			case LITERAL_intersect:
			case LITERAL_except:
			case LITERAL_text:
			case LITERAL_node:
			case LITERAL_child:
			case LITERAL_self:
			case LITERAL_attribute:
			case LITERAL_descendant:
			case 120:
			case 121:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 124:
			case 125:
			case LITERAL_comment:
			case 130:
			case 131:
			case LITERAL_document:
			case LITERAL_collection:
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
			AST tmp270_AST = null;
			tmp270_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp270_AST);
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
	
	public final void nameTest() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST nameTest_AST = null;
		String name= null;
		
		boolean synPredMatched175 = false;
		if (((LA(1)==NCNAME||LA(1)==STAR))) {
			int _m175 = mark();
			synPredMatched175 = true;
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
				synPredMatched175 = false;
			}
			rewind(_m175);
			inputState.guessing--;
		}
		if ( synPredMatched175 ) {
			wildcard();
			astFactory.addASTChild(currentAST, returnAST);
			nameTest_AST = (AST)currentAST.root;
		}
		else if ((_tokenSet_1.member(LA(1)))) {
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
		
		boolean synPredMatched178 = false;
		if (((LA(1)==STAR))) {
			int _m178 = mark();
			synPredMatched178 = true;
			inputState.guessing++;
			try {
				{
				match(STAR);
				match(COLON);
				}
			}
			catch (RecognitionException pe) {
				synPredMatched178 = false;
			}
			rewind(_m178);
			inputState.guessing--;
		}
		if ( synPredMatched178 ) {
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
			AST tmp275_AST = null;
			tmp275_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp275_AST);
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
		case XQUERY:
		case VERSION:
		case LITERAL_namespace:
		case LITERAL_default:
		case LITERAL_function:
		case LITERAL_variable:
		case LITERAL_as:
		case LITERAL_empty:
		case LITERAL_item:
		case LITERAL_for:
		case LITERAL_let:
		case LITERAL_some:
		case LITERAL_every:
		case LITERAL_if:
		case LITERAL_order:
		case LITERAL_by:
		case LITERAL_then:
		case LITERAL_else:
		case LITERAL_or:
		case LITERAL_and:
		case LITERAL_is:
		case LITERAL_isnot:
		case LITERAL_div:
		case LITERAL_mod:
		case LITERAL_union:
		case LITERAL_intersect:
		case LITERAL_except:
		case LITERAL_text:
		case LITERAL_node:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_attribute:
		case LITERAL_descendant:
		case 120:
		case 121:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 124:
		case 125:
		case LITERAL_comment:
		case LITERAL_document:
		case LITERAL_collection:
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
		case XQUERY:
		case VERSION:
		case LITERAL_namespace:
		case LITERAL_default:
		case LITERAL_function:
		case LITERAL_variable:
		case STRING_LITERAL:
		case LITERAL_element:
		case DOLLAR:
		case LITERAL_as:
		case LITERAL_empty:
		case STAR:
		case PLUS:
		case LITERAL_item:
		case LITERAL_for:
		case LITERAL_let:
		case LITERAL_some:
		case LITERAL_every:
		case LITERAL_if:
		case LITERAL_order:
		case LITERAL_by:
		case LITERAL_then:
		case LITERAL_else:
		case LITERAL_or:
		case LITERAL_and:
		case LT:
		case LITERAL_is:
		case LITERAL_isnot:
		case MINUS:
		case LITERAL_div:
		case LITERAL_mod:
		case LITERAL_union:
		case LITERAL_intersect:
		case LITERAL_except:
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
		case 120:
		case 121:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 124:
		case 125:
		case DOUBLE_LITERAL:
		case DECIMAL_LITERAL:
		case INTEGER_LITERAL:
		case LITERAL_comment:
		case 130:
		case 131:
		case XML_PI:
		case LITERAL_document:
		case LITERAL_collection:
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
		
		AST tmp279_AST = null;
		tmp279_AST = astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp279_AST);
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
		case XQUERY:
		case VERSION:
		case LITERAL_namespace:
		case LITERAL_default:
		case LITERAL_function:
		case LITERAL_variable:
		case STRING_LITERAL:
		case LITERAL_element:
		case DOLLAR:
		case LITERAL_as:
		case LITERAL_empty:
		case STAR:
		case PLUS:
		case LITERAL_item:
		case LITERAL_for:
		case LITERAL_let:
		case LITERAL_some:
		case LITERAL_every:
		case LITERAL_if:
		case LITERAL_order:
		case LITERAL_by:
		case LITERAL_then:
		case LITERAL_else:
		case LITERAL_or:
		case LITERAL_and:
		case LT:
		case LITERAL_is:
		case LITERAL_isnot:
		case MINUS:
		case LITERAL_div:
		case LITERAL_mod:
		case LITERAL_union:
		case LITERAL_intersect:
		case LITERAL_except:
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
		case 120:
		case 121:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 124:
		case 125:
		case DOUBLE_LITERAL:
		case DECIMAL_LITERAL:
		case INTEGER_LITERAL:
		case LITERAL_comment:
		case 130:
		case 131:
		case XML_PI:
		case LITERAL_document:
		case LITERAL_collection:
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
			AST tmp282_AST = null;
			tmp282_AST = astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp282_AST);
			match(DOUBLE_LITERAL);
			numericLiteral_AST = (AST)currentAST.root;
			break;
		}
		case DECIMAL_LITERAL:
		{
			AST tmp283_AST = null;
			tmp283_AST = astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp283_AST);
			match(DECIMAL_LITERAL);
			numericLiteral_AST = (AST)currentAST.root;
			break;
		}
		case INTEGER_LITERAL:
		{
			AST tmp284_AST = null;
			tmp284_AST = astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp284_AST);
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
		_loop189:
		do {
			if ((LA(1)==COMMA)) {
				match(COMMA);
				exprSingle();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop189;
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
		
		AST tmp286_AST = null;
		tmp286_AST = astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp286_AST);
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
		
		AST tmp289_AST = null;
		tmp289_AST = astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp289_AST);
		match(LITERAL_node);
		match(LPAREN);
		match(RPAREN);
		anyKindTest_AST = (AST)currentAST.root;
		returnAST = anyKindTest_AST;
	}
	
	public final void elementTest() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST elementTest_AST = null;
		
		AST tmp292_AST = null;
		tmp292_AST = astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp292_AST);
		match(LITERAL_element);
		match(LPAREN);
		match(RPAREN);
		elementTest_AST = (AST)currentAST.root;
		returnAST = elementTest_AST;
	}
	
	public final void attributeTest() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST attributeTest_AST = null;
		
		AST tmp295_AST = null;
		tmp295_AST = astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp295_AST);
		match(LITERAL_attribute);
		match(LPAREN);
		match(RPAREN);
		attributeTest_AST = (AST)currentAST.root;
		returnAST = attributeTest_AST;
	}
	
	public final void commentTest() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST commentTest_AST = null;
		
		AST tmp298_AST = null;
		tmp298_AST = astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp298_AST);
		match(LITERAL_comment);
		match(LPAREN);
		match(RPAREN);
		commentTest_AST = (AST)currentAST.root;
		returnAST = commentTest_AST;
	}
	
	public final void piTest() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST piTest_AST = null;
		
		AST tmp301_AST = null;
		tmp301_AST = astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp301_AST);
		match(130);
		match(LPAREN);
		match(RPAREN);
		piTest_AST = (AST)currentAST.root;
		returnAST = piTest_AST;
	}
	
	public final void documentTest() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST documentTest_AST = null;
		
		AST tmp304_AST = null;
		tmp304_AST = astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp304_AST);
		match(131);
		match(LPAREN);
		match(RPAREN);
		documentTest_AST = (AST)currentAST.root;
		returnAST = documentTest_AST;
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
		case XQUERY:
		case VERSION:
		case LITERAL_namespace:
		case LITERAL_default:
		case LITERAL_function:
		case LITERAL_variable:
		case LITERAL_as:
		case LITERAL_empty:
		case LITERAL_item:
		case LITERAL_for:
		case LITERAL_let:
		case LITERAL_some:
		case LITERAL_every:
		case LITERAL_if:
		case LITERAL_order:
		case LITERAL_by:
		case LITERAL_then:
		case LITERAL_else:
		case LITERAL_or:
		case LITERAL_and:
		case LITERAL_is:
		case LITERAL_isnot:
		case LITERAL_div:
		case LITERAL_mod:
		case LITERAL_union:
		case LITERAL_intersect:
		case LITERAL_except:
		case LITERAL_text:
		case LITERAL_node:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_attribute:
		case LITERAL_descendant:
		case 120:
		case 121:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 124:
		case 125:
		case LITERAL_comment:
		case LITERAL_document:
		case LITERAL_collection:
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
		
		
		boolean synPredMatched205 = false;
		if (((LA(1)==LT))) {
			int _m205 = mark();
			synPredMatched205 = true;
			inputState.guessing++;
			try {
				{
				match(LT);
				qName();
				match(WS);
				}
			}
			catch (RecognitionException pe) {
				synPredMatched205 = false;
			}
			rewind(_m205);
			inputState.guessing--;
		}
		if ( synPredMatched205 ) {
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
		
		AST tmp307_AST = null;
		tmp307_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp307_AST);
		match(XML_COMMENT);
		match(XML_COMMENT_END);
		xmlComment_AST = (AST)currentAST.root;
		returnAST = xmlComment_AST;
	}
	
	public final void xmlPI() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST xmlPI_AST = null;
		
		AST tmp309_AST = null;
		tmp309_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp309_AST);
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
				
								if (!elementStack.isEmpty())
									lexer.inElementContent= true;
								lexer.wsExplicit= false;
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
				
								elementStack.push(name);
								lexer.inElementContent= true;
								lexer.wsExplicit= false;
							
			}
			mixedElementContent();
			content_AST = (AST)returnAST;
			astFactory.addASTChild(currentAST, returnAST);
			match(END_TAG_START);
			name=qName();
			match(GT);
			if ( inputState.guessing==0 ) {
				elementWithAttributes_AST = (AST)currentAST.root;
				
								if (elementStack.isEmpty())
									throw new RecognitionException("found wrong closing tag: " + name);
								String prev= (String) elementStack.pop();
								if (!prev.equals(name))
									throw new RecognitionException("found closing tag: " + name + "; expected: " + prev);
								elementWithAttributes_AST= (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(ELEMENT,name)).add(attrs_AST));
								if (!elementStack.isEmpty()) {
									lexer.inElementContent= true;
									lexer.wsExplicit= false;
								}
							
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
		
		AST tmp317_AST = null;
		tmp317_AST = astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp317_AST);
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
				
								lexer.wsExplicit= false;
								if (!elementStack.isEmpty())
									lexer.inElementContent= true;
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
				
								elementStack.push(name);
								lexer.inElementContent= true;
								lexer.wsExplicit= false;
							
			}
			mixedElementContent();
			content_AST = (AST)returnAST;
			astFactory.addASTChild(currentAST, returnAST);
			match(END_TAG_START);
			name=qName();
			match(GT);
			if ( inputState.guessing==0 ) {
				elementWithoutAttributes_AST = (AST)currentAST.root;
				
								if (elementStack.isEmpty())
									throw new RecognitionException("found wrong closing tag: " + name);
								String prev= (String) elementStack.pop();
								if (!prev.equals(name))
									throw new RecognitionException("found closing tag: " + name + "; expected: " + prev);
								elementWithoutAttributes_AST= (AST)astFactory.make( (new ASTArray(2)).add(astFactory.create(ELEMENT,name)).add(content_AST));
								if (!elementStack.isEmpty()) {
									lexer.inElementContent= true;
									lexer.wsExplicit= false;
								}
							
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
		_loop223:
		do {
			if ((_tokenSet_10.member(LA(1)))) {
				elementContent();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop223;
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
		int _cnt216=0;
		_loop216:
		do {
			if ((LA(1)==WS)) {
				attributeDef();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				if ( _cnt216>=1 ) { break _loop216; } else {throw new NoViableAltException(LT(1), getFilename());}
			}
			
			_cnt216++;
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
		int _cnt220=0;
		_loop220:
		do {
			switch ( LA(1)) {
			case ATTRIBUTE_CONTENT:
			{
				AST tmp327_AST = null;
				tmp327_AST = astFactory.create(LT(1));
				astFactory.addASTChild(currentAST, tmp327_AST);
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
				if ( _cnt220>=1 ) { break _loop220; } else {throw new NoViableAltException(LT(1), getFilename());}
			}
			}
			_cnt220++;
		} while (true);
		}
		attributeValue_AST = (AST)currentAST.root;
		returnAST = attributeValue_AST;
	}
	
	public final void attributeEnclosedExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		AST attributeEnclosedExpr_AST = null;
		
		AST tmp328_AST = null;
		tmp328_AST = astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp328_AST);
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
		
		AST tmp330_AST = null;
		tmp330_AST = astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp330_AST);
		match(LCURLY);
		if ( inputState.guessing==0 ) {
			
					globalStack.push(elementStack);
					elementStack= new Stack();
					lexer.inElementContent= false;
					lexer.wsExplicit= false;
				
		}
		expr();
		astFactory.addASTChild(currentAST, returnAST);
		match(RCURLY);
		if ( inputState.guessing==0 ) {
			
					elementStack= (Stack) globalStack.pop();
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
			AST tmp332_AST = null;
			tmp332_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp332_AST);
			match(LITERAL_div);
			if ( inputState.guessing==0 ) {
				name= "div";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_mod:
		{
			AST tmp333_AST = null;
			tmp333_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp333_AST);
			match(LITERAL_mod);
			if ( inputState.guessing==0 ) {
				name= "mod";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_text:
		{
			AST tmp334_AST = null;
			tmp334_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp334_AST);
			match(LITERAL_text);
			if ( inputState.guessing==0 ) {
				name= "text";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_node:
		{
			AST tmp335_AST = null;
			tmp335_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp335_AST);
			match(LITERAL_node);
			if ( inputState.guessing==0 ) {
				name= "node";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_or:
		{
			AST tmp336_AST = null;
			tmp336_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp336_AST);
			match(LITERAL_or);
			if ( inputState.guessing==0 ) {
				name= "or";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_and:
		{
			AST tmp337_AST = null;
			tmp337_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp337_AST);
			match(LITERAL_and);
			if ( inputState.guessing==0 ) {
				name= "and";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_child:
		{
			AST tmp338_AST = null;
			tmp338_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp338_AST);
			match(LITERAL_child);
			if ( inputState.guessing==0 ) {
				name= "child";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_parent:
		{
			AST tmp339_AST = null;
			tmp339_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp339_AST);
			match(LITERAL_parent);
			if ( inputState.guessing==0 ) {
				name= "parent";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_self:
		{
			AST tmp340_AST = null;
			tmp340_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp340_AST);
			match(LITERAL_self);
			if ( inputState.guessing==0 ) {
				name= "self";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_attribute:
		{
			AST tmp341_AST = null;
			tmp341_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp341_AST);
			match(LITERAL_attribute);
			if ( inputState.guessing==0 ) {
				name= "attribute";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_comment:
		{
			AST tmp342_AST = null;
			tmp342_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp342_AST);
			match(LITERAL_comment);
			if ( inputState.guessing==0 ) {
				name= "comment";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_document:
		{
			AST tmp343_AST = null;
			tmp343_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp343_AST);
			match(LITERAL_document);
			if ( inputState.guessing==0 ) {
				name= "document";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_collection:
		{
			AST tmp344_AST = null;
			tmp344_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp344_AST);
			match(LITERAL_collection);
			if ( inputState.guessing==0 ) {
				name= "collection";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_ancestor:
		{
			AST tmp345_AST = null;
			tmp345_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp345_AST);
			match(LITERAL_ancestor);
			if ( inputState.guessing==0 ) {
				name= "ancestor";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_descendant:
		{
			AST tmp346_AST = null;
			tmp346_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp346_AST);
			match(LITERAL_descendant);
			if ( inputState.guessing==0 ) {
				name= "descendant";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case 120:
		{
			AST tmp347_AST = null;
			tmp347_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp347_AST);
			match(120);
			if ( inputState.guessing==0 ) {
				name= "descendant-or-self";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case 124:
		{
			AST tmp348_AST = null;
			tmp348_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp348_AST);
			match(124);
			if ( inputState.guessing==0 ) {
				name= "ancestor-or-self";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case 125:
		{
			AST tmp349_AST = null;
			tmp349_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp349_AST);
			match(125);
			if ( inputState.guessing==0 ) {
				name= "preceding-sibling";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case 121:
		{
			AST tmp350_AST = null;
			tmp350_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp350_AST);
			match(121);
			if ( inputState.guessing==0 ) {
				name= "following-sibling";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_item:
		{
			AST tmp351_AST = null;
			tmp351_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp351_AST);
			match(LITERAL_item);
			if ( inputState.guessing==0 ) {
				name= "item";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_empty:
		{
			AST tmp352_AST = null;
			tmp352_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp352_AST);
			match(LITERAL_empty);
			if ( inputState.guessing==0 ) {
				name= "empty";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case VERSION:
		{
			AST tmp353_AST = null;
			tmp353_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp353_AST);
			match(VERSION);
			if ( inputState.guessing==0 ) {
				name= "version";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case XQUERY:
		{
			AST tmp354_AST = null;
			tmp354_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp354_AST);
			match(XQUERY);
			if ( inputState.guessing==0 ) {
				name= "xquery";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_variable:
		{
			AST tmp355_AST = null;
			tmp355_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp355_AST);
			match(LITERAL_variable);
			if ( inputState.guessing==0 ) {
				name= "variable";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_namespace:
		{
			AST tmp356_AST = null;
			tmp356_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp356_AST);
			match(LITERAL_namespace);
			if ( inputState.guessing==0 ) {
				name= "namespace";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_if:
		{
			AST tmp357_AST = null;
			tmp357_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp357_AST);
			match(LITERAL_if);
			if ( inputState.guessing==0 ) {
				name= "if";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_then:
		{
			AST tmp358_AST = null;
			tmp358_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp358_AST);
			match(LITERAL_then);
			if ( inputState.guessing==0 ) {
				name= "then";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_else:
		{
			AST tmp359_AST = null;
			tmp359_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp359_AST);
			match(LITERAL_else);
			if ( inputState.guessing==0 ) {
				name= "else";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_for:
		{
			AST tmp360_AST = null;
			tmp360_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp360_AST);
			match(LITERAL_for);
			if ( inputState.guessing==0 ) {
				name= "for";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_let:
		{
			AST tmp361_AST = null;
			tmp361_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp361_AST);
			match(LITERAL_let);
			if ( inputState.guessing==0 ) {
				name= "let";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_default:
		{
			AST tmp362_AST = null;
			tmp362_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp362_AST);
			match(LITERAL_default);
			if ( inputState.guessing==0 ) {
				name= "default";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_function:
		{
			AST tmp363_AST = null;
			tmp363_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp363_AST);
			match(LITERAL_function);
			if ( inputState.guessing==0 ) {
				name= "function";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_as:
		{
			AST tmp364_AST = null;
			tmp364_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp364_AST);
			match(LITERAL_as);
			if ( inputState.guessing==0 ) {
				name = "as";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_union:
		{
			AST tmp365_AST = null;
			tmp365_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp365_AST);
			match(LITERAL_union);
			if ( inputState.guessing==0 ) {
				name = "union";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_intersect:
		{
			AST tmp366_AST = null;
			tmp366_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp366_AST);
			match(LITERAL_intersect);
			if ( inputState.guessing==0 ) {
				name = "intersect";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_except:
		{
			AST tmp367_AST = null;
			tmp367_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp367_AST);
			match(LITERAL_except);
			if ( inputState.guessing==0 ) {
				name = "except";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_order:
		{
			AST tmp368_AST = null;
			tmp368_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp368_AST);
			match(LITERAL_order);
			if ( inputState.guessing==0 ) {
				name = "order";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_by:
		{
			AST tmp369_AST = null;
			tmp369_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp369_AST);
			match(LITERAL_by);
			if ( inputState.guessing==0 ) {
				name = "by";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_some:
		{
			AST tmp370_AST = null;
			tmp370_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp370_AST);
			match(LITERAL_some);
			if ( inputState.guessing==0 ) {
				name = "some";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_every:
		{
			AST tmp371_AST = null;
			tmp371_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp371_AST);
			match(LITERAL_every);
			if ( inputState.guessing==0 ) {
				name = "every";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_is:
		{
			AST tmp372_AST = null;
			tmp372_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp372_AST);
			match(LITERAL_is);
			if ( inputState.guessing==0 ) {
				name = "is";
			}
			reservedKeywords_AST = (AST)currentAST.root;
			break;
		}
		case LITERAL_isnot:
		{
			AST tmp373_AST = null;
			tmp373_AST = astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp373_AST);
			match(LITERAL_isnot);
			if ( inputState.guessing==0 ) {
				name = "isnot";
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
		"ATOMIC_TYPE",
		"MODULE",
		"ORDER_BY",
		"POSITIONAL_VAR",
		"BEFORE",
		"AFTER",
		"\"xpointer\"",
		"LPAREN",
		"RPAREN",
		"NCNAME",
		"\"xquery\"",
		"\"version\"",
		"SEMICOLON",
		"\"declare\"",
		"\"namespace\"",
		"\"default\"",
		"\"function\"",
		"\"variable\"",
		"STRING_LITERAL",
		"EQ",
		"\"element\"",
		"DOLLAR",
		"LCURLY",
		"RCURLY",
		"\"as\"",
		"COMMA",
		"\"empty\"",
		"QUESTION",
		"STAR",
		"PLUS",
		"\"item\"",
		"\"for\"",
		"\"let\"",
		"\"some\"",
		"\"every\"",
		"\"if\"",
		"\"where\"",
		"\"return\"",
		"\"in\"",
		"\"at\"",
		"COLON",
		"\"order\"",
		"\"by\"",
		"\"ascending\"",
		"\"descending\"",
		"\"greatest\"",
		"\"least\"",
		"\"satisfies\"",
		"\"then\"",
		"\"else\"",
		"\"or\"",
		"\"and\"",
		"\"cast\"",
		"LT",
		"GT",
		"\"eq\"",
		"\"ne\"",
		"\"lt\"",
		"\"le\"",
		"\"gt\"",
		"\"ge\"",
		"NEQ",
		"GTEQ",
		"LTEQ",
		"\"is\"",
		"\"isnot\"",
		"ANDEQ",
		"OREQ",
		"\"to\"",
		"MINUS",
		"\"div\"",
		"\"idiv\"",
		"\"mod\"",
		"\"union\"",
		"UNION",
		"\"intersect\"",
		"\"except\"",
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
		"\"comment\"",
		"\"processing-instruction\"",
		"\"document-node\"",
		"WS",
		"END_TAG_START",
		"QUOT",
		"ATTRIBUTE_CONTENT",
		"ELEMENT_CONTENT",
		"XML_COMMENT_END",
		"XML_PI",
		"XML_PI_END",
		"\"document\"",
		"\"collection\"",
		"XML_PI_START",
		"LETTER",
		"DIGITS",
		"HEX_DIGITS",
		"NMSTART",
		"NMCHAR",
		"EXPR_COMMENT",
		"PREDEFINED_ENTITY_REF",
		"CHAR_REF",
		"NEXT_TOKEN",
		"CHAR",
		"BASECHAR",
		"IDEOGRAPHIC",
		"COMBINING_CHAR",
		"DIGIT",
		"EXTENDER"
	};
	
	protected void buildTokenTypeASTClassMap() {
		tokenTypeToASTClassMap=null;
	};
	
	private static final long[] mk_tokenSet_0() {
		long[] data = { -97111272146862080L, -845058974293823L, 13327L, 0L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_0 = new BitSet(mk_tokenSet_0());
	private static final long[] mk_tokenSet_1() {
		long[] data = { -531290890489036800L, 4607238942180368577L, 12290L, 0L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_1 = new BitSet(mk_tokenSet_1());
	private static final long[] mk_tokenSet_2() {
		long[] data = { -530727940535615488L, 4607238942180368577L, 12302L, 0L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_2 = new BitSet(mk_tokenSet_2());
	private static final long[] mk_tokenSet_3() {
		long[] data = { 562949953421312L, 18067175067615232L, 14L, 0L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_3 = new BitSet(mk_tokenSet_3());
	private static final long[] mk_tokenSet_4() {
		long[] data = { -97115670193373184L, -845058974293823L, 13327L, 0L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_4 = new BitSet(mk_tokenSet_4());
	private static final long[] mk_tokenSet_5() {
		long[] data = { 281474976710656L, 470548480L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_5 = new BitSet(mk_tokenSet_5());
	private static final long[] mk_tokenSet_6() {
		long[] data = { 144115188075855872L, 240518168576L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_6 = new BitSet(mk_tokenSet_6());
	private static final long[] mk_tokenSet_7() {
		long[] data = { -386612752459759616L, 4610616641900896449L, 12302L, 0L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_7 = new BitSet(mk_tokenSet_7());
	private static final long[] mk_tokenSet_8() {
		long[] data = { -530024184374362112L, -4235970014224191L, 13315L, 0L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_8 = new BitSet(mk_tokenSet_8());
	private static final long[] mk_tokenSet_9() {
		long[] data = { -387175702413180928L, 4607238942180368577L, 12290L, 0L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_9 = new BitSet(mk_tokenSet_9());
	private static final long[] mk_tokenSet_10() {
		long[] data = { 2251799813685248L, 140737488617472L, 1280L, 0L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_10 = new BitSet(mk_tokenSet_10());
	
	}
