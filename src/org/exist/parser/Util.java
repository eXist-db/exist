/*
 * Util.java - Aug 29, 2003
 * 
 * @author wolf
 */
package org.exist.parser;

import java.util.List;

import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.security.PermissionDeniedException;
import org.exist.xpath.Constants;
import org.exist.xpath.Expression;
import org.exist.xpath.GeneralComparison;
import org.exist.xpath.LiteralValue;
import org.exist.xpath.PathExpr;
import org.exist.xpath.RootNode;
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.functions.ExtNear;
import org.exist.xpath.functions.Function;
import org.exist.xpath.value.AtomicValue;
import org.exist.xpath.value.StringValue;
import org.exist.xpath.value.Type;

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
		StaticContext context,
		PathExpr parent,
		String fnName,
		List params)
		throws XPathException {
		Expression step = null;
		if (fnName.equals("document")) {
			DocumentSet docs = null;
			if (params.size() == 0)
				docs = context.getBroker().getAllDocuments();
			else {
				docs = new DocumentSet();
				try {
					String next;
					DocumentImpl doc;
					for (int i = 0; i < params.size(); i++) {
						next = ((PathExpr) params.get(i)).getLiteralValue();
						doc =
							(DocumentImpl) context.getBroker().getDocument(
								next);
						if (doc != null)
							docs.add(doc);
					}
				} catch (PermissionDeniedException e) {
					throw new XPathException("permission denied while retrieving input set");
				}
			}
			step = new RootNode(context);
			parent.add(step);
			parent.setDocumentSet(docs);
		}
		if (fnName.equals("collection") || fnName.equals("xcollection")) {
			DocumentSet docs = new DocumentSet();
			boolean inclusive = fnName.equals("collection");
			try {
				String next;
				DocumentSet temp;
				for (int i = 0; i < params.size(); i++) {
					next = ((PathExpr) params.get(i)).getLiteralValue();
					temp =
						context.getBroker().getDocumentsByCollection(
							next,
							inclusive);
					docs.addAll(temp);
				}
			} catch (PermissionDeniedException e) {
				throw new XPathException("permission denied while retrieving input set");
			}
			step = new RootNode(context);
			parent.add(step);
			parent.setDocumentSet(docs);
		}
		if (fnName.equals("doctype")) {
			DocumentSet docs = new DocumentSet();
			String next;
			DocumentSet temp;
			for (int i = 0; i < params.size(); i++) {
				next = ((PathExpr) params.get(i)).getLiteralValue();
				temp = context.getBroker().getDocumentsByDoctype(next);
				docs.addAll(temp);
			}
			step = new RootNode(context);
			parent.add(step);
			parent.setDocumentSet(docs);
		}

		// near(node-set, string)
		if (fnName.equals("near")) {
			if (params.size() < 2)
				throw new IllegalArgumentException("Function near requires two arguments");
			PathExpr p1 = (PathExpr) params.get(1);
			if (p1.getLength() == 0)
				throw new IllegalArgumentException("Second argument to near is empty");
			Expression e1 = p1.getExpression(0);
			ExtNear near = new ExtNear(context);
			near.addTerm(e1);
			near.setPath((PathExpr) params.get(0));
			if (params.size() > 2) {
				p1 = (PathExpr) params.get(2);
				if (p1.getLength() == 0)
					throw new IllegalArgumentException("Distance argument to near is empty");
				near.setDistance(p1);
			}
			step = near;
			parent.addPath(near);
		}

		// ends-with(node-set, string)
		if (fnName.equals("starts-with")) {
			if (params.size() < 2)
				throw new IllegalArgumentException("Function starts-with requires two arguments");
			PathExpr p0 = (PathExpr) params.get(0);
			PathExpr p1 = (PathExpr) params.get(1);
			if (p1.getLength() == 0)
				throw new IllegalArgumentException("Second argument to starts-with is empty");
			Expression e1 = p1.getExpression(0);
			if (e1 instanceof LiteralValue && p0.returnsType() == Type.NODE) {
				LiteralValue l = (LiteralValue) e1;
				AtomicValue v =
					new StringValue(l.getValue().getStringValue() + '%');
				l.setValue(v);
				GeneralComparison op =
					new GeneralComparison(context, p0, e1, Constants.EQ);
				parent.addPath(op);
				step = op;
			}
		}

		// ends-with(node-set, string)
		if (fnName.equals("ends-with")) {
			if (params.size() < 2)
				throw new IllegalArgumentException("Function ends-with requires two arguments");
			PathExpr p0 = (PathExpr) params.get(0);
			PathExpr p1 = (PathExpr) params.get(1);
			if (p1.getLength() == 0)
				throw new IllegalArgumentException("Second argument to ends-with is empty");
			Expression e1 = p1.getExpression(0);
			if (e1 instanceof LiteralValue && p0.returnsType() == Type.NODE) {
				LiteralValue l = (LiteralValue) e1;
				AtomicValue v =
					new StringValue('%' + l.getValue().getStringValue());
				l.setValue(v);
				GeneralComparison op =
					new GeneralComparison(context, p0, e1, Constants.EQ);
				parent.addPath(op);
				step = op;
			}
		}

		// contains(node-set, string)
		if (fnName.equals("contains")) {
			if (params.size() < 2)
				throw new IllegalArgumentException("Function contains requires two arguments");
			PathExpr p0 = (PathExpr) params.get(0);
			PathExpr p1 = (PathExpr) params.get(1);
			if (p1.getLength() == 0)
				throw new IllegalArgumentException("Second argument to contains is empty");
			Expression e1 = p1.getExpression(0);
			if (e1 instanceof LiteralValue && p0.returnsType() == Type.NODE) {
				LiteralValue l = (LiteralValue) e1;
				AtomicValue v =
					new StringValue('%' + l.getValue().getStringValue() + '%');
				l.setValue(v);
				GeneralComparison op =
					new GeneralComparison(context, p0, e1, Constants.EQ);
				parent.addPath(op);
				step = op;
			}
		}
		if (step == null) {
			String clazz = context.getClassForFunction(fnName);
			if (clazz == null)
				throw new XPathException("function " + fnName + " not defined");
			Function func = Function.createFunction(context, clazz);
			func.setArguments(params);
			parent.addPath(func);
			step = func;
		}
		return step;
	}
}
