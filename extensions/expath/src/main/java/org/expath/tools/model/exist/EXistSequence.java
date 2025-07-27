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
package org.expath.tools.model.exist;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.transform.OutputKeys;

import org.apache.commons.io.output.CloseShieldOutputStream;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.XQuerySerializer;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.SequenceIterator;
import org.expath.tools.ToolsException;
import org.expath.tools.model.Sequence;
import org.expath.tools.serial.SerialParameters;
import org.xml.sax.SAXException;

/**
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 */
public class EXistSequence implements Sequence {
 
    private final org.exist.xquery.value.Sequence sequence;
    private SequenceIterator sequenceIterator = SequenceIterator.EMPTY_ITERATOR;
    private final XQueryContext context;
    
    public EXistSequence(final org.exist.xquery.value.Sequence sequence, final XQueryContext context) throws XPathException {
        this.sequence = sequence;
        if(sequence != null) {
            this.sequenceIterator = sequence.iterate();
        }
        this.context = context;
    }
    
    @Override
    public boolean isEmpty() throws ToolsException {
        return sequence.isEmpty();
    }

    @Override
    public Sequence next() throws ToolsException {
        try {
            final Item item = sequenceIterator.nextItem();
            final org.exist.xquery.value.Sequence singleton = (org.exist.xquery.value.Sequence) item;
            return new EXistSequence(singleton, context);
        } catch (final XPathException xpe) {
            throw new ToolsException(xpe.getMessage(), xpe);
        }
    }

    @Override
    public void serialize(final OutputStream out, final SerialParameters params) throws ToolsException {
        final Properties props = params == null ? null : makeOutputProperties(params);
        props.setProperty(Serializer.GENERATE_DOC_EVENTS, "false");

        final String encoding = props.getProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
        try(final Writer writer = new OutputStreamWriter(CloseShieldOutputStream.wrap(out), encoding)) {
            final XQuerySerializer xqSerializer = new XQuerySerializer(context.getBroker(), props, writer);
            xqSerializer.serialize(sequence);
        } catch(final SAXException | IOException | XPathException e) {
            throw new ToolsException("A problem occurred while serializing the node set: " + e.getMessage(), e);
        }
    }

    /**
     * Borrowed from {@link org.expath.tools.saxon.model.SaxonSequence}
     */
    private Properties makeOutputProperties(final SerialParameters params) throws ToolsException
    {
        final Properties props = new Properties();

        setOutputKey(props, OutputKeys.METHOD,                 params.getMethod());
        setOutputKey(props, OutputKeys.MEDIA_TYPE,             params.getMediaType());
        setOutputKey(props, OutputKeys.ENCODING,               params.getEncoding());
        setOutputKey(props, OutputKeys.CDATA_SECTION_ELEMENTS, params.getCdataSectionElements());
        setOutputKey(props, OutputKeys.DOCTYPE_PUBLIC,         params.getDoctypePublic());
        setOutputKey(props, OutputKeys.DOCTYPE_SYSTEM,         params.getDoctypeSystem());
        setOutputKey(props, OutputKeys.INDENT,                 params.getIndent());
        setOutputKey(props, OutputKeys.OMIT_XML_DECLARATION,   params.getOmitXmlDeclaration());
        setOutputKey(props, OutputKeys.STANDALONE,             params.getStandalone());
        setOutputKey(props, OutputKeys.VERSION,                params.getVersion());

        return props;
    }

    private void setOutputKey(Properties props, String name, String value)
            throws ToolsException
    {
        if ( value != null ) {
            props.setProperty(name, value);
        }
    }

    private void setOutputKey(Properties props, String name, Boolean value)
            throws ToolsException
    {
        if ( value != null ) {
            props.setProperty(name, value ? "yes" : "no");
        }
    }

    private void setOutputKey(Properties props, String name, SerialParameters.Standalone value)
            throws ToolsException
    {
        if ( value != null ) {
            switch ( value ) {
                case YES:
                    props.setProperty(name, "yes");
                    break;
                case NO:
                    props.setProperty(name, "no");
                    break;
                case OMIT:
                    props.setProperty(name, "omit");
                    break;
                default:
                    throw new ToolsException("Invalid Standalone value: " + value);
            }
        }
    }

    private void setOutputKey(Properties props, String name, QName value)
            throws ToolsException
    {
        if ( value != null ) {
            if ( value.getNamespaceURI() != null && !value.getNamespaceURI().equals(XMLConstants.NULL_NS_URI) ) {
                throw new ToolsException(
                        "A QName with a non-null namespace not supported as a serialization param: {"
                                + value.getNamespaceURI() + "}" + value.getLocalPart());
            }
            props.setProperty(name, value.getLocalPart());
        }
    }

    private void setOutputKey(Properties props, String name, Iterable<QName> value)
            throws ToolsException
    {
        if ( value != null ) {
            StringBuilder buf = new StringBuilder();
            for ( QName qname : value ) {
                if ( qname.getNamespaceURI() != null ) {
                    throw new ToolsException(
                            "A QName with a non-null namespace not supported as a serialization param: {"
                                    + qname.getNamespaceURI() + "}" + qname.getLocalPart());
                }
                buf.append(qname.getLocalPart());
                buf.append(" ");
            }
            props.setProperty(name, buf.toString());
        }
    }
}
