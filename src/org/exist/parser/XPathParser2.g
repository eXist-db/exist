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
 * $Id:
 */
header {
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
}

/* -----------------------------------------------------------------------------------------------------
 * The XPath parser: generates an AST which is then passed to the tree parser for analysis
 * and code generation.
 * ----------------------------------------------------------------------------------------------------- */
class XPathParser2 extends Parser;
options {
	defaultErrorHandler=false;
	k=1;
	buildAST=true;
}
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
}

imaginaryTokenDefinitions :
	QNAME
	PREDICATE
	PARENTHESIZED
	ABSOLUTE_SLASH
	ABSOLUTE_DSLASH
	WILDCARD
	PREFIX_WILDCARD
	FUNCTION
	UNARY_MINUS
	UNARY_PLUS
	XPOINTER
	XPOINTER_ID
	;

xpointer :
	"xpointer"^ LPAREN! ex:expr RPAREN!
		{
			#xpointer = #(#[XPOINTER, "xpointer"], #ex);
		}
	| nc:NCNAME 
		{
			#xpointer = #(#[XPOINTER_ID, "id"], #nc);
		}
	;
		
xpath :
	( expr )?
	;
	exception
	catch[RecognitionException e] {
		handleException(e);
	}
	
expr :
	orExpr
	;
	
orExpr :
	andExpr ( "or"^ andExpr )*
	;
	
andExpr :
	comparisonExpr ( "and"^ comparisonExpr )*
	;
	
comparisonExpr :
	additiveExpr ( 
		( ( EQ^ | NEQ^ | GT^ | GTEQ^ | LT^ | LTEQ^ ) additiveExpr )
		| ( ( ANDEQ^ | OREQ^ ) STRING_LITERAL )
	)?
	;

additiveExpr :
	multiplicativeExpr ( ( PLUS^ | MINUS^ ) multiplicativeExpr )*
	;
	
multiplicativeExpr :
	unaryExpr ( ( STAR^ | "div"^ | "mod"^ ) unaryExpr )*
	;

unaryExpr :
	// TODO: XPath 2.0 allows an arbitrary number of +/-, 
	// we restrict it to one
	MINUS expr:unionExpr
		{
			#unaryExpr = #(#[UNARY_MINUS, "-"], #expr);
		}
	| PLUS expr2:unionExpr
		{
			#unaryExpr = #(#[UNARY_PLUS, "+"], #expr2);
		}
	| unionExpr
	;
	
unionExpr :
	pathExpr ( UNION^ pathExpr )?
	;
	
pathExpr :
	relativePathExpr
	| (SLASH relativePathExpr) => SLASH relPath:relativePathExpr 
		{
			#pathExpr = #(#[ABSOLUTE_SLASH, "AbsoluteSlash"], #relPath);
		}
	// lone slash
	| SLASH
		{
			#pathExpr = #[ABSOLUTE_SLASH, "AbsoluteSlash"];
		}
	| DSLASH relPath2:relativePathExpr
		{
			#pathExpr = #(#[ABSOLUTE_DSLASH, "AbsoluteSlashSlash"], #relPath2);
		}
	;
	
relativePathExpr :
	stepExpr (
		( SLASH^ | DSLASH^ ) stepExpr
	)*
	;
	
stepExpr :
	( ( "text" | "node" ) LPAREN ) => axisStep
	| ( ( qName LPAREN ) | SELF | LPAREN | literal ) => filterStep
	| axisStep
	;
	
axisStep :
	( forwardOrReverseStep ) predicates
	;

predicates :
	( predicate )*
	;
	
predicate :
	LPPAREN! predExpr:expr RPPAREN!
		{
			#predicate = #(#[PREDICATE, "Pred"], #predExpr);
		}
	;
	
forwardOrReverseStep :
	( forwardAxisSpecifier COLON ) => forwardAxis nodeTest
	| ( reverseAxisSpecifier COLON ) => reverseAxis nodeTest
	| abbrevStep
	;

abbrevStep :
	( AT )? nodeTest
	| PARENT
	;
	
forwardAxis :
	forwardAxisSpecifier COLON! COLON!
	;

forwardAxisSpecifier :
	"child"
	| "self"
	| "attribute"
	| "descendant"
	| "descendant-or-self"
	| "following-sibling"
	;
	
reverseAxis :
	reverseAxisSpecifier COLON! COLON!
	;

reverseAxisSpecifier :
	"parent"
	| "ancestor"
	| "ancestor-or-self"
	| "preceding-sibling"
	;
	
nodeTest :
	( ( "text" | "node" ) LPAREN ) => kindTest
	| nameTest
	;
	
nameTest
{
	String name = null;
}:
	( ( NCNAME COLON STAR ) | STAR ) => wildcard
	| name=qName 
		{
			#nameTest = #[QNAME, name];
		}
	;

wildcard :
	// *:localname
	( STAR COLON ) => STAR! COLON! nc1:NCNAME
		{
			#wildcard = #(#[PREFIX_WILDCARD, "*"], #nc1);
		}
	// prefix:*
	| nc2:NCNAME COLON! STAR!
		{
			#wildcard = #(#nc2, #[WILDCARD, "*"]);
		}
	// *
	| STAR
		{
			// make this distinct from multiplication
			#wildcard = #[WILDCARD, "*"];
		}
	;
	
filterStep :
	primaryExpr predicates
	;
	
primaryExpr :
	literal
	| functionCall
	| contextItemExpr
	| parenthesizedExpr
	;

literal :
	STRING_LITERAL^
	| numericLiteral
	;

numericLiteral :
	DOUBLE_LITERAL^
	| DECIMAL_LITERAL^
	| INTEGER_LITERAL^
	;
	
parenthesizedExpr :
	LPAREN! e:expr RPAREN!
		{
			#parenthesizedExpr = #(#[PARENTHESIZED, "Parenthesized"], #e);
		}
	;

functionCall
{
	String fnName = null;
}:
	fnName=qName LPAREN!
		{
			#functionCall = #[FUNCTION, fnName];
		}
	( params:functionParameters 
		{
			#functionCall = #(#[FUNCTION, fnName], #params);
		}
	)? RPAREN!
	;

functionParameters :
	expr ( COMMA! expr )*
	;
	
contextItemExpr : SELF^ ;
	
kindTest :
	textTest
	| anyKindTest
	;
	
textTest :
	"text"^ LPAREN! RPAREN!
	;

anyKindTest :
	"node"^ LPAREN! RPAREN!
	;

qName returns [String name]
{
	name = null;
	String name2;
}:
	name=ncnameOrKeyword ( COLON name2=ncnameOrKeyword
		{
			name = name + ':' + name2;
		}
	)?
	;

/* All of the literals used in this grammar can also be
 * part of a valid QName. We thus have to test for each
 * of them below.
 */
ncnameOrKeyword returns [String name]
{
	name = null;
}:
	n1:NCNAME { name = n1.getText(); }
	| name=reservedKeywords
	;
	
reservedKeywords returns [String name]
{
	name = null;
}:
	"div" { name = "div"; }
	| "mod" { name = "mod"; }
	| "text" { name = "text"; }
	| "node" { name = "node"; }
	| "or" { name = "or"; }
	| "and" { name = "and"; }
	| "child" { name = "child"; }
	| "parent" { name = "parent"; }
	| "self" { name = "self"; }
	| "attribute" { name = "attribute"; }
	| "ancestor" { name = "ancestor"; }
	| "descendant" { name = "descendant"; }
	| "descendant-or-self" { name = "descendant-or-self"; }
	| "ancestor-or-self" { name = "ancestor-or-self"; }
	| "preceding-sibling" { name = "preceding-sibling"; }
	| "following-sibling" { name = "following-sibling"; }
	;



/* -----------------------------------------------------------------------------------------------------
 * The tree parser: walks the AST created by the parser to generate
 * XPath objects.
 * ----------------------------------------------------------------------------------------------------- */
 
class XPathTreeParser2 extends TreeParser;
options {
	k=1;
	defaultErrorHandler = false;
}
{
	private BrokerPool pool;
	private StaticContext context;
	protected ArrayList exceptions = new ArrayList(2);
	protected boolean foundError = false;
	
	public XPathTreeParser2(BrokerPool pool, StaticContext context) {
		this();
		this.pool = pool;
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
}

xpointer [PathExpr path ] :
	#(XPOINTER expr[path])
	| #(XPOINTER_ID nc:NCNAME) 
		{
			Function fun = new FunId(pool);
			fun.addArgument(new Literal(nc.getText()));
			path.addPath(fun);
		}
	;
	exception
	catch[RecognitionException e] {
		handleException(e);
	}
	catch[EXistException e] {
		handleException(e);
	}
	catch[PermissionDeniedException e] {
		handleException(e);
	}
	
xpath [PathExpr path] :
	expr[path]
	;
	exception
	catch[RecognitionException e] {
		handleException(e);
	}
	catch[EXistException e] {
		handleException(e);
	}
	catch[PermissionDeniedException e] {
		handleException(e);
	}
	
expr [PathExpr path]
throws PermissionDeniedException, EXistException
{
	Expression step = null;
}:
	#( "or" 
		{
			PathExpr left = new PathExpr(pool);
			PathExpr right = new PathExpr(pool);
		}
		expr[left] expr[right]
	)
		{
			OpOr or = new OpOr(pool);
			or.add(left);
			or.add(right);
			path.addPath(or);
		}
		
	| #( "and"
		{
			PathExpr left = new PathExpr(pool);
			PathExpr right = new PathExpr(pool);
		}
		expr[left] expr[right]
	)
		{
			OpAnd and = new OpAnd(pool);
			and.add(left);
			and.add(right);
			path.addPath(and);
		}
		
	| #( PARENTHESIZED
		{
			PathExpr expr = new PathExpr(pool);
			path.addPath(expr);
		}
		expr[expr]
	)
	
	| #(UNION 
			{
				PathExpr left = new PathExpr(pool);
				PathExpr right = new PathExpr(pool);
			}
		expr[left] expr[right])
			{
				Union union = new Union(pool, left, right);
				path.addPath(union);
			}
			
	| #(ABSOLUTE_SLASH 
			{
				RootNode root = new RootNode(pool);
				path.add(root);
			}
		( expr[path] )? 
	)
	
	| #(ABSOLUTE_DSLASH 
			{
				RootNode root = new RootNode(pool);
				path.add(root);
			}
		( step=pathExpr[path]
			{
				if(step instanceof LocationStep) {
					LocationStep s = (LocationStep)step;
					if(s.getAxis() == Constants.ATTRIBUTE_AXIS)
						// combines descendant-or-self::node()/attribute:*
						s.setAxis(Constants.DESCENDANT_ATTRIBUTE_AXIS);
					else
						s.setAxis(Constants.DESCENDANT_AXIS);
				}
			}
		)? 
	)
	
	| step=generalComp[path]
	
	| step=fulltextComp[path]
	
	| step=pathExpr [path]
	
	| step=numericExpr [path]
	;

pathExpr [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException
{
	Expression rightStep = null;
	step = null;
	int axis = Constants.CHILD_AXIS;
}:
	c:STRING_LITERAL
		{
			step = new Literal(c.getText());
			path.add(step);
		}
	
	| i:INTEGER_LITERAL
		{
			step = new IntNumber(Integer.parseInt(i.getText()));
			path.add(step);
		}
	
	| ( dec:DECIMAL_LITERAL 
			{
				step = new IntNumber(Double.parseDouble(dec.getText()));
			}
		| dbl:DOUBLE_LITERAL 
			{
				step = new IntNumber(Double.parseDouble(dbl.getText()));
			}
	)
		{
			path.add(step);
		}
	
	| step=functionCall[path]	
	
	| ( axis=forwardAxis )? 
			{
				NodeTest test;
			}
		( qn:QNAME 
			{
				QName qname = QName.parse(context, qn.getText());
				test = new NameTest(Constants.ELEMENT_NODE, qname);
			}
		| #( PREFIX_WILDCARD nc1:NCNAME )
			{
				QName qname = new QName(nc1.getText(), null, null);
				test = new NameTest(Constants.ELEMENT_NODE, qname);
			}
		| #( nc:NCNAME WILDCARD )
			{
				String namespaceURI = context.getURIForPrefix(nc.getText());
				QName qname = new QName(null, namespaceURI, null);
				test = new NameTest(Constants.ELEMENT_NODE, qname);
			}
		| WILDCARD
			{
				test = new TypeTest(Constants.ELEMENT_NODE);
			}
		| "node"
			{
				test = new AnyNodeTest();
			}
		| "text"
			{
				test = new TypeTest(Constants.TEXT_NODE);
			}
		)
			{
				step = new LocationStep(pool, axis, test);
				path.add(step);
			}
		( predicate[(LocationStep)step] )*
		
	| AT 
		{
			QName qname;
		}
		( attr:QNAME
			{
				qname = QName.parse(context, attr.getText());
			}
		| #( PREFIX_WILDCARD nc2:NCNAME )
			{
				qname = new QName(nc2.getText(), null, null);
			}
		| #( nc3:NCNAME WILDCARD )
			{
				String namespaceURI = context.getURIForPrefix(nc3.getText());
				if(namespaceURI == null)
					throw new EXistException("No namespace defined for prefix " + nc.getText());
				qname = new QName(null, namespaceURI, null);
			}
		)
		{	
			step = 
				new LocationStep(pool, Constants.ATTRIBUTE_AXIS, 
					new NameTest(Constants.ATTRIBUTE_NODE, qname));
			path.add(step);
		}
		( predicate[(LocationStep)step] )*
		
	| SELF
		{
			step = 
				new LocationStep(pool, Constants.SELF_AXIS, new TypeTest(Constants.NODE_TYPE));
			path.add(step);
		}
		( predicate[(LocationStep)step] )*
		
	| PARENT
		{
			step =
				new LocationStep(pool, Constants.PARENT_AXIS, new TypeTest(Constants.NODE_TYPE));
			path.add(step);
		}
		( predicate[(LocationStep)step] )*
			
	| #(SLASH step=pathExpr[path] 
		( rightStep=pathExpr[path] 
			{
				if(rightStep instanceof LocationStep && ((LocationStep)rightStep).getAxis() == -1)
					((LocationStep)rightStep).setAxis(Constants.CHILD_AXIS);
			}
		)?
	)
		{
			if(rightStep instanceof LocationStep && ((LocationStep)rightStep).getAxis() == -1)
				((LocationStep)step).setAxis(Constants.CHILD_AXIS);
		}
		
	| #(DSLASH step=pathExpr[path] 
		( rightStep=pathExpr[path]
			{
				if(rightStep instanceof LocationStep) {
					LocationStep rs = (LocationStep)rightStep;
					if(rs.getAxis() == Constants.ATTRIBUTE_AXIS)
						rs.setAxis(Constants.DESCENDANT_ATTRIBUTE_AXIS);
					else
						rs.setAxis(Constants.DESCENDANT_SELF_AXIS);
				}
			}
		)?
	)
		{
			if(step instanceof LocationStep)
				((LocationStep)step).setAxis(Constants.DESCENDANT_SELF_AXIS);
		}
	;

numericExpr [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException
{
	step = null;
	PathExpr left = new PathExpr(pool);
	PathExpr right = new PathExpr(pool);
}:
	#( PLUS expr[left] expr[right] )
		{
			OpNumeric op = new OpNumeric(pool, left, right, Constants.PLUS);
			path.addPath(op);
			step = op;
		}
	| #( MINUS expr[left] expr[right] )
		{
			OpNumeric op = new OpNumeric(pool, left, right, Constants.MINUS);
			path.addPath(op);
			step = op;
		}
	| #( UNARY_MINUS expr[left] )
		{
			UnaryExpr unary = new UnaryExpr(pool, Constants.MINUS);
			unary.add(left);
			path.addPath(unary);
			step = unary;
		}
	| #( UNARY_PLUS expr[left] )
		{
			UnaryExpr unary = new UnaryExpr(pool, Constants.PLUS);
			unary.add(left);
			path.addPath(unary);
			step = unary;
		}
	| #( "div" expr[left] expr[right] )
		{
			OpNumeric op = new OpNumeric(pool, left, right, Constants.DIV);
			path.addPath(op);
			step = op;
		}
	| #( "mod" expr[left] expr[right] )
		{
			OpNumeric op = new OpNumeric(pool, left, right, Constants.MOD);
			path.addPath(op);
			step = op;
		}
	| #( STAR expr[left] expr[right] )
		{
			OpNumeric op = new OpNumeric(pool, left, right, Constants.MULT);
			path.addPath(op);
			step = op;
		}
	;
	
predicate [LocationStep step]
throws PermissionDeniedException, EXistException:
	#( 
			PREDICATE
				{ Predicate predicateExpr = new Predicate(pool); }
			expr[predicateExpr]
				{ 
					step.addPredicate(predicateExpr); 
				}
	)
	;

functionCall [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException
{
	PathExpr pathExpr;
	step = null;
}:
	#(fn:FUNCTION 
			{
				Vector params = new Vector();
			}
		(
			{ 
				pathExpr = new PathExpr(pool); 
			}
			expr[pathExpr]
			{
				params.addElement(pathExpr);
			}
		)*
	)
		{
			step = Util.createFunction(pool, context, path, fn.getText(), params);
		}
	;
	
forwardAxis returns [int axis]
throws PermissionDeniedException, EXistException
{
	axis = -1;
}:
	"child" { axis = Constants.CHILD_AXIS; }
	| "attribute" { axis = Constants.ATTRIBUTE_AXIS; }
	| "self" { axis = Constants.SELF_AXIS; }
	| "parent" { axis = Constants.PARENT_AXIS; }
	| "descendant" { axis = Constants.DESCENDANT_AXIS; }
	| "descendant-or-self" { axis = Constants.DESCENDANT_SELF_AXIS; }
	| "following-sibling" { axis = Constants.FOLLOWING_SIBLING_AXIS; }
	| "preceding-sibling" { axis = Constants.PRECEDING_SIBLING_AXIS; }
	| "ancestor" { axis = Constants.ANCESTOR_AXIS; }
	| "ancestor-or-self" { axis = Constants.ANCESTOR_SELF_AXIS; }
	;

fulltextComp [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException
{
	step = null;
	PathExpr left = new PathExpr(pool);
}:
	#( ANDEQ expr[left] c:STRING_LITERAL )
		{
			ExtFulltext exprCont = new ExtFulltext(pool, Constants.FULLTEXT_AND);
	   	  	exprCont.setPath(left);
	   	  	exprCont.addTerms(c.getText());
			path.addPath(exprCont);
		}
	| #( OREQ expr[left] c2:STRING_LITERAL )
		{
			ExtFulltext exprCont = new ExtFulltext(pool, Constants.FULLTEXT_OR);
	   	  	exprCont.setPath(left);
			exprCont.addTerms(c2.getText());
			path.addPath(exprCont);
		}
	;
	
generalComp [PathExpr path]
returns [Expression step]
throws PermissionDeniedException, EXistException
{
	step = null;
	PathExpr left = new PathExpr(pool);
	PathExpr right = new PathExpr(pool);
}:
	#( EQ expr[left] expr[right]
		{
			step = new OpEquals(pool, left, right, Constants.EQ);
			path.add(step);
		}
	)
	| #( NEQ expr[left] expr[right]
		{
			step = new OpEquals(pool, left, right, Constants.NEQ);
			path.add(step);
		}
	)
	| #( LT expr[left] expr[right]
		{
			step = new OpEquals(pool, left, right, Constants.LT);
			path.add(step);
		}
	)
	| #( LTEQ expr[left] expr[right]
		{
			step = new OpEquals(pool, left, right, Constants.LTEQ);
			path.add(step);
		}
	)
	| #( GT expr[left] expr[right]
		{
			step = new OpEquals(pool, left, right, Constants.GT);
			path.add(step);
		}
	)
	| #( GTEQ expr[left] expr[right]
		{
			step = new OpEquals(pool, left, right, Constants.GTEQ);
			path.add(step);
		}
	)
	;
	
/* -----------------------------------------------------------------------------------------------------
 * The XPath lexer.
 * ----------------------------------------------------------------------------------------------------- */
 
class XPathLexer2 extends Lexer;
options {
	k = 3;
	testLiterals = false;
    charVocabulary = '\u0003'..'\uFFFF';
    codeGenBitsetTestThreshold=20;
}

protected
BASECHAR 
options { testLiterals=true; }
	:	('\u0041'..'\u005a' | '\u0061'..'\u007a' |
		'\u00c0'..'\u00d6' | '\u00d8'..'\u00f6' |
		'\u00f8'..'\u00ff')
	;

protected
IDEOGRAPHIC	: ('\u4e00'..'\u9fa5' | '\u3007' | '\u3021'..'\u3029')
	;

protected
DIGIT	:	('\u0030'..'\u0039')
		;

protected
DIGITS : ( DIGIT )+
	;
		
protected
NMSTART
	:	(BASECHAR | '_')
	;
	
protected
NMCHAR
	:	(BASECHAR | DIGIT | '.' | '-' | '_' )
	;

NCNAME
options { testLiterals=true; }
	:	NMSTART (NMCHAR)*
	;
		
WS :	(' ' | '\t' | '\n' | '\r')
		{ $setType(Token.SKIP); }
	;

protected
INTEGER_LITERAL :
	DIGITS
	;
	
protected
DECIMAL_LITERAL : 
	( '.' DIGITS )
	| ( DIGITS '.' ) => DIGITS '.' (DIGITS)?
	;

protected
DOUBLE_LITERAL :
	( ( '.' DIGITS ) | ( DIGITS ( '.' ( DIGIT )* )? ) ) ( 'e' | 'E' ) ( '+' | '-' )? DIGITS
	;

STRING_LITERAL : '"'! ( ~('"') )* '"'!
	| '\''! ( ~('\'') )* '\''!
    ;
    
INTEGER_DECIMAL_PARENT :
	( '.' '.' ) => PARENT { $setType(PARENT); }
	| ( '.' INTEGER_LITERAL ) => DECIMAL_LITERAL { $setType(DECIMAL_LITERAL); }
	| ( '.' ) => SELF { $setType(SELF); }
	| ( DECIMAL_LITERAL ( 'e' | 'E' ) ) => DOUBLE_LITERAL
		{
			$setType(DOUBLE_LITERAL);
		}
	| ( INTEGER_LITERAL '.' ) => DECIMAL_LITERAL 
		{ 
			$setType(DECIMAL_LITERAL); 
			System.out.println("found decimal");
		}
	| INTEGER_LITERAL { $setType(INTEGER_LITERAL); }
	;
	
SLASH : '/'
        ;

DSLASH : '/' '/'
        ;
	
COLON : ':'
	;

COMMA : ','
	;

STAR : '*'
	;
	
PLUS : '+'
	;

MINUS : '-'
	;
		
LPPAREN : '['
    ;

RPPAREN : ']'
	;

LPAREN : '('
    ;

RPAREN : ')'
	;

protected
SELF : '.'
	;

protected
PARENT : ".."
    ;

UNION : '|'
	;

AT : '@'
	;

VARIABLE: '$'! NCNAME
	;

ANDEQ : "&="
	;

OREQ : "|="
	;

EQ : '='
    ;

NEQ : "!="
	;

LT : '<'
	;

GT : '>'
	;

LTEQ	: "<="
	;

GTEQ : ">="
	;
	
