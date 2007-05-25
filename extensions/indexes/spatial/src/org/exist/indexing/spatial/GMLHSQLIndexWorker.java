package org.exist.indexing.spatial;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.StoredNode;
import org.exist.indexing.spatial.GMLHSQLIndex.SpatialOperator;
import org.exist.numbering.DLN;
import org.exist.numbering.NodeId;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.xmldb.XmldbURI;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.w3c.dom.Document;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.ParseException;

/**
 *
 * Each index entry maps a key (collectionId, ngram) to a list of occurrences, which has the
 * following structure:
 *
 * <pre>[docId : int, nameType: byte, occurrenceCount: int, entrySize: long, [id: NodeId, offset: int, ...]* ]</pre>
 */
public class GMLHSQLIndexWorker extends AbstractGMLJDBCIndexWorker {
	
	public final static String ID = GMLHSQLIndexWorker.class.getName();	
	
	private static final Logger LOG = Logger.getLogger(GMLHSQLIndexWorker.class);
	
    public GMLHSQLIndexWorker(GMLHSQLIndex index, DBBroker broker) {
    	super(index, broker);        
        /*
        try {
	        getConnection() = DriverManager.getConnection("jdbc:hsqldb:" + index.getDataDir() + "/" + 
					index.db_file_name_prefix + ";shutdown=true", "sa", "");
        } catch (SQLException e) {
        	LOG.error(e);
        }
        */
    }
    
    protected boolean saveGeometryNode(Geometry geometry, String srsName, DocumentImpl doc, NodeId nodeId, Connection conn) throws SQLException {
    	PreparedStatement ps = conn.prepareStatement("INSERT INTO " + GMLHSQLIndex.TABLE_NAME + "(" +
        		/*1*/ "DOCUMENT_URI, " +            		
    			//TODO : use binary format ?
        		/*2*/ "NODE_ID, " +        			
        		/*3*/ "GEOMETRY_TYPE, " +
        		/*4*/ "SRS_NAME, " +
        		/*5*/ "WKT, " +
    			//TODO : use binary format ?
        		/*6*/ "BASE64_WKB, " +
        		/*7*/ "WSG84_WKT, " +
    			//TODO : use binary format ?
        		/*8*/ "WSG84_BASE64_WKB, " +
        		/*9*/ "WSG84_MINX, " +
    			/*10*/ "WSG84_MAXX, " +
    			/*11*/ "WSG84_MINY, " +
    			/*12*/ "WSG84_MAXY, " +
    			/*13*/ "WSG84_CENTROID_X, " +
    			/*14*/ "WSG84_CENTROID_Y, " +
    			/*15*/ "WSG84_AREA" +	            		
        		") VALUES (" +
        		"?, " +
        		"?, " +
        		"?, " +
        		"?, " +
        		"?, " +
        		"?, " +
        		"?, " +
        		"?, " +          
        		"?, " +
        		"?, " +
        		"?, " +
        		"?, " +
        		"?, " +
        		"?, " +
        		"?" 	            		
        		+ ")"
            );       
    	try {
    		//Let's fallback to this during the tests :-)
    		if ("osgb:BNG".equals(srsName))
    			srsName = "EPSG:27700";
    		//TODO : use a default SRS from the config file ?
            if (srsName == null) {
        		LOG.error("Geometry has a null SRS");
        		return false;                    	
            }
            MathTransform mathTransform = getTransformToWGS84(srsName);
            if (mathTransform == null) {
        		LOG.error("Unable to get a transformation from '" + srsName + "' to 'EPSG:4326'");
        		return false;              	
            }
            coordinateTransformer.setMathTransform(mathTransform);        
            Geometry wsg84_geometry = null;
            try {
            	wsg84_geometry = coordinateTransformer.transform(geometry);
            } catch (TransformException e) {
        		LOG.error(e);
        		return false;
            }
            ps.setString(1, doc.getURI().toString());		          
            ps.setString(2, nodeId.toString());
            ps.setString(3, geometry.getGeometryType());
            ps.setString(4, srsName);
            ps.setString(5, wktWriter.write(geometry));
            base64Encoder.reset();
            base64Encoder.translate(wkbWriter.write(geometry));
            ps.setString(6, new String(base64Encoder.getCharArray()));
            ps.setString(7, wktWriter.write(wsg84_geometry));
            base64Encoder.reset();
            base64Encoder.translate(wkbWriter.write(wsg84_geometry));
            ps.setString(8, new String(base64Encoder.getCharArray()));		
        	ps.setDouble(9, wsg84_geometry.getEnvelopeInternal().getMinX());
        	ps.setDouble(10, wsg84_geometry.getEnvelopeInternal().getMaxX());
        	ps.setDouble(11, wsg84_geometry.getEnvelopeInternal().getMinY());
        	ps.setDouble(12, wsg84_geometry.getEnvelopeInternal().getMaxY());
        	ps.setDouble(13, wsg84_geometry.getCentroid().getCoordinate().x);   
        	ps.setDouble(14, wsg84_geometry.getCentroid().getCoordinate().y);  
            //wsg84_geometry.getRepresentativePoint()
        	ps.setDouble(15, wsg84_geometry.getArea());
        	return (ps.executeUpdate() == 1);
    	} finally {
        	if (ps != null)
        		ps.close();
            //Let's help the garbage collector...
        	geometry = null;
    	}    	
    }
   
    protected boolean removeDocumentNode(DocumentImpl doc, NodeId nodeID, Connection conn) throws SQLException {   
        PreparedStatement ps = conn.prepareStatement(
        		"DELETE FROM " + GMLHSQLIndex.TABLE_NAME + " WHERE DOCUMENT_URI = ? AND NODE_ID = ?;"
        	); 
        ps.setString(1, doc.getURI().toString());	   
        ps.setString(2, nodeID.toString());
        try {	 
	        return (ps.executeUpdate() == 1);
    	} finally {
    		if (ps != null)
    			ps.close();
    	}       
    }
    
    protected int removeDocument(DocumentImpl doc, Connection conn) throws SQLException {    	
    	PreparedStatement ps = conn.prepareStatement(
    		"DELETE FROM " + GMLHSQLIndex.TABLE_NAME + " WHERE DOCUMENT_URI = ?;"
    	); 
        ps.setString(1, doc.getURI().toString());
        try {
	        return ps.executeUpdate();	 
    	} finally {
    		if (ps != null)
    			ps.close();
    	}       
    }    

    protected int removeCollection(Collection collection, Connection conn) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
    		"DELETE FROM " + GMLHSQLIndex.TABLE_NAME + " WHERE SUBSTRING(DOCUMENT_URI, 1, ?) = ?;"
    	); 
        ps.setInt(1, collection.getURI().toString().length());
        ps.setString(2, collection.getURI().toString());
        try {
        	return ps.executeUpdate();
    	} finally {
    		if (ps != null)
    			ps.close();
    	}
    }
    
    protected boolean checkIndex(DBBroker broker, Connection conn) throws SQLException {
    	PreparedStatement ps = conn.prepareStatement(
	    		"SELECT * FROM " + GMLHSQLIndex.TABLE_NAME + ";"
	    	);
    	ResultSet rs = null;
    	try {
    		rs = ps.executeQuery();
	        while (rs.next()) {	        	
	        	base64Decoder.reset();
	        	base64Decoder.translate(rs.getString("BASE64_WKB"));
	        	Geometry original_geometry = wkbReader.read(base64Decoder.getByteArray());		        	
	            if (! original_geometry.equals(wktReader.read(rs.getString("WKT")))) {
	            	LOG.info("Inconsistent WKT : " + rs.getString("WKT"));
	    			return false;
	        	}		            	
	        	base64Decoder.reset();
	        	base64Decoder.translate(rs.getString("WSG84_BASE64_WKB"));
	        	Geometry wsg84_geometry = wkbReader.read(base64Decoder.getByteArray());		        	
	            if (!wsg84_geometry.equals(wktReader.read(rs.getString("WSG84_WKT")))) {
	            	LOG.info("Inconsistent WKT : " + rs.getString("WSG84_WKT"));
	    			return false;
	        	}
	            
	        	if (!original_geometry.getGeometryType().equals(rs.getString("GEOMETRY_TYPE"))) {
	        		LOG.info("Inconsistent geometry type: " + rs.getString("GEOMETRY_TYPE"));
	    			return false;
	        	}
	        	
	        	String srsName = rs.getString("SRS_NAME");
	            MathTransform mathTransform = getTransformToWGS84(srsName);
	            if (mathTransform == null) {
	        		LOG.error("Unable to get a transformation from '" + srsName + "' to 'EPSG:4326'");
	        		return false;              	
	            }
	            getCoordinateTransformer().setMathTransform(mathTransform);
	            try {
	            	if (!getCoordinateTransformer().transform(original_geometry).equals(wsg84_geometry)) {
		        		LOG.info("Transformed original geometry inconsistent with stored tranformed one");
	            		return false;
	            	}
	            } catch (TransformException e) {
	        		LOG.error(e);
	        		return false;
	            }
	
	            if (wsg84_geometry.getEnvelopeInternal().getMinX() != rs.getDouble("WSG84_MINX")) {
	            	LOG.info("Inconsistent MinX: " + rs.getString("WSG84_MINX"));
	    			return false;
	        	}
	            if (wsg84_geometry.getEnvelopeInternal().getMaxX() != rs.getDouble("WSG84_MAXX")) {
	            	LOG.info("Inconsistent MaxX: " + rs.getString("WSG84_MAXX"));
	    			return false;
	        	}
	            if (wsg84_geometry.getEnvelopeInternal().getMinY() != rs.getDouble("WSG84_MINY")) {
	            	LOG.info("Inconsistent MinY: " + rs.getString("WSG84_MINY"));
	    			return false;
	        	}
	            if (wsg84_geometry.getEnvelopeInternal().getMaxY() != rs.getDouble("WSG84_MAXY")) {
	            	LOG.info("Inconsistent MaxY: " + rs.getString("WSG84_MAXY"));
	    			return false;
	        	}
	            if (wsg84_geometry.getCentroid().getCoordinate().x != rs.getDouble("WSG84_CENTROID_X")) {
	            	LOG.info("Inconsistent X for centroid : " + rs.getString("WSG84_CENTROID_X"));
	    			return false;
	        	}
	            if (wsg84_geometry.getCentroid().getCoordinate().y != rs.getDouble("WSG84_CENTROID_Y")) {
	            	LOG.info("Inconsistent Y for centroid : " + rs.getString("WSG84_CENTROID_Y"));
	    			return false;
	        	}
	            if (wsg84_geometry.getArea() != rs.getDouble("WSG84_AREA")) {
	            	LOG.info("Inconsistent area: " + rs.getString("WSG84_AREA"));
	    			return false;
	            }
	
	            Document doc = broker.getXMLResource(XmldbURI.create(rs.getString("DOCUMENT_URI")));
	    		NodeId nodeId = new DLN(rs.getString("NODE_ID")); 
	    			           
	        	StoredNode node = broker.objectWith(new NodeProxy((DocumentImpl)doc, nodeId));
	        	if (!GMLHSQLIndexWorker.GML_NS.equals(node.getNamespaceURI())) {
	        		LOG.info("GML indexed node (" + node.getNodeId()+ ") is in the '" + 
	        				node.getNamespaceURI() + "' namespace. '" + 
	        				GMLHSQLIndexWorker.GML_NS + "' was expected !");
	        		return false;
	        	}
	        	if (!original_geometry.getGeometryType().equals(node.getLocalName())) {
	        		if ("Box".equals(node.getLocalName()) && "Polygon".equals(original_geometry.getGeometryType())) {
	        			LOG.debug("GML indexed node (" + node.getNodeId() + ") is a gml:Box indexed as a polygon");
	        		} else {
	        			LOG.info("GML indexed node (" + node.getNodeId() + ") has '" + 
	        					node.getLocalName() + "' as its local name. '" + 
	        					original_geometry.getGeometryType() + "' was expected !");
	        			return false;
	        		}
	        	}
	        	
	    		LOG.info(node);	        		
	        }
	        return true;
	        
        } catch (ParseException e) {
        	LOG.error(e);
        	return false;
        } catch (PermissionDeniedException e) {
        	LOG.error(e);
        	return false;
		} finally {   
			if (rs != null)
				rs.close();
			if (ps != null)
				ps.close();	
	    }
    }
    
    protected Map getGeometriesForDocument(DocumentImpl doc, Connection conn) throws SQLException {       	
        PreparedStatement ps = conn.prepareStatement(
    		"SELECT WSG84_BASE64_WKB, WSG84_WKT FROM " + GMLHSQLIndex.TABLE_NAME + " WHERE DOCUMENT_URI = ?;"
    	); 
        ps.setString(1, doc.getURI().toString());
        //TODO : better a List with a end of process string transformation ?
    	Map map = null;
        ResultSet rs = null;
        try {	 
	        rs = ps.executeQuery();
	        map = new TreeMap();
	        while (rs.next()) {
	        	base64Decoder.reset();
	        	base64Decoder.translate(rs.getString("WSG84_BASE64_WKB"));
	        	Geometry geometry = wkbReader.read(base64Decoder.getByteArray());
	        	map.put(geometry, rs.getString("WSG84_WKT"));
	        }
	        return map;
        } catch (ParseException e) {
        	LOG.error(e);
        	return null;
    	} finally {   
    		if (rs != null)
    			rs.close();
    		if (ps != null)
    			ps.close();
    	}
    } 
    
    protected Geometry getGeometryForNode(DBBroker broker, NodeProxy p, Connection conn) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
    		"SELECT WSG84_BASE64_WKB" +
    		" FROM " + GMLHSQLIndex.TABLE_NAME + 
    		" WHERE DOCUMENT_URI = ? AND NODE_ID = ?;"
    	);
        ps.setString(1, p.getDocument().getURI().toString());
    	ps.setString(2, p.getNodeId().toString());   
    	ResultSet rs = null;    	
    	try {
    		rs = ps.executeQuery();
    		if (!rs.next())
    			//Nothing returned
    			return null;    		
			base64Decoder.reset();
        	base64Decoder.translate(rs.getString("WSG84_BASE64_WKB"));
        	Geometry geometry = wkbReader.read(base64Decoder.getByteArray());        			
        	if (rs.next()) {   	
    			//Should be impossible    		
    			throw new SQLException("More than one geometry for node " + p);
    		}
        	return geometry;    
        } catch (ParseException e) {
        	LOG.error(e); 
        	return null;
    	} finally {   
    		if (rs != null)
    			rs.close();
    		if (ps != null)
    			ps.close();
        }
    }    
    
    protected NodeSet search(DBBroker broker, NodeSet contextSet, Geometry wsg84_geometry, int spatialOp, Connection conn) throws SQLException {
    	String extraSelection = null;
    	String bboxConstraint = null;    	
        switch (spatialOp) {
        	//BBoxes are equal
        	case SpatialOperator.EQUALS:
        		bboxConstraint = "(WSG84_MINX = ? AND WSG84_MAXX = ?)" +
    				" AND (WSG84_MINY = ? AND WSG84_MAXY = ?)";
        		break;
        	case SpatialOperator.DISJOINT:
        		//Nothing much we can do with the BBox at this stage
        		extraSelection = ", WSG84_MINX, WSG84_MAXX, WSG84_MINY, WSG84_MAXY";
        		break;
       		//BBoxes intersect themselves
        	case SpatialOperator.INTERSECTS:        		
        	case SpatialOperator.TOUCHES:        		   		
        	case SpatialOperator.CROSSES:        		      		
        	case SpatialOperator.OVERLAPS: 
        		bboxConstraint = "(WSG84_MAXX >= ? AND WSG84_MINX <= ?)" +
				" AND (WSG84_MAXY >= ? AND WSG84_MINY <= ?)";
        		break;
        	//BBoxe is fully within
        	case SpatialOperator.WITHIN:   
        		bboxConstraint = "(WSG84_MINX >= ? AND WSG84_MAXX <= ?)" +
				" AND (WSG84_MINY >= ? AND WSG84_MAXY <= ?)";
        		break;        		
        	case SpatialOperator.CONTAINS: 
        		bboxConstraint = "(WSG84_MINX <= ? AND WSG84_MAXX >= ?)" +
				" AND (WSG84_MINY <= ? AND WSG84_MAXY >= ?)";
        		break;             		
        	default:
        		throw new IllegalArgumentException("Unsupported spatial operator:" + spatialOp);
        }
        PreparedStatement ps = conn.prepareStatement(
    		"SELECT WSG84_BASE64_WKB, DOCUMENT_URI, NODE_ID" + (extraSelection == null ? "" : extraSelection) +
    		" FROM " + GMLHSQLIndex.TABLE_NAME + 
    		(bboxConstraint == null ? "" : " WHERE " + bboxConstraint) + ";"
    	);
        if (bboxConstraint != null) {
	        ps.setDouble(1, wsg84_geometry.getEnvelopeInternal().getMinX());
	    	ps.setDouble(2, wsg84_geometry.getEnvelopeInternal().getMaxX());
	    	ps.setDouble(3, wsg84_geometry.getEnvelopeInternal().getMinY());
	    	ps.setDouble(4, wsg84_geometry.getEnvelopeInternal().getMaxY());
        }
    	ResultSet rs = null;
    	NodeSet result = null;
    	try { 
    		int disjointPostFiltered = 0;
    		rs = ps.executeQuery();
    		result = new ExtArrayNodeSet(); //new ExtArrayNodeSet(docs.getLength(), 250)
    		while (rs.next()) {
    			NodeProxy p = null;	    		
        		XmldbURI documentURI = XmldbURI.create(rs.getString("DOCUMENT_URI"));
        		try {
        			Document doc = broker.getXMLResource(documentURI);
	        		NodeId nodeId = new DLN(rs.getString("NODE_ID")); 
	        		p = new NodeProxy((DocumentImpl)doc, nodeId);		        		
        		} catch (PermissionDeniedException e) {
        			LOG.debug(e);
        		}        		
        		//Node is in the context : check if it is accurate
        		//contextSet.contains(p) would have made more sense but there is a problem with
        		//VirtualNodeSet when on the DESCENDANT_OR_SELF axis
        		if (contextSet == null || contextSet.get(p) != null) {	
        			
		        	boolean geometryMatches = false;
		        	if (spatialOp == SpatialOperator.DISJOINT) {
		        		//Obviously disjoint
		        		if (rs.getDouble("WSG84_MAXX") < wsg84_geometry.getEnvelopeInternal().getMinX() ||	        			
			        		rs.getDouble("WSG84_MINX") > wsg84_geometry.getEnvelopeInternal().getMaxX() ||	        			
			        		rs.getDouble("WSG84_MAXY") < wsg84_geometry.getEnvelopeInternal().getMinY() ||	        			
			        		rs.getDouble("WSG84_MINY") > wsg84_geometry.getEnvelopeInternal().getMaxY()) {
			        			geometryMatches = true;
					        		disjointPostFiltered++;	
		        		}
		        	}
		        	//Check the geometry
		        	if (!geometryMatches) {	
		        		try {			        	
			    			base64Decoder.reset();
				        	base64Decoder.translate(rs.getString("WSG84_BASE64_WKB"));
				        	Geometry geometry = wkbReader.read(base64Decoder.getByteArray());			        	
				        	switch (spatialOp) {
				        	case SpatialOperator.EQUALS:	        		        		
				        		geometryMatches = geometry.equals(wsg84_geometry);
				        		break;
				        	case SpatialOperator.DISJOINT:        		
				        		geometryMatches = geometry.disjoint(wsg84_geometry);
				        		break;	        		
				        	case SpatialOperator.INTERSECTS:        		
				        		geometryMatches = geometry.intersects(wsg84_geometry);
				        		break;	        		
				        	case SpatialOperator.TOUCHES:
					        	geometryMatches = geometry.touches(wsg84_geometry);
				        		break;	        		
				        	case SpatialOperator.CROSSES:
					        	geometryMatches = geometry.crosses(wsg84_geometry);
				        		break;	        		
				        	case SpatialOperator.WITHIN:        		
				        		geometryMatches = geometry.within(wsg84_geometry);
				        		break;	        		
				        	case SpatialOperator.CONTAINS:	        		
				        		geometryMatches = geometry.contains(wsg84_geometry);
				        		break;	        		
				        	case SpatialOperator.OVERLAPS:	        		
				        		geometryMatches = geometry.overlaps(wsg84_geometry);
				        		break;	        		
				        	}
		    	        } catch (ParseException e) {
		    	        	LOG.error(e); 
		    	        	return NodeSet.EMPTY_SET;
		    	        }
		        	}
		        	if (geometryMatches)        	
			        	result.add(p);
        		}
    		}
    		if (LOG.isDebugEnabled()) {
    			LOG.debug(rs.getRow() + " eligible geometries, " + result.getItemCount() + "selected" +
    				(spatialOp == SpatialOperator.DISJOINT ? "(" + disjointPostFiltered + " post filtered)" : ""));
    		}
    		return result;	    	
    	} finally {   
    		if (rs != null)
    			rs.close();
    		if (ps != null)
    			ps.close();	    		
    	}
    } 
    
    protected NodeSet isIndexed(DBBroker broker, Geometry wsg84_geometry, Connection conn) throws SQLException {    	
    	String bboxConstraint = "(WSG84_MINX = ? AND WSG84_MAXX = ?)" +
    				" AND (WSG84_MINY = ? AND WSG84_MAXY = ?)"; 
    	NodeSet result = null;
        PreparedStatement ps = conn.prepareStatement(
    		"SELECT WSG84_BASE64_WKB, DOCUMENT_URI, NODE_ID" +
    		" FROM " + GMLHSQLIndex.TABLE_NAME + 
    		" WHERE " + bboxConstraint + ";"
    	);
        ps.setDouble(1, wsg84_geometry.getEnvelopeInternal().getMinX());
    	ps.setDouble(2, wsg84_geometry.getEnvelopeInternal().getMaxX());
    	ps.setDouble(3, wsg84_geometry.getEnvelopeInternal().getMinY());
    	ps.setDouble(4, wsg84_geometry.getEnvelopeInternal().getMaxY());      
    	ResultSet rs = null;
    	result = new ExtArrayNodeSet(); //new ExtArrayNodeSet(docs.getLength(), 250)
    	try {
    		rs = ps.executeQuery();
    		while (rs.next()) {    			
    			base64Decoder.reset();
	        	base64Decoder.translate(rs.getString("WSG84_BASE64_WKB"));
	        	Geometry geometry = wkbReader.read(base64Decoder.getByteArray());	
	        	boolean geometryMatches = geometry.equals(wsg84_geometry);	        	
	        	if (geometryMatches) {	        	
	        		XmldbURI documentURI = XmldbURI.create(rs.getString("DOCUMENT_URI"));
	        		try {
	        			Document doc = broker.getXMLResource(documentURI);
		        		NodeId nodeId = new DLN(rs.getString("NODE_ID")); 
		        		NodeProxy p = new NodeProxy((DocumentImpl)doc, nodeId);
		        		result.add(p);
	        		} catch (PermissionDeniedException e) {
	        			LOG.debug(e);
	        		}
	        	}
    		}
    		if (LOG.isDebugEnabled()) {
    			LOG.debug(rs.getRow() + " eligible geometries, " + result.getItemCount() + "selected");
    		}
    		return result;
        } catch (ParseException e) {
        	LOG.error(e);
        	return null;
    	} finally {   
    		if (rs != null)
    			rs.close();
    		if (ps != null)
    			ps.close();
    	}    	
    }
 
}