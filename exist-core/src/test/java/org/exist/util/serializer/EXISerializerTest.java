/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.util.serializer;

import static org.easymock.EasyMock.aryEq;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.isNull;
import static org.easymock.EasyMock.matches;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;

import java.io.OutputStream;
import java.util.List;

import org.easymock.Capture;
import org.exist.dom.QName;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.Attributes;

import com.siemens.ct.exi.main.api.sax.SAXEncoder;

public class EXISerializerTest {

	private EXISerializer serializer;
	private OutputStream mockOutputStream;
	private SAXEncoder mockEncoder;
	
	@Before
	public void setUp() throws Exception {
		mockOutputStream = createMock(OutputStream.class);
		serializer = new EXISerializer(mockOutputStream);
		mockEncoder = createMock(SAXEncoder.class);
		serializer.setEncoder(mockEncoder);
	}
	
	@Test
	public void testStartDocument() throws Exception {
		mockEncoder.startDocument();
		replay(mockEncoder);
		serializer.startDocument();
		verify(mockEncoder);
	}
	
	@Test
	public void testEndDocument() throws Exception {
		mockEncoder.endDocument();
		replay(mockEncoder);
		serializer.endDocument();
		verify(mockEncoder);
	}
	
	@Test
	public void testStartPrefixMapping() throws Exception {
		mockEncoder.startPrefixMapping("prefix", "uri");
		replay(mockEncoder);
		serializer.startPrefixMapping("prefix", "uri");
		verify(mockEncoder);
	}
	
	@Test
	public void testEndPrefixMapping() throws Exception {
		mockEncoder.endPrefixMapping("prefix");
		replay(mockEncoder);
		serializer.endPrefixMapping("prefix");
		verify(mockEncoder);
	}
	
	@Test
	public void testStartElement() throws Exception {
		QName testQName = new QName("local", "uri", "prefix");
		AttrList testAttrList = new AttrList();
		testAttrList.addAttribute(new QName("local", "uri"), "value");
		Capture<Attributes> capturedAttributes = Capture.newInstance();
		mockEncoder.startElement(matches("uri"), matches("local"), (String)isNull(), capture(capturedAttributes));
		replay(mockEncoder);
		serializer.startElement(testQName, testAttrList);
		verify(mockEncoder);
		List<Attributes> capturedAttributeList = capturedAttributes.getValues();
		assertEquals("local", capturedAttributeList.getFirst().getLocalName(0));
		assertEquals("uri", capturedAttributeList.getFirst().getURI(0));
		assertEquals("value", capturedAttributeList.getFirst().getValue(0));
	}
	
	@Test
	public void testEndElement() throws Exception {
		QName testQName = new QName("local", "uri", "prefix");
		mockEncoder.endElement(matches("uri"), matches("local"), (String)isNull());
		replay(mockEncoder);
		serializer.endElement(testQName);
		verify(mockEncoder);
	}
	
	@Test
	public void testCharacters() throws Exception {
		String testString = "test";
		CharSequence testSeq = testString;
		mockEncoder.characters(aryEq(testString.toCharArray()), eq(0), eq(testString.length()));
		replay(mockEncoder);
		serializer.characters(testSeq);
		verify(mockEncoder);
	}
	
}