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
package org.exist.xquery.value;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.apache.commons.codec.binary.Base64InputStream;
import org.exist.util.ConfigurationHelper;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.exist.xquery.XPathException;
import org.junit.Test;
import static org.junit.Assert.assertNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 *
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 */
public class Base64BinaryValueTypeTest {

    @Test(expected=XPathException.class)
    public void verify_invalidBase64_fails() throws XPathException {
        TestableBase64BinaryValueType base64Type = new TestableBase64BinaryValueType();
        base64Type.verifyString("=aaabbcd");
    }

    @Test(expected=XPathException.class)
    public void verify_invalidBase64_fails_2() throws XPathException {
        TestableBase64BinaryValueType base64Type = new TestableBase64BinaryValueType();
        base64Type.verifyString("frfhforlksid745323==");
    }

    @Test
    public void verify_validBase64_passes() throws XPathException {
        TestableBase64BinaryValueType base64Type = new TestableBase64BinaryValueType();
        base64Type.verifyString("aaabbcd=");
    }

    @Test
    public void verify_validBase64_passes_2() throws XPathException {
        TestableBase64BinaryValueType base64Type = new TestableBase64BinaryValueType();
        base64Type.verifyString("dGVzdCBkYXRh");
    }

    @Test
    public void verify_validBase64_passes_3() throws XPathException {
        TestableBase64BinaryValueType base64Type = new TestableBase64BinaryValueType();
        base64Type.verifyString("aaa a");
    }

    @Test
    public void verify_validBase64_passes_large_string() throws XPathException, IOException, URISyntaxException {
        Optional<Path> home = ConfigurationHelper.getExistHome();
        Path binaryFile = Paths.get(getClass().getResource("logo.jpg").toURI());

        final String base64data;
        try(final InputStream is = new Base64InputStream(Files.newInputStream(binaryFile), true, -1, null);
                final UnsynchronizedByteArrayOutputStream baos  = new UnsynchronizedByteArrayOutputStream()) {
            baos.write(is);
            base64data = baos.toString(UTF_8);
        }

        assertNotNull(base64data);

        TestableBase64BinaryValueType base64Type = new TestableBase64BinaryValueType();

        base64Type.verifyString(base64data);
    }

    public class TestableBase64BinaryValueType extends Base64BinaryValueType {
        @Override
        public void verifyString(String str) throws XPathException {
            super.verifyString(str);
        }

        @Override
        protected String formatString(String str) {
            return super.formatString(str);
        }
    }
}