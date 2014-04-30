/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2007 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 * 
 *  $Id$
 *  
 *  @author Pierrick Brihaye <pierrick.brihaye@free.fr>
 */
package org.exist.indexing.spatial;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.WKBReader;
import com.vividsolutions.jts.io.WKBWriter;
import com.vividsolutions.jts.io.WKTReader;
import com.vividsolutions.jts.io.WKTWriter;
import org.apache.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.dom.*;
import org.exist.indexing.AbstractStreamListener;
import org.exist.indexing.Index;
import org.exist.indexing.IndexController;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.MatchListener;
import org.exist.indexing.StreamListener;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.storage.IndexSpec;
import org.exist.storage.NodePath;
import org.exist.storage.txn.Txn;
import org.exist.util.Base64Decoder;
import org.exist.util.Base64Encoder;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.Occurrences;
import org.exist.util.serializer.Receiver;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.ValueSequence;
import org.geotools.geometry.jts.GeometryCoordinateSequenceTransformer;
import org.geotools.gml.GMLFilterDocument;
import org.geotools.gml.GMLFilterGeometry;
import org.geotools.gml.GMLHandlerJTS;
import org.geotools.gml.producer.GeometryTransformer;
import org.geotools.referencing.CRS;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.OperationNotFoundException;
import org.opengis.referencing.operation.TransformException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.XMLFilterImpl;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;

public abstract class AbstractGMLJDBCIndexWorker implements IndexWorker {

    public static final String GML_NS = "http://www.opengis.net/gml";
    //The general configuration's element name to configure this kind of worker
    protected final static String INDEX_ELEMENT = "gml";
    
    public static final String START_KEY = "start_key";
    public static final String END_KEY = "end_key";
    
    private static final Logger LOG = Logger.getLogger(AbstractGMLJDBCIndexWorker.class);

    protected IndexController controller;
    protected AbstractGMLJDBCIndex index;
    protected DBBroker broker;
    protected int currentMode = StreamListener.UNKNOWN;
    protected DocumentImpl currentDoc = null;  
    private boolean isDocumentGMLAware = false;
    protected Map<NodeId, SRSGeometry> geometries = new TreeMap<NodeId, SRSGeometry>();
    NodeId currentNodeId = null;
    Geometry streamedGeometry = null;
    boolean documentDeleted = false;
    int flushAfter = -1;
    protected GMLHandlerJTS geometryHandler = new GeometryHandler(); 
    protected GMLFilterGeometry geometryFilter = new GMLFilterGeometry(geometryHandler); 
    protected GMLFilterDocument geometryDocument = new GMLFilterDocument(geometryFilter);
    protected GMLStreamListener gmlStreamListener = new GMLStreamListener();
    protected TreeMap<String, MathTransform> transformations = new TreeMap<String, MathTransform>();
    protected boolean useLenientMode = false;
    protected GeometryCoordinateSequenceTransformer coordinateTransformer = new GeometryCoordinateSequenceTransformer();
    protected GeometryTransformer gmlTransformer = new GeometryTransformer();
    protected WKBWriter wkbWriter = new WKBWriter();
    protected WKBReader wkbReader = new WKBReader();
    protected WKTWriter wktWriter = new WKTWriter();
    protected WKTReader wktReader = new WKTReader();
    protected Base64Encoder base64Encoder = new Base64Encoder();
    protected Base64Decoder base64Decoder = new Base64Decoder();

    public AbstractGMLJDBCIndexWorker(AbstractGMLJDBCIndex index, DBBroker broker) {
        this.index = index;
        this.broker = broker;
    }

    protected DBBroker getBroker() {
        return broker;
    }

    public String getIndexId() {
        return AbstractGMLJDBCIndex.ID;
    }

    public String getIndexName() {
        return index.getIndexName();
    }

    public Index getIndex() {
        return index;
    }

    public Object configure(IndexController controller, NodeList configNodes, Map<String, String> namespaces) throws DatabaseConfigurationException {
        this.controller = controller;
        Map<String, GMLIndexConfig> map = null;
        for(int i = 0; i < configNodes.getLength(); i++) {
            Node node = configNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE &&
                    INDEX_ELEMENT.equals(node.getLocalName())) { 
                map = new TreeMap<String, GMLIndexConfig>();
                GMLIndexConfig config = new GMLIndexConfig(namespaces, (Element)node);
                map.put(AbstractGMLJDBCIndex.ID, config);
            }
        }
        return map;
    }

    public void setDocument(DocumentImpl document) {
        isDocumentGMLAware = false;
        documentDeleted= false;
        if (document != null) {
            IndexSpec idxConf = document.getCollection().getIndexConfiguration(getBroker());
            if (idxConf != null) {
                Map collectionConfig = (Map) idxConf.getCustomIndexSpec(AbstractGMLJDBCIndex.ID);
                if (collectionConfig != null) {
                    isDocumentGMLAware = true;
                    if (collectionConfig.get(AbstractGMLJDBCIndex.ID) != null)
                        flushAfter = ((GMLIndexConfig)collectionConfig.get(AbstractGMLJDBCIndex.ID)).getFlushAfter();
                }
            }
        }
        if (isDocumentGMLAware) {
            currentDoc = document;
        } else {
            currentDoc = null;
            currentMode = StreamListener.UNKNOWN;
        }
    } 

    public void setMode(int newMode) {
        currentMode = newMode; 
    }

    public void setDocument(DocumentImpl doc, int mode) {
        setDocument(doc);
        setMode(mode);
    }

    /**
     * Returns the document for the next operation.
     * 
     * @return the document
     */
    public DocumentImpl getDocument() {
        return currentDoc;
    }

    /**
     * Returns the mode for the next operation.
     * 
     * @return the document
     */
    public int getMode() {
        return currentMode;
    }

    public StreamListener getListener() {
        //We won't listen to anything here
        if (currentDoc == null || currentMode == StreamListener.REMOVE_ALL_NODES)
            return null;
        return gmlStreamListener;
    }

    public MatchListener getMatchListener(DBBroker broker, NodeProxy proxy) {
        return null;
    }

    public StoredNode getReindexRoot(StoredNode node, NodePath path, boolean insert, boolean includeSelf) {
        if (!isDocumentGMLAware)
            //Not concerned
            return null;
        StoredNode relevantNode = null;
        StoredNode currentNode = node;
        for (int i = path.length() ; i > 0; i--) {
            if (GML_NS.equals(currentNode.getNamespaceURI()))
                relevantNode = currentNode;
            //Stop below root
            if (currentNode.getParentNode() instanceof DocumentImpl)
                break;
            currentNode = (StoredNode)currentNode.getParentNode();
        }
        return relevantNode;
    }

    public void flush() {
        if (!isDocumentGMLAware)
            //Not concerned
            return;
        //Is the job already done ?
        if (currentMode == StreamListener.REMOVE_ALL_NODES && documentDeleted)
            return;
        Connection conn = null;
        try {
            conn = acquireConnection();
            conn.setAutoCommit(false);
            switch (currentMode) {
                case StreamListener.STORE :
                    saveDocumentNodes(conn);
                    break;
                case StreamListener.REMOVE_SOME_NODES :
                    dropDocumentNode(conn);
                    break;
                case StreamListener.REMOVE_ALL_NODES:
                    removeDocument(conn);
                    documentDeleted = true;
                    break;
            }
            conn.commit();
        } catch (SQLException e) {
            LOG.error("Document: " + currentDoc + " NodeID: " + currentNodeId, e);
            try {
                if (conn != null)
                    conn.rollback();
            } catch (SQLException ee) {
                LOG.error(ee);
            }
        } finally {
            try {
                if (conn != null) {
                    conn.setAutoCommit(true);
                    releaseConnection(conn);
                    //geometries.clear();
                }
            } catch (SQLException e) {
                LOG.error(e);
            }
        }
    }

    private void saveDocumentNodes(Connection conn) throws SQLException {
        if (geometries.size() == 0)
            return;

        PreparedStatement ps = conn.prepareStatement("INSERT INTO " + GMLHSQLIndex.TABLE_NAME + "(" +
                /*1*/ "DOCUMENT_URI, " +
                /*2*/ "NODE_ID_UNITS, " +
                /*3*/ "NODE_ID, " +
                /*4*/ "GEOMETRY_TYPE, " +
                /*5*/ "SRS_NAME, " +
                /*6*/ "WKT, " +
                /*7*/ "WKB, " +
                /*8*/ "MINX, " +
                /*9*/ "MAXX, " +
                /*10*/ "MINY, " +
                /*11*/ "MAXY, " +
                /*12*/ "CENTROID_X, " +
                /*13*/ "CENTROID_Y, " +
                /*14*/ "AREA, " +
                //Boundary ?
                /*15*/ "EPSG4326_WKT, " +
                /*16*/ "EPSG4326_WKB, " +
                /*17*/ "EPSG4326_MINX, " +
                /*18*/ "EPSG4326_MAXX, " +
                /*19*/ "EPSG4326_MINY, " +
                /*20*/ "EPSG4326_MAXY, " +
                /*21*/ "EPSG4326_CENTROID_X, " +
                /*22*/ "EPSG4326_CENTROID_Y, " +
                /*23*/ "EPSG4326_AREA," +
                //Boundary ?
                /*24*/ "IS_CLOSED, " +
                /*25*/ "IS_SIMPLE, " +
                /*26*/ "IS_VALID" +
                ") VALUES (" +
                    "?, ?, ?, ?, ?, " +
                    "?, ?, ?, ?, ?, " +
                    "?, ?, ?, ?, ?, " +
                    "?, ?, ?, ?, ?, " +
                    "?, ?, ?, ?, ?, " +
                    "?"
                + ")"
            );

        try {
            NodeId nodeId = null;
            SRSGeometry srsGeometry = null;
        	for (Map.Entry<NodeId, SRSGeometry> entry : geometries.entrySet()) {
                nodeId = entry.getKey();
                srsGeometry = entry.getValue();
                
                try {
                    saveGeometryNode(srsGeometry.getGeometry(), srsGeometry.getSRSName(), 
                            currentDoc, nodeId, ps);
                } finally {
                    //Help the garbage collector
                    srsGeometry = null;
                }
            }
        } finally {
            geometries.clear();
            if (ps != null) {
            	ps.close();
            	ps = null;
            }
        }
    }

    private void dropDocumentNode(Connection conn) throws SQLException {
        if (currentNodeId == null)
            return;
        try {
            boolean removed = removeDocumentNode(currentDoc, currentNodeId, conn);
            if (!removed)
                LOG.error("No data dropped for node " + currentNodeId.toString() + " from GML index");
            else {
                if (LOG.isDebugEnabled())
                    LOG.debug("Dropped data for node " + currentNodeId.toString() + " from GML index");
            }
        } finally {
            currentNodeId = null;
        }
    }

    private void removeDocument(Connection conn) throws SQLException {
        if (LOG.isDebugEnabled())
            LOG.debug("Dropping GML index for document " + currentDoc.getURI());
        int nodeCount = removeDocument(currentDoc, conn);
        if (LOG.isDebugEnabled())
            LOG.debug("Dropped " + nodeCount + " nodes from GML index");
    }

    public void removeCollection(Collection collection, DBBroker broker, boolean reindex) {
        boolean isCollectionGMLAware = false;
        IndexSpec idxConf = collection.getIndexConfiguration(broker);
        if (idxConf != null) {
            Map collectionConfig = (Map) idxConf.getCustomIndexSpec(AbstractGMLJDBCIndex.ID);
            isCollectionGMLAware = (collectionConfig != null);
        }
        if (!isCollectionGMLAware)
            return;

        Connection conn = null;
        try {
            conn = acquireConnection();
            if (LOG.isDebugEnabled())
                LOG.debug("Dropping GML index for collection " + collection.getURI());
            int nodeCount = removeCollection(collection, conn);
            if (LOG.isDebugEnabled())
                LOG.debug("Dropped " + nodeCount + " nodes from GML index");
        } catch (SQLException e) {
            LOG.error(e);
        } finally {
            try {
                if (conn != null)
                    releaseConnection(conn);
            } catch (SQLException e) {
                LOG.error(e);
            }
        }
    }

    public NodeSet search(DBBroker broker, NodeSet contextSet, Geometry EPSG4326_geometry, int spatialOp)
            throws SpatialIndexException {
        Connection conn = null;
        try { 
            conn = acquireConnection();
            return search(broker, contextSet, EPSG4326_geometry, spatialOp, conn);
        } catch (SQLException e) {
            throw new SpatialIndexException(e);
        } finally {
            try {
                if (conn != null)
                    releaseConnection(conn);
            } catch (SQLException e) {
                LOG.error(e);
                return null;
            }
        }
    }

    public Geometry getGeometryForNode(DBBroker broker, NodeProxy p, boolean getEPSG4326) 
            throws  SpatialIndexException {
        Connection conn = null;
        try {
            conn = acquireConnection();
            return getGeometryForNode(broker, p, getEPSG4326, conn);
        } catch (SQLException e) {
            throw new SpatialIndexException(e);
        } finally {
            try {
                if (conn != null)
                    releaseConnection(conn);
            } catch (SQLException e) {
                LOG.error(e);
                return null;
            }
        }
    }

    protected Geometry[] getGeometriesForNodes(DBBroker broker, NodeSet contextSet, boolean getEPSG4326)
            throws SpatialIndexException {
        Connection conn = null;
        try {
            conn = acquireConnection();
            return getGeometriesForNodes(broker, contextSet, getEPSG4326, conn);
        } catch (SQLException e) {
            throw new SpatialIndexException(e);
        } finally {
            try {
                if (conn != null)
                    releaseConnection(conn);
            } catch (SQLException e) {
                LOG.error(e);
                return null;
            }
        }
    }

    public AtomicValue getGeometricPropertyForNode(XQueryContext context, NodeProxy p, String propertyName)
            throws  SpatialIndexException {
        Connection conn = null;
        try {
            conn = acquireConnection();
            return getGeometricPropertyForNode(context, p, conn, propertyName);
        } catch (SQLException e) {
            throw new SpatialIndexException(e);
        } catch (XPathException e) {
            throw new SpatialIndexException(e);
        } finally {
            try {
                if (conn != null)
                    releaseConnection(conn);
            } catch (SQLException e) {
                LOG.error(e);
                return null;
            }
        }
    }

    public ValueSequence getGeometricPropertyForNodes(XQueryContext context, NodeSet contextSet, String propertyName) 
            throws  SpatialIndexException {
        Connection conn = null;
        try {
            conn = acquireConnection();
            return getGeometricPropertyForNodes(context, contextSet, conn, propertyName);
        } catch (SQLException e) {
            throw new SpatialIndexException(e);
        } catch (XPathException e) {
            throw new SpatialIndexException(e);
        } finally {
            try {
                if (conn != null)
                    releaseConnection(conn);
            } catch (SQLException e) {
                LOG.error(e);
                return null;
            }
        }
    }

    public boolean checkIndex(DBBroker broker) {
        Connection conn = null;
        try {
            conn = acquireConnection();
            return checkIndex(broker, conn);
        } catch (SQLException e) {
            LOG.error(e);
            return false;
        } catch (SpatialIndexException e) {
            LOG.error(e);
            return false;
        } finally {
            try {
                if (conn != null)
                    releaseConnection(conn);
            } catch (SQLException e) {
                LOG.error(e);
                return false;
            }
        }
    }

    protected abstract boolean saveGeometryNode(Geometry geometry, String srsName, DocumentImpl doc, NodeId nodeId, PreparedStatement ps) throws SQLException;

    protected abstract boolean removeDocumentNode(DocumentImpl doc, NodeId nodeID, Connection conn) throws SQLException;

    protected abstract int removeDocument(DocumentImpl doc, Connection conn) throws SQLException;

    protected abstract int removeCollection(Collection collection, Connection conn) throws SQLException;

    protected abstract Map<Geometry, String> getGeometriesForDocument(DocumentImpl doc, Connection conn) throws SQLException;

    protected abstract AtomicValue getGeometricPropertyForNode(XQueryContext context, NodeProxy p, Connection conn, String propertyName) throws SQLException, XPathException;

    protected abstract ValueSequence getGeometricPropertyForNodes(XQueryContext context, NodeSet contextSet, Connection conn, String propertyName) throws SQLException, XPathException;

    protected abstract Geometry getGeometryForNode(DBBroker broker, NodeProxy p, boolean getEPSG4326, Connection conn) throws SQLException;

    protected abstract Geometry[] getGeometriesForNodes(DBBroker broker, NodeSet contextSet, boolean getEPSG4326, Connection conn) throws SQLException;

    protected abstract NodeSet search(DBBroker broker, NodeSet contextSet, Geometry EPSG4326_geometry, int spatialOp, Connection conn) throws SQLException;

    protected abstract boolean checkIndex(DBBroker broker, Connection conn) throws SQLException, SpatialIndexException;

    protected abstract Connection acquireConnection() throws SQLException;

    protected abstract void releaseConnection(Connection conn) throws SQLException;

    public Occurrences[] scanIndex(XQueryContext context, DocumentSet docs, NodeSet contextSet, Map hints) {
        //TODO : try to use contextSet
        Map<Geometry, Occurrences> occurences = new TreeMap<Geometry, Occurrences>();
        Connection conn = null;
        try {
            conn = acquireConnection();
            //Collect the (normalized) geometries for each document
            for (Iterator<DocumentImpl> iDoc = docs.getDocumentIterator(); iDoc.hasNext();) {
                DocumentImpl doc = iDoc.next();
                //TODO : check if document is GML-aware ?
                //Aggregate the occurences between different documents
                for (Map.Entry<Geometry, String> entry : getGeometriesForDocument(doc, conn).entrySet()) {
                    ///TODO : use the IndexWorker.VALUE_COUNT hint, if present, to limit the number of returned entries
                    Geometry key = entry.getKey();
                    //Do we already have an occurence for this geometry ?
                    Occurrences oc = occurences.get(key);
                    if (oc != null) {
                        //Yes : increment occurence count
                        oc.addOccurrences(oc.getOccurrences() + 1);
                        //...and reference the document
                        oc.addDocument(doc);
                    } else {
                        //No : create a new occurence with EPSG4326_WKT as "term"
                        oc = new Occurrences(entry.getValue());
                        //... with a count set to 1
                        oc.addOccurrences(1);
                        //... and reference the document
                        oc.addDocument(doc);
                        occurences.put(key, oc);
                    }
                }
            }
        } catch (SQLException e) {
            LOG.error(e);
            return null;
        } finally {
            try {
                if (conn != null)
                    releaseConnection(conn);
            } catch (SQLException e) {
                LOG.error(e);
                return null;
            }
        }
        Occurrences[] result = new Occurrences[occurences.size()];
        occurences.values().toArray(result);
        return result;
    }

    public Geometry streamNodeToGeometry(XQueryContext context, NodeValue node) throws SpatialIndexException {
        try {
            context.pushDocumentContext();
            try {
                //TODO : get rid of the context dependency
                node.toSAX(context.getBroker(), geometryDocument, null);
            } finally {
                context.popDocumentContext();
            }
        } catch (SAXException e) {
            throw new SpatialIndexException(e);
        }
        return streamedGeometry;
    }

    public Element streamGeometryToElement(Geometry geometry, String srsName, Receiver receiver) throws SpatialIndexException {       
        //YES !!!
        String gmlString = null;
        try {
            //TODO : find a way to pass
            //1) the SRS
            //2) gmlPrefix
            //3) other stuff...
            //This will possibly require some changes in GeometryTransformer
            gmlString = gmlTransformer.transform(geometry);
        } catch (TransformerException e) {
            throw new SpatialIndexException(e);
        } 

        try {
            //Copied from org.exist.xquery.functions.request.getData
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            InputSource src = new InputSource(new StringReader(gmlString));
            SAXParser parser = factory.newSAXParser();
            XMLReader reader = parser.getXMLReader();
            reader.setContentHandler((ContentHandler)receiver);
            reader.parse(src);
            Document doc = receiver.getDocument();
            return doc.getDocumentElement(); 
        } catch (ParserConfigurationException e) {
            throw new SpatialIndexException(e);
        } catch (SAXException e) {
            throw new SpatialIndexException(e);
        } catch (IOException e) {
            throw new SpatialIndexException(e);	
        }
    }

    public Geometry transformGeometry(Geometry geometry, String sourceCRS, String targetCRS) throws SpatialIndexException {
        //provisional workarounds
        if ("osgb:BNG".equalsIgnoreCase(sourceCRS.trim()))
            sourceCRS = "EPSG:27700";
        if ("osgb:BNG".equalsIgnoreCase(targetCRS.trim()))
            targetCRS = "EPSG:27700"; 
        MathTransform transform = transformations.get(sourceCRS + "_" + targetCRS);
        if (transform == null) {
            try {
                try {
                    transform = CRS.findMathTransform(CRS.decode(sourceCRS), CRS.decode(targetCRS), useLenientMode);
                } catch (OperationNotFoundException e) {
                    LOG.info(e);
                    LOG.info("Switching to lenient mode... beware of precision loss !");
                    //Last parameter set to true ; won't bail out if it can't find the Bursa Wolf parameters
                    //as it is the case in current gt2-epsg-wkt-2.4-M1.jar
                    useLenientMode = true;
                    transform = CRS.findMathTransform(CRS.decode(sourceCRS), CRS.decode(targetCRS), useLenientMode);	
                }
                transformations.put(sourceCRS + "_" + targetCRS, transform);
                LOG.debug("Instantiated transformation from '" + sourceCRS + "' to '" + targetCRS + "'");
            } catch (NoSuchAuthorityCodeException e) {
                LOG.error(e);
            } catch (FactoryException e) {
                LOG.error(e);
            }
        }
        if (transform == null) {
            throw new SpatialIndexException("Unable to get a transformation from '" + sourceCRS + "' to '" + targetCRS +"'");        		           	
        }
        coordinateTransformer.setMathTransform(transform);
        try {
        	return coordinateTransformer.transform(geometry);
        } catch (TransformException e) {
        	throw new SpatialIndexException(e);
        }
    }

    private class GMLStreamListener extends AbstractStreamListener {

        Stack<String> srsNamesStack = new Stack<String>();
        ElementImpl deferredElement;

        @Override
        public IndexWorker getWorker() {
        	return AbstractGMLJDBCIndexWorker.this;
        }
        
        @Override
        public void startElement(Txn transaction, ElementImpl element, NodePath path) { 
            if (isDocumentGMLAware) {
                //Release the deferred element if any
                if (deferredElement != null)
                    processDeferredElement();
                //Retain this element
                deferredElement = element;
            }
            //Forward the event to the next listener 
            super.startElement(transaction, element, path);
        }
        
        @Override
        public void attribute(Txn transaction, AttrImpl attrib, NodePath path) { 
            //Forward the event to the next listener 
            super.attribute(transaction, attrib, path);
        }

        @Override
        public void characters(Txn transaction, CharacterDataImpl text, NodePath path) {
            if (isDocumentGMLAware) {
                //Release the deferred element if any
                if (deferredElement != null)
                    processDeferredElement();
                try {
                    geometryDocument.characters(text.getData().toCharArray(), 0, text.getLength());
                } catch (Exception e) {
                    LOG.error(e);
                }
            }
            //Forward the event to the next listener 
            super.characters(transaction, text, path);
        }

        @Override
        public void endElement(Txn transaction, ElementImpl element, NodePath path) {
            if (isDocumentGMLAware) {
                //Release the deferred element if any
                if (deferredElement != null)
                    processDeferredElement();
                //Process the element 
                processCurrentElement(element);
            }
            //Forward the event to the next listener 
            super.endElement(transaction, element, path);
        }
        
        private void processDeferredElement() {
            //We need to collect the deferred element's attributes in order to feed the SAX handler
            AttributesImpl attList = new AttributesImpl();
            NamedNodeMap attrs = deferredElement.getAttributes();

            String whatToPush = null;

            for (int i = 0; i < attrs.getLength() ; i++) {
                AttrImpl attrib = (AttrImpl)attrs.item(i);

                //Store the srs
                if (GML_NS.equals(deferredElement.getNamespaceURI())) {
                    //Maybe we could assume a configurable default value here
                    if (attrib.getName().equals("srsName")) {
                        whatToPush = attrib.getValue();
                    }
                }

                attList.addAttribute(attrib.getNamespaceURI(), 
                        attrib.getLocalName(), 
                        attrib.getQName().getStringValue(), 
                        Integer.toString(attrib.getType()), 
                        attrib.getValue());
            }

            srsNamesStack.push(whatToPush);

            try {
                geometryDocument.startElement(deferredElement.getNamespaceURI(), deferredElement.getLocalName(), deferredElement.getQName().getStringValue(), attList);
            } catch (Exception e) {
                e.printStackTrace();
                LOG.error(e);
            } finally {
                deferredElement = null;
            }
        }

        private void processCurrentElement(ElementImpl element) {
            currentNodeId = element.getNodeId();
            String currentSrsName = srsNamesStack.pop();
            try {
                geometryDocument.endElement(element.getNamespaceURI(), element.getLocalName(), element.getQName().getStringValue());
                //Some invalid/(yet) incomplete geometries don't have a SRS
                if (streamedGeometry != null && currentSrsName != null) {
                    geometries.put(currentNodeId, new SRSGeometry(currentSrsName, streamedGeometry));
                    if (flushAfter != -1 && geometries.size() >= flushAfter) {
                        //Mmmh... doesn't flush since it is currently dependant from the
                        //number of nodes in the DOM file ; would need refactorings
                        //currentDoc.getBroker().checkAvailableMemory();
                        ((AbstractGMLJDBCIndexWorker)getWorker()).getBroker().flush();
                        ///Aaaaaargl !
                        final double percent = ((double) Runtime.getRuntime().freeMemory() / (double) Runtime.getRuntime().maxMemory()) * 100;
                        if (percent < 30) {
                            System.gc();
                        }
                    }
                }
            } catch (Exception e) {
                LOG.error("Unable to collect geometry for node: " + currentNodeId + ". Indexing will be skipped", e);        		
            } finally {
                streamedGeometry = null;
            }
        }
    }

    private class GeometryHandler extends XMLFilterImpl implements GMLHandlerJTS {
        public void geometry(Geometry geometry) {
            streamedGeometry = geometry;
            //TODO : null geometries can be returned for many reasons, including a (too) strict
            //topology check done by the Geotools SAX parser.
            //It would be nice to have static classes extending Geometry to report such geometries
            if (geometry == null)
                LOG.error("Collected null geometry for node: " + currentNodeId + ". Indexing will be skipped");
        }
    }

    private class SRSGeometry {

        private String SRSName;
        private Geometry geometry;

        public SRSGeometry(String SRSName, Geometry geometry) {
            //TODO : implement a default, eventually configurable, SRS ?
            if (SRSName == null)
                throw new IllegalArgumentException("Got null SRS");
            if (geometry == null)
                throw new IllegalArgumentException("Got null geometry");
            this.SRSName = SRSName;
            this.geometry = geometry;
        }

        public String getSRSName() {
            return SRSName;
        }

        public Geometry getGeometry() {
            return geometry;
        }
    }
}
