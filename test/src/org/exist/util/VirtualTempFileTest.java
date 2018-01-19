/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2011 The eXist Project
 *  http://exist.sourceforge.net
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package org.exist.util;

import com.googlecode.junittoolbox.ParallelRunner;
import org.apache.commons.io.output.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import org.junit.Test;
import org.junit.Assert;
import org.junit.runner.RunWith;

import static java.nio.charset.StandardCharsets.UTF_8;

/*
 * VirtualTempFileTest.java
 * A test battery for the VirtualTempFile class
 * 
 * @author jmfernandez
 */
@RunWith(ParallelRunner.class)
public class VirtualTempFileTest {
	@Test
	public void testWriteSwitch()
		throws IOException
	{
		byte[] testString = "teststring".getBytes(UTF_8);
		int testStringLength = testString.length;
		
		// Test1, no temp file
		VirtualTempFile vtempFile = new VirtualTempFile(testStringLength+100,testStringLength+100);
		
		vtempFile.write(testString,0,testStringLength);
		vtempFile.close();
		
		Assert.assertFalse(vtempFile.getContent() instanceof File);
		
		// Test2, temp file
		vtempFile = new VirtualTempFile(testStringLength-3,testStringLength-3);
		
		vtempFile.write(testString,0,testStringLength);
		vtempFile.close();
		
		Assert.assertTrue(vtempFile.getContent() instanceof File);
		
		// Test3, no temp file just at the limit
		vtempFile = new VirtualTempFile(testStringLength,testStringLength);
		
		vtempFile.write(testString,0,testStringLength);
		vtempFile.close();
		
		Assert.assertFalse(vtempFile.getContent() instanceof File);
	}
	
	@Test
	public void testLength()
		throws IOException
	{
		byte[] testString = "teststring".getBytes(UTF_8);
		byte[] testString2 = "teststring2".getBytes(UTF_8);
		int testStringLength = testString.length;
		
		// Test1, no temp file
		VirtualTempFile vtempFile = new VirtualTempFile(testStringLength+100,testStringLength+100);
		
		vtempFile.write(testString,0,testStringLength);
		vtempFile.close();
		
		Assert.assertEquals("Length must match",testStringLength,vtempFile.length());
		
		// Test2, temp file
		vtempFile = new VirtualTempFile(testStringLength-3,testStringLength-3);
		
		vtempFile.write(testString,0,testStringLength);
		vtempFile.close();
		
		Assert.assertEquals("Length must match",testStringLength,vtempFile.length());
		
		// Test3, several writes
		vtempFile = new VirtualTempFile(testStringLength,testStringLength);
		
		vtempFile.write(testString,0,testStringLength);
		vtempFile.write(testString2,0,testString2.length);
		vtempFile.close();
		
		Assert.assertEquals("Length must match",testStringLength+testString2.length,vtempFile.length());
	}
	
	@Test
	public void testCompare()
		throws IOException
	{
		byte[] testString = "teststring".getBytes(UTF_8);
		byte[] testString2 = "teststring2".getBytes(UTF_8);
		int testStringLength = testString.length;
		
		// Test1, no temp file
		VirtualTempFile vtempFile = new VirtualTempFile(testStringLength+100,testStringLength+100);
		
		vtempFile.write(testString,0,testStringLength);
		vtempFile.close();
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		vtempFile.writeToStream(baos);
		baos.close();
		
		Assert.assertArrayEquals("Written content must match",testString,baos.toByteArray());
		
		// Test2, temp file
		vtempFile = new VirtualTempFile(testStringLength-3,testStringLength-3);
		
		vtempFile.write(testString,0,testStringLength);
		vtempFile.close();
		
		baos = new ByteArrayOutputStream();
		vtempFile.writeToStream(baos);
		baos.close();
		
		Assert.assertArrayEquals("Written content must match",testString,baos.toByteArray());
		
		// Test3, several writes
		vtempFile = new VirtualTempFile(testStringLength,testStringLength);
		
		vtempFile.write(testString,0,testStringLength);
		vtempFile.write(testString2,0,testString2.length);
		vtempFile.close();
		
		baos = new ByteArrayOutputStream();
		vtempFile.writeToStream(baos);
		baos.close();
		
		byte[] joinedTestString = new byte[testStringLength+testString2.length];
		System.arraycopy(testString,0,joinedTestString,0,testStringLength);
		System.arraycopy(testString2,0,joinedTestString,testStringLength,testString2.length);
		
		Assert.assertArrayEquals("Written content must match",joinedTestString,baos.toByteArray());
	}
}
