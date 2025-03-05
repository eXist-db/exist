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
package org.exist.xmldb;

import java.io.IOException;
import java.io.StringWriter;
import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Namespaces;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.SortedNodeSet;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.*;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

public class LocalResourceSet extends AbstractLocal implements ResourceSet {

    private final static Logger LOG = LogManager.getLogger(LocalResourceSet.class);

    private final List<Object> resources = new ArrayList<>();
    private final Properties outputProperties;

    public LocalResourceSet(final Subject user, final BrokerPool pool, final LocalCollection col, final Properties properties, final Sequence val, final String sortExpr) throws XMLDBException {
        super(user, pool, col);
        this.outputProperties = properties;

        if(val.isEmpty()) {
            return;
        }

        final Sequence seq;
        if(Type.subTypeOf(val.getItemType(), Type.NODE) && sortExpr != null) {
            final SortedNodeSet sorted = new SortedNodeSet(brokerPool, user, sortExpr);
            try {
                    sorted.addAll(val);
            } catch (final XPathException e) {
                    throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, e.getMessage(), e);
            }
            seq = sorted;
        } else {
            seq = val;
        }

        try {
            for(final SequenceIterator i = seq.iterate(); i.hasNext(); ) {
                final Item item = i.nextItem();
                resources.add(item);
            }
        } catch (final XPathException e) {
            throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, e.getMessage(), e);
        }
    }

    @Override
    public void addResource(final Resource resource) throws XMLDBException {
        resources.add(resource);
    }

    @Override
    public void addAll(final ResourceSet resourceSet) throws XMLDBException {
        for (long i = 0; i < resourceSet.getSize(); i++) {
            addResource(resourceSet.getResource(i));
        }
    }

    @Override
    public void clear() throws XMLDBException {
        //cleanup any binary values
        resources.stream().filter((resource) -> (resource instanceof BinaryValue)).forEach((resource) -> {
            try {
                ((BinaryValue) resource).close();
            } catch(final IOException ioe) {
                LOG.warn("Unable to cleanup BinaryValue: {}", resource.hashCode(), ioe);
            }
        });

        resources.clear();
    }

    @Override
    public ResourceIterator getIterator() throws XMLDBException {
        return new NewResourceIterator();
    }

    public ResourceIterator getIterator(final long start) throws XMLDBException {
        return new NewResourceIterator(start);
    }

    @Override
    public Resource getMembersAsResource() throws XMLDBException {
        return this.<Resource>withDb((broker, transaction) -> {
            final Serializer serializer = broker.borrowSerializer();
            final SAXSerializer handler = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
            final StringWriter writer = new StringWriter();
            handler.setOutput(writer, outputProperties);
            try {
                // configure the serializer
                collection.setProperty(Serializer.GENERATE_DOC_EVENTS, "false");
                serializer.setProperties(outputProperties);
                serializer.setUser(user);
                serializer.setSAXHandlers(handler, handler);

                //	serialize results
                handler.startDocument();
                handler.startPrefixMapping("exist", Namespaces.EXIST_NS);
                final AttributesImpl attribs = new AttributesImpl();
                attribs.addAttribute("", "hitCount", "hitCount", "CDATA", Integer.toString(resources.size()));
                handler.startElement(Namespaces.EXIST_NS, "result", "exist:result", attribs);
                Item current;
                char[] value;
                for (Object resource : resources) {
                    current = (Item) resource;
                    if (Type.subTypeOf(current.getType(), Type.NODE)) {
                        ((NodeValue) current).toSAX(broker, handler, outputProperties);
                    } else {
                        value = current.toString().toCharArray();
                        handler.characters(value, 0, value.length);
                    }
                }
                handler.endElement(Namespaces.EXIST_NS, "result", "exist:result");
                handler.endPrefixMapping("exist");
                handler.endDocument();

                final Resource res = new LocalXMLResource(user, brokerPool, collection, XmldbURI.EMPTY_URI);
                res.setContent(writer.toString());
                return res;
            }  catch (final SAXException e) {
                throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, "serialization error", e);
            } finally {
                SerializerPool.getInstance().returnObject(handler);
                broker.returnSerializer(serializer);
            }
        });
    }

    @Override
    public Resource getResource(final long pos) throws XMLDBException {
        if (pos < 0 || pos >= resources.size()) {
            return null;
        }

        final Object r = resources.get((int) pos);

        EXistResource res = null;
        if (r instanceof NodeProxy p) {
            // the resource might belong to a different collection
            // than the one by which this resource set has been
            // generated: adjust if necessary.
            LocalCollection coll = collection;
            if (p.getOwnerDocument().getCollection() == null || !coll.getPathURI().toCollectionPathURI().equals(p.getOwnerDocument().getCollection().getURI())) {
                    coll = new LocalCollection(user, brokerPool, null, p.getOwnerDocument().getCollection().getURI());
                    coll.setProperties(outputProperties);
            }
            res = new LocalXMLResource(user, brokerPool, coll, p);
        } else if (r instanceof Node) {
            res = new LocalXMLResource(user, brokerPool, collection, XmldbURI.EMPTY_URI);
            ((LocalXMLResource)res).setContentAsDOM((Node) r);
        } else if (r instanceof AtomicValue) {
            if(r instanceof BinaryValue) {
                final XmldbURI docId;
                if(r instanceof Base64BinaryDocument) {
                    docId = Optional.ofNullable(((Base64BinaryDocument)r).getUrl()).filter(s -> !s.isEmpty()).map(XmldbURI::create).orElse(XmldbURI.EMPTY_URI);
                } else {
                    docId = XmldbURI.EMPTY_URI;
                }
                res = new LocalBinaryResource(user, brokerPool, collection, docId);
            } else {
                res = new LocalXMLResource(user, brokerPool, collection, XmldbURI.EMPTY_URI);
            }
            res.setContent(r);
        } else if (r instanceof Resource) {
            return (Resource)r;
        }

        res.setProperties(outputProperties);

        return res;
    }

    public Sequence toSequence() {
        if (resources.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        } else if (resources.size() == 1) {
            return ((Item) resources.getFirst()).toSequence();
        } else {
            final ValueSequence s = new ValueSequence();
            for (Object resource : resources) {
                final Item item = (Item) resource;
                s.add(item);
            }
            return s;
        }
    }

    @Override
    public long getSize() throws XMLDBException {
        return resources.size();
    }

    @Override
    public void removeResource(final long pos) throws XMLDBException {
        resources.remove(pos);
    }

    class NewResourceIterator implements ResourceIterator {
        long pos = 0;

        public NewResourceIterator() {
        }

        public NewResourceIterator(final long start) {
            pos = start;
        }

        @Override
        public boolean hasMoreResources() throws XMLDBException {
            return pos < getSize();
        }

        @Override
        public Resource nextResource() throws XMLDBException {
            return getResource(pos++);
        }
    }
}
