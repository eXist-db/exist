// $ANTLR 2.7.4: "XQuery.g" -> "XQueryTreeParser.java"$

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

import antlr.TreeParser;
import antlr.Token;
import antlr.collections.AST;
import antlr.RecognitionException;
import antlr.ANTLRException;
import antlr.NoViableAltException;
import antlr.MismatchedTokenException;
import antlr.SemanticException;
import antlr.collections.impl.BitSet;
import antlr.ASTPair;
import antlr.collections.impl.ASTArray;


/**
 * The tree parser: walks the AST created by the parser to generate
 * XQuery expression objects.
 */
public class XQueryTreeParser extends antlr.TreeParser       implements XQueryParserTokenTypes
 {

	private XQueryContext context;
	private ExternalModule myModule = null;
	protected ArrayList exceptions= new ArrayList(2);
	protected boolean foundError= false;

	public XQueryTreeParser(XQueryContext context) {
		this();
		this.context= context;
	}

	public ExternalModule getModule() {
		return myModule;
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

	public Exception getLastException() {
		return (Exception) exceptions.get(exceptions.size() - 1);
	}

	protected void handleException(Exception e) {
		foundError= true;
		exceptions.add(e);
	}

	private static class ForLetClause {
		String varName;
		SequenceType sequenceType= null;
		String posVar= null;
		Expression inputSequence;
		Expression action;
		boolean isForClause= true;
	}

	private static class FunctionParameter {
		String varName;
		SequenceType type= FunctionSignature.DEFAULT_TYPE;

		public FunctionParameter(String name) {
			this.varName= name;
		}
	}
public XQueryTreeParser() {
	tokenNames = _tokenNames;
}

	public final void xpointer(AST _t,
		PathExpr path
	) throws RecognitionException {
		
		org.exist.xquery.parser.XQueryAST xpointer_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		org.exist.xquery.parser.XQueryAST nc = null;
		Expression step = null;
		
		try {      // for error handling
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case XPOINTER:
			{
				AST __t278 = _t;
				org.exist.xquery.parser.XQueryAST tmp1_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,XPOINTER);
				_t = _t.getFirstChild();
				step=expr(_t,path);
				_t = _retTree;
				_t = __t278;
				_t = _t.getNextSibling();
				break;
			}
			case XPOINTER_ID:
			{
				AST __t279 = _t;
				org.exist.xquery.parser.XQueryAST tmp2_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,XPOINTER_ID);
				_t = _t.getFirstChild();
				nc = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,NCNAME);
				_t = _t.getNextSibling();
				_t = __t279;
				_t = _t.getNextSibling();
				
						Function fun= new FunId(context);
						List params= new ArrayList(1);
						params.add(new LiteralValue(context, new StringValue(nc.getText())));
						fun.setArguments(params);
						path.addPath(fun);
					
				break;
			}
			default:
			{
				throw new NoViableAltException(_t);
			}
			}
		}
		catch (RecognitionException e) {
			handleException(e);
		}
		catch (EXistException e) {
			handleException(e);
		}
		catch (PermissionDeniedException e) {
			handleException(e);
		}
		catch (XPathException e) {
			handleException(e);
		}
		_retTree = _t;
	}
	
	public final Expression  expr(AST _t,
		PathExpr path
	) throws RecognitionException, PermissionDeniedException,EXistException,XPathException {
		Expression step;
		
		org.exist.xquery.parser.XQueryAST expr_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		org.exist.xquery.parser.XQueryAST castAST = null;
		org.exist.xquery.parser.XQueryAST t = null;
		org.exist.xquery.parser.XQueryAST someVarName = null;
		org.exist.xquery.parser.XQueryAST everyVarName = null;
		org.exist.xquery.parser.XQueryAST varName = null;
		org.exist.xquery.parser.XQueryAST posVar = null;
		org.exist.xquery.parser.XQueryAST letVarName = null;
		
			step= null;
		
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case LITERAL_cast:
		{
			AST __t324 = _t;
			castAST = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_cast);
			_t = _t.getFirstChild();
			
						PathExpr expr= new PathExpr(context);
						int cardinality= Cardinality.EXACTLY_ONE;
					
			step=expr(_t,expr);
			_t = _retTree;
			t = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,ATOMIC_TYPE);
			_t = _t.getNextSibling();
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case QUESTION:
			{
				org.exist.xquery.parser.XQueryAST tmp3_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,QUESTION);
				_t = _t.getNextSibling();
				cardinality= Cardinality.ZERO_OR_ONE;
				break;
			}
			case 3:
			{
				break;
			}
			default:
			{
				throw new NoViableAltException(_t);
			}
			}
			}
			
						QName qn= QName.parse(context, t.getText());
						int code= Type.getType(qn);
						CastExpression castExpr= new CastExpression(context, expr, code, cardinality);
			castExpr.setASTNode(castAST);
						path.add(castExpr);
						step = castExpr;
					
			_t = __t324;
			_t = _t.getNextSibling();
			break;
		}
		case COMMA:
		{
			AST __t326 = _t;
			org.exist.xquery.parser.XQueryAST tmp4_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,COMMA);
			_t = _t.getFirstChild();
			
						PathExpr left= new PathExpr(context);
						PathExpr right= new PathExpr(context);
					
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						SequenceConstructor sc= new SequenceConstructor(context);
						sc.addPath(left);
						sc.addPath(right);
						path.addPath(sc);
						step = sc;
					
			_t = __t326;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_if:
		{
			AST __t327 = _t;
			org.exist.xquery.parser.XQueryAST tmp5_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_if);
			_t = _t.getFirstChild();
			
						PathExpr testExpr= new PathExpr(context);
						PathExpr thenExpr= new PathExpr(context);
						PathExpr elseExpr= new PathExpr(context);
					
			step=expr(_t,testExpr);
			_t = _retTree;
			step=expr(_t,thenExpr);
			_t = _retTree;
			step=expr(_t,elseExpr);
			_t = _retTree;
			
						ConditionalExpression cond= new ConditionalExpression(context, testExpr, thenExpr, elseExpr);
						path.add(cond);
						step = cond;
					
			_t = __t327;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_some:
		{
			AST __t328 = _t;
			org.exist.xquery.parser.XQueryAST tmp6_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_some);
			_t = _t.getFirstChild();
			
						List clauses= new ArrayList();
						PathExpr satisfiesExpr = new PathExpr(context);
					
			{
			_loop333:
			do {
				if (_t==null) _t=ASTNULL;
				if ((_t.getType()==VARIABLE_BINDING)) {
					AST __t330 = _t;
					someVarName = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
					match(_t,VARIABLE_BINDING);
					_t = _t.getFirstChild();
					
										ForLetClause clause= new ForLetClause();
										PathExpr inputSequence = new PathExpr(context);
									
					{
					if (_t==null) _t=ASTNULL;
					switch ( _t.getType()) {
					case LITERAL_as:
					{
						AST __t332 = _t;
						org.exist.xquery.parser.XQueryAST tmp7_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
						match(_t,LITERAL_as);
						_t = _t.getFirstChild();
						sequenceType(_t,clause.sequenceType);
						_t = _retTree;
						_t = __t332;
						_t = _t.getNextSibling();
						break;
					}
					case QNAME:
					case PARENTHESIZED:
					case ABSOLUTE_SLASH:
					case ABSOLUTE_DSLASH:
					case WILDCARD:
					case PREFIX_WILDCARD:
					case FUNCTION:
					case UNARY_MINUS:
					case UNARY_PLUS:
					case VARIABLE_REF:
					case ELEMENT:
					case TEXT:
					case BEFORE:
					case AFTER:
					case ATTRIBUTE_TEST:
					case COMP_ELEM_CONSTRUCTOR:
					case COMP_ATTR_CONSTRUCTOR:
					case COMP_TEXT_CONSTRUCTOR:
					case COMP_COMMENT_CONSTRUCTOR:
					case COMP_PI_CONSTRUCTOR:
					case NCNAME:
					case EQ:
					case STRING_LITERAL:
					case LITERAL_element:
					case LCURLY:
					case COMMA:
					case STAR:
					case PLUS:
					case LITERAL_some:
					case LITERAL_every:
					case LITERAL_if:
					case LITERAL_return:
					case LITERAL_or:
					case LITERAL_and:
					case LITERAL_instance:
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
					case UNION:
					case LITERAL_intersect:
					case LITERAL_except:
					case SLASH:
					case DSLASH:
					case LITERAL_text:
					case LITERAL_node:
					case LITERAL_attribute:
					case LITERAL_comment:
					case 125:
					case SELF:
					case XML_COMMENT:
					case XML_PI:
					case AT:
					case PARENT:
					case LITERAL_child:
					case LITERAL_self:
					case LITERAL_descendant:
					case 137:
					case 138:
					case LITERAL_following:
					case LITERAL_parent:
					case LITERAL_ancestor:
					case 142:
					case 143:
					case DOUBLE_LITERAL:
					case DECIMAL_LITERAL:
					case INTEGER_LITERAL:
					case LITERAL_preceding:
					case COMP_DOC_CONSTRUCTOR:
					{
						break;
					}
					default:
					{
						throw new NoViableAltException(_t);
					}
					}
					}
					step=expr(_t,inputSequence);
					_t = _retTree;
					
										clause.varName= someVarName.getText();
										clause.inputSequence= inputSequence;
										clauses.add(clause);
									
					_t = __t330;
					_t = _t.getNextSibling();
				}
				else {
					break _loop333;
				}
				
			} while (true);
			}
			step=expr(_t,satisfiesExpr);
			_t = _retTree;
			
						Expression action = satisfiesExpr;
						for (int i= clauses.size() - 1; i >= 0; i--) {
							ForLetClause clause= (ForLetClause) clauses.get(i);
							BindingExpression expr = new QuantifiedExpression(context, QuantifiedExpression.SOME);
							expr.setVariable(clause.varName);
							expr.setSequenceType(clause.sequenceType);
							expr.setInputSequence(clause.inputSequence);
							expr.setReturnExpression(action);
							satisfiesExpr= null;
							action= expr;
						}
						path.add(action);
						step = action;
					
			_t = __t328;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_every:
		{
			AST __t334 = _t;
			org.exist.xquery.parser.XQueryAST tmp8_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_every);
			_t = _t.getFirstChild();
			
						List clauses= new ArrayList();
						PathExpr satisfiesExpr = new PathExpr(context);
					
			{
			_loop339:
			do {
				if (_t==null) _t=ASTNULL;
				if ((_t.getType()==VARIABLE_BINDING)) {
					AST __t336 = _t;
					everyVarName = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
					match(_t,VARIABLE_BINDING);
					_t = _t.getFirstChild();
					
										ForLetClause clause= new ForLetClause();
										PathExpr inputSequence = new PathExpr(context);
									
					{
					if (_t==null) _t=ASTNULL;
					switch ( _t.getType()) {
					case LITERAL_as:
					{
						AST __t338 = _t;
						org.exist.xquery.parser.XQueryAST tmp9_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
						match(_t,LITERAL_as);
						_t = _t.getFirstChild();
						sequenceType(_t,clause.sequenceType);
						_t = _retTree;
						_t = __t338;
						_t = _t.getNextSibling();
						break;
					}
					case QNAME:
					case PARENTHESIZED:
					case ABSOLUTE_SLASH:
					case ABSOLUTE_DSLASH:
					case WILDCARD:
					case PREFIX_WILDCARD:
					case FUNCTION:
					case UNARY_MINUS:
					case UNARY_PLUS:
					case VARIABLE_REF:
					case ELEMENT:
					case TEXT:
					case BEFORE:
					case AFTER:
					case ATTRIBUTE_TEST:
					case COMP_ELEM_CONSTRUCTOR:
					case COMP_ATTR_CONSTRUCTOR:
					case COMP_TEXT_CONSTRUCTOR:
					case COMP_COMMENT_CONSTRUCTOR:
					case COMP_PI_CONSTRUCTOR:
					case NCNAME:
					case EQ:
					case STRING_LITERAL:
					case LITERAL_element:
					case LCURLY:
					case COMMA:
					case STAR:
					case PLUS:
					case LITERAL_some:
					case LITERAL_every:
					case LITERAL_if:
					case LITERAL_return:
					case LITERAL_or:
					case LITERAL_and:
					case LITERAL_instance:
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
					case UNION:
					case LITERAL_intersect:
					case LITERAL_except:
					case SLASH:
					case DSLASH:
					case LITERAL_text:
					case LITERAL_node:
					case LITERAL_attribute:
					case LITERAL_comment:
					case 125:
					case SELF:
					case XML_COMMENT:
					case XML_PI:
					case AT:
					case PARENT:
					case LITERAL_child:
					case LITERAL_self:
					case LITERAL_descendant:
					case 137:
					case 138:
					case LITERAL_following:
					case LITERAL_parent:
					case LITERAL_ancestor:
					case 142:
					case 143:
					case DOUBLE_LITERAL:
					case DECIMAL_LITERAL:
					case INTEGER_LITERAL:
					case LITERAL_preceding:
					case COMP_DOC_CONSTRUCTOR:
					{
						break;
					}
					default:
					{
						throw new NoViableAltException(_t);
					}
					}
					}
					step=expr(_t,inputSequence);
					_t = _retTree;
					
										clause.varName= everyVarName.getText();
										clause.inputSequence= inputSequence;
										clauses.add(clause);
									
					_t = __t336;
					_t = _t.getNextSibling();
				}
				else {
					break _loop339;
				}
				
			} while (true);
			}
			step=expr(_t,satisfiesExpr);
			_t = _retTree;
			
						Expression action = satisfiesExpr;
						for (int i= clauses.size() - 1; i >= 0; i--) {
							ForLetClause clause= (ForLetClause) clauses.get(i);
							BindingExpression expr = new QuantifiedExpression(context, QuantifiedExpression.EVERY);
							expr.setVariable(clause.varName);
							expr.setSequenceType(clause.sequenceType);
							expr.setInputSequence(clause.inputSequence);
							expr.setReturnExpression(action);
							satisfiesExpr= null;
							action= expr;
						}
						path.add(action);
						step = action;
					
			_t = __t334;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_return:
		{
			AST __t340 = _t;
			org.exist.xquery.parser.XQueryAST tmp10_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_return);
			_t = _t.getFirstChild();
			
						List clauses= new ArrayList();
						Expression action= new PathExpr(context);
						PathExpr whereExpr= null;
						List orderBy= null;
					
			{
			int _cnt355=0;
			_loop355:
			do {
				if (_t==null) _t=ASTNULL;
				switch ( _t.getType()) {
				case LITERAL_for:
				{
					AST __t342 = _t;
					org.exist.xquery.parser.XQueryAST tmp11_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
					match(_t,LITERAL_for);
					_t = _t.getFirstChild();
					{
					int _cnt348=0;
					_loop348:
					do {
						if (_t==null) _t=ASTNULL;
						if ((_t.getType()==VARIABLE_BINDING)) {
							AST __t344 = _t;
							varName = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
							match(_t,VARIABLE_BINDING);
							_t = _t.getFirstChild();
							
														ForLetClause clause= new ForLetClause();
														PathExpr inputSequence= new PathExpr(context);
													
							{
							if (_t==null) _t=ASTNULL;
							switch ( _t.getType()) {
							case LITERAL_as:
							{
								AST __t346 = _t;
								org.exist.xquery.parser.XQueryAST tmp12_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
								match(_t,LITERAL_as);
								_t = _t.getFirstChild();
								clause.sequenceType= new SequenceType();
								sequenceType(_t,clause.sequenceType);
								_t = _retTree;
								_t = __t346;
								_t = _t.getNextSibling();
								break;
							}
							case QNAME:
							case PARENTHESIZED:
							case ABSOLUTE_SLASH:
							case ABSOLUTE_DSLASH:
							case WILDCARD:
							case PREFIX_WILDCARD:
							case FUNCTION:
							case UNARY_MINUS:
							case UNARY_PLUS:
							case VARIABLE_REF:
							case ELEMENT:
							case TEXT:
							case POSITIONAL_VAR:
							case BEFORE:
							case AFTER:
							case ATTRIBUTE_TEST:
							case COMP_ELEM_CONSTRUCTOR:
							case COMP_ATTR_CONSTRUCTOR:
							case COMP_TEXT_CONSTRUCTOR:
							case COMP_COMMENT_CONSTRUCTOR:
							case COMP_PI_CONSTRUCTOR:
							case NCNAME:
							case EQ:
							case STRING_LITERAL:
							case LITERAL_element:
							case LCURLY:
							case COMMA:
							case STAR:
							case PLUS:
							case LITERAL_some:
							case LITERAL_every:
							case LITERAL_if:
							case LITERAL_return:
							case LITERAL_or:
							case LITERAL_and:
							case LITERAL_instance:
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
							case UNION:
							case LITERAL_intersect:
							case LITERAL_except:
							case SLASH:
							case DSLASH:
							case LITERAL_text:
							case LITERAL_node:
							case LITERAL_attribute:
							case LITERAL_comment:
							case 125:
							case SELF:
							case XML_COMMENT:
							case XML_PI:
							case AT:
							case PARENT:
							case LITERAL_child:
							case LITERAL_self:
							case LITERAL_descendant:
							case 137:
							case 138:
							case LITERAL_following:
							case LITERAL_parent:
							case LITERAL_ancestor:
							case 142:
							case 143:
							case DOUBLE_LITERAL:
							case DECIMAL_LITERAL:
							case INTEGER_LITERAL:
							case LITERAL_preceding:
							case COMP_DOC_CONSTRUCTOR:
							{
								break;
							}
							default:
							{
								throw new NoViableAltException(_t);
							}
							}
							}
							{
							if (_t==null) _t=ASTNULL;
							switch ( _t.getType()) {
							case POSITIONAL_VAR:
							{
								posVar = (org.exist.xquery.parser.XQueryAST)_t;
								match(_t,POSITIONAL_VAR);
								_t = _t.getNextSibling();
								clause.posVar= posVar.getText();
								break;
							}
							case QNAME:
							case PARENTHESIZED:
							case ABSOLUTE_SLASH:
							case ABSOLUTE_DSLASH:
							case WILDCARD:
							case PREFIX_WILDCARD:
							case FUNCTION:
							case UNARY_MINUS:
							case UNARY_PLUS:
							case VARIABLE_REF:
							case ELEMENT:
							case TEXT:
							case BEFORE:
							case AFTER:
							case ATTRIBUTE_TEST:
							case COMP_ELEM_CONSTRUCTOR:
							case COMP_ATTR_CONSTRUCTOR:
							case COMP_TEXT_CONSTRUCTOR:
							case COMP_COMMENT_CONSTRUCTOR:
							case COMP_PI_CONSTRUCTOR:
							case NCNAME:
							case EQ:
							case STRING_LITERAL:
							case LITERAL_element:
							case LCURLY:
							case COMMA:
							case STAR:
							case PLUS:
							case LITERAL_some:
							case LITERAL_every:
							case LITERAL_if:
							case LITERAL_return:
							case LITERAL_or:
							case LITERAL_and:
							case LITERAL_instance:
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
							case UNION:
							case LITERAL_intersect:
							case LITERAL_except:
							case SLASH:
							case DSLASH:
							case LITERAL_text:
							case LITERAL_node:
							case LITERAL_attribute:
							case LITERAL_comment:
							case 125:
							case SELF:
							case XML_COMMENT:
							case XML_PI:
							case AT:
							case PARENT:
							case LITERAL_child:
							case LITERAL_self:
							case LITERAL_descendant:
							case 137:
							case 138:
							case LITERAL_following:
							case LITERAL_parent:
							case LITERAL_ancestor:
							case 142:
							case 143:
							case DOUBLE_LITERAL:
							case DECIMAL_LITERAL:
							case INTEGER_LITERAL:
							case LITERAL_preceding:
							case COMP_DOC_CONSTRUCTOR:
							{
								break;
							}
							default:
							{
								throw new NoViableAltException(_t);
							}
							}
							}
							step=expr(_t,inputSequence);
							_t = _retTree;
							
														clause.varName= varName.getText();
														clause.inputSequence= inputSequence;
														clauses.add(clause);
													
							_t = __t344;
							_t = _t.getNextSibling();
						}
						else {
							if ( _cnt348>=1 ) { break _loop348; } else {throw new NoViableAltException(_t);}
						}
						
						_cnt348++;
					} while (true);
					}
					_t = __t342;
					_t = _t.getNextSibling();
					break;
				}
				case LITERAL_let:
				{
					AST __t349 = _t;
					org.exist.xquery.parser.XQueryAST tmp13_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
					match(_t,LITERAL_let);
					_t = _t.getFirstChild();
					{
					int _cnt354=0;
					_loop354:
					do {
						if (_t==null) _t=ASTNULL;
						if ((_t.getType()==VARIABLE_BINDING)) {
							AST __t351 = _t;
							letVarName = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
							match(_t,VARIABLE_BINDING);
							_t = _t.getFirstChild();
							
														ForLetClause clause= new ForLetClause();
														clause.isForClause= false;
														PathExpr inputSequence= new PathExpr(context);
													
							{
							if (_t==null) _t=ASTNULL;
							switch ( _t.getType()) {
							case LITERAL_as:
							{
								AST __t353 = _t;
								org.exist.xquery.parser.XQueryAST tmp14_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
								match(_t,LITERAL_as);
								_t = _t.getFirstChild();
								clause.sequenceType= new SequenceType();
								sequenceType(_t,clause.sequenceType);
								_t = _retTree;
								_t = __t353;
								_t = _t.getNextSibling();
								break;
							}
							case QNAME:
							case PARENTHESIZED:
							case ABSOLUTE_SLASH:
							case ABSOLUTE_DSLASH:
							case WILDCARD:
							case PREFIX_WILDCARD:
							case FUNCTION:
							case UNARY_MINUS:
							case UNARY_PLUS:
							case VARIABLE_REF:
							case ELEMENT:
							case TEXT:
							case BEFORE:
							case AFTER:
							case ATTRIBUTE_TEST:
							case COMP_ELEM_CONSTRUCTOR:
							case COMP_ATTR_CONSTRUCTOR:
							case COMP_TEXT_CONSTRUCTOR:
							case COMP_COMMENT_CONSTRUCTOR:
							case COMP_PI_CONSTRUCTOR:
							case NCNAME:
							case EQ:
							case STRING_LITERAL:
							case LITERAL_element:
							case LCURLY:
							case COMMA:
							case STAR:
							case PLUS:
							case LITERAL_some:
							case LITERAL_every:
							case LITERAL_if:
							case LITERAL_return:
							case LITERAL_or:
							case LITERAL_and:
							case LITERAL_instance:
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
							case UNION:
							case LITERAL_intersect:
							case LITERAL_except:
							case SLASH:
							case DSLASH:
							case LITERAL_text:
							case LITERAL_node:
							case LITERAL_attribute:
							case LITERAL_comment:
							case 125:
							case SELF:
							case XML_COMMENT:
							case XML_PI:
							case AT:
							case PARENT:
							case LITERAL_child:
							case LITERAL_self:
							case LITERAL_descendant:
							case 137:
							case 138:
							case LITERAL_following:
							case LITERAL_parent:
							case LITERAL_ancestor:
							case 142:
							case 143:
							case DOUBLE_LITERAL:
							case DECIMAL_LITERAL:
							case INTEGER_LITERAL:
							case LITERAL_preceding:
							case COMP_DOC_CONSTRUCTOR:
							{
								break;
							}
							default:
							{
								throw new NoViableAltException(_t);
							}
							}
							}
							step=expr(_t,inputSequence);
							_t = _retTree;
							
														clause.varName= letVarName.getText();
														clause.inputSequence= inputSequence;
														clauses.add(clause);
													
							_t = __t351;
							_t = _t.getNextSibling();
						}
						else {
							if ( _cnt354>=1 ) { break _loop354; } else {throw new NoViableAltException(_t);}
						}
						
						_cnt354++;
					} while (true);
					}
					_t = __t349;
					_t = _t.getNextSibling();
					break;
				}
				default:
				{
					if ( _cnt355>=1 ) { break _loop355; } else {throw new NoViableAltException(_t);}
				}
				}
				_cnt355++;
			} while (true);
			}
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case LITERAL_where:
			{
				org.exist.xquery.parser.XQueryAST tmp15_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,LITERAL_where);
				_t = _t.getNextSibling();
				whereExpr= new PathExpr(context);
				step=expr(_t,whereExpr);
				_t = _retTree;
				break;
			}
			case QNAME:
			case PARENTHESIZED:
			case ABSOLUTE_SLASH:
			case ABSOLUTE_DSLASH:
			case WILDCARD:
			case PREFIX_WILDCARD:
			case FUNCTION:
			case UNARY_MINUS:
			case UNARY_PLUS:
			case VARIABLE_REF:
			case ELEMENT:
			case TEXT:
			case ORDER_BY:
			case BEFORE:
			case AFTER:
			case ATTRIBUTE_TEST:
			case COMP_ELEM_CONSTRUCTOR:
			case COMP_ATTR_CONSTRUCTOR:
			case COMP_TEXT_CONSTRUCTOR:
			case COMP_COMMENT_CONSTRUCTOR:
			case COMP_PI_CONSTRUCTOR:
			case NCNAME:
			case EQ:
			case STRING_LITERAL:
			case LITERAL_element:
			case LCURLY:
			case COMMA:
			case STAR:
			case PLUS:
			case LITERAL_some:
			case LITERAL_every:
			case LITERAL_if:
			case LITERAL_return:
			case LITERAL_or:
			case LITERAL_and:
			case LITERAL_instance:
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
			case UNION:
			case LITERAL_intersect:
			case LITERAL_except:
			case SLASH:
			case DSLASH:
			case LITERAL_text:
			case LITERAL_node:
			case LITERAL_attribute:
			case LITERAL_comment:
			case 125:
			case SELF:
			case XML_COMMENT:
			case XML_PI:
			case AT:
			case PARENT:
			case LITERAL_child:
			case LITERAL_self:
			case LITERAL_descendant:
			case 137:
			case 138:
			case LITERAL_following:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 142:
			case 143:
			case DOUBLE_LITERAL:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
			case LITERAL_preceding:
			case COMP_DOC_CONSTRUCTOR:
			{
				break;
			}
			default:
			{
				throw new NoViableAltException(_t);
			}
			}
			}
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case ORDER_BY:
			{
				AST __t358 = _t;
				org.exist.xquery.parser.XQueryAST tmp16_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,ORDER_BY);
				_t = _t.getFirstChild();
				orderBy= new ArrayList(3);
				{
				int _cnt364=0;
				_loop364:
				do {
					if (_t==null) _t=ASTNULL;
					if ((_tokenSet_0.member(_t.getType()))) {
						PathExpr orderSpecExpr= new PathExpr(context);
						step=expr(_t,orderSpecExpr);
						_t = _retTree;
						
												OrderSpec orderSpec= new OrderSpec(orderSpecExpr);
												int modifiers= 0;
												orderBy.add(orderSpec);
											
						{
						if (_t==null) _t=ASTNULL;
						switch ( _t.getType()) {
						case LITERAL_ascending:
						case LITERAL_descending:
						{
							{
							if (_t==null) _t=ASTNULL;
							switch ( _t.getType()) {
							case LITERAL_ascending:
							{
								org.exist.xquery.parser.XQueryAST tmp17_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
								match(_t,LITERAL_ascending);
								_t = _t.getNextSibling();
								break;
							}
							case LITERAL_descending:
							{
								org.exist.xquery.parser.XQueryAST tmp18_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
								match(_t,LITERAL_descending);
								_t = _t.getNextSibling();
								
																modifiers= OrderSpec.DESCENDING_ORDER;
																orderSpec.setModifiers(modifiers);
															
								break;
							}
							default:
							{
								throw new NoViableAltException(_t);
							}
							}
							}
							break;
						}
						case 3:
						case QNAME:
						case PARENTHESIZED:
						case ABSOLUTE_SLASH:
						case ABSOLUTE_DSLASH:
						case WILDCARD:
						case PREFIX_WILDCARD:
						case FUNCTION:
						case UNARY_MINUS:
						case UNARY_PLUS:
						case VARIABLE_REF:
						case ELEMENT:
						case TEXT:
						case BEFORE:
						case AFTER:
						case ATTRIBUTE_TEST:
						case COMP_ELEM_CONSTRUCTOR:
						case COMP_ATTR_CONSTRUCTOR:
						case COMP_TEXT_CONSTRUCTOR:
						case COMP_COMMENT_CONSTRUCTOR:
						case COMP_PI_CONSTRUCTOR:
						case NCNAME:
						case EQ:
						case STRING_LITERAL:
						case LITERAL_element:
						case LCURLY:
						case COMMA:
						case LITERAL_empty:
						case STAR:
						case PLUS:
						case LITERAL_some:
						case LITERAL_every:
						case LITERAL_if:
						case LITERAL_return:
						case LITERAL_or:
						case LITERAL_and:
						case LITERAL_instance:
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
						case UNION:
						case LITERAL_intersect:
						case LITERAL_except:
						case SLASH:
						case DSLASH:
						case LITERAL_text:
						case LITERAL_node:
						case LITERAL_attribute:
						case LITERAL_comment:
						case 125:
						case SELF:
						case XML_COMMENT:
						case XML_PI:
						case AT:
						case PARENT:
						case LITERAL_child:
						case LITERAL_self:
						case LITERAL_descendant:
						case 137:
						case 138:
						case LITERAL_following:
						case LITERAL_parent:
						case LITERAL_ancestor:
						case 142:
						case 143:
						case DOUBLE_LITERAL:
						case DECIMAL_LITERAL:
						case INTEGER_LITERAL:
						case LITERAL_preceding:
						case COMP_DOC_CONSTRUCTOR:
						{
							break;
						}
						default:
						{
							throw new NoViableAltException(_t);
						}
						}
						}
						{
						if (_t==null) _t=ASTNULL;
						switch ( _t.getType()) {
						case LITERAL_empty:
						{
							org.exist.xquery.parser.XQueryAST tmp19_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
							match(_t,LITERAL_empty);
							_t = _t.getNextSibling();
							{
							if (_t==null) _t=ASTNULL;
							switch ( _t.getType()) {
							case LITERAL_greatest:
							{
								org.exist.xquery.parser.XQueryAST tmp20_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
								match(_t,LITERAL_greatest);
								_t = _t.getNextSibling();
								break;
							}
							case LITERAL_least:
							{
								org.exist.xquery.parser.XQueryAST tmp21_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
								match(_t,LITERAL_least);
								_t = _t.getNextSibling();
								
																modifiers |= OrderSpec.EMPTY_LEAST;
																orderSpec.setModifiers(modifiers);
															
								break;
							}
							default:
							{
								throw new NoViableAltException(_t);
							}
							}
							}
							break;
						}
						case 3:
						case QNAME:
						case PARENTHESIZED:
						case ABSOLUTE_SLASH:
						case ABSOLUTE_DSLASH:
						case WILDCARD:
						case PREFIX_WILDCARD:
						case FUNCTION:
						case UNARY_MINUS:
						case UNARY_PLUS:
						case VARIABLE_REF:
						case ELEMENT:
						case TEXT:
						case BEFORE:
						case AFTER:
						case ATTRIBUTE_TEST:
						case COMP_ELEM_CONSTRUCTOR:
						case COMP_ATTR_CONSTRUCTOR:
						case COMP_TEXT_CONSTRUCTOR:
						case COMP_COMMENT_CONSTRUCTOR:
						case COMP_PI_CONSTRUCTOR:
						case NCNAME:
						case EQ:
						case STRING_LITERAL:
						case LITERAL_element:
						case LCURLY:
						case COMMA:
						case STAR:
						case PLUS:
						case LITERAL_some:
						case LITERAL_every:
						case LITERAL_if:
						case LITERAL_return:
						case LITERAL_or:
						case LITERAL_and:
						case LITERAL_instance:
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
						case UNION:
						case LITERAL_intersect:
						case LITERAL_except:
						case SLASH:
						case DSLASH:
						case LITERAL_text:
						case LITERAL_node:
						case LITERAL_attribute:
						case LITERAL_comment:
						case 125:
						case SELF:
						case XML_COMMENT:
						case XML_PI:
						case AT:
						case PARENT:
						case LITERAL_child:
						case LITERAL_self:
						case LITERAL_descendant:
						case 137:
						case 138:
						case LITERAL_following:
						case LITERAL_parent:
						case LITERAL_ancestor:
						case 142:
						case 143:
						case DOUBLE_LITERAL:
						case DECIMAL_LITERAL:
						case INTEGER_LITERAL:
						case LITERAL_preceding:
						case COMP_DOC_CONSTRUCTOR:
						{
							break;
						}
						default:
						{
							throw new NoViableAltException(_t);
						}
						}
						}
					}
					else {
						if ( _cnt364>=1 ) { break _loop364; } else {throw new NoViableAltException(_t);}
					}
					
					_cnt364++;
				} while (true);
				}
				_t = __t358;
				_t = _t.getNextSibling();
				break;
			}
			case QNAME:
			case PARENTHESIZED:
			case ABSOLUTE_SLASH:
			case ABSOLUTE_DSLASH:
			case WILDCARD:
			case PREFIX_WILDCARD:
			case FUNCTION:
			case UNARY_MINUS:
			case UNARY_PLUS:
			case VARIABLE_REF:
			case ELEMENT:
			case TEXT:
			case BEFORE:
			case AFTER:
			case ATTRIBUTE_TEST:
			case COMP_ELEM_CONSTRUCTOR:
			case COMP_ATTR_CONSTRUCTOR:
			case COMP_TEXT_CONSTRUCTOR:
			case COMP_COMMENT_CONSTRUCTOR:
			case COMP_PI_CONSTRUCTOR:
			case NCNAME:
			case EQ:
			case STRING_LITERAL:
			case LITERAL_element:
			case LCURLY:
			case COMMA:
			case STAR:
			case PLUS:
			case LITERAL_some:
			case LITERAL_every:
			case LITERAL_if:
			case LITERAL_return:
			case LITERAL_or:
			case LITERAL_and:
			case LITERAL_instance:
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
			case UNION:
			case LITERAL_intersect:
			case LITERAL_except:
			case SLASH:
			case DSLASH:
			case LITERAL_text:
			case LITERAL_node:
			case LITERAL_attribute:
			case LITERAL_comment:
			case 125:
			case SELF:
			case XML_COMMENT:
			case XML_PI:
			case AT:
			case PARENT:
			case LITERAL_child:
			case LITERAL_self:
			case LITERAL_descendant:
			case 137:
			case 138:
			case LITERAL_following:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 142:
			case 143:
			case DOUBLE_LITERAL:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
			case LITERAL_preceding:
			case COMP_DOC_CONSTRUCTOR:
			{
				break;
			}
			default:
			{
				throw new NoViableAltException(_t);
			}
			}
			}
			step=expr(_t,(PathExpr) action);
			_t = _retTree;
			
						for (int i= clauses.size() - 1; i >= 0; i--) {
							ForLetClause clause= (ForLetClause) clauses.get(i);
							BindingExpression expr;
							if (clause.isForClause)
								expr= new ForExpr(context);
							else
								expr= new LetExpr(context);
							expr.setVariable(clause.varName);
							expr.setSequenceType(clause.sequenceType);
							expr.setInputSequence(clause.inputSequence);
							expr.setReturnExpression(action);
							if (clause.isForClause)
								 ((ForExpr) expr).setPositionalVariable(clause.posVar);
							if (whereExpr != null) {
								expr.setWhereExpression(whereExpr);
								whereExpr= null;
							}
							action= expr;
						}
						if (orderBy != null) {
							OrderSpec orderSpecs[]= new OrderSpec[orderBy.size()];
							int k= 0;
							for (Iterator j= orderBy.iterator(); j.hasNext(); k++) {
								OrderSpec orderSpec= (OrderSpec) j.next();
								orderSpecs[k]= orderSpec;
							}
							((BindingExpression)action).setOrderSpecs(orderSpecs);
						}
						path.add(action);
						step = action;
					
			_t = __t340;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_instance:
		{
			AST __t365 = _t;
			org.exist.xquery.parser.XQueryAST tmp22_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_instance);
			_t = _t.getFirstChild();
			
						PathExpr expr = new PathExpr(context);
						SequenceType type= new SequenceType(); 
					
			step=expr(_t,expr);
			_t = _retTree;
			sequenceType(_t,type);
			_t = _retTree;
			
						step = new InstanceOfExpression(context, expr, type); 
						path.add(step);
					
			_t = __t365;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_or:
		{
			AST __t366 = _t;
			org.exist.xquery.parser.XQueryAST tmp23_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_or);
			_t = _t.getFirstChild();
			
						PathExpr left= new PathExpr(context);
						PathExpr right= new PathExpr(context);
					
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			_t = __t366;
			_t = _t.getNextSibling();
			
					OpOr or= new OpOr(context);
					or.addPath(left);
					or.addPath(right);
					path.addPath(or);
					step = or;
				
			break;
		}
		case LITERAL_and:
		{
			AST __t367 = _t;
			org.exist.xquery.parser.XQueryAST tmp24_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_and);
			_t = _t.getFirstChild();
			
						PathExpr left= new PathExpr(context);
						PathExpr right= new PathExpr(context);
					
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			_t = __t367;
			_t = _t.getNextSibling();
			
					OpAnd and= new OpAnd(context);
					and.addPath(left);
					and.addPath(right);
					path.addPath(and);
					step = and;
				
			break;
		}
		case UNION:
		{
			AST __t368 = _t;
			org.exist.xquery.parser.XQueryAST tmp25_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,UNION);
			_t = _t.getFirstChild();
			
						PathExpr left= new PathExpr(context);
						PathExpr right= new PathExpr(context);
					
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			_t = __t368;
			_t = _t.getNextSibling();
			
					Union union= new Union(context, left, right);
					path.add(union);
					step = union;
				
			break;
		}
		case LITERAL_intersect:
		{
			AST __t369 = _t;
			org.exist.xquery.parser.XQueryAST tmp26_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_intersect);
			_t = _t.getFirstChild();
			
						PathExpr left = new PathExpr(context);
						PathExpr right = new PathExpr(context);
					
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			_t = __t369;
			_t = _t.getNextSibling();
			
					Intersection intersect = new Intersection(context, left, right);
					path.add(intersect);
					step = intersect;
				
			break;
		}
		case LITERAL_except:
		{
			AST __t370 = _t;
			org.exist.xquery.parser.XQueryAST tmp27_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_except);
			_t = _t.getFirstChild();
			
						PathExpr left = new PathExpr(context);
						PathExpr right = new PathExpr(context);
					
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			_t = __t370;
			_t = _t.getNextSibling();
			
					Except intersect = new Except(context, left, right);
					path.add(intersect);
					step = intersect;
				
			break;
		}
		case ABSOLUTE_SLASH:
		{
			AST __t371 = _t;
			org.exist.xquery.parser.XQueryAST tmp28_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,ABSOLUTE_SLASH);
			_t = _t.getFirstChild();
			
						RootNode root= new RootNode(context);
						path.add(root);
					
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case QNAME:
			case PARENTHESIZED:
			case ABSOLUTE_SLASH:
			case ABSOLUTE_DSLASH:
			case WILDCARD:
			case PREFIX_WILDCARD:
			case FUNCTION:
			case UNARY_MINUS:
			case UNARY_PLUS:
			case VARIABLE_REF:
			case ELEMENT:
			case TEXT:
			case BEFORE:
			case AFTER:
			case ATTRIBUTE_TEST:
			case COMP_ELEM_CONSTRUCTOR:
			case COMP_ATTR_CONSTRUCTOR:
			case COMP_TEXT_CONSTRUCTOR:
			case COMP_COMMENT_CONSTRUCTOR:
			case COMP_PI_CONSTRUCTOR:
			case NCNAME:
			case EQ:
			case STRING_LITERAL:
			case LITERAL_element:
			case LCURLY:
			case COMMA:
			case STAR:
			case PLUS:
			case LITERAL_some:
			case LITERAL_every:
			case LITERAL_if:
			case LITERAL_return:
			case LITERAL_or:
			case LITERAL_and:
			case LITERAL_instance:
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
			case UNION:
			case LITERAL_intersect:
			case LITERAL_except:
			case SLASH:
			case DSLASH:
			case LITERAL_text:
			case LITERAL_node:
			case LITERAL_attribute:
			case LITERAL_comment:
			case 125:
			case SELF:
			case XML_COMMENT:
			case XML_PI:
			case AT:
			case PARENT:
			case LITERAL_child:
			case LITERAL_self:
			case LITERAL_descendant:
			case 137:
			case 138:
			case LITERAL_following:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 142:
			case 143:
			case DOUBLE_LITERAL:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
			case LITERAL_preceding:
			case COMP_DOC_CONSTRUCTOR:
			{
				step=expr(_t,path);
				_t = _retTree;
				break;
			}
			case 3:
			{
				break;
			}
			default:
			{
				throw new NoViableAltException(_t);
			}
			}
			}
			_t = __t371;
			_t = _t.getNextSibling();
			break;
		}
		case ABSOLUTE_DSLASH:
		{
			AST __t373 = _t;
			org.exist.xquery.parser.XQueryAST tmp29_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,ABSOLUTE_DSLASH);
			_t = _t.getFirstChild();
			
						RootNode root= new RootNode(context);
						path.add(root);
					
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case QNAME:
			case PARENTHESIZED:
			case ABSOLUTE_SLASH:
			case ABSOLUTE_DSLASH:
			case WILDCARD:
			case PREFIX_WILDCARD:
			case FUNCTION:
			case UNARY_MINUS:
			case UNARY_PLUS:
			case VARIABLE_REF:
			case ELEMENT:
			case TEXT:
			case BEFORE:
			case AFTER:
			case ATTRIBUTE_TEST:
			case COMP_ELEM_CONSTRUCTOR:
			case COMP_ATTR_CONSTRUCTOR:
			case COMP_TEXT_CONSTRUCTOR:
			case COMP_COMMENT_CONSTRUCTOR:
			case COMP_PI_CONSTRUCTOR:
			case NCNAME:
			case EQ:
			case STRING_LITERAL:
			case LITERAL_element:
			case LCURLY:
			case COMMA:
			case STAR:
			case PLUS:
			case LITERAL_some:
			case LITERAL_every:
			case LITERAL_if:
			case LITERAL_return:
			case LITERAL_or:
			case LITERAL_and:
			case LITERAL_instance:
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
			case UNION:
			case LITERAL_intersect:
			case LITERAL_except:
			case SLASH:
			case DSLASH:
			case LITERAL_text:
			case LITERAL_node:
			case LITERAL_attribute:
			case LITERAL_comment:
			case 125:
			case SELF:
			case XML_COMMENT:
			case XML_PI:
			case AT:
			case PARENT:
			case LITERAL_child:
			case LITERAL_self:
			case LITERAL_descendant:
			case 137:
			case 138:
			case LITERAL_following:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 142:
			case 143:
			case DOUBLE_LITERAL:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
			case LITERAL_preceding:
			case COMP_DOC_CONSTRUCTOR:
			{
				step=expr(_t,path);
				_t = _retTree;
				
								if (step instanceof LocationStep) {
									LocationStep s= (LocationStep) step;
									if (s.getAxis() == Constants.ATTRIBUTE_AXIS)
										// combines descendant-or-self::node()/attribute:*
										s.setAxis(Constants.DESCENDANT_ATTRIBUTE_AXIS);
									else
										s.setAxis(Constants.DESCENDANT_SELF_AXIS);
								} else
									step.setPrimaryAxis(Constants.DESCENDANT_SELF_AXIS);
							
				break;
			}
			case 3:
			{
				break;
			}
			default:
			{
				throw new NoViableAltException(_t);
			}
			}
			}
			_t = __t373;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_to:
		{
			AST __t375 = _t;
			org.exist.xquery.parser.XQueryAST tmp30_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_to);
			_t = _t.getFirstChild();
			
						PathExpr start= new PathExpr(context);
						PathExpr end= new PathExpr(context);
						List args= new ArrayList(2);
						args.add(start);
						args.add(end);
					
			step=expr(_t,start);
			_t = _retTree;
			step=expr(_t,end);
			_t = _retTree;
			
						RangeExpression range= new RangeExpression(context);
						range.setArguments(args);
						path.addPath(range);
						step = range;
					
			_t = __t375;
			_t = _t.getNextSibling();
			break;
		}
		case EQ:
		case LT:
		case GT:
		case NEQ:
		case GTEQ:
		case LTEQ:
		{
			step=generalComp(_t,path);
			_t = _retTree;
			break;
		}
		case LITERAL_eq:
		case LITERAL_ne:
		case LITERAL_lt:
		case LITERAL_le:
		case LITERAL_gt:
		case LITERAL_ge:
		{
			step=valueComp(_t,path);
			_t = _retTree;
			break;
		}
		case BEFORE:
		case AFTER:
		case LITERAL_is:
		case LITERAL_isnot:
		{
			step=nodeComp(_t,path);
			_t = _retTree;
			break;
		}
		case ANDEQ:
		case OREQ:
		{
			step=fulltextComp(_t,path);
			_t = _retTree;
			break;
		}
		case PARENTHESIZED:
		case FUNCTION:
		case VARIABLE_REF:
		case ELEMENT:
		case TEXT:
		case COMP_ELEM_CONSTRUCTOR:
		case COMP_ATTR_CONSTRUCTOR:
		case COMP_TEXT_CONSTRUCTOR:
		case COMP_COMMENT_CONSTRUCTOR:
		case COMP_PI_CONSTRUCTOR:
		case STRING_LITERAL:
		case LCURLY:
		case XML_COMMENT:
		case XML_PI:
		case DOUBLE_LITERAL:
		case DECIMAL_LITERAL:
		case INTEGER_LITERAL:
		case COMP_DOC_CONSTRUCTOR:
		{
			step=primaryExpr(_t,path);
			_t = _retTree;
			break;
		}
		case QNAME:
		case WILDCARD:
		case PREFIX_WILDCARD:
		case ATTRIBUTE_TEST:
		case NCNAME:
		case LITERAL_element:
		case SLASH:
		case DSLASH:
		case LITERAL_text:
		case LITERAL_node:
		case LITERAL_attribute:
		case LITERAL_comment:
		case 125:
		case SELF:
		case AT:
		case PARENT:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_descendant:
		case 137:
		case 138:
		case LITERAL_following:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 142:
		case 143:
		case LITERAL_preceding:
		{
			step=pathExpr(_t,path);
			_t = _retTree;
			break;
		}
		case UNARY_MINUS:
		case UNARY_PLUS:
		case STAR:
		case PLUS:
		case MINUS:
		case LITERAL_div:
		case LITERAL_idiv:
		case LITERAL_mod:
		{
			step=numericExpr(_t,path);
			_t = _retTree;
			break;
		}
		default:
		{
			throw new NoViableAltException(_t);
		}
		}
		_retTree = _t;
		return step;
	}
	
	public final void xpath(AST _t,
		PathExpr path
	) throws RecognitionException {
		
		org.exist.xquery.parser.XQueryAST xpath_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		context.setRootExpression(path);
		
		try {      // for error handling
			module(_t,path);
			_t = _retTree;
			
					context.resolveForwardReferences();
				
		}
		catch (RecognitionException e) {
			handleException(e);
		}
		catch (EXistException e) {
			handleException(e);
		}
		catch (PermissionDeniedException e) {
			handleException(e);
		}
		catch (XPathException e) {
			handleException(e);
		}
		_retTree = _t;
	}
	
	public final void module(AST _t,
		PathExpr path
	) throws RecognitionException, PermissionDeniedException,EXistException,XPathException {
		
		org.exist.xquery.parser.XQueryAST module_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		org.exist.xquery.parser.XQueryAST m = null;
		org.exist.xquery.parser.XQueryAST uri = null;
		Expression step = null;
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case MODULE_DECL:
		{
			AST __t282 = _t;
			m = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,MODULE_DECL);
			_t = _t.getFirstChild();
			uri = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,STRING_LITERAL);
			_t = _t.getNextSibling();
			
						myModule = new ExternalModuleImpl(uri.getText(), m.getText());
						context.declareNamespace(m.getText(), uri.getText());
					
			_t = __t282;
			_t = _t.getNextSibling();
			prolog(_t,path);
			_t = _retTree;
			break;
		}
		case QNAME:
		case PARENTHESIZED:
		case ABSOLUTE_SLASH:
		case ABSOLUTE_DSLASH:
		case WILDCARD:
		case PREFIX_WILDCARD:
		case FUNCTION:
		case UNARY_MINUS:
		case UNARY_PLUS:
		case VARIABLE_REF:
		case ELEMENT:
		case TEXT:
		case VERSION_DECL:
		case NAMESPACE_DECL:
		case DEF_NAMESPACE_DECL:
		case DEF_FUNCTION_NS_DECL:
		case GLOBAL_VAR:
		case FUNCTION_DECL:
		case BEFORE:
		case AFTER:
		case ATTRIBUTE_TEST:
		case COMP_ELEM_CONSTRUCTOR:
		case COMP_ATTR_CONSTRUCTOR:
		case COMP_TEXT_CONSTRUCTOR:
		case COMP_COMMENT_CONSTRUCTOR:
		case COMP_PI_CONSTRUCTOR:
		case NCNAME:
		case EQ:
		case STRING_LITERAL:
		case LITERAL_element:
		case LCURLY:
		case LITERAL_import:
		case COMMA:
		case STAR:
		case PLUS:
		case LITERAL_some:
		case LITERAL_every:
		case LITERAL_if:
		case LITERAL_return:
		case LITERAL_or:
		case LITERAL_and:
		case LITERAL_instance:
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
		case UNION:
		case LITERAL_intersect:
		case LITERAL_except:
		case SLASH:
		case DSLASH:
		case LITERAL_text:
		case LITERAL_node:
		case LITERAL_attribute:
		case LITERAL_comment:
		case 125:
		case SELF:
		case XML_COMMENT:
		case XML_PI:
		case AT:
		case PARENT:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_descendant:
		case 137:
		case 138:
		case LITERAL_following:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 142:
		case 143:
		case DOUBLE_LITERAL:
		case DECIMAL_LITERAL:
		case INTEGER_LITERAL:
		case LITERAL_preceding:
		case COMP_DOC_CONSTRUCTOR:
		{
			prolog(_t,path);
			_t = _retTree;
			step=expr(_t,path);
			_t = _retTree;
			break;
		}
		default:
		{
			throw new NoViableAltException(_t);
		}
		}
		_retTree = _t;
	}
	
	public final void prolog(AST _t,
		PathExpr path
	) throws RecognitionException, PermissionDeniedException,EXistException,XPathException {
		
		org.exist.xquery.parser.XQueryAST prolog_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		org.exist.xquery.parser.XQueryAST v = null;
		org.exist.xquery.parser.XQueryAST prefix = null;
		org.exist.xquery.parser.XQueryAST uri = null;
		org.exist.xquery.parser.XQueryAST defu = null;
		org.exist.xquery.parser.XQueryAST deff = null;
		org.exist.xquery.parser.XQueryAST qname = null;
		org.exist.xquery.parser.XQueryAST e = null;
		org.exist.xquery.parser.XQueryAST i = null;
		org.exist.xquery.parser.XQueryAST pfx = null;
		org.exist.xquery.parser.XQueryAST moduleURI = null;
		org.exist.xquery.parser.XQueryAST at = null;
		Expression step = null;
		
		{
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case VERSION_DECL:
		{
			AST __t285 = _t;
			v = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,VERSION_DECL);
			_t = _t.getFirstChild();
			
							if (!v.getText().equals("1.0"))
								throw new XPathException(v, "Wrong XQuery version: require 1.0");
						
			_t = __t285;
			_t = _t.getNextSibling();
			break;
		}
		case 3:
		case QNAME:
		case PARENTHESIZED:
		case ABSOLUTE_SLASH:
		case ABSOLUTE_DSLASH:
		case WILDCARD:
		case PREFIX_WILDCARD:
		case FUNCTION:
		case UNARY_MINUS:
		case UNARY_PLUS:
		case VARIABLE_REF:
		case ELEMENT:
		case TEXT:
		case NAMESPACE_DECL:
		case DEF_NAMESPACE_DECL:
		case DEF_FUNCTION_NS_DECL:
		case GLOBAL_VAR:
		case FUNCTION_DECL:
		case BEFORE:
		case AFTER:
		case ATTRIBUTE_TEST:
		case COMP_ELEM_CONSTRUCTOR:
		case COMP_ATTR_CONSTRUCTOR:
		case COMP_TEXT_CONSTRUCTOR:
		case COMP_COMMENT_CONSTRUCTOR:
		case COMP_PI_CONSTRUCTOR:
		case NCNAME:
		case EQ:
		case STRING_LITERAL:
		case LITERAL_element:
		case LCURLY:
		case LITERAL_import:
		case COMMA:
		case STAR:
		case PLUS:
		case LITERAL_some:
		case LITERAL_every:
		case LITERAL_if:
		case LITERAL_return:
		case LITERAL_or:
		case LITERAL_and:
		case LITERAL_instance:
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
		case UNION:
		case LITERAL_intersect:
		case LITERAL_except:
		case SLASH:
		case DSLASH:
		case LITERAL_text:
		case LITERAL_node:
		case LITERAL_attribute:
		case LITERAL_comment:
		case 125:
		case SELF:
		case XML_COMMENT:
		case XML_PI:
		case AT:
		case PARENT:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_descendant:
		case 137:
		case 138:
		case LITERAL_following:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 142:
		case 143:
		case DOUBLE_LITERAL:
		case DECIMAL_LITERAL:
		case INTEGER_LITERAL:
		case LITERAL_preceding:
		case COMP_DOC_CONSTRUCTOR:
		{
			break;
		}
		default:
		{
			throw new NoViableAltException(_t);
		}
		}
		}
		{
		_loop296:
		do {
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case NAMESPACE_DECL:
			{
				AST __t287 = _t;
				prefix = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
				match(_t,NAMESPACE_DECL);
				_t = _t.getFirstChild();
				uri = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,STRING_LITERAL);
				_t = _t.getNextSibling();
				context.declareNamespace(prefix.getText(), uri.getText());
				_t = __t287;
				_t = _t.getNextSibling();
				break;
			}
			case DEF_NAMESPACE_DECL:
			{
				AST __t288 = _t;
				org.exist.xquery.parser.XQueryAST tmp31_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,DEF_NAMESPACE_DECL);
				_t = _t.getFirstChild();
				defu = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,STRING_LITERAL);
				_t = _t.getNextSibling();
				context.declareNamespace("", defu.getText());
				_t = __t288;
				_t = _t.getNextSibling();
				break;
			}
			case DEF_FUNCTION_NS_DECL:
			{
				AST __t289 = _t;
				org.exist.xquery.parser.XQueryAST tmp32_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,DEF_FUNCTION_NS_DECL);
				_t = _t.getFirstChild();
				deff = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,STRING_LITERAL);
				_t = _t.getNextSibling();
				context.setDefaultFunctionNamespace(deff.getText());
				_t = __t289;
				_t = _t.getNextSibling();
				break;
			}
			case GLOBAL_VAR:
			{
				AST __t290 = _t;
				qname = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
				match(_t,GLOBAL_VAR);
				_t = _t.getFirstChild();
				
								PathExpr enclosed= new PathExpr(context);
								SequenceType type= null;
							
				{
				if (_t==null) _t=ASTNULL;
				switch ( _t.getType()) {
				case LITERAL_as:
				{
					AST __t292 = _t;
					org.exist.xquery.parser.XQueryAST tmp33_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
					match(_t,LITERAL_as);
					_t = _t.getFirstChild();
					type= new SequenceType();
					sequenceType(_t,type);
					_t = _retTree;
					_t = __t292;
					_t = _t.getNextSibling();
					break;
				}
				case QNAME:
				case PARENTHESIZED:
				case ABSOLUTE_SLASH:
				case ABSOLUTE_DSLASH:
				case WILDCARD:
				case PREFIX_WILDCARD:
				case FUNCTION:
				case UNARY_MINUS:
				case UNARY_PLUS:
				case VARIABLE_REF:
				case ELEMENT:
				case TEXT:
				case BEFORE:
				case AFTER:
				case ATTRIBUTE_TEST:
				case COMP_ELEM_CONSTRUCTOR:
				case COMP_ATTR_CONSTRUCTOR:
				case COMP_TEXT_CONSTRUCTOR:
				case COMP_COMMENT_CONSTRUCTOR:
				case COMP_PI_CONSTRUCTOR:
				case NCNAME:
				case EQ:
				case STRING_LITERAL:
				case LITERAL_element:
				case LCURLY:
				case COMMA:
				case STAR:
				case PLUS:
				case LITERAL_some:
				case LITERAL_every:
				case LITERAL_if:
				case LITERAL_return:
				case LITERAL_or:
				case LITERAL_and:
				case LITERAL_instance:
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
				case UNION:
				case LITERAL_intersect:
				case LITERAL_except:
				case SLASH:
				case DSLASH:
				case LITERAL_text:
				case LITERAL_node:
				case LITERAL_attribute:
				case LITERAL_comment:
				case 125:
				case SELF:
				case XML_COMMENT:
				case XML_PI:
				case AT:
				case PARENT:
				case LITERAL_child:
				case LITERAL_self:
				case LITERAL_descendant:
				case 137:
				case 138:
				case LITERAL_following:
				case LITERAL_parent:
				case LITERAL_ancestor:
				case 142:
				case 143:
				case DOUBLE_LITERAL:
				case DECIMAL_LITERAL:
				case INTEGER_LITERAL:
				case LITERAL_preceding:
				case COMP_DOC_CONSTRUCTOR:
				{
					break;
				}
				default:
				{
					throw new NoViableAltException(_t);
				}
				}
				}
				e = _t==ASTNULL ? null : (org.exist.xquery.parser.XQueryAST)_t;
				step=expr(_t,enclosed);
				_t = _retTree;
				
								VariableDeclaration decl= new VariableDeclaration(context, qname.getText(), enclosed);
								decl.setSequenceType(type);
								decl.setASTNode(e);
								path.add(decl);
								if(myModule != null) {
									QName qn = QName.parse(context, qname.getText());
									myModule.declareVariable(qn, decl);
								}
							
				_t = __t290;
				_t = _t.getNextSibling();
				break;
			}
			case FUNCTION_DECL:
			{
				functionDecl(_t,path);
				_t = _retTree;
				break;
			}
			case LITERAL_import:
			{
				AST __t293 = _t;
				i = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
				match(_t,LITERAL_import);
				_t = _t.getFirstChild();
				
								String modulePrefix = null;
								String location = null;
							
				{
				if (_t==null) _t=ASTNULL;
				switch ( _t.getType()) {
				case NCNAME:
				{
					pfx = (org.exist.xquery.parser.XQueryAST)_t;
					match(_t,NCNAME);
					_t = _t.getNextSibling();
					modulePrefix = pfx.getText();
					break;
				}
				case STRING_LITERAL:
				{
					break;
				}
				default:
				{
					throw new NoViableAltException(_t);
				}
				}
				}
				moduleURI = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,STRING_LITERAL);
				_t = _t.getNextSibling();
				{
				if (_t==null) _t=ASTNULL;
				switch ( _t.getType()) {
				case STRING_LITERAL:
				{
					at = (org.exist.xquery.parser.XQueryAST)_t;
					match(_t,STRING_LITERAL);
					_t = _t.getNextSibling();
					location = at.getText();
					break;
				}
				case 3:
				{
					break;
				}
				default:
				{
					throw new NoViableAltException(_t);
				}
				}
				}
				
				try {
								    context.importModule(moduleURI.getText(), modulePrefix, location);
				} catch(XPathException xpe) {
				xpe.setASTNode(i);
				throw xpe;
				}
							
				_t = __t293;
				_t = _t.getNextSibling();
				break;
			}
			default:
			{
				break _loop296;
			}
			}
		} while (true);
		}
		_retTree = _t;
	}
	
	public final void sequenceType(AST _t,
		SequenceType type
	) throws RecognitionException, XPathException {
		
		org.exist.xquery.parser.XQueryAST sequenceType_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		org.exist.xquery.parser.XQueryAST t = null;
		
		{
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case ATOMIC_TYPE:
		{
			AST __t312 = _t;
			t = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,ATOMIC_TYPE);
			_t = _t.getFirstChild();
			
							QName qn= QName.parse(context, t.getText());
							int code= Type.getType(qn);
							type.setPrimaryType(code);
						
			_t = __t312;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_empty:
		{
			AST __t313 = _t;
			org.exist.xquery.parser.XQueryAST tmp34_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_empty);
			_t = _t.getFirstChild();
			
							type.setPrimaryType(Type.EMPTY);
							type.setCardinality(Cardinality.EMPTY);
						
			_t = __t313;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_item:
		{
			AST __t314 = _t;
			org.exist.xquery.parser.XQueryAST tmp35_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_item);
			_t = _t.getFirstChild();
			type.setPrimaryType(Type.ITEM);
			_t = __t314;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_node:
		{
			AST __t315 = _t;
			org.exist.xquery.parser.XQueryAST tmp36_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_node);
			_t = _t.getFirstChild();
			type.setPrimaryType(Type.NODE);
			_t = __t315;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_element:
		{
			AST __t316 = _t;
			org.exist.xquery.parser.XQueryAST tmp37_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_element);
			_t = _t.getFirstChild();
			type.setPrimaryType(Type.ELEMENT);
			_t = __t316;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_attribute:
		{
			AST __t317 = _t;
			org.exist.xquery.parser.XQueryAST tmp38_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_attribute);
			_t = _t.getFirstChild();
			type.setPrimaryType(Type.ATTRIBUTE);
			_t = __t317;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_text:
		{
			AST __t318 = _t;
			org.exist.xquery.parser.XQueryAST tmp39_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_text);
			_t = _t.getFirstChild();
			type.setPrimaryType(Type.ITEM);
			_t = __t318;
			_t = _t.getNextSibling();
			break;
		}
		case 124:
		{
			AST __t319 = _t;
			org.exist.xquery.parser.XQueryAST tmp40_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,124);
			_t = _t.getFirstChild();
			type.setPrimaryType(Type.PROCESSING_INSTRUCTION);
			_t = __t319;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_comment:
		{
			AST __t320 = _t;
			org.exist.xquery.parser.XQueryAST tmp41_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_comment);
			_t = _t.getFirstChild();
			type.setPrimaryType(Type.COMMENT);
			_t = __t320;
			_t = _t.getNextSibling();
			break;
		}
		case 125:
		{
			AST __t321 = _t;
			org.exist.xquery.parser.XQueryAST tmp42_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,125);
			_t = _t.getFirstChild();
			type.setPrimaryType(Type.DOCUMENT);
			_t = __t321;
			_t = _t.getNextSibling();
			break;
		}
		default:
		{
			throw new NoViableAltException(_t);
		}
		}
		}
		{
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case STAR:
		{
			org.exist.xquery.parser.XQueryAST tmp43_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,STAR);
			_t = _t.getNextSibling();
			type.setCardinality(Cardinality.ZERO_OR_MORE);
			break;
		}
		case PLUS:
		{
			org.exist.xquery.parser.XQueryAST tmp44_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,PLUS);
			_t = _t.getNextSibling();
			type.setCardinality(Cardinality.ONE_OR_MORE);
			break;
		}
		case QUESTION:
		{
			org.exist.xquery.parser.XQueryAST tmp45_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,QUESTION);
			_t = _t.getNextSibling();
			type.setCardinality(Cardinality.ZERO_OR_ONE);
			break;
		}
		case 3:
		{
			break;
		}
		default:
		{
			throw new NoViableAltException(_t);
		}
		}
		}
		_retTree = _t;
	}
	
	public final void functionDecl(AST _t,
		PathExpr path
	) throws RecognitionException, PermissionDeniedException,EXistException,XPathException {
		
		org.exist.xquery.parser.XQueryAST functionDecl_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		org.exist.xquery.parser.XQueryAST name = null;
		Expression step = null;
		
		AST __t298 = _t;
		name = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
		match(_t,FUNCTION_DECL);
		_t = _t.getFirstChild();
		PathExpr body= new PathExpr(context);
		
					QName qn= QName.parse(context, name.getText());
					FunctionSignature signature= new FunctionSignature(qn);
					UserDefinedFunction func= new UserDefinedFunction(context, signature);
		func.setASTNode(name);
					List varList= new ArrayList(3);
				
		{
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case VARIABLE_BINDING:
		{
			paramList(_t,varList);
			_t = _retTree;
			break;
		}
		case LCURLY:
		case LITERAL_as:
		{
			break;
		}
		default:
		{
			throw new NoViableAltException(_t);
		}
		}
		}
		
					SequenceType[] types= new SequenceType[varList.size()];
					int j= 0;
					for (Iterator i= varList.iterator(); i.hasNext(); j++) {
						FunctionParameter param= (FunctionParameter) i.next();
						types[j]= param.type;
						func.addVariable(param.varName);
					}
					signature.setArgumentTypes(types);
					context.declareFunction(func);
					if(myModule != null)
						myModule.declareFunction(func);
				
		{
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case LITERAL_as:
		{
			AST __t301 = _t;
			org.exist.xquery.parser.XQueryAST tmp46_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_as);
			_t = _t.getFirstChild();
			SequenceType type= new SequenceType();
			sequenceType(_t,type);
			_t = _retTree;
			signature.setReturnType(type);
			_t = __t301;
			_t = _t.getNextSibling();
			break;
		}
		case LCURLY:
		{
			break;
		}
		default:
		{
			throw new NoViableAltException(_t);
		}
		}
		}
		AST __t302 = _t;
		org.exist.xquery.parser.XQueryAST tmp47_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
		match(_t,LCURLY);
		_t = _t.getFirstChild();
		step=expr(_t,body);
		_t = _retTree;
		func.setFunctionBody(body);
		_t = __t302;
		_t = _t.getNextSibling();
		_t = __t298;
		_t = _t.getNextSibling();
		_retTree = _t;
	}
	
	public final void paramList(AST _t,
		List vars
	) throws RecognitionException, XPathException {
		
		org.exist.xquery.parser.XQueryAST paramList_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		
		param(_t,vars);
		_t = _retTree;
		{
		_loop305:
		do {
			if (_t==null) _t=ASTNULL;
			if ((_t.getType()==VARIABLE_BINDING)) {
				param(_t,vars);
				_t = _retTree;
			}
			else {
				break _loop305;
			}
			
		} while (true);
		}
		_retTree = _t;
	}
	
	public final void param(AST _t,
		List vars
	) throws RecognitionException, XPathException {
		
		org.exist.xquery.parser.XQueryAST param_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		org.exist.xquery.parser.XQueryAST varname = null;
		
		AST __t307 = _t;
		varname = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
		match(_t,VARIABLE_BINDING);
		_t = _t.getFirstChild();
		
					FunctionParameter var= new FunctionParameter(varname.getText());
					vars.add(var);
				
		{
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case LITERAL_as:
		{
			AST __t309 = _t;
			org.exist.xquery.parser.XQueryAST tmp48_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_as);
			_t = _t.getFirstChild();
			SequenceType type= new SequenceType();
			sequenceType(_t,type);
			_t = _retTree;
			_t = __t309;
			_t = _t.getNextSibling();
			var.type= type;
			break;
		}
		case 3:
		{
			break;
		}
		default:
		{
			throw new NoViableAltException(_t);
		}
		}
		}
		_t = __t307;
		_t = _t.getNextSibling();
		_retTree = _t;
	}
	
	public final Expression  generalComp(AST _t,
		PathExpr path
	) throws RecognitionException, PermissionDeniedException,EXistException,XPathException {
		Expression step;
		
		org.exist.xquery.parser.XQueryAST generalComp_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		org.exist.xquery.parser.XQueryAST eq = null;
		org.exist.xquery.parser.XQueryAST neq = null;
		org.exist.xquery.parser.XQueryAST lt = null;
		org.exist.xquery.parser.XQueryAST lteq = null;
		org.exist.xquery.parser.XQueryAST gt = null;
		org.exist.xquery.parser.XQueryAST gteq = null;
		
			step= null;
			PathExpr left= new PathExpr(context);
			PathExpr right= new PathExpr(context);
		
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case EQ:
		{
			AST __t432 = _t;
			eq = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,EQ);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						step= new GeneralComparison(context, left, right, Constants.EQ);
			step.setASTNode(eq);
						path.add(step);
					
			_t = __t432;
			_t = _t.getNextSibling();
			break;
		}
		case NEQ:
		{
			AST __t433 = _t;
			neq = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,NEQ);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						step= new GeneralComparison(context, left, right, Constants.NEQ);
			step.setASTNode(neq);
						path.add(step);
					
			_t = __t433;
			_t = _t.getNextSibling();
			break;
		}
		case LT:
		{
			AST __t434 = _t;
			lt = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LT);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						step= new GeneralComparison(context, left, right, Constants.LT);
			step.setASTNode(lt);
						path.add(step);
					
			_t = __t434;
			_t = _t.getNextSibling();
			break;
		}
		case LTEQ:
		{
			AST __t435 = _t;
			lteq = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LTEQ);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						step= new GeneralComparison(context, left, right, Constants.LTEQ);
			step.setASTNode(lteq);
						path.add(step);
					
			_t = __t435;
			_t = _t.getNextSibling();
			break;
		}
		case GT:
		{
			AST __t436 = _t;
			gt = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,GT);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						step= new GeneralComparison(context, left, right, Constants.GT);
			step.setASTNode(gt);
						path.add(step);
					
			_t = __t436;
			_t = _t.getNextSibling();
			break;
		}
		case GTEQ:
		{
			AST __t437 = _t;
			gteq = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,GTEQ);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						step= new GeneralComparison(context, left, right, Constants.GTEQ);
			step.setASTNode(gteq);
						path.add(step);
					
			_t = __t437;
			_t = _t.getNextSibling();
			break;
		}
		default:
		{
			throw new NoViableAltException(_t);
		}
		}
		_retTree = _t;
		return step;
	}
	
	public final Expression  valueComp(AST _t,
		PathExpr path
	) throws RecognitionException, PermissionDeniedException,EXistException,XPathException {
		Expression step;
		
		org.exist.xquery.parser.XQueryAST valueComp_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		org.exist.xquery.parser.XQueryAST eq = null;
		org.exist.xquery.parser.XQueryAST ne = null;
		org.exist.xquery.parser.XQueryAST lt = null;
		org.exist.xquery.parser.XQueryAST le = null;
		org.exist.xquery.parser.XQueryAST gt = null;
		org.exist.xquery.parser.XQueryAST ge = null;
		
			step= null;
			PathExpr left= new PathExpr(context);
			PathExpr right= new PathExpr(context);
		
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case LITERAL_eq:
		{
			AST __t425 = _t;
			eq = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_eq);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						step= new ValueComparison(context, left, right, Constants.EQ);
			step.setASTNode(eq);
						path.add(step);
					
			_t = __t425;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_ne:
		{
			AST __t426 = _t;
			ne = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_ne);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						step= new ValueComparison(context, left, right, Constants.NEQ);
			step.setASTNode(ne);
						path.add(step);
					
			_t = __t426;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_lt:
		{
			AST __t427 = _t;
			lt = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_lt);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						step= new ValueComparison(context, left, right, Constants.LT);
			step.setASTNode(lt);
						path.add(step);
					
			_t = __t427;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_le:
		{
			AST __t428 = _t;
			le = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_le);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						step= new ValueComparison(context, left, right, Constants.LTEQ);
			step.setASTNode(le);
						path.add(step);
					
			_t = __t428;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_gt:
		{
			AST __t429 = _t;
			gt = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_gt);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						step= new ValueComparison(context, left, right, Constants.GT);
			step.setASTNode(gt);
						path.add(step);
					
			_t = __t429;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_ge:
		{
			AST __t430 = _t;
			ge = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_ge);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						step= new ValueComparison(context, left, right, Constants.GTEQ);
			step.setASTNode(ge);
						path.add(step);
					
			_t = __t430;
			_t = _t.getNextSibling();
			break;
		}
		default:
		{
			throw new NoViableAltException(_t);
		}
		}
		_retTree = _t;
		return step;
	}
	
	public final Expression  nodeComp(AST _t,
		PathExpr path
	) throws RecognitionException, PermissionDeniedException,EXistException,XPathException {
		Expression step;
		
		org.exist.xquery.parser.XQueryAST nodeComp_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		org.exist.xquery.parser.XQueryAST is = null;
		org.exist.xquery.parser.XQueryAST isnot = null;
		org.exist.xquery.parser.XQueryAST before = null;
		org.exist.xquery.parser.XQueryAST after = null;
		
			step= null;
			PathExpr left= new PathExpr(context);
			PathExpr right= new PathExpr(context);
		
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case LITERAL_is:
		{
			AST __t439 = _t;
			is = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_is);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						step = new NodeComparison(context, left, right, Constants.IS);
			step.setASTNode(is);
						path.add(step);
					
			_t = __t439;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_isnot:
		{
			AST __t440 = _t;
			isnot = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_isnot);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						step = new NodeComparison(context, left, right, Constants.ISNOT);
			step.setASTNode(isnot);
						path.add(step);
					
			_t = __t440;
			_t = _t.getNextSibling();
			break;
		}
		case BEFORE:
		{
			AST __t441 = _t;
			before = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,BEFORE);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						step = new NodeComparison(context, left, right, Constants.BEFORE);
			step.setASTNode(before);
						path.add(step);
					
			_t = __t441;
			_t = _t.getNextSibling();
			break;
		}
		case AFTER:
		{
			AST __t442 = _t;
			after = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,AFTER);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						step = new NodeComparison(context, left, right, Constants.AFTER);
			step.setASTNode(after);
						path.add(step);
					
			_t = __t442;
			_t = _t.getNextSibling();
			break;
		}
		default:
		{
			throw new NoViableAltException(_t);
		}
		}
		_retTree = _t;
		return step;
	}
	
	public final Expression  fulltextComp(AST _t,
		PathExpr path
	) throws RecognitionException, PermissionDeniedException,EXistException,XPathException {
		Expression step;
		
		org.exist.xquery.parser.XQueryAST fulltextComp_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		
			step= null;
			PathExpr nodes= new PathExpr(context);
			PathExpr query= new PathExpr(context);
		
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case ANDEQ:
		{
			AST __t422 = _t;
			org.exist.xquery.parser.XQueryAST tmp49_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,ANDEQ);
			_t = _t.getFirstChild();
			step=expr(_t,nodes);
			_t = _retTree;
			step=expr(_t,query);
			_t = _retTree;
			_t = __t422;
			_t = _t.getNextSibling();
			
					ExtFulltext exprCont= new ExtFulltext(context, Constants.FULLTEXT_AND);
					exprCont.setPath(nodes);
					exprCont.addTerm(query);
					path.addPath(exprCont);
				
			break;
		}
		case OREQ:
		{
			AST __t423 = _t;
			org.exist.xquery.parser.XQueryAST tmp50_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,OREQ);
			_t = _t.getFirstChild();
			step=expr(_t,nodes);
			_t = _retTree;
			step=expr(_t,query);
			_t = _retTree;
			_t = __t423;
			_t = _t.getNextSibling();
			
					ExtFulltext exprCont= new ExtFulltext(context, Constants.FULLTEXT_OR);
					exprCont.setPath(nodes);
					exprCont.addTerm(query);
					path.addPath(exprCont);
				
			break;
		}
		default:
		{
			throw new NoViableAltException(_t);
		}
		}
		_retTree = _t;
		return step;
	}
	
	public final Expression  primaryExpr(AST _t,
		PathExpr path
	) throws RecognitionException, PermissionDeniedException,EXistException,XPathException {
		Expression step;
		
		org.exist.xquery.parser.XQueryAST primaryExpr_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		org.exist.xquery.parser.XQueryAST v = null;
		
			step = null;
		
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case ELEMENT:
		case TEXT:
		case COMP_ELEM_CONSTRUCTOR:
		case COMP_ATTR_CONSTRUCTOR:
		case COMP_TEXT_CONSTRUCTOR:
		case COMP_COMMENT_CONSTRUCTOR:
		case COMP_PI_CONSTRUCTOR:
		case LCURLY:
		case XML_COMMENT:
		case XML_PI:
		case COMP_DOC_CONSTRUCTOR:
		{
			step=constructor(_t,path);
			_t = _retTree;
			step=predicates(_t,step);
			_t = _retTree;
			
					path.add(step);
				
			break;
		}
		case PARENTHESIZED:
		{
			AST __t377 = _t;
			org.exist.xquery.parser.XQueryAST tmp51_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,PARENTHESIZED);
			_t = _t.getFirstChild();
			PathExpr pathExpr= new PathExpr(context);
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case QNAME:
			case PARENTHESIZED:
			case ABSOLUTE_SLASH:
			case ABSOLUTE_DSLASH:
			case WILDCARD:
			case PREFIX_WILDCARD:
			case FUNCTION:
			case UNARY_MINUS:
			case UNARY_PLUS:
			case VARIABLE_REF:
			case ELEMENT:
			case TEXT:
			case BEFORE:
			case AFTER:
			case ATTRIBUTE_TEST:
			case COMP_ELEM_CONSTRUCTOR:
			case COMP_ATTR_CONSTRUCTOR:
			case COMP_TEXT_CONSTRUCTOR:
			case COMP_COMMENT_CONSTRUCTOR:
			case COMP_PI_CONSTRUCTOR:
			case NCNAME:
			case EQ:
			case STRING_LITERAL:
			case LITERAL_element:
			case LCURLY:
			case COMMA:
			case STAR:
			case PLUS:
			case LITERAL_some:
			case LITERAL_every:
			case LITERAL_if:
			case LITERAL_return:
			case LITERAL_or:
			case LITERAL_and:
			case LITERAL_instance:
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
			case UNION:
			case LITERAL_intersect:
			case LITERAL_except:
			case SLASH:
			case DSLASH:
			case LITERAL_text:
			case LITERAL_node:
			case LITERAL_attribute:
			case LITERAL_comment:
			case 125:
			case SELF:
			case XML_COMMENT:
			case XML_PI:
			case AT:
			case PARENT:
			case LITERAL_child:
			case LITERAL_self:
			case LITERAL_descendant:
			case 137:
			case 138:
			case LITERAL_following:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 142:
			case 143:
			case DOUBLE_LITERAL:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
			case LITERAL_preceding:
			case COMP_DOC_CONSTRUCTOR:
			{
				step=expr(_t,pathExpr);
				_t = _retTree;
				break;
			}
			case 3:
			{
				break;
			}
			default:
			{
				throw new NoViableAltException(_t);
			}
			}
			}
			_t = __t377;
			_t = _t.getNextSibling();
			step=predicates(_t,pathExpr);
			_t = _retTree;
			path.add(step);
			break;
		}
		case STRING_LITERAL:
		case DOUBLE_LITERAL:
		case DECIMAL_LITERAL:
		case INTEGER_LITERAL:
		{
			step=literalExpr(_t,path);
			_t = _retTree;
			step=predicates(_t,step);
			_t = _retTree;
			path.add(step);
			break;
		}
		case VARIABLE_REF:
		{
			v = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,VARIABLE_REF);
			_t = _t.getNextSibling();
			
			step= new VariableReference(context, v.getText());
			step.setASTNode(v);
			
			step=predicates(_t,step);
			_t = _retTree;
			path.add(step);
			break;
		}
		case FUNCTION:
		{
			step=functionCall(_t,path);
			_t = _retTree;
			step=predicates(_t,step);
			_t = _retTree;
			path.add(step);
			break;
		}
		default:
		{
			throw new NoViableAltException(_t);
		}
		}
		_retTree = _t;
		return step;
	}
	
	public final Expression  pathExpr(AST _t,
		PathExpr path
	) throws RecognitionException, PermissionDeniedException,EXistException,XPathException {
		Expression step;
		
		org.exist.xquery.parser.XQueryAST pathExpr_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		org.exist.xquery.parser.XQueryAST qn = null;
		org.exist.xquery.parser.XQueryAST nc1 = null;
		org.exist.xquery.parser.XQueryAST nc = null;
		org.exist.xquery.parser.XQueryAST attr = null;
		org.exist.xquery.parser.XQueryAST nc2 = null;
		org.exist.xquery.parser.XQueryAST nc3 = null;
		
			Expression rightStep= null;
			step= null;
			int axis= Constants.CHILD_AXIS;
		
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case QNAME:
		case WILDCARD:
		case PREFIX_WILDCARD:
		case ATTRIBUTE_TEST:
		case NCNAME:
		case LITERAL_element:
		case LITERAL_text:
		case LITERAL_node:
		case LITERAL_attribute:
		case LITERAL_comment:
		case 125:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_descendant:
		case 137:
		case 138:
		case LITERAL_following:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 142:
		case 143:
		case LITERAL_preceding:
		{
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case LITERAL_attribute:
			case LITERAL_child:
			case LITERAL_self:
			case LITERAL_descendant:
			case 137:
			case 138:
			case LITERAL_following:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 142:
			case 143:
			case LITERAL_preceding:
			{
				axis=forwardAxis(_t);
				_t = _retTree;
				break;
			}
			case QNAME:
			case WILDCARD:
			case PREFIX_WILDCARD:
			case ATTRIBUTE_TEST:
			case NCNAME:
			case LITERAL_element:
			case LITERAL_text:
			case LITERAL_node:
			case LITERAL_comment:
			case 125:
			{
				break;
			}
			default:
			{
				throw new NoViableAltException(_t);
			}
			}
			}
			NodeTest test;
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case QNAME:
			{
				qn = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,QNAME);
				_t = _t.getNextSibling();
				
							QName qname= QName.parse(context, qn.getText());
							test= new NameTest(Type.ELEMENT, qname);
						
				break;
			}
			case PREFIX_WILDCARD:
			{
				AST __t382 = _t;
				org.exist.xquery.parser.XQueryAST tmp52_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,PREFIX_WILDCARD);
				_t = _t.getFirstChild();
				nc1 = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,NCNAME);
				_t = _t.getNextSibling();
				_t = __t382;
				_t = _t.getNextSibling();
				
							QName qname= new QName(nc1.getText(), null, null);
							test= new NameTest(Type.ELEMENT, qname);
						
				break;
			}
			case NCNAME:
			{
				AST __t383 = _t;
				nc = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
				match(_t,NCNAME);
				_t = _t.getFirstChild();
				org.exist.xquery.parser.XQueryAST tmp53_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,WILDCARD);
				_t = _t.getNextSibling();
				_t = __t383;
				_t = _t.getNextSibling();
				
							String namespaceURI= context.getURIForPrefix(nc.getText());
							QName qname= new QName(null, namespaceURI, null);
							test= new NameTest(Type.ELEMENT, qname);
						
				break;
			}
			case WILDCARD:
			{
				org.exist.xquery.parser.XQueryAST tmp54_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,WILDCARD);
				_t = _t.getNextSibling();
				test= new TypeTest(Type.ELEMENT);
				break;
			}
			case LITERAL_node:
			{
				org.exist.xquery.parser.XQueryAST tmp55_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,LITERAL_node);
				_t = _t.getNextSibling();
				test= new AnyNodeTest();
				break;
			}
			case LITERAL_text:
			{
				org.exist.xquery.parser.XQueryAST tmp56_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,LITERAL_text);
				_t = _t.getNextSibling();
				test= new TypeTest(Type.TEXT);
				break;
			}
			case LITERAL_element:
			{
				org.exist.xquery.parser.XQueryAST tmp57_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,LITERAL_element);
				_t = _t.getNextSibling();
				test= new TypeTest(Type.ELEMENT);
				break;
			}
			case LITERAL_comment:
			{
				org.exist.xquery.parser.XQueryAST tmp58_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,LITERAL_comment);
				_t = _t.getNextSibling();
				test= new TypeTest(Type.COMMENT);
				break;
			}
			case ATTRIBUTE_TEST:
			{
				org.exist.xquery.parser.XQueryAST tmp59_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,ATTRIBUTE_TEST);
				_t = _t.getNextSibling();
				test= new TypeTest(Type.ATTRIBUTE);
				break;
			}
			case 125:
			{
				org.exist.xquery.parser.XQueryAST tmp60_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,125);
				_t = _t.getNextSibling();
				test= new TypeTest(Type.DOCUMENT);
				break;
			}
			default:
			{
				throw new NoViableAltException(_t);
			}
			}
			}
			
					step= new LocationStep(context, axis, test);
					path.add(step);
				
			{
			_loop385:
			do {
				if (_t==null) _t=ASTNULL;
				if ((_t.getType()==PREDICATE)) {
					predicate(_t,(LocationStep) step);
					_t = _retTree;
				}
				else {
					break _loop385;
				}
				
			} while (true);
			}
			break;
		}
		case AT:
		{
			org.exist.xquery.parser.XQueryAST tmp61_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,AT);
			_t = _t.getNextSibling();
			QName qname= null;
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case QNAME:
			{
				attr = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,QNAME);
				_t = _t.getNextSibling();
				qname= QName.parse(context, attr.getText(), null);
				break;
			}
			case WILDCARD:
			{
				org.exist.xquery.parser.XQueryAST tmp62_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,WILDCARD);
				_t = _t.getNextSibling();
				break;
			}
			case PREFIX_WILDCARD:
			{
				AST __t387 = _t;
				org.exist.xquery.parser.XQueryAST tmp63_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,PREFIX_WILDCARD);
				_t = _t.getFirstChild();
				nc2 = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,NCNAME);
				_t = _t.getNextSibling();
				_t = __t387;
				_t = _t.getNextSibling();
				qname= new QName(nc2.getText(), null, null);
				break;
			}
			case NCNAME:
			{
				AST __t388 = _t;
				nc3 = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
				match(_t,NCNAME);
				_t = _t.getFirstChild();
				org.exist.xquery.parser.XQueryAST tmp64_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,WILDCARD);
				_t = _t.getNextSibling();
				_t = __t388;
				_t = _t.getNextSibling();
				
							String namespaceURI= context.getURIForPrefix(nc3.getText());
							if (namespaceURI == null)
								throw new EXistException("No namespace defined for prefix " + nc3.getText());
							qname= new QName(null, namespaceURI, null);
						
				break;
			}
			default:
			{
				throw new NoViableAltException(_t);
			}
			}
			}
			
					NodeTest test= qname == null ? new TypeTest(Type.ATTRIBUTE) : new NameTest(Type.ATTRIBUTE, qname);
					step= new LocationStep(context, Constants.ATTRIBUTE_AXIS, test);
					path.add(step);
				
			{
			_loop390:
			do {
				if (_t==null) _t=ASTNULL;
				if ((_t.getType()==PREDICATE)) {
					predicate(_t,(LocationStep) step);
					_t = _retTree;
				}
				else {
					break _loop390;
				}
				
			} while (true);
			}
			break;
		}
		case SELF:
		{
			org.exist.xquery.parser.XQueryAST tmp65_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,SELF);
			_t = _t.getNextSibling();
			
					step= new LocationStep(context, Constants.SELF_AXIS, new TypeTest(Type.NODE));
					path.add(step);
				
			{
			_loop392:
			do {
				if (_t==null) _t=ASTNULL;
				if ((_t.getType()==PREDICATE)) {
					predicate(_t,(LocationStep) step);
					_t = _retTree;
				}
				else {
					break _loop392;
				}
				
			} while (true);
			}
			break;
		}
		case PARENT:
		{
			org.exist.xquery.parser.XQueryAST tmp66_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,PARENT);
			_t = _t.getNextSibling();
			
					step= new LocationStep(context, Constants.PARENT_AXIS, new TypeTest(Type.NODE));
					path.add(step);
				
			{
			_loop394:
			do {
				if (_t==null) _t=ASTNULL;
				if ((_t.getType()==PREDICATE)) {
					predicate(_t,(LocationStep) step);
					_t = _retTree;
				}
				else {
					break _loop394;
				}
				
			} while (true);
			}
			break;
		}
		case SLASH:
		{
			AST __t395 = _t;
			org.exist.xquery.parser.XQueryAST tmp67_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,SLASH);
			_t = _t.getFirstChild();
			step=expr(_t,path);
			_t = _retTree;
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case QNAME:
			case PARENTHESIZED:
			case ABSOLUTE_SLASH:
			case ABSOLUTE_DSLASH:
			case WILDCARD:
			case PREFIX_WILDCARD:
			case FUNCTION:
			case UNARY_MINUS:
			case UNARY_PLUS:
			case VARIABLE_REF:
			case ELEMENT:
			case TEXT:
			case BEFORE:
			case AFTER:
			case ATTRIBUTE_TEST:
			case COMP_ELEM_CONSTRUCTOR:
			case COMP_ATTR_CONSTRUCTOR:
			case COMP_TEXT_CONSTRUCTOR:
			case COMP_COMMENT_CONSTRUCTOR:
			case COMP_PI_CONSTRUCTOR:
			case NCNAME:
			case EQ:
			case STRING_LITERAL:
			case LITERAL_element:
			case LCURLY:
			case COMMA:
			case STAR:
			case PLUS:
			case LITERAL_some:
			case LITERAL_every:
			case LITERAL_if:
			case LITERAL_return:
			case LITERAL_or:
			case LITERAL_and:
			case LITERAL_instance:
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
			case UNION:
			case LITERAL_intersect:
			case LITERAL_except:
			case SLASH:
			case DSLASH:
			case LITERAL_text:
			case LITERAL_node:
			case LITERAL_attribute:
			case LITERAL_comment:
			case 125:
			case SELF:
			case XML_COMMENT:
			case XML_PI:
			case AT:
			case PARENT:
			case LITERAL_child:
			case LITERAL_self:
			case LITERAL_descendant:
			case 137:
			case 138:
			case LITERAL_following:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 142:
			case 143:
			case DOUBLE_LITERAL:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
			case LITERAL_preceding:
			case COMP_DOC_CONSTRUCTOR:
			{
				rightStep=expr(_t,path);
				_t = _retTree;
				
								if (rightStep instanceof LocationStep) {
									if(((LocationStep) rightStep).getAxis() == -1)
										((LocationStep) rightStep).setAxis(Constants.CHILD_AXIS);
								} else {
									//rightStep = new SimpleStep(context, Constants.CHILD_AXIS, rightStep);
									rightStep.setPrimaryAxis(Constants.CHILD_AXIS);
									//path.replaceLastExpression(rightStep);
								}
							
				break;
			}
			case 3:
			{
				break;
			}
			default:
			{
				throw new NoViableAltException(_t);
			}
			}
			}
			_t = __t395;
			_t = _t.getNextSibling();
			
					if (step instanceof LocationStep && ((LocationStep) step).getAxis() == -1)
						 ((LocationStep) step).setAxis(Constants.CHILD_AXIS);
				
			break;
		}
		case DSLASH:
		{
			AST __t397 = _t;
			org.exist.xquery.parser.XQueryAST tmp68_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,DSLASH);
			_t = _t.getFirstChild();
			step=expr(_t,path);
			_t = _retTree;
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case QNAME:
			case PARENTHESIZED:
			case ABSOLUTE_SLASH:
			case ABSOLUTE_DSLASH:
			case WILDCARD:
			case PREFIX_WILDCARD:
			case FUNCTION:
			case UNARY_MINUS:
			case UNARY_PLUS:
			case VARIABLE_REF:
			case ELEMENT:
			case TEXT:
			case BEFORE:
			case AFTER:
			case ATTRIBUTE_TEST:
			case COMP_ELEM_CONSTRUCTOR:
			case COMP_ATTR_CONSTRUCTOR:
			case COMP_TEXT_CONSTRUCTOR:
			case COMP_COMMENT_CONSTRUCTOR:
			case COMP_PI_CONSTRUCTOR:
			case NCNAME:
			case EQ:
			case STRING_LITERAL:
			case LITERAL_element:
			case LCURLY:
			case COMMA:
			case STAR:
			case PLUS:
			case LITERAL_some:
			case LITERAL_every:
			case LITERAL_if:
			case LITERAL_return:
			case LITERAL_or:
			case LITERAL_and:
			case LITERAL_instance:
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
			case UNION:
			case LITERAL_intersect:
			case LITERAL_except:
			case SLASH:
			case DSLASH:
			case LITERAL_text:
			case LITERAL_node:
			case LITERAL_attribute:
			case LITERAL_comment:
			case 125:
			case SELF:
			case XML_COMMENT:
			case XML_PI:
			case AT:
			case PARENT:
			case LITERAL_child:
			case LITERAL_self:
			case LITERAL_descendant:
			case 137:
			case 138:
			case LITERAL_following:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 142:
			case 143:
			case DOUBLE_LITERAL:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
			case LITERAL_preceding:
			case COMP_DOC_CONSTRUCTOR:
			{
				rightStep=expr(_t,path);
				_t = _retTree;
				
								if (rightStep instanceof LocationStep) {
									LocationStep rs= (LocationStep) rightStep;
									if (rs.getAxis() == Constants.ATTRIBUTE_AXIS)
										rs.setAxis(Constants.DESCENDANT_ATTRIBUTE_AXIS);
									else
										rs.setAxis(Constants.DESCENDANT_SELF_AXIS);
								} else {
									rightStep.setPrimaryAxis(Constants.DESCENDANT_SELF_AXIS);
								}
							
				break;
			}
			case 3:
			{
				break;
			}
			default:
			{
				throw new NoViableAltException(_t);
			}
			}
			}
			_t = __t397;
			_t = _t.getNextSibling();
			
					if (step instanceof LocationStep && ((LocationStep) step).getAxis() == -1)
						 ((LocationStep) step).setAxis(Constants.DESCENDANT_SELF_AXIS);
				
			break;
		}
		default:
		{
			throw new NoViableAltException(_t);
		}
		}
		_retTree = _t;
		return step;
	}
	
	public final Expression  numericExpr(AST _t,
		PathExpr path
	) throws RecognitionException, PermissionDeniedException,EXistException,XPathException {
		Expression step;
		
		org.exist.xquery.parser.XQueryAST numericExpr_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		org.exist.xquery.parser.XQueryAST plus = null;
		org.exist.xquery.parser.XQueryAST minus = null;
		org.exist.xquery.parser.XQueryAST uminus = null;
		org.exist.xquery.parser.XQueryAST uplus = null;
		org.exist.xquery.parser.XQueryAST div = null;
		org.exist.xquery.parser.XQueryAST idiv = null;
		org.exist.xquery.parser.XQueryAST mod = null;
		org.exist.xquery.parser.XQueryAST mult = null;
		
			step= null;
			PathExpr left= new PathExpr(context);
			PathExpr right= new PathExpr(context);
		
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case PLUS:
		{
			AST __t402 = _t;
			plus = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,PLUS);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			_t = __t402;
			_t = _t.getNextSibling();
			
					OpNumeric op= new OpNumeric(context, left, right, Constants.PLUS);
			op.setASTNode(plus);
					path.addPath(op);
					step= op;
				
			break;
		}
		case MINUS:
		{
			AST __t403 = _t;
			minus = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,MINUS);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			_t = __t403;
			_t = _t.getNextSibling();
			
					OpNumeric op= new OpNumeric(context, left, right, Constants.MINUS);
			op.setASTNode(minus);
					path.addPath(op);
					step= op;
				
			break;
		}
		case UNARY_MINUS:
		{
			AST __t404 = _t;
			uminus = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,UNARY_MINUS);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			_t = __t404;
			_t = _t.getNextSibling();
			
					UnaryExpr unary= new UnaryExpr(context, Constants.MINUS);
			unary.setASTNode(uminus);
					unary.add(left);
					path.addPath(unary);
					step= unary;
				
			break;
		}
		case UNARY_PLUS:
		{
			AST __t405 = _t;
			uplus = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,UNARY_PLUS);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			_t = __t405;
			_t = _t.getNextSibling();
			
					UnaryExpr unary= new UnaryExpr(context, Constants.PLUS);
			unary.setASTNode(uplus);
					unary.add(left);
					path.addPath(unary);
					step= unary;
				
			break;
		}
		case LITERAL_div:
		{
			AST __t406 = _t;
			div = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_div);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			_t = __t406;
			_t = _t.getNextSibling();
			
					OpNumeric op= new OpNumeric(context, left, right, Constants.DIV);
			op.setASTNode(div);
					path.addPath(op);
					step= op;
				
			break;
		}
		case LITERAL_idiv:
		{
			AST __t407 = _t;
			idiv = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_idiv);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			_t = __t407;
			_t = _t.getNextSibling();
			
					OpNumeric op= new OpNumeric(context, left, right, Constants.IDIV);
			op.setASTNode(idiv);
					path.addPath(op);
					step= op;
				
			break;
		}
		case LITERAL_mod:
		{
			AST __t408 = _t;
			mod = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_mod);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			_t = __t408;
			_t = _t.getNextSibling();
			
					OpNumeric op= new OpNumeric(context, left, right, Constants.MOD);
			op.setASTNode(mod);
					path.addPath(op);
					step= op;
				
			break;
		}
		case STAR:
		{
			AST __t409 = _t;
			mult = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,STAR);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			_t = __t409;
			_t = _t.getNextSibling();
			
					OpNumeric op= new OpNumeric(context, left, right, Constants.MULT);
			op.setASTNode(mult);
					path.addPath(op);
					step= op;
				
			break;
		}
		default:
		{
			throw new NoViableAltException(_t);
		}
		}
		_retTree = _t;
		return step;
	}
	
	public final Expression  constructor(AST _t,
		PathExpr path
	) throws RecognitionException, PermissionDeniedException,EXistException,XPathException {
		Expression step;
		
		org.exist.xquery.parser.XQueryAST constructor_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		org.exist.xquery.parser.XQueryAST qn = null;
		org.exist.xquery.parser.XQueryAST prefix = null;
		org.exist.xquery.parser.XQueryAST uri = null;
		org.exist.xquery.parser.XQueryAST attr = null;
		org.exist.xquery.parser.XQueryAST pid = null;
		org.exist.xquery.parser.XQueryAST e = null;
		org.exist.xquery.parser.XQueryAST attrName = null;
		org.exist.xquery.parser.XQueryAST attrVal = null;
		org.exist.xquery.parser.XQueryAST pcdata = null;
		org.exist.xquery.parser.XQueryAST t = null;
		org.exist.xquery.parser.XQueryAST tc = null;
		org.exist.xquery.parser.XQueryAST d = null;
		org.exist.xquery.parser.XQueryAST cdata = null;
		org.exist.xquery.parser.XQueryAST p = null;
		org.exist.xquery.parser.XQueryAST l = null;
		
			step= null;
			PathExpr elementContent= null;
			Expression contentExpr= null;
			Expression qnameExpr = null;
		
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case COMP_ELEM_CONSTRUCTOR:
		{
			AST __t444 = _t;
			qn = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,COMP_ELEM_CONSTRUCTOR);
			_t = _t.getFirstChild();
			
						ElementConstructor c= new ElementConstructor(context);
						c.setASTNode(qn);
						step= c;
						elementContent = new SequenceConstructor(context);
						EnclosedExpr enclosed = new EnclosedExpr(context);
						enclosed.addPath(elementContent);
						c.setContent(enclosed);
						PathExpr qnamePathExpr = new PathExpr(context);
						c.setNameExpr(qnamePathExpr);
					
			qnameExpr=expr(_t,qnamePathExpr);
			_t = _retTree;
			{
			_loop447:
			do {
				if (_t==null) _t=ASTNULL;
				switch ( _t.getType()) {
				case COMP_NS_CONSTRUCTOR:
				{
					AST __t446 = _t;
					prefix = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
					match(_t,COMP_NS_CONSTRUCTOR);
					_t = _t.getFirstChild();
					uri = (org.exist.xquery.parser.XQueryAST)_t;
					match(_t,STRING_LITERAL);
					_t = _t.getNextSibling();
					_t = __t446;
					_t = _t.getNextSibling();
					
									c.addNamespaceDecl(prefix.getText(), uri.getText());
								
					break;
				}
				case QNAME:
				case PARENTHESIZED:
				case ABSOLUTE_SLASH:
				case ABSOLUTE_DSLASH:
				case WILDCARD:
				case PREFIX_WILDCARD:
				case FUNCTION:
				case UNARY_MINUS:
				case UNARY_PLUS:
				case VARIABLE_REF:
				case ELEMENT:
				case TEXT:
				case BEFORE:
				case AFTER:
				case ATTRIBUTE_TEST:
				case COMP_ELEM_CONSTRUCTOR:
				case COMP_ATTR_CONSTRUCTOR:
				case COMP_TEXT_CONSTRUCTOR:
				case COMP_COMMENT_CONSTRUCTOR:
				case COMP_PI_CONSTRUCTOR:
				case NCNAME:
				case EQ:
				case STRING_LITERAL:
				case LITERAL_element:
				case LCURLY:
				case COMMA:
				case STAR:
				case PLUS:
				case LITERAL_some:
				case LITERAL_every:
				case LITERAL_if:
				case LITERAL_return:
				case LITERAL_or:
				case LITERAL_and:
				case LITERAL_instance:
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
				case UNION:
				case LITERAL_intersect:
				case LITERAL_except:
				case SLASH:
				case DSLASH:
				case LITERAL_text:
				case LITERAL_node:
				case LITERAL_attribute:
				case LITERAL_comment:
				case 125:
				case SELF:
				case XML_COMMENT:
				case XML_PI:
				case AT:
				case PARENT:
				case LITERAL_child:
				case LITERAL_self:
				case LITERAL_descendant:
				case 137:
				case 138:
				case LITERAL_following:
				case LITERAL_parent:
				case LITERAL_ancestor:
				case 142:
				case 143:
				case DOUBLE_LITERAL:
				case DECIMAL_LITERAL:
				case INTEGER_LITERAL:
				case LITERAL_preceding:
				case COMP_DOC_CONSTRUCTOR:
				{
					contentExpr=expr(_t,elementContent);
					_t = _retTree;
					break;
				}
				default:
				{
					break _loop447;
				}
				}
			} while (true);
			}
			_t = __t444;
			_t = _t.getNextSibling();
			break;
		}
		case COMP_ATTR_CONSTRUCTOR:
		{
			AST __t448 = _t;
			attr = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,COMP_ATTR_CONSTRUCTOR);
			_t = _t.getFirstChild();
			
						DynamicAttributeConstructor a= new DynamicAttributeConstructor(context);
			a.setASTNode(attr);
			step = a;
			PathExpr qnamePathExpr = new PathExpr(context);
			a.setNameExpr(qnamePathExpr);
			elementContent = new PathExpr(context);
			a.setContentExpr(elementContent);
					
			qnameExpr=expr(_t,qnamePathExpr);
			_t = _retTree;
			contentExpr=expr(_t,elementContent);
			_t = _retTree;
			_t = __t448;
			_t = _t.getNextSibling();
			break;
		}
		case COMP_PI_CONSTRUCTOR:
		{
			AST __t449 = _t;
			pid = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,COMP_PI_CONSTRUCTOR);
			_t = _t.getFirstChild();
			
						DynamicPIConstructor pd= new DynamicPIConstructor(context);
			pd.setASTNode(pid);
			step = pd;
			PathExpr qnamePathExpr = new PathExpr(context);
			pd.setNameExpr(qnamePathExpr);
			elementContent = new PathExpr(context);
			pd.setContentExpr(elementContent);
					
			qnameExpr=expr(_t,qnamePathExpr);
			_t = _retTree;
			contentExpr=expr(_t,elementContent);
			_t = _retTree;
			_t = __t449;
			_t = _t.getNextSibling();
			break;
		}
		case ELEMENT:
		{
			AST __t450 = _t;
			e = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,ELEMENT);
			_t = _t.getFirstChild();
			
						ElementConstructor c= new ElementConstructor(context, e.getText());
						c.setASTNode(e);
						step= c;
					
			{
			_loop456:
			do {
				if (_t==null) _t=ASTNULL;
				if ((_t.getType()==ATTRIBUTE)) {
					AST __t452 = _t;
					attrName = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
					match(_t,ATTRIBUTE);
					_t = _t.getFirstChild();
					
										AttributeConstructor attrib= new AttributeConstructor(context, attrName.getText());
					attrib.setASTNode(attrName);
									
					{
					int _cnt455=0;
					_loop455:
					do {
						if (_t==null) _t=ASTNULL;
						switch ( _t.getType()) {
						case ATTRIBUTE_CONTENT:
						{
							attrVal = (org.exist.xquery.parser.XQueryAST)_t;
							match(_t,ATTRIBUTE_CONTENT);
							_t = _t.getNextSibling();
							attrib.addValue(attrVal.getText());
							break;
						}
						case LCURLY:
						{
							AST __t454 = _t;
							org.exist.xquery.parser.XQueryAST tmp69_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
							match(_t,LCURLY);
							_t = _t.getFirstChild();
							PathExpr enclosed= new PathExpr(context);
							expr(_t,enclosed);
							_t = _retTree;
							attrib.addEnclosedExpr(enclosed);
							_t = __t454;
							_t = _t.getNextSibling();
							break;
						}
						default:
						{
							if ( _cnt455>=1 ) { break _loop455; } else {throw new NoViableAltException(_t);}
						}
						}
						_cnt455++;
					} while (true);
					}
					c.addAttribute(attrib);
					_t = __t452;
					_t = _t.getNextSibling();
				}
				else {
					break _loop456;
				}
				
			} while (true);
			}
			{
			_loop458:
			do {
				if (_t==null) _t=ASTNULL;
				if ((_tokenSet_1.member(_t.getType()))) {
					
									if (elementContent == null) {
										elementContent= new PathExpr(context);
										c.setContent(elementContent);
									}
								
					contentExpr=constructor(_t,elementContent);
					_t = _retTree;
					elementContent.add(contentExpr);
				}
				else {
					break _loop458;
				}
				
			} while (true);
			}
			_t = __t450;
			_t = _t.getNextSibling();
			break;
		}
		case TEXT:
		{
			AST __t459 = _t;
			pcdata = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,TEXT);
			_t = _t.getFirstChild();
			
						TextConstructor text= new TextConstructor(context, pcdata.getText());
			text.setASTNode(pcdata);
						step= text;
					
			_t = __t459;
			_t = _t.getNextSibling();
			break;
		}
		case COMP_TEXT_CONSTRUCTOR:
		{
			AST __t460 = _t;
			t = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,COMP_TEXT_CONSTRUCTOR);
			_t = _t.getFirstChild();
			
						elementContent = new PathExpr(context);
						DynamicTextConstructor text = new DynamicTextConstructor(context, elementContent);
						text.setASTNode(t);
						step= text;
					
			contentExpr=expr(_t,elementContent);
			_t = _retTree;
			_t = __t460;
			_t = _t.getNextSibling();
			break;
		}
		case COMP_COMMENT_CONSTRUCTOR:
		{
			AST __t461 = _t;
			tc = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,COMP_COMMENT_CONSTRUCTOR);
			_t = _t.getFirstChild();
			
						elementContent = new PathExpr(context);
						DynamicCommentConstructor comment = new DynamicCommentConstructor(context, elementContent);
						comment.setASTNode(t);
						step= comment;
					
			contentExpr=expr(_t,elementContent);
			_t = _retTree;
			_t = __t461;
			_t = _t.getNextSibling();
			break;
		}
		case COMP_DOC_CONSTRUCTOR:
		{
			AST __t462 = _t;
			d = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,COMP_DOC_CONSTRUCTOR);
			_t = _t.getFirstChild();
			
						elementContent = new PathExpr(context);
						DocumentConstructor doc = new DocumentConstructor(context, elementContent);
						doc.setASTNode(d);
						step= doc;
					
			contentExpr=expr(_t,elementContent);
			_t = _retTree;
			_t = __t462;
			_t = _t.getNextSibling();
			break;
		}
		case XML_COMMENT:
		{
			AST __t463 = _t;
			cdata = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,XML_COMMENT);
			_t = _t.getFirstChild();
			
						CommentConstructor comment= new CommentConstructor(context, cdata.getText());
			comment.setASTNode(cdata);
						step= comment;
					
			_t = __t463;
			_t = _t.getNextSibling();
			break;
		}
		case XML_PI:
		{
			AST __t464 = _t;
			p = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,XML_PI);
			_t = _t.getFirstChild();
			
						PIConstructor pi= new PIConstructor(context, p.getText());
			pi.setASTNode(p);
						step= pi;
					
			_t = __t464;
			_t = _t.getNextSibling();
			break;
		}
		case LCURLY:
		{
			AST __t465 = _t;
			l = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LCURLY);
			_t = _t.getFirstChild();
			
			EnclosedExpr subexpr= new EnclosedExpr(context); 
			subexpr.setASTNode(l);
			
			step=expr(_t,subexpr);
			_t = _retTree;
			step= subexpr;
			_t = __t465;
			_t = _t.getNextSibling();
			break;
		}
		default:
		{
			throw new NoViableAltException(_t);
		}
		}
		_retTree = _t;
		return step;
	}
	
	public final Expression  predicates(AST _t,
		Expression expression
	) throws RecognitionException, PermissionDeniedException,EXistException,XPathException {
		Expression step;
		
		org.exist.xquery.parser.XQueryAST predicates_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		
			FilteredExpression filter= null;
			step= expression;
		
		
		{
		_loop413:
		do {
			if (_t==null) _t=ASTNULL;
			if ((_t.getType()==PREDICATE)) {
				AST __t412 = _t;
				org.exist.xquery.parser.XQueryAST tmp70_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,PREDICATE);
				_t = _t.getFirstChild();
				
								if (filter == null) {
									filter= new FilteredExpression(context, step);
									step= filter;
								}
								Predicate predicateExpr= new Predicate(context);
							
				expr(_t,predicateExpr);
				_t = _retTree;
				
								filter.addPredicate(predicateExpr);
							
				_t = __t412;
				_t = _t.getNextSibling();
			}
			else {
				break _loop413;
			}
			
		} while (true);
		}
		_retTree = _t;
		return step;
	}
	
	public final Expression  literalExpr(AST _t,
		PathExpr path
	) throws RecognitionException, XPathException {
		Expression step;
		
		org.exist.xquery.parser.XQueryAST literalExpr_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		org.exist.xquery.parser.XQueryAST c = null;
		org.exist.xquery.parser.XQueryAST i = null;
		org.exist.xquery.parser.XQueryAST dec = null;
		org.exist.xquery.parser.XQueryAST dbl = null;
		step= null;
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case STRING_LITERAL:
		{
			c = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,STRING_LITERAL);
			_t = _t.getNextSibling();
			
					StringValue val = new StringValue(c.getText());
					val.expand();
			step= new LiteralValue(context, val);
			step.setASTNode(c);
			
			break;
		}
		case INTEGER_LITERAL:
		{
			i = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,INTEGER_LITERAL);
			_t = _t.getNextSibling();
			
			step= new LiteralValue(context, new IntegerValue(i.getText()));
			step.setASTNode(i);
			
			break;
		}
		case DOUBLE_LITERAL:
		case DECIMAL_LITERAL:
		{
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case DECIMAL_LITERAL:
			{
				dec = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,DECIMAL_LITERAL);
				_t = _t.getNextSibling();
				
				step= new LiteralValue(context, new DecimalValue(dec.getText()));
				step.setASTNode(dec);
				
				break;
			}
			case DOUBLE_LITERAL:
			{
				dbl = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,DOUBLE_LITERAL);
				_t = _t.getNextSibling();
				
				step= new LiteralValue(context, 
				new DoubleValue(Double.parseDouble(dbl.getText())));
				step.setASTNode(dbl);
				
				break;
			}
			default:
			{
				throw new NoViableAltException(_t);
			}
			}
			}
			break;
		}
		default:
		{
			throw new NoViableAltException(_t);
		}
		}
		_retTree = _t;
		return step;
	}
	
	public final Expression  functionCall(AST _t,
		PathExpr path
	) throws RecognitionException, PermissionDeniedException,EXistException,XPathException {
		Expression step;
		
		org.exist.xquery.parser.XQueryAST functionCall_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		org.exist.xquery.parser.XQueryAST fn = null;
		
			PathExpr pathExpr;
			step= null;
		
		
		AST __t417 = _t;
		fn = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
		match(_t,FUNCTION);
		_t = _t.getFirstChild();
		List params= new ArrayList(2);
		{
		_loop419:
		do {
			if (_t==null) _t=ASTNULL;
			if ((_tokenSet_0.member(_t.getType()))) {
				pathExpr= new PathExpr(context);
				expr(_t,pathExpr);
				_t = _retTree;
				params.add(pathExpr);
			}
			else {
				break _loop419;
			}
			
		} while (true);
		}
		_t = __t417;
		_t = _t.getNextSibling();
		step= FunctionFactory.createFunction(context, fn, path, params);
		_retTree = _t;
		return step;
	}
	
	public final int  forwardAxis(AST _t) throws RecognitionException, PermissionDeniedException,EXistException {
		int axis;
		
		org.exist.xquery.parser.XQueryAST forwardAxis_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		axis= -1;
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case LITERAL_child:
		{
			org.exist.xquery.parser.XQueryAST tmp71_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_child);
			_t = _t.getNextSibling();
			axis= Constants.CHILD_AXIS;
			break;
		}
		case LITERAL_attribute:
		{
			org.exist.xquery.parser.XQueryAST tmp72_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_attribute);
			_t = _t.getNextSibling();
			axis= Constants.ATTRIBUTE_AXIS;
			break;
		}
		case LITERAL_self:
		{
			org.exist.xquery.parser.XQueryAST tmp73_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_self);
			_t = _t.getNextSibling();
			axis= Constants.SELF_AXIS;
			break;
		}
		case LITERAL_parent:
		{
			org.exist.xquery.parser.XQueryAST tmp74_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_parent);
			_t = _t.getNextSibling();
			axis= Constants.PARENT_AXIS;
			break;
		}
		case LITERAL_descendant:
		{
			org.exist.xquery.parser.XQueryAST tmp75_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_descendant);
			_t = _t.getNextSibling();
			axis= Constants.DESCENDANT_AXIS;
			break;
		}
		case 137:
		{
			org.exist.xquery.parser.XQueryAST tmp76_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,137);
			_t = _t.getNextSibling();
			axis= Constants.DESCENDANT_SELF_AXIS;
			break;
		}
		case 138:
		{
			org.exist.xquery.parser.XQueryAST tmp77_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,138);
			_t = _t.getNextSibling();
			axis= Constants.FOLLOWING_SIBLING_AXIS;
			break;
		}
		case LITERAL_following:
		{
			org.exist.xquery.parser.XQueryAST tmp78_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_following);
			_t = _t.getNextSibling();
			axis= Constants.FOLLOWING_AXIS;
			break;
		}
		case 143:
		{
			org.exist.xquery.parser.XQueryAST tmp79_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,143);
			_t = _t.getNextSibling();
			axis= Constants.PRECEDING_SIBLING_AXIS;
			break;
		}
		case LITERAL_preceding:
		{
			org.exist.xquery.parser.XQueryAST tmp80_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_preceding);
			_t = _t.getNextSibling();
			axis= Constants.PRECEDING_AXIS;
			break;
		}
		case LITERAL_ancestor:
		{
			org.exist.xquery.parser.XQueryAST tmp81_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_ancestor);
			_t = _t.getNextSibling();
			axis= Constants.ANCESTOR_AXIS;
			break;
		}
		case 142:
		{
			org.exist.xquery.parser.XQueryAST tmp82_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,142);
			_t = _t.getNextSibling();
			axis= Constants.ANCESTOR_SELF_AXIS;
			break;
		}
		default:
		{
			throw new NoViableAltException(_t);
		}
		}
		_retTree = _t;
		return axis;
	}
	
	public final void predicate(AST _t,
		LocationStep step
	) throws RecognitionException, PermissionDeniedException,EXistException,XPathException {
		
		org.exist.xquery.parser.XQueryAST predicate_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		
		AST __t415 = _t;
		org.exist.xquery.parser.XQueryAST tmp83_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
		match(_t,PREDICATE);
		_t = _t.getFirstChild();
		Predicate predicateExpr= new Predicate(context);
		expr(_t,predicateExpr);
		_t = _retTree;
		step.addPredicate(predicateExpr);
		_t = __t415;
		_t = _t.getNextSibling();
		_retTree = _t;
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
		"MODULE_DECL",
		"ATTRIBUTE_TEST",
		"COMP_ELEM_CONSTRUCTOR",
		"COMP_ATTR_CONSTRUCTOR",
		"COMP_TEXT_CONSTRUCTOR",
		"COMP_COMMENT_CONSTRUCTOR",
		"COMP_PI_CONSTRUCTOR",
		"COMP_NS_CONSTRUCTOR",
		"\"xpointer\"",
		"'('",
		"')'",
		"NCNAME",
		"\"module\"",
		"\"namespace\"",
		"EQ",
		"STRING_LITERAL",
		"SEMICOLON",
		"\"xquery\"",
		"\"version\"",
		"\"declare\"",
		"\"default\"",
		"\"function\"",
		"\"variable\"",
		"\"element\"",
		"DOLLAR",
		"LCURLY",
		"RCURLY",
		"\"import\"",
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
		"COMP_DOC_CONSTRUCTOR",
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
	
	private static final long[] mk_tokenSet_0() {
		long[] data = { 1442915454462623632L, -5765733423243055566L, 403177459L, 0L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_0 = new BitSet(mk_tokenSet_0());
	private static final long[] mk_tokenSet_1() {
		long[] data = { 1152925765217026048L, 0L, 268435459L, 0L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_1 = new BitSet(mk_tokenSet_1());
	}
	
