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
package org.exist.xquery.modules.spatial;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.QName;
import org.exist.indexing.spatial.AbstractGMLJDBCIndex;
import org.exist.indexing.spatial.AbstractGMLJDBCIndexWorker;
import org.exist.indexing.spatial.SpatialIndexException;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.IndexUseReporter;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.w3c.dom.Element;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.operation.buffer.BufferOp;

/**
 * @author <a href="mailto:pierrick.brihaye@free.fr">Pierrick Brihaye</a>
 * @author ljo
 */
public class FunGMLProducers extends BasicFunction implements IndexUseReporter {

    protected static final Logger logger = LogManager.getLogger(FunGMLProducers.class);
    boolean hasUsedIndex = false;

    public final static FunctionSignature[] signatures = {
        new FunctionSignature(
            new QName("transform", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the GML representation of geometry $geometry with the SRS $srs",
            new SequenceType[]{
                FunSpatialSearch.GEOMETRY_PARAMETER,
                new FunctionParameterSequenceType("srs", Type.STRING, Cardinality.EXACTLY_ONE, "The srs")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_ONE, "the GML representation of geometry $geometry with the SRS $srs")
        ),
        new FunctionSignature(
            new QName("WKTtoGML", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the GML representation of WKT $wkt with the SRS $srs",
            new SequenceType[]{
                new FunctionParameterSequenceType("wkt", Type.STRING, Cardinality.ZERO_OR_ONE, "The wkt"),
                new FunctionParameterSequenceType("srs", Type.STRING, Cardinality.EXACTLY_ONE, "The srs")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_ONE, "the GML representation of WKT $wkt with the SRS $srs")
        ),
        new FunctionSignature(
            new QName("buffer", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the GML representation of a buffer around geometry $geometry having width $width in its CRS. " +
            "Curves will be represented by 8 segments per circle quadrant.",
            new SequenceType[]{
                FunSpatialSearch.GEOMETRY_PARAMETER,
                new FunctionParameterSequenceType("width", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The width")
            },
            new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the GML representation of a buffer around geometry $geometry having width $width in its CRS.")
        ),
        new FunctionSignature(
            new QName("buffer", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the GML representation of a buffer around geometry $geometry having width $width in its CRS. " +
            "Curves will be represented by $segments segments per circle quadrant.",
            new SequenceType[]{
                FunSpatialSearch.GEOMETRY_PARAMETER,
                new FunctionParameterSequenceType("width", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The width"),
                new FunctionParameterSequenceType("segments", Type.INTEGER, Cardinality.EXACTLY_ONE, "The segments")
            },
            new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the GML representation of a buffer around geometry $geometry having width $width in its CRS.")
        ),
        new FunctionSignature(
            new QName("buffer", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the GML representation of a buffer around geometry $geometry having width $width in its CRS. " +
            "Curves will be represented by $segments segments per circle quadrant. The fourth argument denotes the line end style (round, butt or square) as an integer constant.",
            new SequenceType[]{
                FunSpatialSearch.GEOMETRY_PARAMETER,
                new FunctionParameterSequenceType("width", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The width"),
                new FunctionParameterSequenceType("segments", Type.INTEGER, Cardinality.EXACTLY_ONE, "The segments"),
                new FunctionParameterSequenceType("line-end-style", Type.INTEGER, Cardinality.EXACTLY_ONE, "The line-end-style")
            },
            new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the GML representation of a buffer around geometry $geometry having width $width in its CRS.")
        ),
        new FunctionSignature(
            new QName("getBbox", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the GML representation of the bounding box of geometry $geometry.",
            new SequenceType[]{
                FunSpatialSearch.GEOMETRY_PARAMETER
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_ONE, "the GML representation of the bounding box of geometry $geometry.")
        ),
        new FunctionSignature(
            new QName("convexHull", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the GML representation of the convex hull of geometry $geometry.",
            new SequenceType[]{
                FunSpatialSearch.GEOMETRY_PARAMETER
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_ONE, "the GML representation of the convex hull of geometry $geometry.")
        ),
        new FunctionSignature(
            new QName("boundary", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the GML representation of the boundary of geometry $geometry.",
            new SequenceType[]{
                FunSpatialSearch.GEOMETRY_PARAMETER
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_ONE, "the GML representation of the boundary of geometry $geometry.")
        ),
        new FunctionSignature(
            new QName("intersection", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the GML representation of the intersection of geometry $geometry-a and geometry $geometry-b in the SRS of $geometry-a.",
            new SequenceType[]{
                new FunctionParameterSequenceType("geometry-a", Type.NODE, Cardinality.ZERO_OR_ONE, "The geometry-a"),
                new FunctionParameterSequenceType("geometry-b", Type.NODE, Cardinality.ZERO_OR_ONE, "The geometry-b")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_ONE, "the GML representation of the intersection of geometry $geometry-a and geometry $geometry-b in the SRS of $geometry-a.")
        ),
        new FunctionSignature(
            new QName("union", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the GML representation of the union of geometry $geometry-a and geometry $geometry-b in the SRS of $geometry-a.",
            new SequenceType[]{
            	new FunctionParameterSequenceType("geometry-a", Type.NODE, Cardinality.ZERO_OR_ONE, "The geometry-a"),
            	new FunctionParameterSequenceType("geometry-b", Type.NODE, Cardinality.ZERO_OR_ONE, "The geometry-b")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_ONE, "the GML representation of the union of geometry $geometry-a and geometry $geometry-b in the SRS of $geometry-a.")
        ),
        new FunctionSignature(
            new QName("difference", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the GML representation of the difference of geometry $geometry-a and geometry $geometry-b in the SRS of $geometry-a.",
            new SequenceType[]{
                new FunctionParameterSequenceType("geometry-a", Type.NODE, Cardinality.ZERO_OR_ONE, "The geometry-a"),
                new FunctionParameterSequenceType("geometry-b", Type.NODE, Cardinality.ZERO_OR_ONE, "The geometry-b")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_ONE, "the GML representation of the difference of geometry $geometry-a and geometry $geometry-b in the SRS of $geometry-a.")
        ),
        new FunctionSignature(
            new QName("symetricDifference", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the GML representation of the symetric difference of geometry $geometry-a and geometry $geometry-b in the SRS of $geometry-a.",
            new SequenceType[]{
                new FunctionParameterSequenceType("geometry-a", Type.NODE, Cardinality.ZERO_OR_ONE, "The geometry-a"),
                new FunctionParameterSequenceType("geometry-b", Type.NODE, Cardinality.ZERO_OR_ONE, "The geometry-b")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_ONE, "the GML representation of the symetric difference of geometry $geometry-a and geometry $geometry-b in the SRS of $geometry-a.")
        )
    };

    public FunGMLProducers(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        Sequence result = null; 
        try {
            AbstractGMLJDBCIndexWorker indexWorker = (AbstractGMLJDBCIndexWorker)
                context.getBroker().getIndexController().getWorkerByIndexId(AbstractGMLJDBCIndex.ID);
            if (indexWorker == null) {
                logger.error("Unable to find a spatial index worker");
                throw new XPathException(this, "Unable to find a spatial index worker");
            }
            Geometry geometry = null;
            String targetSRS = null;
            if (isCalledAs("transform")) {
                if (args[0].isEmpty())
                    result = Sequence.EMPTY_SEQUENCE;
                else {
                    NodeValue geometryNode = (NodeValue) args[0].itemAt(0);
                    //Try to get the geometry from the index
                    String sourceSRS = null;
                    if (geometryNode.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                        sourceSRS = indexWorker.getGeometricPropertyForNode(context, (NodeProxy)geometryNode, "SRS_NAME").getStringValue();
                        geometry = indexWorker.getGeometryForNode(context.getBroker(), (NodeProxy)geometryNode, false);		        		
                        hasUsedIndex = true;
                    }
                    //Otherwise, build it
                    if (geometry == null) {
                        sourceSRS = ((Element)geometryNode.getNode()).getAttribute("srsName").trim();
                        geometry = indexWorker.streamNodeToGeometry(context, geometryNode);
                    }
                    if (geometry == null) {
                        logger.error("Unable to get a geometry from the node");
                        throw new XPathException(this, "Unable to get a geometry from the node");
                    }
                    targetSRS = args[1].itemAt(0).getStringValue().trim();
                    geometry = indexWorker.transformGeometry(geometry, sourceSRS, targetSRS);
                }
            } else if (isCalledAs("WKTtoGML")) {
                if (args[0].isEmpty())
                    result = Sequence.EMPTY_SEQUENCE;
                else {
                    String wkt = args[0].itemAt(0).getStringValue();
                    WKTReader wktReader = new WKTReader();
                    try {
                        geometry = wktReader.read(wkt);
                    } catch (ParseException e) {
                        logger.error(e.getMessage());
                        throw new XPathException(this, e);
                    }
                    if (geometry == null) {
                        logger.error("Unable to get a geometry from the node");
                        throw new XPathException(this, "Unable to get a geometry from the node");
                    }
                    targetSRS = args[1].itemAt(0).getStringValue().trim();
                }
            } else if (isCalledAs("buffer")) {
                if (args[0].isEmpty())
                    result = Sequence.EMPTY_SEQUENCE;
                else {
                    NodeValue geometryNode = (NodeValue) args[0].itemAt(0);
                    //Try to get the geometry from the index
                    if (geometryNode.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                        targetSRS = indexWorker.getGeometricPropertyForNode(context, (NodeProxy)geometryNode, "SRS_NAME").getStringValue();
                        geometry = indexWorker.getGeometryForNode(context.getBroker(), (NodeProxy)geometryNode, false);
                        hasUsedIndex = true;
                    }
                    //Otherwise, build it
                    if (geometry == null) {
                        targetSRS =  ((Element)geometryNode.getNode()).getAttribute("srsName").trim();
                        geometry = indexWorker.streamNodeToGeometry(context, geometryNode);
                    }
                    if (geometry == null) { 
                        logger.error("Unable to get a geometry from the node");
                        throw new XPathException(this, "Unable to get a geometry from the node");
                    }

                    double distance = ((DoubleValue)args[1].itemAt(0)).getDouble();
                    int quadrantSegments = 8;
                    int endCapStyle = BufferOp.CAP_ROUND;
                    if (getArgumentCount() > 2 && Type.subTypeOf(args[2].itemAt(0).getType(), Type.INTEGER))
                        quadrantSegments = ((IntegerValue)args[2].itemAt(0)).getInt();
                    if (getArgumentCount() > 3 && Type.subTypeOf(args[3].itemAt(0).getType(), Type.INTEGER))
                        endCapStyle = ((IntegerValue)args[3].itemAt(0)).getInt();
                    switch (endCapStyle) {
                        case BufferOp.CAP_ROUND:
                        case BufferOp.CAP_BUTT:
                        case BufferOp.CAP_SQUARE:
                            //OK
                            break;
                        default:
                        {
                            logger.error("Invalid line end style");
                            throw new XPathException(this, "Invalid line end style");
                        }
                    }
                    geometry = geometry.buffer(distance, quadrantSegments, endCapStyle);
                }
            } else if (isCalledAs("getBbox")) {
                if (args[0].isEmpty())
                    result = Sequence.EMPTY_SEQUENCE;
                else {
                    NodeValue geometryNode = (NodeValue) args[0].itemAt(0);
                    //Try to get the geometry from the index
                    if (geometryNode.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                        targetSRS = indexWorker.getGeometricPropertyForNode(context, (NodeProxy)geometryNode, "SRS_NAME").getStringValue();
                        geometry = indexWorker.getGeometryForNode(context.getBroker(), (NodeProxy)geometryNode, false);
                        hasUsedIndex = true;
                    }
                    //Otherwise, build it
                    if (geometry == null) {
                        targetSRS = ((Element)geometryNode.getNode()).getAttribute("srsName").trim();
                        geometry = indexWorker.streamNodeToGeometry(context, geometryNode);
                    }
                    if (geometry == null) {
                        logger.error("Unable to get a geometry from the node");
                        throw new XPathException(this, "Unable to get a geometry from the node");
                    }
                    geometry = geometry.getEnvelope();
                }
            } else if (isCalledAs("convexHull")) {
                if (args[0].isEmpty())
                    result = Sequence.EMPTY_SEQUENCE;
                else {
                    NodeValue geometryNode = (NodeValue) args[0].itemAt(0);
                    //Try to get the geometry from the index
                    if (geometryNode.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                        targetSRS = indexWorker.getGeometricPropertyForNode(context, (NodeProxy)geometryNode, "SRS_NAME").getStringValue();
                        geometry = indexWorker.getGeometryForNode(context.getBroker(), (NodeProxy)geometryNode, false);		        		
                        hasUsedIndex = true;
                    }
                    //Otherwise, build it
                    if (geometry == null) {
                        targetSRS = ((Element)geometryNode.getNode()).getAttribute("srsName").trim();
                        geometry = indexWorker.streamNodeToGeometry(context, geometryNode);
                    }
                    if (geometry == null) { 
                        logger.error("Unable to get a geometry from the node");
                        throw new XPathException(this, "Unable to get a geometry from the node");
                    }
                    geometry = geometry.convexHull();
                }
            } else if (isCalledAs("boundary")) {
                if (args[0].isEmpty())
                    result = Sequence.EMPTY_SEQUENCE;
                else {
                    NodeValue geometryNode = (NodeValue) args[0].itemAt(0);
                    //Try to get the geometry from the index
                    if (geometryNode.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                        targetSRS = indexWorker.getGeometricPropertyForNode(context, (NodeProxy)geometryNode, "SRS_NAME").getStringValue();
                        geometry = indexWorker.getGeometryForNode(context.getBroker(), (NodeProxy)geometryNode, false);
                        hasUsedIndex = true;
                    }
                    //Otherwise, build it
                    if (geometry == null) {
                        targetSRS = ((Element)geometryNode.getNode()).getAttribute("srsName").trim();
                        geometry = indexWorker.streamNodeToGeometry(context, geometryNode);
                    }
                    if (geometry == null) {
                        logger.error("Unable to get a geometry from the node");
                        throw new XPathException(this, "Unable to get a geometry from the node");
                    }
                    geometry = geometry.getBoundary();
                }
            } else {
                Geometry geometry1 = null;
                Geometry geometry2 = null;
                if (args[0].isEmpty() && args[1].isEmpty())
                    result = Sequence.EMPTY_SEQUENCE;
                else if (!args[0].isEmpty() && args[1].isEmpty())
                    result = args[0].itemAt(0).toSequence();
                else if (args[0].isEmpty() && !args[1].isEmpty())
                    result = args[1].itemAt(0).toSequence();
                else {
                    NodeValue geometryNode1 = (NodeValue) args[0].itemAt(0);
                    NodeValue geometryNode2 = (NodeValue) args[1].itemAt(0);
                    String srsName1 = null;
                    String srsName2 = null;
                    //Try to get the geometries from the index
                    if (geometryNode1.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                        srsName1 = indexWorker.getGeometricPropertyForNode(context, (NodeProxy)geometryNode1, "SRS_NAME").getStringValue();
                        geometry1 = indexWorker.getGeometryForNode(context.getBroker(), (NodeProxy)geometryNode1, false);
                        hasUsedIndex = true;
                    }
                    if (geometryNode2.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                        srsName2 = indexWorker.getGeometricPropertyForNode(context, (NodeProxy)geometryNode2, "SRS_NAME").getStringValue();
                        geometry2 = indexWorker.getGeometryForNode(context.getBroker(), (NodeProxy)geometryNode2, false);
                        hasUsedIndex = true;
                    }
                    //Otherwise build them
                    if (geometry1 == null) {
                        srsName1 = ((Element)geometryNode1.getNode()).getAttribute("srsName").trim();
                        geometry1 = indexWorker.streamNodeToGeometry(context, geometryNode1);
                    }
                    if (geometry2 == null) {
                        srsName2 = ((Element)geometryNode2.getNode()).getAttribute("srsName").trim();
                        geometry2 = indexWorker.streamNodeToGeometry(context, geometryNode2);
                    }
                    if (geometry1 == null) {
                        logger.error("Unable to get a geometry from the first node");
                        throw new XPathException(this, "Unable to get a geometry from the first node");
                    }
                    if (geometry2 == null) {
                        logger.error("Unable to get a geometry from the second node");
                        throw new XPathException(this, "Unable to get a geometry from the second node");
                    }
                    if (srsName1 == null)
                        throw new XPathException(this, "Unable to get a SRS for the first geometry");
                    if (srsName2 == null)
                        throw new XPathException(this, "Unable to get a SRS for the second geometry");

                    //Transform the second geometry in the SRS of the first one if necessary
                    if (!srsName1.equalsIgnoreCase(srsName2)) {
                        geometry2 = indexWorker.transformGeometry(geometry2, srsName1, srsName2);
                    }

                    if (isCalledAs("intersection")) {
                        geometry = geometry1.intersection(geometry2);
                    } else if (isCalledAs("union")) {
                        geometry = geometry1.union(geometry2);
                    } else if (isCalledAs("difference")) {	
                        geometry = geometry1.difference(geometry2);
                    } else if (isCalledAs("symetricDifference")) {
                        geometry = geometry1.symDifference(geometry2);
                    }

                    targetSRS = srsName1;
                }
            }

            if (result == null) {
                String gmlPrefix = context.getPrefixForURI(AbstractGMLJDBCIndexWorker.GML_NS);
                if (gmlPrefix == null) {
                    logger.error("namespace is not defined:" + SpatialModule.PREFIX);
                    throw new XPathException(this, "'" + AbstractGMLJDBCIndexWorker.GML_NS + "' namespace is not defined");
                }

                context.pushDocumentContext();
                try {
                    MemTreeBuilder builder = context.getDocumentBuilder();
                    DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(this, builder);
                    result = (NodeValue)indexWorker.streamGeometryToElement(geometry, targetSRS, receiver);
                } finally {
                    context.popDocumentContext();
                }
            }
        } catch (SpatialIndexException e) {
            logger.error(e.getMessage());
            throw new XPathException(this, e);
        }
        return result;
    }

    public boolean hasUsedIndex() {
        return hasUsedIndex;
    }

}
