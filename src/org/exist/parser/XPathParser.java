// $ANTLR 2.7.2rc2 (20030105): "XPathParser.g" -> "XPathParser.java"$

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

public class XPathParser extends antlr.LLkParser       implements XPathParserTokenTypes
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
		case NCNAME:
		{
			id = LT(1);
			match(NCNAME);
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
			case LITERAL_xcollection:
			{
				document_function(exprIn);
				or_expr(exprIn);
				if ( inputState.guessing==0 ) {
					
							exprIn.setDocumentSet(includeDocs);
						
				}
				break;
			}
			case LPAREN:
			case NCNAME:
			case CONST:
			case STAR:
			case INT:
			case LITERAL_text:
			case 29:
			case 30:
			case LITERAL_contains:
			case LITERAL_match:
			case LITERAL_near:
			case SLASH:
			case DSLASH:
			case AT:
			case ATTRIB_STAR:
			case LITERAL_node:
			case PARENT:
			case SELF:
			case LITERAL_descendant:
			case 43:
			case LITERAL_child:
			case LITERAL_parent:
			case LITERAL_self:
			case LITERAL_attribute:
			case LITERAL_ancestor:
			case 49:
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
		Token  arg6 = null;
		Token  arg7 = null;
		Token  arg8 = null;
		Token  arg9 = null;
		
			Expression step = null;
			boolean inclusive = true;
			DocumentSet temp;
		
		
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
			arg6 = LT(1);
			match(CONST);
			if ( inputState.guessing==0 ) {
				
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
			{
			_loop31:
			do {
				if ((LA(1)==COMMA)) {
					match(COMMA);
					arg7 = LT(1);
					match(CONST);
					if ( inputState.guessing==0 ) {
						
							DBBroker broker = null;
							try {
								broker = pool.get();
							temp = broker.getDocumentsByCollection(user, arg7.getText(), true);
							includeDocs.addAll(temp);
						} catch(EXistException e) {
						} finally {
							pool.release(broker);
						}
						
					}
				}
				else {
					break _loop31;
				}
				
			} while (true);
			}
			match(RPAREN);
			if ( inputState.guessing==0 ) {
				
					step = new RootNode(pool);
					expr.setDocumentSet(includeDocs);
					expr.add(step);
				
			}
			break;
		}
		case LITERAL_xcollection:
		{
			match(LITERAL_xcollection);
			match(LPAREN);
			arg8 = LT(1);
			match(CONST);
			if ( inputState.guessing==0 ) {
				
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
			{
			_loop33:
			do {
				if ((LA(1)==COMMA)) {
					match(COMMA);
					arg9 = LT(1);
					match(CONST);
					if ( inputState.guessing==0 ) {
						
							DBBroker broker = null;
							try {
								broker = pool.get();
							temp = broker.getDocumentsByCollection(user, arg9.getText(), false);
							includeDocs.addAll(temp);
						} catch(EXistException e) {
						} finally {
							pool.release(broker);
						}
						
					}
				}
				else {
					break _loop33;
				}
				
			} while (true);
			}
			match(RPAREN);
			if ( inputState.guessing==0 ) {
				
					step = new RootNode(pool);
					expr.setDocumentSet(includeDocs);
					expr.add(step);
				
			}
			break;
		}
		default:
			boolean synPredMatched27 = false;
			if (((LA(1)==LITERAL_document) && (LA(2)==LPAREN))) {
				int _m27 = mark();
				synPredMatched27 = true;
				inputState.guessing++;
				try {
					{
					match(LITERAL_document);
					match(LPAREN);
					match(STAR);
					}
				}
				catch (RecognitionException pe) {
					synPredMatched27 = false;
				}
				rewind(_m27);
				inputState.guessing--;
			}
			if ( synPredMatched27 ) {
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
				_loop29:
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
						break _loop29;
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
		_loop7:
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
				break _loop7;
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
			case LITERAL_xcollection:
			{
				document_function(exprIn);
				or_expr(exprIn);
				if ( inputState.guessing==0 ) {
					
					exprIn.setDocumentSet(includeDocs);
					
				}
				break;
			}
			case LPAREN:
			case NCNAME:
			case CONST:
			case STAR:
			case INT:
			case LITERAL_text:
			case 29:
			case 30:
			case LITERAL_contains:
			case LITERAL_match:
			case LITERAL_near:
			case SLASH:
			case DSLASH:
			case AT:
			case ATTRIB_STAR:
			case LITERAL_node:
			case PARENT:
			case SELF:
			case LITERAL_descendant:
			case 43:
			case LITERAL_child:
			case LITERAL_parent:
			case LITERAL_self:
			case LITERAL_attribute:
			case LITERAL_ancestor:
			case 49:
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
		_loop10:
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
				break _loop10;
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
		_loop24:
		do {
			if ((LA(1)==PLUS)) {
				if ( inputState.guessing==0 ) {
					branch = true;
				}
				match(PLUS);
				pathexpr(right);
			}
			else {
				break _loop24;
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
			PathExpr path = null;
		
		
		switch ( LA(1)) {
		case NCNAME:
		case CONST:
		case STAR:
		case INT:
		case LITERAL_text:
		case 29:
		case 30:
		case LITERAL_contains:
		case LITERAL_match:
		case LITERAL_near:
		case SLASH:
		case DSLASH:
		case AT:
		case ATTRIB_STAR:
		case LITERAL_node:
		case PARENT:
		case SELF:
		case LITERAL_descendant:
		case 43:
		case LITERAL_child:
		case LITERAL_parent:
		case LITERAL_self:
		case LITERAL_attribute:
		case LITERAL_ancestor:
		case 49:
		{
			{
			int _cnt36=0;
			_loop36:
			do {
				if ((_tokenSet_0.member(LA(1)))) {
					result=regularexpr(expr);
					if ( inputState.guessing==0 ) {
						
								if(result instanceof Step && ((Step)result).getAxis() == -1)
									((Step)result).setAxis(Constants.CHILD_AXIS);
							
					}
				}
				else {
					if ( _cnt36>=1 ) { break _loop36; } else {throw new NoViableAltException(LT(1), getFilename());}
				}
				
				_cnt36++;
			} while (true);
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
	}
	
	public final Expression  regularexpr(
		PathExpr expr
	) throws RecognitionException, TokenStreamException, PermissionDeniedException,EXistException {
		Expression result;
		
		result = null; 
			Predicate pred = null;
			int axis = Constants.CHILD_AXIS;
		
		
		switch ( LA(1)) {
		case LITERAL_descendant:
		case 43:
		case LITERAL_child:
		case LITERAL_parent:
		case LITERAL_self:
		case LITERAL_attribute:
		case LITERAL_ancestor:
		case 49:
		{
			axis=axis_spec();
			result=step(expr);
			if ( inputState.guessing==0 ) {
				
						if(result instanceof Step && ((Step)result).getAxis() == -1)
				((Step)result).setAxis(axis);
				
			}
			{
			_loop52:
			do {
				if ((LA(1)==LPPAREN)) {
					pred=predicate(expr);
					if ( inputState.guessing==0 ) {
						
								expr.addPredicate(pred);
							
					}
				}
				else {
					break _loop52;
				}
				
			} while (true);
			}
			break;
		}
		case NCNAME:
		case CONST:
		case STAR:
		case INT:
		case LITERAL_text:
		case 29:
		case 30:
		case LITERAL_contains:
		case LITERAL_match:
		case LITERAL_near:
		case AT:
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
			_loop54:
			do {
				if ((LA(1)==LPPAREN)) {
					pred=predicate(expr);
					if ( inputState.guessing==0 ) {
						
								  expr.addPredicate(pred);
							
					}
				}
				else {
					break _loop54;
				}
				
			} while (true);
			}
			break;
		}
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
							((Step)result).setAxis(Constants.DESCENDANT_SELF_AXIS);
				
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
		case LITERAL_text:
		{
			match(LITERAL_text);
			match(LPAREN);
			match(RPAREN);
			if ( inputState.guessing==0 ) {
				
							step = new LocationStep(pool, -1, new TypeTest(Constants.TEXT_NODE));
							expr.add(step);
					
			}
			break;
		}
		case 29:
		{
			match(29);
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
		case 30:
		{
			match(30);
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
		case LITERAL_match:
		{
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
			break;
		}
		case LITERAL_near:
		{
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
			break;
		}
		default:
			boolean synPredMatched41 = false;
			if (((LA(1)==NCNAME) && (LA(2)==LPAREN))) {
				int _m41 = mark();
				synPredMatched41 = true;
				inputState.guessing++;
				try {
					{
					match(NCNAME);
					match(LPAREN);
					match(RPAREN);
					}
				}
				catch (RecognitionException pe) {
					synPredMatched41 = false;
				}
				rewind(_m41);
				inputState.guessing--;
			}
			if ( synPredMatched41 ) {
				f1 = LT(1);
				match(NCNAME);
				if (!( env.hasFunction(f1.getText()) ))
				  throw new SemanticException(" env.hasFunction(f1.getText()) ");
				match(LPAREN);
				match(RPAREN);
				if ( inputState.guessing==0 ) {
					
							fun = Function.createFunction(pool, env.getFunction(f1.getText()));
							expr.addPath(fun);
						
				}
			}
			else {
				boolean synPredMatched43 = false;
				if (((LA(1)==NCNAME) && (LA(2)==LPAREN))) {
					int _m43 = mark();
					synPredMatched43 = true;
					inputState.guessing++;
					try {
						{
						match(NCNAME);
						match(LPAREN);
						}
					}
					catch (RecognitionException pe) {
						synPredMatched43 = false;
					}
					rewind(_m43);
					inputState.guessing--;
				}
				if ( synPredMatched43 ) {
					f2 = LT(1);
					match(NCNAME);
					match(LPAREN);
					if ( inputState.guessing==0 ) {
						
								fun = Function.createFunction(pool, env.getFunction(f2.getText()));
								expr.addPath(fun);
							
					}
					or_expr(arg1);
					if ( inputState.guessing==0 ) {
						fun.addArgument(arg1);
					}
					{
					_loop45:
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
							break _loop45;
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
		_loop49:
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
				break _loop49;
			}
			
		} while (true);
		}
	}
	
	public final int  axis_spec() throws RecognitionException, TokenStreamException {
		int axis;
		
		
			axis = -1;
		
		
		switch ( LA(1)) {
		case LITERAL_descendant:
		{
			match(LITERAL_descendant);
			match(COLON);
			match(COLON);
			if ( inputState.guessing==0 ) {
				
						axis = Constants.DESCENDANT_AXIS;
					
			}
			break;
		}
		case 43:
		{
			match(43);
			match(COLON);
			match(COLON);
			if ( inputState.guessing==0 ) {
				
						axis = Constants.DESCENDANT_SELF_AXIS;
					
			}
			break;
		}
		case LITERAL_child:
		{
			match(LITERAL_child);
			match(COLON);
			match(COLON);
			if ( inputState.guessing==0 ) {
				
						axis = Constants.CHILD_AXIS;
					
			}
			break;
		}
		case LITERAL_parent:
		{
			match(LITERAL_parent);
			match(COLON);
			match(COLON);
			if ( inputState.guessing==0 ) {
				
						axis = Constants.PARENT_AXIS;
					
			}
			break;
		}
		case LITERAL_self:
		{
			match(LITERAL_self);
			match(COLON);
			match(COLON);
			if ( inputState.guessing==0 ) {
				
						axis = Constants.SELF_AXIS;
					
			}
			break;
		}
		case LITERAL_attribute:
		{
			match(LITERAL_attribute);
			match(COLON);
			match(COLON);
			if ( inputState.guessing==0 ) {
				
						axis = Constants.ATTRIBUTE_AXIS;
					
			}
			break;
		}
		case LITERAL_ancestor:
		{
			match(LITERAL_ancestor);
			match(COLON);
			match(COLON);
			if ( inputState.guessing==0 ) {
				
						axis = Constants.ANCESTOR_AXIS;
					
			}
			break;
		}
		case 49:
		{
			match(49);
			match(COLON);
			match(COLON);
			if ( inputState.guessing==0 ) {
				
						axis = Constants.ANCESTOR_SELF_AXIS;
					
			}
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		return axis;
	}
	
	public final Expression  step(
		PathExpr expr
	) throws RecognitionException, TokenStreamException, PermissionDeniedException,EXistException {
		Expression step;
		
		Token  any = null;
		Token  anyAttr = null;
		step = null; 
		String qn;
		String attr;
		
		
		switch ( LA(1)) {
		case AT:
		{
			match(AT);
			attr=qname();
			if ( inputState.guessing==0 ) {
				
							step = new LocationStep(pool,
								Constants.ATTRIBUTE_AXIS,
								new NameTest(attr));
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
		case CONST:
		case INT:
		{
			step=primary_expr(expr);
			break;
		}
		default:
			if ((_tokenSet_1.member(LA(1))) && (LA(2)==LPAREN)) {
				step=function_call(expr);
			}
			else if ((_tokenSet_2.member(LA(1))) && (_tokenSet_3.member(LA(2)))) {
				qn=qname();
				if ( inputState.guessing==0 ) {
					
								step = new LocationStep( pool, -1, new NameTest(qn));
								expr.add(step);
						
				}
			}
		else {
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
	
	public final String  qname() throws RecognitionException, TokenStreamException {
		String name;
		
		Token  n1 = null;
		Token  n2 = null;
		
			name = null;
		
		
		switch ( LA(1)) {
		case NCNAME:
		{
			n1 = LT(1);
			match(NCNAME);
			if ( inputState.guessing==0 ) {
				name = n1.getText();
			}
			{
			switch ( LA(1)) {
			case COLON:
			{
				match(COLON);
				n2 = LT(1);
				match(NCNAME);
				if ( inputState.guessing==0 ) {
					name = name + ':' + n2.getText();
				}
				break;
			}
			case EOF:
			case RPAREN:
			case NCNAME:
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
			case COMMA:
			case INT:
			case LITERAL_text:
			case 29:
			case 30:
			case LITERAL_contains:
			case LITERAL_match:
			case LITERAL_near:
			case SLASH:
			case DSLASH:
			case AT:
			case ATTRIB_STAR:
			case LITERAL_node:
			case PARENT:
			case SELF:
			case LITERAL_descendant:
			case 43:
			case LITERAL_child:
			case LITERAL_parent:
			case LITERAL_self:
			case LITERAL_attribute:
			case LITERAL_ancestor:
			case 49:
			case LPPAREN:
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
			break;
		}
		case LITERAL_text:
		{
			match(LITERAL_text);
			if ( inputState.guessing==0 ) {
				name = "text";
			}
			break;
		}
		case LITERAL_contains:
		{
			match(LITERAL_contains);
			if ( inputState.guessing==0 ) {
				name = "contains";
			}
			break;
		}
		case 29:
		{
			match(29);
			if ( inputState.guessing==0 ) {
				name = "starts-with";
			}
			break;
		}
		case 30:
		{
			match(30);
			if ( inputState.guessing==0 ) {
				name = "ends-with";
			}
			break;
		}
		case LITERAL_near:
		{
			match(LITERAL_near);
			if ( inputState.guessing==0 ) {
				name = "near";
			}
			break;
		}
		default:
		{
			throw new NoViableAltException(LT(1), getFilename());
		}
		}
		return name;
	}
	
	
	public static final String[] _tokenNames = {
		"<0>",
		"EOF",
		"<2>",
		"NULL_TREE_LOOKAHEAD",
		"\"xpointer\"",
		"LPAREN",
		"RPAREN",
		"NCNAME",
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
		"\"xcollection\"",
		"INT",
		"\"text\"",
		"\"starts-with\"",
		"\"ends-with\"",
		"\"contains\"",
		"\"match\"",
		"\"near\"",
		"SLASH",
		"DSLASH",
		"AT",
		"ATTRIB_STAR",
		"\"node\"",
		"PARENT",
		"SELF",
		"COLON",
		"\"descendant\"",
		"\"descendant-or-self\"",
		"\"child\"",
		"\"parent\"",
		"\"self\"",
		"\"attribute\"",
		"\"ancestor\"",
		"\"ancestor-or-self\"",
		"LPPAREN",
		"RPPAREN",
		"WS",
		"BASECHAR",
		"IDEOGRAPHIC",
		"DIGIT",
		"NMSTART",
		"NMCHAR",
		"VARIABLE"
	};
	
	private static final long[] mk_tokenSet_0() {
		long[] data = { 1123700757759104L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_0 = new BitSet(mk_tokenSet_0());
	private static final long[] mk_tokenSet_1() {
		long[] data = { 16911433856L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_1 = new BitSet(mk_tokenSet_1());
	private static final long[] mk_tokenSet_2() {
		long[] data = { 12616466560L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_2 = new BitSet(mk_tokenSet_2());
	private static final long[] mk_tokenSet_3() {
		long[] data = { 4503599520415682L, 0L};
		return data;
	}
	public static final BitSet _tokenSet_3 = new BitSet(mk_tokenSet_3());
	
	}
