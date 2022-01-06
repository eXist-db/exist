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
package org.exist.contentextraction;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.exist.util.serializer.Receiver;
import org.exist.util.serializer.SAXToReceiver;
import org.exist.xquery.value.BinaryValue;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * @author <a href="mailto:dulip.withanage@gmail.com">Dulip Withanage</a>
 * @version 1.0
 */
public class ContentExtraction {
    final AutoDetectParser parser = new AutoDetectParser();

    public Metadata extractContentAndMetadata(final BinaryValue binaryValue, final ContentHandler contentHandler) throws IOException, SAXException, ContentExtractionException {
        try (final InputStream is = binaryValue.getInputStream()) {
            final Metadata metadata = new Metadata();
            parser.parse(is, contentHandler, metadata);
            return metadata;
        } catch (final TikaException e) {
            throw new ContentExtractionException("Problem with content extraction library: " + e.getMessage(), e);
        }
    }

    public void extractContentAndMetadata(final BinaryValue binaryValue, final Receiver receiver)
            throws IOException, SAXException, ContentExtractionException {
        extractContentAndMetadata(binaryValue, new SAXToReceiver(receiver, false));
    }

    public Metadata extractMetadata(final BinaryValue binaryValue) throws IOException, SAXException, ContentExtractionException {
        try (final InputStream is = binaryValue.getInputStream()) {
            final Metadata metadata = new Metadata();
            parser.parse(is, null, metadata);
            return metadata;
        } catch (final TikaException e) {
            throw new ContentExtractionException("Problem with content extraction library: " + e.getMessage(), e);
        }
    }
}