// $ANTLR 2.7.4: "XQuery.g" -> "XQueryParser.java"$

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

/**
 * The XQuery parser. eXist uses two steps to parse an XQuery expression:
 * in the first step, the XQueryParser generates an abstract syntax tree (AST),
 * which is - in the second step - passed to {@link XQueryTreeParser} for
 * analysis. XQueryTreeParser finally creates an internal representation of
 * the query.
 */
public class XQueryParser extends antlr.LLkParser       implements XQueryTokenTypes
 {

	protected ArrayList exceptions= new ArrayList(2);
	protected boolean foundError= false;
	protected Stack globalStack= new Stack();
	protected Stack elementStack= new Stack();
	protected XQueryLexer lexer;

	public XQueryParser(XQueryLexer lexer) {
		this((TokenStream)lexer);
		this.lexer= lexer;
		setASTNodeClass("org.exist.xquery.parser.XQueryAST");
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

protected XQueryParser(TokenBuffer tokenBuf, int k) {
  super(tokenBuf,k);
  tokenNames = _tokenNames;
  buildTokenTypeASTClassMap();
  astFactory = new ASTFactory(getTokenTypeToASTClassMap());
}

public XQueryParser(TokenBuffer tokenBuf) {
  this(tokenBuf,1);
}

protected XQueryParser(TokenStream lexer, int k) {
  super(lexer,k);
  tokenNames = _tokenNames;
  buildTokenTypeASTClassMap();
  astFactory = new ASTFactory(getTokenTypeToASTClassMap());
}

public XQueryParser(TokenStream lexer) {
  this(lexer,1);
}

public XQueryParser(ParserSharedInputState state) {
  super(state,1);
  tokenNames = _tokenNames;
  buildTokenTypeASTClassMap();
  astFactory = new ASTFactory(getTokenTypeToASTClassMap());
}

	public final void imaginaryTokenDefinitions() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST imaginaryTokenDefinitions_AST = null;
		
		org.exist.xquery.parser.XQueryAST tmp1_AST = null;
		tmp1_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp1_AST);
		match(QNAME);
		org.exist.xquery.parser.XQueryAST tmp2_AST = null;
		tmp2_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp2_AST);
		match(PREDICATE);
		org.exist.xquery.parser.XQueryAST tmp3_AST = null;
		tmp3_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp3_AST);
		match(FLWOR);
		org.exist.xquery.parser.XQueryAST tmp4_AST = null;
		tmp4_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp4_AST);
		match(PARENTHESIZED);
		org.exist.xquery.parser.XQueryAST tmp5_AST = null;
		tmp5_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp5_AST);
		match(ABSOLUTE_SLASH);
		org.exist.xquery.parser.XQueryAST tmp6_AST = null;
		tmp6_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp6_AST);
		match(ABSOLUTE_DSLASH);
		org.exist.xquery.parser.XQueryAST tmp7_AST = null;
		tmp7_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp7_AST);
		match(WILDCARD);
		org.exist.xquery.parser.XQueryAST tmp8_AST = null;
		tmp8_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp8_AST);
		match(PREFIX_WILDCARD);
		org.exist.xquery.parser.XQueryAST tmp9_AST = null;
		tmp9_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp9_AST);
		match(FUNCTION);
		org.exist.xquery.parser.XQueryAST tmp10_AST = null;
		tmp10_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp10_AST);
		match(UNARY_MINUS);
		org.exist.xquery.parser.XQueryAST tmp11_AST = null;
		tmp11_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp11_AST);
		match(UNARY_PLUS);
		org.exist.xquery.parser.XQueryAST tmp12_AST = null;
		tmp12_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp12_AST);
		match(XPOINTER);
		org.exist.xquery.parser.XQueryAST tmp13_AST = null;
		tmp13_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp13_AST);
		match(XPOINTER_ID);
		org.exist.xquery.parser.XQueryAST tmp14_AST = null;
		tmp14_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp14_AST);
		match(VARIABLE_REF);
		org.exist.xquery.parser.XQueryAST tmp15_AST = null;
		tmp15_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp15_AST);
		match(VARIABLE_BINDING);
		org.exist.xquery.parser.XQueryAST tmp16_AST = null;
		tmp16_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp16_AST);
		match(ELEMENT);
		org.exist.xquery.parser.XQueryAST tmp17_AST = null;
		tmp17_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp17_AST);
		match(ATTRIBUTE);
		org.exist.xquery.parser.XQueryAST tmp18_AST = null;
		tmp18_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp18_AST);
		match(TEXT);
		org.exist.xquery.parser.XQueryAST tmp19_AST = null;
		tmp19_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp19_AST);
		match(VERSION_DECL);
		org.exist.xquery.parser.XQueryAST tmp20_AST = null;
		tmp20_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp20_AST);
		match(NAMESPACE_DECL);
		org.exist.xquery.parser.XQueryAST tmp21_AST = null;
		tmp21_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp21_AST);
		match(DEF_NAMESPACE_DECL);
		org.exist.xquery.parser.XQueryAST tmp22_AST = null;
		tmp22_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp22_AST);
		match(DEF_COLLATION_DECL);
		org.exist.xquery.parser.XQueryAST tmp23_AST = null;
		tmp23_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp23_AST);
		match(DEF_FUNCTION_NS_DECL);
		org.exist.xquery.parser.XQueryAST tmp24_AST = null;
		tmp24_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp24_AST);
		match(GLOBAL_VAR);
		org.exist.xquery.parser.XQueryAST tmp25_AST = null;
		tmp25_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp25_AST);
		match(FUNCTION_DECL);
		org.exist.xquery.parser.XQueryAST tmp26_AST = null;
		tmp26_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp26_AST);
		match(PROLOG);
		org.exist.xquery.parser.XQueryAST tmp27_AST = null;
		tmp27_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp27_AST);
		match(ATOMIC_TYPE);
		org.exist.xquery.parser.XQueryAST tmp28_AST = null;
		tmp28_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp28_AST);
		match(MODULE);
		org.exist.xquery.parser.XQueryAST tmp29_AST = null;
		tmp29_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp29_AST);
		match(ORDER_BY);
		org.exist.xquery.parser.XQueryAST tmp30_AST = null;
		tmp30_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp30_AST);
		match(POSITIONAL_VAR);
		org.exist.xquery.parser.XQueryAST tmp31_AST = null;
		tmp31_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp31_AST);
		match(BEFORE);
		org.exist.xquery.parser.XQueryAST tmp32_AST = null;
		tmp32_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp32_AST);
		match(AFTER);
		org.exist.xquery.parser.XQueryAST tmp33_AST = null;
		tmp33_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp33_AST);
		match(MODULE_DECL);
		org.exist.xquery.parser.XQueryAST tmp34_AST = null;
		tmp34_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp34_AST);
		match(ATTRIBUTE_TEST);
		org.exist.xquery.parser.XQueryAST tmp35_AST = null;
		tmp35_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp35_AST);
		match(COMP_ELEM_CONSTRUCTOR);
		org.exist.xquery.parser.XQueryAST tmp36_AST = null;
		tmp36_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp36_AST);
		match(COMP_ATTR_CONSTRUCTOR);
		org.exist.xquery.parser.XQueryAST tmp37_AST = null;
		tmp37_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp37_AST);
		match(COMP_TEXT_CONSTRUCTOR);
		org.exist.xquery.parser.XQueryAST tmp38_AST = null;
		tmp38_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp38_AST);
		match(COMP_COMMENT_CONSTRUCTOR);
		org.exist.xquery.parser.XQueryAST tmp39_AST = null;
		tmp39_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp39_AST);
		match(COMP_PI_CONSTRUCTOR);
		org.exist.xquery.parser.XQueryAST tmp40_AST = null;
		tmp40_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp40_AST);
		match(COMP_NS_CONSTRUCTOR);
		org.exist.xquery.parser.XQueryAST tmp41_AST = null;
		tmp41_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp41_AST);
		match(COMP_DOC_CONSTRUCTOR);
		imaginaryTokenDefinitions_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = imaginaryTokenDefinitions_AST;
	}
	
	public final void xpointer() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST xpointer_AST = null;
		org.exist.xquery.parser.XQueryAST ex_AST = null;
		Token  nc = null;
		org.exist.xquery.parser.XQueryAST nc_AST = null;
		
		switch ( LA(1)) {
		case LITERAL_xpointer:
		{
			org.exist.xquery.parser.XQueryAST tmp42_AST = null;
			tmp42_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp42_AST);
			match(LITERAL_xpointer);
			match(LPAREN);
			expr();
			ex_AST = (org.exist.xquery.parser.XQueryAST)returnAST;
			astFactory.addASTChild(currentAST, returnAST);
			match(RPAREN);
			if ( inputState.guessing==0 ) {
				xpointer_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
				xpointer_AST= (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(XPOINTER,"xpointer")).add(ex_AST));
				currentAST.root = xpointer_AST;
				currentAST.child = xpointer_AST!=null &&xpointer_AST.getFirstChild()!=null ?
					xpointer_AST.getFirstChild() : xpointer_AST;
				currentAST.advanceChildToEnd();
			}
			xpointer_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case NCNAME:
		{
			nc = LT(1);
			nc_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(nc);
			astFactory.addASTChild(currentAST, nc_AST);
			match(NCNAME);
			if ( inputState.guessing==0 ) {
				xpointer_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
				xpointer_AST= (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(XPOINTER_ID,"id")).add(nc_AST));
				currentAST.root = xpointer_AST;
				currentAST.child = xpointer_AST!=null &&xpointer_AST.getFirstChild()!=null ?
					xpointer_AST.getFirstChild() : xpointer_AST;
				currentAST.advanceChildToEnd();
			}
			xpointer_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
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
		org.exist.xquery.parser.XQueryAST expr_AST = null;
		
		exprSingle();
		astFactory.addASTChild(currentAST, returnAST);
		{
		_loop72:
		do {
			if ((LA(1)==COMMA)) {
				org.exist.xquery.parser.XQueryAST tmp45_AST = null;
				tmp45_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp45_AST);
				match(COMMA);
				exprSingle();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop72;
			}
			
		} while (true);
		}
		expr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = expr_AST;
	}
	
	public final void xpath() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST xpath_AST = null;
		
		{
		switch ( LA(1)) {
		case LPAREN:
		case NCNAME:
		case LITERAL_xquery:
		case LITERAL_version:
		case LITERAL_module:
		case LITERAL_namespace:
		case STRING_LITERAL:
		case LITERAL_import:
		case LITERAL_declare:
		case LITERAL_default:
		case LITERAL_xmlspace:
		case LITERAL_function:
		case LITERAL_variable:
		case LITERAL_collation:
		case LITERAL_element:
		case LITERAL_preserve:
		case LITERAL_strip:
		case DOLLAR:
		case LITERAL_at:
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
		case LITERAL_return:
		case LITERAL_order:
		case LITERAL_by:
		case LITERAL_then:
		case LITERAL_else:
		case LITERAL_or:
		case LITERAL_and:
		case LITERAL_instance:
		case LITERAL_of:
		case LITERAL_cast:
		case LT:
		case LITERAL_is:
		case LITERAL_isnot:
		case LITERAL_to:
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
		case LITERAL_attribute:
		case LITERAL_comment:
		case 131:
		case 132:
		case LITERAL_document:
		case SELF:
		case XML_COMMENT:
		case XML_PI:
		case AT:
		case PARENT:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_descendant:
		case 144:
		case 145:
		case LITERAL_following:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 149:
		case 150:
		case DOUBLE_LITERAL:
		case DECIMAL_LITERAL:
		case INTEGER_LITERAL:
		case LITERAL_collection:
		case LITERAL_preceding:
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
		org.exist.xquery.parser.XQueryAST tmp46_AST = null;
		tmp46_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp46_AST);
		match(Token.EOF_TYPE);
		xpath_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = xpath_AST;
	}
	
	public final void module() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST module_AST = null;
		org.exist.xquery.parser.XQueryAST v_AST = null;
		
		{
		boolean synPredMatched8 = false;
		if (((LA(1)==LITERAL_xquery))) {
			int _m8 = mark();
			synPredMatched8 = true;
			inputState.guessing++;
			try {
				{
				match(LITERAL_xquery);
				match(LITERAL_version);
				}
			}
			catch (RecognitionException pe) {
				synPredMatched8 = false;
			}
			rewind(_m8);
			inputState.guessing--;
		}
		if ( synPredMatched8 ) {
			versionDecl();
			v_AST = (org.exist.xquery.parser.XQueryAST)returnAST;
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
		boolean synPredMatched11 = false;
		if (((LA(1)==LITERAL_module))) {
			int _m11 = mark();
			synPredMatched11 = true;
			inputState.guessing++;
			try {
				{
				match(LITERAL_module);
				match(LITERAL_namespace);
				}
			}
			catch (RecognitionException pe) {
				synPredMatched11 = false;
			}
			rewind(_m11);
			inputState.guessing--;
		}
		if ( synPredMatched11 ) {
			libraryModule();
			astFactory.addASTChild(currentAST, returnAST);
		}
		else if ((_tokenSet_0.member(LA(1)))) {
			mainModule();
			astFactory.addASTChild(currentAST, returnAST);
		}
		else {
			throw new NoViableAltException(LT(1), getFilename());
		}
		
		}
		module_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = module_AST;
	}
	
	public final void versionDecl() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST versionDecl_AST = null;
		Token  v = null;
		org.exist.xquery.parser.XQueryAST v_AST = null;
		
		org.exist.xquery.parser.XQueryAST tmp48_AST = null;
		tmp48_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp48_AST);
		match(LITERAL_xquery);
		org.exist.xquery.parser.XQueryAST tmp49_AST = null;
		tmp49_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp49_AST);
		match(LITERAL_version);
		v = LT(1);
		v_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(v);
		astFactory.addASTChild(currentAST, v_AST);
		match(STRING_LITERAL);
		if ( inputState.guessing==0 ) {
			versionDecl_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			versionDecl_AST = (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(1)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(VERSION_DECL,v.getText())));
			currentAST.root = versionDecl_AST;
			currentAST.child = versionDecl_AST!=null &&versionDecl_AST.getFirstChild()!=null ?
				versionDecl_AST.getFirstChild() : versionDecl_AST;
			currentAST.advanceChildToEnd();
		}
		versionDecl_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = versionDecl_AST;
	}
	
	public final void libraryModule() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST libraryModule_AST = null;
		
		moduleDecl();
		astFactory.addASTChild(currentAST, returnAST);
		prolog();
		astFactory.addASTChild(currentAST, returnAST);
		libraryModule_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = libraryModule_AST;
	}
	
	public final void mainModule() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST mainModule_AST = null;
		
		prolog();
		astFactory.addASTChild(currentAST, returnAST);
		queryBody();
		astFactory.addASTChild(currentAST, returnAST);
		mainModule_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = mainModule_AST;
	}
	
	public final void prolog() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST prolog_AST = null;
		boolean inSetters = true;
		
		{
		_loop29:
		do {
			if ((LA(1)==LITERAL_import||LA(1)==LITERAL_declare)) {
				{
				boolean synPredMatched19 = false;
				if (((LA(1)==LITERAL_import))) {
					int _m19 = mark();
					synPredMatched19 = true;
					inputState.guessing++;
					try {
						{
						match(LITERAL_import);
						match(LITERAL_module);
						}
					}
					catch (RecognitionException pe) {
						synPredMatched19 = false;
					}
					rewind(_m19);
					inputState.guessing--;
				}
				if ( synPredMatched19 ) {
					moduleImport();
					astFactory.addASTChild(currentAST, returnAST);
				}
				else {
					boolean synPredMatched22 = false;
					if (((LA(1)==LITERAL_declare))) {
						int _m22 = mark();
						synPredMatched22 = true;
						inputState.guessing++;
						try {
							{
							match(LITERAL_declare);
							{
							switch ( LA(1)) {
							case LITERAL_default:
							{
								match(LITERAL_default);
								break;
							}
							case LITERAL_xmlspace:
							{
								match(LITERAL_xmlspace);
								break;
							}
							default:
							{
								throw new NoViableAltException(LT(1), getFilename());
							}
							}
							}
							}
						}
						catch (RecognitionException pe) {
							synPredMatched22 = false;
						}
						rewind(_m22);
						inputState.guessing--;
					}
					if ( synPredMatched22 ) {
						setter();
						astFactory.addASTChild(currentAST, returnAST);
						if ( inputState.guessing==0 ) {
							
											if(!inSetters)
												throw new TokenStreamException("Default declarations have to come first");
										
						}
					}
					else {
						boolean synPredMatched24 = false;
						if (((LA(1)==LITERAL_declare))) {
							int _m24 = mark();
							synPredMatched24 = true;
							inputState.guessing++;
							try {
								{
								match(LITERAL_declare);
								match(LITERAL_namespace);
								}
							}
							catch (RecognitionException pe) {
								synPredMatched24 = false;
							}
							rewind(_m24);
							inputState.guessing--;
						}
						if ( synPredMatched24 ) {
							namespaceDecl();
							astFactory.addASTChild(currentAST, returnAST);
							if ( inputState.guessing==0 ) {
								inSetters = false;
							}
						}
						else {
							boolean synPredMatched26 = false;
							if (((LA(1)==LITERAL_declare))) {
								int _m26 = mark();
								synPredMatched26 = true;
								inputState.guessing++;
								try {
									{
									match(LITERAL_declare);
									match(LITERAL_function);
									}
								}
								catch (RecognitionException pe) {
									synPredMatched26 = false;
								}
								rewind(_m26);
								inputState.guessing--;
							}
							if ( synPredMatched26 ) {
								functionDecl();
								astFactory.addASTChild(currentAST, returnAST);
								if ( inputState.guessing==0 ) {
									inSetters = false;
								}
							}
							else {
								boolean synPredMatched28 = false;
								if (((LA(1)==LITERAL_declare))) {
									int _m28 = mark();
									synPredMatched28 = true;
									inputState.guessing++;
									try {
										{
										match(LITERAL_declare);
										match(LITERAL_variable);
										}
									}
									catch (RecognitionException pe) {
										synPredMatched28 = false;
									}
									rewind(_m28);
									inputState.guessing--;
								}
								if ( synPredMatched28 ) {
									varDecl();
									astFactory.addASTChild(currentAST, returnAST);
									if ( inputState.guessing==0 ) {
										inSetters = false;
									}
								}
								else {
									throw new NoViableAltException(LT(1), getFilename());
								}
								}}}}
								}
								match(SEMICOLON);
							}
							else {
								break _loop29;
							}
							
						} while (true);
						}
						prolog_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
						returnAST = prolog_AST;
					}
					
	public final void queryBody() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST queryBody_AST = null;
		
		expr();
		astFactory.addASTChild(currentAST, returnAST);
		queryBody_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = queryBody_AST;
	}
	
	public final void moduleDecl() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST moduleDecl_AST = null;
		Token  prefix = null;
		org.exist.xquery.parser.XQueryAST prefix_AST = null;
		Token  uri = null;
		org.exist.xquery.parser.XQueryAST uri_AST = null;
		
		match(LITERAL_module);
		match(LITERAL_namespace);
		prefix = LT(1);
		prefix_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(prefix);
		astFactory.addASTChild(currentAST, prefix_AST);
		match(NCNAME);
		match(EQ);
		uri = LT(1);
		uri_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(uri);
		astFactory.addASTChild(currentAST, uri_AST);
		match(STRING_LITERAL);
		match(SEMICOLON);
		if ( inputState.guessing==0 ) {
			moduleDecl_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			
					moduleDecl_AST = (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(MODULE_DECL,prefix.getText())).add(uri_AST));
				
			currentAST.root = moduleDecl_AST;
			currentAST.child = moduleDecl_AST!=null &&moduleDecl_AST.getFirstChild()!=null ?
				moduleDecl_AST.getFirstChild() : moduleDecl_AST;
			currentAST.advanceChildToEnd();
		}
		moduleDecl_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = moduleDecl_AST;
	}
	
	public final void moduleImport() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST moduleImport_AST = null;
		
		org.exist.xquery.parser.XQueryAST tmp55_AST = null;
		tmp55_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp55_AST);
		match(LITERAL_import);
		match(LITERAL_module);
		{
		switch ( LA(1)) {
		case LITERAL_namespace:
		{
			match(LITERAL_namespace);
			org.exist.xquery.parser.XQueryAST tmp58_AST = null;
			tmp58_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp58_AST);
			match(NCNAME);
			match(EQ);
			break;
		}
		case STRING_LITERAL:
		{
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		}
		org.exist.xquery.parser.XQueryAST tmp60_AST = null;
		tmp60_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp60_AST);
		match(STRING_LITERAL);
		{
		switch ( LA(1)) {
		case LITERAL_at:
		{
			match(LITERAL_at);
			org.exist.xquery.parser.XQueryAST tmp62_AST = null;
			tmp62_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp62_AST);
			match(STRING_LITERAL);
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
		moduleImport_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = moduleImport_AST;
	}
	
	public final void setter() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST setter_AST = null;
		Token  defc = null;
		org.exist.xquery.parser.XQueryAST defc_AST = null;
		Token  defu = null;
		org.exist.xquery.parser.XQueryAST defu_AST = null;
		Token  deff = null;
		org.exist.xquery.parser.XQueryAST deff_AST = null;
		
		{
		boolean synPredMatched34 = false;
		if (((LA(1)==LITERAL_declare))) {
			int _m34 = mark();
			synPredMatched34 = true;
			inputState.guessing++;
			try {
				{
				match(LITERAL_declare);
				match(LITERAL_default);
				}
			}
			catch (RecognitionException pe) {
				synPredMatched34 = false;
			}
			rewind(_m34);
			inputState.guessing--;
		}
		if ( synPredMatched34 ) {
			match(LITERAL_declare);
			match(LITERAL_default);
			{
			switch ( LA(1)) {
			case LITERAL_collation:
			{
				match(LITERAL_collation);
				defc = LT(1);
				defc_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(defc);
				astFactory.addASTChild(currentAST, defc_AST);
				match(STRING_LITERAL);
				if ( inputState.guessing==0 ) {
					setter_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
					setter_AST = (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(DEF_COLLATION_DECL,"defaultCollationDecl")).add(defc_AST));
					currentAST.root = setter_AST;
					currentAST.child = setter_AST!=null &&setter_AST.getFirstChild()!=null ?
						setter_AST.getFirstChild() : setter_AST;
					currentAST.advanceChildToEnd();
				}
				break;
			}
			case LITERAL_element:
			{
				match(LITERAL_element);
				match(LITERAL_namespace);
				defu = LT(1);
				defu_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(defu);
				astFactory.addASTChild(currentAST, defu_AST);
				match(STRING_LITERAL);
				if ( inputState.guessing==0 ) {
					setter_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
					setter_AST= (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(DEF_NAMESPACE_DECL,"defaultNamespaceDecl")).add(defu_AST));
					currentAST.root = setter_AST;
					currentAST.child = setter_AST!=null &&setter_AST.getFirstChild()!=null ?
						setter_AST.getFirstChild() : setter_AST;
					currentAST.advanceChildToEnd();
				}
				break;
			}
			case LITERAL_function:
			{
				match(LITERAL_function);
				match(LITERAL_namespace);
				deff = LT(1);
				deff_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(deff);
				astFactory.addASTChild(currentAST, deff_AST);
				match(STRING_LITERAL);
				if ( inputState.guessing==0 ) {
					setter_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
					setter_AST= (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(DEF_FUNCTION_NS_DECL,"defaultFunctionNSDecl")).add(deff_AST));
					currentAST.root = setter_AST;
					currentAST.child = setter_AST!=null &&setter_AST.getFirstChild()!=null ?
						setter_AST.getFirstChild() : setter_AST;
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
		}
		else {
			boolean synPredMatched37 = false;
			if (((LA(1)==LITERAL_declare))) {
				int _m37 = mark();
				synPredMatched37 = true;
				inputState.guessing++;
				try {
					{
					match(LITERAL_declare);
					match(LITERAL_xmlspace);
					}
				}
				catch (RecognitionException pe) {
					synPredMatched37 = false;
				}
				rewind(_m37);
				inputState.guessing--;
			}
			if ( synPredMatched37 ) {
				match(LITERAL_declare);
				org.exist.xquery.parser.XQueryAST tmp71_AST = null;
				tmp71_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp71_AST);
				match(LITERAL_xmlspace);
				{
				switch ( LA(1)) {
				case LITERAL_preserve:
				{
					org.exist.xquery.parser.XQueryAST tmp72_AST = null;
					tmp72_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
					astFactory.addASTChild(currentAST, tmp72_AST);
					match(LITERAL_preserve);
					break;
				}
				case LITERAL_strip:
				{
					org.exist.xquery.parser.XQueryAST tmp73_AST = null;
					tmp73_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
					astFactory.addASTChild(currentAST, tmp73_AST);
					match(LITERAL_strip);
					break;
				}
				default:
				{
					throw new NoViableAltException(LT(1), getFilename());
				}
				}
				}
			}
			else {
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
			}
			setter_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			returnAST = setter_AST;
		}
		
	public final void namespaceDecl() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST namespaceDecl_AST = null;
		Token  prefix = null;
		org.exist.xquery.parser.XQueryAST prefix_AST = null;
		Token  uri = null;
		org.exist.xquery.parser.XQueryAST uri_AST = null;
		
		org.exist.xquery.parser.XQueryAST tmp74_AST = null;
		tmp74_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp74_AST);
		match(LITERAL_declare);
		org.exist.xquery.parser.XQueryAST tmp75_AST = null;
		tmp75_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp75_AST);
		match(LITERAL_namespace);
		prefix = LT(1);
		prefix_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(prefix);
		astFactory.addASTChild(currentAST, prefix_AST);
		match(NCNAME);
		match(EQ);
		uri = LT(1);
		uri_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(uri);
		astFactory.addASTChild(currentAST, uri_AST);
		match(STRING_LITERAL);
		if ( inputState.guessing==0 ) {
			namespaceDecl_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			namespaceDecl_AST= (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(NAMESPACE_DECL,prefix.getText())).add(uri_AST));
			currentAST.root = namespaceDecl_AST;
			currentAST.child = namespaceDecl_AST!=null &&namespaceDecl_AST.getFirstChild()!=null ?
				namespaceDecl_AST.getFirstChild() : namespaceDecl_AST;
			currentAST.advanceChildToEnd();
		}
		namespaceDecl_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = namespaceDecl_AST;
	}
	
	public final void functionDecl() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST functionDecl_AST = null;
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
			functionDecl_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			functionDecl_AST= (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(FUNCTION_DECL,name)).add(functionDecl_AST));
			currentAST.root = functionDecl_AST;
			currentAST.child = functionDecl_AST!=null &&functionDecl_AST.getFirstChild()!=null ?
				functionDecl_AST.getFirstChild() : functionDecl_AST;
			currentAST.advanceChildToEnd();
		}
		functionDecl_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = functionDecl_AST;
	}
	
	public final void varDecl() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST varDecl_AST = null;
		Token  decl = null;
		org.exist.xquery.parser.XQueryAST decl_AST = null;
		org.exist.xquery.parser.XQueryAST ex_AST = null;
		String varName= null;
		
		decl = LT(1);
		decl_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(decl);
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
		ex_AST = (org.exist.xquery.parser.XQueryAST)returnAST;
		astFactory.addASTChild(currentAST, returnAST);
		match(RCURLY);
		if ( inputState.guessing==0 ) {
			varDecl_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			
			varDecl_AST= (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(GLOBAL_VAR,varName)).add(varDecl_AST));
			varDecl_AST.copyLexInfo(decl_AST);
			
			currentAST.root = varDecl_AST;
			currentAST.child = varDecl_AST!=null &&varDecl_AST.getFirstChild()!=null ?
				varDecl_AST.getFirstChild() : varDecl_AST;
			currentAST.advanceChildToEnd();
		}
		varDecl_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = varDecl_AST;
	}
	
	public final String  qName() throws RecognitionException, TokenStreamException {
		String name;
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST qName_AST = null;
		org.exist.xquery.parser.XQueryAST nc1_AST = null;
		
			name= null;
			String name2;
		
		
		boolean synPredMatched239 = false;
		if (((_tokenSet_1.member(LA(1))))) {
			int _m239 = mark();
			synPredMatched239 = true;
			inputState.guessing++;
			try {
				{
				ncnameOrKeyword();
				match(COLON);
				ncnameOrKeyword();
				}
			}
			catch (RecognitionException pe) {
				synPredMatched239 = false;
			}
			rewind(_m239);
			inputState.guessing--;
		}
		if ( synPredMatched239 ) {
			name=ncnameOrKeyword();
			nc1_AST = (org.exist.xquery.parser.XQueryAST)returnAST;
			astFactory.addASTChild(currentAST, returnAST);
			org.exist.xquery.parser.XQueryAST tmp85_AST = null;
			tmp85_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp85_AST);
			match(COLON);
			name2=ncnameOrKeyword();
			astFactory.addASTChild(currentAST, returnAST);
			if ( inputState.guessing==0 ) {
				qName_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
				
						name= name + ':' + name2;
						qName_AST.copyLexInfo(nc1_AST);
					
			}
			qName_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		}
		else if ((_tokenSet_1.member(LA(1)))) {
			name=ncnameOrKeyword();
			astFactory.addASTChild(currentAST, returnAST);
			qName_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
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
		org.exist.xquery.parser.XQueryAST typeDeclaration_AST = null;
		
		org.exist.xquery.parser.XQueryAST tmp86_AST = null;
		tmp86_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp86_AST);
		match(LITERAL_as);
		sequenceType();
		astFactory.addASTChild(currentAST, returnAST);
		typeDeclaration_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = typeDeclaration_AST;
	}
	
	public final void paramList() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST paramList_AST = null;
		org.exist.xquery.parser.XQueryAST p1_AST = null;
		
		param();
		astFactory.addASTChild(currentAST, returnAST);
		{
		_loop52:
		do {
			if ((LA(1)==COMMA)) {
				match(COMMA);
				param();
				p1_AST = (org.exist.xquery.parser.XQueryAST)returnAST;
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop52;
			}
			
		} while (true);
		}
		paramList_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = paramList_AST;
	}
	
	public final void returnType() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST returnType_AST = null;
		
		org.exist.xquery.parser.XQueryAST tmp88_AST = null;
		tmp88_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp88_AST);
		match(LITERAL_as);
		sequenceType();
		astFactory.addASTChild(currentAST, returnAST);
		returnType_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = returnType_AST;
	}
	
	public final void functionBody() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST functionBody_AST = null;
		org.exist.xquery.parser.XQueryAST e_AST = null;
		
		org.exist.xquery.parser.XQueryAST tmp89_AST = null;
		tmp89_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp89_AST);
		match(LCURLY);
		expr();
		e_AST = (org.exist.xquery.parser.XQueryAST)returnAST;
		astFactory.addASTChild(currentAST, returnAST);
		match(RCURLY);
		functionBody_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = functionBody_AST;
	}
	
	public final void sequenceType() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST sequenceType_AST = null;
		
		boolean synPredMatched58 = false;
		if (((LA(1)==LITERAL_empty))) {
			int _m58 = mark();
			synPredMatched58 = true;
			inputState.guessing++;
			try {
				{
				match(LITERAL_empty);
				match(LPAREN);
				}
			}
			catch (RecognitionException pe) {
				synPredMatched58 = false;
			}
			rewind(_m58);
			inputState.guessing--;
		}
		if ( synPredMatched58 ) {
			org.exist.xquery.parser.XQueryAST tmp91_AST = null;
			tmp91_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp91_AST);
			match(LITERAL_empty);
			match(LPAREN);
			match(RPAREN);
			sequenceType_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
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
			case EOF:
			case RPAREN:
			case LITERAL_collation:
			case LCURLY:
			case RCURLY:
			case LITERAL_at:
			case COMMA:
			case LITERAL_empty:
			case LITERAL_for:
			case LITERAL_let:
			case LITERAL_where:
			case LITERAL_return:
			case LITERAL_in:
			case COLON:
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
			sequenceType_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		}
		else {
			throw new NoViableAltException(LT(1), getFilename());
		}
		
		returnAST = sequenceType_AST;
	}
	
	public final void param() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST param_AST = null;
		org.exist.xquery.parser.XQueryAST t_AST = null;
		String varName= null;
		
		match(DOLLAR);
		varName=qName();
		astFactory.addASTChild(currentAST, returnAST);
		{
		switch ( LA(1)) {
		case LITERAL_as:
		{
			typeDeclaration();
			t_AST = (org.exist.xquery.parser.XQueryAST)returnAST;
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
			param_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			param_AST= (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(VARIABLE_BINDING,varName)).add(t_AST));
			currentAST.root = param_AST;
			currentAST.child = param_AST!=null &&param_AST.getFirstChild()!=null ?
				param_AST.getFirstChild() : param_AST;
			currentAST.advanceChildToEnd();
		}
		param_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = param_AST;
	}
	
	public final void itemType() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST itemType_AST = null;
		
		boolean synPredMatched63 = false;
		if (((LA(1)==LITERAL_item))) {
			int _m63 = mark();
			synPredMatched63 = true;
			inputState.guessing++;
			try {
				{
				match(LITERAL_item);
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
			org.exist.xquery.parser.XQueryAST tmp95_AST = null;
			tmp95_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp95_AST);
			match(LITERAL_item);
			match(LPAREN);
			match(RPAREN);
			itemType_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		}
		else {
			boolean synPredMatched65 = false;
			if (((_tokenSet_3.member(LA(1))))) {
				int _m65 = mark();
				synPredMatched65 = true;
				inputState.guessing++;
				try {
					{
					matchNot(EOF);
					match(LPAREN);
					}
				}
				catch (RecognitionException pe) {
					synPredMatched65 = false;
				}
				rewind(_m65);
				inputState.guessing--;
			}
			if ( synPredMatched65 ) {
				kindTest();
				astFactory.addASTChild(currentAST, returnAST);
				itemType_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			}
			else if ((_tokenSet_1.member(LA(1)))) {
				atomicType();
				astFactory.addASTChild(currentAST, returnAST);
				itemType_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
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
		org.exist.xquery.parser.XQueryAST occurrenceIndicator_AST = null;
		
		switch ( LA(1)) {
		case QUESTION:
		{
			org.exist.xquery.parser.XQueryAST tmp98_AST = null;
			tmp98_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp98_AST);
			match(QUESTION);
			occurrenceIndicator_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case STAR:
		{
			org.exist.xquery.parser.XQueryAST tmp99_AST = null;
			tmp99_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp99_AST);
			match(STAR);
			occurrenceIndicator_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case PLUS:
		{
			org.exist.xquery.parser.XQueryAST tmp100_AST = null;
			tmp100_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp100_AST);
			match(PLUS);
			occurrenceIndicator_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
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
		org.exist.xquery.parser.XQueryAST kindTest_AST = null;
		
		switch ( LA(1)) {
		case LITERAL_text:
		{
			textTest();
			astFactory.addASTChild(currentAST, returnAST);
			kindTest_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_node:
		{
			anyKindTest();
			astFactory.addASTChild(currentAST, returnAST);
			kindTest_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_element:
		{
			elementTest();
			astFactory.addASTChild(currentAST, returnAST);
			kindTest_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_attribute:
		{
			attributeTest();
			astFactory.addASTChild(currentAST, returnAST);
			kindTest_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_comment:
		{
			commentTest();
			astFactory.addASTChild(currentAST, returnAST);
			kindTest_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case 131:
		{
			piTest();
			astFactory.addASTChild(currentAST, returnAST);
			kindTest_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case 132:
		{
			documentTest();
			astFactory.addASTChild(currentAST, returnAST);
			kindTest_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
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
		org.exist.xquery.parser.XQueryAST atomicType_AST = null;
		String name= null;
		
		name=qName();
		astFactory.addASTChild(currentAST, returnAST);
		if ( inputState.guessing==0 ) {
			atomicType_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			atomicType_AST= (org.exist.xquery.parser.XQueryAST)astFactory.create(ATOMIC_TYPE,name);
			currentAST.root = atomicType_AST;
			currentAST.child = atomicType_AST!=null &&atomicType_AST.getFirstChild()!=null ?
				atomicType_AST.getFirstChild() : atomicType_AST;
			currentAST.advanceChildToEnd();
		}
		atomicType_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = atomicType_AST;
	}
	
	public final void singleType() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST singleType_AST = null;
		
		atomicType();
		astFactory.addASTChild(currentAST, returnAST);
		{
		switch ( LA(1)) {
		case QUESTION:
		{
			org.exist.xquery.parser.XQueryAST tmp101_AST = null;
			tmp101_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp101_AST);
			match(QUESTION);
			break;
		}
		case EOF:
		case RPAREN:
		case LITERAL_collation:
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
		case LITERAL_instance:
		case LITERAL_castable:
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
		singleType_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = singleType_AST;
	}
	
	public final void exprSingle() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST exprSingle_AST = null;
		
		boolean synPredMatched76 = false;
		if (((LA(1)==LITERAL_for||LA(1)==LITERAL_let))) {
			int _m76 = mark();
			synPredMatched76 = true;
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
				synPredMatched76 = false;
			}
			rewind(_m76);
			inputState.guessing--;
		}
		if ( synPredMatched76 ) {
			flworExpr();
			astFactory.addASTChild(currentAST, returnAST);
			exprSingle_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		}
		else {
			boolean synPredMatched79 = false;
			if (((LA(1)==LITERAL_some||LA(1)==LITERAL_every))) {
				int _m79 = mark();
				synPredMatched79 = true;
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
					synPredMatched79 = false;
				}
				rewind(_m79);
				inputState.guessing--;
			}
			if ( synPredMatched79 ) {
				quantifiedExpr();
				astFactory.addASTChild(currentAST, returnAST);
				exprSingle_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			}
			else {
				boolean synPredMatched81 = false;
				if (((LA(1)==LITERAL_if))) {
					int _m81 = mark();
					synPredMatched81 = true;
					inputState.guessing++;
					try {
						{
						match(LITERAL_if);
						match(LPAREN);
						}
					}
					catch (RecognitionException pe) {
						synPredMatched81 = false;
					}
					rewind(_m81);
					inputState.guessing--;
				}
				if ( synPredMatched81 ) {
					ifExpr();
					astFactory.addASTChild(currentAST, returnAST);
					exprSingle_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
				}
				else if ((_tokenSet_0.member(LA(1)))) {
					orExpr();
					astFactory.addASTChild(currentAST, returnAST);
					exprSingle_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
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
		org.exist.xquery.parser.XQueryAST flworExpr_AST = null;
		
		{
		int _cnt84=0;
		_loop84:
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
				if ( _cnt84>=1 ) { break _loop84; } else {throw new NoViableAltException(LT(1), getFilename());}
			}
			}
			_cnt84++;
		} while (true);
		}
		{
		switch ( LA(1)) {
		case LITERAL_where:
		{
			org.exist.xquery.parser.XQueryAST tmp102_AST = null;
			tmp102_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp102_AST);
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
		org.exist.xquery.parser.XQueryAST tmp103_AST = null;
		tmp103_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp103_AST);
		match(LITERAL_return);
		exprSingle();
		astFactory.addASTChild(currentAST, returnAST);
		flworExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = flworExpr_AST;
	}
	
	public final void quantifiedExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST quantifiedExpr_AST = null;
		
		{
		switch ( LA(1)) {
		case LITERAL_some:
		{
			org.exist.xquery.parser.XQueryAST tmp104_AST = null;
			tmp104_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp104_AST);
			match(LITERAL_some);
			break;
		}
		case LITERAL_every:
		{
			org.exist.xquery.parser.XQueryAST tmp105_AST = null;
			tmp105_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp105_AST);
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
		_loop112:
		do {
			if ((LA(1)==COMMA)) {
				match(COMMA);
				quantifiedInVarBinding();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop112;
			}
			
		} while (true);
		}
		match(LITERAL_satisfies);
		exprSingle();
		astFactory.addASTChild(currentAST, returnAST);
		quantifiedExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = quantifiedExpr_AST;
	}
	
	public final void ifExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST ifExpr_AST = null;
		
		org.exist.xquery.parser.XQueryAST tmp108_AST = null;
		tmp108_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp108_AST);
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
		ifExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = ifExpr_AST;
	}
	
	public final void orExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST orExpr_AST = null;
		
		andExpr();
		astFactory.addASTChild(currentAST, returnAST);
		{
		_loop118:
		do {
			if ((LA(1)==LITERAL_or)) {
				org.exist.xquery.parser.XQueryAST tmp113_AST = null;
				tmp113_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp113_AST);
				match(LITERAL_or);
				andExpr();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop118;
			}
			
		} while (true);
		}
		orExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = orExpr_AST;
	}
	
	public final void forClause() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST forClause_AST = null;
		
		org.exist.xquery.parser.XQueryAST tmp114_AST = null;
		tmp114_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp114_AST);
		match(LITERAL_for);
		inVarBinding();
		astFactory.addASTChild(currentAST, returnAST);
		{
		_loop89:
		do {
			if ((LA(1)==COMMA)) {
				match(COMMA);
				inVarBinding();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop89;
			}
			
		} while (true);
		}
		forClause_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = forClause_AST;
	}
	
	public final void letClause() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST letClause_AST = null;
		
		org.exist.xquery.parser.XQueryAST tmp116_AST = null;
		tmp116_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp116_AST);
		match(LITERAL_let);
		letVarBinding();
		astFactory.addASTChild(currentAST, returnAST);
		{
		_loop92:
		do {
			if ((LA(1)==COMMA)) {
				match(COMMA);
				letVarBinding();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop92;
			}
			
		} while (true);
		}
		letClause_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = letClause_AST;
	}
	
	public final void orderByClause() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST orderByClause_AST = null;
		
		match(LITERAL_order);
		match(LITERAL_by);
		orderSpecList();
		astFactory.addASTChild(currentAST, returnAST);
		if ( inputState.guessing==0 ) {
			orderByClause_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			orderByClause_AST= (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(ORDER_BY,"order by")).add(orderByClause_AST));
			currentAST.root = orderByClause_AST;
			currentAST.child = orderByClause_AST!=null &&orderByClause_AST.getFirstChild()!=null ?
				orderByClause_AST.getFirstChild() : orderByClause_AST;
			currentAST.advanceChildToEnd();
		}
		orderByClause_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = orderByClause_AST;
	}
	
	public final void inVarBinding() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST inVarBinding_AST = null;
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
		case LITERAL_at:
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
			inVarBinding_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			inVarBinding_AST= (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(VARIABLE_BINDING,varName)).add(inVarBinding_AST));
			currentAST.root = inVarBinding_AST;
			currentAST.child = inVarBinding_AST!=null &&inVarBinding_AST.getFirstChild()!=null ?
				inVarBinding_AST.getFirstChild() : inVarBinding_AST;
			currentAST.advanceChildToEnd();
		}
		inVarBinding_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = inVarBinding_AST;
	}
	
	public final void letVarBinding() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST letVarBinding_AST = null;
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
			letVarBinding_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			letVarBinding_AST= (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(VARIABLE_BINDING,varName)).add(letVarBinding_AST));
			currentAST.root = letVarBinding_AST;
			currentAST.child = letVarBinding_AST!=null &&letVarBinding_AST.getFirstChild()!=null ?
				letVarBinding_AST.getFirstChild() : letVarBinding_AST;
			currentAST.advanceChildToEnd();
		}
		letVarBinding_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = letVarBinding_AST;
	}
	
	public final void positionalVar() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST positionalVar_AST = null;
		String varName;
		
		org.exist.xquery.parser.XQueryAST tmp125_AST = null;
		tmp125_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp125_AST);
		match(LITERAL_at);
		match(DOLLAR);
		varName=qName();
		astFactory.addASTChild(currentAST, returnAST);
		if ( inputState.guessing==0 ) {
			positionalVar_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			positionalVar_AST= (org.exist.xquery.parser.XQueryAST)astFactory.create(POSITIONAL_VAR,varName);
			currentAST.root = positionalVar_AST;
			currentAST.child = positionalVar_AST!=null &&positionalVar_AST.getFirstChild()!=null ?
				positionalVar_AST.getFirstChild() : positionalVar_AST;
			currentAST.advanceChildToEnd();
		}
		positionalVar_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = positionalVar_AST;
	}
	
	public final void orderSpecList() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST orderSpecList_AST = null;
		
		orderSpec();
		astFactory.addASTChild(currentAST, returnAST);
		{
		_loop102:
		do {
			if ((LA(1)==COMMA)) {
				match(COMMA);
				orderSpec();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop102;
			}
			
		} while (true);
		}
		orderSpecList_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = orderSpecList_AST;
	}
	
	public final void orderSpec() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST orderSpec_AST = null;
		
		exprSingle();
		astFactory.addASTChild(currentAST, returnAST);
		orderModifier();
		astFactory.addASTChild(currentAST, returnAST);
		orderSpec_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = orderSpec_AST;
	}
	
	public final void orderModifier() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST orderModifier_AST = null;
		
		{
		switch ( LA(1)) {
		case LITERAL_ascending:
		{
			org.exist.xquery.parser.XQueryAST tmp128_AST = null;
			tmp128_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp128_AST);
			match(LITERAL_ascending);
			break;
		}
		case LITERAL_descending:
		{
			org.exist.xquery.parser.XQueryAST tmp129_AST = null;
			tmp129_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp129_AST);
			match(LITERAL_descending);
			break;
		}
		case LITERAL_collation:
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
			org.exist.xquery.parser.XQueryAST tmp130_AST = null;
			tmp130_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp130_AST);
			match(LITERAL_empty);
			{
			switch ( LA(1)) {
			case LITERAL_greatest:
			{
				org.exist.xquery.parser.XQueryAST tmp131_AST = null;
				tmp131_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
				astFactory.addASTChild(currentAST, tmp131_AST);
				match(LITERAL_greatest);
				break;
			}
			case LITERAL_least:
			{
				org.exist.xquery.parser.XQueryAST tmp132_AST = null;
				tmp132_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
				astFactory.addASTChild(currentAST, tmp132_AST);
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
		case LITERAL_collation:
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
		{
		switch ( LA(1)) {
		case LITERAL_collation:
		{
			org.exist.xquery.parser.XQueryAST tmp133_AST = null;
			tmp133_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp133_AST);
			match(LITERAL_collation);
			org.exist.xquery.parser.XQueryAST tmp134_AST = null;
			tmp134_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp134_AST);
			match(STRING_LITERAL);
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
		orderModifier_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = orderModifier_AST;
	}
	
	public final void quantifiedInVarBinding() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST quantifiedInVarBinding_AST = null;
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
			quantifiedInVarBinding_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			quantifiedInVarBinding_AST = (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(VARIABLE_BINDING,varName)).add(quantifiedInVarBinding_AST));
			currentAST.root = quantifiedInVarBinding_AST;
			currentAST.child = quantifiedInVarBinding_AST!=null &&quantifiedInVarBinding_AST.getFirstChild()!=null ?
				quantifiedInVarBinding_AST.getFirstChild() : quantifiedInVarBinding_AST;
			currentAST.advanceChildToEnd();
		}
		quantifiedInVarBinding_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = quantifiedInVarBinding_AST;
	}
	
	public final void andExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST andExpr_AST = null;
		
		instanceofExpr();
		astFactory.addASTChild(currentAST, returnAST);
		{
		_loop121:
		do {
			if ((LA(1)==LITERAL_and)) {
				org.exist.xquery.parser.XQueryAST tmp137_AST = null;
				tmp137_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp137_AST);
				match(LITERAL_and);
				instanceofExpr();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop121;
			}
			
		} while (true);
		}
		andExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = andExpr_AST;
	}
	
	public final void instanceofExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST instanceofExpr_AST = null;
		
		castableExpr();
		astFactory.addASTChild(currentAST, returnAST);
		{
		switch ( LA(1)) {
		case LITERAL_instance:
		{
			org.exist.xquery.parser.XQueryAST tmp138_AST = null;
			tmp138_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp138_AST);
			match(LITERAL_instance);
			match(LITERAL_of);
			sequenceType();
			astFactory.addASTChild(currentAST, returnAST);
			break;
		}
		case EOF:
		case RPAREN:
		case LITERAL_collation:
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
		instanceofExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = instanceofExpr_AST;
	}
	
	public final void castableExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST castableExpr_AST = null;
		
		castExpr();
		astFactory.addASTChild(currentAST, returnAST);
		{
		switch ( LA(1)) {
		case LITERAL_castable:
		{
			org.exist.xquery.parser.XQueryAST tmp140_AST = null;
			tmp140_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp140_AST);
			match(LITERAL_castable);
			match(LITERAL_as);
			singleType();
			astFactory.addASTChild(currentAST, returnAST);
			break;
		}
		case EOF:
		case RPAREN:
		case LITERAL_collation:
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
		case LITERAL_instance:
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
		castableExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = castableExpr_AST;
	}
	
	public final void castExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST castExpr_AST = null;
		
		comparisonExpr();
		astFactory.addASTChild(currentAST, returnAST);
		{
		switch ( LA(1)) {
		case LITERAL_cast:
		{
			org.exist.xquery.parser.XQueryAST tmp142_AST = null;
			tmp142_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp142_AST);
			match(LITERAL_cast);
			match(LITERAL_as);
			singleType();
			astFactory.addASTChild(currentAST, returnAST);
			break;
		}
		case EOF:
		case RPAREN:
		case LITERAL_collation:
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
		case LITERAL_instance:
		case LITERAL_castable:
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
		castExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = castExpr_AST;
	}
	
	public final void comparisonExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST comparisonExpr_AST = null;
		
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
				org.exist.xquery.parser.XQueryAST tmp144_AST = null;
				tmp144_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp144_AST);
				match(LITERAL_eq);
				break;
			}
			case LITERAL_ne:
			{
				org.exist.xquery.parser.XQueryAST tmp145_AST = null;
				tmp145_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp145_AST);
				match(LITERAL_ne);
				break;
			}
			case LITERAL_lt:
			{
				org.exist.xquery.parser.XQueryAST tmp146_AST = null;
				tmp146_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp146_AST);
				match(LITERAL_lt);
				break;
			}
			case LITERAL_le:
			{
				org.exist.xquery.parser.XQueryAST tmp147_AST = null;
				tmp147_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp147_AST);
				match(LITERAL_le);
				break;
			}
			case LITERAL_gt:
			{
				org.exist.xquery.parser.XQueryAST tmp148_AST = null;
				tmp148_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp148_AST);
				match(LITERAL_gt);
				break;
			}
			case LITERAL_ge:
			{
				org.exist.xquery.parser.XQueryAST tmp149_AST = null;
				tmp149_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp149_AST);
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
				org.exist.xquery.parser.XQueryAST tmp150_AST = null;
				tmp150_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp150_AST);
				match(LITERAL_is);
				break;
			}
			case LITERAL_isnot:
			{
				org.exist.xquery.parser.XQueryAST tmp151_AST = null;
				tmp151_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp151_AST);
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
				org.exist.xquery.parser.XQueryAST tmp152_AST = null;
				tmp152_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp152_AST);
				match(ANDEQ);
				break;
			}
			case OREQ:
			{
				org.exist.xquery.parser.XQueryAST tmp153_AST = null;
				tmp153_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
				astFactory.makeASTRoot(currentAST, tmp153_AST);
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
		case LITERAL_collation:
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
		case LITERAL_instance:
		case LITERAL_castable:
		case LITERAL_cast:
		case RPPAREN:
		{
			break;
		}
		default:
			boolean synPredMatched131 = false;
			if (((LA(1)==LT))) {
				int _m131 = mark();
				synPredMatched131 = true;
				inputState.guessing++;
				try {
					{
					match(LT);
					match(LT);
					}
				}
				catch (RecognitionException pe) {
					synPredMatched131 = false;
				}
				rewind(_m131);
				inputState.guessing--;
			}
			if ( synPredMatched131 ) {
				match(LT);
				match(LT);
				rangeExpr();
				astFactory.addASTChild(currentAST, returnAST);
				if ( inputState.guessing==0 ) {
					comparisonExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
					
									comparisonExpr_AST = (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(BEFORE,"<<")).add(comparisonExpr_AST));
								
					currentAST.root = comparisonExpr_AST;
					currentAST.child = comparisonExpr_AST!=null &&comparisonExpr_AST.getFirstChild()!=null ?
						comparisonExpr_AST.getFirstChild() : comparisonExpr_AST;
					currentAST.advanceChildToEnd();
				}
			}
			else {
				boolean synPredMatched133 = false;
				if (((LA(1)==GT))) {
					int _m133 = mark();
					synPredMatched133 = true;
					inputState.guessing++;
					try {
						{
						match(GT);
						match(GT);
						}
					}
					catch (RecognitionException pe) {
						synPredMatched133 = false;
					}
					rewind(_m133);
					inputState.guessing--;
				}
				if ( synPredMatched133 ) {
					match(GT);
					match(GT);
					rangeExpr();
					astFactory.addASTChild(currentAST, returnAST);
					if ( inputState.guessing==0 ) {
						comparisonExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
						
										comparisonExpr_AST = (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(AFTER,">>")).add(comparisonExpr_AST));
									
						currentAST.root = comparisonExpr_AST;
						currentAST.child = comparisonExpr_AST!=null &&comparisonExpr_AST.getFirstChild()!=null ?
							comparisonExpr_AST.getFirstChild() : comparisonExpr_AST;
						currentAST.advanceChildToEnd();
					}
				}
				else if ((_tokenSet_4.member(LA(1)))) {
					{
					{
					switch ( LA(1)) {
					case EQ:
					{
						org.exist.xquery.parser.XQueryAST tmp158_AST = null;
						tmp158_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
						astFactory.makeASTRoot(currentAST, tmp158_AST);
						match(EQ);
						break;
					}
					case NEQ:
					{
						org.exist.xquery.parser.XQueryAST tmp159_AST = null;
						tmp159_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
						astFactory.makeASTRoot(currentAST, tmp159_AST);
						match(NEQ);
						break;
					}
					case GT:
					{
						org.exist.xquery.parser.XQueryAST tmp160_AST = null;
						tmp160_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
						astFactory.makeASTRoot(currentAST, tmp160_AST);
						match(GT);
						break;
					}
					case GTEQ:
					{
						org.exist.xquery.parser.XQueryAST tmp161_AST = null;
						tmp161_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
						astFactory.makeASTRoot(currentAST, tmp161_AST);
						match(GTEQ);
						break;
					}
					case LT:
					{
						org.exist.xquery.parser.XQueryAST tmp162_AST = null;
						tmp162_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
						astFactory.makeASTRoot(currentAST, tmp162_AST);
						match(LT);
						break;
					}
					case LTEQ:
					{
						org.exist.xquery.parser.XQueryAST tmp163_AST = null;
						tmp163_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
						astFactory.makeASTRoot(currentAST, tmp163_AST);
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
			comparisonExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			returnAST = comparisonExpr_AST;
		}
		
	public final void rangeExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST rangeExpr_AST = null;
		
		additiveExpr();
		astFactory.addASTChild(currentAST, returnAST);
		{
		switch ( LA(1)) {
		case LITERAL_to:
		{
			org.exist.xquery.parser.XQueryAST tmp164_AST = null;
			tmp164_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp164_AST);
			match(LITERAL_to);
			additiveExpr();
			astFactory.addASTChild(currentAST, returnAST);
			break;
		}
		case EOF:
		case RPAREN:
		case EQ:
		case LITERAL_collation:
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
		case LITERAL_instance:
		case LITERAL_castable:
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
		rangeExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = rangeExpr_AST;
	}
	
	public final void additiveExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST additiveExpr_AST = null;
		
		multiplicativeExpr();
		astFactory.addASTChild(currentAST, returnAST);
		{
		_loop147:
		do {
			if ((LA(1)==PLUS||LA(1)==MINUS)) {
				{
				switch ( LA(1)) {
				case PLUS:
				{
					org.exist.xquery.parser.XQueryAST tmp165_AST = null;
					tmp165_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
					astFactory.makeASTRoot(currentAST, tmp165_AST);
					match(PLUS);
					break;
				}
				case MINUS:
				{
					org.exist.xquery.parser.XQueryAST tmp166_AST = null;
					tmp166_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
					astFactory.makeASTRoot(currentAST, tmp166_AST);
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
				break _loop147;
			}
			
		} while (true);
		}
		additiveExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = additiveExpr_AST;
	}
	
	public final void multiplicativeExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST multiplicativeExpr_AST = null;
		
		unaryExpr();
		astFactory.addASTChild(currentAST, returnAST);
		{
		_loop151:
		do {
			if ((_tokenSet_5.member(LA(1)))) {
				{
				switch ( LA(1)) {
				case STAR:
				{
					org.exist.xquery.parser.XQueryAST tmp167_AST = null;
					tmp167_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
					astFactory.makeASTRoot(currentAST, tmp167_AST);
					match(STAR);
					break;
				}
				case LITERAL_div:
				{
					org.exist.xquery.parser.XQueryAST tmp168_AST = null;
					tmp168_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
					astFactory.makeASTRoot(currentAST, tmp168_AST);
					match(LITERAL_div);
					break;
				}
				case LITERAL_idiv:
				{
					org.exist.xquery.parser.XQueryAST tmp169_AST = null;
					tmp169_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
					astFactory.makeASTRoot(currentAST, tmp169_AST);
					match(LITERAL_idiv);
					break;
				}
				case LITERAL_mod:
				{
					org.exist.xquery.parser.XQueryAST tmp170_AST = null;
					tmp170_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
					astFactory.makeASTRoot(currentAST, tmp170_AST);
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
				break _loop151;
			}
			
		} while (true);
		}
		multiplicativeExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = multiplicativeExpr_AST;
	}
	
	public final void unaryExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST unaryExpr_AST = null;
		Token  m = null;
		org.exist.xquery.parser.XQueryAST m_AST = null;
		org.exist.xquery.parser.XQueryAST expr_AST = null;
		Token  p = null;
		org.exist.xquery.parser.XQueryAST p_AST = null;
		org.exist.xquery.parser.XQueryAST expr2_AST = null;
		
		switch ( LA(1)) {
		case MINUS:
		{
			m = LT(1);
			m_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(m);
			astFactory.addASTChild(currentAST, m_AST);
			match(MINUS);
			unionExpr();
			expr_AST = (org.exist.xquery.parser.XQueryAST)returnAST;
			astFactory.addASTChild(currentAST, returnAST);
			if ( inputState.guessing==0 ) {
				unaryExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
				
				unaryExpr_AST= (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(UNARY_MINUS,"-")).add(expr_AST));
				unaryExpr_AST.copyLexInfo(m_AST);
				
				currentAST.root = unaryExpr_AST;
				currentAST.child = unaryExpr_AST!=null &&unaryExpr_AST.getFirstChild()!=null ?
					unaryExpr_AST.getFirstChild() : unaryExpr_AST;
				currentAST.advanceChildToEnd();
			}
			unaryExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case PLUS:
		{
			p = LT(1);
			p_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(p);
			astFactory.addASTChild(currentAST, p_AST);
			match(PLUS);
			unionExpr();
			expr2_AST = (org.exist.xquery.parser.XQueryAST)returnAST;
			astFactory.addASTChild(currentAST, returnAST);
			if ( inputState.guessing==0 ) {
				unaryExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
				
				unaryExpr_AST= (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(UNARY_PLUS,"+")).add(expr2_AST));
				unaryExpr_AST.copyLexInfo(p_AST);
				
				currentAST.root = unaryExpr_AST;
				currentAST.child = unaryExpr_AST!=null &&unaryExpr_AST.getFirstChild()!=null ?
					unaryExpr_AST.getFirstChild() : unaryExpr_AST;
				currentAST.advanceChildToEnd();
			}
			unaryExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LPAREN:
		case NCNAME:
		case LITERAL_xquery:
		case LITERAL_version:
		case LITERAL_module:
		case LITERAL_namespace:
		case STRING_LITERAL:
		case LITERAL_import:
		case LITERAL_declare:
		case LITERAL_default:
		case LITERAL_xmlspace:
		case LITERAL_function:
		case LITERAL_variable:
		case LITERAL_collation:
		case LITERAL_element:
		case LITERAL_preserve:
		case LITERAL_strip:
		case DOLLAR:
		case LITERAL_at:
		case LITERAL_as:
		case LITERAL_empty:
		case STAR:
		case LITERAL_item:
		case LITERAL_for:
		case LITERAL_let:
		case LITERAL_some:
		case LITERAL_every:
		case LITERAL_if:
		case LITERAL_return:
		case LITERAL_order:
		case LITERAL_by:
		case LITERAL_then:
		case LITERAL_else:
		case LITERAL_or:
		case LITERAL_and:
		case LITERAL_instance:
		case LITERAL_of:
		case LITERAL_cast:
		case LT:
		case LITERAL_is:
		case LITERAL_isnot:
		case LITERAL_to:
		case LITERAL_div:
		case LITERAL_mod:
		case LITERAL_union:
		case LITERAL_intersect:
		case LITERAL_except:
		case SLASH:
		case DSLASH:
		case LITERAL_text:
		case LITERAL_node:
		case LITERAL_attribute:
		case LITERAL_comment:
		case 131:
		case 132:
		case LITERAL_document:
		case SELF:
		case XML_COMMENT:
		case XML_PI:
		case AT:
		case PARENT:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_descendant:
		case 144:
		case 145:
		case LITERAL_following:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 149:
		case 150:
		case DOUBLE_LITERAL:
		case DECIMAL_LITERAL:
		case INTEGER_LITERAL:
		case LITERAL_collection:
		case LITERAL_preceding:
		{
			unionExpr();
			astFactory.addASTChild(currentAST, returnAST);
			unaryExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
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
		org.exist.xquery.parser.XQueryAST unionExpr_AST = null;
		
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
			unionExpr();
			astFactory.addASTChild(currentAST, returnAST);
			if ( inputState.guessing==0 ) {
				unionExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
				
							unionExpr_AST = (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(UNION,"union")).add(unionExpr_AST));
						
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
		case LITERAL_collation:
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
		case LITERAL_instance:
		case LITERAL_castable:
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
		unionExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = unionExpr_AST;
	}
	
	public final void intersectExceptExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST intersectExceptExpr_AST = null;
		
		pathExpr();
		astFactory.addASTChild(currentAST, returnAST);
		{
		_loop159:
		do {
			if ((LA(1)==LITERAL_intersect||LA(1)==LITERAL_except)) {
				{
				switch ( LA(1)) {
				case LITERAL_intersect:
				{
					org.exist.xquery.parser.XQueryAST tmp173_AST = null;
					tmp173_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
					astFactory.makeASTRoot(currentAST, tmp173_AST);
					match(LITERAL_intersect);
					break;
				}
				case LITERAL_except:
				{
					org.exist.xquery.parser.XQueryAST tmp174_AST = null;
					tmp174_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
					astFactory.makeASTRoot(currentAST, tmp174_AST);
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
				break _loop159;
			}
			
		} while (true);
		}
		intersectExceptExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = intersectExceptExpr_AST;
	}
	
	public final void pathExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST pathExpr_AST = null;
		org.exist.xquery.parser.XQueryAST relPath_AST = null;
		org.exist.xquery.parser.XQueryAST relPath2_AST = null;
		
		switch ( LA(1)) {
		case LPAREN:
		case NCNAME:
		case LITERAL_xquery:
		case LITERAL_version:
		case LITERAL_module:
		case LITERAL_namespace:
		case STRING_LITERAL:
		case LITERAL_import:
		case LITERAL_declare:
		case LITERAL_default:
		case LITERAL_xmlspace:
		case LITERAL_function:
		case LITERAL_variable:
		case LITERAL_collation:
		case LITERAL_element:
		case LITERAL_preserve:
		case LITERAL_strip:
		case DOLLAR:
		case LITERAL_at:
		case LITERAL_as:
		case LITERAL_empty:
		case STAR:
		case LITERAL_item:
		case LITERAL_for:
		case LITERAL_let:
		case LITERAL_some:
		case LITERAL_every:
		case LITERAL_if:
		case LITERAL_return:
		case LITERAL_order:
		case LITERAL_by:
		case LITERAL_then:
		case LITERAL_else:
		case LITERAL_or:
		case LITERAL_and:
		case LITERAL_instance:
		case LITERAL_of:
		case LITERAL_cast:
		case LT:
		case LITERAL_is:
		case LITERAL_isnot:
		case LITERAL_to:
		case LITERAL_div:
		case LITERAL_mod:
		case LITERAL_union:
		case LITERAL_intersect:
		case LITERAL_except:
		case LITERAL_text:
		case LITERAL_node:
		case LITERAL_attribute:
		case LITERAL_comment:
		case 131:
		case 132:
		case LITERAL_document:
		case SELF:
		case XML_COMMENT:
		case XML_PI:
		case AT:
		case PARENT:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_descendant:
		case 144:
		case 145:
		case LITERAL_following:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 149:
		case 150:
		case DOUBLE_LITERAL:
		case DECIMAL_LITERAL:
		case INTEGER_LITERAL:
		case LITERAL_collection:
		case LITERAL_preceding:
		{
			relativePathExpr();
			astFactory.addASTChild(currentAST, returnAST);
			pathExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case DSLASH:
		{
			org.exist.xquery.parser.XQueryAST tmp175_AST = null;
			tmp175_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp175_AST);
			match(DSLASH);
			relativePathExpr();
			relPath2_AST = (org.exist.xquery.parser.XQueryAST)returnAST;
			astFactory.addASTChild(currentAST, returnAST);
			if ( inputState.guessing==0 ) {
				pathExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
				pathExpr_AST= (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(ABSOLUTE_DSLASH,"AbsoluteSlashSlash")).add(relPath2_AST));
				currentAST.root = pathExpr_AST;
				currentAST.child = pathExpr_AST!=null &&pathExpr_AST.getFirstChild()!=null ?
					pathExpr_AST.getFirstChild() : pathExpr_AST;
				currentAST.advanceChildToEnd();
			}
			pathExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		default:
			boolean synPredMatched162 = false;
			if (((LA(1)==SLASH))) {
				int _m162 = mark();
				synPredMatched162 = true;
				inputState.guessing++;
				try {
					{
					match(SLASH);
					relativePathExpr();
					}
				}
				catch (RecognitionException pe) {
					synPredMatched162 = false;
				}
				rewind(_m162);
				inputState.guessing--;
			}
			if ( synPredMatched162 ) {
				org.exist.xquery.parser.XQueryAST tmp176_AST = null;
				tmp176_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
				astFactory.addASTChild(currentAST, tmp176_AST);
				match(SLASH);
				relativePathExpr();
				relPath_AST = (org.exist.xquery.parser.XQueryAST)returnAST;
				astFactory.addASTChild(currentAST, returnAST);
				if ( inputState.guessing==0 ) {
					pathExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
					pathExpr_AST= (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(ABSOLUTE_SLASH,"AbsoluteSlash")).add(relPath_AST));
					currentAST.root = pathExpr_AST;
					currentAST.child = pathExpr_AST!=null &&pathExpr_AST.getFirstChild()!=null ?
						pathExpr_AST.getFirstChild() : pathExpr_AST;
					currentAST.advanceChildToEnd();
				}
				pathExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			}
			else if ((LA(1)==SLASH)) {
				org.exist.xquery.parser.XQueryAST tmp177_AST = null;
				tmp177_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
				astFactory.addASTChild(currentAST, tmp177_AST);
				match(SLASH);
				if ( inputState.guessing==0 ) {
					pathExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
					pathExpr_AST= (org.exist.xquery.parser.XQueryAST)astFactory.create(ABSOLUTE_SLASH,"AbsoluteSlash");
					currentAST.root = pathExpr_AST;
					currentAST.child = pathExpr_AST!=null &&pathExpr_AST.getFirstChild()!=null ?
						pathExpr_AST.getFirstChild() : pathExpr_AST;
					currentAST.advanceChildToEnd();
				}
				pathExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
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
		org.exist.xquery.parser.XQueryAST relativePathExpr_AST = null;
		
		stepExpr();
		astFactory.addASTChild(currentAST, returnAST);
		{
		_loop166:
		do {
			if ((LA(1)==SLASH||LA(1)==DSLASH)) {
				{
				switch ( LA(1)) {
				case SLASH:
				{
					org.exist.xquery.parser.XQueryAST tmp178_AST = null;
					tmp178_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
					astFactory.makeASTRoot(currentAST, tmp178_AST);
					match(SLASH);
					break;
				}
				case DSLASH:
				{
					org.exist.xquery.parser.XQueryAST tmp179_AST = null;
					tmp179_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
					astFactory.makeASTRoot(currentAST, tmp179_AST);
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
				break _loop166;
			}
			
		} while (true);
		}
		relativePathExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = relativePathExpr_AST;
	}
	
	public final void stepExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST stepExpr_AST = null;
		
		boolean synPredMatched170 = false;
		if (((_tokenSet_6.member(LA(1))))) {
			int _m170 = mark();
			synPredMatched170 = true;
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
				case LITERAL_attribute:
				{
					match(LITERAL_attribute);
					break;
				}
				case LITERAL_comment:
				{
					match(LITERAL_comment);
					break;
				}
				case 131:
				{
					match(131);
					break;
				}
				case 132:
				{
					match(132);
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
				synPredMatched170 = false;
			}
			rewind(_m170);
			inputState.guessing--;
		}
		if ( synPredMatched170 ) {
			axisStep();
			astFactory.addASTChild(currentAST, returnAST);
			stepExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		}
		else {
			boolean synPredMatched173 = false;
			if (((_tokenSet_7.member(LA(1))))) {
				int _m173 = mark();
				synPredMatched173 = true;
				inputState.guessing++;
				try {
					{
					{
					switch ( LA(1)) {
					case LITERAL_element:
					{
						match(LITERAL_element);
						break;
					}
					case LITERAL_attribute:
					{
						match(LITERAL_attribute);
						break;
					}
					case LITERAL_text:
					{
						match(LITERAL_text);
						break;
					}
					case LITERAL_document:
					{
						match(LITERAL_document);
						break;
					}
					case 131:
					{
						match(131);
						break;
					}
					case LITERAL_comment:
					{
						match(LITERAL_comment);
						break;
					}
					default:
					{
						throw new NoViableAltException(LT(1), getFilename());
					}
					}
					}
					match(LCURLY);
					}
				}
				catch (RecognitionException pe) {
					synPredMatched173 = false;
				}
				rewind(_m173);
				inputState.guessing--;
			}
			if ( synPredMatched173 ) {
				filterStep();
				astFactory.addASTChild(currentAST, returnAST);
				stepExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			}
			else {
				boolean synPredMatched176 = false;
				if (((_tokenSet_7.member(LA(1))))) {
					int _m176 = mark();
					synPredMatched176 = true;
					inputState.guessing++;
					try {
						{
						{
						switch ( LA(1)) {
						case LITERAL_element:
						{
							match(LITERAL_element);
							break;
						}
						case LITERAL_attribute:
						{
							match(LITERAL_attribute);
							break;
						}
						case 131:
						{
							match(131);
							break;
						}
						case LITERAL_namespace:
						{
							match(LITERAL_namespace);
							break;
						}
						default:
						{
							throw new NoViableAltException(LT(1), getFilename());
						}
						}
						}
						qName();
						match(LCURLY);
						}
					}
					catch (RecognitionException pe) {
						synPredMatched176 = false;
					}
					rewind(_m176);
					inputState.guessing--;
				}
				if ( synPredMatched176 ) {
					filterStep();
					astFactory.addASTChild(currentAST, returnAST);
					stepExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
				}
				else {
					boolean synPredMatched179 = false;
					if (((_tokenSet_7.member(LA(1))))) {
						int _m179 = mark();
						synPredMatched179 = true;
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
							case LITERAL_xquery:
							case LITERAL_version:
							case LITERAL_module:
							case LITERAL_namespace:
							case LITERAL_import:
							case LITERAL_declare:
							case LITERAL_default:
							case LITERAL_xmlspace:
							case LITERAL_function:
							case LITERAL_variable:
							case LITERAL_collation:
							case LITERAL_element:
							case LITERAL_preserve:
							case LITERAL_strip:
							case LITERAL_at:
							case LITERAL_as:
							case LITERAL_empty:
							case LITERAL_item:
							case LITERAL_for:
							case LITERAL_let:
							case LITERAL_some:
							case LITERAL_every:
							case LITERAL_if:
							case LITERAL_return:
							case LITERAL_order:
							case LITERAL_by:
							case LITERAL_then:
							case LITERAL_else:
							case LITERAL_or:
							case LITERAL_and:
							case LITERAL_instance:
							case LITERAL_of:
							case LITERAL_cast:
							case LITERAL_is:
							case LITERAL_isnot:
							case LITERAL_to:
							case LITERAL_div:
							case LITERAL_mod:
							case LITERAL_union:
							case LITERAL_intersect:
							case LITERAL_except:
							case LITERAL_text:
							case LITERAL_node:
							case LITERAL_attribute:
							case LITERAL_comment:
							case 132:
							case LITERAL_document:
							case LITERAL_child:
							case LITERAL_self:
							case LITERAL_descendant:
							case 144:
							case 145:
							case LITERAL_following:
							case LITERAL_parent:
							case LITERAL_ancestor:
							case 149:
							case 150:
							case LITERAL_collection:
							case LITERAL_preceding:
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
							case XML_PI:
							{
								match(XML_PI);
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
							synPredMatched179 = false;
						}
						rewind(_m179);
						inputState.guessing--;
					}
					if ( synPredMatched179 ) {
						filterStep();
						astFactory.addASTChild(currentAST, returnAST);
						stepExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
					}
					else if ((_tokenSet_6.member(LA(1)))) {
						axisStep();
						astFactory.addASTChild(currentAST, returnAST);
						stepExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
					}
					else {
						throw new NoViableAltException(LT(1), getFilename());
					}
					}}}
					returnAST = stepExpr_AST;
				}
				
	public final void axisStep() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST axisStep_AST = null;
		
		{
		forwardOrReverseStep();
		astFactory.addASTChild(currentAST, returnAST);
		}
		predicates();
		astFactory.addASTChild(currentAST, returnAST);
		axisStep_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = axisStep_AST;
	}
	
	public final void filterStep() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST filterStep_AST = null;
		
		primaryExpr();
		astFactory.addASTChild(currentAST, returnAST);
		predicates();
		astFactory.addASTChild(currentAST, returnAST);
		filterStep_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = filterStep_AST;
	}
	
	public final void literal() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST literal_AST = null;
		
		switch ( LA(1)) {
		case STRING_LITERAL:
		{
			org.exist.xquery.parser.XQueryAST tmp180_AST = null;
			tmp180_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp180_AST);
			match(STRING_LITERAL);
			literal_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case DOUBLE_LITERAL:
		case DECIMAL_LITERAL:
		case INTEGER_LITERAL:
		{
			numericLiteral();
			astFactory.addASTChild(currentAST, returnAST);
			literal_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		returnAST = literal_AST;
	}
	
	public final void forwardOrReverseStep() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST forwardOrReverseStep_AST = null;
		
		boolean synPredMatched188 = false;
		if (((_tokenSet_8.member(LA(1))))) {
			int _m188 = mark();
			synPredMatched188 = true;
			inputState.guessing++;
			try {
				{
				forwardAxisSpecifier();
				match(COLON);
				}
			}
			catch (RecognitionException pe) {
				synPredMatched188 = false;
			}
			rewind(_m188);
			inputState.guessing--;
		}
		if ( synPredMatched188 ) {
			forwardAxis();
			astFactory.addASTChild(currentAST, returnAST);
			nodeTest();
			astFactory.addASTChild(currentAST, returnAST);
			forwardOrReverseStep_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		}
		else {
			boolean synPredMatched190 = false;
			if ((((LA(1) >= LITERAL_parent && LA(1) <= 150)))) {
				int _m190 = mark();
				synPredMatched190 = true;
				inputState.guessing++;
				try {
					{
					reverseAxisSpecifier();
					match(COLON);
					}
				}
				catch (RecognitionException pe) {
					synPredMatched190 = false;
				}
				rewind(_m190);
				inputState.guessing--;
			}
			if ( synPredMatched190 ) {
				reverseAxis();
				astFactory.addASTChild(currentAST, returnAST);
				nodeTest();
				astFactory.addASTChild(currentAST, returnAST);
				forwardOrReverseStep_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			}
			else if ((_tokenSet_6.member(LA(1)))) {
				abbrevStep();
				astFactory.addASTChild(currentAST, returnAST);
				forwardOrReverseStep_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
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
		org.exist.xquery.parser.XQueryAST predicates_AST = null;
		
		{
		_loop184:
		do {
			if ((LA(1)==LPPAREN)) {
				predicate();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop184;
			}
			
		} while (true);
		}
		predicates_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = predicates_AST;
	}
	
	public final void predicate() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST predicate_AST = null;
		org.exist.xquery.parser.XQueryAST predExpr_AST = null;
		
		match(LPPAREN);
		expr();
		predExpr_AST = (org.exist.xquery.parser.XQueryAST)returnAST;
		astFactory.addASTChild(currentAST, returnAST);
		match(RPPAREN);
		if ( inputState.guessing==0 ) {
			predicate_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			predicate_AST= (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(PREDICATE,"Pred")).add(predExpr_AST));
			currentAST.root = predicate_AST;
			currentAST.child = predicate_AST!=null &&predicate_AST.getFirstChild()!=null ?
				predicate_AST.getFirstChild() : predicate_AST;
			currentAST.advanceChildToEnd();
		}
		predicate_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = predicate_AST;
	}
	
	public final void forwardAxisSpecifier() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST forwardAxisSpecifier_AST = null;
		
		switch ( LA(1)) {
		case LITERAL_child:
		{
			org.exist.xquery.parser.XQueryAST tmp183_AST = null;
			tmp183_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp183_AST);
			match(LITERAL_child);
			forwardAxisSpecifier_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_self:
		{
			org.exist.xquery.parser.XQueryAST tmp184_AST = null;
			tmp184_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp184_AST);
			match(LITERAL_self);
			forwardAxisSpecifier_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_attribute:
		{
			org.exist.xquery.parser.XQueryAST tmp185_AST = null;
			tmp185_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp185_AST);
			match(LITERAL_attribute);
			forwardAxisSpecifier_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_descendant:
		{
			org.exist.xquery.parser.XQueryAST tmp186_AST = null;
			tmp186_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp186_AST);
			match(LITERAL_descendant);
			forwardAxisSpecifier_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case 144:
		{
			org.exist.xquery.parser.XQueryAST tmp187_AST = null;
			tmp187_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp187_AST);
			match(144);
			forwardAxisSpecifier_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case 145:
		{
			org.exist.xquery.parser.XQueryAST tmp188_AST = null;
			tmp188_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp188_AST);
			match(145);
			forwardAxisSpecifier_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_following:
		{
			org.exist.xquery.parser.XQueryAST tmp189_AST = null;
			tmp189_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp189_AST);
			match(LITERAL_following);
			forwardAxisSpecifier_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
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
		org.exist.xquery.parser.XQueryAST forwardAxis_AST = null;
		
		forwardAxisSpecifier();
		astFactory.addASTChild(currentAST, returnAST);
		match(COLON);
		match(COLON);
		forwardAxis_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = forwardAxis_AST;
	}
	
	public final void nodeTest() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST nodeTest_AST = null;
		
		boolean synPredMatched199 = false;
		if (((_tokenSet_3.member(LA(1))))) {
			int _m199 = mark();
			synPredMatched199 = true;
			inputState.guessing++;
			try {
				{
				matchNot(EOF);
				match(LPAREN);
				}
			}
			catch (RecognitionException pe) {
				synPredMatched199 = false;
			}
			rewind(_m199);
			inputState.guessing--;
		}
		if ( synPredMatched199 ) {
			kindTest();
			astFactory.addASTChild(currentAST, returnAST);
			nodeTest_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		}
		else if ((_tokenSet_9.member(LA(1)))) {
			nameTest();
			astFactory.addASTChild(currentAST, returnAST);
			nodeTest_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		}
		else {
			throw new NoViableAltException(LT(1), getFilename());
		}
		
		returnAST = nodeTest_AST;
	}
	
	public final void reverseAxisSpecifier() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST reverseAxisSpecifier_AST = null;
		
		switch ( LA(1)) {
		case LITERAL_parent:
		{
			org.exist.xquery.parser.XQueryAST tmp192_AST = null;
			tmp192_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp192_AST);
			match(LITERAL_parent);
			reverseAxisSpecifier_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_ancestor:
		{
			org.exist.xquery.parser.XQueryAST tmp193_AST = null;
			tmp193_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp193_AST);
			match(LITERAL_ancestor);
			reverseAxisSpecifier_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case 149:
		{
			org.exist.xquery.parser.XQueryAST tmp194_AST = null;
			tmp194_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp194_AST);
			match(149);
			reverseAxisSpecifier_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case 150:
		{
			org.exist.xquery.parser.XQueryAST tmp195_AST = null;
			tmp195_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp195_AST);
			match(150);
			reverseAxisSpecifier_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
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
		org.exist.xquery.parser.XQueryAST reverseAxis_AST = null;
		
		reverseAxisSpecifier();
		astFactory.addASTChild(currentAST, returnAST);
		match(COLON);
		match(COLON);
		reverseAxis_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = reverseAxis_AST;
	}
	
	public final void abbrevStep() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST abbrevStep_AST = null;
		
		switch ( LA(1)) {
		case NCNAME:
		case LITERAL_xquery:
		case LITERAL_version:
		case LITERAL_module:
		case LITERAL_namespace:
		case LITERAL_import:
		case LITERAL_declare:
		case LITERAL_default:
		case LITERAL_xmlspace:
		case LITERAL_function:
		case LITERAL_variable:
		case LITERAL_collation:
		case LITERAL_element:
		case LITERAL_preserve:
		case LITERAL_strip:
		case LITERAL_at:
		case LITERAL_as:
		case LITERAL_empty:
		case STAR:
		case LITERAL_item:
		case LITERAL_for:
		case LITERAL_let:
		case LITERAL_some:
		case LITERAL_every:
		case LITERAL_if:
		case LITERAL_return:
		case LITERAL_order:
		case LITERAL_by:
		case LITERAL_then:
		case LITERAL_else:
		case LITERAL_or:
		case LITERAL_and:
		case LITERAL_instance:
		case LITERAL_of:
		case LITERAL_cast:
		case LITERAL_is:
		case LITERAL_isnot:
		case LITERAL_to:
		case LITERAL_div:
		case LITERAL_mod:
		case LITERAL_union:
		case LITERAL_intersect:
		case LITERAL_except:
		case LITERAL_text:
		case LITERAL_node:
		case LITERAL_attribute:
		case LITERAL_comment:
		case 131:
		case 132:
		case LITERAL_document:
		case AT:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_descendant:
		case 144:
		case 145:
		case LITERAL_following:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 149:
		case 150:
		case LITERAL_collection:
		case LITERAL_preceding:
		{
			{
			switch ( LA(1)) {
			case AT:
			{
				org.exist.xquery.parser.XQueryAST tmp198_AST = null;
				tmp198_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
				astFactory.addASTChild(currentAST, tmp198_AST);
				match(AT);
				break;
			}
			case NCNAME:
			case LITERAL_xquery:
			case LITERAL_version:
			case LITERAL_module:
			case LITERAL_namespace:
			case LITERAL_import:
			case LITERAL_declare:
			case LITERAL_default:
			case LITERAL_xmlspace:
			case LITERAL_function:
			case LITERAL_variable:
			case LITERAL_collation:
			case LITERAL_element:
			case LITERAL_preserve:
			case LITERAL_strip:
			case LITERAL_at:
			case LITERAL_as:
			case LITERAL_empty:
			case STAR:
			case LITERAL_item:
			case LITERAL_for:
			case LITERAL_let:
			case LITERAL_some:
			case LITERAL_every:
			case LITERAL_if:
			case LITERAL_return:
			case LITERAL_order:
			case LITERAL_by:
			case LITERAL_then:
			case LITERAL_else:
			case LITERAL_or:
			case LITERAL_and:
			case LITERAL_instance:
			case LITERAL_of:
			case LITERAL_cast:
			case LITERAL_is:
			case LITERAL_isnot:
			case LITERAL_to:
			case LITERAL_div:
			case LITERAL_mod:
			case LITERAL_union:
			case LITERAL_intersect:
			case LITERAL_except:
			case LITERAL_text:
			case LITERAL_node:
			case LITERAL_attribute:
			case LITERAL_comment:
			case 131:
			case 132:
			case LITERAL_document:
			case LITERAL_child:
			case LITERAL_self:
			case LITERAL_descendant:
			case 144:
			case 145:
			case LITERAL_following:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 149:
			case 150:
			case LITERAL_collection:
			case LITERAL_preceding:
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
			abbrevStep_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case PARENT:
		{
			org.exist.xquery.parser.XQueryAST tmp199_AST = null;
			tmp199_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp199_AST);
			match(PARENT);
			abbrevStep_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
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
		org.exist.xquery.parser.XQueryAST nameTest_AST = null;
		String name= null;
		
		boolean synPredMatched203 = false;
		if (((_tokenSet_9.member(LA(1))))) {
			int _m203 = mark();
			synPredMatched203 = true;
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
				synPredMatched203 = false;
			}
			rewind(_m203);
			inputState.guessing--;
		}
		if ( synPredMatched203 ) {
			wildcard();
			astFactory.addASTChild(currentAST, returnAST);
			nameTest_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		}
		else if ((_tokenSet_1.member(LA(1)))) {
			name=qName();
			astFactory.addASTChild(currentAST, returnAST);
			if ( inputState.guessing==0 ) {
				nameTest_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
				nameTest_AST= (org.exist.xquery.parser.XQueryAST)astFactory.create(QNAME,name);
				currentAST.root = nameTest_AST;
				currentAST.child = nameTest_AST!=null &&nameTest_AST.getFirstChild()!=null ?
					nameTest_AST.getFirstChild() : nameTest_AST;
				currentAST.advanceChildToEnd();
			}
			nameTest_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		}
		else {
			throw new NoViableAltException(LT(1), getFilename());
		}
		
		returnAST = nameTest_AST;
	}
	
	public final void wildcard() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST wildcard_AST = null;
		String name= null;
		
		boolean synPredMatched206 = false;
		if (((LA(1)==STAR))) {
			int _m206 = mark();
			synPredMatched206 = true;
			inputState.guessing++;
			try {
				{
				match(STAR);
				match(COLON);
				}
			}
			catch (RecognitionException pe) {
				synPredMatched206 = false;
			}
			rewind(_m206);
			inputState.guessing--;
		}
		if ( synPredMatched206 ) {
			match(STAR);
			match(COLON);
			name=ncnameOrKeyword();
			astFactory.addASTChild(currentAST, returnAST);
			if ( inputState.guessing==0 ) {
				wildcard_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
				wildcard_AST= (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(PREFIX_WILDCARD,"*")).add((org.exist.xquery.parser.XQueryAST)astFactory.create(NCNAME,name)));
				currentAST.root = wildcard_AST;
				currentAST.child = wildcard_AST!=null &&wildcard_AST.getFirstChild()!=null ?
					wildcard_AST.getFirstChild() : wildcard_AST;
				currentAST.advanceChildToEnd();
			}
			wildcard_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		}
		else if ((_tokenSet_1.member(LA(1)))) {
			name=ncnameOrKeyword();
			astFactory.addASTChild(currentAST, returnAST);
			match(COLON);
			match(STAR);
			if ( inputState.guessing==0 ) {
				wildcard_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
				wildcard_AST= (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(NCNAME,name)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(WILDCARD,"*")));
				currentAST.root = wildcard_AST;
				currentAST.child = wildcard_AST!=null &&wildcard_AST.getFirstChild()!=null ?
					wildcard_AST.getFirstChild() : wildcard_AST;
				currentAST.advanceChildToEnd();
			}
			wildcard_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		}
		else if ((LA(1)==STAR)) {
			org.exist.xquery.parser.XQueryAST tmp204_AST = null;
			tmp204_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp204_AST);
			match(STAR);
			if ( inputState.guessing==0 ) {
				wildcard_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
				
						// make this distinct from multiplication
						wildcard_AST= (org.exist.xquery.parser.XQueryAST)astFactory.create(WILDCARD,"*");
					
				currentAST.root = wildcard_AST;
				currentAST.child = wildcard_AST!=null &&wildcard_AST.getFirstChild()!=null ?
					wildcard_AST.getFirstChild() : wildcard_AST;
				currentAST.advanceChildToEnd();
			}
			wildcard_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		}
		else {
			throw new NoViableAltException(LT(1), getFilename());
		}
		
		returnAST = wildcard_AST;
	}
	
	public final String  ncnameOrKeyword() throws RecognitionException, TokenStreamException {
		String name;
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST ncnameOrKeyword_AST = null;
		Token  n1 = null;
		org.exist.xquery.parser.XQueryAST n1_AST = null;
		name= null;
		
		switch ( LA(1)) {
		case NCNAME:
		{
			n1 = LT(1);
			n1_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(n1);
			astFactory.addASTChild(currentAST, n1_AST);
			match(NCNAME);
			if ( inputState.guessing==0 ) {
				name= n1.getText();
			}
			ncnameOrKeyword_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_xquery:
		case LITERAL_version:
		case LITERAL_module:
		case LITERAL_namespace:
		case LITERAL_import:
		case LITERAL_declare:
		case LITERAL_default:
		case LITERAL_xmlspace:
		case LITERAL_function:
		case LITERAL_variable:
		case LITERAL_collation:
		case LITERAL_element:
		case LITERAL_preserve:
		case LITERAL_strip:
		case LITERAL_at:
		case LITERAL_as:
		case LITERAL_empty:
		case LITERAL_item:
		case LITERAL_for:
		case LITERAL_let:
		case LITERAL_some:
		case LITERAL_every:
		case LITERAL_if:
		case LITERAL_return:
		case LITERAL_order:
		case LITERAL_by:
		case LITERAL_then:
		case LITERAL_else:
		case LITERAL_or:
		case LITERAL_and:
		case LITERAL_instance:
		case LITERAL_of:
		case LITERAL_cast:
		case LITERAL_is:
		case LITERAL_isnot:
		case LITERAL_to:
		case LITERAL_div:
		case LITERAL_mod:
		case LITERAL_union:
		case LITERAL_intersect:
		case LITERAL_except:
		case LITERAL_text:
		case LITERAL_node:
		case LITERAL_attribute:
		case LITERAL_comment:
		case 132:
		case LITERAL_document:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_descendant:
		case 144:
		case 145:
		case LITERAL_following:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 149:
		case 150:
		case LITERAL_collection:
		case LITERAL_preceding:
		{
			name=reservedKeywords();
			astFactory.addASTChild(currentAST, returnAST);
			ncnameOrKeyword_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
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
	
	public final void primaryExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST primaryExpr_AST = null;
		org.exist.xquery.parser.XQueryAST v_AST = null;
		String varName= null;
		
		switch ( LA(1)) {
		case LT:
		case XML_COMMENT:
		case XML_PI:
		{
			constructor();
			astFactory.addASTChild(currentAST, returnAST);
			primaryExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case SELF:
		{
			contextItemExpr();
			astFactory.addASTChild(currentAST, returnAST);
			primaryExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LPAREN:
		{
			parenthesizedExpr();
			astFactory.addASTChild(currentAST, returnAST);
			primaryExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case DOLLAR:
		{
			match(DOLLAR);
			varName=qName();
			v_AST = (org.exist.xquery.parser.XQueryAST)returnAST;
			astFactory.addASTChild(currentAST, returnAST);
			if ( inputState.guessing==0 ) {
				primaryExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
				
				primaryExpr_AST= (org.exist.xquery.parser.XQueryAST)astFactory.create(VARIABLE_REF,varName);
				primaryExpr_AST.copyLexInfo(v_AST);
				
				currentAST.root = primaryExpr_AST;
				currentAST.child = primaryExpr_AST!=null &&primaryExpr_AST.getFirstChild()!=null ?
					primaryExpr_AST.getFirstChild() : primaryExpr_AST;
				currentAST.advanceChildToEnd();
			}
			primaryExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case STRING_LITERAL:
		case DOUBLE_LITERAL:
		case DECIMAL_LITERAL:
		case INTEGER_LITERAL:
		{
			literal();
			astFactory.addASTChild(currentAST, returnAST);
			primaryExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		default:
			boolean synPredMatched211 = false;
			if (((_tokenSet_10.member(LA(1))))) {
				int _m211 = mark();
				synPredMatched211 = true;
				inputState.guessing++;
				try {
					{
					{
					switch ( LA(1)) {
					case LITERAL_element:
					{
						match(LITERAL_element);
						break;
					}
					case LITERAL_attribute:
					{
						match(LITERAL_attribute);
						break;
					}
					case LITERAL_text:
					{
						match(LITERAL_text);
						break;
					}
					case LITERAL_document:
					{
						match(LITERAL_document);
						break;
					}
					case 131:
					{
						match(131);
						break;
					}
					case LITERAL_comment:
					{
						match(LITERAL_comment);
						break;
					}
					default:
					{
						throw new NoViableAltException(LT(1), getFilename());
					}
					}
					}
					match(LCURLY);
					}
				}
				catch (RecognitionException pe) {
					synPredMatched211 = false;
				}
				rewind(_m211);
				inputState.guessing--;
			}
			if ( synPredMatched211 ) {
				computedConstructor();
				astFactory.addASTChild(currentAST, returnAST);
				primaryExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			}
			else {
				boolean synPredMatched214 = false;
				if (((_tokenSet_10.member(LA(1))))) {
					int _m214 = mark();
					synPredMatched214 = true;
					inputState.guessing++;
					try {
						{
						{
						switch ( LA(1)) {
						case LITERAL_element:
						{
							match(LITERAL_element);
							break;
						}
						case LITERAL_attribute:
						{
							match(LITERAL_attribute);
							break;
						}
						case 131:
						{
							match(131);
							break;
						}
						case LITERAL_namespace:
						{
							match(LITERAL_namespace);
							break;
						}
						default:
						{
							throw new NoViableAltException(LT(1), getFilename());
						}
						}
						}
						qName();
						match(LCURLY);
						}
					}
					catch (RecognitionException pe) {
						synPredMatched214 = false;
					}
					rewind(_m214);
					inputState.guessing--;
				}
				if ( synPredMatched214 ) {
					computedConstructor();
					astFactory.addASTChild(currentAST, returnAST);
					primaryExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
				}
				else if ((_tokenSet_1.member(LA(1)))) {
					functionCall();
					astFactory.addASTChild(currentAST, returnAST);
					primaryExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
				}
			else {
				throw new NoViableAltException(LT(1), getFilename());
			}
			}}
			returnAST = primaryExpr_AST;
		}
		
	public final void computedConstructor() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST computedConstructor_AST = null;
		
		switch ( LA(1)) {
		case LITERAL_element:
		{
			compElemConstructor();
			astFactory.addASTChild(currentAST, returnAST);
			computedConstructor_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_attribute:
		{
			compAttrConstructor();
			astFactory.addASTChild(currentAST, returnAST);
			computedConstructor_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_text:
		{
			compTextConstructor();
			astFactory.addASTChild(currentAST, returnAST);
			computedConstructor_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_document:
		{
			compDocumentConstructor();
			astFactory.addASTChild(currentAST, returnAST);
			computedConstructor_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case 131:
		{
			compXmlPI();
			astFactory.addASTChild(currentAST, returnAST);
			computedConstructor_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_comment:
		{
			compXmlComment();
			astFactory.addASTChild(currentAST, returnAST);
			computedConstructor_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		returnAST = computedConstructor_AST;
	}
	
	public final void constructor() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST constructor_AST = null;
		
		switch ( LA(1)) {
		case LT:
		{
			elementConstructor();
			astFactory.addASTChild(currentAST, returnAST);
			constructor_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case XML_COMMENT:
		{
			xmlComment();
			astFactory.addASTChild(currentAST, returnAST);
			constructor_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case XML_PI:
		{
			xmlPI();
			astFactory.addASTChild(currentAST, returnAST);
			constructor_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		returnAST = constructor_AST;
	}
	
	public final void functionCall() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST functionCall_AST = null;
		org.exist.xquery.parser.XQueryAST q_AST = null;
		Token  l = null;
		org.exist.xquery.parser.XQueryAST l_AST = null;
		org.exist.xquery.parser.XQueryAST params_AST = null;
		String fnName= null;
		
		fnName=qName();
		q_AST = (org.exist.xquery.parser.XQueryAST)returnAST;
		astFactory.addASTChild(currentAST, returnAST);
		l = LT(1);
		l_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(l);
		match(LPAREN);
		if ( inputState.guessing==0 ) {
			functionCall_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			
			functionCall_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(FUNCTION,fnName);
			
			currentAST.root = functionCall_AST;
			currentAST.child = functionCall_AST!=null &&functionCall_AST.getFirstChild()!=null ?
				functionCall_AST.getFirstChild() : functionCall_AST;
			currentAST.advanceChildToEnd();
		}
		{
		switch ( LA(1)) {
		case LPAREN:
		case NCNAME:
		case LITERAL_xquery:
		case LITERAL_version:
		case LITERAL_module:
		case LITERAL_namespace:
		case STRING_LITERAL:
		case LITERAL_import:
		case LITERAL_declare:
		case LITERAL_default:
		case LITERAL_xmlspace:
		case LITERAL_function:
		case LITERAL_variable:
		case LITERAL_collation:
		case LITERAL_element:
		case LITERAL_preserve:
		case LITERAL_strip:
		case DOLLAR:
		case LITERAL_at:
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
		case LITERAL_return:
		case LITERAL_order:
		case LITERAL_by:
		case LITERAL_then:
		case LITERAL_else:
		case LITERAL_or:
		case LITERAL_and:
		case LITERAL_instance:
		case LITERAL_of:
		case LITERAL_cast:
		case LT:
		case LITERAL_is:
		case LITERAL_isnot:
		case LITERAL_to:
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
		case LITERAL_attribute:
		case LITERAL_comment:
		case 131:
		case 132:
		case LITERAL_document:
		case SELF:
		case XML_COMMENT:
		case XML_PI:
		case AT:
		case PARENT:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_descendant:
		case 144:
		case 145:
		case LITERAL_following:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 149:
		case 150:
		case DOUBLE_LITERAL:
		case DECIMAL_LITERAL:
		case INTEGER_LITERAL:
		case LITERAL_collection:
		case LITERAL_preceding:
		{
			functionParameters();
			params_AST = (org.exist.xquery.parser.XQueryAST)returnAST;
			astFactory.addASTChild(currentAST, returnAST);
			if ( inputState.guessing==0 ) {
				functionCall_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
				functionCall_AST= (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(FUNCTION,fnName)).add(params_AST));
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
		if ( inputState.guessing==0 ) {
			functionCall_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			functionCall_AST.copyLexInfo(q_AST);
		}
		match(RPAREN);
		functionCall_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = functionCall_AST;
	}
	
	public final void contextItemExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST contextItemExpr_AST = null;
		
		org.exist.xquery.parser.XQueryAST tmp207_AST = null;
		tmp207_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp207_AST);
		match(SELF);
		contextItemExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = contextItemExpr_AST;
	}
	
	public final void parenthesizedExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST parenthesizedExpr_AST = null;
		org.exist.xquery.parser.XQueryAST e_AST = null;
		
		match(LPAREN);
		{
		switch ( LA(1)) {
		case LPAREN:
		case NCNAME:
		case LITERAL_xquery:
		case LITERAL_version:
		case LITERAL_module:
		case LITERAL_namespace:
		case STRING_LITERAL:
		case LITERAL_import:
		case LITERAL_declare:
		case LITERAL_default:
		case LITERAL_xmlspace:
		case LITERAL_function:
		case LITERAL_variable:
		case LITERAL_collation:
		case LITERAL_element:
		case LITERAL_preserve:
		case LITERAL_strip:
		case DOLLAR:
		case LITERAL_at:
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
		case LITERAL_return:
		case LITERAL_order:
		case LITERAL_by:
		case LITERAL_then:
		case LITERAL_else:
		case LITERAL_or:
		case LITERAL_and:
		case LITERAL_instance:
		case LITERAL_of:
		case LITERAL_cast:
		case LT:
		case LITERAL_is:
		case LITERAL_isnot:
		case LITERAL_to:
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
		case LITERAL_attribute:
		case LITERAL_comment:
		case 131:
		case 132:
		case LITERAL_document:
		case SELF:
		case XML_COMMENT:
		case XML_PI:
		case AT:
		case PARENT:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_descendant:
		case 144:
		case 145:
		case LITERAL_following:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 149:
		case 150:
		case DOUBLE_LITERAL:
		case DECIMAL_LITERAL:
		case INTEGER_LITERAL:
		case LITERAL_collection:
		case LITERAL_preceding:
		{
			expr();
			e_AST = (org.exist.xquery.parser.XQueryAST)returnAST;
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
			parenthesizedExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			parenthesizedExpr_AST= (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(PARENTHESIZED,"Parenthesized")).add(e_AST));
			currentAST.root = parenthesizedExpr_AST;
			currentAST.child = parenthesizedExpr_AST!=null &&parenthesizedExpr_AST.getFirstChild()!=null ?
				parenthesizedExpr_AST.getFirstChild() : parenthesizedExpr_AST;
			currentAST.advanceChildToEnd();
		}
		parenthesizedExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = parenthesizedExpr_AST;
	}
	
	public final void numericLiteral() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST numericLiteral_AST = null;
		
		switch ( LA(1)) {
		case DOUBLE_LITERAL:
		{
			org.exist.xquery.parser.XQueryAST tmp210_AST = null;
			tmp210_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp210_AST);
			match(DOUBLE_LITERAL);
			numericLiteral_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case DECIMAL_LITERAL:
		{
			org.exist.xquery.parser.XQueryAST tmp211_AST = null;
			tmp211_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp211_AST);
			match(DECIMAL_LITERAL);
			numericLiteral_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case INTEGER_LITERAL:
		{
			org.exist.xquery.parser.XQueryAST tmp212_AST = null;
			tmp212_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.makeASTRoot(currentAST, tmp212_AST);
			match(INTEGER_LITERAL);
			numericLiteral_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
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
		org.exist.xquery.parser.XQueryAST functionParameters_AST = null;
		
		exprSingle();
		astFactory.addASTChild(currentAST, returnAST);
		{
		_loop223:
		do {
			if ((LA(1)==COMMA)) {
				match(COMMA);
				exprSingle();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop223;
			}
			
		} while (true);
		}
		functionParameters_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = functionParameters_AST;
	}
	
	public final void textTest() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST textTest_AST = null;
		
		org.exist.xquery.parser.XQueryAST tmp214_AST = null;
		tmp214_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp214_AST);
		match(LITERAL_text);
		match(LPAREN);
		match(RPAREN);
		textTest_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = textTest_AST;
	}
	
	public final void anyKindTest() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST anyKindTest_AST = null;
		
		org.exist.xquery.parser.XQueryAST tmp217_AST = null;
		tmp217_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp217_AST);
		match(LITERAL_node);
		match(LPAREN);
		match(RPAREN);
		anyKindTest_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = anyKindTest_AST;
	}
	
	public final void elementTest() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST elementTest_AST = null;
		
		org.exist.xquery.parser.XQueryAST tmp220_AST = null;
		tmp220_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp220_AST);
		match(LITERAL_element);
		match(LPAREN);
		{
		switch ( LA(1)) {
		case NCNAME:
		case LITERAL_xquery:
		case LITERAL_version:
		case LITERAL_module:
		case LITERAL_namespace:
		case LITERAL_import:
		case LITERAL_declare:
		case LITERAL_default:
		case LITERAL_xmlspace:
		case LITERAL_function:
		case LITERAL_variable:
		case LITERAL_collation:
		case LITERAL_element:
		case LITERAL_preserve:
		case LITERAL_strip:
		case LITERAL_at:
		case LITERAL_as:
		case LITERAL_empty:
		case STAR:
		case LITERAL_item:
		case LITERAL_for:
		case LITERAL_let:
		case LITERAL_some:
		case LITERAL_every:
		case LITERAL_if:
		case LITERAL_return:
		case LITERAL_order:
		case LITERAL_by:
		case LITERAL_then:
		case LITERAL_else:
		case LITERAL_or:
		case LITERAL_and:
		case LITERAL_instance:
		case LITERAL_of:
		case LITERAL_cast:
		case LITERAL_is:
		case LITERAL_isnot:
		case LITERAL_to:
		case LITERAL_div:
		case LITERAL_mod:
		case LITERAL_union:
		case LITERAL_intersect:
		case LITERAL_except:
		case LITERAL_text:
		case LITERAL_node:
		case LITERAL_attribute:
		case LITERAL_comment:
		case 132:
		case LITERAL_document:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_descendant:
		case 144:
		case 145:
		case LITERAL_following:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 149:
		case 150:
		case LITERAL_collection:
		case LITERAL_preceding:
		{
			elementNameOrWildcard();
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
		elementTest_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = elementTest_AST;
	}
	
	public final void attributeTest() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST attributeTest_AST = null;
		
		match(LITERAL_attribute);
		match(LPAREN);
		{
		switch ( LA(1)) {
		case NCNAME:
		case LITERAL_xquery:
		case LITERAL_version:
		case LITERAL_module:
		case LITERAL_namespace:
		case LITERAL_import:
		case LITERAL_declare:
		case LITERAL_default:
		case LITERAL_xmlspace:
		case LITERAL_function:
		case LITERAL_variable:
		case LITERAL_collation:
		case LITERAL_element:
		case LITERAL_preserve:
		case LITERAL_strip:
		case LITERAL_at:
		case LITERAL_as:
		case LITERAL_empty:
		case STAR:
		case LITERAL_item:
		case LITERAL_for:
		case LITERAL_let:
		case LITERAL_some:
		case LITERAL_every:
		case LITERAL_if:
		case LITERAL_return:
		case LITERAL_order:
		case LITERAL_by:
		case LITERAL_then:
		case LITERAL_else:
		case LITERAL_or:
		case LITERAL_and:
		case LITERAL_instance:
		case LITERAL_of:
		case LITERAL_cast:
		case LITERAL_is:
		case LITERAL_isnot:
		case LITERAL_to:
		case LITERAL_div:
		case LITERAL_mod:
		case LITERAL_union:
		case LITERAL_intersect:
		case LITERAL_except:
		case LITERAL_text:
		case LITERAL_node:
		case LITERAL_attribute:
		case LITERAL_comment:
		case 132:
		case LITERAL_document:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_descendant:
		case 144:
		case 145:
		case LITERAL_following:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 149:
		case 150:
		case LITERAL_collection:
		case LITERAL_preceding:
		{
			attributeNameOrWildcard();
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
			attributeTest_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			attributeTest_AST= (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(ATTRIBUTE_TEST,"attribute()")).add(attributeTest_AST));
			currentAST.root = attributeTest_AST;
			currentAST.child = attributeTest_AST!=null &&attributeTest_AST.getFirstChild()!=null ?
				attributeTest_AST.getFirstChild() : attributeTest_AST;
			currentAST.advanceChildToEnd();
		}
		attributeTest_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = attributeTest_AST;
	}
	
	public final void commentTest() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST commentTest_AST = null;
		
		org.exist.xquery.parser.XQueryAST tmp226_AST = null;
		tmp226_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp226_AST);
		match(LITERAL_comment);
		match(LPAREN);
		match(RPAREN);
		commentTest_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = commentTest_AST;
	}
	
	public final void piTest() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST piTest_AST = null;
		
		org.exist.xquery.parser.XQueryAST tmp229_AST = null;
		tmp229_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp229_AST);
		match(131);
		match(LPAREN);
		match(RPAREN);
		piTest_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = piTest_AST;
	}
	
	public final void documentTest() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST documentTest_AST = null;
		
		org.exist.xquery.parser.XQueryAST tmp232_AST = null;
		tmp232_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp232_AST);
		match(132);
		match(LPAREN);
		match(RPAREN);
		documentTest_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = documentTest_AST;
	}
	
	public final void elementNameOrWildcard() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST elementNameOrWildcard_AST = null;
		String qn = null;
		
		switch ( LA(1)) {
		case STAR:
		{
			org.exist.xquery.parser.XQueryAST tmp235_AST = null;
			tmp235_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp235_AST);
			match(STAR);
			if ( inputState.guessing==0 ) {
				elementNameOrWildcard_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
				elementNameOrWildcard_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(WILDCARD,"*");
				currentAST.root = elementNameOrWildcard_AST;
				currentAST.child = elementNameOrWildcard_AST!=null &&elementNameOrWildcard_AST.getFirstChild()!=null ?
					elementNameOrWildcard_AST.getFirstChild() : elementNameOrWildcard_AST;
				currentAST.advanceChildToEnd();
			}
			elementNameOrWildcard_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case NCNAME:
		case LITERAL_xquery:
		case LITERAL_version:
		case LITERAL_module:
		case LITERAL_namespace:
		case LITERAL_import:
		case LITERAL_declare:
		case LITERAL_default:
		case LITERAL_xmlspace:
		case LITERAL_function:
		case LITERAL_variable:
		case LITERAL_collation:
		case LITERAL_element:
		case LITERAL_preserve:
		case LITERAL_strip:
		case LITERAL_at:
		case LITERAL_as:
		case LITERAL_empty:
		case LITERAL_item:
		case LITERAL_for:
		case LITERAL_let:
		case LITERAL_some:
		case LITERAL_every:
		case LITERAL_if:
		case LITERAL_return:
		case LITERAL_order:
		case LITERAL_by:
		case LITERAL_then:
		case LITERAL_else:
		case LITERAL_or:
		case LITERAL_and:
		case LITERAL_instance:
		case LITERAL_of:
		case LITERAL_cast:
		case LITERAL_is:
		case LITERAL_isnot:
		case LITERAL_to:
		case LITERAL_div:
		case LITERAL_mod:
		case LITERAL_union:
		case LITERAL_intersect:
		case LITERAL_except:
		case LITERAL_text:
		case LITERAL_node:
		case LITERAL_attribute:
		case LITERAL_comment:
		case 132:
		case LITERAL_document:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_descendant:
		case 144:
		case 145:
		case LITERAL_following:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 149:
		case 150:
		case LITERAL_collection:
		case LITERAL_preceding:
		{
			qn=qName();
			astFactory.addASTChild(currentAST, returnAST);
			if ( inputState.guessing==0 ) {
				elementNameOrWildcard_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
				elementNameOrWildcard_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(QNAME,qn);
				currentAST.root = elementNameOrWildcard_AST;
				currentAST.child = elementNameOrWildcard_AST!=null &&elementNameOrWildcard_AST.getFirstChild()!=null ?
					elementNameOrWildcard_AST.getFirstChild() : elementNameOrWildcard_AST;
				currentAST.advanceChildToEnd();
			}
			elementNameOrWildcard_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		returnAST = elementNameOrWildcard_AST;
	}
	
	public final void attributeNameOrWildcard() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST attributeNameOrWildcard_AST = null;
		String qn = null;
		
		switch ( LA(1)) {
		case STAR:
		{
			org.exist.xquery.parser.XQueryAST tmp236_AST = null;
			tmp236_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp236_AST);
			match(STAR);
			if ( inputState.guessing==0 ) {
				attributeNameOrWildcard_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
				attributeNameOrWildcard_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(WILDCARD,"*");
				currentAST.root = attributeNameOrWildcard_AST;
				currentAST.child = attributeNameOrWildcard_AST!=null &&attributeNameOrWildcard_AST.getFirstChild()!=null ?
					attributeNameOrWildcard_AST.getFirstChild() : attributeNameOrWildcard_AST;
				currentAST.advanceChildToEnd();
			}
			attributeNameOrWildcard_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case NCNAME:
		case LITERAL_xquery:
		case LITERAL_version:
		case LITERAL_module:
		case LITERAL_namespace:
		case LITERAL_import:
		case LITERAL_declare:
		case LITERAL_default:
		case LITERAL_xmlspace:
		case LITERAL_function:
		case LITERAL_variable:
		case LITERAL_collation:
		case LITERAL_element:
		case LITERAL_preserve:
		case LITERAL_strip:
		case LITERAL_at:
		case LITERAL_as:
		case LITERAL_empty:
		case LITERAL_item:
		case LITERAL_for:
		case LITERAL_let:
		case LITERAL_some:
		case LITERAL_every:
		case LITERAL_if:
		case LITERAL_return:
		case LITERAL_order:
		case LITERAL_by:
		case LITERAL_then:
		case LITERAL_else:
		case LITERAL_or:
		case LITERAL_and:
		case LITERAL_instance:
		case LITERAL_of:
		case LITERAL_cast:
		case LITERAL_is:
		case LITERAL_isnot:
		case LITERAL_to:
		case LITERAL_div:
		case LITERAL_mod:
		case LITERAL_union:
		case LITERAL_intersect:
		case LITERAL_except:
		case LITERAL_text:
		case LITERAL_node:
		case LITERAL_attribute:
		case LITERAL_comment:
		case 132:
		case LITERAL_document:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_descendant:
		case 144:
		case 145:
		case LITERAL_following:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 149:
		case 150:
		case LITERAL_collection:
		case LITERAL_preceding:
		{
			qn=qName();
			astFactory.addASTChild(currentAST, returnAST);
			if ( inputState.guessing==0 ) {
				attributeNameOrWildcard_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
				attributeNameOrWildcard_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(QNAME,qn);
				currentAST.root = attributeNameOrWildcard_AST;
				currentAST.child = attributeNameOrWildcard_AST!=null &&attributeNameOrWildcard_AST.getFirstChild()!=null ?
					attributeNameOrWildcard_AST.getFirstChild() : attributeNameOrWildcard_AST;
				currentAST.advanceChildToEnd();
			}
			attributeNameOrWildcard_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		returnAST = attributeNameOrWildcard_AST;
	}
	
	public final void elementConstructor() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST elementConstructor_AST = null;
		
			String name= null;
			//lexer.wsExplicit= false;
		
		
		boolean synPredMatched267 = false;
		if (((LA(1)==LT))) {
			int _m267 = mark();
			synPredMatched267 = true;
			inputState.guessing++;
			try {
				{
				match(LT);
				qName();
				{
				match(_tokenSet_11);
				}
				}
			}
			catch (RecognitionException pe) {
				synPredMatched267 = false;
			}
			rewind(_m267);
			inputState.guessing--;
		}
		if ( synPredMatched267 ) {
			elementWithAttributes();
			astFactory.addASTChild(currentAST, returnAST);
			elementConstructor_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		}
		else if ((LA(1)==LT)) {
			elementWithoutAttributes();
			astFactory.addASTChild(currentAST, returnAST);
			elementConstructor_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		}
		else {
			throw new NoViableAltException(LT(1), getFilename());
		}
		
		returnAST = elementConstructor_AST;
	}
	
	public final void xmlComment() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST xmlComment_AST = null;
		
		org.exist.xquery.parser.XQueryAST tmp237_AST = null;
		tmp237_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp237_AST);
		match(XML_COMMENT);
		match(XML_COMMENT_END);
		xmlComment_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = xmlComment_AST;
	}
	
	public final void xmlPI() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST xmlPI_AST = null;
		
		org.exist.xquery.parser.XQueryAST tmp239_AST = null;
		tmp239_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp239_AST);
		match(XML_PI);
		match(XML_PI_END);
		xmlPI_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = xmlPI_AST;
	}
	
	public final void compElemConstructor() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST compElemConstructor_AST = null;
		org.exist.xquery.parser.XQueryAST e3_AST = null;
		
			String qn;
		
		
		boolean synPredMatched244 = false;
		if (((LA(1)==LITERAL_element))) {
			int _m244 = mark();
			synPredMatched244 = true;
			inputState.guessing++;
			try {
				{
				match(LITERAL_element);
				match(LCURLY);
				}
			}
			catch (RecognitionException pe) {
				synPredMatched244 = false;
			}
			rewind(_m244);
			inputState.guessing--;
		}
		if ( synPredMatched244 ) {
			match(LITERAL_element);
			match(LCURLY);
			expr();
			astFactory.addASTChild(currentAST, returnAST);
			match(RCURLY);
			match(LCURLY);
			compElemBody();
			astFactory.addASTChild(currentAST, returnAST);
			match(RCURLY);
			if ( inputState.guessing==0 ) {
				compElemConstructor_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
				compElemConstructor_AST = (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(COMP_ELEM_CONSTRUCTOR)).add(compElemConstructor_AST));
				currentAST.root = compElemConstructor_AST;
				currentAST.child = compElemConstructor_AST!=null &&compElemConstructor_AST.getFirstChild()!=null ?
					compElemConstructor_AST.getFirstChild() : compElemConstructor_AST;
				currentAST.advanceChildToEnd();
			}
			compElemConstructor_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		}
		else if ((LA(1)==LITERAL_element)) {
			match(LITERAL_element);
			qn=qName();
			astFactory.addASTChild(currentAST, returnAST);
			match(LCURLY);
			compElemBody();
			e3_AST = (org.exist.xquery.parser.XQueryAST)returnAST;
			astFactory.addASTChild(currentAST, returnAST);
			match(RCURLY);
			if ( inputState.guessing==0 ) {
				compElemConstructor_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
				compElemConstructor_AST = (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(3)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(COMP_ELEM_CONSTRUCTOR,qn)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(STRING_LITERAL,qn)).add(e3_AST));
				currentAST.root = compElemConstructor_AST;
				currentAST.child = compElemConstructor_AST!=null &&compElemConstructor_AST.getFirstChild()!=null ?
					compElemConstructor_AST.getFirstChild() : compElemConstructor_AST;
				currentAST.advanceChildToEnd();
			}
			compElemConstructor_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		}
		else {
			throw new NoViableAltException(LT(1), getFilename());
		}
		
		returnAST = compElemConstructor_AST;
	}
	
	public final void compAttrConstructor() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST compAttrConstructor_AST = null;
		org.exist.xquery.parser.XQueryAST e1_AST = null;
		org.exist.xquery.parser.XQueryAST e2_AST = null;
		org.exist.xquery.parser.XQueryAST e3_AST = null;
		
			String qn;
		
		
		boolean synPredMatched256 = false;
		if (((LA(1)==LITERAL_attribute))) {
			int _m256 = mark();
			synPredMatched256 = true;
			inputState.guessing++;
			try {
				{
				match(LITERAL_attribute);
				match(LCURLY);
				}
			}
			catch (RecognitionException pe) {
				synPredMatched256 = false;
			}
			rewind(_m256);
			inputState.guessing--;
		}
		if ( synPredMatched256 ) {
			match(LITERAL_attribute);
			match(LCURLY);
			expr();
			e1_AST = (org.exist.xquery.parser.XQueryAST)returnAST;
			astFactory.addASTChild(currentAST, returnAST);
			match(RCURLY);
			match(LCURLY);
			expr();
			e2_AST = (org.exist.xquery.parser.XQueryAST)returnAST;
			astFactory.addASTChild(currentAST, returnAST);
			match(RCURLY);
			if ( inputState.guessing==0 ) {
				compAttrConstructor_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
				compAttrConstructor_AST = (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(COMP_ATTR_CONSTRUCTOR)).add(compAttrConstructor_AST));
				currentAST.root = compAttrConstructor_AST;
				currentAST.child = compAttrConstructor_AST!=null &&compAttrConstructor_AST.getFirstChild()!=null ?
					compAttrConstructor_AST.getFirstChild() : compAttrConstructor_AST;
				currentAST.advanceChildToEnd();
			}
			compAttrConstructor_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		}
		else if ((LA(1)==LITERAL_attribute)) {
			match(LITERAL_attribute);
			qn=qName();
			astFactory.addASTChild(currentAST, returnAST);
			match(LCURLY);
			expr();
			e3_AST = (org.exist.xquery.parser.XQueryAST)returnAST;
			astFactory.addASTChild(currentAST, returnAST);
			match(RCURLY);
			if ( inputState.guessing==0 ) {
				compAttrConstructor_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
				compAttrConstructor_AST = (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(3)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(COMP_ATTR_CONSTRUCTOR,qn)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(STRING_LITERAL,qn)).add(e3_AST));
				currentAST.root = compAttrConstructor_AST;
				currentAST.child = compAttrConstructor_AST!=null &&compAttrConstructor_AST.getFirstChild()!=null ?
					compAttrConstructor_AST.getFirstChild() : compAttrConstructor_AST;
				currentAST.advanceChildToEnd();
			}
			compAttrConstructor_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		}
		else {
			throw new NoViableAltException(LT(1), getFilename());
		}
		
		returnAST = compAttrConstructor_AST;
	}
	
	public final void compTextConstructor() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST compTextConstructor_AST = null;
		org.exist.xquery.parser.XQueryAST e_AST = null;
		
		org.exist.xquery.parser.XQueryAST tmp257_AST = null;
		tmp257_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp257_AST);
		match(LITERAL_text);
		match(LCURLY);
		expr();
		e_AST = (org.exist.xquery.parser.XQueryAST)returnAST;
		astFactory.addASTChild(currentAST, returnAST);
		match(RCURLY);
		if ( inputState.guessing==0 ) {
			compTextConstructor_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			compTextConstructor_AST = (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(COMP_TEXT_CONSTRUCTOR,"text")).add(e_AST));
			currentAST.root = compTextConstructor_AST;
			currentAST.child = compTextConstructor_AST!=null &&compTextConstructor_AST.getFirstChild()!=null ?
				compTextConstructor_AST.getFirstChild() : compTextConstructor_AST;
			currentAST.advanceChildToEnd();
		}
		compTextConstructor_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = compTextConstructor_AST;
	}
	
	public final void compDocumentConstructor() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST compDocumentConstructor_AST = null;
		org.exist.xquery.parser.XQueryAST e_AST = null;
		
		org.exist.xquery.parser.XQueryAST tmp260_AST = null;
		tmp260_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp260_AST);
		match(LITERAL_document);
		match(LCURLY);
		expr();
		e_AST = (org.exist.xquery.parser.XQueryAST)returnAST;
		astFactory.addASTChild(currentAST, returnAST);
		match(RCURLY);
		if ( inputState.guessing==0 ) {
			compDocumentConstructor_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			compDocumentConstructor_AST = (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(COMP_DOC_CONSTRUCTOR,"document")).add(e_AST));
			currentAST.root = compDocumentConstructor_AST;
			currentAST.child = compDocumentConstructor_AST!=null &&compDocumentConstructor_AST.getFirstChild()!=null ?
				compDocumentConstructor_AST.getFirstChild() : compDocumentConstructor_AST;
			currentAST.advanceChildToEnd();
		}
		compDocumentConstructor_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = compDocumentConstructor_AST;
	}
	
	public final void compXmlPI() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST compXmlPI_AST = null;
		org.exist.xquery.parser.XQueryAST e1_AST = null;
		org.exist.xquery.parser.XQueryAST e2_AST = null;
		org.exist.xquery.parser.XQueryAST e3_AST = null;
		
			String qn;
		
		
		boolean synPredMatched261 = false;
		if (((LA(1)==131))) {
			int _m261 = mark();
			synPredMatched261 = true;
			inputState.guessing++;
			try {
				{
				match(131);
				match(LCURLY);
				}
			}
			catch (RecognitionException pe) {
				synPredMatched261 = false;
			}
			rewind(_m261);
			inputState.guessing--;
		}
		if ( synPredMatched261 ) {
			match(131);
			match(LCURLY);
			expr();
			e1_AST = (org.exist.xquery.parser.XQueryAST)returnAST;
			astFactory.addASTChild(currentAST, returnAST);
			match(RCURLY);
			match(LCURLY);
			expr();
			e2_AST = (org.exist.xquery.parser.XQueryAST)returnAST;
			astFactory.addASTChild(currentAST, returnAST);
			match(RCURLY);
			if ( inputState.guessing==0 ) {
				compXmlPI_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
				compXmlPI_AST = (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(COMP_PI_CONSTRUCTOR)).add(compXmlPI_AST));
				currentAST.root = compXmlPI_AST;
				currentAST.child = compXmlPI_AST!=null &&compXmlPI_AST.getFirstChild()!=null ?
					compXmlPI_AST.getFirstChild() : compXmlPI_AST;
				currentAST.advanceChildToEnd();
			}
			compXmlPI_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		}
		else if ((LA(1)==131)) {
			match(131);
			qn=qName();
			astFactory.addASTChild(currentAST, returnAST);
			match(LCURLY);
			expr();
			e3_AST = (org.exist.xquery.parser.XQueryAST)returnAST;
			astFactory.addASTChild(currentAST, returnAST);
			match(RCURLY);
			if ( inputState.guessing==0 ) {
				compXmlPI_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
				compXmlPI_AST = (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(3)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(COMP_PI_CONSTRUCTOR,qn)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(STRING_LITERAL,qn)).add(e3_AST));
				currentAST.root = compXmlPI_AST;
				currentAST.child = compXmlPI_AST!=null &&compXmlPI_AST.getFirstChild()!=null ?
					compXmlPI_AST.getFirstChild() : compXmlPI_AST;
				currentAST.advanceChildToEnd();
			}
			compXmlPI_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		}
		else {
			throw new NoViableAltException(LT(1), getFilename());
		}
		
		returnAST = compXmlPI_AST;
	}
	
	public final void compXmlComment() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST compXmlComment_AST = null;
		org.exist.xquery.parser.XQueryAST e_AST = null;
		
		org.exist.xquery.parser.XQueryAST tmp271_AST = null;
		tmp271_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp271_AST);
		match(LITERAL_comment);
		match(LCURLY);
		expr();
		e_AST = (org.exist.xquery.parser.XQueryAST)returnAST;
		astFactory.addASTChild(currentAST, returnAST);
		match(RCURLY);
		if ( inputState.guessing==0 ) {
			compXmlComment_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			compXmlComment_AST = (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(COMP_COMMENT_CONSTRUCTOR,"comment")).add(e_AST));
			currentAST.root = compXmlComment_AST;
			currentAST.child = compXmlComment_AST!=null &&compXmlComment_AST.getFirstChild()!=null ?
				compXmlComment_AST.getFirstChild() : compXmlComment_AST;
			currentAST.advanceChildToEnd();
		}
		compXmlComment_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = compXmlComment_AST;
	}
	
	public final void compElemBody() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST compElemBody_AST = null;
		
		{
		boolean synPredMatched248 = false;
		if (((LA(1)==LITERAL_namespace))) {
			int _m248 = mark();
			synPredMatched248 = true;
			inputState.guessing++;
			try {
				{
				match(LITERAL_namespace);
				ncnameOrKeyword();
				match(LCURLY);
				}
			}
			catch (RecognitionException pe) {
				synPredMatched248 = false;
			}
			rewind(_m248);
			inputState.guessing--;
		}
		if ( synPredMatched248 ) {
			localNamespaceDecl();
			astFactory.addASTChild(currentAST, returnAST);
		}
		else if ((_tokenSet_0.member(LA(1)))) {
			exprSingle();
			astFactory.addASTChild(currentAST, returnAST);
		}
		else {
			throw new NoViableAltException(LT(1), getFilename());
		}
		
		}
		{
		_loop253:
		do {
			if ((LA(1)==COMMA)) {
				match(COMMA);
				{
				boolean synPredMatched252 = false;
				if (((LA(1)==LITERAL_namespace))) {
					int _m252 = mark();
					synPredMatched252 = true;
					inputState.guessing++;
					try {
						{
						match(LITERAL_namespace);
						ncnameOrKeyword();
						match(LCURLY);
						}
					}
					catch (RecognitionException pe) {
						synPredMatched252 = false;
					}
					rewind(_m252);
					inputState.guessing--;
				}
				if ( synPredMatched252 ) {
					localNamespaceDecl();
					astFactory.addASTChild(currentAST, returnAST);
				}
				else if ((_tokenSet_0.member(LA(1)))) {
					exprSingle();
					astFactory.addASTChild(currentAST, returnAST);
				}
				else {
					throw new NoViableAltException(LT(1), getFilename());
				}
				
				}
			}
			else {
				break _loop253;
			}
			
		} while (true);
		}
		compElemBody_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = compElemBody_AST;
	}
	
	public final void localNamespaceDecl() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST localNamespaceDecl_AST = null;
		Token  l = null;
		org.exist.xquery.parser.XQueryAST l_AST = null;
		
			String nc = null;
		
		
		match(LITERAL_namespace);
		nc=ncnameOrKeyword();
		astFactory.addASTChild(currentAST, returnAST);
		match(LCURLY);
		l = LT(1);
		l_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(l);
		astFactory.addASTChild(currentAST, l_AST);
		match(STRING_LITERAL);
		match(RCURLY);
		if ( inputState.guessing==0 ) {
			localNamespaceDecl_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			localNamespaceDecl_AST = (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(COMP_NS_CONSTRUCTOR,nc)).add(l_AST));
			currentAST.root = localNamespaceDecl_AST;
			currentAST.child = localNamespaceDecl_AST!=null &&localNamespaceDecl_AST.getFirstChild()!=null ?
				localNamespaceDecl_AST.getFirstChild() : localNamespaceDecl_AST;
			currentAST.advanceChildToEnd();
		}
		localNamespaceDecl_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = localNamespaceDecl_AST;
	}
	
	public final void elementWithAttributes() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST elementWithAttributes_AST = null;
		org.exist.xquery.parser.XQueryAST q_AST = null;
		org.exist.xquery.parser.XQueryAST attrs_AST = null;
		org.exist.xquery.parser.XQueryAST content_AST = null;
		String name= null;
		
		match(LT);
		name=qName();
		q_AST = (org.exist.xquery.parser.XQueryAST)returnAST;
		astFactory.addASTChild(currentAST, returnAST);
		attributeList();
		attrs_AST = (org.exist.xquery.parser.XQueryAST)returnAST;
		astFactory.addASTChild(currentAST, returnAST);
		{
		switch ( LA(1)) {
		case SLASH:
		{
			{
			match(SLASH);
			match(GT);
			if ( inputState.guessing==0 ) {
				elementWithAttributes_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
				
								if (!elementStack.isEmpty())
									lexer.inElementContent= true;
								//lexer.wsExplicit= false;
								elementWithAttributes_AST= (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(ELEMENT,name)).add(attrs_AST));
							
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
								//lexer.wsExplicit= false;
							
			}
			mixedElementContent();
			content_AST = (org.exist.xquery.parser.XQueryAST)returnAST;
			astFactory.addASTChild(currentAST, returnAST);
			match(END_TAG_START);
			name=qName();
			match(GT);
			if ( inputState.guessing==0 ) {
				elementWithAttributes_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
				
								if (elementStack.isEmpty())
									throw new RecognitionException("found closing tag without opening tag: " + name);
								String prev= (String) elementStack.pop();
								if (!prev.equals(name))
									throw new RecognitionException("found closing tag: " + name + "; expected: " + prev);
								elementWithAttributes_AST= (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(ELEMENT,name)).add(attrs_AST));
								if (!elementStack.isEmpty()) {
									lexer.inElementContent= true;
									//lexer.wsExplicit= false;
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
		if ( inputState.guessing==0 ) {
			elementWithAttributes_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			elementWithAttributes_AST.copyLexInfo(q_AST);
		}
		elementWithAttributes_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = elementWithAttributes_AST;
	}
	
	public final void elementWithoutAttributes() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST elementWithoutAttributes_AST = null;
		org.exist.xquery.parser.XQueryAST q_AST = null;
		org.exist.xquery.parser.XQueryAST content_AST = null;
		String name= null;
		
		org.exist.xquery.parser.XQueryAST tmp284_AST = null;
		tmp284_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.addASTChild(currentAST, tmp284_AST);
		match(LT);
		name=qName();
		q_AST = (org.exist.xquery.parser.XQueryAST)returnAST;
		astFactory.addASTChild(currentAST, returnAST);
		{
		switch ( LA(1)) {
		case SLASH:
		{
			{
			match(SLASH);
			match(GT);
			if ( inputState.guessing==0 ) {
				elementWithoutAttributes_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
				
								//lexer.wsExplicit= false;
								if (!elementStack.isEmpty())
									lexer.inElementContent= true;
								elementWithoutAttributes_AST= (org.exist.xquery.parser.XQueryAST)astFactory.create(ELEMENT,name);
							
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
							
			}
			mixedElementContent();
			content_AST = (org.exist.xquery.parser.XQueryAST)returnAST;
			astFactory.addASTChild(currentAST, returnAST);
			match(END_TAG_START);
			name=qName();
			match(GT);
			if ( inputState.guessing==0 ) {
				elementWithoutAttributes_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
				
								if (elementStack.isEmpty())
									throw new RecognitionException("found wrong closing tag: " + name);
								String prev= (String) elementStack.pop();
								if (!prev.equals(name))
									throw new RecognitionException("found closing tag: " + name + "; expected: " + prev);
								elementWithoutAttributes_AST= (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(ELEMENT,name)).add(content_AST));
								if (!elementStack.isEmpty()) {
									lexer.inElementContent= true;
									//lexer.wsExplicit= false;
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
		if ( inputState.guessing==0 ) {
			elementWithoutAttributes_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			elementWithoutAttributes_AST.copyLexInfo(q_AST);
		}
		elementWithoutAttributes_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = elementWithoutAttributes_AST;
	}
	
	public final void mixedElementContent() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST mixedElementContent_AST = null;
		
		{
		_loop286:
		do {
			if ((_tokenSet_12.member(LA(1)))) {
				elementContent();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				break _loop286;
			}
			
		} while (true);
		}
		mixedElementContent_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = mixedElementContent_AST;
	}
	
	public final void attributeList() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST attributeList_AST = null;
		
		{
		int _cnt278=0;
		_loop278:
		do {
			if ((_tokenSet_1.member(LA(1)))) {
				attributeDef();
				astFactory.addASTChild(currentAST, returnAST);
			}
			else {
				if ( _cnt278>=1 ) { break _loop278; } else {throw new NoViableAltException(LT(1), getFilename());}
			}
			
			_cnt278++;
		} while (true);
		}
		attributeList_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = attributeList_AST;
	}
	
	public final void attributeDef() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST attributeDef_AST = null;
		org.exist.xquery.parser.XQueryAST q_AST = null;
		
			String name= null;
			lexer.parseStringLiterals= false;
		
		
		name=qName();
		q_AST = (org.exist.xquery.parser.XQueryAST)returnAST;
		match(EQ);
		{
		switch ( LA(1)) {
		case QUOT:
		{
			match(QUOT);
			if ( inputState.guessing==0 ) {
				lexer.inAttributeContent= true;
			}
			attributeValue();
			astFactory.addASTChild(currentAST, returnAST);
			if ( inputState.guessing==0 ) {
				lexer.inAttributeContent= false;
			}
			match(QUOT);
			if ( inputState.guessing==0 ) {
				
							lexer.parseStringLiterals= true;
							lexer.inAttributeContent= false;
						
			}
			break;
		}
		case APOS:
		{
			match(APOS);
			if ( inputState.guessing==0 ) {
				lexer.inAttributeContent= true;
			}
			attributeValue();
			astFactory.addASTChild(currentAST, returnAST);
			match(APOS);
			if ( inputState.guessing==0 ) {
				
							lexer.parseStringLiterals= true;
							lexer.inAttributeContent= false;
						
			}
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		}
		if ( inputState.guessing==0 ) {
			attributeDef_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			
					attributeDef_AST= (org.exist.xquery.parser.XQueryAST)astFactory.make( (new ASTArray(2)).add((org.exist.xquery.parser.XQueryAST)astFactory.create(ATTRIBUTE,name)).add(attributeDef_AST));
					attributeDef_AST.copyLexInfo(q_AST);
				
			currentAST.root = attributeDef_AST;
			currentAST.child = attributeDef_AST!=null &&attributeDef_AST.getFirstChild()!=null ?
				attributeDef_AST.getFirstChild() : attributeDef_AST;
			currentAST.advanceChildToEnd();
		}
		attributeDef_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = attributeDef_AST;
	}
	
	public final void attributeValue() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST attributeValue_AST = null;
		
		{
		int _cnt283=0;
		_loop283:
		do {
			switch ( LA(1)) {
			case ATTRIBUTE_CONTENT:
			{
				org.exist.xquery.parser.XQueryAST tmp295_AST = null;
				tmp295_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
				astFactory.addASTChild(currentAST, tmp295_AST);
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
				if ( _cnt283>=1 ) { break _loop283; } else {throw new NoViableAltException(LT(1), getFilename());}
			}
			}
			_cnt283++;
		} while (true);
		}
		attributeValue_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = attributeValue_AST;
	}
	
	public final void attributeEnclosedExpr() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST attributeEnclosedExpr_AST = null;
		
		org.exist.xquery.parser.XQueryAST tmp296_AST = null;
		tmp296_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp296_AST);
		match(LCURLY);
		if ( inputState.guessing==0 ) {
			
					lexer.inAttributeContent= false;
					lexer.parseStringLiterals = true;
					//lexer.wsExplicit= false;
				
		}
		expr();
		astFactory.addASTChild(currentAST, returnAST);
		match(RCURLY);
		if ( inputState.guessing==0 ) {
			
					lexer.inAttributeContent= true;
					lexer.parseStringLiterals = false;
					//lexer.wsExplicit= true;
				
		}
		attributeEnclosedExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = attributeEnclosedExpr_AST;
	}
	
	public final void elementContent() throws RecognitionException, TokenStreamException {
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST elementContent_AST = null;
		Token  content = null;
		org.exist.xquery.parser.XQueryAST content_AST = null;
		
		switch ( LA(1)) {
		case LT:
		{
			elementConstructor();
			astFactory.addASTChild(currentAST, returnAST);
			elementContent_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case ELEMENT_CONTENT:
		{
			content = LT(1);
			content_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(content);
			astFactory.addASTChild(currentAST, content_AST);
			match(ELEMENT_CONTENT);
			if ( inputState.guessing==0 ) {
				elementContent_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
				elementContent_AST= (org.exist.xquery.parser.XQueryAST)astFactory.create(TEXT,content.getText());
				currentAST.root = elementContent_AST;
				currentAST.child = elementContent_AST!=null &&elementContent_AST.getFirstChild()!=null ?
					elementContent_AST.getFirstChild() : elementContent_AST;
				currentAST.advanceChildToEnd();
			}
			elementContent_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case XML_COMMENT:
		{
			xmlComment();
			astFactory.addASTChild(currentAST, returnAST);
			elementContent_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case XML_PI:
		{
			xmlPI();
			astFactory.addASTChild(currentAST, returnAST);
			elementContent_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LCURLY:
		{
			enclosedExpr();
			astFactory.addASTChild(currentAST, returnAST);
			elementContent_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
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
		org.exist.xquery.parser.XQueryAST enclosedExpr_AST = null;
		
		org.exist.xquery.parser.XQueryAST tmp298_AST = null;
		tmp298_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
		astFactory.makeASTRoot(currentAST, tmp298_AST);
		match(LCURLY);
		if ( inputState.guessing==0 ) {
			
					globalStack.push(elementStack);
					elementStack= new Stack();
					lexer.inElementContent= false;
					//lexer.wsExplicit= false;
				
		}
		expr();
		astFactory.addASTChild(currentAST, returnAST);
		match(RCURLY);
		if ( inputState.guessing==0 ) {
			
					elementStack= (Stack) globalStack.pop();
					lexer.inElementContent= true;
					//lexer.wsExplicit= true;
				
		}
		enclosedExpr_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
		returnAST = enclosedExpr_AST;
	}
	
	public final String  reservedKeywords() throws RecognitionException, TokenStreamException {
		String name;
		
		returnAST = null;
		ASTPair currentAST = new ASTPair();
		org.exist.xquery.parser.XQueryAST reservedKeywords_AST = null;
		name= null;
		
		switch ( LA(1)) {
		case LITERAL_element:
		{
			org.exist.xquery.parser.XQueryAST tmp300_AST = null;
			tmp300_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp300_AST);
			match(LITERAL_element);
			if ( inputState.guessing==0 ) {
				name = "element";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_to:
		{
			org.exist.xquery.parser.XQueryAST tmp301_AST = null;
			tmp301_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp301_AST);
			match(LITERAL_to);
			if ( inputState.guessing==0 ) {
				name = "to";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_div:
		{
			org.exist.xquery.parser.XQueryAST tmp302_AST = null;
			tmp302_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp302_AST);
			match(LITERAL_div);
			if ( inputState.guessing==0 ) {
				name= "div";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_mod:
		{
			org.exist.xquery.parser.XQueryAST tmp303_AST = null;
			tmp303_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp303_AST);
			match(LITERAL_mod);
			if ( inputState.guessing==0 ) {
				name= "mod";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_text:
		{
			org.exist.xquery.parser.XQueryAST tmp304_AST = null;
			tmp304_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp304_AST);
			match(LITERAL_text);
			if ( inputState.guessing==0 ) {
				name= "text";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_node:
		{
			org.exist.xquery.parser.XQueryAST tmp305_AST = null;
			tmp305_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp305_AST);
			match(LITERAL_node);
			if ( inputState.guessing==0 ) {
				name= "node";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_or:
		{
			org.exist.xquery.parser.XQueryAST tmp306_AST = null;
			tmp306_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp306_AST);
			match(LITERAL_or);
			if ( inputState.guessing==0 ) {
				name= "or";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_and:
		{
			org.exist.xquery.parser.XQueryAST tmp307_AST = null;
			tmp307_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp307_AST);
			match(LITERAL_and);
			if ( inputState.guessing==0 ) {
				name= "and";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_child:
		{
			org.exist.xquery.parser.XQueryAST tmp308_AST = null;
			tmp308_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp308_AST);
			match(LITERAL_child);
			if ( inputState.guessing==0 ) {
				name= "child";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_parent:
		{
			org.exist.xquery.parser.XQueryAST tmp309_AST = null;
			tmp309_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp309_AST);
			match(LITERAL_parent);
			if ( inputState.guessing==0 ) {
				name= "parent";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_self:
		{
			org.exist.xquery.parser.XQueryAST tmp310_AST = null;
			tmp310_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp310_AST);
			match(LITERAL_self);
			if ( inputState.guessing==0 ) {
				name= "self";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_attribute:
		{
			org.exist.xquery.parser.XQueryAST tmp311_AST = null;
			tmp311_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp311_AST);
			match(LITERAL_attribute);
			if ( inputState.guessing==0 ) {
				name= "attribute";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_comment:
		{
			org.exist.xquery.parser.XQueryAST tmp312_AST = null;
			tmp312_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp312_AST);
			match(LITERAL_comment);
			if ( inputState.guessing==0 ) {
				name= "comment";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_document:
		{
			org.exist.xquery.parser.XQueryAST tmp313_AST = null;
			tmp313_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp313_AST);
			match(LITERAL_document);
			if ( inputState.guessing==0 ) {
				name= "document";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case 132:
		{
			org.exist.xquery.parser.XQueryAST tmp314_AST = null;
			tmp314_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp314_AST);
			match(132);
			if ( inputState.guessing==0 ) {
				name= "document-node";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_collection:
		{
			org.exist.xquery.parser.XQueryAST tmp315_AST = null;
			tmp315_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp315_AST);
			match(LITERAL_collection);
			if ( inputState.guessing==0 ) {
				name= "collection";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_ancestor:
		{
			org.exist.xquery.parser.XQueryAST tmp316_AST = null;
			tmp316_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp316_AST);
			match(LITERAL_ancestor);
			if ( inputState.guessing==0 ) {
				name= "ancestor";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_descendant:
		{
			org.exist.xquery.parser.XQueryAST tmp317_AST = null;
			tmp317_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp317_AST);
			match(LITERAL_descendant);
			if ( inputState.guessing==0 ) {
				name= "descendant";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case 144:
		{
			org.exist.xquery.parser.XQueryAST tmp318_AST = null;
			tmp318_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp318_AST);
			match(144);
			if ( inputState.guessing==0 ) {
				name= "descendant-or-self";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case 149:
		{
			org.exist.xquery.parser.XQueryAST tmp319_AST = null;
			tmp319_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp319_AST);
			match(149);
			if ( inputState.guessing==0 ) {
				name= "ancestor-or-self";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case 150:
		{
			org.exist.xquery.parser.XQueryAST tmp320_AST = null;
			tmp320_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp320_AST);
			match(150);
			if ( inputState.guessing==0 ) {
				name= "preceding-sibling";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case 145:
		{
			org.exist.xquery.parser.XQueryAST tmp321_AST = null;
			tmp321_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp321_AST);
			match(145);
			if ( inputState.guessing==0 ) {
				name= "following-sibling";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_following:
		{
			org.exist.xquery.parser.XQueryAST tmp322_AST = null;
			tmp322_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp322_AST);
			match(LITERAL_following);
			if ( inputState.guessing==0 ) {
				name = "following";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_preceding:
		{
			org.exist.xquery.parser.XQueryAST tmp323_AST = null;
			tmp323_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp323_AST);
			match(LITERAL_preceding);
			if ( inputState.guessing==0 ) {
				name = "preceding";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_item:
		{
			org.exist.xquery.parser.XQueryAST tmp324_AST = null;
			tmp324_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp324_AST);
			match(LITERAL_item);
			if ( inputState.guessing==0 ) {
				name= "item";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_empty:
		{
			org.exist.xquery.parser.XQueryAST tmp325_AST = null;
			tmp325_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp325_AST);
			match(LITERAL_empty);
			if ( inputState.guessing==0 ) {
				name= "empty";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_version:
		{
			org.exist.xquery.parser.XQueryAST tmp326_AST = null;
			tmp326_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp326_AST);
			match(LITERAL_version);
			if ( inputState.guessing==0 ) {
				name= "version";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_xquery:
		{
			org.exist.xquery.parser.XQueryAST tmp327_AST = null;
			tmp327_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp327_AST);
			match(LITERAL_xquery);
			if ( inputState.guessing==0 ) {
				name= "xquery";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_variable:
		{
			org.exist.xquery.parser.XQueryAST tmp328_AST = null;
			tmp328_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp328_AST);
			match(LITERAL_variable);
			if ( inputState.guessing==0 ) {
				name= "variable";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_namespace:
		{
			org.exist.xquery.parser.XQueryAST tmp329_AST = null;
			tmp329_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp329_AST);
			match(LITERAL_namespace);
			if ( inputState.guessing==0 ) {
				name= "namespace";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_if:
		{
			org.exist.xquery.parser.XQueryAST tmp330_AST = null;
			tmp330_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp330_AST);
			match(LITERAL_if);
			if ( inputState.guessing==0 ) {
				name= "if";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_then:
		{
			org.exist.xquery.parser.XQueryAST tmp331_AST = null;
			tmp331_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp331_AST);
			match(LITERAL_then);
			if ( inputState.guessing==0 ) {
				name= "then";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_else:
		{
			org.exist.xquery.parser.XQueryAST tmp332_AST = null;
			tmp332_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp332_AST);
			match(LITERAL_else);
			if ( inputState.guessing==0 ) {
				name= "else";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_for:
		{
			org.exist.xquery.parser.XQueryAST tmp333_AST = null;
			tmp333_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp333_AST);
			match(LITERAL_for);
			if ( inputState.guessing==0 ) {
				name= "for";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_let:
		{
			org.exist.xquery.parser.XQueryAST tmp334_AST = null;
			tmp334_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp334_AST);
			match(LITERAL_let);
			if ( inputState.guessing==0 ) {
				name= "let";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_default:
		{
			org.exist.xquery.parser.XQueryAST tmp335_AST = null;
			tmp335_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp335_AST);
			match(LITERAL_default);
			if ( inputState.guessing==0 ) {
				name= "default";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_function:
		{
			org.exist.xquery.parser.XQueryAST tmp336_AST = null;
			tmp336_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp336_AST);
			match(LITERAL_function);
			if ( inputState.guessing==0 ) {
				name= "function";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_as:
		{
			org.exist.xquery.parser.XQueryAST tmp337_AST = null;
			tmp337_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp337_AST);
			match(LITERAL_as);
			if ( inputState.guessing==0 ) {
				name = "as";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_union:
		{
			org.exist.xquery.parser.XQueryAST tmp338_AST = null;
			tmp338_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp338_AST);
			match(LITERAL_union);
			if ( inputState.guessing==0 ) {
				name = "union";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_intersect:
		{
			org.exist.xquery.parser.XQueryAST tmp339_AST = null;
			tmp339_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp339_AST);
			match(LITERAL_intersect);
			if ( inputState.guessing==0 ) {
				name = "intersect";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_except:
		{
			org.exist.xquery.parser.XQueryAST tmp340_AST = null;
			tmp340_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp340_AST);
			match(LITERAL_except);
			if ( inputState.guessing==0 ) {
				name = "except";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_order:
		{
			org.exist.xquery.parser.XQueryAST tmp341_AST = null;
			tmp341_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp341_AST);
			match(LITERAL_order);
			if ( inputState.guessing==0 ) {
				name = "order";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_by:
		{
			org.exist.xquery.parser.XQueryAST tmp342_AST = null;
			tmp342_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp342_AST);
			match(LITERAL_by);
			if ( inputState.guessing==0 ) {
				name = "by";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_some:
		{
			org.exist.xquery.parser.XQueryAST tmp343_AST = null;
			tmp343_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp343_AST);
			match(LITERAL_some);
			if ( inputState.guessing==0 ) {
				name = "some";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_every:
		{
			org.exist.xquery.parser.XQueryAST tmp344_AST = null;
			tmp344_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp344_AST);
			match(LITERAL_every);
			if ( inputState.guessing==0 ) {
				name = "every";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_is:
		{
			org.exist.xquery.parser.XQueryAST tmp345_AST = null;
			tmp345_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp345_AST);
			match(LITERAL_is);
			if ( inputState.guessing==0 ) {
				name = "is";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_isnot:
		{
			org.exist.xquery.parser.XQueryAST tmp346_AST = null;
			tmp346_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp346_AST);
			match(LITERAL_isnot);
			if ( inputState.guessing==0 ) {
				name = "isnot";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_module:
		{
			org.exist.xquery.parser.XQueryAST tmp347_AST = null;
			tmp347_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp347_AST);
			match(LITERAL_module);
			if ( inputState.guessing==0 ) {
				name = "module";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_import:
		{
			org.exist.xquery.parser.XQueryAST tmp348_AST = null;
			tmp348_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp348_AST);
			match(LITERAL_import);
			if ( inputState.guessing==0 ) {
				name = "import";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_at:
		{
			org.exist.xquery.parser.XQueryAST tmp349_AST = null;
			tmp349_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp349_AST);
			match(LITERAL_at);
			if ( inputState.guessing==0 ) {
				name = "at";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_cast:
		{
			org.exist.xquery.parser.XQueryAST tmp350_AST = null;
			tmp350_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp350_AST);
			match(LITERAL_cast);
			if ( inputState.guessing==0 ) {
				name = "cast";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_return:
		{
			org.exist.xquery.parser.XQueryAST tmp351_AST = null;
			tmp351_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp351_AST);
			match(LITERAL_return);
			if ( inputState.guessing==0 ) {
				name = "return";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_instance:
		{
			org.exist.xquery.parser.XQueryAST tmp352_AST = null;
			tmp352_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp352_AST);
			match(LITERAL_instance);
			if ( inputState.guessing==0 ) {
				name = "instance";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_of:
		{
			org.exist.xquery.parser.XQueryAST tmp353_AST = null;
			tmp353_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp353_AST);
			match(LITERAL_of);
			if ( inputState.guessing==0 ) {
				name = "of";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_declare:
		{
			org.exist.xquery.parser.XQueryAST tmp354_AST = null;
			tmp354_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp354_AST);
			match(LITERAL_declare);
			if ( inputState.guessing==0 ) {
				name = "declare";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_collation:
		{
			org.exist.xquery.parser.XQueryAST tmp355_AST = null;
			tmp355_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp355_AST);
			match(LITERAL_collation);
			if ( inputState.guessing==0 ) {
				name = "collation";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_xmlspace:
		{
			org.exist.xquery.parser.XQueryAST tmp356_AST = null;
			tmp356_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp356_AST);
			match(LITERAL_xmlspace);
			if ( inputState.guessing==0 ) {
				name = "xmlspace";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_preserve:
		{
			org.exist.xquery.parser.XQueryAST tmp357_AST = null;
			tmp357_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp357_AST);
			match(LITERAL_preserve);
			if ( inputState.guessing==0 ) {
				name = "preserve";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
			break;
		}
		case LITERAL_strip:
		{
			org.exist.xquery.parser.XQueryAST tmp358_AST = null;
			tmp358_AST = (org.exist.xquery.parser.XQueryAST)astFactory.create(LT(1));
			astFactory.addASTChild(currentAST, tmp358_AST);
			match(LITERAL_strip);
			if ( inputState.guessing==0 ) {
				name = "strip";
			}
			reservedKeywords_AST = (org.exist.xquery.parser.XQueryAST)currentAST.root;
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
		"DEF_COLLATION_DECL",
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
		"MODULE_DECL",
		"ATTRIBUTE_TEST",
		"COMP_ELEM_CONSTRUCTOR",
		"COMP_ATTR_CONSTRUCTOR",
		"COMP_TEXT_CONSTRUCTOR",
		"COMP_COMMENT_CONSTRUCTOR",
		"COMP_PI_CONSTRUCTOR",
		"COMP_NS_CONSTRUCTOR",
		"COMP_DOC_CONSTRUCTOR",
		"\"xpointer\"",
		"'('",
		"')'",
		"NCNAME",
		"\"xquery\"",
		"\"version\"",
		"SEMICOLON",
		"\"module\"",
		"\"namespace\"",
		"EQ",
		"STRING_LITERAL",
		"\"import\"",
		"\"declare\"",
		"\"default\"",
		"\"xmlspace\"",
		"\"function\"",
		"\"variable\"",
		"\"collation\"",
		"\"element\"",
		"\"preserve\"",
		"\"strip\"",
		"DOLLAR",
		"LCURLY",
		"RCURLY",
		"\"at\"",
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
		"\"instance\"",
		"\"of\"",
		"\"castable\"",
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
		"\"attribute\"",
		"\"comment\"",
		"\"processing-instruction\"",
		"\"document-node\"",
		"\"document\"",
		"SELF",
		"XML_COMMENT",
		"XML_PI",
		"LPPAREN",
		"RPPAREN",
		"AT",
		"PARENT",
		"\"child\"",
		"\"self\"",
		"\"descendant\"",
		"\"descendant-or-self\"",
		"\"following-sibling\"",
		"\"following\"",
		"\"parent\"",
		"\"ancestor\"",
		"\"ancestor-or-self\"",
		"\"preceding-sibling\"",
		"DOUBLE_LITERAL",
		"DECIMAL_LITERAL",
		"INTEGER_LITERAL",
		"END_TAG_START",
		"QUOT",
		"APOS",
		"ATTRIBUTE_CONTENT",
		"ELEMENT_CONTENT",
		"XML_COMMENT_END",
		"XML_PI_END",
		"\"collection\"",
		"\"preceding\"",
		"XML_PI_START",
		"LETTER",
		"DIGITS",
		"HEX_DIGITS",
		"NMSTART",
		"NMCHAR",
		"WS",
		"EXPR_COMMENT",
		"PRAGMA",
		"PRAGMA_CONTENT",
		"PRAGMA_QNAME",
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
		long[] data = { -20477304555700224L, -327918107873247897L, 25836911103L, 0L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_0 = new BitSet(mk_tokenSet_0());
	private static final long[] mk_tokenSet_1() {
		long[] data = { -56576470318841856L, -7254454472208027293L, 25778184247L, 0L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_1 = new BitSet(mk_tokenSet_1());
	private static final long[] mk_tokenSet_2() {
		long[] data = { -56576470318841856L, -7254454472208027293L, 25778184255L, 0L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_2 = new BitSet(mk_tokenSet_2());
	private static final long[] mk_tokenSet_3() {
		long[] data = { -9223372036854775808L, -9223372036854775808L, 31L, 0L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_3 = new BitSet(mk_tokenSet_3());
	private static final long[] mk_tokenSet_4() {
		long[] data = { 18014398509481984L, 246702921482240L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_4 = new BitSet(mk_tokenSet_4());
	private static final long[] mk_tokenSet_5() {
		long[] data = { 0L, 126100789566374912L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_5 = new BitSet(mk_tokenSet_5());
	private static final long[] mk_tokenSet_6() {
		long[] data = { -56576470318841856L, -7254454472208026269L, 25778190399L, 0L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_6 = new BitSet(mk_tokenSet_6());
	private static final long[] mk_tokenSet_7() {
		long[] data = { -20477304555700224L, -7254454334769073817L, 25836904959L, 0L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_7 = new BitSet(mk_tokenSet_7());
	private static final long[] mk_tokenSet_8() {
		long[] data = { 0L, 0L, 516098L, 0L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_8 = new BitSet(mk_tokenSet_8());
	private static final long[] mk_tokenSet_9() {
		long[] data = { -56576470318841856L, -7254454472208026269L, 25778184247L, 0L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_9 = new BitSet(mk_tokenSet_9());
	private static final long[] mk_tokenSet_10() {
		long[] data = { -9223372036854775808L, -9223372036854775808L, 46L, 0L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_10 = new BitSet(mk_tokenSet_10());
	private static final long[] mk_tokenSet_11() {
		long[] data = new long[8];
		data[0]=-16L;
		data[1]=-2305843284091600897L;
		data[2]=36028797018963967L;
		return data;
	}
	public static final BitSet _tokenSet_11 = new BitSet(mk_tokenSet_11());
	private static final long[] mk_tokenSet_12() {
		long[] data = { 0L, 137438953480L, 1073742208L, 0L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_12 = new BitSet(mk_tokenSet_12());
	
	}
