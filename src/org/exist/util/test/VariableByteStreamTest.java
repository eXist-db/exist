package org.exist.util.test;

import junit.framework.TestCase;

import java.io.EOFException;
import java.io.IOException;
import java.util.Random;

import org.exist.util.VariableByteInputStream;
import org.exist.util.VariableByteOutputStream;

public class VariableByteStreamTest extends TestCase {

	private long[] longValues = new long[1000];
	
	/**
	 * Constructor for VariableByteStreamTest.
	 * @param arg0
	 */
	public VariableByteStreamTest(String arg0) {
		super(arg0);
	}

	protected void setUp() {
		System.out.println("generating 1000 longs ...");
		Random rand = new Random(System.currentTimeMillis());
		Random rand2 = new Random();
		boolean useLong;
		for(int i = 0; i < 1000; i++) {
			useLong = rand2.nextBoolean();
			if(useLong)
				longValues[i] = rand.nextLong() & 0x7fffffffffffffffL;
			else
				longValues[i] = rand.nextInt() & 0x7fffffff;
			System.out.println(longValues[i]);
		}
	}
	
	public void testInOut() {
		VariableByteOutputStream os = new VariableByteOutputStream();
		for(int i = 0; i < 1000; i++)
			os.writeLong(longValues[i]);
		byte[] data = os.toByteArray();
		System.out.println("data length: " + data.length);
		
		VariableByteInputStream is = new VariableByteInputStream(data);
		long l;
		int i;
		try {
			for(int j = 0; j < 1000; j++) {
				l = is.readLong();
				assertEquals(l, longValues[j]);
			}
		} catch (EOFException e) {
			fail("Exception: " + e);
		} catch(IOException e) {
			fail("Exception: " + e);
		}
	}
	
	public static void main(String args[]) {
		junit.textui.TestRunner.run(VariableByteStreamTest.class);
	}
}
