// $ANTLR 2.7.2: "XPathParser2.g" -> "XPathTreeParser2.java"$

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


public class XPathTreeParser2 extends antlr.TreeParser       implements XPathParser2TokenTypes
 {

	private StaticContext context;
	protected ArrayList exceptions= new ArrayList(2);
	protected boolean foundError= false;

	public XPathTreeParser2(StaticContext context) {
		this();
		this.context= context;
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
public XPathTreeParser2() {
	tokenNames = _tokenNames;
}

	public final void xpointer(AST _t,
		PathExpr path
	) throws RecognitionException {
		
		AST xpointer_AST_in = (AST)_t;
		AST nc = null;
		Expression step = null;
		
		try {      // for error handling
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case XPOINTER:
			{
				AST __t232 = _t;
				AST tmp1_AST_in = (AST)_t;
				match(_t,XPOINTER);
				_t = _t.getFirstChild();
				step=expr(_t,path);
				_t = _retTree;
				_t = __t232;
				_t = _t.getNextSibling();
				break;
			}
			case XPOINTER_ID:
			{
				AST __t233 = _t;
				AST tmp2_AST_in = (AST)_t;
				match(_t,XPOINTER_ID);
				_t = _t.getFirstChild();
				nc = (AST)_t;
				match(_t,NCNAME);
				_t = _t.getNextSibling();
				_t = __t233;
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
		
		AST expr_AST_in = (AST)_t;
		AST t = null;
		AST someVarName = null;
		AST everyVarName = null;
		AST varName = null;
		AST posVar = null;
		AST letVarName = null;
		
			step= null;
		
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case LITERAL_cast:
		{
			AST __t274 = _t;
			AST tmp3_AST_in = (AST)_t;
			match(_t,LITERAL_cast);
			_t = _t.getFirstChild();
			
						PathExpr expr= new PathExpr(context);
						int cardinality= Cardinality.EXACTLY_ONE;
					
			step=expr(_t,expr);
			_t = _retTree;
			t = (AST)_t;
			match(_t,ATOMIC_TYPE);
			_t = _t.getNextSibling();
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case QUESTION:
			{
				AST tmp4_AST_in = (AST)_t;
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
						path.add(castExpr);
						step = castExpr;
					
			_t = __t274;
			_t = _t.getNextSibling();
			break;
		}
		case COMMA:
		{
			AST __t276 = _t;
			AST tmp5_AST_in = (AST)_t;
			match(_t,COMMA);
			_t = _t.getFirstChild();
			
						PathExpr left= new PathExpr(context);
						PathExpr right= new PathExpr(context);
					
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						SequenceConstructor sc= new SequenceConstructor(context);
						sc.addExpression(left);
						sc.addExpression(right);
						path.add(sc);
						step = sc;
					
			_t = __t276;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_if:
		{
			AST __t277 = _t;
			AST tmp6_AST_in = (AST)_t;
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
					
			_t = __t277;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_some:
		{
			AST __t278 = _t;
			AST tmp7_AST_in = (AST)_t;
			match(_t,LITERAL_some);
			_t = _t.getFirstChild();
			
						List clauses= new ArrayList();
						PathExpr satisfiesExpr = new PathExpr(context);
					
			{
			_loop283:
			do {
				if (_t==null) _t=ASTNULL;
				if ((_t.getType()==VARIABLE_BINDING)) {
					AST __t280 = _t;
					someVarName = _t==ASTNULL ? null :(AST)_t;
					match(_t,VARIABLE_BINDING);
					_t = _t.getFirstChild();
					
										ForLetClause clause= new ForLetClause();
										PathExpr inputSequence = new PathExpr(context);
									
					{
					if (_t==null) _t=ASTNULL;
					switch ( _t.getType()) {
					case LITERAL_as:
					{
						AST __t282 = _t;
						AST tmp8_AST_in = (AST)_t;
						match(_t,LITERAL_as);
						_t = _t.getFirstChild();
						sequenceType(_t,clause.sequenceType);
						_t = _retTree;
						_t = __t282;
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
					case NCNAME:
					case STRING_LITERAL:
					case EQ:
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
					case XML_PI:
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
									
					_t = __t280;
					_t = _t.getNextSibling();
				}
				else {
					break _loop283;
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
					
			_t = __t278;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_every:
		{
			AST __t284 = _t;
			AST tmp9_AST_in = (AST)_t;
			match(_t,LITERAL_every);
			_t = _t.getFirstChild();
			
						List clauses= new ArrayList();
						PathExpr satisfiesExpr = new PathExpr(context);
					
			{
			_loop289:
			do {
				if (_t==null) _t=ASTNULL;
				if ((_t.getType()==VARIABLE_BINDING)) {
					AST __t286 = _t;
					everyVarName = _t==ASTNULL ? null :(AST)_t;
					match(_t,VARIABLE_BINDING);
					_t = _t.getFirstChild();
					
										ForLetClause clause= new ForLetClause();
										PathExpr inputSequence = new PathExpr(context);
									
					{
					if (_t==null) _t=ASTNULL;
					switch ( _t.getType()) {
					case LITERAL_as:
					{
						AST __t288 = _t;
						AST tmp10_AST_in = (AST)_t;
						match(_t,LITERAL_as);
						_t = _t.getFirstChild();
						sequenceType(_t,clause.sequenceType);
						_t = _retTree;
						_t = __t288;
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
					case NCNAME:
					case STRING_LITERAL:
					case EQ:
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
					case XML_PI:
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
									
					_t = __t286;
					_t = _t.getNextSibling();
				}
				else {
					break _loop289;
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
					
			_t = __t284;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_return:
		{
			AST __t290 = _t;
			AST tmp11_AST_in = (AST)_t;
			match(_t,LITERAL_return);
			_t = _t.getFirstChild();
			
						List clauses= new ArrayList();
						Expression action= new PathExpr(context);
						PathExpr whereExpr= null;
						List orderBy= null;
					
			{
			int _cnt305=0;
			_loop305:
			do {
				if (_t==null) _t=ASTNULL;
				switch ( _t.getType()) {
				case LITERAL_for:
				{
					AST __t292 = _t;
					AST tmp12_AST_in = (AST)_t;
					match(_t,LITERAL_for);
					_t = _t.getFirstChild();
					{
					int _cnt298=0;
					_loop298:
					do {
						if (_t==null) _t=ASTNULL;
						if ((_t.getType()==VARIABLE_BINDING)) {
							AST __t294 = _t;
							varName = _t==ASTNULL ? null :(AST)_t;
							match(_t,VARIABLE_BINDING);
							_t = _t.getFirstChild();
							
														ForLetClause clause= new ForLetClause();
														PathExpr inputSequence= new PathExpr(context);
													
							{
							if (_t==null) _t=ASTNULL;
							switch ( _t.getType()) {
							case LITERAL_as:
							{
								AST __t296 = _t;
								AST tmp13_AST_in = (AST)_t;
								match(_t,LITERAL_as);
								_t = _t.getFirstChild();
								clause.sequenceType= new SequenceType();
								sequenceType(_t,clause.sequenceType);
								_t = _retTree;
								_t = __t296;
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
							case NCNAME:
							case STRING_LITERAL:
							case EQ:
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
							case XML_PI:
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
								posVar = (AST)_t;
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
							case NCNAME:
							case STRING_LITERAL:
							case EQ:
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
							case XML_PI:
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
													
							_t = __t294;
							_t = _t.getNextSibling();
						}
						else {
							if ( _cnt298>=1 ) { break _loop298; } else {throw new NoViableAltException(_t);}
						}
						
						_cnt298++;
					} while (true);
					}
					_t = __t292;
					_t = _t.getNextSibling();
					break;
				}
				case LITERAL_let:
				{
					AST __t299 = _t;
					AST tmp14_AST_in = (AST)_t;
					match(_t,LITERAL_let);
					_t = _t.getFirstChild();
					{
					int _cnt304=0;
					_loop304:
					do {
						if (_t==null) _t=ASTNULL;
						if ((_t.getType()==VARIABLE_BINDING)) {
							AST __t301 = _t;
							letVarName = _t==ASTNULL ? null :(AST)_t;
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
								AST __t303 = _t;
								AST tmp15_AST_in = (AST)_t;
								match(_t,LITERAL_as);
								_t = _t.getFirstChild();
								clause.sequenceType= new SequenceType();
								sequenceType(_t,clause.sequenceType);
								_t = _retTree;
								_t = __t303;
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
							case NCNAME:
							case STRING_LITERAL:
							case EQ:
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
							case XML_PI:
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
													
							_t = __t301;
							_t = _t.getNextSibling();
						}
						else {
							if ( _cnt304>=1 ) { break _loop304; } else {throw new NoViableAltException(_t);}
						}
						
						_cnt304++;
					} while (true);
					}
					_t = __t299;
					_t = _t.getNextSibling();
					break;
				}
				default:
				{
					if ( _cnt305>=1 ) { break _loop305; } else {throw new NoViableAltException(_t);}
				}
				}
				_cnt305++;
			} while (true);
			}
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case LITERAL_where:
			{
				AST tmp16_AST_in = (AST)_t;
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
			case NCNAME:
			case STRING_LITERAL:
			case EQ:
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
			case XML_PI:
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
				AST __t308 = _t;
				AST tmp17_AST_in = (AST)_t;
				match(_t,ORDER_BY);
				_t = _t.getFirstChild();
				orderBy= new ArrayList(3);
				{
				int _cnt314=0;
				_loop314:
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
								AST tmp18_AST_in = (AST)_t;
								match(_t,LITERAL_ascending);
								_t = _t.getNextSibling();
								break;
							}
							case LITERAL_descending:
							{
								AST tmp19_AST_in = (AST)_t;
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
						case NCNAME:
						case STRING_LITERAL:
						case EQ:
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
						case XML_PI:
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
							AST tmp20_AST_in = (AST)_t;
							match(_t,LITERAL_empty);
							_t = _t.getNextSibling();
							{
							if (_t==null) _t=ASTNULL;
							switch ( _t.getType()) {
							case LITERAL_greatest:
							{
								AST tmp21_AST_in = (AST)_t;
								match(_t,LITERAL_greatest);
								_t = _t.getNextSibling();
								break;
							}
							case LITERAL_least:
							{
								AST tmp22_AST_in = (AST)_t;
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
						case NCNAME:
						case STRING_LITERAL:
						case EQ:
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
						case XML_PI:
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
						if ( _cnt314>=1 ) { break _loop314; } else {throw new NoViableAltException(_t);}
					}
					
					_cnt314++;
				} while (true);
				}
				_t = __t308;
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
			case NCNAME:
			case STRING_LITERAL:
			case EQ:
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
			case XML_PI:
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
							if (orderBy != null) {
								OrderSpec orderSpecs[]= new OrderSpec[orderBy.size()];
								int k= 0;
								for (Iterator j= orderBy.iterator(); j.hasNext(); k++) {
									OrderSpec orderSpec= (OrderSpec) j.next();
									orderSpecs[k]= orderSpec;
								}
								expr.setOrderSpecs(orderSpecs);
								orderBy = null;
							}
							action= expr;
						}
						path.add(action);
						step = action;
					
			_t = __t290;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_or:
		{
			AST __t315 = _t;
			AST tmp23_AST_in = (AST)_t;
			match(_t,LITERAL_or);
			_t = _t.getFirstChild();
			
						PathExpr left= new PathExpr(context);
						PathExpr right= new PathExpr(context);
					
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			_t = __t315;
			_t = _t.getNextSibling();
			
					OpOr or= new OpOr(context);
					or.add(left);
					or.add(right);
					path.addPath(or);
					step = or;
				
			break;
		}
		case LITERAL_and:
		{
			AST __t316 = _t;
			AST tmp24_AST_in = (AST)_t;
			match(_t,LITERAL_and);
			_t = _t.getFirstChild();
			
						PathExpr left= new PathExpr(context);
						PathExpr right= new PathExpr(context);
					
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			_t = __t316;
			_t = _t.getNextSibling();
			
					OpAnd and= new OpAnd(context);
					and.add(left);
					and.add(right);
					path.addPath(and);
					step = and;
				
			break;
		}
		case UNION:
		{
			AST __t317 = _t;
			AST tmp25_AST_in = (AST)_t;
			match(_t,UNION);
			_t = _t.getFirstChild();
			
						PathExpr left= new PathExpr(context);
						PathExpr right= new PathExpr(context);
					
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			_t = __t317;
			_t = _t.getNextSibling();
			
					Union union= new Union(context, left, right);
					path.add(union);
					step = union;
				
			break;
		}
		case LITERAL_intersect:
		{
			AST __t318 = _t;
			AST tmp26_AST_in = (AST)_t;
			match(_t,LITERAL_intersect);
			_t = _t.getFirstChild();
			
						PathExpr left = new PathExpr(context);
						PathExpr right = new PathExpr(context);
					
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			_t = __t318;
			_t = _t.getNextSibling();
			
					Intersection intersect = new Intersection(context, left, right);
					path.add(intersect);
					step = intersect;
				
			break;
		}
		case LITERAL_except:
		{
			AST __t319 = _t;
			AST tmp27_AST_in = (AST)_t;
			match(_t,LITERAL_except);
			_t = _t.getFirstChild();
			
						PathExpr left = new PathExpr(context);
						PathExpr right = new PathExpr(context);
					
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			_t = __t319;
			_t = _t.getNextSibling();
			
					Except intersect = new Except(context, left, right);
					path.add(intersect);
					step = intersect;
				
			break;
		}
		case ABSOLUTE_SLASH:
		{
			AST __t320 = _t;
			AST tmp28_AST_in = (AST)_t;
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
			case NCNAME:
			case STRING_LITERAL:
			case EQ:
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
			case XML_PI:
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
			_t = __t320;
			_t = _t.getNextSibling();
			break;
		}
		case ABSOLUTE_DSLASH:
		{
			AST __t322 = _t;
			AST tmp29_AST_in = (AST)_t;
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
			case NCNAME:
			case STRING_LITERAL:
			case EQ:
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
			case XML_PI:
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
			_t = __t322;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_to:
		{
			AST __t324 = _t;
			AST tmp30_AST_in = (AST)_t;
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
					
			_t = __t324;
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
		case STRING_LITERAL:
		case LCURLY:
		case XML_COMMENT:
		case DOUBLE_LITERAL:
		case DECIMAL_LITERAL:
		case INTEGER_LITERAL:
		case XML_PI:
		{
			step=primaryExpr(_t,path);
			_t = _retTree;
			break;
		}
		case QNAME:
		case WILDCARD:
		case PREFIX_WILDCARD:
		case NCNAME:
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
		case 120:
		case 121:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 124:
		case 125:
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
		
		AST xpath_AST_in = (AST)_t;
		
		try {      // for error handling
			module(_t,path);
			_t = _retTree;
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
		
		AST module_AST_in = (AST)_t;
		Expression step = null;
		
		prolog(_t,path);
		_t = _retTree;
		step=expr(_t,path);
		_t = _retTree;
		_retTree = _t;
	}
	
	public final void prolog(AST _t,
		PathExpr path
	) throws RecognitionException, PermissionDeniedException,EXistException,XPathException {
		
		AST prolog_AST_in = (AST)_t;
		AST v = null;
		AST prefix = null;
		AST uri = null;
		AST defu = null;
		AST deff = null;
		AST qname = null;
		Expression step = null;
		
		{
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case VERSION_DECL:
		{
			AST __t238 = _t;
			v = _t==ASTNULL ? null :(AST)_t;
			match(_t,VERSION_DECL);
			_t = _t.getFirstChild();
			
							if (!v.getText().equals("1.0"))
								throw new XPathException("Wrong XQuery version: require 1.0");
						
			_t = __t238;
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
		case NAMESPACE_DECL:
		case DEF_NAMESPACE_DECL:
		case DEF_FUNCTION_NS_DECL:
		case GLOBAL_VAR:
		case FUNCTION_DECL:
		case BEFORE:
		case AFTER:
		case NCNAME:
		case STRING_LITERAL:
		case EQ:
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
		case XML_PI:
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
		_loop246:
		do {
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case NAMESPACE_DECL:
			{
				AST __t240 = _t;
				prefix = _t==ASTNULL ? null :(AST)_t;
				match(_t,NAMESPACE_DECL);
				_t = _t.getFirstChild();
				uri = (AST)_t;
				match(_t,STRING_LITERAL);
				_t = _t.getNextSibling();
				context.declareNamespace(prefix.getText(), uri.getText());
				_t = __t240;
				_t = _t.getNextSibling();
				break;
			}
			case DEF_NAMESPACE_DECL:
			{
				AST __t241 = _t;
				AST tmp31_AST_in = (AST)_t;
				match(_t,DEF_NAMESPACE_DECL);
				_t = _t.getFirstChild();
				defu = (AST)_t;
				match(_t,STRING_LITERAL);
				_t = _t.getNextSibling();
				context.declareNamespace("", defu.getText());
				_t = __t241;
				_t = _t.getNextSibling();
				break;
			}
			case DEF_FUNCTION_NS_DECL:
			{
				AST __t242 = _t;
				AST tmp32_AST_in = (AST)_t;
				match(_t,DEF_FUNCTION_NS_DECL);
				_t = _t.getFirstChild();
				deff = (AST)_t;
				match(_t,STRING_LITERAL);
				_t = _t.getNextSibling();
				context.setDefaultFunctionNamespace(deff.getText());
				_t = __t242;
				_t = _t.getNextSibling();
				break;
			}
			case GLOBAL_VAR:
			{
				AST __t243 = _t;
				qname = _t==ASTNULL ? null :(AST)_t;
				match(_t,GLOBAL_VAR);
				_t = _t.getFirstChild();
				
								PathExpr enclosed= new PathExpr(context);
								SequenceType type= null;
							
				{
				if (_t==null) _t=ASTNULL;
				switch ( _t.getType()) {
				case LITERAL_as:
				{
					AST __t245 = _t;
					AST tmp33_AST_in = (AST)_t;
					match(_t,LITERAL_as);
					_t = _t.getFirstChild();
					type= new SequenceType();
					sequenceType(_t,type);
					_t = _retTree;
					_t = __t245;
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
				case NCNAME:
				case STRING_LITERAL:
				case EQ:
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
				case XML_PI:
				{
					break;
				}
				default:
				{
					throw new NoViableAltException(_t);
				}
				}
				}
				step=expr(_t,enclosed);
				_t = _retTree;
				
								VariableDeclaration decl= new VariableDeclaration(context, qname.getText(), enclosed);
								decl.setSequenceType(type);
								path.add(decl);
							
				_t = __t243;
				_t = _t.getNextSibling();
				break;
			}
			case FUNCTION_DECL:
			{
				functionDecl(_t,path);
				_t = _retTree;
				break;
			}
			default:
			{
				break _loop246;
			}
			}
		} while (true);
		}
		_retTree = _t;
	}
	
	public final void sequenceType(AST _t,
		SequenceType type
	) throws RecognitionException, XPathException {
		
		AST sequenceType_AST_in = (AST)_t;
		AST t = null;
		
		{
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case ATOMIC_TYPE:
		{
			AST __t262 = _t;
			t = _t==ASTNULL ? null :(AST)_t;
			match(_t,ATOMIC_TYPE);
			_t = _t.getFirstChild();
			
							QName qn= QName.parse(context, t.getText());
							int code= Type.getType(qn);
							type.setPrimaryType(code);
						
			_t = __t262;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_empty:
		{
			AST __t263 = _t;
			AST tmp34_AST_in = (AST)_t;
			match(_t,LITERAL_empty);
			_t = _t.getFirstChild();
			
							type.setPrimaryType(Type.EMPTY);
							type.setCardinality(Cardinality.EMPTY);
						
			_t = __t263;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_item:
		{
			AST __t264 = _t;
			AST tmp35_AST_in = (AST)_t;
			match(_t,LITERAL_item);
			_t = _t.getFirstChild();
			type.setPrimaryType(Type.ITEM);
			_t = __t264;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_node:
		{
			AST __t265 = _t;
			AST tmp36_AST_in = (AST)_t;
			match(_t,LITERAL_node);
			_t = _t.getFirstChild();
			type.setPrimaryType(Type.NODE);
			_t = __t265;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_element:
		{
			AST __t266 = _t;
			AST tmp37_AST_in = (AST)_t;
			match(_t,LITERAL_element);
			_t = _t.getFirstChild();
			type.setPrimaryType(Type.ELEMENT);
			_t = __t266;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_attribute:
		{
			AST __t267 = _t;
			AST tmp38_AST_in = (AST)_t;
			match(_t,LITERAL_attribute);
			_t = _t.getFirstChild();
			type.setPrimaryType(Type.ATTRIBUTE);
			_t = __t267;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_text:
		{
			AST __t268 = _t;
			AST tmp39_AST_in = (AST)_t;
			match(_t,LITERAL_text);
			_t = _t.getFirstChild();
			type.setPrimaryType(Type.ITEM);
			_t = __t268;
			_t = _t.getNextSibling();
			break;
		}
		case 130:
		{
			AST __t269 = _t;
			AST tmp40_AST_in = (AST)_t;
			match(_t,130);
			_t = _t.getFirstChild();
			type.setPrimaryType(Type.PROCESSING_INSTRUCTION);
			_t = __t269;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_comment:
		{
			AST __t270 = _t;
			AST tmp41_AST_in = (AST)_t;
			match(_t,LITERAL_comment);
			_t = _t.getFirstChild();
			type.setPrimaryType(Type.COMMENT);
			_t = __t270;
			_t = _t.getNextSibling();
			break;
		}
		case 131:
		{
			AST __t271 = _t;
			AST tmp42_AST_in = (AST)_t;
			match(_t,131);
			_t = _t.getFirstChild();
			type.setPrimaryType(Type.DOCUMENT);
			_t = __t271;
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
			AST tmp43_AST_in = (AST)_t;
			match(_t,STAR);
			_t = _t.getNextSibling();
			type.setCardinality(Cardinality.ZERO_OR_MORE);
			break;
		}
		case PLUS:
		{
			AST tmp44_AST_in = (AST)_t;
			match(_t,PLUS);
			_t = _t.getNextSibling();
			type.setCardinality(Cardinality.ONE_OR_MORE);
			break;
		}
		case QUESTION:
		{
			AST tmp45_AST_in = (AST)_t;
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
		
		AST functionDecl_AST_in = (AST)_t;
		AST name = null;
		Expression step = null;
		
		AST __t248 = _t;
		name = _t==ASTNULL ? null :(AST)_t;
		match(_t,FUNCTION_DECL);
		_t = _t.getFirstChild();
		PathExpr body= new PathExpr(context);
		
					QName qn= QName.parse(context, name.getText());
					FunctionSignature signature= new FunctionSignature(qn);
					UserDefinedFunction func= new UserDefinedFunction(context, signature);
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
				
		{
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case LITERAL_as:
		{
			AST __t251 = _t;
			AST tmp46_AST_in = (AST)_t;
			match(_t,LITERAL_as);
			_t = _t.getFirstChild();
			SequenceType type= new SequenceType();
			sequenceType(_t,type);
			_t = _retTree;
			signature.setReturnType(type);
			_t = __t251;
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
		AST __t252 = _t;
		AST tmp47_AST_in = (AST)_t;
		match(_t,LCURLY);
		_t = _t.getFirstChild();
		step=expr(_t,body);
		_t = _retTree;
		func.setFunctionBody(body);
		_t = __t252;
		_t = _t.getNextSibling();
		_t = __t248;
		_t = _t.getNextSibling();
		_retTree = _t;
	}
	
	public final void paramList(AST _t,
		List vars
	) throws RecognitionException, XPathException {
		
		AST paramList_AST_in = (AST)_t;
		
		param(_t,vars);
		_t = _retTree;
		{
		_loop255:
		do {
			if (_t==null) _t=ASTNULL;
			if ((_t.getType()==VARIABLE_BINDING)) {
				param(_t,vars);
				_t = _retTree;
			}
			else {
				break _loop255;
			}
			
		} while (true);
		}
		_retTree = _t;
	}
	
	public final void param(AST _t,
		List vars
	) throws RecognitionException, XPathException {
		
		AST param_AST_in = (AST)_t;
		AST varname = null;
		
		AST __t257 = _t;
		varname = _t==ASTNULL ? null :(AST)_t;
		match(_t,VARIABLE_BINDING);
		_t = _t.getFirstChild();
		
					FunctionParameter var= new FunctionParameter(varname.getText());
					vars.add(var);
				
		{
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case LITERAL_as:
		{
			AST __t259 = _t;
			AST tmp48_AST_in = (AST)_t;
			match(_t,LITERAL_as);
			_t = _t.getFirstChild();
			SequenceType type= new SequenceType();
			sequenceType(_t,type);
			_t = _retTree;
			_t = __t259;
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
		_t = __t257;
		_t = _t.getNextSibling();
		_retTree = _t;
	}
	
	public final Expression  generalComp(AST _t,
		PathExpr path
	) throws RecognitionException, PermissionDeniedException,EXistException,XPathException {
		Expression step;
		
		AST generalComp_AST_in = (AST)_t;
		
			step= null;
			PathExpr left= new PathExpr(context);
			PathExpr right= new PathExpr(context);
		
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case EQ:
		{
			AST __t381 = _t;
			AST tmp49_AST_in = (AST)_t;
			match(_t,EQ);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						step= new GeneralComparison(context, left, right, Constants.EQ);
						path.add(step);
					
			_t = __t381;
			_t = _t.getNextSibling();
			break;
		}
		case NEQ:
		{
			AST __t382 = _t;
			AST tmp50_AST_in = (AST)_t;
			match(_t,NEQ);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						step= new GeneralComparison(context, left, right, Constants.NEQ);
						path.add(step);
					
			_t = __t382;
			_t = _t.getNextSibling();
			break;
		}
		case LT:
		{
			AST __t383 = _t;
			AST tmp51_AST_in = (AST)_t;
			match(_t,LT);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						step= new GeneralComparison(context, left, right, Constants.LT);
						path.add(step);
					
			_t = __t383;
			_t = _t.getNextSibling();
			break;
		}
		case LTEQ:
		{
			AST __t384 = _t;
			AST tmp52_AST_in = (AST)_t;
			match(_t,LTEQ);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						step= new GeneralComparison(context, left, right, Constants.LTEQ);
						path.add(step);
					
			_t = __t384;
			_t = _t.getNextSibling();
			break;
		}
		case GT:
		{
			AST __t385 = _t;
			AST tmp53_AST_in = (AST)_t;
			match(_t,GT);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						step= new GeneralComparison(context, left, right, Constants.GT);
						path.add(step);
					
			_t = __t385;
			_t = _t.getNextSibling();
			break;
		}
		case GTEQ:
		{
			AST __t386 = _t;
			AST tmp54_AST_in = (AST)_t;
			match(_t,GTEQ);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						step= new GeneralComparison(context, left, right, Constants.GTEQ);
						path.add(step);
					
			_t = __t386;
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
		
		AST valueComp_AST_in = (AST)_t;
		
			step= null;
			PathExpr left= new PathExpr(context);
			PathExpr right= new PathExpr(context);
		
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case LITERAL_eq:
		{
			AST __t374 = _t;
			AST tmp55_AST_in = (AST)_t;
			match(_t,LITERAL_eq);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						step= new ValueComparison(context, left, right, Constants.EQ);
						path.add(step);
					
			_t = __t374;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_ne:
		{
			AST __t375 = _t;
			AST tmp56_AST_in = (AST)_t;
			match(_t,LITERAL_ne);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						step= new ValueComparison(context, left, right, Constants.NEQ);
						path.add(step);
					
			_t = __t375;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_lt:
		{
			AST __t376 = _t;
			AST tmp57_AST_in = (AST)_t;
			match(_t,LITERAL_lt);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						step= new ValueComparison(context, left, right, Constants.LT);
						path.add(step);
					
			_t = __t376;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_le:
		{
			AST __t377 = _t;
			AST tmp58_AST_in = (AST)_t;
			match(_t,LITERAL_le);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						step= new ValueComparison(context, left, right, Constants.LTEQ);
						path.add(step);
					
			_t = __t377;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_gt:
		{
			AST __t378 = _t;
			AST tmp59_AST_in = (AST)_t;
			match(_t,LITERAL_gt);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						step= new ValueComparison(context, left, right, Constants.GT);
						path.add(step);
					
			_t = __t378;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_ge:
		{
			AST __t379 = _t;
			AST tmp60_AST_in = (AST)_t;
			match(_t,LITERAL_ge);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						step= new ValueComparison(context, left, right, Constants.GTEQ);
						path.add(step);
					
			_t = __t379;
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
		
		AST nodeComp_AST_in = (AST)_t;
		
			step= null;
			PathExpr left= new PathExpr(context);
			PathExpr right= new PathExpr(context);
		
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case LITERAL_is:
		{
			AST __t388 = _t;
			AST tmp61_AST_in = (AST)_t;
			match(_t,LITERAL_is);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						step = new NodeComparison(context, left, right, Constants.IS);
						path.add(step);
					
			_t = __t388;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_isnot:
		{
			AST __t389 = _t;
			AST tmp62_AST_in = (AST)_t;
			match(_t,LITERAL_isnot);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						step = new NodeComparison(context, left, right, Constants.ISNOT);
						path.add(step);
					
			_t = __t389;
			_t = _t.getNextSibling();
			break;
		}
		case BEFORE:
		{
			AST __t390 = _t;
			AST tmp63_AST_in = (AST)_t;
			match(_t,BEFORE);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						step = new NodeComparison(context, left, right, Constants.BEFORE);
						path.add(step);
					
			_t = __t390;
			_t = _t.getNextSibling();
			break;
		}
		case AFTER:
		{
			AST __t391 = _t;
			AST tmp64_AST_in = (AST)_t;
			match(_t,AFTER);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						step = new NodeComparison(context, left, right, Constants.AFTER);
						path.add(step);
					
			_t = __t391;
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
		
		AST fulltextComp_AST_in = (AST)_t;
		
			step= null;
			PathExpr nodes= new PathExpr(context);
			PathExpr query= new PathExpr(context);
		
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case ANDEQ:
		{
			AST __t371 = _t;
			AST tmp65_AST_in = (AST)_t;
			match(_t,ANDEQ);
			_t = _t.getFirstChild();
			step=expr(_t,nodes);
			_t = _retTree;
			step=expr(_t,query);
			_t = _retTree;
			_t = __t371;
			_t = _t.getNextSibling();
			
					ExtFulltext exprCont= new ExtFulltext(context, Constants.FULLTEXT_AND);
					exprCont.setPath(nodes);
					exprCont.addTerm(query);
					path.addPath(exprCont);
				
			break;
		}
		case OREQ:
		{
			AST __t372 = _t;
			AST tmp66_AST_in = (AST)_t;
			match(_t,OREQ);
			_t = _t.getFirstChild();
			step=expr(_t,nodes);
			_t = _retTree;
			step=expr(_t,query);
			_t = _retTree;
			_t = __t372;
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
		
		AST primaryExpr_AST_in = (AST)_t;
		AST v = null;
		
			step = null;
		
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case ELEMENT:
		case TEXT:
		case LCURLY:
		case XML_COMMENT:
		case XML_PI:
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
			AST __t326 = _t;
			AST tmp67_AST_in = (AST)_t;
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
			case NCNAME:
			case STRING_LITERAL:
			case EQ:
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
			case XML_PI:
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
			_t = __t326;
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
			v = (AST)_t;
			match(_t,VARIABLE_REF);
			_t = _t.getNextSibling();
			step= new VariableReference(context, v.getText());
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
		
		AST pathExpr_AST_in = (AST)_t;
		AST qn = null;
		AST nc1 = null;
		AST nc = null;
		AST attr = null;
		AST nc2 = null;
		AST nc3 = null;
		
			Expression rightStep= null;
			step= null;
			int axis= Constants.CHILD_AXIS;
		
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case QNAME:
		case WILDCARD:
		case PREFIX_WILDCARD:
		case NCNAME:
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
		{
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
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
			{
				axis=forwardAxis(_t);
				_t = _retTree;
				break;
			}
			case QNAME:
			case WILDCARD:
			case PREFIX_WILDCARD:
			case NCNAME:
			case LITERAL_text:
			case LITERAL_node:
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
				qn = (AST)_t;
				match(_t,QNAME);
				_t = _t.getNextSibling();
				
							QName qname= QName.parse(context, qn.getText());
							test= new NameTest(Type.ELEMENT, qname);
						
				break;
			}
			case PREFIX_WILDCARD:
			{
				AST __t331 = _t;
				AST tmp68_AST_in = (AST)_t;
				match(_t,PREFIX_WILDCARD);
				_t = _t.getFirstChild();
				nc1 = (AST)_t;
				match(_t,NCNAME);
				_t = _t.getNextSibling();
				_t = __t331;
				_t = _t.getNextSibling();
				
							QName qname= new QName(nc1.getText(), null, null);
							test= new NameTest(Type.ELEMENT, qname);
						
				break;
			}
			case NCNAME:
			{
				AST __t332 = _t;
				nc = _t==ASTNULL ? null :(AST)_t;
				match(_t,NCNAME);
				_t = _t.getFirstChild();
				AST tmp69_AST_in = (AST)_t;
				match(_t,WILDCARD);
				_t = _t.getNextSibling();
				_t = __t332;
				_t = _t.getNextSibling();
				
							String namespaceURI= context.getURIForPrefix(nc.getText());
							QName qname= new QName(null, namespaceURI, null);
							test= new NameTest(Type.ELEMENT, qname);
						
				break;
			}
			case WILDCARD:
			{
				AST tmp70_AST_in = (AST)_t;
				match(_t,WILDCARD);
				_t = _t.getNextSibling();
				test= new TypeTest(Type.ELEMENT);
				break;
			}
			case LITERAL_node:
			{
				AST tmp71_AST_in = (AST)_t;
				match(_t,LITERAL_node);
				_t = _t.getNextSibling();
				test= new AnyNodeTest();
				break;
			}
			case LITERAL_text:
			{
				AST tmp72_AST_in = (AST)_t;
				match(_t,LITERAL_text);
				_t = _t.getNextSibling();
				test= new TypeTest(Type.TEXT);
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
			_loop334:
			do {
				if (_t==null) _t=ASTNULL;
				if ((_t.getType()==PREDICATE)) {
					predicate(_t,(LocationStep) step);
					_t = _retTree;
				}
				else {
					break _loop334;
				}
				
			} while (true);
			}
			break;
		}
		case AT:
		{
			AST tmp73_AST_in = (AST)_t;
			match(_t,AT);
			_t = _t.getNextSibling();
			QName qname= null;
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case QNAME:
			{
				attr = (AST)_t;
				match(_t,QNAME);
				_t = _t.getNextSibling();
				qname= QName.parseAttribute(context, attr.getText());
				break;
			}
			case WILDCARD:
			{
				AST tmp74_AST_in = (AST)_t;
				match(_t,WILDCARD);
				_t = _t.getNextSibling();
				break;
			}
			case PREFIX_WILDCARD:
			{
				AST __t336 = _t;
				AST tmp75_AST_in = (AST)_t;
				match(_t,PREFIX_WILDCARD);
				_t = _t.getFirstChild();
				nc2 = (AST)_t;
				match(_t,NCNAME);
				_t = _t.getNextSibling();
				_t = __t336;
				_t = _t.getNextSibling();
				qname= new QName(nc2.getText(), null, null);
				break;
			}
			case NCNAME:
			{
				AST __t337 = _t;
				nc3 = _t==ASTNULL ? null :(AST)_t;
				match(_t,NCNAME);
				_t = _t.getFirstChild();
				AST tmp76_AST_in = (AST)_t;
				match(_t,WILDCARD);
				_t = _t.getNextSibling();
				_t = __t337;
				_t = _t.getNextSibling();
				
							String namespaceURI= context.getURIForPrefix(nc3.getText());
							if (namespaceURI == null)
								throw new EXistException("No namespace defined for prefix " + nc.getText());
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
			_loop339:
			do {
				if (_t==null) _t=ASTNULL;
				if ((_t.getType()==PREDICATE)) {
					predicate(_t,(LocationStep) step);
					_t = _retTree;
				}
				else {
					break _loop339;
				}
				
			} while (true);
			}
			break;
		}
		case SELF:
		{
			AST tmp77_AST_in = (AST)_t;
			match(_t,SELF);
			_t = _t.getNextSibling();
			
					step= new LocationStep(context, Constants.SELF_AXIS, new TypeTest(Type.NODE));
					path.add(step);
				
			{
			_loop341:
			do {
				if (_t==null) _t=ASTNULL;
				if ((_t.getType()==PREDICATE)) {
					predicate(_t,(LocationStep) step);
					_t = _retTree;
				}
				else {
					break _loop341;
				}
				
			} while (true);
			}
			break;
		}
		case PARENT:
		{
			AST tmp78_AST_in = (AST)_t;
			match(_t,PARENT);
			_t = _t.getNextSibling();
			
					step= new LocationStep(context, Constants.PARENT_AXIS, new TypeTest(Type.NODE));
					path.add(step);
				
			{
			_loop343:
			do {
				if (_t==null) _t=ASTNULL;
				if ((_t.getType()==PREDICATE)) {
					predicate(_t,(LocationStep) step);
					_t = _retTree;
				}
				else {
					break _loop343;
				}
				
			} while (true);
			}
			break;
		}
		case SLASH:
		{
			AST __t344 = _t;
			AST tmp79_AST_in = (AST)_t;
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
			case NCNAME:
			case STRING_LITERAL:
			case EQ:
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
			case XML_PI:
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
			_t = __t344;
			_t = _t.getNextSibling();
			
					if (step instanceof LocationStep && ((LocationStep) step).getAxis() == -1)
						 ((LocationStep) step).setAxis(Constants.CHILD_AXIS);
				
			break;
		}
		case DSLASH:
		{
			AST __t346 = _t;
			AST tmp80_AST_in = (AST)_t;
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
			case NCNAME:
			case STRING_LITERAL:
			case EQ:
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
			case XML_PI:
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
			_t = __t346;
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
		
		AST numericExpr_AST_in = (AST)_t;
		
			step= null;
			PathExpr left= new PathExpr(context);
			PathExpr right= new PathExpr(context);
		
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case PLUS:
		{
			AST __t351 = _t;
			AST tmp81_AST_in = (AST)_t;
			match(_t,PLUS);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			_t = __t351;
			_t = _t.getNextSibling();
			
					OpNumeric op= new OpNumeric(context, left, right, Constants.PLUS);
					path.addPath(op);
					step= op;
				
			break;
		}
		case MINUS:
		{
			AST __t352 = _t;
			AST tmp82_AST_in = (AST)_t;
			match(_t,MINUS);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			_t = __t352;
			_t = _t.getNextSibling();
			
					OpNumeric op= new OpNumeric(context, left, right, Constants.MINUS);
					path.addPath(op);
					step= op;
				
			break;
		}
		case UNARY_MINUS:
		{
			AST __t353 = _t;
			AST tmp83_AST_in = (AST)_t;
			match(_t,UNARY_MINUS);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			_t = __t353;
			_t = _t.getNextSibling();
			
					UnaryExpr unary= new UnaryExpr(context, Constants.MINUS);
					unary.add(left);
					path.addPath(unary);
					step= unary;
				
			break;
		}
		case UNARY_PLUS:
		{
			AST __t354 = _t;
			AST tmp84_AST_in = (AST)_t;
			match(_t,UNARY_PLUS);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			_t = __t354;
			_t = _t.getNextSibling();
			
					UnaryExpr unary= new UnaryExpr(context, Constants.PLUS);
					unary.add(left);
					path.addPath(unary);
					step= unary;
				
			break;
		}
		case LITERAL_div:
		{
			AST __t355 = _t;
			AST tmp85_AST_in = (AST)_t;
			match(_t,LITERAL_div);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			_t = __t355;
			_t = _t.getNextSibling();
			
					OpNumeric op= new OpNumeric(context, left, right, Constants.DIV);
					path.addPath(op);
					step= op;
				
			break;
		}
		case LITERAL_idiv:
		{
			AST __t356 = _t;
			AST tmp86_AST_in = (AST)_t;
			match(_t,LITERAL_idiv);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			_t = __t356;
			_t = _t.getNextSibling();
			
					OpNumeric op= new OpNumeric(context, left, right, Constants.IDIV);
					path.addPath(op);
					step= op;
				
			break;
		}
		case LITERAL_mod:
		{
			AST __t357 = _t;
			AST tmp87_AST_in = (AST)_t;
			match(_t,LITERAL_mod);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			_t = __t357;
			_t = _t.getNextSibling();
			
					OpNumeric op= new OpNumeric(context, left, right, Constants.MOD);
					path.addPath(op);
					step= op;
				
			break;
		}
		case STAR:
		{
			AST __t358 = _t;
			AST tmp88_AST_in = (AST)_t;
			match(_t,STAR);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			_t = __t358;
			_t = _t.getNextSibling();
			
					OpNumeric op= new OpNumeric(context, left, right, Constants.MULT);
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
		
		AST constructor_AST_in = (AST)_t;
		AST e = null;
		AST attrName = null;
		AST attrVal = null;
		AST pcdata = null;
		AST cdata = null;
		AST p = null;
		
			step= null;
			PathExpr elementContent= null;
			Expression contentExpr= null;
		
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case ELEMENT:
		{
			AST __t393 = _t;
			e = _t==ASTNULL ? null :(AST)_t;
			match(_t,ELEMENT);
			_t = _t.getFirstChild();
			
						ElementConstructor c= new ElementConstructor(context, e.getText());
						step= c;
					
			{
			_loop399:
			do {
				if (_t==null) _t=ASTNULL;
				if ((_t.getType()==ATTRIBUTE)) {
					AST __t395 = _t;
					attrName = _t==ASTNULL ? null :(AST)_t;
					match(_t,ATTRIBUTE);
					_t = _t.getFirstChild();
					
										AttributeConstructor attrib= new AttributeConstructor(context, attrName.getText());
										c.addAttribute(attrib);
									
					{
					int _cnt398=0;
					_loop398:
					do {
						if (_t==null) _t=ASTNULL;
						switch ( _t.getType()) {
						case ATTRIBUTE_CONTENT:
						{
							attrVal = (AST)_t;
							match(_t,ATTRIBUTE_CONTENT);
							_t = _t.getNextSibling();
							attrib.addValue(attrVal.getText());
							break;
						}
						case LCURLY:
						{
							AST __t397 = _t;
							AST tmp89_AST_in = (AST)_t;
							match(_t,LCURLY);
							_t = _t.getFirstChild();
							PathExpr enclosed= new PathExpr(context);
							expr(_t,enclosed);
							_t = _retTree;
							attrib.addEnclosedExpr(enclosed);
							_t = __t397;
							_t = _t.getNextSibling();
							break;
						}
						default:
						{
							if ( _cnt398>=1 ) { break _loop398; } else {throw new NoViableAltException(_t);}
						}
						}
						_cnt398++;
					} while (true);
					}
					_t = __t395;
					_t = _t.getNextSibling();
				}
				else {
					break _loop399;
				}
				
			} while (true);
			}
			{
			_loop401:
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
					break _loop401;
				}
				
			} while (true);
			}
			_t = __t393;
			_t = _t.getNextSibling();
			break;
		}
		case TEXT:
		{
			AST __t402 = _t;
			pcdata = _t==ASTNULL ? null :(AST)_t;
			match(_t,TEXT);
			_t = _t.getFirstChild();
			
						TextConstructor text= new TextConstructor(context, pcdata.getText());
						step= text;
					
			_t = __t402;
			_t = _t.getNextSibling();
			break;
		}
		case XML_COMMENT:
		{
			AST __t403 = _t;
			cdata = _t==ASTNULL ? null :(AST)_t;
			match(_t,XML_COMMENT);
			_t = _t.getFirstChild();
			
						CommentConstructor comment= new CommentConstructor(context, cdata.getText());
						step= comment;
					
			_t = __t403;
			_t = _t.getNextSibling();
			break;
		}
		case XML_PI:
		{
			AST __t404 = _t;
			p = _t==ASTNULL ? null :(AST)_t;
			match(_t,XML_PI);
			_t = _t.getFirstChild();
			
						PIConstructor pi= new PIConstructor(context, p.getText());
						step= pi;
					
			_t = __t404;
			_t = _t.getNextSibling();
			break;
		}
		case LCURLY:
		{
			AST __t405 = _t;
			AST tmp90_AST_in = (AST)_t;
			match(_t,LCURLY);
			_t = _t.getFirstChild();
			EnclosedExpr subexpr= new EnclosedExpr(context);
			step=expr(_t,subexpr);
			_t = _retTree;
			step= subexpr;
			_t = __t405;
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
		
		AST predicates_AST_in = (AST)_t;
		
			FilteredExpression filter= null;
			step= expression;
		
		
		{
		_loop362:
		do {
			if (_t==null) _t=ASTNULL;
			if ((_t.getType()==PREDICATE)) {
				AST __t361 = _t;
				AST tmp91_AST_in = (AST)_t;
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
							
				_t = __t361;
				_t = _t.getNextSibling();
			}
			else {
				break _loop362;
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
		
		AST literalExpr_AST_in = (AST)_t;
		AST c = null;
		AST i = null;
		AST dec = null;
		AST dbl = null;
		step= null;
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case STRING_LITERAL:
		{
			c = (AST)_t;
			match(_t,STRING_LITERAL);
			_t = _t.getNextSibling();
			step= new LiteralValue(context, new StringValue(c.getText()));
			break;
		}
		case INTEGER_LITERAL:
		{
			i = (AST)_t;
			match(_t,INTEGER_LITERAL);
			_t = _t.getNextSibling();
			step= new LiteralValue(context, new IntegerValue(Integer.parseInt(i.getText())));
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
				dec = (AST)_t;
				match(_t,DECIMAL_LITERAL);
				_t = _t.getNextSibling();
				step= new LiteralValue(context, new DecimalValue(dec.getText()));
				break;
			}
			case DOUBLE_LITERAL:
			{
				dbl = (AST)_t;
				match(_t,DOUBLE_LITERAL);
				_t = _t.getNextSibling();
				step= new LiteralValue(context, new DoubleValue(Double.parseDouble(dbl.getText())));
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
		
		AST functionCall_AST_in = (AST)_t;
		AST fn = null;
		
			PathExpr pathExpr;
			step= null;
		
		
		AST __t366 = _t;
		fn = _t==ASTNULL ? null :(AST)_t;
		match(_t,FUNCTION);
		_t = _t.getFirstChild();
		List params= new ArrayList(2);
		{
		_loop368:
		do {
			if (_t==null) _t=ASTNULL;
			if ((_tokenSet_0.member(_t.getType()))) {
				pathExpr= new PathExpr(context);
				expr(_t,pathExpr);
				_t = _retTree;
				params.add(pathExpr);
			}
			else {
				break _loop368;
			}
			
		} while (true);
		}
		_t = __t366;
		_t = _t.getNextSibling();
		step= FunctionFactory.createFunction(context, path, fn.getText(), params);
		_retTree = _t;
		return step;
	}
	
	public final int  forwardAxis(AST _t) throws RecognitionException, PermissionDeniedException,EXistException {
		int axis;
		
		AST forwardAxis_AST_in = (AST)_t;
		axis= -1;
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case LITERAL_child:
		{
			AST tmp92_AST_in = (AST)_t;
			match(_t,LITERAL_child);
			_t = _t.getNextSibling();
			axis= Constants.CHILD_AXIS;
			break;
		}
		case LITERAL_attribute:
		{
			AST tmp93_AST_in = (AST)_t;
			match(_t,LITERAL_attribute);
			_t = _t.getNextSibling();
			axis= Constants.ATTRIBUTE_AXIS;
			break;
		}
		case LITERAL_self:
		{
			AST tmp94_AST_in = (AST)_t;
			match(_t,LITERAL_self);
			_t = _t.getNextSibling();
			axis= Constants.SELF_AXIS;
			break;
		}
		case LITERAL_parent:
		{
			AST tmp95_AST_in = (AST)_t;
			match(_t,LITERAL_parent);
			_t = _t.getNextSibling();
			axis= Constants.PARENT_AXIS;
			break;
		}
		case LITERAL_descendant:
		{
			AST tmp96_AST_in = (AST)_t;
			match(_t,LITERAL_descendant);
			_t = _t.getNextSibling();
			axis= Constants.DESCENDANT_AXIS;
			break;
		}
		case 120:
		{
			AST tmp97_AST_in = (AST)_t;
			match(_t,120);
			_t = _t.getNextSibling();
			axis= Constants.DESCENDANT_SELF_AXIS;
			break;
		}
		case 121:
		{
			AST tmp98_AST_in = (AST)_t;
			match(_t,121);
			_t = _t.getNextSibling();
			axis= Constants.FOLLOWING_SIBLING_AXIS;
			break;
		}
		case 125:
		{
			AST tmp99_AST_in = (AST)_t;
			match(_t,125);
			_t = _t.getNextSibling();
			axis= Constants.PRECEDING_SIBLING_AXIS;
			break;
		}
		case LITERAL_ancestor:
		{
			AST tmp100_AST_in = (AST)_t;
			match(_t,LITERAL_ancestor);
			_t = _t.getNextSibling();
			axis= Constants.ANCESTOR_AXIS;
			break;
		}
		case 124:
		{
			AST tmp101_AST_in = (AST)_t;
			match(_t,124);
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
		
		AST predicate_AST_in = (AST)_t;
		
		AST __t364 = _t;
		AST tmp102_AST_in = (AST)_t;
		match(_t,PREDICATE);
		_t = _t.getFirstChild();
		Predicate predicateExpr= new Predicate(context);
		expr(_t,predicateExpr);
		_t = _retTree;
		step.addPredicate(predicateExpr);
		_t = __t364;
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
	
	private static final long[] mk_tokenSet_0() {
		long[] data = { -4158651742761091184L, -844699808071675L, 1025L, 0L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_0 = new BitSet(mk_tokenSet_0());
	private static final long[] mk_tokenSet_1() {
		long[] data = { 2251799816306688L, 140737488355328L, 1024L, 0L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_1 = new BitSet(mk_tokenSet_1());
	}
	
