/*
 * Util.java - Aug 29, 2003
 * 
 * @author wolf
 */
package org.exist.parser;

import java.util.Vector;

import org.exist.EXistException;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.xpath.Constants;
import org.exist.xpath.Expression;
import org.exist.xpath.ExtNear;
import org.exist.xpath.Function;
import org.exist.xpath.Literal;
import org.exist.xpath.OpEquals;
import org.exist.xpath.PathExpr;
import org.exist.xpath.RootNode;
import org.exist.xpath.StaticContext;

public class Util {

	/**
	 * Create a function call. This method handles special functions like 
	 * document(), collection() or near(). It also optimizes some function 
	 * calls.
	 * 
	 * @param pool
	 * @param context
	 * @param parent
	 * @param fnName
	 * @param params
	 * @return
	 * @throws EXistException
	 * @throws PermissionDeniedException
	 */
	public static Expression createFunction(
		BrokerPool pool,
		StaticContext context,
		PathExpr parent,
		String fnName,
		Vector params)
		throws EXistException, PermissionDeniedException {
		Expression step = null;
		if (fnName.equals("document")) {
			DocumentSet docs = null;
			DBBroker broker = null;
			try {
				broker = pool.get();
				if (params.size() == 0)
					docs = broker.getAllDocuments(context.getUser());
				else {
					docs = new DocumentSet();
					String next;
					DocumentImpl doc;
					for (int i = 0; i < params.size(); i++) {
						next = ((PathExpr) params.elementAt(i)).getLiteralValue();
						doc = (DocumentImpl) broker.getDocument(context.getUser(), next);
						if (doc != null)
							docs.add(doc);
					}
				}
			} finally {
				pool.release(broker);
			}
			step = new RootNode(pool);
			parent.add(step);
			parent.setDocumentSet(docs);
		}
		if (fnName.equals("collection") || fnName.equals("xcollection")) {
			DocumentSet docs = new DocumentSet();
			DBBroker broker = null;
			boolean inclusive = fnName.equals("collection");
			try {
				broker = pool.get();
				String next;
				DocumentSet temp;
				for (int i = 0; i < params.size(); i++) {
					next = ((PathExpr) params.elementAt(i)).getLiteralValue();
					temp = broker.getDocumentsByCollection(context.getUser(), next, inclusive);
					docs.addAll(temp);
				}
			} finally {
				pool.release(broker);
			}
			step = new RootNode(pool);
			parent.add(step);
			parent.setDocumentSet(docs);
		}
		if (fnName.equals("doctype")) {
			DocumentSet docs = new DocumentSet();
			DBBroker broker = null;
			try {
				broker = pool.get();
				String next;
				DocumentSet temp;
				for (int i = 0; i < params.size(); i++) {
					next = ((PathExpr) params.elementAt(i)).getLiteralValue();
					temp = broker.getDocumentsByDoctype(context.getUser(), next);
					docs.addAll(temp);
				}
			} finally {
				pool.release(broker);
			}
			step = new RootNode(pool);
			parent.add(step);
			parent.setDocumentSet(docs);
		}
		
		// near(node-set, string)
		if (fnName.equals("near")) {
			if (params.size() < 2)
				throw new IllegalArgumentException("Function near requires two arguments");
			PathExpr p1 = (PathExpr) params.elementAt(1);
			if (p1.getLength() == 0)
				throw new IllegalArgumentException("Second argument to near is empty");
			Expression e1 = p1.getExpression(0);
			if(!(e1 instanceof Literal))
				throw new IllegalArgumentException("Second argument has to be a literal expression");
			ExtNear near = new ExtNear(pool);
			near.addTerms(((Literal)e1).getLiteral());
			near.setPath((PathExpr)params.elementAt(0));
			step = near;
			parent.addPath(near);
		}
		
		// ends-with(node-set, string)
		if (fnName.equals("starts-with")) {
			if (params.size() < 2)
				throw new IllegalArgumentException("Function starts-with requires two arguments");
			PathExpr p0 = (PathExpr) params.elementAt(0);
			PathExpr p1 = (PathExpr) params.elementAt(1);
			if (p1.getLength() == 0)
				throw new IllegalArgumentException("Second argument to starts-with is empty");
			Expression e1 = p1.getExpression(0);
			if (e1 instanceof Literal && p0.returnsType() == Constants.TYPE_NODELIST) {
				Literal l = (Literal) e1;
				l.setLiteral(l.getLiteral() + '%');
				OpEquals op = new OpEquals(pool, p0, e1, Constants.EQ);
				parent.addPath(op);
				step = op;
			}
		}
		
		// ends-with(node-set, string)
		if (fnName.equals("ends-with")) {
			if (params.size() < 2)
				throw new IllegalArgumentException("Function ends-with requires two arguments");
			PathExpr p0 = (PathExpr) params.elementAt(0);
			PathExpr p1 = (PathExpr) params.elementAt(1);
			if (p1.getLength() == 0)
				throw new IllegalArgumentException("Second argument to ends-with is empty");
			Expression e1 = p1.getExpression(0);
			if (e1 instanceof Literal && p0.returnsType() == Constants.TYPE_NODELIST) {
				Literal l = (Literal) e1;
				l.setLiteral('%' + l.getLiteral());
				OpEquals op = new OpEquals(pool, p0, e1, Constants.EQ);
				parent.addPath(op);
				step = op;
			}
		}
		
		// contains(node-set, string)
		if (fnName.equals("contains")) {
			if (params.size() < 2)
				throw new IllegalArgumentException("Function contains requires two arguments");
			PathExpr p0 = (PathExpr) params.elementAt(0);
			PathExpr p1 = (PathExpr) params.elementAt(1);
			if (p1.getLength() == 0)
				throw new IllegalArgumentException("Second argument to contains is empty");
			Expression e1 = p1.getExpression(0);
			if (e1 instanceof Literal && p0.returnsType() == Constants.TYPE_NODELIST) {
				Literal l = (Literal) e1;
				l.setLiteral('%' + l.getLiteral() + '%');
				OpEquals op = new OpEquals(pool, p0, e1, Constants.EQ);
				parent.addPath(op);
				step = op;
			}
		}
		if (step == null) {
			String clazz = context.getClassForFunction(fnName);
			if (clazz == null)
				throw new EXistException("function " + fnName + " not defined");
			Function func = Function.createFunction(pool, clazz);
			parent.addPath(func);
			for (int i = 0; i < params.size(); i++)
				func.addArgument((PathExpr) params.elementAt(i));
			step = func;
		}
		return step;
	}
}
