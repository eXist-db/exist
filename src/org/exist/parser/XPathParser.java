// $ANTLR : "XPathParser.g" -> "XPathParser.java"$

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

public class XPathParser extends antlr.LLkParser
       implements XPathParserTokenTypes
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

protected XPathParser(TokenBuffer tokenBuf, int k) {
  super(tokenBuf,k);
  tokenNames = _tokenNames;
}

public XPathParser(TokenBuffer tokenBuf) {
  this(tokenBuf,2);
}

protected XPathParser(TokenStream lexer, int k) {
  super(lexer,k);
  tokenNames = _tokenNames;
}

public XPathParser(TokenStream lexer) {
  this(lexer,2);
}

public XPathParser(ParserSharedInputState state) {
  super(state,2);
  tokenNames = _tokenNames;
}

	public final void xpointer(
		PathExpr exprIn
	) throws RecognitionException, TokenStreamException, PermissionDeniedException,EXistException {
		
		Token  id = null;
		
		switch ( LA(1)) {
		case LITERAL_xpointer:
		{
			match(LITERAL_xpointer);
			match(LPAREN);
			xpointer_expr(exprIn);
			match(RPAREN);
			match(Token.EOF_TYPE);
			break;
		}
		case ID:
		{
			id = LT(1);
			match(ID);
			match(Token.EOF_TYPE);
			if ( inputState.guessing==0 ) {
				
						exprIn.setDocumentSet(includeDocs);
						Function idf = new FunId(pool);
						idf.addArgument(new Literal(id.getText()));
						exprIn.addPath(idf);
					
			}
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
	}
	
	public final void xpointer_expr(
		PathExpr exprIn
	) throws RecognitionException, TokenStreamException, PermissionDeniedException,EXistException {
		
		
				PathExpr expr = new PathExpr(pool);
			
		
		try {      // for error handling
			switch ( LA(1)) {
			case LITERAL_doctype:
			case LITERAL_document:
			case LITERAL_collection:
			{
				document_function(exprIn);
				or_expr(exprIn);
				if ( inputState.guessing==0 ) {
					
							exprIn.setDocumentSet(includeDocs);
						
				}
				break;
			}
			case LPAREN:
			case RPAREN:
			case ID:
			case LITERAL_or:
			case LITERAL_and:
			case CONST:
			case ANDEQ:
			case OREQ:
			case EQ:
			case NEQ:
			case UNION:
			case LT:
			case GT:
			case LTEQ:
			case GTEQ:
			case PLUS:
			case STAR:
			case INT:
			case LITERAL_text:
			case SLASH:
			case DSLASH:
			case 33:
			case 34:
			case LITERAL_contains:
			case LITERAL_match:
			case LITERAL_near:
			case FUNC:
			case ATTRIB:
			case ATTRIB_STAR:
			case LITERAL_node:
			case PARENT:
			case SELF:
			{
				or_expr(expr);
				if ( inputState.guessing==0 ) {
					
							RootNode rootStep = new RootNode(pool);
					exprIn.add(rootStep);
					exprIn.addPath(expr);
					exprIn.setDocumentSet(includeDocs);
						
				}
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				
						handleException(ex);
					
			} else {
				throw ex;
			}
		}
		catch (PermissionDeniedException e) {
			if (inputState.guessing==0) {
				
				handleException(e);
				
			} else {
				throw e;
			}
		}
	}
	
	public final void document_function(
		PathExpr expr
	) throws RecognitionException, TokenStreamException, PermissionDeniedException,EXistException {
		
		Token  arg3 = null;
		Token  arg1 = null;
		Token  arg2 = null;
		Token  arg4 = null;
		
			Expression step = null;
			boolean inclusive = true;
		
		
		switch ( LA(1)) {
		case LITERAL_doctype:
		{
			match(LITERAL_doctype);
			match(LPAREN);
			arg3 = LT(1);
			match(CONST);
			match(RPAREN);
			if ( inputState.guessing==0 ) {
				
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
			break;
		}
		case LITERAL_collection:
		{
			match(LITERAL_collection);
			match(LPAREN);
			arg4 = LT(1);
			match(CONST);
			{
			_loop374:
			do {
				if ((LA(1)==COMMA)) {
					match(COMMA);
					{
					switch ( LA(1)) {
					case LITERAL_false:
					{
						match(LITERAL_false);
						if ( inputState.guessing==0 ) {
							inclusive = false;
						}
						break;
					}
					case LITERAL_true:
					{
						match(LITERAL_true);
						if ( inputState.guessing==0 ) {
							inclusive = true;
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
					break _loop374;
				}
				
			} while (true);
			}
			match(RPAREN);
			if ( inputState.guessing==0 ) {
				
				DBBroker broker = null;
				try {
				broker = pool.get();
				includeDocs = broker.getDocumentsByCollection(user, arg4.getText(), inclusive);
				
				step = new RootNode(pool);
				expr.setDocumentSet(includeDocs);
				expr.add(step);
				} catch(EXistException e) {
				e.printStackTrace();
				} finally {
				pool.release( broker );
				}
					
			}
			break;
		}
		default:
			boolean synPredMatched369 = false;
			if (((LA(1)==LITERAL_document) && (LA(2)==LPAREN))) {
				int _m369 = mark();
				synPredMatched369 = true;
				inputState.guessing++;
				try {
					{
					match(LITERAL_document);
					match(LPAREN);
					match(STAR);
					}
				}
				catch (RecognitionException pe) {
					synPredMatched369 = false;
				}
				rewind(_m369);
				inputState.guessing--;
			}
			if ( synPredMatched369 ) {
				match(LITERAL_document);
				match(LPAREN);
				match(STAR);
				match(RPAREN);
				if ( inputState.guessing==0 ) {
					
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
			}
			else if ((LA(1)==LITERAL_document) && (LA(2)==LPAREN)) {
				match(LITERAL_document);
				match(LPAREN);
				arg1 = LT(1);
				match(CONST);
				if ( inputState.guessing==0 ) {
					
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
				{
				_loop371:
				do {
					if ((LA(1)==COMMA)) {
						match(COMMA);
						arg2 = LT(1);
						match(CONST);
						if ( inputState.guessing==0 ) {
							
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
					}
					else {
						break _loop371;
					}
					
				} while (true);
				}
				match(RPAREN);
			}
		else {
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
	}
	
	public final void or_expr(
		PathExpr exprIn
	) throws RecognitionException, TokenStreamException, PermissionDeniedException,EXistException {
		
		
			PathExpr left = new PathExpr(pool);
			PathExpr right = new PathExpr(pool);
			OpOr op = null;
			boolean branch = false;
		
		
		and_expr(left);
		{
		_loop349:
		do {
			if ((LA(1)==LITERAL_or)) {
				match(LITERAL_or);
				and_expr(right);
				if ( inputState.guessing==0 ) {
					
									if(!branch) {
										op = new OpOr(pool);
										exprIn.addPath(op);
										op.addPath(left);
									}
									op.add(right);
									right = new PathExpr(pool);
									branch = true;
								
				}
			}
			else {
				break _loop349;
			}
			
		} while (true);
		}
		if ( inputState.guessing==0 ) {
						
					if(!branch) 
						exprIn.add(left); 
				
		}
	}
	
	public final void expr(
		PathExpr exprIn
	) throws RecognitionException, TokenStreamException, PermissionDeniedException,EXistException {
		
		
		xpath_expr(exprIn);
		match(Token.EOF_TYPE);
	}
	
	public final void xpath_expr(
		PathExpr exprIn
	) throws RecognitionException, TokenStreamException, PermissionDeniedException,EXistException {
		
		
			PathExpr expr = new PathExpr(pool);
		
		
		try {      // for error handling
			switch ( LA(1)) {
			case LITERAL_doctype:
			case LITERAL_document:
			case LITERAL_collection:
			{
				document_function(exprIn);
				or_expr(exprIn);
				if ( inputState.guessing==0 ) {
					
					exprIn.setDocumentSet(includeDocs);
					
				}
				break;
			}
			case EOF:
			case LPAREN:
			case ID:
			case LITERAL_or:
			case LITERAL_and:
			case CONST:
			case ANDEQ:
			case OREQ:
			case EQ:
			case NEQ:
			case UNION:
			case LT:
			case GT:
			case LTEQ:
			case GTEQ:
			case PLUS:
			case STAR:
			case INT:
			case LITERAL_text:
			case SLASH:
			case DSLASH:
			case 33:
			case 34:
			case LITERAL_contains:
			case LITERAL_match:
			case LITERAL_near:
			case FUNC:
			case ATTRIB:
			case ATTRIB_STAR:
			case LITERAL_node:
			case PARENT:
			case SELF:
			{
				or_expr(exprIn);
				break;
			}
			default:
			{
				throw new NoViableAltException(LT(1), getFilename());
			}
			}
		}
		catch (RecognitionException ex) {
			if (inputState.guessing==0) {
				
						handleException(ex);
					
			} else {
				throw ex;
			}
		}
		catch (PermissionDeniedException e) {
			if (inputState.guessing==0) {
				
				handleException(e);
				
			} else {
				throw e;
			}
		}
	}
	
	public final void and_expr(
		PathExpr exprIn
	) throws RecognitionException, TokenStreamException, PermissionDeniedException,EXistException {
		
		
			PathExpr left = new PathExpr(pool);
			PathExpr right = new PathExpr(pool);
			boolean branch = false;
			OpAnd op = null;
		
		
		equality_expr(left);
		{
		_loop352:
		do {
			if ((LA(1)==LITERAL_and)) {
				match(LITERAL_and);
				equality_expr(right);
				if ( inputState.guessing==0 ) {
					
									if(op == null) {
										op = new OpAnd(pool);
										exprIn.addPath(op);
										op.add(left);
									}
									op.add(right);
									right = new PathExpr(pool);
									branch = true;
								
				}
			}
			else {
				break _loop352;
			}
			
		} while (true);
		}
		if ( inputState.guessing==0 ) {
			
					if(!branch) exprIn.add(left);
				
		}
	}
	
	public final void equality_expr(
		PathExpr exprIn
	) throws RecognitionException, TokenStreamException, PermissionDeniedException,EXistException {
		
		Token  l = null;
		
			PathExpr left = new PathExpr(pool);
			PathExpr right = new PathExpr(pool);
			int op=0;
			boolean branch = false;
		
		
		union_expr(left);
		{
		switch ( LA(1)) {
		case EQ:
		case NEQ:
		{
			{
			op=equality_operator();
			relational_expr(right);
			if ( inputState.guessing==0 ) {
				
									OpEquals exprEq = new OpEquals(pool, left, right, op);
									exprIn.addPath(exprEq);
									branch = true;
								
			}
			}
			break;
		}
		case ANDEQ:
		case OREQ:
		{
			{
			op=fulltext_operator();
			l = LT(1);
			match(CONST);
			if ( inputState.guessing==0 ) {
				
							  FunContains exprCont = new FunContains(pool, op);
						   	  exprCont.setPath(left);
						   	  DBBroker broker = null;
						   	  System.out.println("&= " + l.getText());
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
			}
			break;
		}
		case EOF:
		case RPAREN:
		case LITERAL_or:
		case LITERAL_and:
		case COMMA:
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
		if ( inputState.guessing==0 ) {
			
					if(!branch)
						exprIn.add(left);
				
		}
	}
	
	public final void union_expr(
		PathExpr expr
	) throws RecognitionException, TokenStreamException, PermissionDeniedException,EXistException {
		
		
		PathExpr left = new PathExpr(pool), right = new PathExpr(pool);
		boolean branch = false;
		
		
		relational_expr(left);
		{
		switch ( LA(1)) {
		case UNION:
		{
			if ( inputState.guessing==0 ) {
				branch=true;
			}
			match(UNION);
			union_expr(right);
			break;
		}
		case EOF:
		case RPAREN:
		case LITERAL_or:
		case LITERAL_and:
		case ANDEQ:
		case OREQ:
		case EQ:
		case NEQ:
		case COMMA:
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
		if ( inputState.guessing==0 ) {
			
					if(branch) {
						Union result = new Union(pool, left, right);
						expr.addPath(result);
					} else
						expr.add(left);
				
		}
	}
	
	public final int  equality_operator() throws RecognitionException, TokenStreamException, PermissionDeniedException,EXistException {
		int type;
		
		
		type = 0;
		
		
		switch ( LA(1)) {
		case EQ:
		{
			match(EQ);
			if ( inputState.guessing==0 ) {
				type = Constants.EQ;
			}
			break;
		}
		case NEQ:
		{
			match(NEQ);
			if ( inputState.guessing==0 ) {
				type = Constants.NEQ;
			}
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		return type;
	}
	
	public final void relational_expr(
		PathExpr expr
	) throws RecognitionException, TokenStreamException, PermissionDeniedException,EXistException {
		
		
			PathExpr left = new PathExpr(pool);
			PathExpr right = new PathExpr(pool);
			boolean branch = false;
			int rop = 0;
		
		
		additive_expr(left);
		{
		switch ( LA(1)) {
		case LT:
		case GT:
		case LTEQ:
		case GTEQ:
		{
			if ( inputState.guessing==0 ) {
				branch = true;
			}
			rop=relational_operator();
			additive_expr(right);
			break;
		}
		case EOF:
		case RPAREN:
		case LITERAL_or:
		case LITERAL_and:
		case ANDEQ:
		case OREQ:
		case EQ:
		case NEQ:
		case UNION:
		case COMMA:
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
		if ( inputState.guessing==0 ) {
			
					if(branch) {
						OpEquals exprEq = new OpEquals(pool, left, right, rop);
						expr.addPath(exprEq);
					} else
						expr.add(left);
				
		}
	}
	
	public final int  fulltext_operator() throws RecognitionException, TokenStreamException, PermissionDeniedException {
		int type;
		
		
		type = 0;
		
		
		switch ( LA(1)) {
		case ANDEQ:
		{
			match(ANDEQ);
			if ( inputState.guessing==0 ) {
				type = Constants.FULLTEXT_AND;
			}
			break;
		}
		case OREQ:
		{
			match(OREQ);
			if ( inputState.guessing==0 ) {
				type = Constants.FULLTEXT_OR;
			}
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		return type;
	}
	
	public final void additive_expr(
		PathExpr expr
	) throws RecognitionException, TokenStreamException, PermissionDeniedException,EXistException {
		
		
			PathExpr left = new PathExpr(pool);
			PathExpr right = new PathExpr(pool);
			boolean branch = false;
		
		
		pathexpr(left);
		{
		_loop366:
		do {
			if ((LA(1)==PLUS)) {
				if ( inputState.guessing==0 ) {
					branch = true;
				}
				match(PLUS);
				pathexpr(right);
			}
			else {
				break _loop366;
			}
			
		} while (true);
		}
		if ( inputState.guessing==0 ) {
			
					if(branch) {
						OpNumeric exprNum = new OpNumeric(pool, left, right, Constants.PLUS);
						expr.addPath(exprNum);
					} else
						expr.add(left);
				
		}
	}
	
	public final int  relational_operator() throws RecognitionException, TokenStreamException, PermissionDeniedException,EXistException {
		int type;
		
		
		type = 0;
		
		
		switch ( LA(1)) {
		case LT:
		{
			match(LT);
			if ( inputState.guessing==0 ) {
				type = Constants.LT;
			}
			break;
		}
		case GT:
		{
			match(GT);
			if ( inputState.guessing==0 ) {
				type = Constants.GT;
			}
			break;
		}
		case LTEQ:
		{
			match(LTEQ);
			if ( inputState.guessing==0 ) {
				type = Constants.LTEQ;
			}
			break;
		}
		case GTEQ:
		{
			match(GTEQ);
			if ( inputState.guessing==0 ) {
				type = Constants.GTEQ;
			}
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		return type;
	}
	
	public final void pathexpr(
		PathExpr expr
	) throws RecognitionException, TokenStreamException, PermissionDeniedException,EXistException {
		
		Expression result = null;
		
		
		switch ( LA(1)) {
		case EOF:
		case RPAREN:
		case LITERAL_or:
		case LITERAL_and:
		case ANDEQ:
		case OREQ:
		case EQ:
		case NEQ:
		case UNION:
		case LT:
		case GT:
		case LTEQ:
		case GTEQ:
		case PLUS:
		case COMMA:
		case RPPAREN:
		{
			break;
		}
		case LPAREN:
		case ID:
		case CONST:
		case STAR:
		case INT:
		case LITERAL_text:
		case SLASH:
		case DSLASH:
		case 33:
		case 34:
		case LITERAL_contains:
		case LITERAL_match:
		case LITERAL_near:
		case FUNC:
		case ATTRIB:
		case ATTRIB_STAR:
		case LITERAL_node:
		case PARENT:
		case SELF:
		{
			{
			int _cnt377=0;
			_loop377:
			do {
				if ((_tokenSet_2.member(LA(1)))) {
					result=regularexpr(expr);
					if ( inputState.guessing==0 ) {
						
								if(result instanceof Step && ((Step)result).getAxis() == -1)
									((Step)result).setAxis(Constants.CHILD_AXIS);
							
					}
				}
				else {
					if ( _cnt377>=1 ) { break _loop377; } else {throw new NoViableAltException(LT(1), getFilename());}
				}
				
				_cnt377++;
			} while (true);
			}
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
	}
	
	public final Expression  regularexpr(
		PathExpr expr
	) throws RecognitionException, TokenStreamException, PermissionDeniedException,EXistException {
		Expression result;
		
		result = null; Predicate pred = null;
		
		
		switch ( LA(1)) {
		case SLASH:
		{
			match(SLASH);
			result=regularexpr(expr);
			if ( inputState.guessing==0 ) {
				
						if(result instanceof Step && ((Step)result).getAxis() == -1)
							((Step)result).setAxis(Constants.CHILD_AXIS);
				
			}
			break;
		}
		case DSLASH:
		{
			match(DSLASH);
			result=regularexpr(expr);
			if ( inputState.guessing==0 ) {
				
						if(result instanceof Step)
							((Step)result).setAxis(Constants.DESCENDANT_AXIS);
				
			}
			break;
		}
		case LPAREN:
		case ID:
		case CONST:
		case STAR:
		case INT:
		case LITERAL_text:
		case 33:
		case 34:
		case LITERAL_contains:
		case LITERAL_match:
		case LITERAL_near:
		case FUNC:
		case ATTRIB:
		case ATTRIB_STAR:
		case LITERAL_node:
		case PARENT:
		case SELF:
		{
			result=step(expr);
			if ( inputState.guessing==0 ) {
				
						if(result instanceof Step && ((Step)result).getAxis() == -1)
				((Step)result).setAxis(Constants.CHILD_AXIS);
				
			}
			{
			_loop394:
			do {
				if ((LA(1)==LPPAREN)) {
					pred=predicate(expr);
					if ( inputState.guessing==0 ) {
						
								  expr.addPredicate(pred);
							
					}
				}
				else {
					break _loop394;
				}
				
			} while (true);
			}
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		return result;
	}
	
	public final Expression  primary_expr(
		PathExpr expr
	) throws RecognitionException, TokenStreamException, PermissionDeniedException,EXistException {
		Expression step;
		
		Token  l = null;
		Token  i = null;
		
			step = null;
			PathExpr path = null;
		
		
		switch ( LA(1)) {
		case LITERAL_text:
		case 33:
		case 34:
		case LITERAL_contains:
		case LITERAL_match:
		case LITERAL_near:
		case FUNC:
		{
			step=function_call(expr);
			break;
		}
		case CONST:
		{
			l = LT(1);
			match(CONST);
			if ( inputState.guessing==0 ) {
				
						step = new Literal(l.getText());
						expr.add(step);
					
			}
			break;
		}
		case INT:
		{
			i = LT(1);
			match(INT);
			if ( inputState.guessing==0 ) {
				
						step = new IntNumber(Double.parseDouble(i.getText()));
						expr.add(step);
					
			}
			break;
		}
		case LPAREN:
		{
			match(LPAREN);
			if ( inputState.guessing==0 ) {
				path = new PathExpr(pool);
			}
			or_expr(path);
			match(RPAREN);
			if ( inputState.guessing==0 ) {
				
						expr.addPath(path);
					
			}
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		return step;
	}
	
	public final Expression  function_call(
		PathExpr expr
	) throws RecognitionException, TokenStreamException, PermissionDeniedException,EXistException {
		Expression step;
		
		Token  l = null;
		Token  l2 = null;
		Token  arg = null;
		Token  l3 = null;
		Token  l4 = null;
		Token  i = null;
		Token  f1 = null;
		Token  f2 = null;
		
			step = null;
			PathExpr path = new PathExpr(pool);
			PathExpr arg1 = new PathExpr(pool);
			PathExpr arg2 = null;
			Function fun = null;
		int distance = 1;
		
		
		switch ( LA(1)) {
		case 33:
		{
			match(33);
			match(LPAREN);
			or_expr(path);
			match(COMMA);
			l = LT(1);
			match(CONST);
			match(RPAREN);
			if ( inputState.guessing==0 ) {
				
						if(path.returnsType() == Constants.TYPE_NODELIST) {
				String val = l.getText() + "%";
						   step = new OpEquals(pool, path, new Literal(val), Constants.EQ);
				expr.add(step);
						} else {
						   step = new FunStartsWith(pool, path, new Literal(l.getText()));
						   expr.add(step);
					    }
				
			}
			break;
		}
		case 34:
		{
			match(34);
			match(LPAREN);
			or_expr(path);
			match(COMMA);
			l2 = LT(1);
			match(CONST);
			match(RPAREN);
			if ( inputState.guessing==0 ) {
				
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
			break;
		}
		case LITERAL_contains:
		{
			match(LITERAL_contains);
			match(LPAREN);
			or_expr(path);
			match(COMMA);
			arg = LT(1);
			match(CONST);
			match(RPAREN);
			if ( inputState.guessing==0 ) {
				
				String term = "%" + arg.getText() + "%";
							step =
							  new OpEquals(pool, path, new Literal(term), Constants.EQ);
				System.out.println(step.pprint());
							expr.add(step);
					
			}
			break;
		}
		default:
			if ((LA(1)==LITERAL_text) && (LA(2)==LPAREN)) {
				match(LITERAL_text);
				match(LPAREN);
				match(RPAREN);
				if ( inputState.guessing==0 ) {
					
								step = new LocationStep(pool, -1, new TypeTest(Constants.TEXT_NODE));
								expr.add(step);
						
				}
			}
			else if ((LA(1)==LITERAL_text) && (_tokenSet_3.member(LA(2)))) {
				match(LITERAL_text);
				{
				switch ( LA(1)) {
				case SLASH:
				{
					match(SLASH);
					break;
				}
				case DSLASH:
				{
					match(DSLASH);
					break;
				}
				case EOF:
				{
					match(Token.EOF_TYPE);
					break;
				}
				case LPPAREN:
				{
					match(LPPAREN);
					break;
				}
				default:
				{
					throw new NoViableAltException(LT(1), getFilename());
				}
				}
				}
				if ( inputState.guessing==0 ) {
					
							step = new LocationStep( pool, -1,
								new NameTest("text"));
							expr.add(step);
						
				}
			}
			else if ((LA(1)==LITERAL_match) && (LA(2)==LPAREN)) {
				match(LITERAL_match);
				match(LPAREN);
				or_expr(path);
				match(COMMA);
				l3 = LT(1);
				match(CONST);
				match(RPAREN);
				if ( inputState.guessing==0 ) {
					
					if(path.returnsType() == Constants.TYPE_NODELIST) {
							step = new OpEquals(pool, path, new Literal(l3.getText()), 
					Constants.REGEXP);
					expr.add(step);
						     }
						
				}
			}
			else if ((LA(1)==LITERAL_match) && (_tokenSet_3.member(LA(2)))) {
				match(LITERAL_match);
				{
				switch ( LA(1)) {
				case SLASH:
				{
					match(SLASH);
					break;
				}
				case DSLASH:
				{
					match(DSLASH);
					break;
				}
				case EOF:
				{
					match(Token.EOF_TYPE);
					break;
				}
				case LPPAREN:
				{
					match(LPPAREN);
					break;
				}
				default:
				{
					throw new NoViableAltException(LT(1), getFilename());
				}
				}
				}
				if ( inputState.guessing==0 ) {
					
							step = new LocationStep( pool, -1,
								new NameTest("match"));
							expr.add(step);
						
				}
			}
			else if ((LA(1)==LITERAL_near) && (LA(2)==LPAREN)) {
				match(LITERAL_near);
				match(LPAREN);
				or_expr(path);
				match(COMMA);
				l4 = LT(1);
				match(CONST);
				{
				switch ( LA(1)) {
				case COMMA:
				{
					match(COMMA);
					i = LT(1);
					match(INT);
					if ( inputState.guessing==0 ) {
						distance = Integer.parseInt(i.getText());
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
				if ( inputState.guessing==0 ) {
					
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
			}
			else if ((LA(1)==LITERAL_near) && (_tokenSet_3.member(LA(2)))) {
				match(LITERAL_near);
				{
				switch ( LA(1)) {
				case SLASH:
				{
					match(SLASH);
					break;
				}
				case DSLASH:
				{
					match(DSLASH);
					break;
				}
				case EOF:
				{
					match(Token.EOF_TYPE);
					break;
				}
				case LPPAREN:
				{
					match(LPPAREN);
					break;
				}
				default:
				{
					throw new NoViableAltException(LT(1), getFilename());
				}
				}
				}
				if ( inputState.guessing==0 ) {
					
							step = new LocationStep( pool, -1,
								new NameTest("text"));
							expr.add(step);
						
				}
			}
			else {
				boolean synPredMatched385 = false;
				if (((LA(1)==FUNC) && (LA(2)==LPAREN))) {
					int _m385 = mark();
					synPredMatched385 = true;
					inputState.guessing++;
					try {
						{
						match(FUNC);
						match(LPAREN);
						match(RPAREN);
						}
					}
					catch (RecognitionException pe) {
						synPredMatched385 = false;
					}
					rewind(_m385);
					inputState.guessing--;
				}
				if ( synPredMatched385 ) {
					f1 = LT(1);
					match(FUNC);
					if (!( env.hasFunction(f1.getText()) ))
					  throw new SemanticException(" env.hasFunction(f1.getText()) ");
					match(LPAREN);
					match(RPAREN);
					if ( inputState.guessing==0 ) {
						
								fun = Function.createFunction(pool, env.getFunction(f1.getText()));
								expr.addPath(fun);
							
					}
				}
				else if ((LA(1)==FUNC) && (LA(2)==LPAREN)) {
					f2 = LT(1);
					match(FUNC);
					if (!( env.hasFunction(f2.getText()) ))
					  throw new SemanticException(" env.hasFunction(f2.getText()) ");
					if ( inputState.guessing==0 ) {
						
								fun = Function.createFunction(pool, env.getFunction(f2.getText()));
								expr.addPath(fun);
							
					}
					match(LPAREN);
					or_expr(arg1);
					if ( inputState.guessing==0 ) {
						fun.addArgument(arg1);
					}
					{
					_loop387:
					do {
						if ((LA(1)==COMMA)) {
							match(COMMA);
							if ( inputState.guessing==0 ) {
								arg2 = new PathExpr(pool);
							}
							or_expr(arg2);
							if ( inputState.guessing==0 ) {
								fun.addArgument(arg2);
							}
						}
						else {
							break _loop387;
						}
						
					} while (true);
					}
					match(RPAREN);
				}
			else {
				throw new NoViableAltException(LT(1), getFilename());
			}
			}}
			return step;
		}
		
	public final void empty_arglist() throws RecognitionException, TokenStreamException {
		
		
	}
	
	public final void function_args(
		Function fun
	) throws RecognitionException, TokenStreamException, PermissionDeniedException,EXistException {
		
		
			PathExpr arg1 = new PathExpr(pool);
			PathExpr arg2 = null;
		
		
		or_expr(arg1);
		if ( inputState.guessing==0 ) {
			fun.addArgument(arg1);
		}
		{
		_loop391:
		do {
			if ((LA(1)==COMMA)) {
				match(COMMA);
				if ( inputState.guessing==0 ) {
					arg2 = new PathExpr(pool);
				}
				or_expr(arg2);
				if ( inputState.guessing==0 ) {
					fun.addArgument(arg2);
				}
			}
			else {
				break _loop391;
			}
			
		} while (true);
		}
	}
	
	public final Expression  step(
		PathExpr expr
	) throws RecognitionException, TokenStreamException, PermissionDeniedException,EXistException {
		Expression step;
		
		Token  attr = null;
		Token  any = null;
		Token  anyAttr = null;
		Token  name = null;
		step = null;
		
		switch ( LA(1)) {
		case ATTRIB:
		{
			attr = LT(1);
			match(ATTRIB);
			if ( inputState.guessing==0 ) {
				
							step = new LocationStep(pool,
								Constants.ATTRIBUTE_AXIS,
								new NameTest(attr.getText()));
							expr.add(step);
					
			}
			break;
		}
		case STAR:
		{
			any = LT(1);
			match(STAR);
			if ( inputState.guessing==0 ) {
				
							step = new LocationStep(pool,
								-1,
								new TypeTest(Constants.ELEMENT_NODE));
							expr.add(step);
					
			}
			break;
		}
		case ATTRIB_STAR:
		{
			anyAttr = LT(1);
			match(ATTRIB_STAR);
			if ( inputState.guessing==0 ) {
				
						step = new LocationStep(pool, Constants.ATTRIBUTE_AXIS, new TypeTest(Constants.ATTRIBUTE_NODE));
						expr.add(step);
					
			}
			break;
		}
		case LITERAL_node:
		{
			match(LITERAL_node);
			match(LPAREN);
			match(RPAREN);
			if ( inputState.guessing==0 ) {
				
							step = new LocationStep(pool, -1, new TypeTest(Constants.NODE_TYPE));
							expr.add(step);
					
			}
			break;
		}
		case PARENT:
		{
			match(PARENT);
			if ( inputState.guessing==0 ) {
				
							step = new LocationStep(pool, 
								Constants.PARENT_AXIS,
								new TypeTest(Constants.NODE_TYPE));
							expr.add(step);
					
			}
			break;
		}
		case SELF:
		{
			match(SELF);
			if ( inputState.guessing==0 ) {
				
							step = new LocationStep(pool,
								Constants.SELF_AXIS,
								new TypeTest(Constants.NODE_TYPE));
							expr.add(step);
					
			}
			break;
		}
		case ID:
		{
			name = LT(1);
			match(ID);
			if ( inputState.guessing==0 ) {
				
							step = new LocationStep( pool, -1,
								new NameTest(name.getText()));
							expr.add(step);
					
			}
			break;
		}
		case LPAREN:
		case CONST:
		case INT:
		case LITERAL_text:
		case 33:
		case 34:
		case LITERAL_contains:
		case LITERAL_match:
		case LITERAL_near:
		case FUNC:
		{
			step=primary_expr(expr);
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		return step;
	}
	
	public final Predicate  predicate(
		PathExpr expr
	) throws RecognitionException, TokenStreamException, PermissionDeniedException,EXistException {
		Predicate pred;
		
		
		pred = new Predicate(pool);
		
		
		match(LPPAREN);
		or_expr(pred);
		match(RPPAREN);
		return pred;
	}
	
	
	public static final String[] _tokenNames = {
		"<0>",
		"EOF",
		"<2>",
		"NULL_TREE_LOOKAHEAD",
		"\"xpointer\"",
		"LPAREN",
		"RPAREN",
		"ID",
		"\"or\"",
		"\"and\"",
		"CONST",
		"ANDEQ",
		"OREQ",
		"EQ",
		"NEQ",
		"UNION",
		"LT",
		"GT",
		"LTEQ",
		"GTEQ",
		"PLUS",
		"\"doctype\"",
		"\"document\"",
		"STAR",
		"COMMA",
		"\"collection\"",
		"\"false\"",
		"\"true\"",
		"INT",
		"\"text\"",
		"SLASH",
		"DSLASH",
		"LPPAREN",
		"\"starts-with\"",
		"\"ends-with\"",
		"\"contains\"",
		"\"match\"",
		"\"near\"",
		"FUNC",
		"ATTRIB",
		"ATTRIB_STAR",
		"\"node\"",
		"PARENT",
		"SELF",
		"RPPAREN",
		"WS",
		"BASECHAR",
		"IDEOGRAPHIC",
		"DIGIT",
		"NMSTART",
		"NMCHAR",
		"NCNAME",
		"ID_OR_FUNC",
		"VARIABLE"
	};
	
	private static final long[] mk_tokenSet_0() {
		long[] data = { 64L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_0 = new BitSet(mk_tokenSet_0());
	private static final long[] mk_tokenSet_1() {
		long[] data = { 2L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_1 = new BitSet(mk_tokenSet_1());
	private static final long[] mk_tokenSet_2() {
		long[] data = { 17587631031456L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_2 = new BitSet(mk_tokenSet_2());
	private static final long[] mk_tokenSet_3() {
		long[] data = { 7516192770L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_3 = new BitSet(mk_tokenSet_3());
	
	}
