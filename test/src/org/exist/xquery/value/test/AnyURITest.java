package org.exist.xquery.value.test;

import java.net.URI;

import junit.framework.TestCase;

import org.exist.test.TestConstants;
import org.exist.xquery.test.ValueIndexByQNameTest;
import org.exist.xquery.value.AnyURIValue;

/**
 *
 * @author cgeorg
 */
public class AnyURITest extends TestCase {

    public static void main(String[] args) {
        junit.textui.TestRunner.run(ValueIndexByQNameTest.class);
    }

    public void testFullyEscapedStringToXmldbURI() {
    	try {
	    	String escaped = TestConstants.SPECIAL_NAME;
	    	AnyURIValue anyUri = new AnyURIValue(escaped);
	    	assertEquals(anyUri.toXmldbURI(),TestConstants.SPECIAL_URI);
    	} catch(Exception e) {
    		fail(e.toString());
    	}
    }

    public void testFullyEscapedStringToURI() {
    	try {
    		URI uri = TestConstants.SPECIAL_URI.getXmldbURI();
	    	String escaped = TestConstants.SPECIAL_NAME;
	    	AnyURIValue anyUri = new AnyURIValue(escaped);
	    	assertEquals(anyUri.toURI(),uri);
    	} catch(Exception e) {
    		fail(e.toString());
    	}
    }

    public void testPartiallyEscapedStringToXmldbURI() {
    	try {
	    	String escaped = TestConstants.SPECIAL_NAME.replaceAll("%20"," ").replaceAll("%C3%A0","\u00E0");
	    	AnyURIValue anyUri = new AnyURIValue(escaped);
	    	assertEquals(anyUri.toXmldbURI(),TestConstants.SPECIAL_URI);
       	} catch(Exception e) {
    		fail(e.toString());
    	}
    }

    public void testPartiallyEscapedStringToURI() {
    	try {
    		URI uri = TestConstants.SPECIAL_URI.getXmldbURI();
	    	String escaped = TestConstants.SPECIAL_NAME.replaceAll("%20"," ").replaceAll("%C3%A0","\u00E0");
	    	AnyURIValue anyUri = new AnyURIValue(escaped);
	    	assertEquals(anyUri.toURI(),uri);
    	} catch(Exception e) {
    		fail(e.toString());
    	}
    }

}
