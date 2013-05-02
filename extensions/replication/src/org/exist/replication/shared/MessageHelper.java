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
package org.exist.replication.shared;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Map;
import java.util.Properties;
import java.util.zip.GZIPOutputStream;
import javax.xml.transform.OutputKeys;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.log4j.Logger;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentMetadata;
import org.exist.security.Permission;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.xml.sax.SAXException;

/**
 *  Helper class for retrieving (meta)data from an in eXist stored document.
 * 
 * @author Dannes Wessels
 */
public class MessageHelper {
    
    public static final String EXIST_RESOURCE_CONTENTLENGTH = "exist.resource.contentlength";
    public static final String EXIST_RESOURCE_DOCUMENTID = "exist.resource.documentid";
    public static final String EXIST_RESOURCE_GROUP = "exist.resource.group";
    public static final String EXIST_RESOURCE_MIMETYPE = "exist.resource.mimetype";
    public static final String EXIST_RESOURCE_OWNER = "exist.resource.owner";
    public static final String EXIST_RESOURCE_TYPE = "exist.resource.type";
    public static final String EXIST_RESOURCE_MODE = "exist.resource.permission.mode";
    public static final String EXIST_MESSAGE_CONTENTENCODING = "exist.message.content-encoding";

    private final static Logger LOG = Logger.getLogger(MessageHelper.class);
    
    //	Copied from webdav interface ; there is a better one
    public final static Properties OUTPUT_PROPERTIES = new Properties();

    static {
        OUTPUT_PROPERTIES.setProperty(OutputKeys.INDENT, "yes");
        OUTPUT_PROPERTIES.setProperty(OutputKeys.ENCODING, "UTF-8");
        OUTPUT_PROPERTIES.setProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        OUTPUT_PROPERTIES.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, "no");
        OUTPUT_PROPERTIES.setProperty(EXistOutputKeys.PROCESS_XSL_PI, "no");
    }
    

    /**
     *  Serialize document to byte array as gzipped document.
     * 
     * @param broker
     * @param document
     * @return
     * @throws IOException 
     */
    public static byte[] gzipSerialize(DBBroker broker, DocumentImpl document) throws IOException {
        
        // This is the weak spot, the data is serialized into
        // a byte array. Better to have an overloap to a file,
        byte[] payload;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPOutputStream gos = new GZIPOutputStream(baos);


        if (document.getResourceType() == DocumentImpl.XML_FILE) {

            // Stream XML document
            Serializer serializer = broker.getSerializer();
            serializer.reset();
            try {
                serializer.setProperties(OUTPUT_PROPERTIES);

                Writer w = new OutputStreamWriter(gos, "UTF-8");
                serializer.serialize(document, w);
                w.flush();
                w.close();

                payload = baos.toByteArray();


            } catch (SAXException e) {
                payload = new byte[0];
                LOG.error(e);
                throw new IOException("Error while serializing XML document: " + e.getMessage(), e);

            } catch (Throwable e) {
                payload = new byte[0];
                System.gc(); // recover from out of memory exception
                LOG.error(e);
                throw new IOException("Error while serializing XML document: " + e.getMessage(), e);
            }

        } else {
            // Stream NON-XML document

            try {
                broker.readBinaryResource((BinaryDocument) document, gos);
                gos.flush();
                gos.close();

                payload = baos.toByteArray();

            } catch (IOException e) {
                payload = new byte[0];
                LOG.error(e);
                throw new IOException("Error while serializing binary document: " + e.getMessage(), e);

            } catch (Throwable e) {
                payload = new byte[0];
                System.gc(); // recover from out of memory exception
                LOG.error(e);
                throw new IOException("Error while serializing binary document: " + e.getMessage(), e);
            }
        }


        return payload;

    }

    public static void retrieveDocMetadata(Map<String, Object> props, DocumentMetadata docMetadata) {
        if (docMetadata == null) {
            LOG.error("no metadata supplied");

        } else {
            props.put(EXIST_RESOURCE_MIMETYPE, docMetadata.getMimeType()); 
        }
    }
    
    public static void retrievePermission(Map<String, Object> props, Permission perm){
            if (perm == null) {
                LOG.error("no permissions supplied");
                
            } else {
                props.put(EXIST_RESOURCE_OWNER, perm.getOwner().getName());
                props.put(EXIST_RESOURCE_GROUP, perm.getGroup().getName());
                props.put(EXIST_RESOURCE_MODE, perm.getMode());
            }
    }
    
    
    public static void retrieveFromDocument(Map<String, Object> props, DocumentImpl document){
            // We do not differ between DOCUMENT subtypes,
	        // mime-type is set in document metadata EXIST_RESOURCE_MIMETYPE. /ljo
            props.put(EXIST_RESOURCE_TYPE, eXistMessage.ResourceType.DOCUMENT); 
            props.put(EXIST_RESOURCE_DOCUMENTID, document.getDocId()); 
            props.put(EXIST_RESOURCE_CONTENTLENGTH, document.getContentLength()); 
        
    }
}
