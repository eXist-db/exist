package org.exist.util.serializer;

import java.io.OutputStream;
import java.util.List;

import junit.framework.Assert;

import org.easymock.Capture;
import org.exist.dom.QName;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.Attributes;

import com.siemens.ct.exi.api.sax.SAXEncoder;

import static org.easymock.EasyMock.*;

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
		Capture<Attributes> capturedAttributes = newCapture();
		mockEncoder.startElement(matches("uri"), matches("local"), (String)isNull(), capture(capturedAttributes));
		replay(mockEncoder);
		serializer.startElement(testQName, testAttrList);
		verify(mockEncoder);
		List<Attributes> capturedAttributeList = capturedAttributes.getValues();
		Assert.assertEquals("local", capturedAttributeList.get(0).getLocalName(0));
		Assert.assertEquals("uri", capturedAttributeList.get(0).getURI(0));
		Assert.assertEquals("value", capturedAttributeList.get(0).getValue(0));	
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