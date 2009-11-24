package org.exist.xquery.value;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;

import junit.framework.*;

import org.exist.xquery.XPathException;

/**
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class NumericOpPromotionTest extends TestCase {
	private static final double VALUE = 1.5;
	private static final IntegerValue ZERO = new IntegerValue(0), ONE = new IntegerValue(1);
	
	private static String[] tests;
	static {
		Collection<String> names = new ArrayList<String>();
		Method[] ms = NumericOpPromotionTest.class.getMethods();
		for (int i=0; i<ms.length; i++) {
			Method m = ms[i];
			if (Modifier.isPublic(m.getModifiers()) && m.getName().startsWith("test")) names.add(m.getName());
		}
		tests = (String[]) names.toArray(new String[0]);
	}
	
	public static Test suite() throws XPathException {
		TestSuite suite = new TestSuite();
		suite.addTest(fill(new DecimalValue(VALUE)));
		suite.addTest(fill(new DoubleValue(VALUE)));
		suite.addTest(fill(new FloatValue((float) VALUE)));
		return suite;
	}
	
	private static Test fill(ComputableValue operand) {
		TestSuite suite = new TestSuite();
		for (int i=0; i<tests.length; i++) {suite.addTest(new NumericOpPromotionTest(tests[i], operand));}
		return suite;
	}
	
	private final ComputableValue operand;
	public NumericOpPromotionTest(String name, ComputableValue operand) {
		super(name);
		this.operand = operand;
	}
	
	private void assertDoubleValue(double target, ComputableValue result) throws XPathException {
		assertEquals(target, ((Double) result.toJavaObject(Double.class)).doubleValue(), 0);
	}
	
	public void testIntegerDiv() throws XPathException {
		assertDoubleValue(VALUE, operand.div(ONE));
	}
	public void testIntegerMult() throws XPathException {
		assertDoubleValue(VALUE, operand.mult(ONE));
	}
	public void testIntegerPlus() throws XPathException {
		assertDoubleValue(VALUE, operand.plus(ZERO));
	}
	public void testIntegerMinus() throws XPathException {
		assertDoubleValue(VALUE, operand.minus(ZERO));
	}
	
	public String toString() {
		// TODO: replace with getSimpleName() in JDK 1.5
		String operandName = operand.getClass().getName();
		int k = operandName.lastIndexOf('.');
		if (k >= 0) operandName = operandName.substring(k+1);
		return getName() + " " + operandName + " (" + getClass().getName() + ")";
	}

}
