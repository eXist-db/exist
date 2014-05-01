/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2010 The eXist Project
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

package org.exist.validation.internal.node;

import java.io.IOException;
import java.util.Properties;

import javax.xml.transform.OutputKeys;

import org.apache.log4j.Logger;
import org.exist.storage.io.BlockingOutputStream;
import org.exist.storage.serializers.Serializer;
import org.exist.xquery.value.NodeValue;

/**
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class NodeSerializerThread extends Thread{
    
    private final static Logger logger = Logger.getLogger(NodeSerializerThread.class);
    
    private Serializer serializer;
    private NodeValue node;
    private BlockingOutputStream bos;
    
    /**
     * Creates a new instance of NodeSerializerThread
     */
    public NodeSerializerThread(Serializer serializer, NodeValue node, BlockingOutputStream bos) {
        this.serializer=serializer;
        this.node=node;
        this.bos=bos;
    }
    
    /**
     * Write resource to the output stream.
     */
    public void run() {
        logger.debug("Thread started." );
        IOException exception=null;
        try {
            //parse serialization options
            final Properties outputProperties = new Properties();
            outputProperties.setProperty(OutputKeys.INDENT, "yes");
            outputProperties.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            
            NodeSerializer.serialize(serializer, node, outputProperties, bos);
            
        } catch (IOException ex) {
            logger.error(ex);
            exception = ex;
            
        } finally {
            try { // NEEDED!
                bos.close(exception);
            } catch (final IOException ex) {
                logger.debug(ex);
            }
            logger.debug("Thread stopped." );
        }
    }
    
    
}
