package org.exist.storage.io;

import java.util.Random;

import junit.framework.TestCase;

public class VariableByteStreamTest extends TestCase {

    private final static int SIZE = 1000;
    
	private long[] values = new long[1000 * 3];

	/**
	 * Constructor for VariableByteStreamTest.
	 * @param arg0
	 */
	public VariableByteStreamTest(String arg0) {
		super(arg0);
	}

	protected void setUp() {
		System.out.println("generating " + (SIZE * 3) + " numbers ...");
		Random rand = new Random(System.currentTimeMillis()); 
		for(int i = 0; i < SIZE * 3; i++) {
			values[i++] = rand.nextInt();
			values[i++] = rand.nextInt() & 0xffffff;
			values[i] = rand.nextInt() & 0xff;
		}
	}
	
	public void testInOutLong() {
		VariableByteOutputStream os = new VariableByteOutputStream();
		for(int i = 0; i < SIZE * 3; i++) {
			os.writeLong(values[i++]);
			os.writeInt((int)values[i++]);
			os.writeShort((short)values[i]);
		}
		byte[] data = os.toByteArray();
		System.out.println("long data length: " + data.length + "; original: " + (SIZE * 8 + SIZE * 2 + SIZE * 4));
		
		VariableByteArrayInput is = new VariableByteArrayInput(data);
		long l;
		short s;
		int i;
		try {
			for(int j = 0; j < SIZE * 3; j++) {
				l = is.readLong();
				assertEquals(l, values[j++]);
				i = is.readInt();
				assertEquals(i, values[j++]);
				s = is.readShort();
				assertEquals(s, values[j]);
			}
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}
	
	public void testCopyTo() {
		try {
		    Random rand = new Random(System.currentTimeMillis());
		    int valuesWritten = 0;
		    int dataLen = 0;
		    VariableByteOutputStream os = new VariableByteOutputStream();
		    for(int i = 0; i < 1000; i++) {
		        int count = rand.nextInt(0xfff);
		        os.writeShort((short)count);
		        dataLen += 2;
		        for(int j = 0; j < count; j++) {
		            int next = rand.nextInt(0xff);
		            os.writeShort((short) next);
		            valuesWritten++;
		            dataLen += 2;
		        }
		    }
		    
		    byte[] data = os.toByteArray();
		    System.out.println(valuesWritten + " values written");
			System.out.println("compressed data length: " + data.length + "; original: " + dataLen);
			
			int valuesCopied = 0;
			dataLen = 0;
			VariableByteArrayInput is = new VariableByteArrayInput(data);
			os = new VariableByteOutputStream();
			while(is.available() > 0) {
			    int count = is.readShort();
			    boolean skip = rand.nextBoolean();
			    if(skip)
			        is.skip(count);
			    else {
			        os.writeShort(count);
			        is.copyTo(os, count);
			        valuesCopied += count;
			        dataLen += 2 * count + 2;
			    }
			}
			data = os.toByteArray();
			System.out.println("copied " + valuesCopied + " values; skipped " + (valuesWritten - valuesCopied));
			System.out.println("compressed data length: " + data.length + "; original: " + dataLen);
			
			int valuesRead = 0;
			is = new VariableByteArrayInput(data);
			while(is.available() > 0) {
			    int count = is.readShort();
			    for(int i = 0; i < count; i++) {
			        is.readShort();
			        valuesRead++;
			    }
			}
			assertEquals(valuesRead, valuesCopied);
		} catch (Exception e) {
			fail(e.getMessage()); 
		}		
	}
	
	public static void main(String args[]) {
		junit.textui.TestRunner.run(VariableByteStreamTest.class);
	}
}
