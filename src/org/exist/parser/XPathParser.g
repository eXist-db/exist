/* eXist Open Source Native XML Database
 * Copyright (C) 2000-01,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
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
	
	import org.exist.xpath.*;
	import org.exist.*;
	import org.exist.util.*;
	import org.exist.storage.*;
	import org.exist.dom.*;
    import org.exist.storage.analysis.Tokenizer;
    import org.exist.security.User;
    import org.exist.security.PermissionDeniedException;
	import org.w3c.dom.*;
	import java.util.ArrayList;
	import java.util.Iterator;
	import java.util.StringTokenizer;
	import org.apache.log4j.BasicConfigurator;
	import java.io.StringReader;
}

class XPathParser extends Parser;
options {
	defaultErrorHandler=false;
	k=2;
}
{
	protected DocumentSet includeDocs = new DocumentSet();
	protected BrokerPool pool = null;
	protected ArrayList exceptions = new ArrayList(5);
	protected PathExpr topExpr;
	protected Environment env = null;
	protected boolean error = false;
    protected User user;
    
	protected static final String[][] internalFunctions = {
		{ "substring", "org.exist.xpath.FunSubstring" },
        	{ "not", "org.exist.xpath.FunNot" },
		{ "position", "org.exist.xpath.FunPosition" },
		{ "last", "org.exist.xpath.FunLast" },
		{ "count", "org.exist.xpath.FunCount" },
		{ "string-length", "org.exist.xpath.FunStrLength" },
		{ "boolean", "org.exist.xpath.FunBoolean" },
		{ "string", "org.exist.xpath.FunString" },
		{ "number", "org.exist.xpath.FunNumber" },
		{ "true", "org.exist.xpath.FunTrue" },
		{ "false", "org.exist.xpath.FunFalse" },
		{ "sum", "org.exist.xpath.FunSum" },
		{ "floor", "org.exist.xpath.FunFloor" },
		{ "ceiling", "org.exist.xpath.FunCeiling" },
		{ "round", "org.exist.xpath.FunRound" },
	        { "name", "org.exist.xpath.FunName" },
		{ "match-any", "org.exist.xpath.FunKeywordMatchAny" },
		{ "match-all", "org.exist.xpath.FunKeywordMatchAll" },
		{ "id", "org.exist.xpath.FunId" }
	};

	public XPathParser(BrokerPool pool, User user, TokenStream lexer) {
		this(pool, user, lexer, null);
	}
	
	public XPathParser(BrokerPool pool, User user, 
		TokenStream lexer, DocumentSet docs) {
		this(lexer);
        this.user = user;
        this.pool = pool;
		this.env = new Environment(internalFunctions);
		if(docs != null)
			this.includeDocs = docs;
        try {
            pool = BrokerPool.getInstance();
        } catch( EXistException e) {
            e.printStackTrace();
        }
	}

	public void setEnvironment(Environment environment) {
		env = environment;
	}
		
	public String getErrorMsg() {
		StringBuffer buf = new StringBuffer();
		for(Iterator i = exceptions.iterator(); i.hasNext(); ) {
			buf.append(((Exception)i.next()).toString());
			buf.append('\n');
		}
		return buf.toString();
	}

	public boolean foundErrors() {
		return error;
	}

	protected void handleException(Exception ex) {
		error = true;
		exceptions.add(ex);
	}
}

xpointer [PathExpr exprIn]
	throws PermissionDeniedException, EXistException:
	"xpointer" LPAREN xpointer_expr[exprIn] RPAREN EOF
	| id:ID EOF {
		exprIn.setDocumentSet(includeDocs);
		Function idf = new FunId(pool);
		idf.addArgument(new Literal(id.getText()));
		exprIn.addPath(idf);
	}
	;

xpointer_expr [PathExpr exprIn]
	throws PermissionDeniedException, EXistException
	{
		PathExpr expr = new PathExpr(pool);
	}:
	document_function[exprIn] or_expr[exprIn] {
		exprIn.setDocumentSet(includeDocs);
	}
	| or_expr[expr] {
		RootNode rootStep = new RootNode(pool);
        exprIn.add(rootStep);
        exprIn.addPath(expr);
        exprIn.setDocumentSet(includeDocs);
	}
	;
	exception
	catch[RecognitionException ex] {
		handleException(ex);
	}
    catch[PermissionDeniedException e] {
        handleException(e);
    }
    
expr [PathExpr exprIn] 
	throws PermissionDeniedException, EXistException:
	xpath_expr[exprIn] EOF
	;

xpath_expr [PathExpr exprIn] 
	throws PermissionDeniedException, EXistException
{
	PathExpr expr = new PathExpr(pool);
}:	
    document_function[exprIn] or_expr[exprIn] {
      exprIn.setDocumentSet(includeDocs);
    }
	| or_expr[exprIn]
	;
    exception
    catch[RecognitionException ex] {
		handleException(ex);
	}
    catch[PermissionDeniedException e] {
        handleException(e);
    }

or_expr [PathExpr exprIn] 
	throws PermissionDeniedException, EXistException
{
	PathExpr left = new PathExpr(pool);
	PathExpr right = new PathExpr(pool);
	OpOr op = null;
	boolean branch = false;
}:
	and_expr[left] 
		(
			"or" and_expr[right] {
				if(!branch) {
					op = new OpOr(pool);
					exprIn.addPath(op);
					op.addPath(left);
				}
				op.add(right);
				right = new PathExpr(pool);
				branch = true;
			}
		)*
	{			
		if(!branch) 
			exprIn.add(left); 
	}
	;

and_expr [PathExpr exprIn] 
	throws PermissionDeniedException, EXistException
{
	PathExpr left = new PathExpr(pool);
	PathExpr right = new PathExpr(pool);
	boolean branch = false;
	OpAnd op = null;
}:
	equality_expr[left] 
		(
			"and" equality_expr[right] {
				if(op == null) {
					op = new OpAnd(pool);
					exprIn.addPath(op);
					op.add(left);
				}
				op.add(right);
				right = new PathExpr(pool);
				branch = true;
			}
		)*
	{
		if(!branch) exprIn.add(left);
	}
	;

equality_expr[PathExpr exprIn] 
	throws PermissionDeniedException, EXistException
{
	PathExpr left = new PathExpr(pool);
	PathExpr right = new PathExpr(pool);
	int op=0;
	boolean branch = false;
}:
	union_expr[left] 
		(
			( op=equality_operator relational_expr[right] {
					OpEquals exprEq = new OpEquals(pool, left, right, op);
					exprIn.addPath(exprEq);
					branch = true;
				}
			)
			| ( op=fulltext_operator l:CONST {
			  FunContains exprCont = new FunContains(pool, op);
		   	  exprCont.setPath(left);
		   	  DBBroker broker = null;
		   	  try {
		   	  	  broker = pool.get();
	              Tokenizer tokenizer = 
	              	broker.getTextEngine().getTokenizer();
	              tokenizer.setText(l.getText());
	              org.exist.storage.analysis.TextToken token;
		          String word;
	              while (null != (token = tokenizer.nextToken( true ))) {
		          	word = token.getText();
	                exprCont.addTerm(word);
	              }
	          } finally {
	          	pool.release(broker);
	          }
			  exprIn.addPath(exprCont);
			  branch = true;
			}
			)
		)?
	{
		if(!branch)
			exprIn.add(left);
	}
	;

fulltext_operator
returns [int type] throws PermissionDeniedException {
  type = 0;
}:
	ANDEQ { type = Constants.FULLTEXT_AND; }
	| OREQ { type = Constants.FULLTEXT_OR; }
	;

equality_operator
returns [int type] 
	 throws PermissionDeniedException, EXistException {
  type = 0;
}:
	EQ { type = Constants.EQ; }
	| NEQ {type = Constants.NEQ; }
	;

union_expr [PathExpr expr] 
	throws PermissionDeniedException, EXistException
{
  PathExpr left = new PathExpr(pool), right = new PathExpr(pool);
  boolean branch = false;
}:
	relational_expr[left] ( { branch=true; } UNION union_expr[right] )? {
		if(branch) {
			Union result = new Union(pool, left, right);
			expr.addPath(result);
		} else
			expr.add(left);
	}
	;

relational_expr [PathExpr expr] 
	throws PermissionDeniedException, EXistException
{
	PathExpr left = new PathExpr(pool);
	PathExpr right = new PathExpr(pool);
	boolean branch = false;
	int rop = 0;
}:
	additive_expr[left] ( { branch = true; } rop=relational_operator additive_expr[right] )? {
		if(branch) {
			OpEquals exprEq = new OpEquals(pool, left, right, rop);
			expr.addPath(exprEq);
		} else
			expr.add(left);
	}
	;

relational_operator
returns [int type] 
	throws PermissionDeniedException, EXistException {
  type = 0;
}:
	LT { type = Constants.LT; }
	| GT { type = Constants.GT; }
	| LTEQ { type = Constants.LTEQ; }
	| GTEQ { type = Constants.GTEQ; }
	;

additive_expr [PathExpr expr] 
	throws PermissionDeniedException, EXistException
{
	PathExpr left = new PathExpr(pool);
	PathExpr right = new PathExpr(pool);
	boolean branch = false;
}:
	pathexpr[left] ( { branch = true; } PLUS pathexpr[right] )* {
		if(branch) {
			OpNumeric exprNum = new OpNumeric(pool, left, right, Constants.PLUS);
			expr.addPath(exprNum);
		} else
			expr.add(left);
	}
	;

document_function [PathExpr expr] 
	throws PermissionDeniedException, EXistException
{
	Expression step = null;
	boolean inclusive = true;
	DocumentSet temp;
}:
	"doctype" LPAREN arg3:CONST RPAREN {
        DBBroker broker = null;
        try {
            broker = pool.get();
            includeDocs = broker.getDocumentsByDoctype(user, arg3.getText());
            step = new RootNode(pool);
            expr.add(step);
            expr.setDocumentSet(includeDocs);
        } catch(EXistException e) {
            e.printStackTrace();
        } finally {
            pool.release( broker );
        }
	}
	// 1. document(*) function
	| ( "document" LPAREN STAR ) => "document" LPAREN STAR RPAREN {
        DBBroker broker = null;
        try {
            broker = pool.get();
            includeDocs = broker.getAllDocuments(user);
            step = new RootNode(pool);
            expr.setDocumentSet(includeDocs);
            expr.add(step);
        } catch(EXistException e) {
            e.printStackTrace();
        } finally {
            pool.release( broker );
        }
	}
	// 2. document('filename', 'filename', ...) function
	| "document" LPAREN arg1:CONST {
        DBBroker broker = null;
        try {
            broker = pool.get();
            step = new RootNode(pool);
            expr.add(step);
            DocumentImpl doc = (DocumentImpl)broker.getDocument(user, arg1.getText());
            if(doc != null) {
              expr.addDocument(doc);
              includeDocs.add(doc);
            }
        } catch(EXistException e) {
            e.printStackTrace();
        } finally {
            pool.release( broker );
        }
	}
	( COMMA arg2:CONST {
        DBBroker broker = null;
        try {
            broker = pool.get();
            DocumentImpl doc = (DocumentImpl)broker.getDocument(arg2.getText());
            if(doc != null) {
              expr.addDocument(doc);
              includeDocs.add(doc);
            }
        } catch(EXistException e) {
            e.printStackTrace();
        } finally {
            pool.release( broker );
        }
	}
	)* RPAREN
	| "collection" LPAREN arg6:CONST {
		DBBroker broker = null;
      	try {
      		broker = pool.get();
        	temp = broker.getDocumentsByCollection(user, arg6.getText(), true);
        	includeDocs = temp;
        } catch(EXistException e) {
        } finally {
        	pool.release(broker);
        }
      }
      (COMMA arg7:CONST {
      	DBBroker broker = null;
      	try {
      		broker = pool.get();
        	temp = broker.getDocumentsByCollection(user, arg7.getText(), true);
        	includeDocs.addAll(temp);
        } catch(EXistException e) {
        } finally {
        	pool.release(broker);
        }
      })*
      RPAREN {
        	step = new RootNode(pool);
        	expr.setDocumentSet(includeDocs);
        	expr.add(step);
    }
    | "xcollection" LPAREN arg8:CONST {
		DBBroker broker = null;
      	try {
      		broker = pool.get();
        	temp = broker.getDocumentsByCollection(user, arg8.getText(), false);
        	includeDocs.addAll(temp);
        } catch(EXistException e) {
        } finally {
        	pool.release(broker);
        }
      }
      (COMMA arg9:CONST {
      	DBBroker broker = null;
      	try {
      		broker = pool.get();
        	temp = broker.getDocumentsByCollection(user, arg9.getText(), false);
        	includeDocs.addAll(temp);
        } catch(EXistException e) {
        } finally {
        	pool.release(broker);
        }
      })*
      RPAREN {
        	step = new RootNode(pool);
        	expr.setDocumentSet(includeDocs);
        	expr.add(step);
      }
	;

pathexpr [PathExpr expr] 
	throws PermissionDeniedException, EXistException
{ 
	Expression result = null;
	PathExpr path = null;
}:
	( result=regularexpr[expr] {
		if(result instanceof Step && ((Step)result).getAxis() == -1)
			((Step)result).setAxis(Constants.CHILD_AXIS);
	  }
	  )+
	| LPAREN { path = new PathExpr(pool); } or_expr[path] RPAREN {
		expr.addPath(path);
	}
	;

primary_expr [PathExpr expr]
returns [Expression step] 
	throws PermissionDeniedException, EXistException
{
	step = null;
	PathExpr path = null;
}:
	l:CONST {
		step = new Literal(l.getText());
		expr.add(step);
	}
	| i:INT {
		step = new IntNumber(Double.parseDouble(i.getText()));
		expr.add(step);
	}
	;

function_call [PathExpr expr]
returns [Expression step] 
	throws PermissionDeniedException, EXistException
{
	step = null;
	PathExpr path = new PathExpr(pool);
	PathExpr arg1 = new PathExpr(pool);
	PathExpr arg2 = null;
	Function fun = null;
    int distance = 1;
}:
    // special functions
    "text" LPAREN RPAREN {
			step = new LocationStep(pool, -1, new TypeTest(Constants.TEXT_NODE));
			expr.add(step);
	}
	| "starts-with" LPAREN or_expr[path] COMMA l:CONST RPAREN {
		if(path.returnsType() == Constants.TYPE_NODELIST) {
                   String val = l.getText() + "%";
		   step = new OpEquals(pool, path, new Literal(val), Constants.EQ);
                   expr.add(step);
		} else {
		   step = new FunStartsWith(pool, path, new Literal(l.getText()));
		   expr.add(step);
	    }
    }
	| "ends-with" LPAREN or_expr[path] COMMA l2:CONST RPAREN {
		if(path.returnsType() == Constants.TYPE_NODELIST) {
                   String val = l2.getText();
		   step = new OpEquals(pool, path, new Literal(l2.getText()), 
                                       Constants.EQ);
                   expr.add(step);
		} else {
		   step = new FunEndsWith(pool, path, new Literal(l2.getText()));
		   expr.add(step);
	    }
	}
    | "contains" LPAREN or_expr[path] COMMA arg:CONST RPAREN {
            String term = "%" + arg.getText() + "%";
			step =
			  new OpEquals(pool, path, new Literal(term), Constants.EQ);
            System.out.println(step.pprint());
			expr.add(step);
	}
	| "match" LPAREN or_expr[path] COMMA l3:CONST RPAREN {
             if(path.returnsType() == Constants.TYPE_NODELIST) {
		step = new OpEquals(pool, path, new Literal(l3.getText()), 
                                    Constants.REGEXP);
                expr.add(step);
	     }
	}
	| "near" LPAREN or_expr[path] COMMA l4:CONST 
          ( COMMA i:INT { distance = Integer.parseInt(i.getText()); } )? RPAREN {
            FunNear near = new FunNear(pool);
            near.setDistance(distance);
            near.setPath(path);
	    DBBroker broker = null;
	    try {
	      broker = pool.get();
              Tokenizer tok = broker.getTextEngine().getTokenizer();
            tok.setText(l4.getText());
            org.exist.storage.analysis.TextToken token;
             String next;
             while((token = tok.nextToken(true)) != null) {
                next = token.getText();
                near.addTerm(next);
             }
	     } finally {
	       pool.release(broker);
	     }
            expr.addPath(near);
	}
        // generic function without arguments
	| ( NCNAME LPAREN RPAREN ) =>
	  f1:NCNAME { env.hasFunction(f1.getText()) }? LPAREN RPAREN {
		fun = Function.createFunction(pool, env.getFunction(f1.getText()));
		expr.addPath(fun);
	}

	// generic function with arguments
	| (NCNAME LPAREN) => f2:NCNAME LPAREN
    {
		fun = Function.createFunction(pool, env.getFunction(f2.getText()));
		expr.addPath(fun);
	}
	or_expr[arg1] { fun.addArgument(arg1); } 
    ( COMMA { arg2 = new PathExpr(pool); } 
	or_expr[arg2] { fun.addArgument(arg2); } )*
	RPAREN
	;

empty_arglist: /* empty */;

function_args [Function fun] 
	throws PermissionDeniedException, EXistException
{
	PathExpr arg1 = new PathExpr(pool);
	PathExpr arg2 = null;
}:
	or_expr[arg1] { fun.addArgument(arg1); }
	( COMMA { arg2 = new PathExpr(pool); } 
	or_expr[arg2]
	{ fun.addArgument(arg2); }
	)*
	;

regularexpr [PathExpr expr]
returns [Expression result] 
	throws PermissionDeniedException, EXistException
{   result = null; 
	Predicate pred = null;
	int axis = Constants.CHILD_AXIS;
}:
	axis=axis_spec result=step[expr] {
		if(result instanceof Step && ((Step)result).getAxis() == -1)
            ((Step)result).setAxis(axis);
    }
    ( 
	  	pred=predicate[expr] {
		expr.addPredicate(pred);
	}
	)*
 	| result=step[expr] {
		if(result instanceof Step && ((Step)result).getAxis() == -1)
            ((Step)result).setAxis(Constants.CHILD_AXIS);
      }
	  ( 
	    pred=predicate[expr] {
		  expr.addPredicate(pred);
	    }
	  )*
    | SLASH result=regularexpr[expr] {
		if(result instanceof Step && ((Step)result).getAxis() == -1)
			((Step)result).setAxis(Constants.CHILD_AXIS);
    }
	| DSLASH result=regularexpr[expr] {
		if(result instanceof Step)
			((Step)result).setAxis(Constants.DESCENDANT_AXIS);
    }
	;

step [PathExpr expr]
returns [Expression step] 
	throws PermissionDeniedException, EXistException
{ step = null; 
  String qn;
  String attr;
}:
	AT attr=qname {
			step = new LocationStep(pool,
				Constants.ATTRIBUTE_AXIS,
				new NameTest(attr));
			expr.add(step);
	}
	| any:STAR {
			step = new LocationStep(pool,
				-1,
				new TypeTest(Constants.ELEMENT_NODE));
			expr.add(step);
	}
	| anyAttr:ATTRIB_STAR {
		step = new LocationStep(pool, Constants.ATTRIBUTE_AXIS, new TypeTest(Constants.ATTRIBUTE_NODE));
		expr.add(step);
	}
	| "node" LPAREN RPAREN {
			step = new LocationStep(pool, -1, new TypeTest(Constants.NODE_TYPE));
			expr.add(step);
	}
	| PARENT {
			step = new LocationStep(pool, 
				Constants.PARENT_AXIS,
				new TypeTest(Constants.NODE_TYPE));
			expr.add(step);
	}
	| SELF {
			step = new LocationStep(pool,
				Constants.SELF_AXIS,
				new TypeTest(Constants.NODE_TYPE));
			expr.add(step);
	}
	| step=function_call[expr]
	| qn=qname {
			step = new LocationStep( pool, -1, new NameTest(qn));
			expr.add(step);
	}
	| step=primary_expr[expr]
	;
	
qname
returns [String name]
{
	name = null;
}:
	n1:NCNAME { name = n1.getText(); }
	(COLON n2:NCNAME { name = name + ':' + n2.getText(); } )?
	| "text" { name = "text"; }
	| "contains" { name = "contains"; }
	| "starts-with" { name = "starts-with"; }
	| "ends-with" { name = "ends-with"; }
	| "near" { name = "near"; }
	;

axis_spec
returns [int axis]
{
	axis = -1;
}:
	"ancestor" COLON COLON {
		axis = Constants.ANCESTOR_AXIS;
	}
	;
	
predicate [PathExpr expr]
returns [Predicate pred] 
	throws PermissionDeniedException, EXistException
{
    pred = new Predicate(pool);
}:
		LPPAREN or_expr[pred] RPPAREN
	;


class XPathLexer extends Lexer;
options {
	k = 2;
	testLiterals = false;
    charVocabulary = '\u0003'..'\uFFFF';
    codeGenBitsetTestThreshold=20;
}

WS	:	(' '
	|	'\t'
	|	'\n'
	|	'\r')
		{ $setType(Token.SKIP); }
	;

CONST 
:       '"'! ( ~('"') )* '"'!
        |     '\''! ( ~('\'') )* '\''!
        ;

SLASH   :       '/'
        ;

DSLASH  :       '/' '/'
        ;

COLON   :   ':'
		;
		
STAR	:	'*'
		;

COMMA   :       ','
        ;

LPPAREN  :      '['
        ;

RPPAREN  :      ']'
        ;

LPAREN  :       '('
        ;

RPAREN  :       ')'
        ;

SELF    :       '.'
        ;

PARENT  :       ".."
        ;

UNION	:		'|'
		;

PLUS	:		'+'
		;

AT : '@'
	;
	
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

INT	:	(DIGIT)+ ('.' (DIGIT)+)*
	;

VARIABLE:       '$'! NCNAME
        ;

ATTRIB_STAR:	'@'! STAR
		;

ANDEQ	:		"&="
		;

OREQ	:		"|="
		;

EQ      :       '='
        ;

NEQ		:		"!="
		;

LT      :       '<'
        ;

GT      :       '>'
        ;

LTEQ	:		"<="
		;

GTEQ	:		">="
		;
