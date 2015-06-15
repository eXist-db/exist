package org.exist.xquery.value;

import java.net.URI;

import org.exist.test.TestConstants;
import org.exist.xquery.XPathException;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author cgeorg
 */
public class AnyURITest {

	@Test
    public void fullyEscapedStringToXmldbURI() throws XPathException {
        String escaped = TestConstants.SPECIAL_NAME;
        AnyURIValue anyUri = new AnyURIValue(escaped);
        assertEquals(anyUri.toXmldbURI(),TestConstants.SPECIAL_URI);
    }

    @Test
    public void fullyEscapedStringToURI() throws XPathException {
        URI uri = TestConstants.SPECIAL_URI.getXmldbURI();
        String escaped = TestConstants.SPECIAL_NAME;
        AnyURIValue anyUri = new AnyURIValue(escaped);
        assertEquals(anyUri.toURI(),uri);
    }

    /**
     * TODO: change AnyURIValue to directly store the escaped value?
     */
    @Ignore
    @Test
    public void partiallyEscapedStringToXmldbURI() throws XPathException {
        String escaped = TestConstants.SPECIAL_NAME.replaceAll("%20"," ").replaceAll("%C3%A0","\u00E0");
        AnyURIValue anyUri = new AnyURIValue(escaped);
        assertEquals(anyUri.toXmldbURI(), TestConstants.SPECIAL_URI);
    }

    @Test
    public void partiallyEscapedStringToURI() throws XPathException {
        URI uri = TestConstants.SPECIAL_URI.getXmldbURI();
        String escaped = TestConstants.SPECIAL_NAME.replaceAll("%20"," ").replaceAll("%C3%A0","\u00E0");
        AnyURIValue anyUri = new AnyURIValue(escaped);
        assertEquals(anyUri.toURI(),uri);
    }
}
