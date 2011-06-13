package org.exist.util.serializer;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.isNull;
import static org.easymock.EasyMock.matches;
import static org.easymock.classextension.EasyMock.createMock;
import static org.easymock.classextension.EasyMock.replay;
import static org.easymock.classextension.EasyMock.verify;

import java.io.OutputStream;
import java.util.List;

import junit.framework.Assert;

import org.easymock.Capture;
import org.exist.dom.QName;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.xml.sax.Attributes;

import com.siemens.ct.exi.api.sax.SAXEncoder;

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
		Capture<Attributes> capturedAttributes = new Capture<Attributes>();
		mockEncoder.startElement(matches("uri"), matches("local"), (String)isNull(), capture(capturedAttributes));
		replay(mockEncoder);
		serializer.startElement(testQName, testAttrList);
		verify(mockEncoder);
		List<Attributes> capturedAttributeList = capturedAttributes.getValues();
		Assert.assertEquals("local", capturedAttributeList.get(0).getLocalName(0));
		Assert.assertEquals("uri", capturedAttributeList.get(0).getURI(0));
		Assert.assertEquals("value", capturedAttributeList.get(0).getValue(0));	
	}
	
	@Ignore("incomplete")
	@Test
	public void testEndElement() {
		// TODO
	}
	
	@Ignore("incomplete")
	@Test
	public void testCharacters() {
		// TODO
	}
	
}