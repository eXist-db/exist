// $ANTLR : "XPathParser2.g" -> "XPathTreeParser2.java"$

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
	import org.exist.xpath.value.Type;

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
	protected ArrayList exceptions = new ArrayList(2);
	protected boolean foundError = false;
	
	public XPathTreeParser2(StaticContext context) {
		this();
		this.context = context;
	}
	
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
				AST __t90 = _t;
				AST tmp1_AST_in = (AST)_t;
				match(_t,XPOINTER);
				_t = _t.getFirstChild();
				expr(_t,path);
				_t = _retTree;
				_t = __t90;
				_t = _t.getNextSibling();
				break;
			}
			case XPOINTER_ID:
			{
				AST __t91 = _t;
				AST tmp2_AST_in = (AST)_t;
				match(_t,XPOINTER_ID);
				_t = _t.getFirstChild();
				nc = (AST)_t;
				match(_t,NCNAME);
				_t = _t.getNextSibling();
				_t = __t91;
				_t = _t.getNextSibling();
				
							Function fun = new FunId();
							fun.addArgument(new Literal(nc.getText()));
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
		_retTree = _t;
	}
	
	public final void expr(AST _t,
		PathExpr path
	) throws RecognitionException, PermissionDeniedException,EXistException {
		
		AST expr_AST_in = (AST)_t;
		
			Expression step = null;
		
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case LITERAL_or:
		{
			AST __t94 = _t;
			AST tmp3_AST_in = (AST)_t;
			match(_t,LITERAL_or);
			_t = _t.getFirstChild();
			
						PathExpr left = new PathExpr();
						PathExpr right = new PathExpr();
					
			expr(_t,left);
			_t = _retTree;
			expr(_t,right);
			_t = _retTree;
			_t = __t94;
			_t = _t.getNextSibling();
			
						OpOr or = new OpOr();
						or.add(left);
						or.add(right);
						path.addPath(or);
					
			break;
		}
		case LITERAL_and:
		{
			AST __t95 = _t;
			AST tmp4_AST_in = (AST)_t;
			match(_t,LITERAL_and);
			_t = _t.getFirstChild();
			
						PathExpr left = new PathExpr();
						PathExpr right = new PathExpr();
					
			expr(_t,left);
			_t = _retTree;
			expr(_t,right);
			_t = _retTree;
			_t = __t95;
			_t = _t.getNextSibling();
			
						OpAnd and = new OpAnd();
						and.add(left);
						and.add(right);
						path.addPath(and);
					
			break;
		}
		case PARENTHESIZED:
		{
			AST __t96 = _t;
			AST tmp5_AST_in = (AST)_t;
			match(_t,PARENTHESIZED);
			_t = _t.getFirstChild();
			
						PathExpr expr = new PathExpr();
						path.addPath(expr);
					
			expr(_t,expr);
			_t = _retTree;
			_t = __t96;
			_t = _t.getNextSibling();
			break;
		}
		case UNION:
		{
			AST __t97 = _t;
			AST tmp6_AST_in = (AST)_t;
			match(_t,UNION);
			_t = _t.getFirstChild();
			
							PathExpr left = new PathExpr();
							PathExpr right = new PathExpr();
						
			expr(_t,left);
			_t = _retTree;
			expr(_t,right);
			_t = _retTree;
			_t = __t97;
			_t = _t.getNextSibling();
			
							Union union = new Union(left, right);
							path.addPath(union);
						
			break;
		}
		case ABSOLUTE_SLASH:
		{
			AST __t98 = _t;
			AST tmp7_AST_in = (AST)_t;
			match(_t,ABSOLUTE_SLASH);
			_t = _t.getFirstChild();
			
							RootNode root = new RootNode();
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
			case NCNAME:
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
			case STRING_LITERAL:
			case PLUS:
			case MINUS:
			case STAR:
			case LITERAL_div:
			case LITERAL_mod:
			case UNION:
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
			_t = __t98;
			_t = _t.getNextSibling();
			break;
		}
		case ABSOLUTE_DSLASH:
		{
			AST __t100 = _t;
			AST tmp8_AST_in = (AST)_t;
			match(_t,ABSOLUTE_DSLASH);
			_t = _t.getFirstChild();
			
							RootNode root = new RootNode();
							path.add(root);
						
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case QNAME:
			case WILDCARD:
			case PREFIX_WILDCARD:
			case FUNCTION:
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
				step=pathExpr(_t,path);
				_t = _retTree;
				
								if(step instanceof LocationStep) {
									LocationStep s = (LocationStep)step;
									if(s.getAxis() == Constants.ATTRIBUTE_AXIS)
										// combines descendant-or-self::node()/attribute:*
										s.setAxis(Constants.DESCENDANT_ATTRIBUTE_AXIS);
									else
										s.setAxis(Constants.DESCENDANT_AXIS);
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
			_t = __t100;
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
			step=pathExpr(_t,path);
			_t = _retTree;
			break;
		}
		case UNARY_MINUS:
		case UNARY_PLUS:
		case PLUS:
		case MINUS:
		case STAR:
		case LITERAL_div:
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
	}
	
	public final void xpath(AST _t,
		PathExpr path
	) throws RecognitionException {
		
		AST xpath_AST_in = (AST)_t;
		
		try {      // for error handling
			expr(_t,path);
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
		_retTree = _t;
	}
	
	public final Expression  pathExpr(AST _t,
		PathExpr path
	) throws RecognitionException, PermissionDeniedException,EXistException {
		Expression step;
		
		AST pathExpr_AST_in = (AST)_t;
		AST c = null;
		AST i = null;
		AST dec = null;
		AST dbl = null;
		AST qn = null;
		AST nc1 = null;
		AST nc = null;
		AST attr = null;
		AST nc2 = null;
		AST nc3 = null;
		
			Expression rightStep = null;
			step = null;
			int axis = Constants.CHILD_AXIS;
		
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case STRING_LITERAL:
		{
			c = (AST)_t;
			match(_t,STRING_LITERAL);
			_t = _t.getNextSibling();
			
						step = new Literal(c.getText());
						path.add(step);
					
			break;
		}
		case INTEGER_LITERAL:
		{
			i = (AST)_t;
			match(_t,INTEGER_LITERAL);
			_t = _t.getNextSibling();
			
						step = new IntNumber(Integer.parseInt(i.getText()));
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
				
								step = new IntNumber(Double.parseDouble(dec.getText()));
							
				break;
			}
			case DOUBLE_LITERAL:
			{
				dbl = (AST)_t;
				match(_t,DOUBLE_LITERAL);
				_t = _t.getNextSibling();
				
								step = new IntNumber(Double.parseDouble(dbl.getText()));
							
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
		case 51:
		case 52:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 55:
		case 56:
		{
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
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
				
								QName qname = QName.parse(context, qn.getText());
								test = new NameTest(Type.ELEMENT, qname);
							
				break;
			}
			case PREFIX_WILDCARD:
			{
				AST __t106 = _t;
				AST tmp9_AST_in = (AST)_t;
				match(_t,PREFIX_WILDCARD);
				_t = _t.getFirstChild();
				nc1 = (AST)_t;
				match(_t,NCNAME);
				_t = _t.getNextSibling();
				_t = __t106;
				_t = _t.getNextSibling();
				
								QName qname = new QName(nc1.getText(), null, null);
								test = new NameTest(Type.ELEMENT, qname);
							
				break;
			}
			case NCNAME:
			{
				AST __t107 = _t;
				nc = _t==ASTNULL ? null :(AST)_t;
				match(_t,NCNAME);
				_t = _t.getFirstChild();
				AST tmp10_AST_in = (AST)_t;
				match(_t,WILDCARD);
				_t = _t.getNextSibling();
				_t = __t107;
				_t = _t.getNextSibling();
				
								String namespaceURI = context.getURIForPrefix(nc.getText());
								QName qname = new QName(null, namespaceURI, null);
								test = new NameTest(Type.ELEMENT, qname);
							
				break;
			}
			case WILDCARD:
			{
				AST tmp11_AST_in = (AST)_t;
				match(_t,WILDCARD);
				_t = _t.getNextSibling();
				
								test = new TypeTest(Type.ELEMENT);
							
				break;
			}
			case LITERAL_node:
			{
				AST tmp12_AST_in = (AST)_t;
				match(_t,LITERAL_node);
				_t = _t.getNextSibling();
				
								test = new AnyNodeTest();
							
				break;
			}
			case LITERAL_text:
			{
				AST tmp13_AST_in = (AST)_t;
				match(_t,LITERAL_text);
				_t = _t.getNextSibling();
				
								test = new TypeTest(Type.TEXT);
							
				break;
			}
			default:
			{
				throw new NoViableAltException(_t);
			}
			}
			}
			
							step = new LocationStep(axis, test);
							path.add(step);
						
			{
			_loop109:
			do {
				if (_t==null) _t=ASTNULL;
				if ((_t.getType()==PREDICATE)) {
					predicate(_t,(LocationStep)step);
					_t = _retTree;
				}
				else {
					break _loop109;
				}
				
			} while (true);
			}
			break;
		}
		case AT:
		{
			AST tmp14_AST_in = (AST)_t;
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
				
								qname = QName.parse(context, attr.getText());
							
				break;
			}
			case PREFIX_WILDCARD:
			{
				AST __t111 = _t;
				AST tmp15_AST_in = (AST)_t;
				match(_t,PREFIX_WILDCARD);
				_t = _t.getFirstChild();
				nc2 = (AST)_t;
				match(_t,NCNAME);
				_t = _t.getNextSibling();
				_t = __t111;
				_t = _t.getNextSibling();
				
								qname = new QName(nc2.getText(), null, null);
							
				break;
			}
			case NCNAME:
			{
				AST __t112 = _t;
				nc3 = _t==ASTNULL ? null :(AST)_t;
				match(_t,NCNAME);
				_t = _t.getFirstChild();
				AST tmp16_AST_in = (AST)_t;
				match(_t,WILDCARD);
				_t = _t.getNextSibling();
				_t = __t112;
				_t = _t.getNextSibling();
				
								String namespaceURI = context.getURIForPrefix(nc3.getText());
								if(namespaceURI == null)
									throw new EXistException("No namespace defined for prefix " + nc.getText());
								qname = new QName(null, namespaceURI, null);
							
				break;
			}
			default:
			{
				throw new NoViableAltException(_t);
			}
			}
			}
				
						step = 
							new LocationStep(Constants.ATTRIBUTE_AXIS, 
								new NameTest(Type.ATTRIBUTE, qname));
						path.add(step);
					
			{
			_loop114:
			do {
				if (_t==null) _t=ASTNULL;
				if ((_t.getType()==PREDICATE)) {
					predicate(_t,(LocationStep)step);
					_t = _retTree;
				}
				else {
					break _loop114;
				}
				
			} while (true);
			}
			break;
		}
		case SELF:
		{
			AST tmp17_AST_in = (AST)_t;
			match(_t,SELF);
			_t = _t.getNextSibling();
			
						step = 
							new LocationStep(Constants.SELF_AXIS, new TypeTest(Type.NODE));
						path.add(step);
					
			{
			_loop116:
			do {
				if (_t==null) _t=ASTNULL;
				if ((_t.getType()==PREDICATE)) {
					predicate(_t,(LocationStep)step);
					_t = _retTree;
				}
				else {
					break _loop116;
				}
				
			} while (true);
			}
			break;
		}
		case PARENT:
		{
			AST tmp18_AST_in = (AST)_t;
			match(_t,PARENT);
			_t = _t.getNextSibling();
			
						step =
							new LocationStep(Constants.PARENT_AXIS, new TypeTest(Type.NODE));
						path.add(step);
					
			{
			_loop118:
			do {
				if (_t==null) _t=ASTNULL;
				if ((_t.getType()==PREDICATE)) {
					predicate(_t,(LocationStep)step);
					_t = _retTree;
				}
				else {
					break _loop118;
				}
				
			} while (true);
			}
			break;
		}
		case SLASH:
		{
			AST __t119 = _t;
			AST tmp19_AST_in = (AST)_t;
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
				rightStep=pathExpr(_t,path);
				_t = _retTree;
				
								if(rightStep instanceof LocationStep && ((LocationStep)rightStep).getAxis() == -1)
									((LocationStep)rightStep).setAxis(Constants.CHILD_AXIS);
							
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
			_t = __t119;
			_t = _t.getNextSibling();
			
						if(rightStep instanceof LocationStep && ((LocationStep)rightStep).getAxis() == -1)
							((LocationStep)step).setAxis(Constants.CHILD_AXIS);
					
			break;
		}
		case DSLASH:
		{
			AST __t121 = _t;
			AST tmp20_AST_in = (AST)_t;
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
				rightStep=pathExpr(_t,path);
				_t = _retTree;
				
								if(rightStep instanceof LocationStep) {
									LocationStep rs = (LocationStep)rightStep;
									if(rs.getAxis() == Constants.ATTRIBUTE_AXIS)
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
			_t = __t121;
			_t = _t.getNextSibling();
			
						if(step instanceof LocationStep)
							((LocationStep)step).setAxis(Constants.DESCENDANT_SELF_AXIS);
					
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
	) throws RecognitionException, PermissionDeniedException,EXistException {
		Expression step;
		
		AST generalComp_AST_in = (AST)_t;
		
			step = null;
			PathExpr left = new PathExpr();
			PathExpr right = new PathExpr();
		
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case EQ:
		{
			AST __t142 = _t;
			AST tmp21_AST_in = (AST)_t;
			match(_t,EQ);
			_t = _t.getFirstChild();
			expr(_t,left);
			_t = _retTree;
			expr(_t,right);
			_t = _retTree;
			
						step = new OpEquals(left, right, Constants.EQ);
						path.add(step);
					
			_t = __t142;
			_t = _t.getNextSibling();
			break;
		}
		case NEQ:
		{
			AST __t143 = _t;
			AST tmp22_AST_in = (AST)_t;
			match(_t,NEQ);
			_t = _t.getFirstChild();
			expr(_t,left);
			_t = _retTree;
			expr(_t,right);
			_t = _retTree;
			
						step = new OpEquals(left, right, Constants.NEQ);
						path.add(step);
					
			_t = __t143;
			_t = _t.getNextSibling();
			break;
		}
		case LT:
		{
			AST __t144 = _t;
			AST tmp23_AST_in = (AST)_t;
			match(_t,LT);
			_t = _t.getFirstChild();
			expr(_t,left);
			_t = _retTree;
			expr(_t,right);
			_t = _retTree;
			
						step = new OpEquals(left, right, Constants.LT);
						path.add(step);
					
			_t = __t144;
			_t = _t.getNextSibling();
			break;
		}
		case LTEQ:
		{
			AST __t145 = _t;
			AST tmp24_AST_in = (AST)_t;
			match(_t,LTEQ);
			_t = _t.getFirstChild();
			expr(_t,left);
			_t = _retTree;
			expr(_t,right);
			_t = _retTree;
			
						step = new OpEquals(left, right, Constants.LTEQ);
						path.add(step);
					
			_t = __t145;
			_t = _t.getNextSibling();
			break;
		}
		case GT:
		{
			AST __t146 = _t;
			AST tmp25_AST_in = (AST)_t;
			match(_t,GT);
			_t = _t.getFirstChild();
			expr(_t,left);
			_t = _retTree;
			expr(_t,right);
			_t = _retTree;
			
						step = new OpEquals(left, right, Constants.GT);
						path.add(step);
					
			_t = __t146;
			_t = _t.getNextSibling();
			break;
		}
		case GTEQ:
		{
			AST __t147 = _t;
			AST tmp26_AST_in = (AST)_t;
			match(_t,GTEQ);
			_t = _t.getFirstChild();
			expr(_t,left);
			_t = _retTree;
			expr(_t,right);
			_t = _retTree;
			
						step = new OpEquals(left, right, Constants.GTEQ);
						path.add(step);
					
			_t = __t147;
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
	) throws RecognitionException, PermissionDeniedException,EXistException {
		Expression step;
		
		AST fulltextComp_AST_in = (AST)_t;
		AST c = null;
		AST c2 = null;
		
			step = null;
			PathExpr left = new PathExpr();
		
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case ANDEQ:
		{
			AST __t139 = _t;
			AST tmp27_AST_in = (AST)_t;
			match(_t,ANDEQ);
			_t = _t.getFirstChild();
			expr(_t,left);
			_t = _retTree;
			c = (AST)_t;
			match(_t,STRING_LITERAL);
			_t = _t.getNextSibling();
			_t = __t139;
			_t = _t.getNextSibling();
			
						ExtFulltext exprCont = new ExtFulltext(Constants.FULLTEXT_AND);
				   	  	exprCont.setPath(left);
				   	  	exprCont.addTerms(context, c.getText());
						path.addPath(exprCont);
					
			break;
		}
		case OREQ:
		{
			AST __t140 = _t;
			AST tmp28_AST_in = (AST)_t;
			match(_t,OREQ);
			_t = _t.getFirstChild();
			expr(_t,left);
			_t = _retTree;
			c2 = (AST)_t;
			match(_t,STRING_LITERAL);
			_t = _t.getNextSibling();
			_t = __t140;
			_t = _t.getNextSibling();
			
						ExtFulltext exprCont = new ExtFulltext(Constants.FULLTEXT_OR);
				   	  	exprCont.setPath(left);
						exprCont.addTerms(context, c2.getText());
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
	) throws RecognitionException, PermissionDeniedException,EXistException {
		Expression step;
		
		AST numericExpr_AST_in = (AST)_t;
		
			step = null;
			PathExpr left = new PathExpr();
			PathExpr right = new PathExpr();
		
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case PLUS:
		{
			AST __t124 = _t;
			AST tmp29_AST_in = (AST)_t;
			match(_t,PLUS);
			_t = _t.getFirstChild();
			expr(_t,left);
			_t = _retTree;
			expr(_t,right);
			_t = _retTree;
			_t = __t124;
			_t = _t.getNextSibling();
			
						OpNumeric op = new OpNumeric(left, right, Constants.PLUS);
						path.addPath(op);
						step = op;
					
			break;
		}
		case MINUS:
		{
			AST __t125 = _t;
			AST tmp30_AST_in = (AST)_t;
			match(_t,MINUS);
			_t = _t.getFirstChild();
			expr(_t,left);
			_t = _retTree;
			expr(_t,right);
			_t = _retTree;
			_t = __t125;
			_t = _t.getNextSibling();
			
						OpNumeric op = new OpNumeric(left, right, Constants.MINUS);
						path.addPath(op);
						step = op;
					
			break;
		}
		case UNARY_MINUS:
		{
			AST __t126 = _t;
			AST tmp31_AST_in = (AST)_t;
			match(_t,UNARY_MINUS);
			_t = _t.getFirstChild();
			expr(_t,left);
			_t = _retTree;
			_t = __t126;
			_t = _t.getNextSibling();
			
						UnaryExpr unary = new UnaryExpr(Constants.MINUS);
						unary.add(left);
						path.addPath(unary);
						step = unary;
					
			break;
		}
		case UNARY_PLUS:
		{
			AST __t127 = _t;
			AST tmp32_AST_in = (AST)_t;
			match(_t,UNARY_PLUS);
			_t = _t.getFirstChild();
			expr(_t,left);
			_t = _retTree;
			_t = __t127;
			_t = _t.getNextSibling();
			
						UnaryExpr unary = new UnaryExpr(Constants.PLUS);
						unary.add(left);
						path.addPath(unary);
						step = unary;
					
			break;
		}
		case LITERAL_div:
		{
			AST __t128 = _t;
			AST tmp33_AST_in = (AST)_t;
			match(_t,LITERAL_div);
			_t = _t.getFirstChild();
			expr(_t,left);
			_t = _retTree;
			expr(_t,right);
			_t = _retTree;
			_t = __t128;
			_t = _t.getNextSibling();
			
						OpNumeric op = new OpNumeric(left, right, Constants.DIV);
						path.addPath(op);
						step = op;
					
			break;
		}
		case LITERAL_mod:
		{
			AST __t129 = _t;
			AST tmp34_AST_in = (AST)_t;
			match(_t,LITERAL_mod);
			_t = _t.getFirstChild();
			expr(_t,left);
			_t = _retTree;
			expr(_t,right);
			_t = _retTree;
			_t = __t129;
			_t = _t.getNextSibling();
			
						OpNumeric op = new OpNumeric(left, right, Constants.MOD);
						path.addPath(op);
						step = op;
					
			break;
		}
		case STAR:
		{
			AST __t130 = _t;
			AST tmp35_AST_in = (AST)_t;
			match(_t,STAR);
			_t = _t.getFirstChild();
			expr(_t,left);
			_t = _retTree;
			expr(_t,right);
			_t = _retTree;
			_t = __t130;
			_t = _t.getNextSibling();
			
						OpNumeric op = new OpNumeric(left, right, Constants.MULT);
						path.addPath(op);
						step = op;
					
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
	) throws RecognitionException, PermissionDeniedException,EXistException {
		Expression step;
		
		AST functionCall_AST_in = (AST)_t;
		AST fn = null;
		
			PathExpr pathExpr;
			step = null;
		
		
		AST __t134 = _t;
		fn = _t==ASTNULL ? null :(AST)_t;
		match(_t,FUNCTION);
		_t = _t.getFirstChild();
		
						Vector params = new Vector();
					
		{
		_loop136:
		do {
			if (_t==null) _t=ASTNULL;
			if ((_tokenSet_0.member(_t.getType()))) {
				
								pathExpr = new PathExpr(); 
							
				expr(_t,pathExpr);
				_t = _retTree;
				
								params.addElement(pathExpr);
							
			}
			else {
				break _loop136;
			}
			
		} while (true);
		}
		_t = __t134;
		_t = _t.getNextSibling();
		
					step = Util.createFunction(context, path, fn.getText(), params);
				
		_retTree = _t;
		return step;
	}
	
	public final int  forwardAxis(AST _t) throws RecognitionException, PermissionDeniedException,EXistException {
		int axis;
		
		AST forwardAxis_AST_in = (AST)_t;
		
			axis = -1;
		
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case LITERAL_child:
		{
			AST tmp36_AST_in = (AST)_t;
			match(_t,LITERAL_child);
			_t = _t.getNextSibling();
			axis = Constants.CHILD_AXIS;
			break;
		}
		case LITERAL_attribute:
		{
			AST tmp37_AST_in = (AST)_t;
			match(_t,LITERAL_attribute);
			_t = _t.getNextSibling();
			axis = Constants.ATTRIBUTE_AXIS;
			break;
		}
		case LITERAL_self:
		{
			AST tmp38_AST_in = (AST)_t;
			match(_t,LITERAL_self);
			_t = _t.getNextSibling();
			axis = Constants.SELF_AXIS;
			break;
		}
		case LITERAL_parent:
		{
			AST tmp39_AST_in = (AST)_t;
			match(_t,LITERAL_parent);
			_t = _t.getNextSibling();
			axis = Constants.PARENT_AXIS;
			break;
		}
		case LITERAL_descendant:
		{
			AST tmp40_AST_in = (AST)_t;
			match(_t,LITERAL_descendant);
			_t = _t.getNextSibling();
			axis = Constants.DESCENDANT_AXIS;
			break;
		}
		case 51:
		{
			AST tmp41_AST_in = (AST)_t;
			match(_t,51);
			_t = _t.getNextSibling();
			axis = Constants.DESCENDANT_SELF_AXIS;
			break;
		}
		case 52:
		{
			AST tmp42_AST_in = (AST)_t;
			match(_t,52);
			_t = _t.getNextSibling();
			axis = Constants.FOLLOWING_SIBLING_AXIS;
			break;
		}
		case 56:
		{
			AST tmp43_AST_in = (AST)_t;
			match(_t,56);
			_t = _t.getNextSibling();
			axis = Constants.PRECEDING_SIBLING_AXIS;
			break;
		}
		case LITERAL_ancestor:
		{
			AST tmp44_AST_in = (AST)_t;
			match(_t,LITERAL_ancestor);
			_t = _t.getNextSibling();
			axis = Constants.ANCESTOR_AXIS;
			break;
		}
		case 55:
		{
			AST tmp45_AST_in = (AST)_t;
			match(_t,55);
			_t = _t.getNextSibling();
			axis = Constants.ANCESTOR_SELF_AXIS;
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
	) throws RecognitionException, PermissionDeniedException,EXistException {
		
		AST predicate_AST_in = (AST)_t;
		
		AST __t132 = _t;
		AST tmp46_AST_in = (AST)_t;
		match(_t,PREDICATE);
		_t = _t.getFirstChild();
		Predicate predicateExpr = new Predicate();
		expr(_t,predicateExpr);
		_t = _retTree;
		
							step.addPredicate(predicateExpr); 
						
		_t = __t132;
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
	
	private static final long[] mk_tokenSet_0() {
		long[] data = { 1152890718280761296L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_0 = new BitSet(mk_tokenSet_0());
	}
	
