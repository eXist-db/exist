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
package org.exist.resolver;

import org.exist.util.sax.event.contenthandler.Characters;
import org.exist.util.sax.event.contenthandler.ContentHandlerEvent;
import org.exist.util.sax.event.contenthandler.EndDocument;
import org.exist.util.sax.event.contenthandler.EndElement;
import org.exist.util.sax.event.contenthandler.EndPrefixMapping;
import org.exist.util.sax.event.contenthandler.IgnorableWhitespace;
import org.exist.util.sax.event.contenthandler.ProcessingInstruction;
import org.exist.util.sax.event.contenthandler.SetDocumentLocator;
import org.exist.util.sax.event.contenthandler.SkippedEntity;
import org.exist.util.sax.event.contenthandler.StartDocument;
import org.exist.util.sax.event.contenthandler.StartElement;
import org.exist.util.sax.event.contenthandler.StartPrefixMapping;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.util.ArrayList;
import java.util.List;

/**
 * Records every {@link ContentHandler} callback it receives as a {@link ContentHandlerEvent}, so
 * they can later be {@link #replay(ContentHandler) replayed} against a different {@link
 * ContentHandler} -- used by {@link
 * ResolverFactory#catalogSaxProducer(org.exist.storage.DBBroker, org.exist.xmldb.XmldbURI)} to
 * avoid a second broker round-trip when the xmlresolver catalog loader asks for the same
 * document's SAX events twice.
 */
final class RecordingContentHandler implements ContentHandler {

    private final List<ContentHandlerEvent> events = new ArrayList<>();

    boolean hasRecording() {
        return !events.isEmpty();
    }

    void replay(final ContentHandler target) throws SAXException {
        for (final ContentHandlerEvent event : events) {
            event.apply(target);
        }
    }

    @Override
    public void setDocumentLocator(final Locator locator) {
        events.add(new SetDocumentLocator(locator));
    }

    @Override
    public void startDocument() {
        events.add(StartDocument.INSTANCE);
    }

    @Override
    public void endDocument() {
        events.add(EndDocument.INSTANCE);
    }

    @Override
    public void startPrefixMapping(final String prefix, final String uri) {
        events.add(new StartPrefixMapping(prefix, uri));
    }

    @Override
    public void endPrefixMapping(final String prefix) {
        events.add(new EndPrefixMapping(prefix));
    }

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts) {
        events.add(new StartElement(uri, localName, qName, atts));
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) {
        events.add(new EndElement(uri, localName, qName));
    }

    @Override
    public void characters(final char[] ch, final int start, final int length) {
        events.add(new Characters(ch, start, length));
    }

    @Override
    public void ignorableWhitespace(final char[] ch, final int start, final int length) {
        events.add(new IgnorableWhitespace(ch, start, length));
    }

    @Override
    public void processingInstruction(final String target, final String data) {
        events.add(new ProcessingInstruction(target, data));
    }

    @Override
    public void skippedEntity(final String name) {
        events.add(new SkippedEntity(name));
    }
}
