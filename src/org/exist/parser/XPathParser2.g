/* eXist Open Source Native XML Database
 * Copyright (C) 2000-03,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * $Id$
 */
header {
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
}

/* -----------------------------------------------------------------------------------------------------
 * The XPath parser: generates an AST which is then passed to the tree parser for analysis
 * and code generation.
 * ----------------------------------------------------------------------------------------------------- */
class XPathParser2 extends Parser;

options {
	defaultErrorHandler= false;
	k= 1;
	buildAST= true;
}

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
}

imaginaryTokenDefinitions
:
	QNAME PREDICATE FLWOR PARENTHESIZED ABSOLUTE_SLASH ABSOLUTE_DSLASH 
	WILDCARD PREFIX_WILDCARD FUNCTION UNARY_MINUS UNARY_PLUS XPOINTER 
	XPOINTER_ID VARIABLE_REF VARIABLE_BINDING ELEMENT ATTRIBUTE TEXT
	VERSION_DECL NAMESPACE_DECL DEF_NAMESPACE_DECL DEF_FUNCTION_NS_DECL 
	GLOBAL_VAR FUNCTION_DECL PROLOG
	;

xpointer
:
	"xpointer"^ LPAREN! ex:expr RPAREN!
	{ #xpointer= #(#[XPOINTER, "xpointer"], #ex); }
	|
	nc:NCNAME
	{ #xpointer= #(#[XPOINTER_ID, "id"], #nc); }
	;

xpath
:
	( module )? EOF
	;
	exception catch [RecognitionException e]
	{ handleException(e); }

module
:
	mainModule
	;
	
mainModule
:
	prolog queryBody
	;

prolog
:
		( version SEMICOLON! )?
		(
			( 
				( "declare" "namespace" ) => namespaceDecl 
				| 
				( "declare" "default" ) => defaultNamespaceDecl 
				|
				( "declare" "function" ) => functionDecl
				| varDecl
			) SEMICOLON!
		)*
	;

version
:
	"xquery" "version" v:STRING_LITERAL {
		#version = #(#[VERSION_DECL, v.getText()]);
	}
	;

namespaceDecl
:
	"declare" "namespace" prefix:NCNAME EQ! uri:STRING_LITERAL
	{
		#namespaceDecl = #(#[NAMESPACE_DECL, prefix.getText()], uri);
	}
	;

defaultNamespaceDecl
:
	"declare" "default" 
	( "element" "namespace" defu:STRING_LITERAL
		{
			#defaultNamespaceDecl = #(#[DEF_NAMESPACE_DECL, "defaultNamespaceDecl"], defu);
		}
	| "function" "namespace" deff:STRING_LITERAL
		{
			#defaultNamespaceDecl = #(#[DEF_FUNCTION_NS_DECL, "defaultFunctionNSDecl"], deff);
		}
	)
	;

varDecl
{ String varName = null; }
:
	"declare" "variable" DOLLAR! varName=qName LCURLY ex:expr RCURLY
	{
		#varDecl = #(#[GLOBAL_VAR, varName], #ex);
	}
	;

functionDecl
{ String name = null; }:
	"declare"! "function"! name=qName
	{
		#functionDecl = #(#[FUNCTION_DECL, name], #body);
	}
	LPAREN! 
	( params:paramList 
		{
			#functionDecl = #(#functionDecl, #params);
		}
	)? 
	RPAREN!
	body:functionBody
	;

functionBody:
	LCURLY! expr RCURLY!
	;

paramList:
	param (COMMA! param)*
	;
	
param
{ String varName = null; }:
	DOLLAR! varName=qName
	{
		#param = #(#[VARIABLE_BINDING, varName]);
	}
	( 
		t:typeDeclaration 
		{
			#param = #(param, #t);
		}
	)?
	;
	
typeDeclaration:
	"as"^ sequenceType
	;
	
sequenceType:
	itemType ( occurrenceIndicator )? | "empty" LPAREN! RPAREN!
	;

occurrenceIndicator:
	"?" | STAR | PLUS
	;
	
itemType:
	( "item" LPAREN ) => "item" LPAREN! RPAREN!
	| atomicType
	;
	
atomicType:
	qName
	;
	
queryBody
:
	expr
	;
	
expr
:
	exprSingle ( COMMA^ exprSingle )*
	;

exprSingle
:
	orExpr | flworExpr | ifExpr
	;

flworExpr
:
	( forClause | letClause )+ ( "where" expr )? "return"^ exprSingle
	;

forClause
:
	"for"^ inVarBinding ( COMMA! inVarBinding )*
	;

letClause
:
	"let"^ letVarBinding ( COMMA! letVarBinding )*
	;

inVarBinding
{ String varName; }
:
	DOLLAR! varName=qName "in" expr:exprSingle
	{ #inVarBinding= #(#[VARIABLE_BINDING, varName], expr); }
	;

letVarBinding
{ String varName; }
:
	DOLLAR! varName=qName COLON! EQ! expr:exprSingle
	{ #letVarBinding= #(#[VARIABLE_BINDING, varName], expr); }
	;

ifExpr:
	"if"^ LPAREN! expr RPAREN! "then"! exprSingle "else"! exprSingle
	;
	
orExpr
:
	andExpr ( "or"^ andExpr )*
	;

andExpr
:
	comparisonExpr ( "and"^ comparisonExpr )*
	;

comparisonExpr
:
	rangeExpr ( ( ( EQ^ | NEQ^ | GT^ | GTEQ^ | LT^ | LTEQ^ ) rangeExpr ) 
	| ( ( ANDEQ^ | OREQ^ ) rangeExpr ) )?
	;

rangeExpr
:
	additiveExpr ( "to"^ additiveExpr )?
	;
	
additiveExpr
:
	multiplicativeExpr ( ( PLUS^ | MINUS^ ) multiplicativeExpr )*
	;

multiplicativeExpr
:
	unaryExpr ( ( STAR^ | "div"^ | "mod"^ ) unaryExpr )*
	;

unaryExpr
:
	// TODO: XPath 2.0 allows an arbitrary number of +/-, 
	// we restrict it to one
	MINUS expr:unionExpr
	{ #unaryExpr= #(#[UNARY_MINUS, "-"], #expr); }
	|
	PLUS expr2:unionExpr
	{ #unaryExpr= #(#[UNARY_PLUS, "+"], #expr2); }
	|
	unionExpr
	;

unionExpr
:
	pathExpr ( UNION^ pathExpr )?
	;

pathExpr
:
	relativePathExpr
	|
	( SLASH relativePathExpr )
	=> SLASH relPath:relativePathExpr
	{ #pathExpr= #(#[ABSOLUTE_SLASH, "AbsoluteSlash"], #relPath); }
	// lone slash
	|
	SLASH
	{ #pathExpr= #[ABSOLUTE_SLASH, "AbsoluteSlash"]; }
	|
	DSLASH relPath2:relativePathExpr
	{ #pathExpr= #(#[ABSOLUTE_DSLASH, "AbsoluteSlashSlash"], #relPath2); }
	;

relativePathExpr
:
	stepExpr ( ( SLASH^ | DSLASH^ ) stepExpr )*
	;

stepExpr
:
	( ( "text" | "node" ) LPAREN )
	=> axisStep
	|
	( DOLLAR | ( qName LPAREN ) | SELF | LPAREN | literal | LT | XML_COMMENT )
	=> filterStep
	|
	axisStep
	;

axisStep
:
	( forwardOrReverseStep ) predicates
	;

predicates
:
	( predicate )*
	;

predicate
:
	LPPAREN! predExpr:expr RPPAREN!
	{ #predicate= #(#[PREDICATE, "Pred"], #predExpr); }
	;

forwardOrReverseStep
:
	( forwardAxisSpecifier COLON )
	=> forwardAxis nodeTest
	|
	( reverseAxisSpecifier COLON )
	=> reverseAxis nodeTest
	|
	abbrevStep
	;

abbrevStep
:
	( AT )? nodeTest | PARENT
	;

forwardAxis : forwardAxisSpecifier COLON! COLON! ;

forwardAxisSpecifier
:
	"child" | "self" | "attribute" | "descendant" | "descendant-or-self" | "following-sibling"
	;

reverseAxis : reverseAxisSpecifier COLON! COLON! ;

reverseAxisSpecifier
:
	"parent" | "ancestor" | "ancestor-or-self" | "preceding-sibling"
	;

nodeTest
:
	( ( "text" | "node" ) LPAREN ) => kindTest | nameTest
	;

nameTest
{ String name= null; }
:
	( ( NCNAME COLON STAR ) | STAR )
	=> wildcard
	|
	name=qName
	{ #nameTest= #[QNAME, name]; }
	;

wildcard
:
	// *:localname
	( STAR COLON )
	=> STAR! COLON! nc1:NCNAME
	{ #wildcard= #(#[PREFIX_WILDCARD, "*"], #nc1); }
	// prefix:*
	|
	nc2:NCNAME COLON! STAR!
	{ #wildcard= #(#nc2, #[WILDCARD, "*"]); }
	// *
	|
	STAR
	{
		// make this distinct from multiplication
		#wildcard= #[WILDCARD, "*"];
	}
	;

filterStep : primaryExpr predicates ;

primaryExpr
{ String varName= null; }
:
	functionCall
	|
	contextItemExpr
	|
	parenthesizedExpr
	|
	DOLLAR! varName=qName
	{ #primaryExpr= #[VARIABLE_REF, varName]; }
	|
	constructor
	|
	literal
	;

literal
:
	STRING_LITERAL^ | numericLiteral
	;

numericLiteral
:
	DOUBLE_LITERAL^ | DECIMAL_LITERAL^ | INTEGER_LITERAL^
	;

parenthesizedExpr
:
	LPAREN! ( e:expr  )? RPAREN!
	{ #parenthesizedExpr= #(#[PARENTHESIZED, "Parenthesized"], #e); }
	;

functionCall
{ String fnName= null; }
:
	fnName=qName LPAREN!
	{ #functionCall= #[FUNCTION, fnName]; }
	(
		params:functionParameters
		{ #functionCall= #(#[FUNCTION, fnName], #params); }
	)?
	RPAREN!
	;

functionParameters
:
	exprSingle ( COMMA! exprSingle )*
	;

contextItemExpr : SELF^ ;

kindTest
:
	textTest | anyKindTest
	;

textTest : "text"^ LPAREN! RPAREN! ;

anyKindTest : "node"^ LPAREN! RPAREN! ;

qName returns [String name]
{
	name= null;
	String name2;
}
:
	( ncnameOrKeyword COLON ncnameOrKeyword )
	=> name=ncnameOrKeyword COLON name2=ncnameOrKeyword
	{ name= name + ':' + name2; }
	|
	name=ncnameOrKeyword
	;

constructor
:
	elementConstructor | xmlComment | xmlPI
	;

elementConstructor
{
	String name= null;
	lexer.wsExplicit= true;
}
:
	( LT NCNAME ( COLON NCNAME )? WS ) => elementWithAttributes | elementWithoutAttributes
	;

elementWithoutAttributes
{ String name= null; }
:
	LT name=qName
	(
		(
			SLASH! GT!
			{ #elementWithoutAttributes= #[ELEMENT, name]; }
		)
		|
		(
			GT! { lexer.inElementContent= true; }
			content:mixedElementContent END_TAG_START! name=qName! GT!
			{ #elementWithoutAttributes= #(#[ELEMENT, name], #content); }
		)
	)
	;

elementWithAttributes
{ String name= null; }
:
	LT! name=qName attrs:attributeList
	(
		(
			SLASH! GT!
			{ #elementWithAttributes= #(#[ELEMENT, name], #attrs); }
		)
		|
		(
			GT! { lexer.inElementContent= true; }
			content:mixedElementContent END_TAG_START! name=qName! GT!
			{ #elementWithAttributes= #(#[ELEMENT, name], #attrs); }
		)
	)
	;

attributeList
:
	( attributeDef )+
	;

attributeDef
{
	String name= null;
	lexer.parseStringLiterals= false;
}
:
	WS! name=qName! EQ! QUOT!
	{ lexer.inAttributeContent= true; }
	value:attributeValue { lexer.inAttributeContent= false; }
	QUOT! { lexer.parseStringLiterals= true; }
	{ #attributeDef= #(#[ATTRIBUTE, name], #value); }
	;

attributeValue
:
	( ATTRIBUTE_CONTENT | attributeEnclosedExpr )+
	;

mixedElementContent
:
	( elementContent )*
	;

elementContent
:
	elementConstructor
	|
	content:ELEMENT_CONTENT
	{ #elementContent= #[TEXT, content.getText()]; }
	|
	xmlComment
	|
	xmlPI
	|
	enclosedExpr
	;

xmlComment : XML_COMMENT XML_COMMENT_END! ;

xmlPI : XML_PI XML_PI_END! ;

enclosedExpr
:
	LCURLY^
	{
		lexer.inElementContent= false;
		lexer.wsExplicit= false;
	}
	expr RCURLY!
	{
		lexer.inElementContent= true;
		lexer.wsExplicit= true;
	}
	;

attributeEnclosedExpr
:
	LCURLY^
	{
		lexer.inAttributeContent= false;
		lexer.wsExplicit= false;
	}
	expr RCURLY!
	{
		lexer.inAttributeContent= true;
		lexer.wsExplicit= true;
	}
	;

/* All of the literals used in this grammar can also be
 * part of a valid QName. We thus have to test for each
 * of them below.
 */
ncnameOrKeyword returns [String name]
{ name= null; }
:
	n1:NCNAME { name= n1.getText(); }
	|
	name=reservedKeywords
	;

reservedKeywords returns [String name]
{ name= null; }
:
	"div" { name= "div"; }
	|
	"mod" { name= "mod"; }
	|
	"text" { name= "text"; }
	|
	"node" { name= "node"; }
	|
	"or" { name= "or"; }
	|
	"and" { name= "and"; }
	|
	"child" { name= "child"; }
	|
	"parent" { name= "parent"; }
	|
	"self" { name= "self"; }
	|
	"attribute" { name= "attribute"; }
	|
	"ancestor" { name= "ancestor"; }
	|
	"descendant" { name= "descendant"; }
	|
	"descendant-or-self" { name= "descendant-or-self"; }
	|
	"ancestor-or-self" { name= "ancestor-or-self"; }
	|
	"preceding-sibling" { name= "preceding-sibling"; }
	|
	"following-sibling" { name= "following-sibling"; }
	|
	"item" { name = "item"; }
	;

/* -----------------------------------------------------------------------------------------------------
 * The tree parser: walks the AST created by the parser to generate
 * XPath objects.
 * ----------------------------------------------------------------------------------------------------- */

class XPathTreeParser2 extends TreeParser;

options {
	k= 1;
	defaultErrorHandler = false;
}

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

	protected void handleException(Exception e) {
		foundError= true;
		exceptions.add(e);
	}

	private static class ForLetClause {
		String varName;
		Expression inputSequence;
		Expression action;
		boolean isForClause= true;
	}
}

xpointer [PathExpr path]
:
	#( XPOINTER expr [path] )
	|
	#( XPOINTER_ID nc:NCNAME )
	{
		Function fun= new FunId(context);
		List params= new ArrayList(1);
		params.add(new LiteralValue(context, new StringValue(nc.getText())));
		fun.setArguments(params);
		path.addPath(fun);
	}
	;
	exception catch [RecognitionException e]
	{ handleException(e); }
	catch [EXistException e]
	{ handleException(e); }
	catch [PermissionDeniedException e]
	{ handleException(e); }
	catch [XPathException e]
	{ handleException(e); }

xpath [PathExpr path]
:
	module [path]
	;
	exception catch [RecognitionException e]
	{ handleException(e); }
	catch [EXistException e]
	{ handleException(e); }
	catch [PermissionDeniedException e]
	{ handleException(e); }
	catch [XPathException e]
	{ handleException(e); }

module [PathExpr path]
throws PermissionDeniedException, EXistException, XPathException
:
	prolog[path] expr[path]
	;

prolog [PathExpr path]
throws PermissionDeniedException, EXistException, XPathException:
	(
		#(v:VERSION_DECL
			{
				if(!v.getText().equals("1.0"))
					throw new XPathException("Wrong XQuery version: require 1.0");
			}
		)
	)?
	(
		#(prefix:NAMESPACE_DECL uri:STRING_LITERAL
			{
				context.declareNamespace(prefix.getText(), uri.getText());
			}
		)
		|
		#(DEF_NAMESPACE_DECL defu:STRING_LITERAL
			{
				context.declareNamespace("", defu.getText());
			}
		)
		|
		#(DEF_FUNCTION_NS_DECL deff:STRING_LITERAL
			{
				context.setDefaultFunctionNamespace(deff.getText());
			}
		)
		|
		#(qname:GLOBAL_VAR { PathExpr enclosed = new PathExpr(context); }
			expr[enclosed]
			{
				VariableDeclaration decl = new VariableDeclaration(context, qname.getText(), enclosed);
				path.add(decl);
			}
		)
	)*
	| functionDecl[path]
	;

functionDecl [PathExpr path]
throws PermissionDeniedException, EXistException, XPathException
:
	#( name:FUNCTION_DECL { PathExpr body = new PathExpr(context); }
		{ 
			System.out.println("found function declaration " + name.getText()); 
		}
		( paramList )?
		expr[body]
	)
	;

paramList:
	param (param)*
	;

param:
	#( var:VARIABLE_BINDING
		{
			System.out.println("found variable binding: " + var.getText());
		}
	)
	;
	
expr [PathExpr path]
throws PermissionDeniedException, EXistException, XPathException
{ Expression step= null; }
:
	#(
		COMMA
		{
			PathExpr left= new PathExpr(context);
			PathExpr right= new PathExpr(context);
		}
		expr [left]
		expr [right]
		{
			SequenceConstructor sc = new SequenceConstructor(context);
			sc.addExpression(left);
			sc.addExpression(right);
			path.add(sc);
		}
	)
	|
	#(
		"if"
		{
			PathExpr testExpr = new PathExpr(context);
			PathExpr thenExpr = new PathExpr(context);
			PathExpr elseExpr = new PathExpr(context);
		}
		expr[testExpr]
		expr[thenExpr]
		expr[elseExpr]
		{
			ConditionalExpression cond = 
				new ConditionalExpression(context, testExpr, thenExpr, elseExpr);
			path.add(cond);
		}
	)
	|
	#(
		"return"
		{
			List clauses= new ArrayList();
			Expression action= new PathExpr(context);
			PathExpr whereExpr= null;
		}
		(
			#(
				"for"
				(
					#(
						varName:VARIABLE_BINDING
						{
							ForLetClause clause= new ForLetClause();
							PathExpr inputSequence= new PathExpr(context);
						}
						expr [inputSequence]
						{
							clause.varName= varName.getText();
							clause.inputSequence= inputSequence;
							clauses.add(clause);
						}
					)
				)+
			)
			|
			#(
				"let"
				(
					#(
						letVarName:VARIABLE_BINDING
						{
							ForLetClause clause= new ForLetClause();
							clause.isForClause= false;
							PathExpr inputSequence= new PathExpr(context);
						}
						expr [inputSequence]
						{
							clause.varName= letVarName.getText();
							clause.inputSequence= inputSequence;
							clauses.add(clause);
						}
					)
				)+
			)
		)+
		(
			"where"
			{ whereExpr= new PathExpr(context); }
			expr [whereExpr]
		)?
		expr [(PathExpr) action]
		{
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
				if (whereExpr != null) {
					expr.setWhereExpression(whereExpr);
					whereExpr= null;
				}
				action= expr;
			}
			path.add(action);
		}
	)
	|
	#(
		"or"
		{
			PathExpr left= new PathExpr(context);
			PathExpr right= new PathExpr(context);
		}
		expr [left]
		expr [right]
	)
	{
		OpOr or= new OpOr(context);
		or.add(left);
		or.add(right);
		path.addPath(or);
	}
	|
	#(
		"and"
		{
			PathExpr left= new PathExpr(context);
			PathExpr right= new PathExpr(context);
		}
		expr [left]
		expr [right]
	)
	{
		OpAnd and= new OpAnd(context);
		and.add(left);
		and.add(right);
		path.addPath(and);
	}
	|
	#(
		PARENTHESIZED
		{
			PathExpr pathExpr= new PathExpr(context);
			path.addPath(pathExpr);
		}
		( expr [pathExpr] )?
	)
	|
	#(
		UNION
		{
			PathExpr left= new PathExpr(context);
			PathExpr right= new PathExpr(context);
		}
		expr [left]
		expr [right]
	)
	{
		Union union= new Union(context, left, right);
		path.addPath(union);
	}
	|
	#(
		ABSOLUTE_SLASH
		{
			RootNode root= new RootNode(context);
			path.add(root);
		}
		( expr [path] )?
	)
	|
	#(
		ABSOLUTE_DSLASH
		{
			RootNode root= new RootNode(context);
			path.add(root);
		}
		(
			step=pathExpr [path]
			{
				if (step instanceof LocationStep) {
					LocationStep s= (LocationStep) step;
					if (s.getAxis() == Constants.ATTRIBUTE_AXIS)
						// combines descendant-or-self::node()/attribute:*
						s.setAxis(Constants.DESCENDANT_ATTRIBUTE_AXIS);
					else
						s.setAxis(Constants.DESCENDANT_SELF_AXIS);
				}
			}
		)?
	)
	|
	#( "to"
		{
			PathExpr start = new PathExpr(context);
			PathExpr end = new PathExpr(context);
			List args = new ArrayList(2);
			args.add(start);
			args.add(end);
		}
		expr[start]
		expr[end]
		{
			RangeExpression range = new RangeExpression(context);
			range.setArguments(args);
			path.addPath(range);
		}
	)
	|
	step=generalComp [path]
	|
	step=fulltextComp [path]
	|
	step=pathExpr [path]
	|
	step=numericExpr [path]
	|
	step=constructor [path]
	;

pathExpr [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
	Expression rightStep= null;
	step= null;
	int axis= Constants.CHILD_AXIS;
}
:
	c:STRING_LITERAL
	{
		step= new LiteralValue(context, new StringValue(c.getText()));
		path.add(step);
	}
	|
	i:INTEGER_LITERAL
	{
		step= new LiteralValue(context, new IntegerValue(Integer.parseInt(i.getText())));
		path.add(step);
	}
	|
	(
		dec:DECIMAL_LITERAL
		{ step= new LiteralValue(context, new DoubleValue(Double.parseDouble(dec.getText()))); }
		|
		dbl:DOUBLE_LITERAL
		{ step= new LiteralValue(context, new DoubleValue(Double.parseDouble(dbl.getText()))); }
	)
	{ path.add(step); }
	|
	v:VARIABLE_REF
	{
		step= new VariableReference(context, v.getText());
		path.add(step);
	}
	|
	step=functionCall [path]
	|
	( axis=forwardAxis )?
	{ NodeTest test; }
	(
		qn:QNAME
		{
			QName qname= QName.parse(context, qn.getText());
			test= new NameTest(Type.ELEMENT, qname);
		}
		|
		#( PREFIX_WILDCARD nc1:NCNAME )
		{
			QName qname= new QName(nc1.getText(), null, null);
			test= new NameTest(Type.ELEMENT, qname);
		}
		|
		#( nc:NCNAME WILDCARD )
		{
			String namespaceURI= context.getURIForPrefix(nc.getText());
			QName qname= new QName(null, namespaceURI, null);
			test= new NameTest(Type.ELEMENT, qname);
		}
		|
		WILDCARD
		{ test= new TypeTest(Type.ELEMENT); }
		|
		"node"
		{ test= new AnyNodeTest(); }
		|
		"text"
		{ test= new TypeTest(Type.TEXT); }
	)
	{
		step= new LocationStep(context, axis, test);
		path.add(step);
	}
	( predicate [(LocationStep) step] )*
	|
	AT
	{ QName qname; }
	(
		attr:QNAME
		{ qname= QName.parseAttribute(context, attr.getText()); }
		|
		#( PREFIX_WILDCARD nc2:NCNAME )
		{ qname= new QName(nc2.getText(), null, null); }
		|
		#( nc3:NCNAME WILDCARD )
		{
			String namespaceURI= context.getURIForPrefix(nc3.getText());
			if (namespaceURI == null)
				throw new EXistException("No namespace defined for prefix " + nc.getText());
			qname= new QName(null, namespaceURI, null);
		}
	)
	{
		step= new LocationStep(context, Constants.ATTRIBUTE_AXIS, new NameTest(Type.ATTRIBUTE, qname));
		path.add(step);
	}
	( predicate [(LocationStep) step] )*
	|
	SELF
	{
		step= new LocationStep(context, Constants.SELF_AXIS, new TypeTest(Type.NODE));
		path.add(step);
	}
	( predicate [(LocationStep) step] )*
	|
	PARENT
	{
		step= new LocationStep(context, Constants.PARENT_AXIS, new TypeTest(Type.NODE));
		path.add(step);
	}
	( predicate [(LocationStep) step] )*
	|
	#(
		SLASH step=pathExpr [path]
		(
			rightStep=pathExpr [path]
			{
				if (rightStep instanceof LocationStep && ((LocationStep) rightStep).getAxis() == -1)
					 ((LocationStep) rightStep).setAxis(Constants.CHILD_AXIS);
			}
		)?
	)
	{
		if (step instanceof LocationStep && ((LocationStep) step).getAxis() == -1)
			 ((LocationStep) step).setAxis(Constants.CHILD_AXIS);
	}
	|
	#(
		DSLASH step=pathExpr [path]
		(
			rightStep=pathExpr [path]
			{
				if (rightStep instanceof LocationStep) {
					LocationStep rs= (LocationStep) rightStep;
					if (rs.getAxis() == Constants.ATTRIBUTE_AXIS)
						rs.setAxis(Constants.DESCENDANT_ATTRIBUTE_AXIS);
					else
						rs.setAxis(Constants.DESCENDANT_SELF_AXIS);
				}
			}
		)?
	)
	{
		if (step instanceof LocationStep && ((LocationStep) rightStep).getAxis() == -1)
			 ((LocationStep) step).setAxis(Constants.DESCENDANT_SELF_AXIS);
	}
	;

numericExpr [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
	step= null;
	PathExpr left= new PathExpr(context);
	PathExpr right= new PathExpr(context);
}
:
	#( PLUS expr [left] expr [right] )
	{
		OpNumeric op= new OpNumeric(context, left, right, Constants.PLUS);
		path.addPath(op);
		step= op;
	}
	|
	#( MINUS expr [left] expr [right] )
	{
		OpNumeric op= new OpNumeric(context, left, right, Constants.MINUS);
		path.addPath(op);
		step= op;
	}
	|
	#( UNARY_MINUS expr [left] )
	{
		UnaryExpr unary= new UnaryExpr(context, Constants.MINUS);
		unary.add(left);
		path.addPath(unary);
		step= unary;
	}
	|
	#( UNARY_PLUS expr [left] )
	{
		UnaryExpr unary= new UnaryExpr(context, Constants.PLUS);
		unary.add(left);
		path.addPath(unary);
		step= unary;
	}
	|
	#( "div" expr [left] expr [right] )
	{
		OpNumeric op= new OpNumeric(context, left, right, Constants.DIV);
		path.addPath(op);
		step= op;
	}
	|
	#( "mod" expr [left] expr [right] )
	{
		OpNumeric op= new OpNumeric(context, left, right, Constants.MOD);
		path.addPath(op);
		step= op;
	}
	|
	#( STAR expr [left] expr [right] )
	{
		OpNumeric op= new OpNumeric(context, left, right, Constants.MULT);
		path.addPath(op);
		step= op;
	}
	;

predicate [LocationStep step]
throws PermissionDeniedException, EXistException, XPathException
:
	#(
		PREDICATE
		{ Predicate predicateExpr= new Predicate(context); }
		expr [predicateExpr]
		{ step.addPredicate(predicateExpr); }
	)
	;

functionCall [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
	PathExpr pathExpr;
	step= null;
}
:
	#(
		fn:FUNCTION
		{ List params= new ArrayList(2); }
		(
			{ pathExpr= new PathExpr(context); }
			expr [pathExpr]
			{ params.add(pathExpr); }
		)*
	)
	{ step = Util.createFunction(context, path, fn.getText(), params); }
	;

forwardAxis returns [int axis]
throws PermissionDeniedException, EXistException
{ axis= -1; }
:
	"child" { axis= Constants.CHILD_AXIS; }
	|
	"attribute" { axis= Constants.ATTRIBUTE_AXIS; }
	|
	"self" { axis= Constants.SELF_AXIS; }
	|
	"parent" { axis= Constants.PARENT_AXIS; }
	|
	"descendant" { axis= Constants.DESCENDANT_AXIS; }
	|
	"descendant-or-self" { axis= Constants.DESCENDANT_SELF_AXIS; }
	|
	"following-sibling" { axis= Constants.FOLLOWING_SIBLING_AXIS; }
	|
	"preceding-sibling" { axis= Constants.PRECEDING_SIBLING_AXIS; }
	|
	"ancestor" { axis= Constants.ANCESTOR_AXIS; }
	|
	"ancestor-or-self" { axis= Constants.ANCESTOR_SELF_AXIS; }
	;

fulltextComp [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
	step= null;
	PathExpr nodes= new PathExpr(context);
	PathExpr query= new PathExpr(context);
}
:
	#( ANDEQ expr [nodes] expr [query] )
	{
		ExtFulltext exprCont= new ExtFulltext(context, Constants.FULLTEXT_AND);
		exprCont.setPath(nodes);
		exprCont.addTerm(query);
		path.addPath(exprCont);
	}
	|
	#( OREQ expr [nodes] expr [query] )
	{
		ExtFulltext exprCont= new ExtFulltext(context, Constants.FULLTEXT_OR);
		exprCont.setPath(nodes);
		exprCont.addTerm(query);
		path.addPath(exprCont);
	}
	;

generalComp [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
	step= null;
	PathExpr left= new PathExpr(context);
	PathExpr right= new PathExpr(context);
}
:
	#(
		EQ expr [left]
		expr [right]
		{
			step= new GeneralComparison(context, left, right, Constants.EQ);
			path.add(step);
		}
	)
	|
	#(
		NEQ expr [left]
		expr [right]
		{
			step= new GeneralComparison(context, left, right, Constants.NEQ);
			path.add(step);
		}
	)
	|
	#(
		LT expr [left]
		expr [right]
		{
			step= new GeneralComparison(context, left, right, Constants.LT);
			path.add(step);
		}
	)
	|
	#(
		LTEQ expr [left]
		expr [right]
		{
			step= new GeneralComparison(context, left, right, Constants.LTEQ);
			path.add(step);
		}
	)
	|
	#(
		GT expr [left]
		expr [right]
		{
			step= new GeneralComparison(context, left, right, Constants.GT);
			path.add(step);
		}
	)
	|
	#(
		GTEQ expr [left]
		expr [right]
		{
			step= new GeneralComparison(context, left, right, Constants.GTEQ);
			path.add(step);
		}
	)
	;

constructor [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException, XPathException
{
	step= null;
	PathExpr elementContent= null;
}
:
	#(
		e:ELEMENT
		{
			ElementConstructor c= new ElementConstructor(context, e.getText());
			path.add(c);
			step= c;
		}
		(
			#(
				attrName:ATTRIBUTE
				{
					AttributeConstructor attrib= new AttributeConstructor(context, attrName.getText());
					c.addAttribute(attrib);
				}
				(
					attrVal:ATTRIBUTE_CONTENT
					{ attrib.addValue(attrVal.getText()); }
					|
					#(
						LCURLY { PathExpr enclosed= new PathExpr(context); }
						expr [enclosed]
						{ attrib.addEnclosedExpr(enclosed); }
					)
				)+
			)
		)*
		(
			{
				if (elementContent == null) {
					elementContent= new PathExpr(context);
					c.setContent(elementContent);
				}
			}
			constructor [elementContent]
		)*
	)
	|
	#(
		pcdata:TEXT
		{
			TextConstructor text= new TextConstructor(context, pcdata.getText());
			path.add(text);
			step= text;
		}
	)
	|
	#(
		cdata:XML_COMMENT
		{
			CommentConstructor comment= new CommentConstructor(context, cdata.getText());
			path.add(comment);
			step= comment;
		}
	)
	|
	#(
		p:XML_PI
		{
			PIConstructor pi= new PIConstructor(context, p.getText());
			path.add(pi);
			step= pi;
		}
	)
	|
	#(
		LCURLY { EnclosedExpr subexpr= new EnclosedExpr(context); }
		expr [subexpr]
		{
			path.addPath(subexpr);
			step= subexpr;
		}
	)
	;

/* -----------------------------------------------------------------------------------------------------
 * The XPath lexer.
 * ----------------------------------------------------------------------------------------------------- */

class XPathLexer2 extends Lexer;

options {
	k = 3;
	testLiterals = true;
	charVocabulary = '\u0003'..'\uffff';
	codeGenBitsetTestThreshold = 20;
}

{
	protected boolean wsExplicit= false;
	protected boolean parseStringLiterals= true;
	protected boolean inElementContent= false;
	protected boolean inAttributeContent= false;
	protected boolean inComment= false;
}

protected SLASH : '/' ;
protected DSLASH : '/' '/' ;
protected COLON : ':' ;
protected COMMA : ',' ;
protected SEMICOLON : ';';
protected STAR : '*' ;
protected PLUS : '+' ;
protected MINUS : '-' ;
protected LPPAREN : '[' ;
protected RPPAREN : ']' ;
protected LPAREN : '(' ;
protected RPAREN : ')' ;
protected SELF : '.' ;
protected PARENT : ".." ;
protected UNION : '|' ;
protected AT : '@' ;
protected DOLLAR : '$' ;
protected ANDEQ : "&=" ;
protected OREQ : "|=" ;
protected EQ : '=' ;
protected NEQ : "!=" ;
protected GT : '>' ;
protected GTEQ : ">=" ;
protected QUOT : '"' ;
protected LTEQ : "<=" ;

protected LT : '<' ;
protected END_TAG_START : "</" ;

protected LCURLY : '{' ;
protected RCURLY : '}' ;

protected XML_COMMENT_END : "-->" ;
protected XML_PI_START : "<?" ;
protected XML_PI_END : "?>" ;

protected CHAR:
	( '\t' | '\n' | '\r' | '\u0020'..'\u0039' | '\u003B'..'\uD7FF' | '\uE000'..'\uFFFD' )
	;
	
protected BASECHAR
options {
	testLiterals=true;
}
:
	(
		'\u0041'..'\u005a'
		|
		'\u0061'..'\u007a'
		|
		'\u00c0'..'\u00d6'
		|
		'\u00d8'..'\u00f6'
		|
		'\u00f8'..'\u00ff'
		|
		'\u0100'..'\u0131'
		|
		'\u0134'..'\u013e'
		|
		'\u0141'..'\u0148'
		|
		'\u014a'..'\u017e'
		|
		'\u0180'..'\u01c3'
		|
		'\u01cd'..'\u01f0'
		|
		'\u01f4'..'\u01f5'
		|
		'\u01fa'..'\u0217'
		|
		'\u0250'..'\u02a8'
		|
		'\u02bb'..'\u02c1'
		|
		'\u0386'
		|
		'\u0388'..'\u038a'
		|
		'\u038c'
		|
		'\u038e'..'\u03a1'
		|
		'\u03a3'..'\u03ce'
		|
		'\u03d0'..'\u03d6'
		|
		'\u03da'
		|
		'\u03dc'
		|
		'\u03de'
		|
		'\u03e0'
		|
		'\u03e2'..'\u03f3'
		|
		'\u0401'..'\u040c'
		|
		'\u040e'..'\u044f'
		|
		'\u0451'..'\u045c'
		|
		'\u045e'..'\u0481'
		|
		'\u0490'..'\u04c4'
		|
		'\u04c7'..'\u04c8'
		|
		'\u04cb'..'\u04cc'
		|
		'\u04d0'..'\u04eb'
		|
		'\u04ee'..'\u04f5'
		|
		'\u04f8'..'\u04f9'
		|
		'\u0531'..'\u0556'
		|
		'\u0559'
		|
		'\u0561'..'\u0586'
		|
		'\u05d0'..'\u05ea'
		|
		'\u05f0'..'\u05f2'
		|
		'\u0621'..'\u063a'
		|
		'\u0641'..'\u064a'
		|
		'\u0671'..'\u06b7'
		|
		'\u06ba'..'\u06be'
		|
		'\u06c0'..'\u06ce'
		|
		'\u06d0'..'\u06d3'
		|
		'\u06d5'
		|
		'\u06e5'..'\u06e6'
		|
		'\u0905'..'\u0939'
		|
		'\u093d'
		|
		'\u0958'..'\u0961'
		|
		'\u0985'..'\u098c'
		|
		'\u098f'..'\u0990'
		|
		'\u0993'..'\u09a8'
		|
		'\u09aa'..'\u09b0'
		|
		'\u09b2'
		|
		'\u09b6'..'\u09b9'
		|
		'\u09dc'..'\u09dd'
		|
		'\u09df'..'\u09e1'
		|
		'\u09f0'..'\u09f1'
		|
		'\u0a05'..'\u0a0a'
		|
		'\u0a0f'..'\u0a10'
		|
		'\u0a13'..'\u0a28'
		|
		'\u0a2a'..'\u0a30'
		|
		'\u0a32'..'\u0a33'
		|
		'\u0a35'..'\u0a36'
		|
		'\u0a38'..'\u0a39'
		|
		'\u0a59'..'\u0a5c'
		|
		'\u0a5e'
		|
		'\u0a72'..'\u0a74'
		|
		'\u0a85'..'\u0a8b'
		|
		'\u0a8d'
		|
		'\u0a8f'..'\u0a91'
		|
		'\u0a93'..'\u0aa8'
		|
		'\u0aaa'..'\u0ab0'
		|
		'\u0ab2'..'\u0ab3'
		|
		'\u0ab5'..'\u0ab9'
		|
		'\u0abd'
		|
		'\u0ae0'
		|
		'\u0b05'..'\u0b0c'
		|
		'\u0b0f'..'\u0b10'
		|
		'\u0b13'..'\u0b28'
		|
		'\u0b2a'..'\u0b30'
		|
		'\u0b32'..'\u0b33'
		|
		'\u0b36'..'\u0b39'
		|
		'\u0b3d'
		|
		'\u0b5c'..'\u0b5d'
		|
		'\u0b5f'..'\u0b61'
		|
		'\u0b85'..'\u0b8a'
		|
		'\u0b8e'..'\u0b90'
		|
		'\u0b92'..'\u0b95'
		|
		'\u0b99'..'\u0b9a'
		|
		'\u0b9c'
		|
		'\u0b9e'..'\u0b9f'
		|
		'\u0ba3'..'\u0ba4'
		|
		'\u0ba8'..'\u0baa'
		|
		'\u0bae'..'\u0bb5'
		|
		'\u0bb7'..'\u0bb9'
		|
		'\u0c05'..'\u0c0c'
		|
		'\u0c0e'..'\u0c10'
		|
		'\u0c12'..'\u0c28'
		|
		'\u0c2a'..'\u0c33'
		|
		'\u0c35'..'\u0c39'
		|
		'\u0c60'..'\u0c61'
		|
		'\u0c85'..'\u0c8c'
		|
		'\u0c8e'..'\u0c90'
		|
		'\u0c92'..'\u0ca8'
		|
		'\u0caa'..'\u0cb3'
		|
		'\u0cb5'..'\u0cb9'
		|
		'\u0cde'
		|
		'\u0ce0'..'\u0ce1'
		|
		'\u0d05'..'\u0d0c'
		|
		'\u0d0e'..'\u0d10'
		|
		'\u0d12'..'\u0d28'
		|
		'\u0d2a'..'\u0d39'
		|
		'\u0d60'..'\u0d61'
		|
		'\u0e01'..'\u0e2e'
		|
		'\u0e30'
		|
		'\u0e32'..'\u0e33'
		|
		'\u0e40'..'\u0e45'
		|
		'\u0e81'..'\u0e82'
		|
		'\u0e84'
		|
		'\u0e87'..'\u0e88'
		|
		'\u0e8a'
		|
		'\u0e8d'
		|
		'\u0e94'..'\u0e97'
		|
		'\u0e99'..'\u0e9f'
		|
		'\u0ea1'..'\u0ea3'
		|
		'\u0ea5'
		|
		'\u0ea7'
		|
		'\u0eaa'..'\u0eab'
		|
		'\u0ead'..'\u0eae'
		|
		'\u0eb0'
		|
		'\u0eb2'..'\u0eb3'
		|
		'\u0ebd'
		|
		'\u0ec0'..'\u0ec4'
		|
		'\u0f40'..'\u0f47'
		|
		'\u0f49'..'\u0f69'
		|
		'\u10a0'..'\u10c5'
		|
		'\u10d0'..'\u10f6'
		|
		'\u1100'
		|
		'\u1102'..'\u1103'
		|
		'\u1105'..'\u1107'
		|
		'\u1109'
		|
		'\u110b'..'\u110c'
		|
		'\u110e'..'\u1112'
		|
		'\u113c'
		|
		'\u113e'
		|
		'\u1140'
		|
		'\u114c'
		|
		'\u114e'
		|
		'\u1150'
		|
		'\u1154'..'\u1155'
		|
		'\u1159'
		|
		'\u115f'..'\u1161'
		|
		'\u1163'
		|
		'\u1165'
		|
		'\u1167'
		|
		'\u1169'
		|
		'\u116d'..'\u116e'
		|
		'\u1172'..'\u1173'
		|
		'\u1175'
		|
		'\u119e'
		|
		'\u11a8'
		|
		'\u11ab'
		|
		'\u11ae'..'\u11af'
		|
		'\u11b7'..'\u11b8'
		|
		'\u11ba'
		|
		'\u11bc'..'\u11c2'
		|
		'\u11eb'
		|
		'\u11f0'
		|
		'\u11f9'
		|
		'\u1e00'..'\u1e9b'
		|
		'\u1ea0'..'\u1ef9'
		|
		'\u1f00'..'\u1f15'
		|
		'\u1f18'..'\u1f1d'
		|
		'\u1f20'..'\u1f45'
		|
		'\u1f48'..'\u1f4d'
		|
		'\u1f50'..'\u1f57'
		|
		'\u1f59'
		|
		'\u1f5b'
		|
		'\u1f5d'
		|
		'\u1f5f'..'\u1f7d'
		|
		'\u1f80'..'\u1fb4'
		|
		'\u1fb6'..'\u1fbc'
		|
		'\u1fbe'
		|
		'\u1fc2'..'\u1fc4'
		|
		'\u1fc6'..'\u1fcc'
		|
		'\u1fd0'..'\u1fd3'
		|
		'\u1fd6'..'\u1fdb'
		|
		'\u1fe0'..'\u1fec'
		|
		'\u1ff2'..'\u1ff4'
		|
		'\u1ff6'..'\u1ffc'
		|
		'\u2126'
		|
		'\u212a'..'\u212b'
		|
		'\u212e'
		|
		'\u2180'..'\u2182'
		|
		'\u3041'..'\u3094'
		|
		'\u30a1'..'\u30fa'
		|
		'\u3105'..'\u312c'
		|
		'\uac00'..'\ud7a3'
	)
	;

protected IDEOGRAPHIC
:
	( '\u4e00'..'\u9fa5' | '\u3007' | '\u3021'..'\u3029' )
	;

protected COMBINING_CHAR
:
	(
		'\u0300'..'\u0345'
		|
		'\u0360'..'\u0361'
		|
		'\u0483'..'\u0486'
		|
		'\u0591'..'\u05a1'
		|
		'\u05a3'..'\u05b9'
		|
		'\u05bb'..'\u05bd'
		|
		'\u05bf'
		|
		'\u05c1'..'\u05c2'
		|
		'\u05c4'
		|
		'\u064b'..'\u0652'
		|
		'\u0670'
		|
		'\u06d6'..'\u06dc'
		|
		'\u06dd'..'\u06df'
		|
		'\u06e0'..'\u06e4'
		|
		'\u06e7'..'\u06e8'
		|
		'\u06ea'..'\u06ed'
		|
		'\u0901'..'\u0903'
		|
		'\u093c'
		|
		'\u093e'..'\u094c'
		|
		'\u094d'
		|
		'\u0951'..'\u0954'
		|
		'\u0962'..'\u0963'
		|
		'\u0981'..'\u0983'
		|
		'\u09bc'
		|
		'\u09be'
		|
		'\u09bf'
		|
		'\u09c0'..'\u09c4'
		|
		'\u09c7'..'\u09c8'
		|
		'\u09cb'..'\u09cd'
		|
		'\u09d7'
		|
		'\u09e2'..'\u09e3'
		|
		'\u0a02'
		|
		'\u0a3c'
		|
		'\u0a3e'
		|
		'\u0a3f'
		|
		'\u0a40'..'\u0a42'
		|
		'\u0a47'..'\u0a48'
		|
		'\u0a4b'..'\u0a4d'
		|
		'\u0a70'..'\u0a71'
		|
		'\u0a81'..'\u0a83'
		|
		'\u0abc'
		|
		'\u0abe'..'\u0ac5'
		|
		'\u0ac7'..'\u0ac9'
		|
		'\u0acb'..'\u0acd'
		|
		'\u0b01'..'\u0b03'
		|
		'\u0b3c'
		|
		'\u0b3e'..'\u0b43'
		|
		'\u0b47'..'\u0b48'
		|
		'\u0b4b'..'\u0b4d'
		|
		'\u0b56'..'\u0b57'
		|
		'\u0b82'..'\u0b83'
		|
		'\u0bbe'..'\u0bc2'
		|
		'\u0bc6'..'\u0bc8'
		|
		'\u0bca'..'\u0bcd'
		|
		'\u0bd7'
		|
		'\u0c01'..'\u0c03'
		|
		'\u0c3e'..'\u0c44'
		|
		'\u0c46'..'\u0c48'
		|
		'\u0c4a'..'\u0c4d'
		|
		'\u0c55'..'\u0c56'
		|
		'\u0c82'..'\u0c83'
		|
		'\u0cbe'..'\u0cc4'
		|
		'\u0cc6'..'\u0cc8'
		|
		'\u0cca'..'\u0ccd'
		|
		'\u0cd5'..'\u0cd6'
		|
		'\u0d02'..'\u0d03'
		|
		'\u0d3e'..'\u0d43'
		|
		'\u0d46'..'\u0d48'
		|
		'\u0d4a'..'\u0d4d'
		|
		'\u0d57'
		|
		'\u0e31'
		|
		'\u0e34'..'\u0e3a'
		|
		'\u0e47'..'\u0e4e'
		|
		'\u0eb1'
		|
		'\u0eb4'..'\u0eb9'
		|
		'\u0ebb'..'\u0ebc'
		|
		'\u0ec8'..'\u0ecd'
		|
		'\u0f18'..'\u0f19'
		|
		'\u0f35'
		|
		'\u0f37'
		|
		'\u0f39'
		|
		'\u0f3e'
		|
		'\u0f3f'
		|
		'\u0f71'..'\u0f84'
		|
		'\u0f86'..'\u0f8b'
		|
		'\u0f90'..'\u0f95'
		|
		'\u0f97'
		|
		'\u0f99'..'\u0fad'
		|
		'\u0fb1'..'\u0fb7'
		|
		'\u0fb9'
		|
		'\u20d0'..'\u20dc'
		|
		'\u20e1'
		|
		'\u302a'..'\u302f'
		|
		'\u3099'
		|
		'\u309a'
	)
	;

protected DIGIT
:
	(
		'\u0030'..'\u0039'
		|
		'\u0660'..'\u0669'
		|
		'\u06f0'..'\u06f9'
		|
		'\u0966'..'\u096f'
		|
		'\u09e6'..'\u09ef'
		|
		'\u0a66'..'\u0a6f'
		|
		'\u0ae6'..'\u0aef'
		|
		'\u0b66'..'\u0b6f'
		|
		'\u0be7'..'\u0bef'
		|
		'\u0c66'..'\u0c6f'
		|
		'\u0ce6'..'\u0cef'
		|
		'\u0d66'..'\u0d6f'
		|
		'\u0e50'..'\u0e59'
		|
		'\u0ed0'..'\u0ed9'
		|
		'\u0f20'..'\u0f29'
	)
	;

protected EXTENDER
:
	(
		'\u00b7'
		|
		'\u02d0'
		|
		'\u02d1'
		|
		'\u0387'
		|
		'\u0640'
		|
		'\u0e46'
		|
		'\u0ec6'
		|
		'\u3005'
		|
		'\u3031'..'\u3035'
		|
		'\u309d'..'\u309e'
		|
		'\u30fc'..'\u30fe'
	)
	;

protected LETTER
:
	( BASECHAR | IDEOGRAPHIC )
	;

protected DIGITS
:
	( DIGIT )+
	;


protected HEX_DIGITS
:
	( '0'..'9' | 'a'..'f' | 'A'..'F' )+
	;

protected NMSTART
:
	( LETTER | '_' )
	;

protected NMCHAR
:
	( LETTER | DIGIT | '.' | '-' | '_' | COMBINING_CHAR | EXTENDER )
	;

protected NCNAME
options {
	testLiterals=false;
}
:
	NMSTART ( NMCHAR )*
	;

protected WS
:
	( ' ' | '\t' | '\n' { newline(); } | '\r' )+
	;

protected EXPR_COMMENT
:
	"(:" ( CHAR | ( ':' ~( ')' ) ) => ':' )* ":)"
	;
	
protected INTEGER_LITERAL : DIGITS ;

protected DECIMAL_LITERAL
:
	( '.' DIGITS ) | ( DIGITS '.' ) => DIGITS '.' ( DIGITS )?
	;

protected DOUBLE_LITERAL
:
	( ( '.' DIGITS ) | ( DIGITS ( '.' ( DIGIT )* )? ) ) ( 'e' | 'E' ) ( '+' | '-' )? DIGITS
	;

protected PREDEFINED_ENTITY_REF
:
	'&' ( "lt" | "gt" | "amp" | "quot" | "apos" ) ';'
	;

protected CHAR_REF
:
	'&' '#' ( DIGITS | ( 'x' HEX_DIGITS ) ) ';'
	;

protected STRING_LITERAL
options {
	testLiterals = false;
}
:
	'"'! ( PREDEFINED_ENTITY_REF | CHAR_REF | ( '"'! '"' ) | ~ ( '"' | '&' ) )*
	'"'!
	|
	'\''! ( PREDEFINED_ENTITY_REF | CHAR_REF | ( '\''! '\'' ) | ~ ( '\'' | '&' ) )*
	'\''!
	;

protected ATTRIBUTE_CONTENT
options {
	testLiterals=false;
}
:
	(
		'\t'
		|
		'\r'
		|
		'\n'
		|
		'\u0020'
		|
		'\u0021'
		|
		'\u0023'..'\u0025'
		|
		'\''..'\u003b'
		|
		'\u003d'..'\u007a'
		|
		'\u007c'
		|
		'\u007e'..'\uFFFD'
	)+
	;

protected ELEMENT_CONTENT
options {
	testLiterals=false;
}
:
	( '\t' | '\r' | '\n' | '\u0020'..'\u0025' | '\''..'\u003b' | '\u003d'..'\u007a' | '\u007c' | '\u007e'..'\uFFFD' )+
	;

protected XML_COMMENT
options {
	testLiterals=false;
}
:
	"<!--"! ( ~ ( '-' ) | ( '-' ~ ( '-' ) ) => '-' )+
	;

protected XML_PI
options {
	testLiterals=false;
}
:
	XML_PI_START! NCNAME ' ' ( ~ ( '?' ) | ( '?' ~ ( '>' ) ) => '?' )+
	;

NEXT_TOKEN
:
	XML_COMMENT { $setType(XML_COMMENT); }
	|
	( XML_PI_START )
	=> XML_PI { $setType(XML_PI); }
	|
	END_TAG_START
	{
		inElementContent= false;
		wsExplicit= false;
		$setType(END_TAG_START);
	}
	|
	LT
	{
		inElementContent= false;
		$setType(LT);
	}
	|
	LTEQ { $setType(LTEQ); }
	|
	LCURLY
	{
		inElementContent= false;
		$setType(LCURLY);
	}
	|
	RCURLY { $setType(RCURLY); }
	|
	{ inAttributeContent }?
	ATTRIBUTE_CONTENT
	{ $setType(ATTRIBUTE_CONTENT); }
	|
	{ !(parseStringLiterals || inElementContent) }?
	QUOT
	{ $setType(QUOT); }
	|
	{ inElementContent }?
	ELEMENT_CONTENT
	{ $setType(ELEMENT_CONTENT); }
	|
	WS
	{
		if (wsExplicit)
			$setType(WS);
		else
			$setType(Token.SKIP);
	}
	| 
	EXPR_COMMENT
	{
		$setType(Token.SKIP);
	}
	|
	NCNAME { $setType(NCNAME); }
	|
	{ parseStringLiterals }?
	STRING_LITERAL { $setType(STRING_LITERAL); }
	|
	( '.' '.' )
	=> PARENT { $setType(PARENT); }
	|
	( '.' INTEGER_LITERAL )
	=> DECIMAL_LITERAL { $setType(DECIMAL_LITERAL); }
	|
	( '.' )
	=> SELF { $setType(SELF); }
	|
	( DECIMAL_LITERAL ( 'e' | 'E' ) )
	=> DOUBLE_LITERAL
	{ $setType(DOUBLE_LITERAL); }
	|
	( INTEGER_LITERAL '.' )
	=> DECIMAL_LITERAL
	{ $setType(DECIMAL_LITERAL); }
	|
	INTEGER_LITERAL { $setType(INTEGER_LITERAL); }
	|
	SLASH { $setType(SLASH); }
	|
	DSLASH { $setType(DSLASH); }
	|
	COLON { $setType(COLON); }
	|
	COMMA { $setType(COMMA); }
	|
	SEMICOLON { $setType(SEMICOLON); }
	|
	STAR { $setType(STAR); }
	|
	PLUS { $setType(PLUS); }
	|
	MINUS { $setType(MINUS); }
	|
	LPPAREN { $setType(LPPAREN); }
	|
	RPPAREN { $setType(RPPAREN); }
	|
	LPAREN { $setType(LPAREN); }
	|
	RPAREN { $setType(RPAREN); }
	|
	UNION { $setType(UNION); }
	|
	AT { $setType(AT); }
	|
	DOLLAR { $setType(DOLLAR); }
	|
	OREQ { $setType(OREQ); }
	|
	ANDEQ { $setType(ANDEQ); }
	|
	EQ { $setType(EQ); }
	|
	NEQ { $setType(NEQ); }
	|
	GT { $setType(GT); }
	|
	GTEQ { $setType(GTEQ); }
	|
	XML_COMMENT_END { $setType(XML_COMMENT_END); }
	|
	XML_PI_END { $setType(XML_PI_END); }
	;