package org.exist.storage.io;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Random;

import static org.junit.Assert.assertEquals;

public class VariableByteStreamTest {

    private final static int SIZE = 1000;
    
	private long[] values = new long[1000 * 3];

	@Before
	public void setUp() {
		Random rand = new Random(System.currentTimeMillis()); 
		for(int i = 0; i < SIZE * 3; i++) {
			values[i++] = rand.nextInt();
			values[i++] = rand.nextInt() & 0xffffff;
			values[i] = rand.nextInt() & 0xff;
		}
	}

	@Test
	public void inOutLong() throws IOException {
		VariableByteOutputStream os = new VariableByteOutputStream();
		for(int i = 0; i < SIZE * 3; i++) {
			os.writeLong(values[i++]);
			os.writeInt((int)values[i++]);
			os.writeShort((short)values[i]);
		}
		byte[] data = os.toByteArray();
		
		VariableByteArrayInput is = new VariableByteArrayInput(data);
		long l;
		short s;
		int i;
		for(int j = 0; j < SIZE * 3; j++) {
			l = is.readLong();
			assertEquals(l, values[j++]);
			i = is.readInt();
			assertEquals(i, values[j++]);
			s = is.readShort();
			assertEquals(s, values[j]);
		}
	}

	@Test
	public void copyTo() throws IOException {
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
	}
}
