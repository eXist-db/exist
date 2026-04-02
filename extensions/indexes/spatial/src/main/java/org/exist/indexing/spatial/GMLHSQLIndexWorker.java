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
package org.exist.indexing.spatial;

import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.dom.persistent.*;
import org.exist.indexing.spatial.AbstractGMLJDBCIndex.SpatialOperator;
import org.exist.numbering.DLN;
import org.exist.numbering.NodeId;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.NodePath;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.QueryRewriter;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.*;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;

import java.sql.*;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author <a href="mailto:pierrick.brihaye@free.fr">Pierrick Brihaye</a>
 */
public class GMLHSQLIndexWorker extends AbstractGMLJDBCIndexWorker {

    private static final Logger LOG = LogManager.getLogger(GMLHSQLIndexWorker.class);

    public GMLHSQLIndexWorker(final GMLHSQLIndex index, final DBBroker broker) {
        super(index, broker);
        //TODO : evaluate one connection per worker
        /*
        try {
            conn = DriverManager.getConnection("jdbc:hsqldb:" + index.getDataDir() + "/" + 
                index.db_file_name_prefix + ";shutdown=true", "sa", "");
        } catch (SQLException e) {
            LOG.error(e);
        }
        */
    }

    @Override
    protected boolean saveGeometryNode(Geometry geometry, final String srsName, final DocumentImpl doc, final NodeId nodeId, final PreparedStatement ps) throws SQLException {
        try {
            Geometry EPSG4326_geometry = null;
            try {
                EPSG4326_geometry = transformGeometry(geometry, srsName, "EPSG:4326");
            } catch (final SpatialIndexException e) {
                //Transforms the exception into an SQLException.
                final SQLException ee = new SQLException(e.getMessage());
                ee.initCause(e);
                throw ee;
            }
            ps.clearParameters();

            /*DOCUMENT_URI*/
            ps.setString(1, doc.getURI().toString());
            /*NODE_ID_UNITS*/
            ps.setInt(2, nodeId.units());
            final byte[] bytes = new byte[nodeId.size()];
            nodeId.serialize(bytes, 0);
            /*NODE_ID*/
            ps.setBytes(3, bytes);
            /*GEOMETRY_TYPE*/
            ps.setString(4, geometry.getGeometryType());
            /*SRS_NAME*/
            ps.setString(5, srsName);
            /*WKT*/
            ps.setString(6, wktWriter.write(geometry));
            /*WKB*/
            ps.setBytes(7, wkbWriter.write(geometry));
            /*MINX*/
            ps.setDouble(8, geometry.getEnvelopeInternal().getMinX());
            /*MAXX*/
            ps.setDouble(9, geometry.getEnvelopeInternal().getMaxX());
            /*MINY*/
            ps.setDouble(10, geometry.getEnvelopeInternal().getMinY());
            /*MAXY*/
            ps.setDouble(11, geometry.getEnvelopeInternal().getMaxY());
            /*CENTROID_X*/
            ps.setDouble(12, geometry.getCentroid().getCoordinate().x);
            /*CENTROID_Y*/
            ps.setDouble(13, geometry.getCentroid().getCoordinate().y);
            //geometry.getRepresentativePoint()
            /*AREA*/
            ps.setDouble(14, geometry.getArea());
            //Boundary ?
            /*EPSG4326_WKT*/
            ps.setString(15, wktWriter.write(EPSG4326_geometry));
            /*EPSG4326_WKB*/
            ps.setBytes(16, wkbWriter.write(EPSG4326_geometry));
            /*EPSG4326_MINX*/
            ps.setDouble(17, EPSG4326_geometry.getEnvelopeInternal().getMinX());
            /*EPSG4326_MAXX*/
            ps.setDouble(18, EPSG4326_geometry.getEnvelopeInternal().getMaxX());
            /*EPSG4326_MINY*/
            ps.setDouble(19, EPSG4326_geometry.getEnvelopeInternal().getMinY());
            /*EPSG4326_MAXY*/
            ps.setDouble(20, EPSG4326_geometry.getEnvelopeInternal().getMaxY());
            /*EPSG4326_CENTROID_X*/
            ps.setDouble(21, EPSG4326_geometry.getCentroid().getCoordinate().x);
            /*EPSG4326_CENTROID_Y*/
            ps.setDouble(22, EPSG4326_geometry.getCentroid().getCoordinate().y);
            //EPSG4326_geometry.getRepresentativePoint()
            /*EPSG4326_AREA*/
            ps.setDouble(23, EPSG4326_geometry.getArea());
            //Boundary ?
            //As discussed earlier, all instances of SFS geometry classes
            //are topologically closed by definition.
            //For empty Curves, isClosed is defined to have the value false.
            /*IS_CLOSED*/
            ps.setBoolean(24, !geometry.isEmpty());
            /*IS_SIMPLE*/
            ps.setBoolean(25, geometry.isSimple());
            //Should always be true (the GML SAX parser makes a too severe check)
            /*IS_VALID*/
            ps.setBoolean(26, geometry.isValid());
            return ps.executeUpdate() == 1;
        } finally {
            //Let's help the garbage collector...
            geometry = null;
            ps.clearParameters();
        }
    }

    @Override
    protected boolean removeDocumentNode(final DocumentImpl doc, final NodeId nodeId, final Connection conn) throws SQLException {
//        final Statement s = conn.createStatement();
//        ResultSet rs = s.executeQuery("SELECT NODE_ID_UNITS, NODE_ID FROM " + GMLHSQLIndex.TABLE_NAME);
//        while (rs.next()) {
//            int units = rs.getInt(1);
//            byte[] data = rs.getBytes(2);
//            System.out.println(new DLN(units, data, 0).toString());
//        }

        final PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM " + GMLHSQLIndex.TABLE_NAME + 
                " WHERE DOCUMENT_URI = ? AND NODE_ID_UNITS = ? AND NODE_ID = ?;"
            ); 
        ps.setString(1, doc.getURI().toString());
        ps.setInt(2, nodeId.units());
        final byte[] bytes = new byte[nodeId.size()];
        nodeId.serialize(bytes, 0);
        ps.setBytes(3, bytes);
        try (ps) {
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    protected int removeDocument(final DocumentImpl doc, final Connection conn) throws SQLException {
        final PreparedStatement ps = conn.prepareStatement(
            "DELETE FROM " + GMLHSQLIndex.TABLE_NAME + " WHERE DOCUMENT_URI = ?;"
        );
        try (ps) {
            ps.setString(1, doc.getURI().toString());
            return ps.executeUpdate();
        }
    }

    @Override
    protected int removeCollection(final Collection collection, final Connection conn) throws SQLException {
        final PreparedStatement ps = conn.prepareStatement(
            "DELETE FROM " + GMLHSQLIndex.TABLE_NAME + " WHERE SUBSTRING(DOCUMENT_URI, 1, ?) = ?;"
        );
        try (ps) {
            ps.setInt(1, collection.getURI().toString().length());
            ps.setString(2, collection.getURI().toString());
            return ps.executeUpdate();
        }
    }

    //Since an embedded HSQL has only one connection available (unless I'm totally dumb)
    //acquire and release the connection from the index, which is *the* connection's owner 

    @Override
    protected Connection acquireConnection() throws SQLException {
        return index.acquireConnection(this.broker);
    }

    @Override
    protected void releaseConnection(final Connection conn) throws SQLException {
        index.releaseConnection(this.broker);
    }

    @Override
    protected NodeSet search(final DBBroker broker, final NodeSet contextSet, final Geometry EPSG4326_geometry, final int spatialOp, final Connection conn) throws SQLException {
        String extraSelection = null;
        String bboxConstraint = null;

        //TODO : generate it in AbstractGMLJDBCIndexWorker
        String docConstraint = "";
        boolean refine_query_on_doc = false;
        if (contextSet != null) {
            if(contextSet.getDocumentSet().getDocumentCount() <= index.getMaxDocsInContextToRefineQuery()) {
                refine_query_on_doc = true;
                DocumentImpl doc;
                final Iterator<DocumentImpl> it = contextSet.getDocumentSet().getDocumentIterator();
                doc  = it.next();
                final StringBuilder docConstraintBuilder = new StringBuilder("(DOCUMENT_URI = '" + doc.getURI().toString() + "')");
                while(it.hasNext()) {
                    doc  = it.next();
                    docConstraintBuilder.append(" OR (DOCUMENT_URI = '").append(doc.getURI().toString()).append("')");
                }
                docConstraint = docConstraintBuilder.toString();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Refine query on documents is enabled.");
                }
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Refine query on documents is disabled.");
                }
            }
        }

        switch (spatialOp) {
        //BBoxes are equal
        case SpatialOperator.EQUALS:
            bboxConstraint = "(EPSG4326_MINX = ? AND EPSG4326_MAXX = ?)" +
                " AND (EPSG4326_MINY = ? AND EPSG4326_MAXY = ?)";
            break;
        //Nothing much we can do with the BBox at this stage
        case SpatialOperator.DISJOINT:
            //Retrieve the BBox though...
            extraSelection = ", EPSG4326_MINX, EPSG4326_MAXX, EPSG4326_MINY, EPSG4326_MAXY";
            break;
        //BBoxes intersect themselves
        case SpatialOperator.INTERSECTS:
        case SpatialOperator.TOUCHES:
        case SpatialOperator.CROSSES:
        case SpatialOperator.OVERLAPS:
            bboxConstraint = "(EPSG4326_MAXX >= ? AND EPSG4326_MINX <= ?)" +
            " AND (EPSG4326_MAXY >= ? AND EPSG4326_MINY <= ?)";
            break;
        //BBox is fully within
        case SpatialOperator.WITHIN:
            bboxConstraint = "(EPSG4326_MINX >= ? AND EPSG4326_MAXX <= ?)" +
            " AND (EPSG4326_MINY >= ? AND EPSG4326_MAXY <= ?)";
            break;
        //BBox fully contains
        case SpatialOperator.CONTAINS: 
            bboxConstraint = "(EPSG4326_MINX <= ? AND EPSG4326_MAXX >= ?)" +
            " AND (EPSG4326_MINY <= ? AND EPSG4326_MAXY >= ?)";
            break;
        default:
            throw new IllegalArgumentException("Unsupported spatial operator:" + spatialOp);
        }
        final PreparedStatement ps = conn.prepareStatement(
            "SELECT EPSG4326_WKB, DOCUMENT_URI, NODE_ID_UNITS, NODE_ID" + (extraSelection == null ? "" : extraSelection) +
            " FROM " + GMLHSQLIndex.TABLE_NAME + 
            (bboxConstraint == null ? 
                (refine_query_on_doc ? " WHERE " + docConstraint : "") : 
                " WHERE " +	(refine_query_on_doc ? "(" + docConstraint + ") AND " :	"") + bboxConstraint) + ";"
        );
        if (bboxConstraint != null) {
            ps.setDouble(1, EPSG4326_geometry.getEnvelopeInternal().getMinX());
            ps.setDouble(2, EPSG4326_geometry.getEnvelopeInternal().getMaxX());
            ps.setDouble(3, EPSG4326_geometry.getEnvelopeInternal().getMinY());
            ps.setDouble(4, EPSG4326_geometry.getEnvelopeInternal().getMaxY());
        }
        ResultSet rs = null;
        NodeSet result = null;
        try { 
            int disjointPostFiltered = 0;
            rs = ps.executeQuery();
            result = new ExtArrayNodeSet(); //new ExtArrayNodeSet(docs.getLength(), 250)
            while (rs.next()) {
                DocumentImpl doc = null;
                try {
                    doc = (DocumentImpl)broker.getXMLResource(XmldbURI.create(rs.getString("DOCUMENT_URI")));        			
                } catch (final PermissionDeniedException e) {
                    LOG.debug(e);
                    //Ignore since the broker has no right on the document
                    continue;
                }
                //contextSet == null should be used to scan the whole index
                if (contextSet == null || refine_query_on_doc || contextSet.getDocumentSet().contains(doc.getDocId())) {
                    final NodeId nodeId = new DLN(rs.getInt("NODE_ID_UNITS"), rs.getBytes("NODE_ID"), 0);
                    final NodeProxy p = new NodeProxy(null, doc, nodeId);
                    //Node is in the context : check if it is accurate
                    //contextSet.contains(p) would have made more sense but there is a problem with
                    //VirtualNodeSet when on the DESCENDANT_OR_SELF axis
                    if (contextSet == null || contextSet.get(p) != null) {
                        boolean geometryMatches = false;
                        if (spatialOp == SpatialOperator.DISJOINT) {
                            //No BBox intersection : obviously disjoint
                            if (rs.getDouble("EPSG4326_MAXX") < EPSG4326_geometry.getEnvelopeInternal().getMinX() ||
                                rs.getDouble("EPSG4326_MINX") > EPSG4326_geometry.getEnvelopeInternal().getMaxX() ||
                                rs.getDouble("EPSG4326_MAXY") < EPSG4326_geometry.getEnvelopeInternal().getMinY() ||
                                rs.getDouble("EPSG4326_MINY") > EPSG4326_geometry.getEnvelopeInternal().getMaxY()) {
                                geometryMatches = true;
                                disjointPostFiltered++;	
                            }
                        }
                        //Possible match : check the geometry
                        if (!geometryMatches) {	
                            try {
                                final Geometry geometry = wkbReader.read(rs.getBytes("EPSG4326_WKB"));
                                geometryMatches = switch (spatialOp) {
                                    case SpatialOperator.EQUALS -> geometry.equals(EPSG4326_geometry);
                                    case SpatialOperator.DISJOINT -> geometry.disjoint(EPSG4326_geometry);
                                    case SpatialOperator.INTERSECTS -> geometry.intersects(EPSG4326_geometry);
                                    case SpatialOperator.TOUCHES -> geometry.touches(EPSG4326_geometry);
                                    case SpatialOperator.CROSSES -> geometry.crosses(EPSG4326_geometry);
                                    case SpatialOperator.WITHIN -> geometry.within(EPSG4326_geometry);
                                    case SpatialOperator.CONTAINS -> geometry.contains(EPSG4326_geometry);
                                    case SpatialOperator.OVERLAPS -> geometry.overlaps(EPSG4326_geometry);
                                    default -> geometryMatches;
                                };
                            } catch (final ParseException e) {
                                //Transforms the exception into an SQLException.
                                //Very unlikely to happen though...
                                final SQLException ee = new SQLException(e.getMessage());
                                ee.initCause(e);
                                throw ee;
                            }
                        }
                        if (geometryMatches) {
                            result.add(p);
                        }
                    }
                }
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("{} eligible geometries, {}selected{}", rs.getRow(), result.getItemCount(), spatialOp == SpatialOperator.DISJOINT ? "(" + disjointPostFiltered + " post filtered)" : "");
            }
            return result;
        } finally {
            if (rs != null) {
                rs.close();
            }
            if (ps != null) {
                ps.close();
            }
        }
    }

    @Override
    protected Map<Geometry, String> getGeometriesForDocument(final DocumentImpl doc, final Connection conn) throws SQLException {
        final PreparedStatement ps = conn.prepareStatement(
            "SELECT EPSG4326_WKB, EPSG4326_WKT FROM " + GMLHSQLIndex.TABLE_NAME + " WHERE DOCUMENT_URI = ?;"
        ); 
        ps.setString(1, doc.getURI().toString());
        ResultSet rs = null;
        try {
            rs = ps.executeQuery();
            final Map<Geometry, String> map = new TreeMap<>();
            while (rs.next()) {
                final Geometry EPSG4326_geometry = wkbReader.read(rs.getBytes("EPSG4326_WKB"));
                //Returns the EPSG:4326 WKT for every geometry to make occurrence aggregation consistent
                map.put(EPSG4326_geometry, rs.getString("EPSG4326_WKT"));
            }
            return map;
        } catch (final ParseException e) {
            //Transforms the exception into an SQLException.
            //Very unlikely to happen though...
            final SQLException ee = new SQLException(e.getMessage());
            ee.initCause(e);
            throw ee;
        } finally {
            if (rs != null) {
                rs.close();
            }
            if (ps != null) {
                ps.close();
            }
        }
    }

    @Override
    protected Geometry getGeometryForNode(final DBBroker broker, final NodeProxy p, final boolean getEPSG4326, final Connection conn) throws SQLException {
        final PreparedStatement ps = conn.prepareStatement(
            "SELECT " + (getEPSG4326 ? "EPSG4326_WKB" : "WKB") +
            " FROM " + GMLHSQLIndex.TABLE_NAME + 
            " WHERE DOCUMENT_URI = ? AND NODE_ID_UNITS = ? AND NODE_ID = ?;"
        );
        ps.setString(1, p.getOwnerDocument().getURI().toString());
        ps.setInt(2, p.getNodeId().units());
        final byte[] bytes = new byte[p.getNodeId().size()];
        p.getNodeId().serialize(bytes, 0);
        ps.setBytes(3, bytes);
        ResultSet rs = null;
        try {
            rs = ps.executeQuery();
            if (!rs.next()) {
                //Nothing returned
                return null;
            }
            final Geometry geometry = wkbReader.read(rs.getBytes(1));
            if (rs.next()) {
                //Should be impossible
                throw new SQLException("More than one geometry for node " + p);
            }
            return geometry;
        } catch (final ParseException e) {
            //Transforms the exception into an SQLException.
            //Very unlikely to happen though...
            final SQLException ee = new SQLException(e.getMessage());
            ee.initCause(e);
            throw ee;
        } finally {
            if (rs != null) {
                rs.close();
            }
            ps.close();
        }
    }
    
    @Override
    protected Geometry[] getGeometriesForNodes(final DBBroker broker, final NodeSet contextSet, final boolean getEPSG4326, final Connection conn) throws SQLException {
        //TODO : generate it in AbstractGMLJDBCIndexWorker
        String docConstraint = "";
        final boolean refine_query_on_doc = false;
        if (contextSet != null) {
            if(contextSet.getDocumentSet().getDocumentCount() <= index.getMaxDocsInContextToRefineQuery()) {
                DocumentImpl doc;
                final Iterator<DocumentImpl> it = contextSet.getDocumentSet().getDocumentIterator();
                doc  = it.next();
                final StringBuilder docConstraintBuilder = new StringBuilder("(DOCUMENT_URI = '" + doc.getURI().toString() + "')");
                while(it.hasNext()) {
                    doc  = it.next();
                    docConstraintBuilder.append(" OR (DOCUMENT_URI = '").append(doc.getURI().toString()).append("')");
                }
                docConstraint = docConstraintBuilder.toString();
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Refine query on documents is {}", refine_query_on_doc ? "enabled." : "disabled.");
        }

        try (final PreparedStatement ps = conn.prepareStatement(
                "SELECT " + (getEPSG4326 ? "EPSG4326_WKB" : "WKB") + ", DOCUMENT_URI, NODE_ID_UNITS, NODE_ID" +
                        " FROM " + GMLHSQLIndex.TABLE_NAME + (refine_query_on_doc ? " WHERE " + docConstraint : "")
        ); final ResultSet rs = ps.executeQuery()) {
            final Geometry[] result = new Geometry[contextSet.getLength()];
            int index = 0;
            while (rs.next()) {
                DocumentImpl doc = null;
                try {
                    doc = (DocumentImpl) broker.getXMLResource(XmldbURI.create(rs.getString("DOCUMENT_URI")));
                } catch (final PermissionDeniedException e) {
                    LOG.debug(e);
                    result[index++] = null;
                    //Ignore since the broker has no right on the document
                    continue;
                }
                if (contextSet == null || refine_query_on_doc || contextSet.getDocumentSet().contains(doc.getDocId())) {
                    final NodeId nodeId = new DLN(rs.getInt("NODE_ID_UNITS"), rs.getBytes("NODE_ID"), 0);
                    final NodeProxy p = new NodeProxy(null, doc, nodeId);
                    //Node is in the context : check if it is accurate
                    //contextSet.contains(p) would have made more sense but there is a problem with
                    //VirtualNodeSet when on the DESCENDANT_OR_SELF axis
                    if (contextSet.get(p) != null) {
                        final Geometry geometry = wkbReader.read(rs.getBytes(1));
                        result[index++] = geometry;
                    }
                }
            }
            return result;
        } catch (final ParseException e) {
            //Transforms the exception into an SQLException.
            //Very unlikely to happen though...
            final SQLException ee = new SQLException(e.getMessage());
            ee.initCause(e);
            throw ee;
        }
    }

    @Override
    protected AtomicValue getGeometricPropertyForNode(final XQueryContext context, final NodeProxy p, final Connection conn, final String propertyName) throws SQLException, XPathException {
        final PreparedStatement ps = conn.prepareStatement(
            "SELECT " + propertyName + 
            " FROM " + GMLHSQLIndex.TABLE_NAME + 
            " WHERE DOCUMENT_URI = ? AND NODE_ID_UNITS = ? AND NODE_ID = ?"
        );
        ps.setString(1, p.getOwnerDocument().getURI().toString());
        ps.setInt(2, p.getNodeId().units());
        final byte[] bytes = new byte[p.getNodeId().size()];
        p.getNodeId().serialize(bytes, 0);
        ps.setBytes(3, bytes);
        ResultSet rs = null;
        try {
            rs = ps.executeQuery();
            if (!rs.next()) {
                //Nothing returned
                return AtomicValue.EMPTY_VALUE;
            }
            AtomicValue result = null;
            if (rs.getMetaData().getColumnClassName(1).equals(Boolean.class.getName())) {
                result = new BooleanValue(rs.getBoolean(1));
            } else if (rs.getMetaData().getColumnClassName(1).equals(Double.class.getName())) {
                result = new DoubleValue(rs.getDouble(1));
            } else if (rs.getMetaData().getColumnClassName(1).equals(String.class.getName())) {
                result = new StringValue(rs.getString(1));
            } else if (rs.getMetaData().getColumnType(1) == java.sql.Types.BINARY) {
                result = BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), new UnsynchronizedByteArrayInputStream(rs.getBytes(1)), null);
            } else {
                throw new SQLException("Unable to make an atomic value from '" + rs.getMetaData().getColumnClassName(1) + "'");
            }		
            if (rs.next()) {
                //Should be impossible
                throw new SQLException("More than one geometry for node " + p);
            }
            return result;
        } finally {
            if (rs != null) {
                rs.close();
            }
            ps.close();
        }
    }

    @Override
    protected ValueSequence getGeometricPropertyForNodes(final XQueryContext context, final NodeSet contextSet, final Connection conn, final String propertyName) throws SQLException, XPathException {
        //TODO : generate it in AbstractGMLJDBCIndexWorker
        String docConstraint = "";
        final boolean refine_query_on_doc = false;
        if (contextSet != null) {
            if(contextSet.getDocumentSet().getDocumentCount() <= index.getMaxDocsInContextToRefineQuery()) {
                DocumentImpl doc;
                final Iterator<DocumentImpl> it = contextSet.getDocumentSet().getDocumentIterator();
                doc  = it.next();
                final StringBuilder docConstraintBuilder = new StringBuilder("(DOCUMENT_URI = '" + doc.getURI().toString() + "')");
                while(it.hasNext()) {
                    doc = it.next();
                    docConstraintBuilder.append(" OR (DOCUMENT_URI = '").append(doc.getURI().toString()).append("')");
                }
                docConstraint = docConstraintBuilder.toString();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Refine query on documents is enabled.");
                }
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Refine query on documents is disabled.");
                }
            }
        }

        try (final PreparedStatement ps = conn.prepareStatement(
                "SELECT " + propertyName + ", DOCUMENT_URI, NODE_ID_UNITS, NODE_ID" +
                        " FROM " + GMLHSQLIndex.TABLE_NAME + (refine_query_on_doc ? " WHERE " + docConstraint : "")
        ); final ResultSet rs = ps.executeQuery()) {
            final ValueSequence result;
            if (contextSet == null) {
                result = new ValueSequence();
            } else {
                result = new ValueSequence(contextSet.getLength());
            }
            while (rs.next()) {
                DocumentImpl doc = null;
                try {
                    doc = (DocumentImpl) broker.getXMLResource(XmldbURI.create(rs.getString("DOCUMENT_URI")));
                } catch (final PermissionDeniedException e) {
                    LOG.debug(e);
                    //Untested, but that is roughly what should be returned.
                    if (rs.getMetaData().getColumnClassName(1).equals(Boolean.class.getName())) {
                        result.add(AtomicValue.EMPTY_VALUE);
                    } else if (rs.getMetaData().getColumnClassName(1).equals(Double.class.getName())) {
                        result.add(AtomicValue.EMPTY_VALUE);
                    } else if (rs.getMetaData().getColumnClassName(1).equals(String.class.getName())) {
                        result.add(AtomicValue.EMPTY_VALUE);
                    } else if (rs.getMetaData().getColumnType(1) == Types.BINARY) {
                        result.add(AtomicValue.EMPTY_VALUE);
                    } else {
                        throw new SQLException("Unable to make an atomic value from '" + rs.getMetaData().getColumnClassName(1) + "'");
                    }
                    //Ignore since the broker has no right on the document
                    continue;
                }
                if (contextSet.getDocumentSet().contains(doc.getDocId())) {
                    final NodeId nodeId = new DLN(rs.getInt("NODE_ID_UNITS"), rs.getBytes("NODE_ID"), 0);
                    final NodeProxy p = new NodeProxy(null, doc, nodeId);
                    //Node is in the context : check if it is accurate
                    //contextSet.contains(p) would have made more sense but there is a problem with
                    //VirtualNodeSet when on the DESCENDANT_OR_SELF axis
                    if (contextSet.get(p) != null) {
                        if (rs.getMetaData().getColumnClassName(1).equals(Boolean.class.getName())) {
                            result.add(new BooleanValue(rs.getBoolean(1)));
                        } else if (rs.getMetaData().getColumnClassName(1).equals(Double.class.getName())) {
                            result.add(new DoubleValue(rs.getDouble(1)));
                        } else if (rs.getMetaData().getColumnClassName(1).equals(String.class.getName())) {
                            result.add(new StringValue(rs.getString(1)));
                        } else if (rs.getMetaData().getColumnType(1) == Types.BINARY) {
                            result.add(BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), new UnsynchronizedByteArrayInputStream(rs.getBytes(1)), null));
                        } else {
                            throw new SQLException("Unable to make an atomic value from '" + rs.getMetaData().getColumnClassName(1) + "'");
                        }
                    }
                }
            }
            return result;
        }
    }

    @Override
    protected boolean checkIndex(final DBBroker broker, final Connection conn) throws SQLException {
        try (final PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM " + GMLHSQLIndex.TABLE_NAME + ";"
        ); final ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                final Geometry original_geometry = wkbReader.read(rs.getBytes("WKB"));
                if (!original_geometry.equals(wktReader.read(rs.getString("WKT")))) {
                    LOG.info("Inconsistent WKT : {}", rs.getString("WKT"));
                    return false;
                }
                final Geometry EPSG4326_geometry = wkbReader.read(rs.getBytes("EPSG4326_WKB"));
                if (!EPSG4326_geometry.equals(wktReader.read(rs.getString("EPSG4326_WKT")))) {
                    LOG.info("Inconsistent WKT : {}", rs.getString("EPSG4326_WKT"));
                    return false;
                }
                if (!original_geometry.getGeometryType().equals(rs.getString("GEOMETRY_TYPE"))) {
                    LOG.info("Inconsistent geometry type: {}", rs.getDouble("GEOMETRY_TYPE"));
                    return false;
                }
                if (original_geometry.getEnvelopeInternal().getMinX() != rs.getDouble("MINX")) {
                    LOG.info("Inconsistent MinX: {}", rs.getDouble("MINX"));
                    return false;
                }
                if (original_geometry.getEnvelopeInternal().getMaxX() != rs.getDouble("MAXX")) {
                    LOG.info("Inconsistent MaxX: {}", rs.getDouble("MAXX"));
                    return false;
                }
                if (original_geometry.getEnvelopeInternal().getMinY() != rs.getDouble("MINY")) {
                    LOG.info("Inconsistent MinY: {}", rs.getDouble("MINY"));
                    return false;
                }
                if (original_geometry.getEnvelopeInternal().getMaxY() != rs.getDouble("MAXY")) {
                    LOG.info("Inconsistent MaxY: {}", rs.getDouble("MAXY"));
                    return false;
                }
                if (original_geometry.getCentroid().getCoordinate().x != rs.getDouble("CENTROID_X")) {
                    LOG.info("Inconsistent X for centroid : {}", rs.getDouble("CENTROID_X"));
                    return false;
                }
                if (original_geometry.getCentroid().getCoordinate().y != rs.getDouble("CENTROID_Y")) {
                    LOG.info("Inconsistent Y for centroid : {}", rs.getDouble("CENTROID_Y"));
                    return false;
                }
                if (original_geometry.getArea() != rs.getDouble("AREA")) {
                    LOG.info("Inconsistent area: {}", rs.getDouble("AREA"));
                    return false;
                }
                final String srsName = rs.getString("SRS_NAME");
                try {
                    if (!transformGeometry(original_geometry, srsName, "EPSG:4326").equals(EPSG4326_geometry)) {
                        LOG.info("Transformed original geometry inconsistent with stored tranformed one");
                        return false;
                    }
                } catch (final SpatialIndexException e) {
                    //Transforms the exception into an SQLException.
                    final SQLException ee = new SQLException(e.getMessage());
                    ee.initCause(e);
                    throw ee;
                }
                if (EPSG4326_geometry.getEnvelopeInternal().getMinX() != rs.getDouble("EPSG4326_MINX")) {
                    LOG.info("Inconsistent MinX: {}", rs.getDouble("EPSG4326_MINX"));
                    return false;
                }
                if (EPSG4326_geometry.getEnvelopeInternal().getMaxX() != rs.getDouble("EPSG4326_MAXX")) {
                    LOG.info("Inconsistent MaxX: {}", rs.getDouble("EPSG4326_MAXX"));
                    return false;
                }
                if (EPSG4326_geometry.getEnvelopeInternal().getMinY() != rs.getDouble("EPSG4326_MINY")) {
                    LOG.info("Inconsistent MinY: {}", rs.getDouble("EPSG4326_MINY"));
                    return false;
                }
                if (EPSG4326_geometry.getEnvelopeInternal().getMaxY() != rs.getDouble("EPSG4326_MAXY")) {
                    LOG.info("Inconsistent MaxY: {}", rs.getDouble("EPSG4326_MAXY"));
                    return false;
                }
                if (EPSG4326_geometry.getCentroid().getCoordinate().x != rs.getDouble("EPSG4326_CENTROID_X")) {
                    LOG.info("Inconsistent X for centroid : {}", rs.getDouble("EPSG4326_CENTROID_X"));
                    return false;
                }
                if (EPSG4326_geometry.getCentroid().getCoordinate().y != rs.getDouble("EPSG4326_CENTROID_Y")) {
                    LOG.info("Inconsistent Y for centroid : {}", rs.getDouble("EPSG4326_CENTROID_Y"));
                    return false;
                }
                if (EPSG4326_geometry.getArea() != rs.getDouble("EPSG4326_AREA")) {
                    LOG.info("Inconsistent area: {}", rs.getDouble("EPSG4326_AREA"));
                    return false;
                }
                if (original_geometry.isEmpty() == rs.getBoolean("IS_CLOSED")) {
                    LOG.info("Inconsistent area: {}", rs.getBoolean("IS_CLOSED"));
                    return false;
                }
                if (original_geometry.isSimple() != rs.getBoolean("IS_SIMPLE")) {
                    LOG.info("Inconsistent area: {}", rs.getBoolean("IS_SIMPLE"));
                    return false;
                }
                if (original_geometry.isValid() != rs.getBoolean("IS_VALID")) {
                    LOG.info("Inconsistent area: {}", rs.getBoolean("IS_VALID"));
                    return false;
                }

                DocumentImpl doc = null;
                try {
                    doc = (DocumentImpl) broker.getXMLResource(XmldbURI.create(rs.getString("DOCUMENT_URI")));
                } catch (final PermissionDeniedException e) {
                    //The broker has no right on the document
                    LOG.error(e);
                    return false;
                }
                final NodeId nodeId = new DLN(rs.getInt("NODE_ID_UNITS"), rs.getBytes("NODE_ID"), 0);
                final IStoredNode node = broker.objectWith(new NodeProxy(null, doc, nodeId));
                if (node == null) {
                    LOG.info("Node {}doesn't exist", nodeId);
                    return false;
                }
                if (!AbstractGMLJDBCIndexWorker.GML_NS.equals(node.getNamespaceURI())) {
                    LOG.info("GML indexed node ({}) is in the '{}' namespace. '" + AbstractGMLJDBCIndexWorker.GML_NS + "' was expected !", node.getNodeId(), node.getNamespaceURI());
                    return false;
                }
                if (!original_geometry.getGeometryType().equals(node.getLocalName())) {
                    if ("Box".equals(node.getLocalName()) && "Polygon".equals(original_geometry.getGeometryType())) {
                        LOG.debug("GML indexed node ({}) is a gml:Box indexed as a polygon", node.getNodeId());
                    } else {
                        LOG.info("GML indexed node ({}) has '{}' as its local name. '{}' was expected !", node.getNodeId(), node.getLocalName(), original_geometry.getGeometryType());
                        return false;
                    }
                }
                LOG.info(node);
            }
            return true;

        } catch (final ParseException e) {
            //Transforms the exception into an SQLException.
            //Very unlikely to happen though...
            final SQLException ee = new SQLException(e.getMessage());
            ee.initCause(e);
            throw ee;
        }
    }

    @Override
    public <T extends IStoredNode> IStoredNode getReindexRoot(final IStoredNode<T> node, final NodePath path, final boolean insert, final boolean includeSelf) {
        return null;
    }

    @Override
    public QueryRewriter getQueryRewriter(final XQueryContext context) {
        return null;
    }
}
