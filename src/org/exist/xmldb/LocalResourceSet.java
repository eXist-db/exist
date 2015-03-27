/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
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
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.BinaryValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
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
            final SortedNodeSet sorted = new SortedNodeSet(brokerPool, user, sortExpr, collection.getAccessContext());
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
    public void clear() throws XMLDBException {
        //cleanup any binary values
        resources.stream().filter((resource) -> (resource instanceof BinaryValue)).forEach((resource) -> {
            try {
                ((BinaryValue) resource).close();
            } catch(final IOException ioe) {
                LOG.warn("Unable to cleanup BinaryValue: " + resource.hashCode(), ioe);
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
        final SAXSerializer handler = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
        final StringWriter writer = new StringWriter();
        handler.setOutput(writer, outputProperties);
		
        return this.<Resource>withDb((broker, transaction) -> {
            try {
                // configure the serializer
                final Serializer serializer = broker.getSerializer();
                serializer.reset();
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
                for (final Iterator<Object> i = resources.iterator(); i.hasNext(); ) {
                    current = (Item) i.next();
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
                SerializerPool.getInstance().returnObject(handler);
                return res;
            }  catch (final SAXException e) {
                throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, "serialization error", e);
            }
        });
    }

    @Override
    public Resource getResource(final long pos) throws XMLDBException {
        if (pos < 0 || pos >= resources.size()) {
            return null;
        }

        final Object r = resources.get((int) pos);

        LocalXMLResource res = null;
        if (r instanceof NodeProxy) {
            final NodeProxy p = (NodeProxy) r;
            // the resource might belong to a different collection
            // than the one by which this resource set has been
            // generated: adjust if necessary.
            LocalCollection coll = collection;
            if (p.getOwnerDocument().getCollection() == null || !coll.getPathURI().toCollectionPathURI().equals(p.getOwnerDocument().getCollection().getURI())) {
                    coll = new LocalCollection(user, brokerPool, null, p.getOwnerDocument().getCollection().getURI(), coll.getAccessContext());
                    coll.setProperties(outputProperties);
            }
            res = new LocalXMLResource(user, brokerPool, coll, p);
        } else if (r instanceof Node) {
            res = new LocalXMLResource(user, brokerPool, collection, XmldbURI.EMPTY_URI);
            res.setContentAsDOM((Node) r);
        } else if (r instanceof AtomicValue) {
            res = new LocalXMLResource(user, brokerPool, collection, XmldbURI.EMPTY_URI);
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
            return ((Item) resources.get(0)).toSequence();
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
