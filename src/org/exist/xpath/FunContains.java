/*
 * FunContains.java - Aug 29, 2003
 * 
 * @author wolf
 */
package org.exist.xpath;

import org.exist.dom.DocumentSet;
import org.exist.xpath.value.BooleanValue;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.Type;

public class FunContains extends Function {

	public FunContains() {
		super("contains");
	}
	
	public int returnsType() {
		return Type.STRING;
	}
	
	public Sequence eval(StaticContext context, DocumentSet docs, Sequence contextSequence, 
		Item contextItem) throws XPathException {
		if (getArgumentCount() != 2)
			throw new IllegalArgumentException("starts-with expects two arguments");
		if(contextItem != null)
			contextSequence = contextItem.toSequence();
		String s1 = getArgument(0).eval(context, docs, contextSequence).getStringValue();
		String s2 = getArgument(1).eval(context, docs, contextSequence).getStringValue();
		if(s1.indexOf(s2) > -1)
			return new BooleanValue(true);
		else
			return new BooleanValue(false);
	}
}
