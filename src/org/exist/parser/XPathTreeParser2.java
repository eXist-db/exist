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
		String posVar = null;
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
		
		try {      // for error handling
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case XPOINTER:
			{
				AST __t207 = _t;
				AST tmp1_AST_in = (AST)_t;
				match(_t,XPOINTER);
				_t = _t.getFirstChild();
				expr(_t,path);
				_t = _retTree;
				_t = __t207;
				_t = _t.getNextSibling();
				break;
			}
			case XPOINTER_ID:
			{
				AST __t208 = _t;
				AST tmp2_AST_in = (AST)_t;
				match(_t,XPOINTER_ID);
				_t = _t.getFirstChild();
				nc = (AST)_t;
				match(_t,NCNAME);
				_t = _t.getNextSibling();
				_t = __t208;
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
	
	public final void expr(AST _t,
		PathExpr path
	) throws RecognitionException, PermissionDeniedException,EXistException,XPathException {
		
		AST expr_AST_in = (AST)_t;
		AST t = null;
		AST varName = null;
		AST posVar = null;
		AST letVarName = null;
		Expression step= null;
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case LITERAL_cast:
		{
			AST __t247 = _t;
			AST tmp3_AST_in = (AST)_t;
			match(_t,LITERAL_cast);
			_t = _t.getFirstChild();
			
						PathExpr expr = new PathExpr(context);
						int cardinality = Cardinality.EXACTLY_ONE;
					
			expr(_t,expr);
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
				
								cardinality = Cardinality.ZERO_OR_ONE;
							
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
						CastExpression castExpr = new CastExpression(context, expr, code, cardinality);
						path.add(castExpr);
					
			_t = __t247;
			_t = _t.getNextSibling();
			break;
		}
		case COMMA:
		{
			AST __t249 = _t;
			AST tmp5_AST_in = (AST)_t;
			match(_t,COMMA);
			_t = _t.getFirstChild();
			
						PathExpr left= new PathExpr(context);
						PathExpr right= new PathExpr(context);
					
			expr(_t,left);
			_t = _retTree;
			expr(_t,right);
			_t = _retTree;
			
						SequenceConstructor sc= new SequenceConstructor(context);
						sc.addExpression(left);
						sc.addExpression(right);
						path.add(sc);
					
			_t = __t249;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_if:
		{
			AST __t250 = _t;
			AST tmp6_AST_in = (AST)_t;
			match(_t,LITERAL_if);
			_t = _t.getFirstChild();
			
						PathExpr testExpr= new PathExpr(context);
						PathExpr thenExpr= new PathExpr(context);
						PathExpr elseExpr= new PathExpr(context);
					
			expr(_t,testExpr);
			_t = _retTree;
			expr(_t,thenExpr);
			_t = _retTree;
			expr(_t,elseExpr);
			_t = _retTree;
			
						ConditionalExpression cond= new ConditionalExpression(context, testExpr, thenExpr, elseExpr);
						path.add(cond);
					
			_t = __t250;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_return:
		{
			AST __t251 = _t;
			AST tmp7_AST_in = (AST)_t;
			match(_t,LITERAL_return);
			_t = _t.getFirstChild();
			
						List clauses= new ArrayList();
						Expression action= new PathExpr(context);
						PathExpr whereExpr= null;
						List orderBy= null;
					
			{
			int _cnt262=0;
			_loop262:
			do {
				if (_t==null) _t=ASTNULL;
				switch ( _t.getType()) {
				case LITERAL_for:
				{
					AST __t253 = _t;
					AST tmp8_AST_in = (AST)_t;
					match(_t,LITERAL_for);
					_t = _t.getFirstChild();
					{
					int _cnt257=0;
					_loop257:
					do {
						if (_t==null) _t=ASTNULL;
						if ((_t.getType()==VARIABLE_BINDING)) {
							AST __t255 = _t;
							varName = _t==ASTNULL ? null :(AST)_t;
							match(_t,VARIABLE_BINDING);
							_t = _t.getFirstChild();
							
														ForLetClause clause= new ForLetClause();
														PathExpr inputSequence= new PathExpr(context);
													
							{
							if (_t==null) _t=ASTNULL;
							switch ( _t.getType()) {
							case POSITIONAL_VAR:
							{
								posVar = (AST)_t;
								match(_t,POSITIONAL_VAR);
								_t = _t.getNextSibling();
								
																clause.posVar = posVar.getText();
															
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
							case NCNAME:
							case STRING_LITERAL:
							case EQ:
							case LCURLY:
							case COMMA:
							case STAR:
							case PLUS:
							case LITERAL_if:
							case LITERAL_return:
							case LITERAL_or:
							case LITERAL_and:
							case LITERAL_cast:
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
							case LITERAL_idiv:
							case LITERAL_mod:
							case UNION:
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
							case 104:
							case 105:
							case LITERAL_parent:
							case LITERAL_ancestor:
							case 108:
							case 109:
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
							expr(_t,inputSequence);
							_t = _retTree;
							
														clause.varName= varName.getText();
														clause.inputSequence= inputSequence;
														clauses.add(clause);
													
							_t = __t255;
							_t = _t.getNextSibling();
						}
						else {
							if ( _cnt257>=1 ) { break _loop257; } else {throw new NoViableAltException(_t);}
						}
						
						_cnt257++;
					} while (true);
					}
					_t = __t253;
					_t = _t.getNextSibling();
					break;
				}
				case LITERAL_let:
				{
					AST __t258 = _t;
					AST tmp9_AST_in = (AST)_t;
					match(_t,LITERAL_let);
					_t = _t.getFirstChild();
					{
					int _cnt261=0;
					_loop261:
					do {
						if (_t==null) _t=ASTNULL;
						if ((_t.getType()==VARIABLE_BINDING)) {
							AST __t260 = _t;
							letVarName = _t==ASTNULL ? null :(AST)_t;
							match(_t,VARIABLE_BINDING);
							_t = _t.getFirstChild();
							
														ForLetClause clause= new ForLetClause();
														clause.isForClause= false;
														PathExpr inputSequence= new PathExpr(context);
													
							expr(_t,inputSequence);
							_t = _retTree;
							
														clause.varName= letVarName.getText();
														clause.inputSequence= inputSequence;
														clauses.add(clause);
													
							_t = __t260;
							_t = _t.getNextSibling();
						}
						else {
							if ( _cnt261>=1 ) { break _loop261; } else {throw new NoViableAltException(_t);}
						}
						
						_cnt261++;
					} while (true);
					}
					_t = __t258;
					_t = _t.getNextSibling();
					break;
				}
				default:
				{
					if ( _cnt262>=1 ) { break _loop262; } else {throw new NoViableAltException(_t);}
				}
				}
				_cnt262++;
			} while (true);
			}
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case LITERAL_where:
			{
				AST tmp10_AST_in = (AST)_t;
				match(_t,LITERAL_where);
				_t = _t.getNextSibling();
				whereExpr= new PathExpr(context);
				expr(_t,whereExpr);
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
			case NCNAME:
			case STRING_LITERAL:
			case EQ:
			case LCURLY:
			case COMMA:
			case STAR:
			case PLUS:
			case LITERAL_if:
			case LITERAL_return:
			case LITERAL_or:
			case LITERAL_and:
			case LITERAL_cast:
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
			case LITERAL_idiv:
			case LITERAL_mod:
			case UNION:
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
			case 104:
			case 105:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 108:
			case 109:
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
				AST __t265 = _t;
				AST tmp11_AST_in = (AST)_t;
				match(_t,ORDER_BY);
				_t = _t.getFirstChild();
				orderBy= new ArrayList(3);
				{
				int _cnt271=0;
				_loop271:
				do {
					if (_t==null) _t=ASTNULL;
					if ((_tokenSet_0.member(_t.getType()))) {
						PathExpr orderSpecExpr= new PathExpr(context);
						expr(_t,orderSpecExpr);
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
								AST tmp12_AST_in = (AST)_t;
								match(_t,LITERAL_ascending);
								_t = _t.getNextSibling();
								break;
							}
							case LITERAL_descending:
							{
								AST tmp13_AST_in = (AST)_t;
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
						case NCNAME:
						case STRING_LITERAL:
						case EQ:
						case LCURLY:
						case COMMA:
						case LITERAL_empty:
						case STAR:
						case PLUS:
						case LITERAL_if:
						case LITERAL_return:
						case LITERAL_or:
						case LITERAL_and:
						case LITERAL_cast:
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
						case LITERAL_idiv:
						case LITERAL_mod:
						case UNION:
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
						case 104:
						case 105:
						case LITERAL_parent:
						case LITERAL_ancestor:
						case 108:
						case 109:
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
							AST tmp14_AST_in = (AST)_t;
							match(_t,LITERAL_empty);
							_t = _t.getNextSibling();
							{
							if (_t==null) _t=ASTNULL;
							switch ( _t.getType()) {
							case LITERAL_greatest:
							{
								AST tmp15_AST_in = (AST)_t;
								match(_t,LITERAL_greatest);
								_t = _t.getNextSibling();
								break;
							}
							case LITERAL_least:
							{
								AST tmp16_AST_in = (AST)_t;
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
						case NCNAME:
						case STRING_LITERAL:
						case EQ:
						case LCURLY:
						case COMMA:
						case STAR:
						case PLUS:
						case LITERAL_if:
						case LITERAL_return:
						case LITERAL_or:
						case LITERAL_and:
						case LITERAL_cast:
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
						case LITERAL_idiv:
						case LITERAL_mod:
						case UNION:
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
						case 104:
						case 105:
						case LITERAL_parent:
						case LITERAL_ancestor:
						case 108:
						case 109:
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
						if ( _cnt271>=1 ) { break _loop271; } else {throw new NoViableAltException(_t);}
					}
					
					_cnt271++;
				} while (true);
				}
				_t = __t265;
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
			case NCNAME:
			case STRING_LITERAL:
			case EQ:
			case LCURLY:
			case COMMA:
			case STAR:
			case PLUS:
			case LITERAL_if:
			case LITERAL_return:
			case LITERAL_or:
			case LITERAL_and:
			case LITERAL_cast:
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
			case LITERAL_idiv:
			case LITERAL_mod:
			case UNION:
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
			case 104:
			case 105:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 108:
			case 109:
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
			expr(_t,(PathExpr) action);
			_t = _retTree;
			
						for (int i= clauses.size() - 1; i >= 0; i--) {
							ForLetClause clause= (ForLetClause) clauses.get(i);
							BindingExpression expr;
							if (clause.isForClause)
								expr= new ForExpr(context);
							else
								expr= new LetExpr(context);
							expr.setVariable(clause.varName);
							expr.setInputSequence(clause.inputSequence);
							expr.setReturnExpression(action);
							if (clause.isForClause)
								((ForExpr)expr).setPositionalVariable(clause.posVar);
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
							}
							action= expr;
						}
						path.add(action);
					
			_t = __t251;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_or:
		{
			AST __t272 = _t;
			AST tmp17_AST_in = (AST)_t;
			match(_t,LITERAL_or);
			_t = _t.getFirstChild();
			
						PathExpr left= new PathExpr(context);
						PathExpr right= new PathExpr(context);
					
			expr(_t,left);
			_t = _retTree;
			expr(_t,right);
			_t = _retTree;
			_t = __t272;
			_t = _t.getNextSibling();
			
					OpOr or= new OpOr(context);
					or.add(left);
					or.add(right);
					path.addPath(or);
				
			break;
		}
		case LITERAL_and:
		{
			AST __t273 = _t;
			AST tmp18_AST_in = (AST)_t;
			match(_t,LITERAL_and);
			_t = _t.getFirstChild();
			
						PathExpr left= new PathExpr(context);
						PathExpr right= new PathExpr(context);
					
			expr(_t,left);
			_t = _retTree;
			expr(_t,right);
			_t = _retTree;
			_t = __t273;
			_t = _t.getNextSibling();
			
					OpAnd and= new OpAnd(context);
					and.add(left);
					and.add(right);
					path.addPath(and);
				
			break;
		}
		case PARENTHESIZED:
		{
			AST __t274 = _t;
			AST tmp19_AST_in = (AST)_t;
			match(_t,PARENTHESIZED);
			_t = _t.getFirstChild();
			
						PathExpr pathExpr= new PathExpr(context);
						path.addPath(pathExpr);
					
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
			case NCNAME:
			case STRING_LITERAL:
			case EQ:
			case LCURLY:
			case COMMA:
			case STAR:
			case PLUS:
			case LITERAL_if:
			case LITERAL_return:
			case LITERAL_or:
			case LITERAL_and:
			case LITERAL_cast:
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
			case LITERAL_idiv:
			case LITERAL_mod:
			case UNION:
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
			case 104:
			case 105:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 108:
			case 109:
			case DOUBLE_LITERAL:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
			case XML_PI:
			{
				expr(_t,pathExpr);
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
			_t = __t274;
			_t = _t.getNextSibling();
			break;
		}
		case UNION:
		{
			AST __t276 = _t;
			AST tmp20_AST_in = (AST)_t;
			match(_t,UNION);
			_t = _t.getFirstChild();
			
						PathExpr left= new PathExpr(context);
						PathExpr right= new PathExpr(context);
					
			expr(_t,left);
			_t = _retTree;
			expr(_t,right);
			_t = _retTree;
			_t = __t276;
			_t = _t.getNextSibling();
			
					Union union= new Union(context, left, right);
					path.add(union);
				
			break;
		}
		case ABSOLUTE_SLASH:
		{
			AST __t277 = _t;
			AST tmp21_AST_in = (AST)_t;
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
			case NCNAME:
			case STRING_LITERAL:
			case EQ:
			case LCURLY:
			case COMMA:
			case STAR:
			case PLUS:
			case LITERAL_if:
			case LITERAL_return:
			case LITERAL_or:
			case LITERAL_and:
			case LITERAL_cast:
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
			case LITERAL_idiv:
			case LITERAL_mod:
			case UNION:
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
			case 104:
			case 105:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 108:
			case 109:
			case DOUBLE_LITERAL:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
			case XML_PI:
			{
				expr(_t,path);
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
			_t = __t277;
			_t = _t.getNextSibling();
			break;
		}
		case ABSOLUTE_DSLASH:
		{
			AST __t279 = _t;
			AST tmp22_AST_in = (AST)_t;
			match(_t,ABSOLUTE_DSLASH);
			_t = _t.getFirstChild();
			
						RootNode root= new RootNode(context);
						path.add(root);
					
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case QNAME:
			case WILDCARD:
			case PREFIX_WILDCARD:
			case FUNCTION:
			case VARIABLE_REF:
			case NCNAME:
			case STRING_LITERAL:
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
			case 104:
			case 105:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 108:
			case 109:
			case DOUBLE_LITERAL:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
			{
				step=pathExpr(_t,path);
				_t = _retTree;
				
								if (step instanceof LocationStep) {
									LocationStep s= (LocationStep) step;
									if (s.getAxis() == Constants.ATTRIBUTE_AXIS)
										// combines descendant-or-self::node()/attribute:*
										s.setAxis(Constants.DESCENDANT_ATTRIBUTE_AXIS);
									else
										s.setAxis(Constants.DESCENDANT_SELF_AXIS);
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
			_t = __t279;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_to:
		{
			AST __t281 = _t;
			AST tmp23_AST_in = (AST)_t;
			match(_t,LITERAL_to);
			_t = _t.getFirstChild();
			
						PathExpr start= new PathExpr(context);
						PathExpr end= new PathExpr(context);
						List args= new ArrayList(2);
						args.add(start);
						args.add(end);
					
			expr(_t,start);
			_t = _retTree;
			expr(_t,end);
			_t = _retTree;
			
						RangeExpression range= new RangeExpression(context);
						range.setArguments(args);
						path.addPath(range);
					
			_t = __t281;
			_t = _t.getNextSibling();
			break;
		}
		case EQ:
		case NEQ:
		case GT:
		case GTEQ:
		case LT:
		case LTEQ:
		{
			step=generalComp(_t,path);
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
		case QNAME:
		case WILDCARD:
		case PREFIX_WILDCARD:
		case FUNCTION:
		case VARIABLE_REF:
		case NCNAME:
		case STRING_LITERAL:
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
		case 104:
		case 105:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 108:
		case 109:
		case DOUBLE_LITERAL:
		case DECIMAL_LITERAL:
		case INTEGER_LITERAL:
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
		case ELEMENT:
		case TEXT:
		case LCURLY:
		case XML_COMMENT:
		case XML_PI:
		{
			step=constructor(_t,path);
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
		
		prolog(_t,path);
		_t = _retTree;
		expr(_t,path);
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
		
		{
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case VERSION_DECL:
		{
			AST __t213 = _t;
			v = _t==ASTNULL ? null :(AST)_t;
			match(_t,VERSION_DECL);
			_t = _t.getFirstChild();
			
							if (!v.getText().equals("1.0"))
								throw new XPathException("Wrong XQuery version: require 1.0");
						
			_t = __t213;
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
		case NCNAME:
		case STRING_LITERAL:
		case EQ:
		case LCURLY:
		case COMMA:
		case STAR:
		case PLUS:
		case LITERAL_if:
		case LITERAL_return:
		case LITERAL_or:
		case LITERAL_and:
		case LITERAL_cast:
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
		case LITERAL_idiv:
		case LITERAL_mod:
		case UNION:
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
		case 104:
		case 105:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 108:
		case 109:
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
		_loop219:
		do {
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case NAMESPACE_DECL:
			{
				AST __t215 = _t;
				prefix = _t==ASTNULL ? null :(AST)_t;
				match(_t,NAMESPACE_DECL);
				_t = _t.getFirstChild();
				uri = (AST)_t;
				match(_t,STRING_LITERAL);
				_t = _t.getNextSibling();
				context.declareNamespace(prefix.getText(), uri.getText());
				_t = __t215;
				_t = _t.getNextSibling();
				break;
			}
			case DEF_NAMESPACE_DECL:
			{
				AST __t216 = _t;
				AST tmp24_AST_in = (AST)_t;
				match(_t,DEF_NAMESPACE_DECL);
				_t = _t.getFirstChild();
				defu = (AST)_t;
				match(_t,STRING_LITERAL);
				_t = _t.getNextSibling();
				context.declareNamespace("", defu.getText());
				_t = __t216;
				_t = _t.getNextSibling();
				break;
			}
			case DEF_FUNCTION_NS_DECL:
			{
				AST __t217 = _t;
				AST tmp25_AST_in = (AST)_t;
				match(_t,DEF_FUNCTION_NS_DECL);
				_t = _t.getFirstChild();
				deff = (AST)_t;
				match(_t,STRING_LITERAL);
				_t = _t.getNextSibling();
				context.setDefaultFunctionNamespace(deff.getText());
				_t = __t217;
				_t = _t.getNextSibling();
				break;
			}
			case GLOBAL_VAR:
			{
				AST __t218 = _t;
				qname = _t==ASTNULL ? null :(AST)_t;
				match(_t,GLOBAL_VAR);
				_t = _t.getFirstChild();
				PathExpr enclosed= new PathExpr(context);
				expr(_t,enclosed);
				_t = _retTree;
				
								VariableDeclaration decl= new VariableDeclaration(context, qname.getText(), enclosed);
								path.add(decl);
							
				_t = __t218;
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
				break _loop219;
			}
			}
		} while (true);
		}
		_retTree = _t;
	}
	
	public final void functionDecl(AST _t,
		PathExpr path
	) throws RecognitionException, PermissionDeniedException,EXistException,XPathException {
		
		AST functionDecl_AST_in = (AST)_t;
		AST name = null;
		
		AST __t221 = _t;
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
			AST __t224 = _t;
			AST tmp26_AST_in = (AST)_t;
			match(_t,LITERAL_as);
			_t = _t.getFirstChild();
			SequenceType type= new SequenceType();
			sequenceType(_t,type);
			_t = _retTree;
			signature.setReturnType(type);
			_t = __t224;
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
		AST __t225 = _t;
		AST tmp27_AST_in = (AST)_t;
		match(_t,LCURLY);
		_t = _t.getFirstChild();
		expr(_t,body);
		_t = _retTree;
		func.setFunctionBody(body);
		_t = __t225;
		_t = _t.getNextSibling();
		_t = __t221;
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
		_loop228:
		do {
			if (_t==null) _t=ASTNULL;
			if ((_t.getType()==VARIABLE_BINDING)) {
				param(_t,vars);
				_t = _retTree;
			}
			else {
				break _loop228;
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
			AST __t235 = _t;
			t = _t==ASTNULL ? null :(AST)_t;
			match(_t,ATOMIC_TYPE);
			_t = _t.getFirstChild();
			
							QName qn= QName.parse(context, t.getText());
							int code= Type.getType(qn);
							type.setPrimaryType(code);
						
			_t = __t235;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_empty:
		{
			AST __t236 = _t;
			AST tmp28_AST_in = (AST)_t;
			match(_t,LITERAL_empty);
			_t = _t.getFirstChild();
			
							type.setPrimaryType(Type.EMPTY);
							type.setCardinality(Cardinality.EMPTY);
						
			_t = __t236;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_item:
		{
			AST __t237 = _t;
			AST tmp29_AST_in = (AST)_t;
			match(_t,LITERAL_item);
			_t = _t.getFirstChild();
			type.setPrimaryType(Type.ITEM);
			_t = __t237;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_node:
		{
			AST __t238 = _t;
			AST tmp30_AST_in = (AST)_t;
			match(_t,LITERAL_node);
			_t = _t.getFirstChild();
			type.setPrimaryType(Type.NODE);
			_t = __t238;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_element:
		{
			AST __t239 = _t;
			AST tmp31_AST_in = (AST)_t;
			match(_t,LITERAL_element);
			_t = _t.getFirstChild();
			type.setPrimaryType(Type.ELEMENT);
			_t = __t239;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_attribute:
		{
			AST __t240 = _t;
			AST tmp32_AST_in = (AST)_t;
			match(_t,LITERAL_attribute);
			_t = _t.getFirstChild();
			type.setPrimaryType(Type.ATTRIBUTE);
			_t = __t240;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_text:
		{
			AST __t241 = _t;
			AST tmp33_AST_in = (AST)_t;
			match(_t,LITERAL_text);
			_t = _t.getFirstChild();
			type.setPrimaryType(Type.ITEM);
			_t = __t241;
			_t = _t.getNextSibling();
			break;
		}
		case 114:
		{
			AST __t242 = _t;
			AST tmp34_AST_in = (AST)_t;
			match(_t,114);
			_t = _t.getFirstChild();
			type.setPrimaryType(Type.PROCESSING_INSTRUCTION);
			_t = __t242;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_comment:
		{
			AST __t243 = _t;
			AST tmp35_AST_in = (AST)_t;
			match(_t,LITERAL_comment);
			_t = _t.getFirstChild();
			type.setPrimaryType(Type.COMMENT);
			_t = __t243;
			_t = _t.getNextSibling();
			break;
		}
		case 115:
		{
			AST __t244 = _t;
			AST tmp36_AST_in = (AST)_t;
			match(_t,115);
			_t = _t.getFirstChild();
			type.setPrimaryType(Type.DOCUMENT);
			_t = __t244;
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
			AST tmp37_AST_in = (AST)_t;
			match(_t,STAR);
			_t = _t.getNextSibling();
			type.setCardinality(Cardinality.ZERO_OR_MORE);
			break;
		}
		case PLUS:
		{
			AST tmp38_AST_in = (AST)_t;
			match(_t,PLUS);
			_t = _t.getNextSibling();
			type.setCardinality(Cardinality.ONE_OR_MORE);
			break;
		}
		case QUESTION:
		{
			AST tmp39_AST_in = (AST)_t;
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
	
	public final void param(AST _t,
		List vars
	) throws RecognitionException, XPathException {
		
		AST param_AST_in = (AST)_t;
		AST varname = null;
		
		AST __t230 = _t;
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
			AST __t232 = _t;
			AST tmp40_AST_in = (AST)_t;
			match(_t,LITERAL_as);
			_t = _t.getFirstChild();
			SequenceType type= new SequenceType();
			sequenceType(_t,type);
			_t = _retTree;
			_t = __t232;
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
		_t = __t230;
		_t = _t.getNextSibling();
		_retTree = _t;
	}
	
	public final Expression  pathExpr(AST _t,
		PathExpr path
	) throws RecognitionException, PermissionDeniedException,EXistException,XPathException {
		Expression step;
		
		AST pathExpr_AST_in = (AST)_t;
		AST c = null;
		AST i = null;
		AST dec = null;
		AST dbl = null;
		AST v = null;
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
		case STRING_LITERAL:
		{
			c = (AST)_t;
			match(_t,STRING_LITERAL);
			_t = _t.getNextSibling();
			
					step= new LiteralValue(context, new StringValue(c.getText()));
					path.add(step);
				
			break;
		}
		case INTEGER_LITERAL:
		{
			i = (AST)_t;
			match(_t,INTEGER_LITERAL);
			_t = _t.getNextSibling();
			
					step= new LiteralValue(context, new IntegerValue(Integer.parseInt(i.getText())));
					path.add(step);
				
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
				step= new LiteralValue(context, new DoubleValue(Double.parseDouble(dec.getText())));
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
			path.add(step);
			break;
		}
		case VARIABLE_REF:
		{
			v = (AST)_t;
			match(_t,VARIABLE_REF);
			_t = _t.getNextSibling();
			
					step= new VariableReference(context, v.getText());
					path.add(step);
				
			break;
		}
		case FUNCTION:
		{
			step=functionCall(_t,path);
			_t = _retTree;
			break;
		}
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
		case 104:
		case 105:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 108:
		case 109:
		{
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case LITERAL_child:
			case LITERAL_self:
			case LITERAL_attribute:
			case LITERAL_descendant:
			case 104:
			case 105:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 108:
			case 109:
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
				AST __t286 = _t;
				AST tmp41_AST_in = (AST)_t;
				match(_t,PREFIX_WILDCARD);
				_t = _t.getFirstChild();
				nc1 = (AST)_t;
				match(_t,NCNAME);
				_t = _t.getNextSibling();
				_t = __t286;
				_t = _t.getNextSibling();
				
							QName qname= new QName(nc1.getText(), null, null);
							test= new NameTest(Type.ELEMENT, qname);
						
				break;
			}
			case NCNAME:
			{
				AST __t287 = _t;
				nc = _t==ASTNULL ? null :(AST)_t;
				match(_t,NCNAME);
				_t = _t.getFirstChild();
				AST tmp42_AST_in = (AST)_t;
				match(_t,WILDCARD);
				_t = _t.getNextSibling();
				_t = __t287;
				_t = _t.getNextSibling();
				
							String namespaceURI= context.getURIForPrefix(nc.getText());
							QName qname= new QName(null, namespaceURI, null);
							test= new NameTest(Type.ELEMENT, qname);
						
				break;
			}
			case WILDCARD:
			{
				AST tmp43_AST_in = (AST)_t;
				match(_t,WILDCARD);
				_t = _t.getNextSibling();
				test= new TypeTest(Type.ELEMENT);
				break;
			}
			case LITERAL_node:
			{
				AST tmp44_AST_in = (AST)_t;
				match(_t,LITERAL_node);
				_t = _t.getNextSibling();
				test= new AnyNodeTest();
				break;
			}
			case LITERAL_text:
			{
				AST tmp45_AST_in = (AST)_t;
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
			_loop289:
			do {
				if (_t==null) _t=ASTNULL;
				if ((_t.getType()==PREDICATE)) {
					predicate(_t,(LocationStep) step);
					_t = _retTree;
				}
				else {
					break _loop289;
				}
				
			} while (true);
			}
			break;
		}
		case AT:
		{
			AST tmp46_AST_in = (AST)_t;
			match(_t,AT);
			_t = _t.getNextSibling();
			QName qname;
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
			case PREFIX_WILDCARD:
			{
				AST __t291 = _t;
				AST tmp47_AST_in = (AST)_t;
				match(_t,PREFIX_WILDCARD);
				_t = _t.getFirstChild();
				nc2 = (AST)_t;
				match(_t,NCNAME);
				_t = _t.getNextSibling();
				_t = __t291;
				_t = _t.getNextSibling();
				qname= new QName(nc2.getText(), null, null);
				break;
			}
			case NCNAME:
			{
				AST __t292 = _t;
				nc3 = _t==ASTNULL ? null :(AST)_t;
				match(_t,NCNAME);
				_t = _t.getFirstChild();
				AST tmp48_AST_in = (AST)_t;
				match(_t,WILDCARD);
				_t = _t.getNextSibling();
				_t = __t292;
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
			
					step= new LocationStep(context, Constants.ATTRIBUTE_AXIS, new NameTest(Type.ATTRIBUTE, qname));
					path.add(step);
				
			{
			_loop294:
			do {
				if (_t==null) _t=ASTNULL;
				if ((_t.getType()==PREDICATE)) {
					predicate(_t,(LocationStep) step);
					_t = _retTree;
				}
				else {
					break _loop294;
				}
				
			} while (true);
			}
			break;
		}
		case SELF:
		{
			AST tmp49_AST_in = (AST)_t;
			match(_t,SELF);
			_t = _t.getNextSibling();
			
					step= new LocationStep(context, Constants.SELF_AXIS, new TypeTest(Type.NODE));
					path.add(step);
				
			{
			_loop296:
			do {
				if (_t==null) _t=ASTNULL;
				if ((_t.getType()==PREDICATE)) {
					predicate(_t,(LocationStep) step);
					_t = _retTree;
				}
				else {
					break _loop296;
				}
				
			} while (true);
			}
			break;
		}
		case PARENT:
		{
			AST tmp50_AST_in = (AST)_t;
			match(_t,PARENT);
			_t = _t.getNextSibling();
			
					step= new LocationStep(context, Constants.PARENT_AXIS, new TypeTest(Type.NODE));
					path.add(step);
				
			{
			_loop298:
			do {
				if (_t==null) _t=ASTNULL;
				if ((_t.getType()==PREDICATE)) {
					predicate(_t,(LocationStep) step);
					_t = _retTree;
				}
				else {
					break _loop298;
				}
				
			} while (true);
			}
			break;
		}
		case SLASH:
		{
			AST __t299 = _t;
			AST tmp51_AST_in = (AST)_t;
			match(_t,SLASH);
			_t = _t.getFirstChild();
			step=pathExpr(_t,path);
			_t = _retTree;
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case QNAME:
			case WILDCARD:
			case PREFIX_WILDCARD:
			case FUNCTION:
			case VARIABLE_REF:
			case NCNAME:
			case STRING_LITERAL:
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
			case 104:
			case 105:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 108:
			case 109:
			case DOUBLE_LITERAL:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
			{
				rightStep=pathExpr(_t,path);
				_t = _retTree;
				
								if (rightStep instanceof LocationStep && ((LocationStep) rightStep).getAxis() == -1)
									 ((LocationStep) rightStep).setAxis(Constants.CHILD_AXIS);
							
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
			_t = __t299;
			_t = _t.getNextSibling();
			
					if (step instanceof LocationStep && ((LocationStep) step).getAxis() == -1)
						 ((LocationStep) step).setAxis(Constants.CHILD_AXIS);
				
			break;
		}
		case DSLASH:
		{
			AST __t301 = _t;
			AST tmp52_AST_in = (AST)_t;
			match(_t,DSLASH);
			_t = _t.getFirstChild();
			step=pathExpr(_t,path);
			_t = _retTree;
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case QNAME:
			case WILDCARD:
			case PREFIX_WILDCARD:
			case FUNCTION:
			case VARIABLE_REF:
			case NCNAME:
			case STRING_LITERAL:
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
			case 104:
			case 105:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 108:
			case 109:
			case DOUBLE_LITERAL:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
			{
				rightStep=pathExpr(_t,path);
				_t = _retTree;
				
								if (rightStep instanceof LocationStep) {
									LocationStep rs= (LocationStep) rightStep;
									if (rs.getAxis() == Constants.ATTRIBUTE_AXIS)
										rs.setAxis(Constants.DESCENDANT_ATTRIBUTE_AXIS);
									else
										rs.setAxis(Constants.DESCENDANT_SELF_AXIS);
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
			_t = __t301;
			_t = _t.getNextSibling();
			
					if (step instanceof LocationStep && ((LocationStep) rightStep).getAxis() == -1)
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
			AST __t323 = _t;
			AST tmp53_AST_in = (AST)_t;
			match(_t,EQ);
			_t = _t.getFirstChild();
			expr(_t,left);
			_t = _retTree;
			expr(_t,right);
			_t = _retTree;
			
						step= new GeneralComparison(context, left, right, Constants.EQ);
						path.add(step);
					
			_t = __t323;
			_t = _t.getNextSibling();
			break;
		}
		case NEQ:
		{
			AST __t324 = _t;
			AST tmp54_AST_in = (AST)_t;
			match(_t,NEQ);
			_t = _t.getFirstChild();
			expr(_t,left);
			_t = _retTree;
			expr(_t,right);
			_t = _retTree;
			
						step= new GeneralComparison(context, left, right, Constants.NEQ);
						path.add(step);
					
			_t = __t324;
			_t = _t.getNextSibling();
			break;
		}
		case LT:
		{
			AST __t325 = _t;
			AST tmp55_AST_in = (AST)_t;
			match(_t,LT);
			_t = _t.getFirstChild();
			expr(_t,left);
			_t = _retTree;
			expr(_t,right);
			_t = _retTree;
			
						step= new GeneralComparison(context, left, right, Constants.LT);
						path.add(step);
					
			_t = __t325;
			_t = _t.getNextSibling();
			break;
		}
		case LTEQ:
		{
			AST __t326 = _t;
			AST tmp56_AST_in = (AST)_t;
			match(_t,LTEQ);
			_t = _t.getFirstChild();
			expr(_t,left);
			_t = _retTree;
			expr(_t,right);
			_t = _retTree;
			
						step= new GeneralComparison(context, left, right, Constants.LTEQ);
						path.add(step);
					
			_t = __t326;
			_t = _t.getNextSibling();
			break;
		}
		case GT:
		{
			AST __t327 = _t;
			AST tmp57_AST_in = (AST)_t;
			match(_t,GT);
			_t = _t.getFirstChild();
			expr(_t,left);
			_t = _retTree;
			expr(_t,right);
			_t = _retTree;
			
						step= new GeneralComparison(context, left, right, Constants.GT);
						path.add(step);
					
			_t = __t327;
			_t = _t.getNextSibling();
			break;
		}
		case GTEQ:
		{
			AST __t328 = _t;
			AST tmp58_AST_in = (AST)_t;
			match(_t,GTEQ);
			_t = _t.getFirstChild();
			expr(_t,left);
			_t = _retTree;
			expr(_t,right);
			_t = _retTree;
			
						step= new GeneralComparison(context, left, right, Constants.GTEQ);
						path.add(step);
					
			_t = __t328;
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
			AST __t320 = _t;
			AST tmp59_AST_in = (AST)_t;
			match(_t,ANDEQ);
			_t = _t.getFirstChild();
			expr(_t,nodes);
			_t = _retTree;
			expr(_t,query);
			_t = _retTree;
			_t = __t320;
			_t = _t.getNextSibling();
			
					ExtFulltext exprCont= new ExtFulltext(context, Constants.FULLTEXT_AND);
					exprCont.setPath(nodes);
					exprCont.addTerm(query);
					path.addPath(exprCont);
				
			break;
		}
		case OREQ:
		{
			AST __t321 = _t;
			AST tmp60_AST_in = (AST)_t;
			match(_t,OREQ);
			_t = _t.getFirstChild();
			expr(_t,nodes);
			_t = _retTree;
			expr(_t,query);
			_t = _retTree;
			_t = __t321;
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
			AST __t304 = _t;
			AST tmp61_AST_in = (AST)_t;
			match(_t,PLUS);
			_t = _t.getFirstChild();
			expr(_t,left);
			_t = _retTree;
			expr(_t,right);
			_t = _retTree;
			_t = __t304;
			_t = _t.getNextSibling();
			
					OpNumeric op= new OpNumeric(context, left, right, Constants.PLUS);
					path.addPath(op);
					step= op;
				
			break;
		}
		case MINUS:
		{
			AST __t305 = _t;
			AST tmp62_AST_in = (AST)_t;
			match(_t,MINUS);
			_t = _t.getFirstChild();
			expr(_t,left);
			_t = _retTree;
			expr(_t,right);
			_t = _retTree;
			_t = __t305;
			_t = _t.getNextSibling();
			
					OpNumeric op= new OpNumeric(context, left, right, Constants.MINUS);
					path.addPath(op);
					step= op;
				
			break;
		}
		case UNARY_MINUS:
		{
			AST __t306 = _t;
			AST tmp63_AST_in = (AST)_t;
			match(_t,UNARY_MINUS);
			_t = _t.getFirstChild();
			expr(_t,left);
			_t = _retTree;
			_t = __t306;
			_t = _t.getNextSibling();
			
					UnaryExpr unary= new UnaryExpr(context, Constants.MINUS);
					unary.add(left);
					path.addPath(unary);
					step= unary;
				
			break;
		}
		case UNARY_PLUS:
		{
			AST __t307 = _t;
			AST tmp64_AST_in = (AST)_t;
			match(_t,UNARY_PLUS);
			_t = _t.getFirstChild();
			expr(_t,left);
			_t = _retTree;
			_t = __t307;
			_t = _t.getNextSibling();
			
					UnaryExpr unary= new UnaryExpr(context, Constants.PLUS);
					unary.add(left);
					path.addPath(unary);
					step= unary;
				
			break;
		}
		case LITERAL_div:
		{
			AST __t308 = _t;
			AST tmp65_AST_in = (AST)_t;
			match(_t,LITERAL_div);
			_t = _t.getFirstChild();
			expr(_t,left);
			_t = _retTree;
			expr(_t,right);
			_t = _retTree;
			_t = __t308;
			_t = _t.getNextSibling();
			
					OpNumeric op= new OpNumeric(context, left, right, Constants.DIV);
					path.addPath(op);
					step= op;
				
			break;
		}
		case LITERAL_idiv:
		{
			AST __t309 = _t;
			AST tmp66_AST_in = (AST)_t;
			match(_t,LITERAL_idiv);
			_t = _t.getFirstChild();
			expr(_t,left);
			_t = _retTree;
			expr(_t,right);
			_t = _retTree;
			_t = __t309;
			_t = _t.getNextSibling();
			
					OpNumeric op= new OpNumeric(context, left, right, Constants.IDIV);
					path.addPath(op);
					step= op;
				
			break;
		}
		case LITERAL_mod:
		{
			AST __t310 = _t;
			AST tmp67_AST_in = (AST)_t;
			match(_t,LITERAL_mod);
			_t = _t.getFirstChild();
			expr(_t,left);
			_t = _retTree;
			expr(_t,right);
			_t = _retTree;
			_t = __t310;
			_t = _t.getNextSibling();
			
					OpNumeric op= new OpNumeric(context, left, right, Constants.MOD);
					path.addPath(op);
					step= op;
				
			break;
		}
		case STAR:
		{
			AST __t311 = _t;
			AST tmp68_AST_in = (AST)_t;
			match(_t,STAR);
			_t = _t.getFirstChild();
			expr(_t,left);
			_t = _retTree;
			expr(_t,right);
			_t = _retTree;
			_t = __t311;
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
		
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case ELEMENT:
		{
			AST __t330 = _t;
			e = _t==ASTNULL ? null :(AST)_t;
			match(_t,ELEMENT);
			_t = _t.getFirstChild();
			
						ElementConstructor c= new ElementConstructor(context, e.getText());
						path.add(c);
						step= c;
					
			{
			_loop336:
			do {
				if (_t==null) _t=ASTNULL;
				if ((_t.getType()==ATTRIBUTE)) {
					AST __t332 = _t;
					attrName = _t==ASTNULL ? null :(AST)_t;
					match(_t,ATTRIBUTE);
					_t = _t.getFirstChild();
					
										AttributeConstructor attrib= new AttributeConstructor(context, attrName.getText());
										c.addAttribute(attrib);
									
					{
					int _cnt335=0;
					_loop335:
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
							AST __t334 = _t;
							AST tmp69_AST_in = (AST)_t;
							match(_t,LCURLY);
							_t = _t.getFirstChild();
							PathExpr enclosed= new PathExpr(context);
							expr(_t,enclosed);
							_t = _retTree;
							attrib.addEnclosedExpr(enclosed);
							_t = __t334;
							_t = _t.getNextSibling();
							break;
						}
						default:
						{
							if ( _cnt335>=1 ) { break _loop335; } else {throw new NoViableAltException(_t);}
						}
						}
						_cnt335++;
					} while (true);
					}
					_t = __t332;
					_t = _t.getNextSibling();
				}
				else {
					break _loop336;
				}
				
			} while (true);
			}
			{
			_loop338:
			do {
				if (_t==null) _t=ASTNULL;
				if ((_tokenSet_1.member(_t.getType()))) {
					
									if (elementContent == null) {
										elementContent= new PathExpr(context);
										c.setContent(elementContent);
									}
								
					step=constructor(_t,elementContent);
					_t = _retTree;
				}
				else {
					break _loop338;
				}
				
			} while (true);
			}
			_t = __t330;
			_t = _t.getNextSibling();
			break;
		}
		case TEXT:
		{
			AST __t339 = _t;
			pcdata = _t==ASTNULL ? null :(AST)_t;
			match(_t,TEXT);
			_t = _t.getFirstChild();
			
						TextConstructor text= new TextConstructor(context, pcdata.getText());
						path.add(text);
						step= text;
					
			_t = __t339;
			_t = _t.getNextSibling();
			break;
		}
		case XML_COMMENT:
		{
			AST __t340 = _t;
			cdata = _t==ASTNULL ? null :(AST)_t;
			match(_t,XML_COMMENT);
			_t = _t.getFirstChild();
			
						CommentConstructor comment= new CommentConstructor(context, cdata.getText());
						path.add(comment);
						step= comment;
					
			_t = __t340;
			_t = _t.getNextSibling();
			break;
		}
		case XML_PI:
		{
			AST __t341 = _t;
			p = _t==ASTNULL ? null :(AST)_t;
			match(_t,XML_PI);
			_t = _t.getFirstChild();
			
						PIConstructor pi= new PIConstructor(context, p.getText());
						path.add(pi);
						step= pi;
					
			_t = __t341;
			_t = _t.getNextSibling();
			break;
		}
		case LCURLY:
		{
			AST __t342 = _t;
			AST tmp70_AST_in = (AST)_t;
			match(_t,LCURLY);
			_t = _t.getFirstChild();
			EnclosedExpr subexpr= new EnclosedExpr(context);
			expr(_t,subexpr);
			_t = _retTree;
			
						path.addPath(subexpr);
						step= subexpr;
					
			_t = __t342;
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
	
	public final Expression  functionCall(AST _t,
		PathExpr path
	) throws RecognitionException, PermissionDeniedException,EXistException,XPathException {
		Expression step;
		
		AST functionCall_AST_in = (AST)_t;
		AST fn = null;
		
			PathExpr pathExpr;
			step= null;
		
		
		AST __t315 = _t;
		fn = _t==ASTNULL ? null :(AST)_t;
		match(_t,FUNCTION);
		_t = _t.getFirstChild();
		List params= new ArrayList(2);
		{
		_loop317:
		do {
			if (_t==null) _t=ASTNULL;
			if ((_tokenSet_0.member(_t.getType()))) {
				pathExpr= new PathExpr(context);
				expr(_t,pathExpr);
				_t = _retTree;
				params.add(pathExpr);
			}
			else {
				break _loop317;
			}
			
		} while (true);
		}
		_t = __t315;
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
			AST tmp71_AST_in = (AST)_t;
			match(_t,LITERAL_child);
			_t = _t.getNextSibling();
			axis= Constants.CHILD_AXIS;
			break;
		}
		case LITERAL_attribute:
		{
			AST tmp72_AST_in = (AST)_t;
			match(_t,LITERAL_attribute);
			_t = _t.getNextSibling();
			axis= Constants.ATTRIBUTE_AXIS;
			break;
		}
		case LITERAL_self:
		{
			AST tmp73_AST_in = (AST)_t;
			match(_t,LITERAL_self);
			_t = _t.getNextSibling();
			axis= Constants.SELF_AXIS;
			break;
		}
		case LITERAL_parent:
		{
			AST tmp74_AST_in = (AST)_t;
			match(_t,LITERAL_parent);
			_t = _t.getNextSibling();
			axis= Constants.PARENT_AXIS;
			break;
		}
		case LITERAL_descendant:
		{
			AST tmp75_AST_in = (AST)_t;
			match(_t,LITERAL_descendant);
			_t = _t.getNextSibling();
			axis= Constants.DESCENDANT_AXIS;
			break;
		}
		case 104:
		{
			AST tmp76_AST_in = (AST)_t;
			match(_t,104);
			_t = _t.getNextSibling();
			axis= Constants.DESCENDANT_SELF_AXIS;
			break;
		}
		case 105:
		{
			AST tmp77_AST_in = (AST)_t;
			match(_t,105);
			_t = _t.getNextSibling();
			axis= Constants.FOLLOWING_SIBLING_AXIS;
			break;
		}
		case 109:
		{
			AST tmp78_AST_in = (AST)_t;
			match(_t,109);
			_t = _t.getNextSibling();
			axis= Constants.PRECEDING_SIBLING_AXIS;
			break;
		}
		case LITERAL_ancestor:
		{
			AST tmp79_AST_in = (AST)_t;
			match(_t,LITERAL_ancestor);
			_t = _t.getNextSibling();
			axis= Constants.ANCESTOR_AXIS;
			break;
		}
		case 108:
		{
			AST tmp80_AST_in = (AST)_t;
			match(_t,108);
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
		
		AST __t313 = _t;
		AST tmp81_AST_in = (AST)_t;
		match(_t,PREDICATE);
		_t = _t.getFirstChild();
		Predicate predicateExpr= new Predicate(context);
		expr(_t,predicateExpr);
		_t = _retTree;
		step.addPredicate(predicateExpr);
		_t = __t313;
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
		"\"then\"",
		"\"else\"",
		"\"or\"",
		"\"and\"",
		"\"cast\"",
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
		"\"idiv\"",
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
		long[] data = { 5877866085510446992L, 288793313220230144L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_0 = new BitSet(mk_tokenSet_0());
	private static final long[] mk_tokenSet_1() {
		long[] data = { 562949956042752L, 288230378299195392L, 0L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_1 = new BitSet(mk_tokenSet_1());
	}
	
