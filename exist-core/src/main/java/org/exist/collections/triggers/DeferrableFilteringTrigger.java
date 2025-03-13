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
package org.exist.collections.triggers;

import org.exist.util.sax.event.SAXEvent;
import org.exist.util.sax.event.contenthandler.*;
import org.exist.util.sax.event.lexicalhandler.*;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * DeferrableFilteringTrigger decorates a FilteringTrigger with the
 * ability to capture and defer the processing of events.
 *
 * By default all events are dispatched to 'super' unless
 * we are deferring events and then they are queued.
 * When events are realised from the deferred queue
 * they will then be dispatched to 'super', you may override
 * either {@link #applyDeferredEvents()} or one or more of the
 * _deferred methods to change this behaviour.
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public abstract class DeferrableFilteringTrigger extends FilteringTrigger {

    private boolean defer = false;
    protected Deque<SAXEvent> deferred = new ArrayDeque<>();

    public boolean isDeferring() {
        return defer;
    }

    /**
     * Controls the deferral of FilteringTrigger
     * event processing.
     *
     * If we are deferring events and this function is called
     * with 'false' then deferred events will be applied
     * by calling {@link #applyDeferredEvents()}.
     *
     * @param defer Should we defer the processing of events?
     * @throws SAXException in case of an Error
     */
    public void defer(final boolean defer) throws SAXException {
        if(this.defer && !defer) {
            applyDeferredEvents();
        }
        this.defer = defer;
    }

    @Override
    public void setDocumentLocator(final Locator locator) {
        if(defer) {
            deferred.add(new SetDocumentLocator(locator));
        } else {
            super.setDocumentLocator(locator);
        }
    }

    @Override
    public void startDocument() throws SAXException {
        if(defer) {
            deferred.add(StartDocument.INSTANCE);
        } else {
            super.startDocument();
        }
    }

    @Override
    public void endDocument() throws SAXException {
        if(defer) {
            deferred.add(EndDocument.INSTANCE);
        } else {
            super.endDocument();
        }
    }

    @Override
    public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
        if(defer) {
            deferred.add(new StartPrefixMapping(prefix, uri));
        } else {
            super.startPrefixMapping(prefix, uri);
        }
    }

    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {
        if(defer) {
            deferred.add(new EndPrefixMapping(prefix));
        } else {
            super.endPrefixMapping(prefix);
        }
    }

    @Override
    public void startElement(final String namespaceURI, final String localName, final String qname, final Attributes attributes) throws SAXException {
        if(defer) {
            deferred.add(new StartElement(namespaceURI, localName, qname, attributes));
        } else {
            super.startElement(namespaceURI, localName, qname, attributes);
        }
    }

    @Override
    public void endElement(final String namespaceURI, final String localName, final String qname) throws SAXException {
        if(defer) {
            deferred.add(new EndElement(namespaceURI, localName, qname));
        } else {
            super.endElement(namespaceURI, localName, qname);
        }
    }

    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        if(defer) {
            deferred.add(new Characters(ch, start, length));
        } else {
            super.characters(ch, start, length);
        }
    }

    @Override
    public void ignorableWhitespace(final char[] ch, final int start, final int length) throws SAXException {
        if(defer) {
            deferred.add(new IgnorableWhitespace(ch, start, length));
        } else {
            super.ignorableWhitespace(ch, start, length);
        }
    }

    @Override
    public void processingInstruction(final String target, final String data) throws SAXException {
        if(defer) {
            deferred.add(new ProcessingInstruction(target, data));
        } else {
            super.processingInstruction(target, data);
        }
    }

    @Override
    public void skippedEntity(final String name) throws SAXException {
        if(defer) {
            deferred.add(new SkippedEntity(name));
        } else {
            super.skippedEntity(name);
        }
    }

    @Override
    public void startDTD(final String name, final String publicId, final String systemId) throws SAXException {
        if(defer) {
            deferred.add(new StartDTD(name, publicId, systemId));
        } else {
            super.startDTD(name, publicId, systemId);
        }
    }

    @Override
    public void endDTD() throws SAXException {
        if(defer) {
            deferred.add(EndDTD.INSTANCE);
        } else {
            super.endDTD();
        }
    }

    @Override
    public void startEntity(final String name) throws SAXException {
        if(defer) {
            deferred.add(new StartEntity(name));
        } else {
            super.startEntity(name);
        }
    }

    @Override
    public void endEntity(final String name) throws SAXException {
        if(defer) {
            deferred.add(new EndEntity(name));
        } else {
            super.endEntity(name);
        }
    }

    @Override
    public void startCDATA() throws SAXException {
        if(defer) {
            deferred.add(StartCDATA.INSTANCE);
        } else {
            super.startCDATA();
        }
    }

    @Override
    public void endCDATA() throws SAXException {
        if(defer) {
            deferred.add(EndCDATA.INSTANCE);
        } else {
            super.endCDATA();
        }
    }

    @Override
    public void comment(final char[] ch, final int start, final int length) throws SAXException {
        if(defer) {
            deferred.add(new Comment(ch, start, length));
        } else {
            super.comment(ch, start, length);
        }
    }

    /**
     * Applies any deferred events
     * by dispatching to the appropriate _deferred method
     * @throws SAXException in case of an error
     */
    protected void applyDeferredEvents() throws SAXException {
        SAXEvent event = null;
        while((event = deferred.poll()) != null) {
            switch (event) {
                case SetDocumentLocator setDocumentLocator -> setDocumentLocator_deferred(setDocumentLocator.locator);
                case StartDocument startDocument -> startDocument_deferred();
                case EndDocument endDocument -> endDocument_deferred();
                case StartPrefixMapping startPrefixMapping ->
                        startPrefixMapping_deferred(startPrefixMapping.prefix, startPrefixMapping.uri);
                case EndPrefixMapping endPrefixMapping -> endPrefixMapping_deferred(endPrefixMapping.prefix);
                case StartElement startElement ->
                        startElement_deferred(startElement.namespaceURI, startElement.localName, startElement.qname, startElement.attributes);
                case EndElement endElement ->
                        endElement_deferred(endElement.namespaceURI, endElement.localName, endElement.qname);
                case Characters characters -> characters_deferred(characters.ch, 0, characters.ch.length);
                case IgnorableWhitespace ignorableWhitespace ->
                        ignorableWhitespace_deferred(ignorableWhitespace.ch, 0, ignorableWhitespace.ch.length);
                case ProcessingInstruction processingInstruction ->
                        processingInstruction_deferred(processingInstruction.target, processingInstruction.data);
                case SkippedEntity skippedEntity -> skippedEntity_deferred(skippedEntity.name);
                case StartDTD startDTD -> startDTD_deferred(startDTD.name, startDTD.publicId, startDTD.systemId);
                case EndDTD endDTD -> endDTD_deferred();
                case StartEntity startEntity -> startEntity_deferred(startEntity.name);
                case EndEntity endEntity -> endEntity_deferred(endEntity.name);
                case StartCDATA startCDATA -> startCDATA_deferred();
                case EndCDATA endCDATA -> endCDATA_deferred();
                case Comment comment -> comment_deferred(comment.ch, 0, comment.ch.length);
                default -> {
                }
            }
        }
    }

    //<editor-fold desc="Deferred ContentHandler">
    protected void setDocumentLocator_deferred(final Locator locator) {
        super.setDocumentLocator(locator);
    }

    protected void startDocument_deferred() throws SAXException {
        super.startDocument();
    }

    protected void endDocument_deferred() throws SAXException {
        super.endDocument();
    }

    protected void startPrefixMapping_deferred(final String prefix, final String uri) throws SAXException {
        super.startPrefixMapping(prefix, uri);
    }

    protected void endPrefixMapping_deferred(final String prefix) throws SAXException {
        super.endPrefixMapping(prefix);
    }

    protected void startElement_deferred(final String namespaceUri, final String localName, final String qName, final Attributes attrs) throws SAXException {
        super.startElement(namespaceUri, localName, qName, attrs);
    }

    protected void endElement_deferred(final String namespaceUri, final String localName, final String qName) throws SAXException {
        super.endElement(namespaceUri, localName, qName);
    }

    protected void characters_deferred(final char[] ch, final int start, final int length) throws SAXException {
        super.characters(ch, start, length);
    }

    protected void ignorableWhitespace_deferred(final char[] ch, final int start, final int length) throws SAXException {
        super.ignorableWhitespace(ch, start, length);
    }

    protected void processingInstruction_deferred(final String target, final String data) throws SAXException {
        super.processingInstruction(target, data);
    }

    protected void skippedEntity_deferred(final String name) throws SAXException {
        super.skippedEntity(name);
    }
    //</editor-fold>

    //<editor-fold desc="Deferred Lexical">
    protected void startDTD_deferred(final String name, final String publicId, final String systemId) throws SAXException {
        super.startDTD(name, publicId, systemId);
    }
    
    protected void endDTD_deferred() throws SAXException {
        super.endDTD();
    }
    
    protected void startEntity_deferred(final String name) throws SAXException {
        super.startEntity(name);
    }
    
    protected void endEntity_deferred(final String name) throws SAXException {
        super.endEntity(name);
    }
    
    protected void startCDATA_deferred() throws SAXException {
        super.startCDATA();
    }
    
    protected void endCDATA_deferred() throws SAXException {
        super.endCDATA();
    }
    
    protected void comment_deferred(final char[] ch, final int start, final int length) throws SAXException {
        super.comment(ch, start, length);
    }
    //</editor-fold>
}
