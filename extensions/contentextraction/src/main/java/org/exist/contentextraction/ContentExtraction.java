/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
 *  http://exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.contentextraction;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.exist.util.serializer.Receiver;
import org.exist.util.serializer.SAXToReceiver;
import org.exist.xquery.value.BinaryValue;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;



/**
 * @author Dulip Withanage <dulip.withanage@gmail.com>
 * @version 1.0
 */
public class ContentExtraction {

    final Parser parser = new AutoDetectParser();
    final ParseContext parseContext = new ParseContext();

    public ContentExtraction() {
        parseContext.set(Parser.class, parser);
    }

    public void extractContentAndMetadata(BinaryValue binaryValue, ContentHandler contentHandler) throws IOException, SAXException, ContentExtractionException {
        
        Metadata metadata = new Metadata();

        try (InputStream is = binaryValue.getInputStream()) {
            parser.parse(is, contentHandler, metadata, parseContext);
            
        } catch (TikaException e) {
            throw new ContentExtractionException("Problem with content extraction library: " + e.getMessage(), e);
        }
    }

    public void extractContentAndMetadata(BinaryValue binaryValue, Receiver receiver) 
            throws IOException, SAXException, ContentExtractionException {
        
        extractContentAndMetadata(binaryValue, new SAXToReceiver(receiver, false));
    }

    public void extractMetadata(BinaryValue binaryValue, ContentHandler contentHandler) throws IOException, SAXException, ContentExtractionException {
        Metadata metadata = new Metadata();

        try (InputStream is = binaryValue.getInputStream()) {
            parser.parse(is, new AbortAfterMetadataContentHandler(contentHandler), metadata, parseContext);
            
        } catch (TikaException e) {
            throw new ContentExtractionException("Problem with content extraction library: " + e.getMessage(), e);
            
        } catch (AbortedAfterMetadataException ame) {
            //do nothing, we have finished with the document
        }
    }
}