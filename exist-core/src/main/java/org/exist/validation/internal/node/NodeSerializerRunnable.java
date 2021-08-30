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

package org.exist.validation.internal.node;

import java.io.IOException;
import java.util.Properties;
import javax.xml.transform.OutputKeys;

import com.evolvedbinary.j8fu.function.ConsumerE;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.io.BlockingOutputStream;
import org.exist.storage.serializers.Serializer;
import org.exist.xquery.value.NodeValue;

/**
 * Node serializer (threaded).
 *
 * @param <SR> Serializer result
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class NodeSerializerRunnable implements Runnable {
    
    private static final Logger logger = LogManager.getLogger(NodeSerializerRunnable.class);

    private final ConsumerE<ConsumerE<Serializer, IOException>, IOException> withSerializer;
    private final NodeValue node;
    private final BlockingOutputStream bos;

    /**
     * Creates a new instance of NodeSerializerRunnable.
     *
     * @param withSerializer The serializer closure.
     * @param node       The node to be serialized.
     * @param bos        Blocking outputstream.
     */
    public NodeSerializerRunnable(final ConsumerE<ConsumerE<Serializer, IOException>, IOException> withSerializer, final NodeValue node, final  BlockingOutputStream bos) {
        this.withSerializer = withSerializer;
        this.node = node;
        this.bos = bos;
    }
    
    /**
     * Write resource to the output stream.
     */
    @Override
    public void run() {
        IOException exception=null;
        try {
            //parse serialization options
            final Properties outputProperties = new Properties();
            outputProperties.setProperty(OutputKeys.INDENT, "yes");
            outputProperties.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

            withSerializer.accept(serializer ->
                NodeSerializer.serialize(serializer, node, outputProperties, bos)
            );

        } catch (final IOException ex) {
            logger.error(ex);
            exception = ex;
            
        } finally {
            try { // NEEDED!
                bos.close(exception);
            } catch (final IOException ex) {
                logger.warn(ex);
            }

        }
    }
}
