// $ANTLR 2.7.7 (2006-11-01): "XQueryTree.g" -> "XQueryTreeParser.java"$

	package org.exist.xquery.parser;

	import antlr.debug.misc.*;
	import java.io.StringReader;
	import java.io.BufferedReader;
	import java.io.InputStreamReader;
	import java.util.ArrayList;
	import java.util.List;
	import java.util.Iterator;
	import java.util.Map;
	import java.util.Set;
	import java.util.TreeSet;
	import java.util.HashMap;
	import java.util.Stack;
	import org.exist.storage.BrokerPool;
	import org.exist.storage.DBBroker;
	import org.exist.storage.analysis.Tokenizer;
	import org.exist.EXistException;
	import org.exist.Namespaces;
	import org.exist.dom.DocumentSet;
	import org.exist.dom.DocumentImpl;
	import org.exist.dom.QName;
	import org.exist.security.PermissionDeniedException;
	import org.exist.security.User;
	import org.exist.util.XMLChar;
	import org.exist.xquery.*;
	import org.exist.xquery.value.*;
	import org.exist.xquery.functions.*;
	import org.exist.xquery.update.*;

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
 * The tree parser: walks the AST created by {@link XQueryParser} and generates
 * an internal representation of the query in the form of XQuery expression objects.
 */
public class XQueryTreeParser extends antlr.TreeParser       implements XQueryTreeParserTokenTypes
 {

	private XQueryContext staticContext;
	private XQueryContext context;
	private ExternalModule myModule = null;
	protected ArrayList exceptions= new ArrayList(2);
	protected boolean foundError= false;
	protected Map declaredNamespaces = new HashMap();
	protected Set declaredGlobalVars = new TreeSet();
	
	public XQueryTreeParser(XQueryContext context) {
		this();
                this.staticContext = new XQueryContext(context);
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

	private void throwException(XQueryAST ast, String message) throws XPathException {
		throw new XPathException(ast, message);
	}
	
	private static class ForLetClause {
		XQueryAST ast;
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
	) throws RecognitionException, XPathException {
		
		org.exist.xquery.parser.XQueryAST xpointer_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		org.exist.xquery.parser.XQueryAST nc = null;
		Expression step = null;
		
		try {      // for error handling
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case XPOINTER:
			{
				AST __t2 = _t;
				org.exist.xquery.parser.XQueryAST tmp1_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,XPOINTER);
				_t = _t.getFirstChild();
				step=expr(_t,path);
				_t = _retTree;
				_t = __t2;
				_t = _t.getNextSibling();
				break;
			}
			case XPOINTER_ID:
			{
				AST __t3 = _t;
				org.exist.xquery.parser.XQueryAST tmp2_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,XPOINTER_ID);
				_t = _t.getFirstChild();
				nc = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,NCNAME);
				_t = _t.getNextSibling();
				_t = __t3;
				_t = _t.getNextSibling();
				
					    PathExpr p = new PathExpr(context);
						RootNode root = new RootNode(context);
						p.add(root);
						Function fun= new FunId(context, FunId.signature[0]);
						List params= new ArrayList(1);
						params.add(new LiteralValue(context, new StringValue(nc.getText())));
						fun.setArguments(params);
						p.addPath(fun);
						path.add(p);
					
				break;
			}
			default:
			{
				throw new NoViableAltException(_t);
			}
			}
		}
		catch (EXistException e) {
			handleException(e);
		}
		catch (PermissionDeniedException e) {
			handleException(e);
		}
		_retTree = _t;
	}
	
/**
 * Process a top-level expression like FLWOR, conditionals, comparisons etc.
 */
	public final Expression  expr(AST _t,
		PathExpr path
	) throws RecognitionException, PermissionDeniedException,EXistException,XPathException {
		Expression step;
		
		org.exist.xquery.parser.XQueryAST expr_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		org.exist.xquery.parser.XQueryAST eof = null;
		org.exist.xquery.parser.XQueryAST c = null;
		org.exist.xquery.parser.XQueryAST astIf = null;
		org.exist.xquery.parser.XQueryAST someVarName = null;
		org.exist.xquery.parser.XQueryAST everyVarName = null;
		org.exist.xquery.parser.XQueryAST r = null;
		org.exist.xquery.parser.XQueryAST f = null;
		org.exist.xquery.parser.XQueryAST varName = null;
		org.exist.xquery.parser.XQueryAST posVar = null;
		org.exist.xquery.parser.XQueryAST l = null;
		org.exist.xquery.parser.XQueryAST letVarName = null;
		org.exist.xquery.parser.XQueryAST w = null;
		org.exist.xquery.parser.XQueryAST toGroupVarName = null;
		org.exist.xquery.parser.XQueryAST groupVarName = null;
		org.exist.xquery.parser.XQueryAST groupKeyVarName = null;
		org.exist.xquery.parser.XQueryAST collURI = null;
		org.exist.xquery.parser.XQueryAST var = null;
		org.exist.xquery.parser.XQueryAST dvar = null;
		
			step= null;
		
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case EOF:
		{
			eof = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,Token.EOF_TYPE);
			_t = _t.getNextSibling();
			
			// Added for handling empty mainModule /ljo
			// System.out.println("EMPTY EXPR");
			if (eof.getText() == null || "".equals(eof.getText()))
			throw new XPathException("err:XPST0003: Parse error: The zero-length string is not a valid XPath expression.");     
			
			
			break;
		}
		case LITERAL_castable:
		case LITERAL_cast:
		{
			step=typeCastExpr(_t,path);
			_t = _retTree;
			break;
		}
		case COMMA:
		{
			AST __t88 = _t;
			c = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,COMMA);
			_t = _t.getFirstChild();
			
						PathExpr left= new PathExpr(context);
						PathExpr right= new PathExpr(context);
					
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			
						SequenceConstructor sc= new SequenceConstructor(context);
						sc.setASTNode(c);
						sc.addPath(left);
						sc.addPath(right);
						path.addPath(sc);
						step = sc;
					
			_t = __t88;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_if:
		{
			AST __t89 = _t;
			astIf = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
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
						cond.setASTNode(astIf);
						path.add(cond);
						step = cond;
					
			_t = __t89;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_some:
		{
			AST __t90 = _t;
			org.exist.xquery.parser.XQueryAST tmp3_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_some);
			_t = _t.getFirstChild();
			
						List clauses= new ArrayList();
						PathExpr satisfiesExpr = new PathExpr(context);
					
			{
			_loop95:
			do {
				if (_t==null) _t=ASTNULL;
				if ((_t.getType()==VARIABLE_BINDING)) {
					AST __t92 = _t;
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
						AST __t94 = _t;
						org.exist.xquery.parser.XQueryAST tmp4_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
						match(_t,LITERAL_as);
						_t = _t.getFirstChild();
						SequenceType type= new SequenceType();
						sequenceType(_t,type);
						_t = _retTree;
						_t = __t94;
						_t = _t.getNextSibling();
						clause.sequenceType = type;
						break;
					}
					case EOF:
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
					case ATTRIBUTE_TEST:
					case COMP_ELEM_CONSTRUCTOR:
					case COMP_ATTR_CONSTRUCTOR:
					case COMP_TEXT_CONSTRUCTOR:
					case COMP_COMMENT_CONSTRUCTOR:
					case COMP_PI_CONSTRUCTOR:
					case COMP_DOC_CONSTRUCTOR:
					case PRAGMA:
					case NCNAME:
					case EQ:
					case STRING_LITERAL:
					case LITERAL_element:
					case COMMA:
					case LCURLY:
					case STAR:
					case PLUS:
					case LITERAL_some:
					case LITERAL_every:
					case LITERAL_if:
					case LITERAL_typeswitch:
					case LITERAL_update:
					case LITERAL_preceding:
					case LITERAL_following:
					case LITERAL_return:
					case LITERAL_or:
					case LITERAL_and:
					case LITERAL_instance:
					case LITERAL_treat:
					case LITERAL_castable:
					case LITERAL_cast:
					case BEFORE:
					case AFTER:
					case LITERAL_eq:
					case LITERAL_ne:
					case LITERAL_lt:
					case LITERAL_le:
					case LITERAL_gt:
					case LITERAL_ge:
					case NEQ:
					case GT:
					case GTEQ:
					case LT:
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
					case 168:
					case 169:
					case SELF:
					case XML_COMMENT:
					case XML_PI:
					case AT:
					case PARENT:
					case LITERAL_child:
					case LITERAL_self:
					case LITERAL_descendant:
					case 181:
					case 182:
					case LITERAL_parent:
					case LITERAL_ancestor:
					case 185:
					case 186:
					case DOUBLE_LITERAL:
					case DECIMAL_LITERAL:
					case INTEGER_LITERAL:
					case XML_CDATA:
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
									
					_t = __t92;
					_t = _t.getNextSibling();
				}
				else {
					break _loop95;
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
					
			_t = __t90;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_every:
		{
			AST __t96 = _t;
			org.exist.xquery.parser.XQueryAST tmp5_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_every);
			_t = _t.getFirstChild();
			
						List clauses= new ArrayList();
						PathExpr satisfiesExpr = new PathExpr(context);
					
			{
			_loop101:
			do {
				if (_t==null) _t=ASTNULL;
				if ((_t.getType()==VARIABLE_BINDING)) {
					AST __t98 = _t;
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
						AST __t100 = _t;
						org.exist.xquery.parser.XQueryAST tmp6_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
						match(_t,LITERAL_as);
						_t = _t.getFirstChild();
						SequenceType type= new SequenceType();
						sequenceType(_t,type);
						_t = _retTree;
						_t = __t100;
						_t = _t.getNextSibling();
						clause.sequenceType = type;
						break;
					}
					case EOF:
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
					case ATTRIBUTE_TEST:
					case COMP_ELEM_CONSTRUCTOR:
					case COMP_ATTR_CONSTRUCTOR:
					case COMP_TEXT_CONSTRUCTOR:
					case COMP_COMMENT_CONSTRUCTOR:
					case COMP_PI_CONSTRUCTOR:
					case COMP_DOC_CONSTRUCTOR:
					case PRAGMA:
					case NCNAME:
					case EQ:
					case STRING_LITERAL:
					case LITERAL_element:
					case COMMA:
					case LCURLY:
					case STAR:
					case PLUS:
					case LITERAL_some:
					case LITERAL_every:
					case LITERAL_if:
					case LITERAL_typeswitch:
					case LITERAL_update:
					case LITERAL_preceding:
					case LITERAL_following:
					case LITERAL_return:
					case LITERAL_or:
					case LITERAL_and:
					case LITERAL_instance:
					case LITERAL_treat:
					case LITERAL_castable:
					case LITERAL_cast:
					case BEFORE:
					case AFTER:
					case LITERAL_eq:
					case LITERAL_ne:
					case LITERAL_lt:
					case LITERAL_le:
					case LITERAL_gt:
					case LITERAL_ge:
					case NEQ:
					case GT:
					case GTEQ:
					case LT:
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
					case 168:
					case 169:
					case SELF:
					case XML_COMMENT:
					case XML_PI:
					case AT:
					case PARENT:
					case LITERAL_child:
					case LITERAL_self:
					case LITERAL_descendant:
					case 181:
					case 182:
					case LITERAL_parent:
					case LITERAL_ancestor:
					case 185:
					case 186:
					case DOUBLE_LITERAL:
					case DECIMAL_LITERAL:
					case INTEGER_LITERAL:
					case XML_CDATA:
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
									
					_t = __t98;
					_t = _t.getNextSibling();
				}
				else {
					break _loop101;
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
					
			_t = __t96;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_return:
		{
			AST __t102 = _t;
			r = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_return);
			_t = _t.getFirstChild();
			
						List clauses= new ArrayList();
						Expression action= new PathExpr(context);
						action.setASTNode(r);
						PathExpr whereExpr= null;
						List orderBy= null;
			//bv : variables for groupBy 
			List groupBy= null; 
			String toGroupVar = null; 
			String groupVar = null; 
			String groupKeyVar = null; 			
					
			{
			int _cnt117=0;
			_loop117:
			do {
				if (_t==null) _t=ASTNULL;
				switch ( _t.getType()) {
				case LITERAL_for:
				{
					AST __t104 = _t;
					f = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
					match(_t,LITERAL_for);
					_t = _t.getFirstChild();
					{
					int _cnt110=0;
					_loop110:
					do {
						if (_t==null) _t=ASTNULL;
						if ((_t.getType()==VARIABLE_BINDING)) {
							AST __t106 = _t;
							varName = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
							match(_t,VARIABLE_BINDING);
							_t = _t.getFirstChild();
							
														ForLetClause clause= new ForLetClause();
														clause.ast = f;
														PathExpr inputSequence= new PathExpr(context);
													
							{
							if (_t==null) _t=ASTNULL;
							switch ( _t.getType()) {
							case LITERAL_as:
							{
								AST __t108 = _t;
								org.exist.xquery.parser.XQueryAST tmp7_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
								match(_t,LITERAL_as);
								_t = _t.getFirstChild();
								clause.sequenceType= new SequenceType();
								sequenceType(_t,clause.sequenceType);
								_t = _retTree;
								_t = __t108;
								_t = _t.getNextSibling();
								break;
							}
							case EOF:
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
							case ATTRIBUTE_TEST:
							case COMP_ELEM_CONSTRUCTOR:
							case COMP_ATTR_CONSTRUCTOR:
							case COMP_TEXT_CONSTRUCTOR:
							case COMP_COMMENT_CONSTRUCTOR:
							case COMP_PI_CONSTRUCTOR:
							case COMP_DOC_CONSTRUCTOR:
							case PRAGMA:
							case NCNAME:
							case EQ:
							case STRING_LITERAL:
							case LITERAL_element:
							case COMMA:
							case LCURLY:
							case STAR:
							case PLUS:
							case LITERAL_some:
							case LITERAL_every:
							case LITERAL_if:
							case LITERAL_typeswitch:
							case LITERAL_update:
							case LITERAL_preceding:
							case LITERAL_following:
							case LITERAL_return:
							case LITERAL_or:
							case LITERAL_and:
							case LITERAL_instance:
							case LITERAL_treat:
							case LITERAL_castable:
							case LITERAL_cast:
							case BEFORE:
							case AFTER:
							case LITERAL_eq:
							case LITERAL_ne:
							case LITERAL_lt:
							case LITERAL_le:
							case LITERAL_gt:
							case LITERAL_ge:
							case NEQ:
							case GT:
							case GTEQ:
							case LT:
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
							case 168:
							case 169:
							case SELF:
							case XML_COMMENT:
							case XML_PI:
							case AT:
							case PARENT:
							case LITERAL_child:
							case LITERAL_self:
							case LITERAL_descendant:
							case 181:
							case 182:
							case LITERAL_parent:
							case LITERAL_ancestor:
							case 185:
							case 186:
							case DOUBLE_LITERAL:
							case DECIMAL_LITERAL:
							case INTEGER_LITERAL:
							case XML_CDATA:
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
							case EOF:
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
							case ATTRIBUTE_TEST:
							case COMP_ELEM_CONSTRUCTOR:
							case COMP_ATTR_CONSTRUCTOR:
							case COMP_TEXT_CONSTRUCTOR:
							case COMP_COMMENT_CONSTRUCTOR:
							case COMP_PI_CONSTRUCTOR:
							case COMP_DOC_CONSTRUCTOR:
							case PRAGMA:
							case NCNAME:
							case EQ:
							case STRING_LITERAL:
							case LITERAL_element:
							case COMMA:
							case LCURLY:
							case STAR:
							case PLUS:
							case LITERAL_some:
							case LITERAL_every:
							case LITERAL_if:
							case LITERAL_typeswitch:
							case LITERAL_update:
							case LITERAL_preceding:
							case LITERAL_following:
							case LITERAL_return:
							case LITERAL_or:
							case LITERAL_and:
							case LITERAL_instance:
							case LITERAL_treat:
							case LITERAL_castable:
							case LITERAL_cast:
							case BEFORE:
							case AFTER:
							case LITERAL_eq:
							case LITERAL_ne:
							case LITERAL_lt:
							case LITERAL_le:
							case LITERAL_gt:
							case LITERAL_ge:
							case NEQ:
							case GT:
							case GTEQ:
							case LT:
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
							case 168:
							case 169:
							case SELF:
							case XML_COMMENT:
							case XML_PI:
							case AT:
							case PARENT:
							case LITERAL_child:
							case LITERAL_self:
							case LITERAL_descendant:
							case 181:
							case 182:
							case LITERAL_parent:
							case LITERAL_ancestor:
							case 185:
							case 186:
							case DOUBLE_LITERAL:
							case DECIMAL_LITERAL:
							case INTEGER_LITERAL:
							case XML_CDATA:
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
													
							_t = __t106;
							_t = _t.getNextSibling();
						}
						else {
							if ( _cnt110>=1 ) { break _loop110; } else {throw new NoViableAltException(_t);}
						}
						
						_cnt110++;
					} while (true);
					}
					_t = __t104;
					_t = _t.getNextSibling();
					break;
				}
				case LITERAL_let:
				{
					AST __t111 = _t;
					l = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
					match(_t,LITERAL_let);
					_t = _t.getFirstChild();
					{
					int _cnt116=0;
					_loop116:
					do {
						if (_t==null) _t=ASTNULL;
						if ((_t.getType()==VARIABLE_BINDING)) {
							AST __t113 = _t;
							letVarName = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
							match(_t,VARIABLE_BINDING);
							_t = _t.getFirstChild();
							
														ForLetClause clause= new ForLetClause();
														clause.ast = l;
														clause.isForClause= false;
														PathExpr inputSequence= new PathExpr(context);
													
							{
							if (_t==null) _t=ASTNULL;
							switch ( _t.getType()) {
							case LITERAL_as:
							{
								AST __t115 = _t;
								org.exist.xquery.parser.XQueryAST tmp8_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
								match(_t,LITERAL_as);
								_t = _t.getFirstChild();
								clause.sequenceType= new SequenceType();
								sequenceType(_t,clause.sequenceType);
								_t = _retTree;
								_t = __t115;
								_t = _t.getNextSibling();
								break;
							}
							case EOF:
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
							case ATTRIBUTE_TEST:
							case COMP_ELEM_CONSTRUCTOR:
							case COMP_ATTR_CONSTRUCTOR:
							case COMP_TEXT_CONSTRUCTOR:
							case COMP_COMMENT_CONSTRUCTOR:
							case COMP_PI_CONSTRUCTOR:
							case COMP_DOC_CONSTRUCTOR:
							case PRAGMA:
							case NCNAME:
							case EQ:
							case STRING_LITERAL:
							case LITERAL_element:
							case COMMA:
							case LCURLY:
							case STAR:
							case PLUS:
							case LITERAL_some:
							case LITERAL_every:
							case LITERAL_if:
							case LITERAL_typeswitch:
							case LITERAL_update:
							case LITERAL_preceding:
							case LITERAL_following:
							case LITERAL_return:
							case LITERAL_or:
							case LITERAL_and:
							case LITERAL_instance:
							case LITERAL_treat:
							case LITERAL_castable:
							case LITERAL_cast:
							case BEFORE:
							case AFTER:
							case LITERAL_eq:
							case LITERAL_ne:
							case LITERAL_lt:
							case LITERAL_le:
							case LITERAL_gt:
							case LITERAL_ge:
							case NEQ:
							case GT:
							case GTEQ:
							case LT:
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
							case 168:
							case 169:
							case SELF:
							case XML_COMMENT:
							case XML_PI:
							case AT:
							case PARENT:
							case LITERAL_child:
							case LITERAL_self:
							case LITERAL_descendant:
							case 181:
							case 182:
							case LITERAL_parent:
							case LITERAL_ancestor:
							case 185:
							case 186:
							case DOUBLE_LITERAL:
							case DECIMAL_LITERAL:
							case INTEGER_LITERAL:
							case XML_CDATA:
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
													
							_t = __t113;
							_t = _t.getNextSibling();
						}
						else {
							if ( _cnt116>=1 ) { break _loop116; } else {throw new NoViableAltException(_t);}
						}
						
						_cnt116++;
					} while (true);
					}
					_t = __t111;
					_t = _t.getNextSibling();
					break;
				}
				default:
				{
					if ( _cnt117>=1 ) { break _loop117; } else {throw new NoViableAltException(_t);}
				}
				}
				_cnt117++;
			} while (true);
			}
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case LITERAL_where:
			{
				w = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,LITERAL_where);
				_t = _t.getNextSibling();
				
								whereExpr= new PathExpr(context); 
								whereExpr.setASTNode(w);
							
				step=expr(_t,whereExpr);
				_t = _retTree;
				break;
			}
			case EOF:
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
			case GROUP_BY:
			case ATTRIBUTE_TEST:
			case COMP_ELEM_CONSTRUCTOR:
			case COMP_ATTR_CONSTRUCTOR:
			case COMP_TEXT_CONSTRUCTOR:
			case COMP_COMMENT_CONSTRUCTOR:
			case COMP_PI_CONSTRUCTOR:
			case COMP_DOC_CONSTRUCTOR:
			case PRAGMA:
			case NCNAME:
			case EQ:
			case STRING_LITERAL:
			case LITERAL_element:
			case COMMA:
			case LCURLY:
			case STAR:
			case PLUS:
			case LITERAL_some:
			case LITERAL_every:
			case LITERAL_if:
			case LITERAL_typeswitch:
			case LITERAL_update:
			case LITERAL_preceding:
			case LITERAL_following:
			case LITERAL_return:
			case LITERAL_or:
			case LITERAL_and:
			case LITERAL_instance:
			case LITERAL_treat:
			case LITERAL_castable:
			case LITERAL_cast:
			case BEFORE:
			case AFTER:
			case LITERAL_eq:
			case LITERAL_ne:
			case LITERAL_lt:
			case LITERAL_le:
			case LITERAL_gt:
			case LITERAL_ge:
			case NEQ:
			case GT:
			case GTEQ:
			case LT:
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
			case 168:
			case 169:
			case SELF:
			case XML_COMMENT:
			case XML_PI:
			case AT:
			case PARENT:
			case LITERAL_child:
			case LITERAL_self:
			case LITERAL_descendant:
			case 181:
			case 182:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 185:
			case 186:
			case DOUBLE_LITERAL:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
			case XML_CDATA:
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
			case GROUP_BY:
			{
				AST __t120 = _t;
				org.exist.xquery.parser.XQueryAST tmp9_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,GROUP_BY);
				_t = _t.getFirstChild();
				groupBy= new ArrayList(3);
				{
				AST __t122 = _t;
				toGroupVarName = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
				match(_t,VARIABLE_REF);
				_t = _t.getFirstChild();
				toGroupVar= toGroupVarName.getText();
				_t = __t122;
				_t = _t.getNextSibling();
				}
				{
				AST __t124 = _t;
				groupVarName = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
				match(_t,VARIABLE_BINDING);
				_t = _t.getFirstChild();
				groupVar= groupVarName.getText();
				_t = __t124;
				_t = _t.getNextSibling();
				}
				{
				int _cnt127=0;
				_loop127:
				do {
					if (_t==null) _t=ASTNULL;
					if ((_tokenSet_0.member(_t.getType()))) {
						PathExpr groupSpecExpr= new PathExpr(context);
						step=expr(_t,groupSpecExpr);
						_t = _retTree;
						
						
						AST __t126 = _t;
						groupKeyVarName = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
						match(_t,VARIABLE_BINDING);
						_t = _t.getFirstChild();
						groupKeyVar = groupKeyVarName.getText(); 
						GroupSpec groupSpec= new GroupSpec(context, groupSpecExpr, groupKeyVar); 
						groupBy.add(groupSpec); 
						
						_t = __t126;
						_t = _t.getNextSibling();
					}
					else {
						if ( _cnt127>=1 ) { break _loop127; } else {throw new NoViableAltException(_t);}
					}
					
					_cnt127++;
				} while (true);
				}
				_t = __t120;
				_t = _t.getNextSibling();
				break;
			}
			case EOF:
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
			case ATTRIBUTE_TEST:
			case COMP_ELEM_CONSTRUCTOR:
			case COMP_ATTR_CONSTRUCTOR:
			case COMP_TEXT_CONSTRUCTOR:
			case COMP_COMMENT_CONSTRUCTOR:
			case COMP_PI_CONSTRUCTOR:
			case COMP_DOC_CONSTRUCTOR:
			case PRAGMA:
			case NCNAME:
			case EQ:
			case STRING_LITERAL:
			case LITERAL_element:
			case COMMA:
			case LCURLY:
			case STAR:
			case PLUS:
			case LITERAL_some:
			case LITERAL_every:
			case LITERAL_if:
			case LITERAL_typeswitch:
			case LITERAL_update:
			case LITERAL_preceding:
			case LITERAL_following:
			case LITERAL_return:
			case LITERAL_or:
			case LITERAL_and:
			case LITERAL_instance:
			case LITERAL_treat:
			case LITERAL_castable:
			case LITERAL_cast:
			case BEFORE:
			case AFTER:
			case LITERAL_eq:
			case LITERAL_ne:
			case LITERAL_lt:
			case LITERAL_le:
			case LITERAL_gt:
			case LITERAL_ge:
			case NEQ:
			case GT:
			case GTEQ:
			case LT:
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
			case 168:
			case 169:
			case SELF:
			case XML_COMMENT:
			case XML_PI:
			case AT:
			case PARENT:
			case LITERAL_child:
			case LITERAL_self:
			case LITERAL_descendant:
			case 181:
			case 182:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 185:
			case 186:
			case DOUBLE_LITERAL:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
			case XML_CDATA:
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
				AST __t129 = _t;
				org.exist.xquery.parser.XQueryAST tmp10_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,ORDER_BY);
				_t = _t.getFirstChild();
				orderBy= new ArrayList(3);
				{
				int _cnt136=0;
				_loop136:
				do {
					if (_t==null) _t=ASTNULL;
					if ((_tokenSet_0.member(_t.getType()))) {
						PathExpr orderSpecExpr= new PathExpr(context);
						step=expr(_t,orderSpecExpr);
						_t = _retTree;
						
												OrderSpec orderSpec= new OrderSpec(context, orderSpecExpr);
												int modifiers= 0;
												boolean orderDescending = false; 
												orderBy.add(orderSpec);
						
						if (!context.orderEmptyGreatest()) {
						modifiers |= OrderSpec.EMPTY_LEAST;
						orderSpec.setModifiers(modifiers);
						}
											
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
								org.exist.xquery.parser.XQueryAST tmp11_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
								match(_t,LITERAL_ascending);
								_t = _t.getNextSibling();
								break;
							}
							case LITERAL_descending:
							{
								org.exist.xquery.parser.XQueryAST tmp12_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
								match(_t,LITERAL_descending);
								_t = _t.getNextSibling();
								
																modifiers |= OrderSpec.DESCENDING_ORDER;
																orderSpec.setModifiers(modifiers);
								orderDescending = true;
															
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
						case EOF:
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
						case ATTRIBUTE_TEST:
						case COMP_ELEM_CONSTRUCTOR:
						case COMP_ATTR_CONSTRUCTOR:
						case COMP_TEXT_CONSTRUCTOR:
						case COMP_COMMENT_CONSTRUCTOR:
						case COMP_PI_CONSTRUCTOR:
						case COMP_DOC_CONSTRUCTOR:
						case PRAGMA:
						case NCNAME:
						case EQ:
						case STRING_LITERAL:
						case LITERAL_collation:
						case LITERAL_element:
						case LITERAL_empty:
						case COMMA:
						case LCURLY:
						case STAR:
						case PLUS:
						case LITERAL_some:
						case LITERAL_every:
						case LITERAL_if:
						case LITERAL_typeswitch:
						case LITERAL_update:
						case LITERAL_preceding:
						case LITERAL_following:
						case LITERAL_return:
						case LITERAL_or:
						case LITERAL_and:
						case LITERAL_instance:
						case LITERAL_treat:
						case LITERAL_castable:
						case LITERAL_cast:
						case BEFORE:
						case AFTER:
						case LITERAL_eq:
						case LITERAL_ne:
						case LITERAL_lt:
						case LITERAL_le:
						case LITERAL_gt:
						case LITERAL_ge:
						case NEQ:
						case GT:
						case GTEQ:
						case LT:
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
						case 168:
						case 169:
						case SELF:
						case XML_COMMENT:
						case XML_PI:
						case AT:
						case PARENT:
						case LITERAL_child:
						case LITERAL_self:
						case LITERAL_descendant:
						case 181:
						case 182:
						case LITERAL_parent:
						case LITERAL_ancestor:
						case 185:
						case 186:
						case DOUBLE_LITERAL:
						case DECIMAL_LITERAL:
						case INTEGER_LITERAL:
						case XML_CDATA:
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
							org.exist.xquery.parser.XQueryAST tmp13_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
							match(_t,LITERAL_empty);
							_t = _t.getNextSibling();
							{
							if (_t==null) _t=ASTNULL;
							switch ( _t.getType()) {
							case LITERAL_greatest:
							{
								org.exist.xquery.parser.XQueryAST tmp14_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
								match(_t,LITERAL_greatest);
								_t = _t.getNextSibling();
								
								if (!context.orderEmptyGreatest())
								modifiers &= OrderSpec.EMPTY_GREATEST;
								if (orderDescending)
								modifiers |= OrderSpec.DESCENDING_ORDER;
								orderSpec.setModifiers(modifiers);
								
								break;
							}
							case LITERAL_least:
							{
								org.exist.xquery.parser.XQueryAST tmp15_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
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
						case EOF:
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
						case ATTRIBUTE_TEST:
						case COMP_ELEM_CONSTRUCTOR:
						case COMP_ATTR_CONSTRUCTOR:
						case COMP_TEXT_CONSTRUCTOR:
						case COMP_COMMENT_CONSTRUCTOR:
						case COMP_PI_CONSTRUCTOR:
						case COMP_DOC_CONSTRUCTOR:
						case PRAGMA:
						case NCNAME:
						case EQ:
						case STRING_LITERAL:
						case LITERAL_collation:
						case LITERAL_element:
						case COMMA:
						case LCURLY:
						case STAR:
						case PLUS:
						case LITERAL_some:
						case LITERAL_every:
						case LITERAL_if:
						case LITERAL_typeswitch:
						case LITERAL_update:
						case LITERAL_preceding:
						case LITERAL_following:
						case LITERAL_return:
						case LITERAL_or:
						case LITERAL_and:
						case LITERAL_instance:
						case LITERAL_treat:
						case LITERAL_castable:
						case LITERAL_cast:
						case BEFORE:
						case AFTER:
						case LITERAL_eq:
						case LITERAL_ne:
						case LITERAL_lt:
						case LITERAL_le:
						case LITERAL_gt:
						case LITERAL_ge:
						case NEQ:
						case GT:
						case GTEQ:
						case LT:
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
						case 168:
						case 169:
						case SELF:
						case XML_COMMENT:
						case XML_PI:
						case AT:
						case PARENT:
						case LITERAL_child:
						case LITERAL_self:
						case LITERAL_descendant:
						case 181:
						case 182:
						case LITERAL_parent:
						case LITERAL_ancestor:
						case 185:
						case 186:
						case DOUBLE_LITERAL:
						case DECIMAL_LITERAL:
						case INTEGER_LITERAL:
						case XML_CDATA:
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
						case LITERAL_collation:
						{
							org.exist.xquery.parser.XQueryAST tmp16_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
							match(_t,LITERAL_collation);
							_t = _t.getNextSibling();
							collURI = (org.exist.xquery.parser.XQueryAST)_t;
							match(_t,STRING_LITERAL);
							_t = _t.getNextSibling();
							
														orderSpec.setCollation(collURI.getText());
													
							break;
						}
						case EOF:
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
						case ATTRIBUTE_TEST:
						case COMP_ELEM_CONSTRUCTOR:
						case COMP_ATTR_CONSTRUCTOR:
						case COMP_TEXT_CONSTRUCTOR:
						case COMP_COMMENT_CONSTRUCTOR:
						case COMP_PI_CONSTRUCTOR:
						case COMP_DOC_CONSTRUCTOR:
						case PRAGMA:
						case NCNAME:
						case EQ:
						case STRING_LITERAL:
						case LITERAL_element:
						case COMMA:
						case LCURLY:
						case STAR:
						case PLUS:
						case LITERAL_some:
						case LITERAL_every:
						case LITERAL_if:
						case LITERAL_typeswitch:
						case LITERAL_update:
						case LITERAL_preceding:
						case LITERAL_following:
						case LITERAL_return:
						case LITERAL_or:
						case LITERAL_and:
						case LITERAL_instance:
						case LITERAL_treat:
						case LITERAL_castable:
						case LITERAL_cast:
						case BEFORE:
						case AFTER:
						case LITERAL_eq:
						case LITERAL_ne:
						case LITERAL_lt:
						case LITERAL_le:
						case LITERAL_gt:
						case LITERAL_ge:
						case NEQ:
						case GT:
						case GTEQ:
						case LT:
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
						case 168:
						case 169:
						case SELF:
						case XML_COMMENT:
						case XML_PI:
						case AT:
						case PARENT:
						case LITERAL_child:
						case LITERAL_self:
						case LITERAL_descendant:
						case 181:
						case 182:
						case LITERAL_parent:
						case LITERAL_ancestor:
						case 185:
						case 186:
						case DOUBLE_LITERAL:
						case DECIMAL_LITERAL:
						case INTEGER_LITERAL:
						case XML_CDATA:
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
						if ( _cnt136>=1 ) { break _loop136; } else {throw new NoViableAltException(_t);}
					}
					
					_cnt136++;
				} while (true);
				}
				_t = __t129;
				_t = _t.getNextSibling();
				break;
			}
			case EOF:
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
			case ATTRIBUTE_TEST:
			case COMP_ELEM_CONSTRUCTOR:
			case COMP_ATTR_CONSTRUCTOR:
			case COMP_TEXT_CONSTRUCTOR:
			case COMP_COMMENT_CONSTRUCTOR:
			case COMP_PI_CONSTRUCTOR:
			case COMP_DOC_CONSTRUCTOR:
			case PRAGMA:
			case NCNAME:
			case EQ:
			case STRING_LITERAL:
			case LITERAL_element:
			case COMMA:
			case LCURLY:
			case STAR:
			case PLUS:
			case LITERAL_some:
			case LITERAL_every:
			case LITERAL_if:
			case LITERAL_typeswitch:
			case LITERAL_update:
			case LITERAL_preceding:
			case LITERAL_following:
			case LITERAL_return:
			case LITERAL_or:
			case LITERAL_and:
			case LITERAL_instance:
			case LITERAL_treat:
			case LITERAL_castable:
			case LITERAL_cast:
			case BEFORE:
			case AFTER:
			case LITERAL_eq:
			case LITERAL_ne:
			case LITERAL_lt:
			case LITERAL_le:
			case LITERAL_gt:
			case LITERAL_ge:
			case NEQ:
			case GT:
			case GTEQ:
			case LT:
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
			case 168:
			case 169:
			case SELF:
			case XML_COMMENT:
			case XML_PI:
			case AT:
			case PARENT:
			case LITERAL_child:
			case LITERAL_self:
			case LITERAL_descendant:
			case 181:
			case 182:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 185:
			case 186:
			case DOUBLE_LITERAL:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
			case XML_CDATA:
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
			
			//bv : save the "real" return expression (used in groupBy) 
			PathExpr groupReturnExpr = (PathExpr) action; 
			
						for (int i= clauses.size() - 1; i >= 0; i--) {
							ForLetClause clause= (ForLetClause) clauses.get(i);
							BindingExpression expr;
							if (clause.isForClause)
								expr= new ForExpr(context);
							else
								expr= new LetExpr(context);
							expr.setASTNode(clause.ast);
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
			// bv : group by initialisation 
			if (groupBy != null) { 
			GroupSpec groupSpecs[]= new GroupSpec[groupBy.size()]; 
			int k= 0; 
			for (Iterator j= groupBy.iterator(); j.hasNext(); k++) { 
			GroupSpec groupSpec= (GroupSpec) j.next(); 
			groupSpecs[k]= groupSpec; 
			} 
			((BindingExpression)action).setGroupSpecs(groupSpecs); 
			((BindingExpression)action).setGroupVariable(groupVar); 
			((BindingExpression)action).setGroupReturnExpr(groupReturnExpr); 
			((BindingExpression)action).setToGroupVariable(toGroupVar); 
			} 
			
						path.add(action);
						step = action;
					
			_t = __t102;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_instance:
		{
			AST __t137 = _t;
			org.exist.xquery.parser.XQueryAST tmp17_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
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
					
			_t = __t137;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_treat:
		{
			AST __t138 = _t;
			org.exist.xquery.parser.XQueryAST tmp18_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_treat);
			_t = _t.getFirstChild();
			
						PathExpr expr = new PathExpr(context);
						SequenceType type= new SequenceType(); 
					
			step=expr(_t,expr);
			_t = _retTree;
			sequenceType(_t,type);
			_t = _retTree;
			
						step = new TreatAsExpression(context, expr, type); 
						path.add(step);
					
			_t = __t138;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_typeswitch:
		{
			AST __t139 = _t;
			org.exist.xquery.parser.XQueryAST tmp19_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_typeswitch);
			_t = _t.getFirstChild();
			
						PathExpr operand = new PathExpr(context);
					
			step=expr(_t,operand);
			_t = _retTree;
			
						TypeswitchExpression tswitch = new TypeswitchExpression(context, operand);
						path.add(tswitch); 
					
			{
			int _cnt144=0;
			_loop144:
			do {
				if (_t==null) _t=ASTNULL;
				if ((_t.getType()==LITERAL_case)) {
					
									SequenceType type = new SequenceType();
									PathExpr returnExpr = new PathExpr(context);
									QName qn = null;
								
					AST __t141 = _t;
					org.exist.xquery.parser.XQueryAST tmp20_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
					match(_t,LITERAL_case);
					_t = _t.getFirstChild();
					{
					if (_t==null) _t=ASTNULL;
					switch ( _t.getType()) {
					case VARIABLE_BINDING:
					{
						var = (org.exist.xquery.parser.XQueryAST)_t;
						match(_t,VARIABLE_BINDING);
						_t = _t.getNextSibling();
						qn = QName.parse(staticContext, var.getText());
						break;
					}
					case ATOMIC_TYPE:
					case ATTRIBUTE_TEST:
					case LITERAL_element:
					case LITERAL_empty:
					case 94:
					case LITERAL_item:
					case LITERAL_text:
					case LITERAL_node:
					case LITERAL_comment:
					case 168:
					case 169:
					{
						break;
					}
					default:
					{
						throw new NoViableAltException(_t);
					}
					}
					}
					sequenceType(_t,type);
					_t = _retTree;
					AST __t143 = _t;
					org.exist.xquery.parser.XQueryAST tmp21_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
					match(_t,LITERAL_return);
					_t = _t.getFirstChild();
					step=expr(_t,returnExpr);
					_t = _retTree;
					tswitch.addCase(type, qn, returnExpr);
					_t = __t143;
					_t = _t.getNextSibling();
					_t = __t141;
					_t = _t.getNextSibling();
				}
				else {
					if ( _cnt144>=1 ) { break _loop144; } else {throw new NoViableAltException(_t);}
				}
				
				_cnt144++;
			} while (true);
			}
			{
			org.exist.xquery.parser.XQueryAST tmp22_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_default);
			_t = _t.getNextSibling();
			
							PathExpr returnExpr = new PathExpr(context);
							QName qn = null;
						
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case VARIABLE_BINDING:
			{
				dvar = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,VARIABLE_BINDING);
				_t = _t.getNextSibling();
				qn = QName.parse(staticContext, dvar.getText());
				break;
			}
			case EOF:
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
			case ATTRIBUTE_TEST:
			case COMP_ELEM_CONSTRUCTOR:
			case COMP_ATTR_CONSTRUCTOR:
			case COMP_TEXT_CONSTRUCTOR:
			case COMP_COMMENT_CONSTRUCTOR:
			case COMP_PI_CONSTRUCTOR:
			case COMP_DOC_CONSTRUCTOR:
			case PRAGMA:
			case NCNAME:
			case EQ:
			case STRING_LITERAL:
			case LITERAL_element:
			case COMMA:
			case LCURLY:
			case STAR:
			case PLUS:
			case LITERAL_some:
			case LITERAL_every:
			case LITERAL_if:
			case LITERAL_typeswitch:
			case LITERAL_update:
			case LITERAL_preceding:
			case LITERAL_following:
			case LITERAL_return:
			case LITERAL_or:
			case LITERAL_and:
			case LITERAL_instance:
			case LITERAL_treat:
			case LITERAL_castable:
			case LITERAL_cast:
			case BEFORE:
			case AFTER:
			case LITERAL_eq:
			case LITERAL_ne:
			case LITERAL_lt:
			case LITERAL_le:
			case LITERAL_gt:
			case LITERAL_ge:
			case NEQ:
			case GT:
			case GTEQ:
			case LT:
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
			case 168:
			case 169:
			case SELF:
			case XML_COMMENT:
			case XML_PI:
			case AT:
			case PARENT:
			case LITERAL_child:
			case LITERAL_self:
			case LITERAL_descendant:
			case 181:
			case 182:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 185:
			case 186:
			case DOUBLE_LITERAL:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
			case XML_CDATA:
			{
				break;
			}
			default:
			{
				throw new NoViableAltException(_t);
			}
			}
			}
			step=expr(_t,returnExpr);
			_t = _retTree;
			
							tswitch.setDefault(qn, returnExpr);
						
			}
			step = tswitch;
			_t = __t139;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_or:
		{
			AST __t147 = _t;
			org.exist.xquery.parser.XQueryAST tmp23_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_or);
			_t = _t.getFirstChild();
			
						PathExpr left= new PathExpr(context);
						PathExpr right= new PathExpr(context);
					
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			_t = __t147;
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
			AST __t148 = _t;
			org.exist.xquery.parser.XQueryAST tmp24_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_and);
			_t = _t.getFirstChild();
			
						PathExpr left= new PathExpr(context);
						PathExpr right= new PathExpr(context);
					
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			_t = __t148;
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
			AST __t149 = _t;
			org.exist.xquery.parser.XQueryAST tmp25_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,UNION);
			_t = _t.getFirstChild();
			
						PathExpr left= new PathExpr(context);
						PathExpr right= new PathExpr(context);
					
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			_t = __t149;
			_t = _t.getNextSibling();
			
					Union union= new Union(context, left, right);
					path.add(union);
					step = union;
				
			break;
		}
		case LITERAL_intersect:
		{
			AST __t150 = _t;
			org.exist.xquery.parser.XQueryAST tmp26_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_intersect);
			_t = _t.getFirstChild();
			
						PathExpr left = new PathExpr(context);
						PathExpr right = new PathExpr(context);
					
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			_t = __t150;
			_t = _t.getNextSibling();
			
					Intersection intersect = new Intersection(context, left, right);
					path.add(intersect);
					step = intersect;
				
			break;
		}
		case LITERAL_except:
		{
			AST __t151 = _t;
			org.exist.xquery.parser.XQueryAST tmp27_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_except);
			_t = _t.getFirstChild();
			
						PathExpr left = new PathExpr(context);
						PathExpr right = new PathExpr(context);
					
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			_t = __t151;
			_t = _t.getNextSibling();
			
					Except intersect = new Except(context, left, right);
					path.add(intersect);
					step = intersect;
				
			break;
		}
		case ABSOLUTE_SLASH:
		{
			AST __t152 = _t;
			org.exist.xquery.parser.XQueryAST tmp28_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,ABSOLUTE_SLASH);
			_t = _t.getFirstChild();
			
						RootNode root= new RootNode(context);
						path.add(root);
					
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case EOF:
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
			case ATTRIBUTE_TEST:
			case COMP_ELEM_CONSTRUCTOR:
			case COMP_ATTR_CONSTRUCTOR:
			case COMP_TEXT_CONSTRUCTOR:
			case COMP_COMMENT_CONSTRUCTOR:
			case COMP_PI_CONSTRUCTOR:
			case COMP_DOC_CONSTRUCTOR:
			case PRAGMA:
			case NCNAME:
			case EQ:
			case STRING_LITERAL:
			case LITERAL_element:
			case COMMA:
			case LCURLY:
			case STAR:
			case PLUS:
			case LITERAL_some:
			case LITERAL_every:
			case LITERAL_if:
			case LITERAL_typeswitch:
			case LITERAL_update:
			case LITERAL_preceding:
			case LITERAL_following:
			case LITERAL_return:
			case LITERAL_or:
			case LITERAL_and:
			case LITERAL_instance:
			case LITERAL_treat:
			case LITERAL_castable:
			case LITERAL_cast:
			case BEFORE:
			case AFTER:
			case LITERAL_eq:
			case LITERAL_ne:
			case LITERAL_lt:
			case LITERAL_le:
			case LITERAL_gt:
			case LITERAL_ge:
			case NEQ:
			case GT:
			case GTEQ:
			case LT:
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
			case 168:
			case 169:
			case SELF:
			case XML_COMMENT:
			case XML_PI:
			case AT:
			case PARENT:
			case LITERAL_child:
			case LITERAL_self:
			case LITERAL_descendant:
			case 181:
			case 182:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 185:
			case 186:
			case DOUBLE_LITERAL:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
			case XML_CDATA:
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
			_t = __t152;
			_t = _t.getNextSibling();
			break;
		}
		case ABSOLUTE_DSLASH:
		{
			AST __t154 = _t;
			org.exist.xquery.parser.XQueryAST tmp29_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,ABSOLUTE_DSLASH);
			_t = _t.getFirstChild();
			
						RootNode root= new RootNode(context);
						path.add(root);
					
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case EOF:
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
			case ATTRIBUTE_TEST:
			case COMP_ELEM_CONSTRUCTOR:
			case COMP_ATTR_CONSTRUCTOR:
			case COMP_TEXT_CONSTRUCTOR:
			case COMP_COMMENT_CONSTRUCTOR:
			case COMP_PI_CONSTRUCTOR:
			case COMP_DOC_CONSTRUCTOR:
			case PRAGMA:
			case NCNAME:
			case EQ:
			case STRING_LITERAL:
			case LITERAL_element:
			case COMMA:
			case LCURLY:
			case STAR:
			case PLUS:
			case LITERAL_some:
			case LITERAL_every:
			case LITERAL_if:
			case LITERAL_typeswitch:
			case LITERAL_update:
			case LITERAL_preceding:
			case LITERAL_following:
			case LITERAL_return:
			case LITERAL_or:
			case LITERAL_and:
			case LITERAL_instance:
			case LITERAL_treat:
			case LITERAL_castable:
			case LITERAL_cast:
			case BEFORE:
			case AFTER:
			case LITERAL_eq:
			case LITERAL_ne:
			case LITERAL_lt:
			case LITERAL_le:
			case LITERAL_gt:
			case LITERAL_ge:
			case NEQ:
			case GT:
			case GTEQ:
			case LT:
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
			case 168:
			case 169:
			case SELF:
			case XML_COMMENT:
			case XML_PI:
			case AT:
			case PARENT:
			case LITERAL_child:
			case LITERAL_self:
			case LITERAL_descendant:
			case 181:
			case 182:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 185:
			case 186:
			case DOUBLE_LITERAL:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
			case XML_CDATA:
			{
				step=expr(_t,path);
				_t = _retTree;
				
								if (step instanceof LocationStep) {
									LocationStep s= (LocationStep) step;
									if (s.getAxis() == Constants.ATTRIBUTE_AXIS ||
										s.getTest().getType() == Type.ATTRIBUTE)
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
			_t = __t154;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_to:
		{
			AST __t156 = _t;
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
					
			_t = __t156;
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
		case COMP_DOC_CONSTRUCTOR:
		case STRING_LITERAL:
		case LCURLY:
		case XML_COMMENT:
		case XML_PI:
		case DOUBLE_LITERAL:
		case DECIMAL_LITERAL:
		case INTEGER_LITERAL:
		case XML_CDATA:
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
		case LITERAL_preceding:
		case LITERAL_following:
		case SLASH:
		case DSLASH:
		case LITERAL_text:
		case LITERAL_node:
		case LITERAL_attribute:
		case LITERAL_comment:
		case 168:
		case 169:
		case SELF:
		case AT:
		case PARENT:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_descendant:
		case 181:
		case 182:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 185:
		case 186:
		{
			step=pathExpr(_t,path);
			_t = _retTree;
			break;
		}
		case PRAGMA:
		{
			step=extensionExpr(_t,path);
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
		case LITERAL_update:
		{
			step=updateExpr(_t,path);
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
	) throws RecognitionException, XPathException {
		
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
		_retTree = _t;
	}
	
	public final void module(AST _t,
		PathExpr path
	) throws RecognitionException, PermissionDeniedException,EXistException,XPathException {
		
		org.exist.xquery.parser.XQueryAST module_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		org.exist.xquery.parser.XQueryAST v = null;
		org.exist.xquery.parser.XQueryAST enc = null;
		Expression step = null;
		
		{
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case VERSION_DECL:
		{
			AST __t7 = _t;
			v = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,VERSION_DECL);
			_t = _t.getFirstChild();
			
			if (!v.getText().equals("1.0"))
			throw new XPathException(v, "err:XQST0031: Wrong XQuery version: require 1.0");
			
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case STRING_LITERAL:
			{
				enc = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,STRING_LITERAL);
				_t = _t.getNextSibling();
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
			
			if (enc != null) {
			if (!XMLChar.isValidIANAEncoding(enc.getText())) {
			throw new XPathException(enc, "err:XQST0087: Unknown or wrong encoding not adhering to required XML 1.0 EncName.");
			}
			if (!enc.getText().equals("UTF-8")) {
			//util.serializer.encodings.CharacterSet
			//context.setEncoding(enc.getText());
			}   
			}
			
			_t = __t7;
			_t = _t.getNextSibling();
			break;
		}
		case EOF:
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
		case DEF_COLLATION_DECL:
		case DEF_FUNCTION_NS_DECL:
		case GLOBAL_VAR:
		case FUNCTION_DECL:
		case OPTION:
		case MODULE_DECL:
		case MODULE_IMPORT:
		case SCHEMA_IMPORT:
		case ATTRIBUTE_TEST:
		case COMP_ELEM_CONSTRUCTOR:
		case COMP_ATTR_CONSTRUCTOR:
		case COMP_TEXT_CONSTRUCTOR:
		case COMP_COMMENT_CONSTRUCTOR:
		case COMP_PI_CONSTRUCTOR:
		case COMP_DOC_CONSTRUCTOR:
		case PRAGMA:
		case NCNAME:
		case EQ:
		case STRING_LITERAL:
		case 62:
		case LITERAL_ordering:
		case LITERAL_construction:
		case 65:
		case 66:
		case LITERAL_element:
		case LITERAL_order:
		case COMMA:
		case LCURLY:
		case STAR:
		case PLUS:
		case LITERAL_some:
		case LITERAL_every:
		case LITERAL_if:
		case LITERAL_typeswitch:
		case LITERAL_update:
		case LITERAL_preceding:
		case LITERAL_following:
		case LITERAL_return:
		case LITERAL_or:
		case LITERAL_and:
		case LITERAL_instance:
		case LITERAL_treat:
		case LITERAL_castable:
		case LITERAL_cast:
		case BEFORE:
		case AFTER:
		case LITERAL_eq:
		case LITERAL_ne:
		case LITERAL_lt:
		case LITERAL_le:
		case LITERAL_gt:
		case LITERAL_ge:
		case NEQ:
		case GT:
		case GTEQ:
		case LT:
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
		case 168:
		case 169:
		case SELF:
		case XML_COMMENT:
		case XML_PI:
		case AT:
		case PARENT:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_descendant:
		case 181:
		case 182:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 185:
		case 186:
		case DOUBLE_LITERAL:
		case DECIMAL_LITERAL:
		case INTEGER_LITERAL:
		case XML_CDATA:
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
		case MODULE_DECL:
		{
			libraryModule(_t,path);
			_t = _retTree;
			break;
		}
		case EOF:
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
		case DEF_COLLATION_DECL:
		case DEF_FUNCTION_NS_DECL:
		case GLOBAL_VAR:
		case FUNCTION_DECL:
		case OPTION:
		case MODULE_IMPORT:
		case SCHEMA_IMPORT:
		case ATTRIBUTE_TEST:
		case COMP_ELEM_CONSTRUCTOR:
		case COMP_ATTR_CONSTRUCTOR:
		case COMP_TEXT_CONSTRUCTOR:
		case COMP_COMMENT_CONSTRUCTOR:
		case COMP_PI_CONSTRUCTOR:
		case COMP_DOC_CONSTRUCTOR:
		case PRAGMA:
		case NCNAME:
		case EQ:
		case STRING_LITERAL:
		case 62:
		case LITERAL_ordering:
		case LITERAL_construction:
		case 65:
		case 66:
		case LITERAL_element:
		case LITERAL_order:
		case COMMA:
		case LCURLY:
		case STAR:
		case PLUS:
		case LITERAL_some:
		case LITERAL_every:
		case LITERAL_if:
		case LITERAL_typeswitch:
		case LITERAL_update:
		case LITERAL_preceding:
		case LITERAL_following:
		case LITERAL_return:
		case LITERAL_or:
		case LITERAL_and:
		case LITERAL_instance:
		case LITERAL_treat:
		case LITERAL_castable:
		case LITERAL_cast:
		case BEFORE:
		case AFTER:
		case LITERAL_eq:
		case LITERAL_ne:
		case LITERAL_lt:
		case LITERAL_le:
		case LITERAL_gt:
		case LITERAL_ge:
		case NEQ:
		case GT:
		case GTEQ:
		case LT:
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
		case 168:
		case 169:
		case SELF:
		case XML_COMMENT:
		case XML_PI:
		case AT:
		case PARENT:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_descendant:
		case 181:
		case 182:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 185:
		case 186:
		case DOUBLE_LITERAL:
		case DECIMAL_LITERAL:
		case INTEGER_LITERAL:
		case XML_CDATA:
		{
			mainModule(_t,path);
			_t = _retTree;
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
	
	public final void libraryModule(AST _t,
		PathExpr path
	) throws RecognitionException, PermissionDeniedException,EXistException,XPathException {
		
		org.exist.xquery.parser.XQueryAST libraryModule_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		org.exist.xquery.parser.XQueryAST m = null;
		org.exist.xquery.parser.XQueryAST uri = null;
		Expression step = null;
		
		AST __t11 = _t;
		m = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
		match(_t,MODULE_DECL);
		_t = _t.getFirstChild();
		uri = (org.exist.xquery.parser.XQueryAST)_t;
		match(_t,STRING_LITERAL);
		_t = _t.getNextSibling();
		
		myModule = new ExternalModuleImpl(uri.getText(), m.getText());
		context.declareNamespace(m.getText(), uri.getText());
		staticContext.declareNamespace(m.getText(), uri.getText());
		
		_t = __t11;
		_t = _t.getNextSibling();
		prolog(_t,path);
		_t = _retTree;
		_retTree = _t;
	}
	
	public final void mainModule(AST _t,
		PathExpr path
	) throws RecognitionException, PermissionDeniedException,EXistException,XPathException {
		
		org.exist.xquery.parser.XQueryAST mainModule_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		Expression step = null;
		
		prolog(_t,path);
		_t = _retTree;
		step=expr(_t,path);
		_t = _retTree;
		_retTree = _t;
	}
	
/**
 * Process the XQuery prolog.
 */
	public final void prolog(AST _t,
		PathExpr path
	) throws RecognitionException, PermissionDeniedException,EXistException,XPathException {
		
		org.exist.xquery.parser.XQueryAST prolog_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		org.exist.xquery.parser.XQueryAST prefix = null;
		org.exist.xquery.parser.XQueryAST uri = null;
		org.exist.xquery.parser.XQueryAST base = null;
		org.exist.xquery.parser.XQueryAST defu = null;
		org.exist.xquery.parser.XQueryAST deff = null;
		org.exist.xquery.parser.XQueryAST defc = null;
		org.exist.xquery.parser.XQueryAST qname = null;
		org.exist.xquery.parser.XQueryAST e = null;
		org.exist.xquery.parser.XQueryAST qname2 = null;
		org.exist.xquery.parser.XQueryAST content = null;
		Expression step = null;
		boolean boundaryspace = false;
		boolean defaultcollation = false;
		boolean orderempty = false;
		boolean copynamespaces = false;
		boolean baseuri = false;
		boolean ordering = false;
		boolean construction = false;
		
		
		
		{
		_loop36:
		do {
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case NAMESPACE_DECL:
			{
				AST __t15 = _t;
				prefix = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
				match(_t,NAMESPACE_DECL);
				_t = _t.getFirstChild();
				uri = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,STRING_LITERAL);
				_t = _t.getNextSibling();
				
								if (declaredNamespaces.get(prefix.getText()) != null)
									throw new XPathException(prefix, "err:XQST0033: Prolog contains " +
										"multiple declarations for namespace prefix: " + prefix.getText());
								context.declareNamespace(prefix.getText(), uri.getText());
								staticContext.declareNamespace(prefix.getText(), uri.getText());
								declaredNamespaces.put(prefix.getText(), uri.getText());
							
				_t = __t15;
				_t = _t.getNextSibling();
				break;
			}
			case 62:
			{
				AST __t16 = _t;
				org.exist.xquery.parser.XQueryAST tmp31_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,62);
				_t = _t.getFirstChild();
				{
				if (_t==null) _t=ASTNULL;
				switch ( _t.getType()) {
				case LITERAL_preserve:
				{
					org.exist.xquery.parser.XQueryAST tmp32_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
					match(_t,LITERAL_preserve);
					_t = _t.getNextSibling();
					
					if (boundaryspace)
										throw new XPathException("err:XQST0068: Boundary-space already declared.");
					boundaryspace = true;
					context.setStripWhitespace(false);
					
					break;
				}
				case LITERAL_strip:
				{
					org.exist.xquery.parser.XQueryAST tmp33_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
					match(_t,LITERAL_strip);
					_t = _t.getNextSibling();
					
					if (boundaryspace)
										throw new XPathException("err:XQST0068: Boundary-space already declared.");
					boundaryspace = true;
					context.setStripWhitespace(true);
					
					break;
				}
				default:
				{
					throw new NoViableAltException(_t);
				}
				}
				}
				_t = __t16;
				_t = _t.getNextSibling();
				break;
			}
			case LITERAL_order:
			{
				AST __t18 = _t;
				org.exist.xquery.parser.XQueryAST tmp34_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,LITERAL_order);
				_t = _t.getFirstChild();
				{
				if (_t==null) _t=ASTNULL;
				switch ( _t.getType()) {
				case LITERAL_greatest:
				{
					org.exist.xquery.parser.XQueryAST tmp35_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
					match(_t,LITERAL_greatest);
					_t = _t.getNextSibling();
					
					context.setOrderEmptyGreatest(true);
					
					break;
				}
				case LITERAL_least:
				{
					org.exist.xquery.parser.XQueryAST tmp36_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
					match(_t,LITERAL_least);
					_t = _t.getNextSibling();
					
					context.setOrderEmptyGreatest(false);
					
					break;
				}
				default:
				{
					throw new NoViableAltException(_t);
				}
				}
				}
				
				if (orderempty)
				throw new XPathException("err:XQST0065: Ordering mode already declared.");
				orderempty = true;
				
				_t = __t18;
				_t = _t.getNextSibling();
				break;
			}
			case 66:
			{
				try {      // for error handling
					AST __t20 = _t;
					org.exist.xquery.parser.XQueryAST tmp37_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
					match(_t,66);
					_t = _t.getFirstChild();
					{
					if (_t==null) _t=ASTNULL;
					switch ( _t.getType()) {
					case LITERAL_preserve:
					{
						org.exist.xquery.parser.XQueryAST tmp38_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
						match(_t,LITERAL_preserve);
						_t = _t.getNextSibling();
						
						staticContext.setPreserveNamespaces(true);
						context.setPreserveNamespaces(true);
						
						break;
					}
					case 83:
					{
						org.exist.xquery.parser.XQueryAST tmp39_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
						match(_t,83);
						_t = _t.getNextSibling();
						
						staticContext.setPreserveNamespaces(false);
						context.setPreserveNamespaces(false);
						
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
					case LITERAL_inherit:
					{
						org.exist.xquery.parser.XQueryAST tmp40_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
						match(_t,LITERAL_inherit);
						_t = _t.getNextSibling();
						
						staticContext.setInheritNamespaces(true);
						context.setInheritNamespaces(true);
						
						break;
					}
					case 85:
					{
						org.exist.xquery.parser.XQueryAST tmp41_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
						match(_t,85);
						_t = _t.getNextSibling();
						
						staticContext.setInheritNamespaces(false);
						context.setInheritNamespaces(false);
						
						break;
					}
					default:
					{
						throw new NoViableAltException(_t);
					}
					}
					}
					
					if (copynamespaces)
					throw new XPathException("err:XQST0055: Copy-namespaces mode already declared.");
					copynamespaces = true;
					
					_t = __t20;
					_t = _t.getNextSibling();
				}
				catch (RecognitionException se) {
					throw new XPathException("err:XPST0003: XQuery syntax error.");
				}
				break;
			}
			case 65:
			{
				AST __t23 = _t;
				org.exist.xquery.parser.XQueryAST tmp42_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,65);
				_t = _t.getFirstChild();
				base = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,STRING_LITERAL);
				_t = _t.getNextSibling();
				
				context.setBaseURI(new AnyURIValue(StringValue.expand(base.getText())), true);
				if (baseuri)
				throw new XPathException(base, "err:XQST0032: Base URI is already declared.");
				baseuri = true;
				
				_t = __t23;
				_t = _t.getNextSibling();
				break;
			}
			case LITERAL_ordering:
			{
				AST __t24 = _t;
				org.exist.xquery.parser.XQueryAST tmp43_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,LITERAL_ordering);
				_t = _t.getFirstChild();
				{
				if (_t==null) _t=ASTNULL;
				switch ( _t.getType()) {
				case LITERAL_ordered:
				{
					org.exist.xquery.parser.XQueryAST tmp44_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
					match(_t,LITERAL_ordered);
					_t = _t.getNextSibling();
					break;
				}
				case LITERAL_unordered:
				{
					org.exist.xquery.parser.XQueryAST tmp45_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
					match(_t,LITERAL_unordered);
					_t = _t.getNextSibling();
					break;
				}
				default:
				{
					throw new NoViableAltException(_t);
				}
				}
				}
				
				// ignored
				if (ordering)
				throw new XPathException("err:XQST0065: Ordering already declared.");
				ordering = true;
				
				_t = __t24;
				_t = _t.getNextSibling();
				break;
			}
			case LITERAL_construction:
			{
				AST __t26 = _t;
				org.exist.xquery.parser.XQueryAST tmp46_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,LITERAL_construction);
				_t = _t.getFirstChild();
				{
				if (_t==null) _t=ASTNULL;
				switch ( _t.getType()) {
				case LITERAL_preserve:
				{
					org.exist.xquery.parser.XQueryAST tmp47_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
					match(_t,LITERAL_preserve);
					_t = _t.getNextSibling();
					break;
				}
				case LITERAL_strip:
				{
					org.exist.xquery.parser.XQueryAST tmp48_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
					match(_t,LITERAL_strip);
					_t = _t.getNextSibling();
					break;
				}
				default:
				{
					throw new NoViableAltException(_t);
				}
				}
				}
				
				// ignored
				if (construction)
				throw new XPathException("err:XQST0069: Construction already declared.");
				construction = true;
				
				_t = __t26;
				_t = _t.getNextSibling();
				break;
			}
			case DEF_NAMESPACE_DECL:
			{
				AST __t28 = _t;
				org.exist.xquery.parser.XQueryAST tmp49_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,DEF_NAMESPACE_DECL);
				_t = _t.getFirstChild();
				defu = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,STRING_LITERAL);
				_t = _t.getNextSibling();
				// Use setDefaultElementNamespace()
				context.declareNamespace("", defu.getText());
				staticContext.declareNamespace("",defu.getText());
				
				_t = __t28;
				_t = _t.getNextSibling();
				break;
			}
			case DEF_FUNCTION_NS_DECL:
			{
				AST __t29 = _t;
				org.exist.xquery.parser.XQueryAST tmp50_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,DEF_FUNCTION_NS_DECL);
				_t = _t.getFirstChild();
				deff = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,STRING_LITERAL);
				_t = _t.getNextSibling();
				
				context.setDefaultFunctionNamespace(deff.getText()); 
				staticContext.setDefaultFunctionNamespace(deff.getText());
				
				_t = __t29;
				_t = _t.getNextSibling();
				break;
			}
			case DEF_COLLATION_DECL:
			{
				AST __t30 = _t;
				org.exist.xquery.parser.XQueryAST tmp51_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,DEF_COLLATION_DECL);
				_t = _t.getFirstChild();
				defc = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,STRING_LITERAL);
				_t = _t.getNextSibling();
				
				if (defaultcollation)
				throw new XPathException("err:XQST0038: Default collation already declared.");
				defaultcollation = true;
				try {
				context.setDefaultCollation(defc.getText());
				} catch (XPathException xp) {
				throw new XPathException(defc, "err:XQST0038: the value specified by a default collation declaration is not present in statically known collations.");
				}
				
				_t = __t30;
				_t = _t.getNextSibling();
				break;
			}
			case GLOBAL_VAR:
			{
				AST __t31 = _t;
				qname = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
				match(_t,GLOBAL_VAR);
				_t = _t.getFirstChild();
				
								PathExpr enclosed= new PathExpr(context);
								SequenceType type= null;
								QName qn = QName.parse(staticContext, qname.getText());
								if (declaredGlobalVars.contains(qn))
									throw new XPathException(qname, "err:XQST0049: It is a " +
										"static error if more than one variable declared or " +
										"imported by a module has the same expanded QName. " +
										"Variable: " + qn.toString());
								declaredGlobalVars.add(qn);
							
				{
				if (_t==null) _t=ASTNULL;
				switch ( _t.getType()) {
				case LITERAL_as:
				{
					AST __t33 = _t;
					org.exist.xquery.parser.XQueryAST tmp52_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
					match(_t,LITERAL_as);
					_t = _t.getFirstChild();
					type= new SequenceType();
					sequenceType(_t,type);
					_t = _retTree;
					_t = __t33;
					_t = _t.getNextSibling();
					break;
				}
				case EOF:
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
				case ATTRIBUTE_TEST:
				case COMP_ELEM_CONSTRUCTOR:
				case COMP_ATTR_CONSTRUCTOR:
				case COMP_TEXT_CONSTRUCTOR:
				case COMP_COMMENT_CONSTRUCTOR:
				case COMP_PI_CONSTRUCTOR:
				case COMP_DOC_CONSTRUCTOR:
				case PRAGMA:
				case NCNAME:
				case EQ:
				case STRING_LITERAL:
				case LITERAL_element:
				case COMMA:
				case LCURLY:
				case LITERAL_external:
				case STAR:
				case PLUS:
				case LITERAL_some:
				case LITERAL_every:
				case LITERAL_if:
				case LITERAL_typeswitch:
				case LITERAL_update:
				case LITERAL_preceding:
				case LITERAL_following:
				case LITERAL_return:
				case LITERAL_or:
				case LITERAL_and:
				case LITERAL_instance:
				case LITERAL_treat:
				case LITERAL_castable:
				case LITERAL_cast:
				case BEFORE:
				case AFTER:
				case LITERAL_eq:
				case LITERAL_ne:
				case LITERAL_lt:
				case LITERAL_le:
				case LITERAL_gt:
				case LITERAL_ge:
				case NEQ:
				case GT:
				case GTEQ:
				case LT:
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
				case 168:
				case 169:
				case SELF:
				case XML_COMMENT:
				case XML_PI:
				case AT:
				case PARENT:
				case LITERAL_child:
				case LITERAL_self:
				case LITERAL_descendant:
				case 181:
				case 182:
				case LITERAL_parent:
				case LITERAL_ancestor:
				case 185:
				case 186:
				case DOUBLE_LITERAL:
				case DECIMAL_LITERAL:
				case INTEGER_LITERAL:
				case XML_CDATA:
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
				case EOF:
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
				case ATTRIBUTE_TEST:
				case COMP_ELEM_CONSTRUCTOR:
				case COMP_ATTR_CONSTRUCTOR:
				case COMP_TEXT_CONSTRUCTOR:
				case COMP_COMMENT_CONSTRUCTOR:
				case COMP_PI_CONSTRUCTOR:
				case COMP_DOC_CONSTRUCTOR:
				case PRAGMA:
				case NCNAME:
				case EQ:
				case STRING_LITERAL:
				case LITERAL_element:
				case COMMA:
				case LCURLY:
				case STAR:
				case PLUS:
				case LITERAL_some:
				case LITERAL_every:
				case LITERAL_if:
				case LITERAL_typeswitch:
				case LITERAL_update:
				case LITERAL_preceding:
				case LITERAL_following:
				case LITERAL_return:
				case LITERAL_or:
				case LITERAL_and:
				case LITERAL_instance:
				case LITERAL_treat:
				case LITERAL_castable:
				case LITERAL_cast:
				case BEFORE:
				case AFTER:
				case LITERAL_eq:
				case LITERAL_ne:
				case LITERAL_lt:
				case LITERAL_le:
				case LITERAL_gt:
				case LITERAL_ge:
				case NEQ:
				case GT:
				case GTEQ:
				case LT:
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
				case 168:
				case 169:
				case SELF:
				case XML_COMMENT:
				case XML_PI:
				case AT:
				case PARENT:
				case LITERAL_child:
				case LITERAL_self:
				case LITERAL_descendant:
				case 181:
				case 182:
				case LITERAL_parent:
				case LITERAL_ancestor:
				case 185:
				case 186:
				case DOUBLE_LITERAL:
				case DECIMAL_LITERAL:
				case INTEGER_LITERAL:
				case XML_CDATA:
				{
					e = _t==ASTNULL ? null : (org.exist.xquery.parser.XQueryAST)_t;
					step=expr(_t,enclosed);
					_t = _retTree;
					
										VariableDeclaration decl= new VariableDeclaration(context, qname.getText(), enclosed);
										decl.setSequenceType(type);
										decl.setASTNode(e);
										path.add(decl);
										if(myModule != null) {
											myModule.declareVariable(qn, decl);
										}
									
					break;
				}
				case LITERAL_external:
				{
					org.exist.xquery.parser.XQueryAST tmp53_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
					match(_t,LITERAL_external);
					_t = _t.getNextSibling();
					
									    Variable decl = null;
										boolean isDeclared = false;
										try {
											decl = context.resolveVariable(qname.getText());
											isDeclared = (decl != null);
										} catch (XPathException ignoredException) {
										}
										
										if (!isDeclared)
					decl = context.declareVariable(qname.getText(), null);
					
					if (decl != null)                        
					decl.setSequenceType(type);
									
					break;
				}
				default:
				{
					throw new NoViableAltException(_t);
				}
				}
				}
				_t = __t31;
				_t = _t.getNextSibling();
				break;
			}
			case OPTION:
			{
				AST __t35 = _t;
				qname2 = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
				match(_t,OPTION);
				_t = _t.getFirstChild();
				content = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,STRING_LITERAL);
				_t = _t.getNextSibling();
				
								context.addOption(qname2.getText(), content.getText());
							
				_t = __t35;
				_t = _t.getNextSibling();
				break;
			}
			case FUNCTION_DECL:
			{
				functionDecl(_t,path);
				_t = _retTree;
				break;
			}
			case MODULE_IMPORT:
			case SCHEMA_IMPORT:
			{
				importDecl(_t,path);
				_t = _retTree;
				break;
			}
			default:
			{
				break _loop36;
			}
			}
		} while (true);
		}
		_retTree = _t;
	}
	
/**
 * A sequence type declaration.
 */
	public final void sequenceType(AST _t,
		SequenceType type
	) throws RecognitionException, XPathException {
		
		org.exist.xquery.parser.XQueryAST sequenceType_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		org.exist.xquery.parser.XQueryAST t = null;
		org.exist.xquery.parser.XQueryAST qn1 = null;
		org.exist.xquery.parser.XQueryAST qn12 = null;
		org.exist.xquery.parser.XQueryAST qn2 = null;
		org.exist.xquery.parser.XQueryAST qn21 = null;
		org.exist.xquery.parser.XQueryAST nc = null;
		org.exist.xquery.parser.XQueryAST sl = null;
		org.exist.xquery.parser.XQueryAST dnqn = null;
		org.exist.xquery.parser.XQueryAST dnqn2 = null;
		
		{
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case ATOMIC_TYPE:
		{
			AST __t65 = _t;
			t = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,ATOMIC_TYPE);
			_t = _t.getFirstChild();
			
							QName qn= QName.parse(staticContext, t.getText());
							int code= Type.getType(qn);
							if(!Type.subTypeOf(code, Type.ATOMIC))
								throw new XPathException(t, "Type " + qn.toString() + " is not an atomic type");
							type.setPrimaryType(code);
						
			_t = __t65;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_empty:
		{
			AST __t66 = _t;
			org.exist.xquery.parser.XQueryAST tmp54_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_empty);
			_t = _t.getFirstChild();
			
							type.setPrimaryType(Type.EMPTY);
							type.setCardinality(Cardinality.EMPTY);
						
			_t = __t66;
			_t = _t.getNextSibling();
			break;
		}
		case 94:
		{
			AST __t67 = _t;
			org.exist.xquery.parser.XQueryAST tmp55_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,94);
			_t = _t.getFirstChild();
			
							type.setPrimaryType(Type.EMPTY);
							type.setCardinality(Cardinality.EMPTY);
						
			_t = __t67;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_item:
		{
			AST __t68 = _t;
			org.exist.xquery.parser.XQueryAST tmp56_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_item);
			_t = _t.getFirstChild();
			type.setPrimaryType(Type.ITEM);
			_t = __t68;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_node:
		{
			AST __t69 = _t;
			org.exist.xquery.parser.XQueryAST tmp57_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_node);
			_t = _t.getFirstChild();
			type.setPrimaryType(Type.NODE);
			_t = __t69;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_element:
		{
			AST __t70 = _t;
			org.exist.xquery.parser.XQueryAST tmp58_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_element);
			_t = _t.getFirstChild();
			type.setPrimaryType(Type.ELEMENT);
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case WILDCARD:
			{
				org.exist.xquery.parser.XQueryAST tmp59_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,WILDCARD);
				_t = _t.getNextSibling();
				break;
			}
			case QNAME:
			{
				qn1 = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,QNAME);
				_t = _t.getNextSibling();
				
									QName qname= QName.parse(staticContext, qn1.getText());
									type.setNodeName(qname);
								
				{
				if (_t==null) _t=ASTNULL;
				switch ( _t.getType()) {
				case QNAME:
				{
					qn12 = (org.exist.xquery.parser.XQueryAST)_t;
					match(_t,QNAME);
					_t = _t.getNextSibling();
					
					QName qname12= QName.parse(staticContext, qn12.getText());
					TypeTest test = new TypeTest(Type.getType(qname12));
										
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
			_t = __t70;
			_t = _t.getNextSibling();
			break;
		}
		case ATTRIBUTE_TEST:
		{
			AST __t73 = _t;
			org.exist.xquery.parser.XQueryAST tmp60_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,ATTRIBUTE_TEST);
			_t = _t.getFirstChild();
			type.setPrimaryType(Type.ATTRIBUTE);
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case QNAME:
			{
				qn2 = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,QNAME);
				_t = _t.getNextSibling();
				
				QName qname= QName.parse(staticContext, qn2.getText(), "");
									type.setNodeName(qname);
								
				break;
			}
			case WILDCARD:
			{
				org.exist.xquery.parser.XQueryAST tmp61_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,WILDCARD);
				_t = _t.getNextSibling();
				{
				if (_t==null) _t=ASTNULL;
				switch ( _t.getType()) {
				case QNAME:
				{
					qn21 = (org.exist.xquery.parser.XQueryAST)_t;
					match(_t,QNAME);
					_t = _t.getNextSibling();
					
					QName qname21= QName.parse(staticContext, qn21.getText());
					TypeTest test = new TypeTest(Type.getType(qname21));
										
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
			_t = __t73;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_text:
		{
			AST __t76 = _t;
			org.exist.xquery.parser.XQueryAST tmp62_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_text);
			_t = _t.getFirstChild();
			type.setPrimaryType(Type.TEXT);
			_t = __t76;
			_t = _t.getNextSibling();
			break;
		}
		case 168:
		{
			AST __t77 = _t;
			org.exist.xquery.parser.XQueryAST tmp63_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,168);
			_t = _t.getFirstChild();
			type.setPrimaryType(Type.PROCESSING_INSTRUCTION);
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case NCNAME:
			{
				nc = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,NCNAME);
				_t = _t.getNextSibling();
				break;
			}
			case STRING_LITERAL:
			{
				sl = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,STRING_LITERAL);
				_t = _t.getNextSibling();
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
			
			String value = "";
			if (nc != null)
			value = nc.getText();
			if (sl != null)
			value = sl.getText();
			QName qname= new QName(value, "", null);
			qname.setNamespaceURI(null);
			if (!"".equals(value))
			type.setNodeName(qname);
			
			_t = __t77;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_comment:
		{
			AST __t79 = _t;
			org.exist.xquery.parser.XQueryAST tmp64_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_comment);
			_t = _t.getFirstChild();
			type.setPrimaryType(Type.COMMENT);
			_t = __t79;
			_t = _t.getNextSibling();
			break;
		}
		case 169:
		{
			AST __t80 = _t;
			org.exist.xquery.parser.XQueryAST tmp65_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,169);
			_t = _t.getFirstChild();
			type.setPrimaryType(Type.DOCUMENT);
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case LITERAL_element:
			{
				AST __t82 = _t;
				org.exist.xquery.parser.XQueryAST tmp66_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,LITERAL_element);
				_t = _t.getFirstChild();
				{
				if (_t==null) _t=ASTNULL;
				switch ( _t.getType()) {
				case QNAME:
				{
					dnqn = (org.exist.xquery.parser.XQueryAST)_t;
					match(_t,QNAME);
					_t = _t.getNextSibling();
					
										    QName qname= QName.parse(staticContext, dnqn.getText());
					type.setNodeName(qname);
					NameTest test= new NameTest(Type.DOCUMENT, qname);
					
					break;
				}
				case WILDCARD:
				{
					org.exist.xquery.parser.XQueryAST tmp67_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
					match(_t,WILDCARD);
					_t = _t.getNextSibling();
					
					TypeTest test= new TypeTest(Type.DOCUMENT);
					
					{
					if (_t==null) _t=ASTNULL;
					switch ( _t.getType()) {
					case QNAME:
					{
						dnqn2 = (org.exist.xquery.parser.XQueryAST)_t;
						match(_t,QNAME);
						_t = _t.getNextSibling();
						
						QName qname = QName.parse(staticContext, dnqn2.getText());
						test = new TypeTest(Type.getType(qname));
						
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
				_t = __t82;
				_t = _t.getNextSibling();
				break;
			}
			case 190:
			{
				AST __t85 = _t;
				org.exist.xquery.parser.XQueryAST tmp68_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,190);
				_t = _t.getFirstChild();
				org.exist.xquery.parser.XQueryAST tmp69_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,QNAME);
				_t = _t.getNextSibling();
				_t = __t85;
				_t = _t.getNextSibling();
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
			_t = __t80;
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
			org.exist.xquery.parser.XQueryAST tmp70_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,STAR);
			_t = _t.getNextSibling();
			type.setCardinality(Cardinality.ZERO_OR_MORE);
			break;
		}
		case PLUS:
		{
			org.exist.xquery.parser.XQueryAST tmp71_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,PLUS);
			_t = _t.getNextSibling();
			type.setCardinality(Cardinality.ONE_OR_MORE);
			break;
		}
		case QUESTION:
		{
			org.exist.xquery.parser.XQueryAST tmp72_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,QUESTION);
			_t = _t.getNextSibling();
			type.setCardinality(Cardinality.ZERO_OR_ONE);
			break;
		}
		case 3:
		case LITERAL_return:
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
	
/**
 * Parse a declared function.
 */
	public final void functionDecl(AST _t,
		PathExpr path
	) throws RecognitionException, PermissionDeniedException,EXistException,XPathException {
		
		org.exist.xquery.parser.XQueryAST functionDecl_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		org.exist.xquery.parser.XQueryAST name = null;
		Expression step = null;
		
		AST __t45 = _t;
		name = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
		match(_t,FUNCTION_DECL);
		_t = _t.getFirstChild();
		PathExpr body= new PathExpr(context);
		
					QName qn= null;
					try {
						qn = QName.parse(staticContext, name.getText(), staticContext.getDefaultFunctionNamespace());
					} catch(XPathException e) {
						// throw exception with correct source location
						e.setASTNode(name);
						throw e;
					}
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
		case LITERAL_external:
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
				
		{
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case LITERAL_as:
		{
			AST __t48 = _t;
			org.exist.xquery.parser.XQueryAST tmp73_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_as);
			_t = _t.getFirstChild();
			SequenceType type= new SequenceType();
			sequenceType(_t,type);
			_t = _retTree;
			signature.setReturnType(type);
			_t = __t48;
			_t = _t.getNextSibling();
			break;
		}
		case LCURLY:
		case LITERAL_external:
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
		case LCURLY:
		{
			AST __t50 = _t;
			org.exist.xquery.parser.XQueryAST tmp74_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LCURLY);
			_t = _t.getFirstChild();
			step=expr(_t,body);
			_t = _retTree;
			
								func.setFunctionBody(body);
								context.declareFunction(func);
								if(myModule != null)
									myModule.declareFunction(func);
							
			_t = __t50;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_external:
		{
			org.exist.xquery.parser.XQueryAST tmp75_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_external);
			_t = _t.getNextSibling();
			break;
		}
		default:
		{
			throw new NoViableAltException(_t);
		}
		}
		}
		_t = __t45;
		_t = _t.getNextSibling();
		_retTree = _t;
	}
	
	public final void importDecl(AST _t,
		PathExpr path
	) throws RecognitionException, PermissionDeniedException,EXistException,XPathException {
		
		org.exist.xquery.parser.XQueryAST importDecl_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		org.exist.xquery.parser.XQueryAST i = null;
		org.exist.xquery.parser.XQueryAST pfx = null;
		org.exist.xquery.parser.XQueryAST moduleURI = null;
		org.exist.xquery.parser.XQueryAST s = null;
		org.exist.xquery.parser.XQueryAST pfx1 = null;
		org.exist.xquery.parser.XQueryAST targetURI = null;
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case MODULE_IMPORT:
		{
			AST __t38 = _t;
			i = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,MODULE_IMPORT);
			_t = _t.getFirstChild();
			
						String modulePrefix = null;
						String location = null;
			List uriList= new ArrayList(2);
					
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
				uriList(_t,uriList);
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
			
						if (modulePrefix != null) {
							if (declaredNamespaces.get(modulePrefix) != null)
								throw new XPathException(i, "err:XQST0033: Prolog contains " +
									"multiple declarations for namespace prefix: " + modulePrefix);
							declaredNamespaces.put(modulePrefix, moduleURI.getText());
						}
			try {
			if (uriList.size() > 0) {
						    for (Iterator j= uriList.iterator(); j.hasNext();) {
			try {
			location= ((AnyURIValue) j.next()).getStringValue();
			context.importModule(moduleURI.getText(), modulePrefix, location);
			staticContext.declareNamespace(modulePrefix, moduleURI.getText());
			} catch(XPathException xpe) {
			if (!j.hasNext()) {
			throw xpe;
			}
			}
			}
			} else {
			context.importModule(moduleURI.getText(), modulePrefix, location);
			staticContext.declareNamespace(modulePrefix, moduleURI.getText());
			}
			} catch(XPathException xpe) {
			xpe.prependMessage("error found while loading module " + modulePrefix + ": ");
			throw xpe;
			}
					
			_t = __t38;
			_t = _t.getNextSibling();
			break;
		}
		case SCHEMA_IMPORT:
		{
			AST __t41 = _t;
			s = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,SCHEMA_IMPORT);
			_t = _t.getFirstChild();
			
						String nsPrefix = null;
						String location = null;
						boolean defaultElementNS = false;
			List uriList= new ArrayList(2);
					
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case NCNAME:
			{
				pfx1 = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,NCNAME);
				_t = _t.getNextSibling();
				nsPrefix = pfx1.getText();
				break;
			}
			case LITERAL_default:
			{
				org.exist.xquery.parser.XQueryAST tmp76_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,LITERAL_default);
				_t = _t.getNextSibling();
				org.exist.xquery.parser.XQueryAST tmp77_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,LITERAL_element);
				_t = _t.getNextSibling();
				org.exist.xquery.parser.XQueryAST tmp78_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,LITERAL_namespace);
				_t = _t.getNextSibling();
				defaultElementNS = true;
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
			targetURI = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,STRING_LITERAL);
			_t = _t.getNextSibling();
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case STRING_LITERAL:
			{
				uriList(_t,uriList);
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
			
			if ("".equals(targetURI.getText()) && nsPrefix != null) {
			throw new XPathException(s, "err:XQST0057: A schema without target namespace (zero-length string target namespace) may not bind a namespace prefix: " + nsPrefix);
			}
			if (nsPrefix != null) {
			if (declaredNamespaces.get(nsPrefix) != null)
			throw new XPathException(s, "err:XQST0033: Prolog contains " +
			"multiple declarations for namespace prefix: " + nsPrefix);
			declaredNamespaces.put(nsPrefix, targetURI.getText());
			}
			try {
			context.declareNamespace(nsPrefix, targetURI.getText());
			staticContext.declareNamespace(nsPrefix, targetURI.getText());
			// We currently do nothing with eventual location hints. /ljo
			} catch(XPathException xpe) {
			xpe.prependMessage("err:XQST0059: Error found while loading schema " + nsPrefix + ": ");
			throw xpe;
			}
			// We ought to do this for now until Dannes can say it works. /ljo
			//throw new XPathException(s, "err:XQST0009: the eXist XQuery implementation does not support the Schema Import Feature quite yet.");
					
			_t = __t41;
			_t = _t.getNextSibling();
			break;
		}
		default:
		{
			throw new NoViableAltException(_t);
		}
		}
		_retTree = _t;
	}
	
/**
 * Parse uris in schema and module declarations.
 */
	public final void uriList(AST _t,
		List uris
	) throws RecognitionException, XPathException {
		
		org.exist.xquery.parser.XQueryAST uriList_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		
		uri(_t,uris);
		_t = _retTree;
		{
		_loop60:
		do {
			if (_t==null) _t=ASTNULL;
			if ((_t.getType()==STRING_LITERAL)) {
				uri(_t,uris);
				_t = _retTree;
			}
			else {
				break _loop60;
			}
			
		} while (true);
		}
		_retTree = _t;
	}
	
/**
 * Parse params in function declaration.
 */
	public final void paramList(AST _t,
		List vars
	) throws RecognitionException, XPathException {
		
		org.exist.xquery.parser.XQueryAST paramList_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		
		param(_t,vars);
		_t = _retTree;
		{
		_loop53:
		do {
			if (_t==null) _t=ASTNULL;
			if ((_t.getType()==VARIABLE_BINDING)) {
				param(_t,vars);
				_t = _retTree;
			}
			else {
				break _loop53;
			}
			
		} while (true);
		}
		_retTree = _t;
	}
	
/**
 * Single function param.
 */
	public final void param(AST _t,
		List vars
	) throws RecognitionException, XPathException {
		
		org.exist.xquery.parser.XQueryAST param_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		org.exist.xquery.parser.XQueryAST varname = null;
		
		AST __t55 = _t;
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
			AST __t57 = _t;
			org.exist.xquery.parser.XQueryAST tmp79_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_as);
			_t = _t.getFirstChild();
			SequenceType type= new SequenceType();
			sequenceType(_t,type);
			_t = _retTree;
			_t = __t57;
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
		_t = __t55;
		_t = _t.getNextSibling();
		_retTree = _t;
	}
	
/**
 * Single uri.
 */
	public final void uri(AST _t,
		List uris
	) throws RecognitionException, XPathException {
		
		org.exist.xquery.parser.XQueryAST uri_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		org.exist.xquery.parser.XQueryAST uri = null;
		
		AST __t62 = _t;
		uri = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
		match(_t,STRING_LITERAL);
		_t = _t.getFirstChild();
		
					AnyURIValue any= new AnyURIValue(uri.getText());
					uris.add(any);
				
		_t = __t62;
		_t = _t.getNextSibling();
		_retTree = _t;
	}
	
	public final Expression  typeCastExpr(AST _t,
		PathExpr path
	) throws RecognitionException, PermissionDeniedException,EXistException,XPathException {
		Expression step;
		
		org.exist.xquery.parser.XQueryAST typeCastExpr_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		org.exist.xquery.parser.XQueryAST castAST = null;
		org.exist.xquery.parser.XQueryAST t = null;
		org.exist.xquery.parser.XQueryAST castableAST = null;
		org.exist.xquery.parser.XQueryAST t2 = null;
		
			step= null;
			PathExpr expr= new PathExpr(context);
			int cardinality= Cardinality.EXACTLY_ONE;
		
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case LITERAL_cast:
		{
			AST __t266 = _t;
			castAST = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_cast);
			_t = _t.getFirstChild();
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
				org.exist.xquery.parser.XQueryAST tmp80_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
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
			
						QName qn= QName.parse(staticContext, t.getText());
						int code= Type.getType(qn);
						CastExpression castExpr= new CastExpression(context, expr, code, cardinality);
						castExpr.setASTNode(castAST);
						path.add(castExpr);
						step = castExpr;
					
			_t = __t266;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_castable:
		{
			AST __t268 = _t;
			castableAST = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_castable);
			_t = _t.getFirstChild();
			step=expr(_t,expr);
			_t = _retTree;
			t2 = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,ATOMIC_TYPE);
			_t = _t.getNextSibling();
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case QUESTION:
			{
				org.exist.xquery.parser.XQueryAST tmp81_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
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
			
						QName qn= QName.parse(staticContext, t2.getText());
						int code= Type.getType(qn);
						CastableExpression castExpr= new CastableExpression(context, expr, code, cardinality);
						castExpr.setASTNode(castAST);
						path.add(castExpr);
						step = castExpr;
					
			_t = __t268;
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
			AST __t226 = _t;
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
					
			_t = __t226;
			_t = _t.getNextSibling();
			break;
		}
		case NEQ:
		{
			AST __t227 = _t;
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
					
			_t = __t227;
			_t = _t.getNextSibling();
			break;
		}
		case LT:
		{
			AST __t228 = _t;
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
					
			_t = __t228;
			_t = _t.getNextSibling();
			break;
		}
		case LTEQ:
		{
			AST __t229 = _t;
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
					
			_t = __t229;
			_t = _t.getNextSibling();
			break;
		}
		case GT:
		{
			AST __t230 = _t;
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
					
			_t = __t230;
			_t = _t.getNextSibling();
			break;
		}
		case GTEQ:
		{
			AST __t231 = _t;
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
					
			_t = __t231;
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
			AST __t219 = _t;
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
					
			_t = __t219;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_ne:
		{
			AST __t220 = _t;
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
					
			_t = __t220;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_lt:
		{
			AST __t221 = _t;
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
					
			_t = __t221;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_le:
		{
			AST __t222 = _t;
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
					
			_t = __t222;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_gt:
		{
			AST __t223 = _t;
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
					
			_t = __t223;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_ge:
		{
			AST __t224 = _t;
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
					
			_t = __t224;
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
			AST __t233 = _t;
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
					
			_t = __t233;
			_t = _t.getNextSibling();
			break;
		}
		case LITERAL_isnot:
		{
			AST __t234 = _t;
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
					
			_t = __t234;
			_t = _t.getNextSibling();
			break;
		}
		case BEFORE:
		{
			AST __t235 = _t;
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
					
			_t = __t235;
			_t = _t.getNextSibling();
			break;
		}
		case AFTER:
		{
			AST __t236 = _t;
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
					
			_t = __t236;
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
			AST __t216 = _t;
			org.exist.xquery.parser.XQueryAST tmp82_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,ANDEQ);
			_t = _t.getFirstChild();
			step=expr(_t,nodes);
			_t = _retTree;
			step=expr(_t,query);
			_t = _retTree;
			_t = __t216;
			_t = _t.getNextSibling();
			
					ExtFulltext exprCont= new ExtFulltext(context, Constants.FULLTEXT_AND);
					exprCont.setPath(nodes);
					exprCont.addTerm(query);
					path.addPath(exprCont);
				
			break;
		}
		case OREQ:
		{
			AST __t217 = _t;
			org.exist.xquery.parser.XQueryAST tmp83_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,OREQ);
			_t = _t.getFirstChild();
			step=expr(_t,nodes);
			_t = _retTree;
			step=expr(_t,query);
			_t = _retTree;
			_t = __t217;
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
	
/**
 * Process a primary expression like function calls,
 * variable references, value constructors etc.
 */
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
		case COMP_DOC_CONSTRUCTOR:
		case LCURLY:
		case XML_COMMENT:
		case XML_PI:
		case XML_CDATA:
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
			AST __t158 = _t;
			org.exist.xquery.parser.XQueryAST tmp84_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,PARENTHESIZED);
			_t = _t.getFirstChild();
			PathExpr pathExpr= new PathExpr(context);
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case EOF:
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
			case ATTRIBUTE_TEST:
			case COMP_ELEM_CONSTRUCTOR:
			case COMP_ATTR_CONSTRUCTOR:
			case COMP_TEXT_CONSTRUCTOR:
			case COMP_COMMENT_CONSTRUCTOR:
			case COMP_PI_CONSTRUCTOR:
			case COMP_DOC_CONSTRUCTOR:
			case PRAGMA:
			case NCNAME:
			case EQ:
			case STRING_LITERAL:
			case LITERAL_element:
			case COMMA:
			case LCURLY:
			case STAR:
			case PLUS:
			case LITERAL_some:
			case LITERAL_every:
			case LITERAL_if:
			case LITERAL_typeswitch:
			case LITERAL_update:
			case LITERAL_preceding:
			case LITERAL_following:
			case LITERAL_return:
			case LITERAL_or:
			case LITERAL_and:
			case LITERAL_instance:
			case LITERAL_treat:
			case LITERAL_castable:
			case LITERAL_cast:
			case BEFORE:
			case AFTER:
			case LITERAL_eq:
			case LITERAL_ne:
			case LITERAL_lt:
			case LITERAL_le:
			case LITERAL_gt:
			case LITERAL_ge:
			case NEQ:
			case GT:
			case GTEQ:
			case LT:
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
			case 168:
			case 169:
			case SELF:
			case XML_COMMENT:
			case XML_PI:
			case AT:
			case PARENT:
			case LITERAL_child:
			case LITERAL_self:
			case LITERAL_descendant:
			case 181:
			case 182:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 185:
			case 186:
			case DOUBLE_LITERAL:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
			case XML_CDATA:
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
			_t = __t158;
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
		org.exist.xquery.parser.XQueryAST n = null;
		org.exist.xquery.parser.XQueryAST qn2 = null;
		org.exist.xquery.parser.XQueryAST qn21 = null;
		org.exist.xquery.parser.XQueryAST qn3 = null;
		org.exist.xquery.parser.XQueryAST qn31 = null;
		org.exist.xquery.parser.XQueryAST ncpi = null;
		org.exist.xquery.parser.XQueryAST slpi = null;
		org.exist.xquery.parser.XQueryAST dnqn = null;
		org.exist.xquery.parser.XQueryAST dnqn1 = null;
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
		case LITERAL_preceding:
		case LITERAL_following:
		case LITERAL_text:
		case LITERAL_node:
		case LITERAL_attribute:
		case LITERAL_comment:
		case 168:
		case 169:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_descendant:
		case 181:
		case 182:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 185:
		case 186:
		{
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case LITERAL_preceding:
			case LITERAL_following:
			case LITERAL_attribute:
			case LITERAL_child:
			case LITERAL_self:
			case LITERAL_descendant:
			case 181:
			case 182:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 185:
			case 186:
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
			case 168:
			case 169:
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
				
							QName qname= QName.parse(staticContext, qn.getText());
							if (axis == Constants.ATTRIBUTE_AXIS) {
				qname.setNamespaceURI(null);
				test= new NameTest(Type.ATTRIBUTE, qname);
				} else {
				test= new NameTest(Type.ELEMENT, qname);
				}
						
				break;
			}
			case PREFIX_WILDCARD:
			{
				AST __t163 = _t;
				org.exist.xquery.parser.XQueryAST tmp85_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,PREFIX_WILDCARD);
				_t = _t.getFirstChild();
				nc1 = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,NCNAME);
				_t = _t.getNextSibling();
				_t = __t163;
				_t = _t.getNextSibling();
				
							QName qname= new QName(nc1.getText(), null, null);
							qname.setNamespaceURI(null);
							test= new NameTest(Type.ELEMENT, qname);
							if (axis == Constants.ATTRIBUTE_AXIS)
								test.setType(Type.ATTRIBUTE);
						
				break;
			}
			case NCNAME:
			{
				AST __t164 = _t;
				nc = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
				match(_t,NCNAME);
				_t = _t.getFirstChild();
				org.exist.xquery.parser.XQueryAST tmp86_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,WILDCARD);
				_t = _t.getNextSibling();
				_t = __t164;
				_t = _t.getNextSibling();
				
							String namespaceURI= staticContext.getURIForPrefix(nc.getText());
							QName qname= new QName(null, namespaceURI, nc.getText());
							test= new NameTest(Type.ELEMENT, qname);
							if (axis == Constants.ATTRIBUTE_AXIS)
								test.setType(Type.ATTRIBUTE);
						
				break;
			}
			case WILDCARD:
			{
				org.exist.xquery.parser.XQueryAST tmp87_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,WILDCARD);
				_t = _t.getNextSibling();
				
							if (axis == Constants.ATTRIBUTE_AXIS)
								test= new TypeTest(Type.ATTRIBUTE);
							else
								test= new TypeTest(Type.ELEMENT);
						
				break;
			}
			case LITERAL_node:
			{
				n = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,LITERAL_node);
				_t = _t.getNextSibling();
				
							if (axis == Constants.ATTRIBUTE_AXIS) {
							//	throw new XPathException(n, "Cannot test for node() on the attribute axis");
							   test= new TypeTest(Type.ATTRIBUTE);
				} else {
							   test= new AnyNodeTest(); 
				}
						
				break;
			}
			case LITERAL_text:
			{
				org.exist.xquery.parser.XQueryAST tmp88_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,LITERAL_text);
				_t = _t.getNextSibling();
				
							if (axis == Constants.ATTRIBUTE_AXIS)
								throw new XPathException(n, "Cannot test for text() on the attribute axis"); 
							test= new TypeTest(Type.TEXT); 
						
				break;
			}
			case LITERAL_element:
			{
				AST __t165 = _t;
				org.exist.xquery.parser.XQueryAST tmp89_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,LITERAL_element);
				_t = _t.getFirstChild();
				
								if (axis == Constants.ATTRIBUTE_AXIS)
									throw new XPathException(n, "Cannot test for element() on the attribute axis"); 
								test= new TypeTest(Type.ELEMENT); 
							
				{
				if (_t==null) _t=ASTNULL;
				switch ( _t.getType()) {
				case QNAME:
				{
					qn2 = (org.exist.xquery.parser.XQueryAST)_t;
					match(_t,QNAME);
					_t = _t.getNextSibling();
					
										QName qname= QName.parse(staticContext, qn2.getText());
										test= new NameTest(Type.ELEMENT, qname);
									
					break;
				}
				case WILDCARD:
				{
					org.exist.xquery.parser.XQueryAST tmp90_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
					match(_t,WILDCARD);
					_t = _t.getNextSibling();
					{
					if (_t==null) _t=ASTNULL;
					switch ( _t.getType()) {
					case QNAME:
					{
						qn21 = (org.exist.xquery.parser.XQueryAST)_t;
						match(_t,QNAME);
						_t = _t.getNextSibling();
						
						QName qname= QName.parse(staticContext, qn21.getText());
						test = new TypeTest(Type.getType(qname));
											
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
				_t = __t165;
				_t = _t.getNextSibling();
				break;
			}
			case ATTRIBUTE_TEST:
			{
				AST __t168 = _t;
				org.exist.xquery.parser.XQueryAST tmp91_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,ATTRIBUTE_TEST);
				_t = _t.getFirstChild();
				test= new TypeTest(Type.ATTRIBUTE);
				{
				if (_t==null) _t=ASTNULL;
				switch ( _t.getType()) {
				case QNAME:
				{
					qn3 = (org.exist.xquery.parser.XQueryAST)_t;
					match(_t,QNAME);
					_t = _t.getNextSibling();
					
										QName qname= QName.parse(staticContext, qn3.getText());
										test= new NameTest(Type.ATTRIBUTE, qname);
										axis= Constants.ATTRIBUTE_AXIS;
									
					break;
				}
				case WILDCARD:
				{
					org.exist.xquery.parser.XQueryAST tmp92_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
					match(_t,WILDCARD);
					_t = _t.getNextSibling();
					{
					if (_t==null) _t=ASTNULL;
					switch ( _t.getType()) {
					case QNAME:
					{
						qn31 = (org.exist.xquery.parser.XQueryAST)_t;
						match(_t,QNAME);
						_t = _t.getNextSibling();
						
						QName qname= QName.parse(staticContext, qn31.getText());
						test = new TypeTest(Type.getType(qname));
											
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
				_t = __t168;
				_t = _t.getNextSibling();
				break;
			}
			case LITERAL_comment:
			{
				org.exist.xquery.parser.XQueryAST tmp93_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,LITERAL_comment);
				_t = _t.getNextSibling();
				
							if (axis == Constants.ATTRIBUTE_AXIS)
								throw new XPathException(n, "Cannot test for comment() on the attribute axis");
							test= new TypeTest(Type.COMMENT); 
						
				break;
			}
			case 168:
			{
				AST __t171 = _t;
				org.exist.xquery.parser.XQueryAST tmp94_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,168);
				_t = _t.getFirstChild();
				
							if (axis == Constants.ATTRIBUTE_AXIS)
								throw new XPathException(n, "Cannot test for processing-instruction() on the attribute axis");
							test= new TypeTest(Type.PROCESSING_INSTRUCTION); 
						
				{
				if (_t==null) _t=ASTNULL;
				switch ( _t.getType()) {
				case NCNAME:
				{
					ncpi = (org.exist.xquery.parser.XQueryAST)_t;
					match(_t,NCNAME);
					_t = _t.getNextSibling();
					
					QName qname;
					qname= new QName(ncpi.getText(), "", null);
					test= new NameTest(Type.PROCESSING_INSTRUCTION, qname);
					
					break;
				}
				case STRING_LITERAL:
				{
					slpi = (org.exist.xquery.parser.XQueryAST)_t;
					match(_t,STRING_LITERAL);
					_t = _t.getNextSibling();
					
					QName qname;
					qname= new QName(slpi.getText(), "", null);                
					test= new NameTest(Type.PROCESSING_INSTRUCTION, qname);
					
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
				_t = __t171;
				_t = _t.getNextSibling();
				break;
			}
			case 169:
			{
				org.exist.xquery.parser.XQueryAST tmp95_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,169);
				_t = _t.getNextSibling();
				test= new TypeTest(Type.DOCUMENT);
				{
				if (_t==null) _t=ASTNULL;
				if ((_t.getType()==LITERAL_element)) {
					AST __t174 = _t;
					org.exist.xquery.parser.XQueryAST tmp96_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
					match(_t,LITERAL_element);
					_t = _t.getFirstChild();
					{
					if (_t==null) _t=ASTNULL;
					switch ( _t.getType()) {
					case QNAME:
					{
						dnqn = (org.exist.xquery.parser.XQueryAST)_t;
						match(_t,QNAME);
						_t = _t.getNextSibling();
						
						QName qname= QName.parse(staticContext, dnqn.getText());
						test= new NameTest(Type.DOCUMENT, qname);
						
						break;
					}
					case WILDCARD:
					{
						org.exist.xquery.parser.XQueryAST tmp97_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
						match(_t,WILDCARD);
						_t = _t.getNextSibling();
						{
						if (_t==null) _t=ASTNULL;
						switch ( _t.getType()) {
						case QNAME:
						{
							dnqn1 = (org.exist.xquery.parser.XQueryAST)_t;
							match(_t,QNAME);
							_t = _t.getNextSibling();
							
							QName qname= QName.parse(staticContext, dnqn1.getText());
							test= new TypeTest(Type.getType(qname));
							
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
					_t = __t174;
					_t = _t.getNextSibling();
				}
				else if ((_t.getType()==190)) {
					AST __t177 = _t;
					org.exist.xquery.parser.XQueryAST tmp98_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
					match(_t,190);
					_t = _t.getFirstChild();
					org.exist.xquery.parser.XQueryAST tmp99_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
					match(_t,QNAME);
					_t = _t.getNextSibling();
					_t = __t177;
					_t = _t.getNextSibling();
				}
				else if ((_tokenSet_1.member(_t.getType()))) {
				}
				else {
					throw new NoViableAltException(_t);
				}
				
				}
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
			_loop179:
			do {
				if (_t==null) _t=ASTNULL;
				if ((_t.getType()==PREDICATE)) {
					predicate(_t,(LocationStep) step);
					_t = _retTree;
				}
				else {
					break _loop179;
				}
				
			} while (true);
			}
			break;
		}
		case AT:
		{
			org.exist.xquery.parser.XQueryAST tmp100_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
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
				
				qname= QName.parse(staticContext, attr.getText(), "");
				
				break;
			}
			case PREFIX_WILDCARD:
			{
				AST __t181 = _t;
				org.exist.xquery.parser.XQueryAST tmp101_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,PREFIX_WILDCARD);
				_t = _t.getFirstChild();
				nc2 = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,NCNAME);
				_t = _t.getNextSibling();
				_t = __t181;
				_t = _t.getNextSibling();
				
				qname= new QName(nc2.getText(), null, null);
				qname.setNamespaceURI(null);
						
				break;
			}
			case NCNAME:
			{
				AST __t182 = _t;
				nc3 = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
				match(_t,NCNAME);
				_t = _t.getFirstChild();
				org.exist.xquery.parser.XQueryAST tmp102_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,WILDCARD);
				_t = _t.getNextSibling();
				_t = __t182;
				_t = _t.getNextSibling();
				
							String namespaceURI= staticContext.getURIForPrefix(nc3.getText());
							if (namespaceURI == null)
								throw new EXistException("No namespace defined for prefix " + nc3.getText());
							qname= new QName(null, namespaceURI, null);
						
				break;
			}
			case WILDCARD:
			{
				org.exist.xquery.parser.XQueryAST tmp103_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
				match(_t,WILDCARD);
				_t = _t.getNextSibling();
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
			_loop184:
			do {
				if (_t==null) _t=ASTNULL;
				if ((_t.getType()==PREDICATE)) {
					predicate(_t,(LocationStep) step);
					_t = _retTree;
				}
				else {
					break _loop184;
				}
				
			} while (true);
			}
			break;
		}
		case SELF:
		{
			org.exist.xquery.parser.XQueryAST tmp104_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,SELF);
			_t = _t.getNextSibling();
			
					step= new LocationStep(context, Constants.SELF_AXIS, new TypeTest(Type.NODE));
					path.add(step);
				
			{
			_loop186:
			do {
				if (_t==null) _t=ASTNULL;
				if ((_t.getType()==PREDICATE)) {
					predicate(_t,(LocationStep) step);
					_t = _retTree;
				}
				else {
					break _loop186;
				}
				
			} while (true);
			}
			break;
		}
		case PARENT:
		{
			org.exist.xquery.parser.XQueryAST tmp105_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,PARENT);
			_t = _t.getNextSibling();
			
					step= new LocationStep(context, Constants.PARENT_AXIS, new TypeTest(Type.NODE));
					path.add(step);
				
			{
			_loop188:
			do {
				if (_t==null) _t=ASTNULL;
				if ((_t.getType()==PREDICATE)) {
					predicate(_t,(LocationStep) step);
					_t = _retTree;
				}
				else {
					break _loop188;
				}
				
			} while (true);
			}
			break;
		}
		case SLASH:
		{
			AST __t189 = _t;
			org.exist.xquery.parser.XQueryAST tmp106_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,SLASH);
			_t = _t.getFirstChild();
			step=expr(_t,path);
			_t = _retTree;
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case EOF:
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
			case ATTRIBUTE_TEST:
			case COMP_ELEM_CONSTRUCTOR:
			case COMP_ATTR_CONSTRUCTOR:
			case COMP_TEXT_CONSTRUCTOR:
			case COMP_COMMENT_CONSTRUCTOR:
			case COMP_PI_CONSTRUCTOR:
			case COMP_DOC_CONSTRUCTOR:
			case PRAGMA:
			case NCNAME:
			case EQ:
			case STRING_LITERAL:
			case LITERAL_element:
			case COMMA:
			case LCURLY:
			case STAR:
			case PLUS:
			case LITERAL_some:
			case LITERAL_every:
			case LITERAL_if:
			case LITERAL_typeswitch:
			case LITERAL_update:
			case LITERAL_preceding:
			case LITERAL_following:
			case LITERAL_return:
			case LITERAL_or:
			case LITERAL_and:
			case LITERAL_instance:
			case LITERAL_treat:
			case LITERAL_castable:
			case LITERAL_cast:
			case BEFORE:
			case AFTER:
			case LITERAL_eq:
			case LITERAL_ne:
			case LITERAL_lt:
			case LITERAL_le:
			case LITERAL_gt:
			case LITERAL_ge:
			case NEQ:
			case GT:
			case GTEQ:
			case LT:
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
			case 168:
			case 169:
			case SELF:
			case XML_COMMENT:
			case XML_PI:
			case AT:
			case PARENT:
			case LITERAL_child:
			case LITERAL_self:
			case LITERAL_descendant:
			case 181:
			case 182:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 185:
			case 186:
			case DOUBLE_LITERAL:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
			case XML_CDATA:
			{
				rightStep=expr(_t,path);
				_t = _retTree;
				
								if (rightStep instanceof LocationStep) {
									if(((LocationStep) rightStep).getAxis() == Constants.UNKNOWN_AXIS)
										((LocationStep) rightStep).setAxis(Constants.CHILD_AXIS);
								} else {
									rightStep.setPrimaryAxis(Constants.CHILD_AXIS);
									if(rightStep instanceof VariableReference) {
										rightStep = new SimpleStep(context, Constants.CHILD_AXIS, rightStep);
										path.replaceLastExpression(rightStep);
									}
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
			_t = __t189;
			_t = _t.getNextSibling();
			
					if (step instanceof LocationStep && ((LocationStep) step).getAxis() == Constants.UNKNOWN_AXIS)
						 ((LocationStep) step).setAxis(Constants.CHILD_AXIS);
				
			break;
		}
		case DSLASH:
		{
			AST __t191 = _t;
			org.exist.xquery.parser.XQueryAST tmp107_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,DSLASH);
			_t = _t.getFirstChild();
			step=expr(_t,path);
			_t = _retTree;
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case EOF:
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
			case ATTRIBUTE_TEST:
			case COMP_ELEM_CONSTRUCTOR:
			case COMP_ATTR_CONSTRUCTOR:
			case COMP_TEXT_CONSTRUCTOR:
			case COMP_COMMENT_CONSTRUCTOR:
			case COMP_PI_CONSTRUCTOR:
			case COMP_DOC_CONSTRUCTOR:
			case PRAGMA:
			case NCNAME:
			case EQ:
			case STRING_LITERAL:
			case LITERAL_element:
			case COMMA:
			case LCURLY:
			case STAR:
			case PLUS:
			case LITERAL_some:
			case LITERAL_every:
			case LITERAL_if:
			case LITERAL_typeswitch:
			case LITERAL_update:
			case LITERAL_preceding:
			case LITERAL_following:
			case LITERAL_return:
			case LITERAL_or:
			case LITERAL_and:
			case LITERAL_instance:
			case LITERAL_treat:
			case LITERAL_castable:
			case LITERAL_cast:
			case BEFORE:
			case AFTER:
			case LITERAL_eq:
			case LITERAL_ne:
			case LITERAL_lt:
			case LITERAL_le:
			case LITERAL_gt:
			case LITERAL_ge:
			case NEQ:
			case GT:
			case GTEQ:
			case LT:
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
			case 168:
			case 169:
			case SELF:
			case XML_COMMENT:
			case XML_PI:
			case AT:
			case PARENT:
			case LITERAL_child:
			case LITERAL_self:
			case LITERAL_descendant:
			case 181:
			case 182:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 185:
			case 186:
			case DOUBLE_LITERAL:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
			case XML_CDATA:
			{
				rightStep=expr(_t,path);
				_t = _retTree;
				
								if (rightStep instanceof LocationStep) {
									LocationStep rs= (LocationStep) rightStep;
									if (rs.getAxis() == Constants.ATTRIBUTE_AXIS || 
										rs.getTest().getType() == Type.ATTRIBUTE) {
										rs.setAxis(Constants.DESCENDANT_ATTRIBUTE_AXIS);
									} else if (rs.getAxis() == Constants.CHILD_AXIS && rs.getTest().isWildcardTest()) {
				rs.setAxis(Constants.DESCENDANT_AXIS);
				} else {
							                        rs.setAxis(Constants.DESCENDANT_SELF_AXIS);
				}
				
								} else {
									rightStep.setPrimaryAxis(Constants.DESCENDANT_SELF_AXIS);
									if(rightStep instanceof VariableReference) {
										rightStep = new SimpleStep(context, Constants.DESCENDANT_SELF_AXIS, rightStep);
										path.replaceLastExpression(rightStep);
									}
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
			_t = __t191;
			_t = _t.getNextSibling();
			
					if (step instanceof LocationStep && ((LocationStep) step).getAxis() == Constants.UNKNOWN_AXIS)
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
	
	public final Expression  extensionExpr(AST _t,
		PathExpr path
	) throws RecognitionException, XPathException,PermissionDeniedException,EXistException {
		Expression step;
		
		org.exist.xquery.parser.XQueryAST extensionExpr_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		org.exist.xquery.parser.XQueryAST p = null;
		org.exist.xquery.parser.XQueryAST c = null;
		
			step = null;
			PathExpr pathExpr = new PathExpr(context);
			ExtensionExpression ext = null;
		
		
		{
		int _cnt274=0;
		_loop274:
		do {
			if (_t==null) _t=ASTNULL;
			if ((_t.getType()==PRAGMA)) {
				AST __t272 = _t;
				p = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
				match(_t,PRAGMA);
				_t = _t.getFirstChild();
				{
				if (_t==null) _t=ASTNULL;
				switch ( _t.getType()) {
				case PRAGMA_END:
				{
					c = (org.exist.xquery.parser.XQueryAST)_t;
					match(_t,PRAGMA_END);
					_t = _t.getNextSibling();
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
				
								Pragma pragma = context.getPragma(p.getText(), c.getText());
								if (pragma != null) {
									if (ext == null)
										ext = new ExtensionExpression(context);
									ext.addPragma(pragma);
								}
							
				_t = __t272;
				_t = _t.getNextSibling();
			}
			else {
				if ( _cnt274>=1 ) { break _loop274; } else {throw new NoViableAltException(_t);}
			}
			
			_cnt274++;
		} while (true);
		}
		expr(_t,pathExpr);
		_t = _retTree;
		
				if (ext != null) {
					ext.setExpression(pathExpr);
					path.add(ext);
					step = ext;
				} else {
					path.add(pathExpr);
					step = pathExpr;
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
			AST __t196 = _t;
			plus = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,PLUS);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			_t = __t196;
			_t = _t.getNextSibling();
			
					OpNumeric op= new OpNumeric(context, left, right, Constants.PLUS);
			op.setASTNode(plus);
					path.addPath(op);
					step= op;
				
			break;
		}
		case MINUS:
		{
			AST __t197 = _t;
			minus = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,MINUS);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			_t = __t197;
			_t = _t.getNextSibling();
			
					OpNumeric op= new OpNumeric(context, left, right, Constants.MINUS);
			op.setASTNode(minus);
					path.addPath(op);
					step= op;
				
			break;
		}
		case UNARY_MINUS:
		{
			AST __t198 = _t;
			uminus = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,UNARY_MINUS);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			_t = __t198;
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
			AST __t199 = _t;
			uplus = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,UNARY_PLUS);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			_t = __t199;
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
			AST __t200 = _t;
			div = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_div);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			_t = __t200;
			_t = _t.getNextSibling();
			
					OpNumeric op= new OpNumeric(context, left, right, Constants.DIV);
			op.setASTNode(div);
					path.addPath(op);
					step= op;
				
			break;
		}
		case LITERAL_idiv:
		{
			AST __t201 = _t;
			idiv = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_idiv);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			_t = __t201;
			_t = _t.getNextSibling();
			
					OpNumeric op= new OpNumeric(context, left, right, Constants.IDIV);
			op.setASTNode(idiv);
					path.addPath(op);
					step= op;
				
			break;
		}
		case LITERAL_mod:
		{
			AST __t202 = _t;
			mod = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_mod);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			_t = __t202;
			_t = _t.getNextSibling();
			
					OpNumeric op= new OpNumeric(context, left, right, Constants.MOD);
			op.setASTNode(mod);
					path.addPath(op);
					step= op;
				
			break;
		}
		case STAR:
		{
			AST __t203 = _t;
			mult = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,STAR);
			_t = _t.getFirstChild();
			step=expr(_t,left);
			_t = _retTree;
			step=expr(_t,right);
			_t = _retTree;
			_t = __t203;
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
	
	public final Expression  updateExpr(AST _t,
		PathExpr path
	) throws RecognitionException, XPathException,PermissionDeniedException,EXistException {
		Expression step;
		
		org.exist.xquery.parser.XQueryAST updateExpr_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		org.exist.xquery.parser.XQueryAST updateAST = null;
		
		
		
		AST __t276 = _t;
		updateAST = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
		match(_t,LITERAL_update);
		_t = _t.getFirstChild();
		
					PathExpr p1 = new PathExpr(context);
					PathExpr p2 = new PathExpr(context);
					int type;
					int position = Insert.INSERT_APPEND;
				
		{
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case LITERAL_replace:
		{
			org.exist.xquery.parser.XQueryAST tmp108_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_replace);
			_t = _t.getNextSibling();
			type = 0;
			break;
		}
		case LITERAL_value:
		{
			org.exist.xquery.parser.XQueryAST tmp109_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_value);
			_t = _t.getNextSibling();
			type = 1;
			break;
		}
		case LITERAL_insert:
		{
			org.exist.xquery.parser.XQueryAST tmp110_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_insert);
			_t = _t.getNextSibling();
			type = 2;
			break;
		}
		case LITERAL_delete:
		{
			org.exist.xquery.parser.XQueryAST tmp111_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_delete);
			_t = _t.getNextSibling();
			type = 3;
			break;
		}
		case LITERAL_rename:
		{
			org.exist.xquery.parser.XQueryAST tmp112_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_rename);
			_t = _t.getNextSibling();
			type = 4;
			break;
		}
		default:
		{
			throw new NoViableAltException(_t);
		}
		}
		}
		step=expr(_t,p1);
		_t = _retTree;
		{
		if (_t==null) _t=ASTNULL;
		if ((_t.getType()==LITERAL_preceding)) {
			org.exist.xquery.parser.XQueryAST tmp113_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_preceding);
			_t = _t.getNextSibling();
			position = Insert.INSERT_BEFORE;
		}
		else if ((_t.getType()==LITERAL_following)) {
			org.exist.xquery.parser.XQueryAST tmp114_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_following);
			_t = _t.getNextSibling();
			position = Insert.INSERT_AFTER;
		}
		else if ((_t.getType()==LITERAL_into)) {
			org.exist.xquery.parser.XQueryAST tmp115_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_into);
			_t = _t.getNextSibling();
			position = Insert.INSERT_APPEND;
		}
		else if ((_tokenSet_2.member(_t.getType()))) {
		}
		else {
			throw new NoViableAltException(_t);
		}
		
		}
		{
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case EOF:
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
		case ATTRIBUTE_TEST:
		case COMP_ELEM_CONSTRUCTOR:
		case COMP_ATTR_CONSTRUCTOR:
		case COMP_TEXT_CONSTRUCTOR:
		case COMP_COMMENT_CONSTRUCTOR:
		case COMP_PI_CONSTRUCTOR:
		case COMP_DOC_CONSTRUCTOR:
		case PRAGMA:
		case NCNAME:
		case EQ:
		case STRING_LITERAL:
		case LITERAL_element:
		case COMMA:
		case LCURLY:
		case STAR:
		case PLUS:
		case LITERAL_some:
		case LITERAL_every:
		case LITERAL_if:
		case LITERAL_typeswitch:
		case LITERAL_update:
		case LITERAL_preceding:
		case LITERAL_following:
		case LITERAL_return:
		case LITERAL_or:
		case LITERAL_and:
		case LITERAL_instance:
		case LITERAL_treat:
		case LITERAL_castable:
		case LITERAL_cast:
		case BEFORE:
		case AFTER:
		case LITERAL_eq:
		case LITERAL_ne:
		case LITERAL_lt:
		case LITERAL_le:
		case LITERAL_gt:
		case LITERAL_ge:
		case NEQ:
		case GT:
		case GTEQ:
		case LT:
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
		case 168:
		case 169:
		case SELF:
		case XML_COMMENT:
		case XML_PI:
		case AT:
		case PARENT:
		case LITERAL_child:
		case LITERAL_self:
		case LITERAL_descendant:
		case 181:
		case 182:
		case LITERAL_parent:
		case LITERAL_ancestor:
		case 185:
		case 186:
		case DOUBLE_LITERAL:
		case DECIMAL_LITERAL:
		case INTEGER_LITERAL:
		case XML_CDATA:
		{
			step=expr(_t,p2);
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
		
					Modification mod;
					if (type == 0)
						mod = new Replace(context, p1, p2);
					else if (type == 1)
						mod = new Update(context, p1, p2);
					else if (type == 2)
						mod = new Insert(context, p2, p1, position);
					else if (type == 3)
						mod = new Delete(context, p1);
					else
						mod = new Rename(context, p1, p2);
					mod.setASTNode(updateAST);
					path.add(mod);
					step = mod;
				
		_t = __t276;
		_t = _t.getNextSibling();
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
		org.exist.xquery.parser.XQueryAST qna = null;
		org.exist.xquery.parser.XQueryAST pid = null;
		org.exist.xquery.parser.XQueryAST ex = null;
		org.exist.xquery.parser.XQueryAST e = null;
		org.exist.xquery.parser.XQueryAST attrName = null;
		org.exist.xquery.parser.XQueryAST attrVal = null;
		org.exist.xquery.parser.XQueryAST pcdata = null;
		org.exist.xquery.parser.XQueryAST t = null;
		org.exist.xquery.parser.XQueryAST tc = null;
		org.exist.xquery.parser.XQueryAST d = null;
		org.exist.xquery.parser.XQueryAST cdata = null;
		org.exist.xquery.parser.XQueryAST p = null;
		org.exist.xquery.parser.XQueryAST cdataSect = null;
		org.exist.xquery.parser.XQueryAST l = null;
		
			step= null;
			PathExpr elementContent= null;
			Expression contentExpr= null;
			Expression qnameExpr = null;
		
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case COMP_ELEM_CONSTRUCTOR:
		{
			AST __t238 = _t;
			qn = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,COMP_ELEM_CONSTRUCTOR);
			_t = _t.getFirstChild();
			
						ElementConstructor c= new ElementConstructor(context);
						c.setASTNode(qn);
						step= c;
						SequenceConstructor construct = new SequenceConstructor(context);
						EnclosedExpr enclosed = new EnclosedExpr(context);
						enclosed.addPath(construct);
						c.setContent(enclosed);
						PathExpr qnamePathExpr = new PathExpr(context);
						c.setNameExpr(qnamePathExpr);
					
			qnameExpr=expr(_t,qnamePathExpr);
			_t = _retTree;
			{
			_loop241:
			do {
				if (_t==null) _t=ASTNULL;
				switch ( _t.getType()) {
				case COMP_NS_CONSTRUCTOR:
				{
					AST __t240 = _t;
					prefix = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
					match(_t,COMP_NS_CONSTRUCTOR);
					_t = _t.getFirstChild();
					uri = (org.exist.xquery.parser.XQueryAST)_t;
					match(_t,STRING_LITERAL);
					_t = _t.getNextSibling();
					_t = __t240;
					_t = _t.getNextSibling();
					
									c.addNamespaceDecl(prefix.getText(), uri.getText());
								
					break;
				}
				case EOF:
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
				case ATTRIBUTE_TEST:
				case COMP_ELEM_CONSTRUCTOR:
				case COMP_ATTR_CONSTRUCTOR:
				case COMP_TEXT_CONSTRUCTOR:
				case COMP_COMMENT_CONSTRUCTOR:
				case COMP_PI_CONSTRUCTOR:
				case COMP_DOC_CONSTRUCTOR:
				case PRAGMA:
				case NCNAME:
				case EQ:
				case STRING_LITERAL:
				case LITERAL_element:
				case COMMA:
				case LCURLY:
				case STAR:
				case PLUS:
				case LITERAL_some:
				case LITERAL_every:
				case LITERAL_if:
				case LITERAL_typeswitch:
				case LITERAL_update:
				case LITERAL_preceding:
				case LITERAL_following:
				case LITERAL_return:
				case LITERAL_or:
				case LITERAL_and:
				case LITERAL_instance:
				case LITERAL_treat:
				case LITERAL_castable:
				case LITERAL_cast:
				case BEFORE:
				case AFTER:
				case LITERAL_eq:
				case LITERAL_ne:
				case LITERAL_lt:
				case LITERAL_le:
				case LITERAL_gt:
				case LITERAL_ge:
				case NEQ:
				case GT:
				case GTEQ:
				case LT:
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
				case 168:
				case 169:
				case SELF:
				case XML_COMMENT:
				case XML_PI:
				case AT:
				case PARENT:
				case LITERAL_child:
				case LITERAL_self:
				case LITERAL_descendant:
				case 181:
				case 182:
				case LITERAL_parent:
				case LITERAL_ancestor:
				case 185:
				case 186:
				case DOUBLE_LITERAL:
				case DECIMAL_LITERAL:
				case INTEGER_LITERAL:
				case XML_CDATA:
				{
					elementContent = new PathExpr(context);
					contentExpr=expr(_t,elementContent);
					_t = _retTree;
					construct.addPath(elementContent);
					break;
				}
				default:
				{
					break _loop241;
				}
				}
			} while (true);
			}
			_t = __t238;
			_t = _t.getNextSibling();
			break;
		}
		case COMP_ATTR_CONSTRUCTOR:
		{
			AST __t242 = _t;
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
					
			qna = _t==ASTNULL ? null : (org.exist.xquery.parser.XQueryAST)_t;
			qnameExpr=expr(_t,qnamePathExpr);
			_t = _retTree;
			
			QName qname = QName.parse(staticContext, qna.getText());
			if (Namespaces.XMLNS_NS.equals(qname.getNamespaceURI()) 
			|| ("".equals(qname.getNamespaceURI()) && qname.getLocalName().equals("xmlns")))
			throw new XPathException("err:XQDY0044: the node-name property of the node constructed by a computed attribute constructor is in the namespace http://www.w3.org/2000/xmlns/ (corresponding to namespace prefix xmlns), or is in no namespace and has local name xmlns.");
			
			AST __t243 = _t;
			org.exist.xquery.parser.XQueryAST tmp116_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LCURLY);
			_t = _t.getFirstChild();
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case EOF:
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
			case ATTRIBUTE_TEST:
			case COMP_ELEM_CONSTRUCTOR:
			case COMP_ATTR_CONSTRUCTOR:
			case COMP_TEXT_CONSTRUCTOR:
			case COMP_COMMENT_CONSTRUCTOR:
			case COMP_PI_CONSTRUCTOR:
			case COMP_DOC_CONSTRUCTOR:
			case PRAGMA:
			case NCNAME:
			case EQ:
			case STRING_LITERAL:
			case LITERAL_element:
			case COMMA:
			case LCURLY:
			case STAR:
			case PLUS:
			case LITERAL_some:
			case LITERAL_every:
			case LITERAL_if:
			case LITERAL_typeswitch:
			case LITERAL_update:
			case LITERAL_preceding:
			case LITERAL_following:
			case LITERAL_return:
			case LITERAL_or:
			case LITERAL_and:
			case LITERAL_instance:
			case LITERAL_treat:
			case LITERAL_castable:
			case LITERAL_cast:
			case BEFORE:
			case AFTER:
			case LITERAL_eq:
			case LITERAL_ne:
			case LITERAL_lt:
			case LITERAL_le:
			case LITERAL_gt:
			case LITERAL_ge:
			case NEQ:
			case GT:
			case GTEQ:
			case LT:
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
			case 168:
			case 169:
			case SELF:
			case XML_COMMENT:
			case XML_PI:
			case AT:
			case PARENT:
			case LITERAL_child:
			case LITERAL_self:
			case LITERAL_descendant:
			case 181:
			case 182:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 185:
			case 186:
			case DOUBLE_LITERAL:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
			case XML_CDATA:
			{
				contentExpr=expr(_t,elementContent);
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
			_t = __t243;
			_t = _t.getNextSibling();
			_t = __t242;
			_t = _t.getNextSibling();
			break;
		}
		case COMP_PI_CONSTRUCTOR:
		{
			AST __t245 = _t;
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
			AST __t246 = _t;
			org.exist.xquery.parser.XQueryAST tmp117_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LCURLY);
			_t = _t.getFirstChild();
			{
			if (_t==null) _t=ASTNULL;
			switch ( _t.getType()) {
			case EOF:
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
			case ATTRIBUTE_TEST:
			case COMP_ELEM_CONSTRUCTOR:
			case COMP_ATTR_CONSTRUCTOR:
			case COMP_TEXT_CONSTRUCTOR:
			case COMP_COMMENT_CONSTRUCTOR:
			case COMP_PI_CONSTRUCTOR:
			case COMP_DOC_CONSTRUCTOR:
			case PRAGMA:
			case NCNAME:
			case EQ:
			case STRING_LITERAL:
			case LITERAL_element:
			case COMMA:
			case LCURLY:
			case STAR:
			case PLUS:
			case LITERAL_some:
			case LITERAL_every:
			case LITERAL_if:
			case LITERAL_typeswitch:
			case LITERAL_update:
			case LITERAL_preceding:
			case LITERAL_following:
			case LITERAL_return:
			case LITERAL_or:
			case LITERAL_and:
			case LITERAL_instance:
			case LITERAL_treat:
			case LITERAL_castable:
			case LITERAL_cast:
			case BEFORE:
			case AFTER:
			case LITERAL_eq:
			case LITERAL_ne:
			case LITERAL_lt:
			case LITERAL_le:
			case LITERAL_gt:
			case LITERAL_ge:
			case NEQ:
			case GT:
			case GTEQ:
			case LT:
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
			case 168:
			case 169:
			case SELF:
			case XML_COMMENT:
			case XML_PI:
			case AT:
			case PARENT:
			case LITERAL_child:
			case LITERAL_self:
			case LITERAL_descendant:
			case 181:
			case 182:
			case LITERAL_parent:
			case LITERAL_ancestor:
			case 185:
			case 186:
			case DOUBLE_LITERAL:
			case DECIMAL_LITERAL:
			case INTEGER_LITERAL:
			case XML_CDATA:
			{
				ex = _t==ASTNULL ? null : (org.exist.xquery.parser.XQueryAST)_t;
				contentExpr=expr(_t,elementContent);
				_t = _retTree;
				
				if (ex.getText() != null && ex.getText().indexOf("?>") > Constants.STRING_NOT_FOUND)
				throw new XPathException("err:XQDY0026: content expression of a computed processing instruction constructor contains the string '?>' which is not allowed.");
				
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
			_t = __t246;
			_t = _t.getNextSibling();
			_t = __t245;
			_t = _t.getNextSibling();
			break;
		}
		case ELEMENT:
		{
			AST __t248 = _t;
			e = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,ELEMENT);
			_t = _t.getFirstChild();
			
						ElementConstructor c= new ElementConstructor(context, e.getText());
						c.setASTNode(e);
						step= c;
						staticContext.pushInScopeNamespaces();
					
			{
			_loop254:
			do {
				if (_t==null) _t=ASTNULL;
				if ((_t.getType()==ATTRIBUTE)) {
					AST __t250 = _t;
					attrName = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
					match(_t,ATTRIBUTE);
					_t = _t.getFirstChild();
					
										AttributeConstructor attrib= new AttributeConstructor(context, attrName.getText());
										attrib.setASTNode(attrName);
									
					{
					_loop253:
					do {
						if (_t==null) _t=ASTNULL;
						switch ( _t.getType()) {
						case ATTRIBUTE_CONTENT:
						{
							attrVal = (org.exist.xquery.parser.XQueryAST)_t;
							match(_t,ATTRIBUTE_CONTENT);
							_t = _t.getNextSibling();
							
													attrib.addValue(StringValue.expand(attrVal.getText())); 
												
							break;
						}
						case LCURLY:
						{
							AST __t252 = _t;
							org.exist.xquery.parser.XQueryAST tmp118_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
							match(_t,LCURLY);
							_t = _t.getFirstChild();
							PathExpr enclosed= new PathExpr(context);
							expr(_t,enclosed);
							_t = _retTree;
							attrib.addEnclosedExpr(enclosed);
							_t = __t252;
							_t = _t.getNextSibling();
							break;
						}
						default:
						{
							break _loop253;
						}
						}
					} while (true);
					}
					c.addAttribute(attrib); 
					if (attrib.isNamespaceDeclaration()) {
					String nsPrefix = attrib.getQName().equals("xmlns") ?
					"" : QName.extractLocalName(attrib.getQName());
					staticContext.declareInScopeNamespace(nsPrefix,attrib.getLiteralValue());
					}
					
					
					_t = __t250;
					_t = _t.getNextSibling();
				}
				else {
					break _loop254;
				}
				
			} while (true);
			}
			{
			_loop256:
			do {
				if (_t==null) _t=ASTNULL;
				if ((_tokenSet_3.member(_t.getType()))) {
					
									if (elementContent == null) {
										elementContent= new PathExpr(context);
										c.setContent(elementContent);
									}
								
					contentExpr=constructor(_t,elementContent);
					_t = _retTree;
					elementContent.add(contentExpr);
				}
				else {
					break _loop256;
				}
				
			} while (true);
			}
			
			staticContext.popInScopeNamespaces();
			
			_t = __t248;
			_t = _t.getNextSibling();
			break;
		}
		case TEXT:
		{
			AST __t257 = _t;
			pcdata = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,TEXT);
			_t = _t.getFirstChild();
			
						TextConstructor text= new TextConstructor(context, pcdata.getText());
			text.setASTNode(pcdata);
						step= text;
					
			_t = __t257;
			_t = _t.getNextSibling();
			break;
		}
		case COMP_TEXT_CONSTRUCTOR:
		{
			AST __t258 = _t;
			t = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,COMP_TEXT_CONSTRUCTOR);
			_t = _t.getFirstChild();
			
						elementContent = new PathExpr(context);
						DynamicTextConstructor text = new DynamicTextConstructor(context, elementContent);
						text.setASTNode(t);
						step= text;
					
			contentExpr=expr(_t,elementContent);
			_t = _retTree;
			_t = __t258;
			_t = _t.getNextSibling();
			break;
		}
		case COMP_COMMENT_CONSTRUCTOR:
		{
			AST __t259 = _t;
			tc = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,COMP_COMMENT_CONSTRUCTOR);
			_t = _t.getFirstChild();
			
						elementContent = new PathExpr(context);
						DynamicCommentConstructor comment = new DynamicCommentConstructor(context, elementContent);
						comment.setASTNode(t);
						step= comment;
					
			contentExpr=expr(_t,elementContent);
			_t = _retTree;
			_t = __t259;
			_t = _t.getNextSibling();
			break;
		}
		case COMP_DOC_CONSTRUCTOR:
		{
			AST __t260 = _t;
			d = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,COMP_DOC_CONSTRUCTOR);
			_t = _t.getFirstChild();
			
						elementContent = new PathExpr(context);
						DocumentConstructor doc = new DocumentConstructor(context, elementContent);
						doc.setASTNode(d);
						step= doc;
					
			contentExpr=expr(_t,elementContent);
			_t = _retTree;
			_t = __t260;
			_t = _t.getNextSibling();
			break;
		}
		case XML_COMMENT:
		{
			AST __t261 = _t;
			cdata = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,XML_COMMENT);
			_t = _t.getFirstChild();
			
						CommentConstructor comment= new CommentConstructor(context, cdata.getText());
			comment.setASTNode(cdata);
						step= comment;
					
			_t = __t261;
			_t = _t.getNextSibling();
			break;
		}
		case XML_PI:
		{
			AST __t262 = _t;
			p = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,XML_PI);
			_t = _t.getFirstChild();
			
						PIConstructor pi= new PIConstructor(context, p.getText());
			pi.setASTNode(p);
						step= pi;
					
			_t = __t262;
			_t = _t.getNextSibling();
			break;
		}
		case XML_CDATA:
		{
			AST __t263 = _t;
			cdataSect = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,XML_CDATA);
			_t = _t.getFirstChild();
			
						CDATAConstructor cd = new CDATAConstructor(context, cdataSect.getText());
						cd.setASTNode(cdataSect);
						step= cd;
					
			_t = __t263;
			_t = _t.getNextSibling();
			break;
		}
		case LCURLY:
		{
			AST __t264 = _t;
			l = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LCURLY);
			_t = _t.getFirstChild();
			
			EnclosedExpr subexpr= new EnclosedExpr(context); 
			subexpr.setASTNode(l);
			
			step=expr(_t,subexpr);
			_t = _retTree;
			step= subexpr;
			_t = __t264;
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
		_loop207:
		do {
			if (_t==null) _t=ASTNULL;
			if ((_t.getType()==PREDICATE)) {
				AST __t206 = _t;
				org.exist.xquery.parser.XQueryAST tmp119_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
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
							
				_t = __t206;
				_t = _t.getNextSibling();
			}
			else {
				break _loop207;
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
		
		
		AST __t211 = _t;
		fn = _t==ASTNULL ? null :(org.exist.xquery.parser.XQueryAST)_t;
		match(_t,FUNCTION);
		_t = _t.getFirstChild();
		List params= new ArrayList(2);
		{
		_loop213:
		do {
			if (_t==null) _t=ASTNULL;
			if ((_tokenSet_0.member(_t.getType()))) {
				pathExpr= new PathExpr(context);
				expr(_t,pathExpr);
				_t = _retTree;
				params.add(pathExpr);
			}
			else {
				break _loop213;
			}
			
		} while (true);
		}
		_t = __t211;
		_t = _t.getNextSibling();
		step= FunctionFactory.createFunction(context, fn, path, params);
		_retTree = _t;
		return step;
	}
	
	public final int  forwardAxis(AST _t) throws RecognitionException, PermissionDeniedException,EXistException {
		int axis;
		
		org.exist.xquery.parser.XQueryAST forwardAxis_AST_in = (_t == ASTNULL) ? null : (org.exist.xquery.parser.XQueryAST)_t;
		axis= Constants.UNKNOWN_AXIS;
		
		if (_t==null) _t=ASTNULL;
		switch ( _t.getType()) {
		case LITERAL_child:
		{
			org.exist.xquery.parser.XQueryAST tmp120_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_child);
			_t = _t.getNextSibling();
			axis= Constants.CHILD_AXIS;
			break;
		}
		case LITERAL_attribute:
		{
			org.exist.xquery.parser.XQueryAST tmp121_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_attribute);
			_t = _t.getNextSibling();
			axis= Constants.ATTRIBUTE_AXIS;
			break;
		}
		case LITERAL_self:
		{
			org.exist.xquery.parser.XQueryAST tmp122_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_self);
			_t = _t.getNextSibling();
			axis= Constants.SELF_AXIS;
			break;
		}
		case LITERAL_parent:
		{
			org.exist.xquery.parser.XQueryAST tmp123_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_parent);
			_t = _t.getNextSibling();
			axis= Constants.PARENT_AXIS;
			break;
		}
		case LITERAL_descendant:
		{
			org.exist.xquery.parser.XQueryAST tmp124_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_descendant);
			_t = _t.getNextSibling();
			axis= Constants.DESCENDANT_AXIS;
			break;
		}
		case 181:
		{
			org.exist.xquery.parser.XQueryAST tmp125_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,181);
			_t = _t.getNextSibling();
			axis= Constants.DESCENDANT_SELF_AXIS;
			break;
		}
		case 182:
		{
			org.exist.xquery.parser.XQueryAST tmp126_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,182);
			_t = _t.getNextSibling();
			axis= Constants.FOLLOWING_SIBLING_AXIS;
			break;
		}
		case LITERAL_following:
		{
			org.exist.xquery.parser.XQueryAST tmp127_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_following);
			_t = _t.getNextSibling();
			axis= Constants.FOLLOWING_AXIS;
			break;
		}
		case 186:
		{
			org.exist.xquery.parser.XQueryAST tmp128_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,186);
			_t = _t.getNextSibling();
			axis= Constants.PRECEDING_SIBLING_AXIS;
			break;
		}
		case LITERAL_preceding:
		{
			org.exist.xquery.parser.XQueryAST tmp129_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_preceding);
			_t = _t.getNextSibling();
			axis= Constants.PRECEDING_AXIS;
			break;
		}
		case LITERAL_ancestor:
		{
			org.exist.xquery.parser.XQueryAST tmp130_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,LITERAL_ancestor);
			_t = _t.getNextSibling();
			axis= Constants.ANCESTOR_AXIS;
			break;
		}
		case 185:
		{
			org.exist.xquery.parser.XQueryAST tmp131_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
			match(_t,185);
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
		
		AST __t209 = _t;
		org.exist.xquery.parser.XQueryAST tmp132_AST_in = (org.exist.xquery.parser.XQueryAST)_t;
		match(_t,PREDICATE);
		_t = _t.getFirstChild();
		Predicate predicateExpr= new Predicate(context);
		expr(_t,predicateExpr);
		_t = _retTree;
		step.addPredicate(predicateExpr);
		_t = __t209;
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
		"ATTRIBUTE_CONTENT",
		"TEXT",
		"VERSION_DECL",
		"NAMESPACE_DECL",
		"DEF_NAMESPACE_DECL",
		"DEF_COLLATION_DECL",
		"DEF_FUNCTION_NS_DECL",
		"GLOBAL_VAR",
		"FUNCTION_DECL",
		"PROLOG",
		"OPTION",
		"ATOMIC_TYPE",
		"MODULE",
		"ORDER_BY",
		"GROUP_BY",
		"POSITIONAL_VAR",
		"MODULE_DECL",
		"MODULE_IMPORT",
		"SCHEMA_IMPORT",
		"ATTRIBUTE_TEST",
		"COMP_ELEM_CONSTRUCTOR",
		"COMP_ATTR_CONSTRUCTOR",
		"COMP_TEXT_CONSTRUCTOR",
		"COMP_COMMENT_CONSTRUCTOR",
		"COMP_PI_CONSTRUCTOR",
		"COMP_NS_CONSTRUCTOR",
		"COMP_DOC_CONSTRUCTOR",
		"PRAGMA",
		"\"xpointer\"",
		"opening parenthesis '('",
		"closing parenthesis ')'",
		"name",
		"\"xquery\"",
		"\"version\"",
		"semicolon ';'",
		"\"module\"",
		"\"namespace\"",
		"=",
		"string literal",
		"\"declare\"",
		"\"default\"",
		"\"boundary-space\"",
		"\"ordering\"",
		"\"construction\"",
		"\"base-uri\"",
		"\"copy-namespaces\"",
		"\"option\"",
		"\"function\"",
		"\"variable\"",
		"\"import\"",
		"\"encoding\"",
		"\"collation\"",
		"\"element\"",
		"\"order\"",
		"\"empty\"",
		"\"greatest\"",
		"\"least\"",
		"\"preserve\"",
		"\"strip\"",
		"\"ordered\"",
		"\"unordered\"",
		"COMMA",
		"\"no-preserve\"",
		"\"inherit\"",
		"\"no-inherit\"",
		"dollar sign '$'",
		"opening curly brace '{'",
		"closing curly brace '}'",
		"COLON",
		"\"external\"",
		"\"schema\"",
		"\"as\"",
		"\"at\"",
		"\"empty-sequence\"",
		"question mark '?'",
		"wildcard '*'",
		"+",
		"\"item\"",
		"\"for\"",
		"\"let\"",
		"\"some\"",
		"\"every\"",
		"\"if\"",
		"\"typeswitch\"",
		"\"update\"",
		"\"replace\"",
		"\"value\"",
		"\"insert\"",
		"\"delete\"",
		"\"rename\"",
		"\"with\"",
		"\"into\"",
		"\"preceding\"",
		"\"following\"",
		"\"where\"",
		"\"return\"",
		"\"in\"",
		"\"by\"",
		"\"stable\"",
		"\"ascending\"",
		"\"descending\"",
		"\"group\"",
		"\"satisfies\"",
		"\"case\"",
		"\"then\"",
		"\"else\"",
		"\"or\"",
		"\"and\"",
		"\"instance\"",
		"\"of\"",
		"\"treat\"",
		"\"castable\"",
		"\"cast\"",
		"BEFORE",
		"AFTER",
		"\"eq\"",
		"\"ne\"",
		"\"lt\"",
		"\"le\"",
		"\"gt\"",
		"\"ge\"",
		"!=",
		">",
		">=",
		"<",
		"<=",
		"\"is\"",
		"\"isnot\"",
		"fulltext operator '&='",
		"fulltext operator '|='",
		"\"to\"",
		"-",
		"\"div\"",
		"\"idiv\"",
		"\"mod\"",
		"PRAGMA_START",
		"pragma expression",
		"\"union\"",
		"union",
		"\"intersect\"",
		"\"except\"",
		"single slash '/'",
		"double slash '//'",
		"\"text\"",
		"\"node\"",
		"\"attribute\"",
		"\"comment\"",
		"\"processing-instruction\"",
		"\"document-node\"",
		"\"document\"",
		".",
		"XML comment",
		"processing instruction",
		"opening brace '['",
		"closing brace ']'",
		"@ char",
		"..",
		"\"child\"",
		"\"self\"",
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
		"\"schema-element\"",
		"XML end tag",
		"double quote '\\\"'",
		"single quote '",
		"QUOT_ATTRIBUTE_CONTENT",
		"ESCAPE_QUOT",
		"APOS_ATTRIBUTE_CONTENT",
		"ESCAPE_APOS",
		"ELEMENT_CONTENT",
		"end of XML comment",
		"end of processing instruction",
		"CDATA section",
		"\"collection\"",
		"\"validate\"",
		"start of processing instruction",
		"CDATA section start",
		"end of CDATA section",
		"LETTER",
		"DIGITS",
		"HEX_DIGITS",
		"NMSTART",
		"NMCHAR",
		"WS",
		"XQuery comment",
		"PREDEFINED_ENTITY_REF",
		"CHAR_REF",
		"S",
		"NEXT_TOKEN",
		"CHAR",
		"BASECHAR",
		"IDEOGRAPHIC",
		"COMBINING_CHAR",
		"DIGIT",
		"EXTENDER"
	};
	
	private static final long[] mk_tokenSet_0() {
		long[] data = new long[8];
		data[0]=869686209785003922L;
		data[1]=-9217175313866030592L;
		data[2]=4611470512269295611L;
		data[3]=512L;
		return data;
	}
	public static final BitSet _tokenSet_0 = new BitSet(mk_tokenSet_0());
	private static final long[] mk_tokenSet_1() {
		long[] data = new long[8];
		data[0]=869756634364018618L;
		data[1]=-7847799533915075840L;
		data[2]=4611470512269295611L;
		data[3]=512L;
		return data;
	}
	public static final BitSet _tokenSet_1 = new BitSet(mk_tokenSet_1());
	private static final long[] mk_tokenSet_2() {
		long[] data = new long[8];
		data[0]=869686209785003930L;
		data[1]=-9217175313866030592L;
		data[2]=4611470512269295611L;
		data[3]=512L;
		return data;
	}
	public static final BitSet _tokenSet_2 = new BitSet(mk_tokenSet_2());
	private static final long[] mk_tokenSet_3() {
		long[] data = new long[8];
		data[0]=208907213996032L;
		data[1]=8388608L;
		data[2]=52776558133248L;
		data[3]=512L;
		return data;
	}
	public static final BitSet _tokenSet_3 = new BitSet(mk_tokenSet_3());
	}
	
