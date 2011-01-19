package org.exist.interpreter;

import org.exist.dom.QName;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

public interface IPragma {

	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException;

	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException;

	public void before(Context context, Expression expression) throws XPathException;

	public void after(Context context, Expression expression) throws XPathException;

	public void resetState(boolean postOptimization);

	public String getContents();

	public QName getQName();
}