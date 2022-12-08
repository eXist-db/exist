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
import org.exist.indexing.spatial.AbstractGMLJDBCIndex.SpatialOperator;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.IndexUseReporter;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.w3c.dom.Element;

import org.locationtech.jts.geom.Geometry;

/**
 * @author <a href="mailto:pierrick.brihaye@free.fr">Pierrick Brihaye</a>
 * @author ljo
 */
public class FunSpatialSearch extends BasicFunction implements IndexUseReporter {
    protected static final FunctionParameterSequenceType NODES_PARAMETER = new FunctionParameterSequenceType("nodes", Type.NODE, Cardinality.ZERO_OR_MORE, "The nodes");
    protected static final FunctionParameterSequenceType GEOMETRY_PARAMETER = new FunctionParameterSequenceType("geometry", Type.NODE, Cardinality.ZERO_OR_ONE, "The geometry");
    protected static final Logger logger = LogManager.getLogger(FunSpatialSearch.class);
    boolean hasUsedIndex = false;

    public final static FunctionSignature[] signatures = {
        new FunctionSignature(
            new QName("equals", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the nodes in $nodes that contain a geometry which is equal to geometry $geometry",
            new SequenceType[] { NODES_PARAMETER, GEOMETRY_PARAMETER },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "the nodes in $nodes that contain a geometry which is equal to geometry $geometry")
        ),
        new FunctionSignature(
            new QName("disjoint", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the nodes in $nodes that contain a geometry which is disjoint with geometry $geometry",
            new SequenceType[] { NODES_PARAMETER, GEOMETRY_PARAMETER },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "the nodes in $nodes that contain a geometry which is disjoint with geometry $geometry")
        ),
        new FunctionSignature(
            new QName("intersects", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the nodes in $nodes that contain a geometry which instersects with geometry $geometry",
            new SequenceType[] { NODES_PARAMETER, GEOMETRY_PARAMETER },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "the nodes in $nodes that contain a geometry which instersects with geometry $geometry")
        ),
        new FunctionSignature(
            new QName("touches", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the nodes in $nodes that contain a geometry which touches geometry $geometry",
            new SequenceType[] { NODES_PARAMETER, GEOMETRY_PARAMETER },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "the nodes in $nodes that contain a geometry which touches geometry $geometry")
        ),
        new FunctionSignature(
            new QName("crosses", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the nodes in $nodes that contain a geometry which crosses geometry $geometry",
            new SequenceType[] { NODES_PARAMETER, GEOMETRY_PARAMETER },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "the nodes in $nodes that contain a geometry which touches geometry $geometry")
        ),
        new FunctionSignature(
            new QName("within", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the nodes in $nodes that contain a geometry which is within geometry $geometry",
            new SequenceType[] { NODES_PARAMETER, GEOMETRY_PARAMETER },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "the nodes in $nodes that contain a geometry which is within geometry $geometry")
        ),
        new FunctionSignature(
            new QName("contains", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the nodes in $nodes that contain a geometry which contains geometry $geometry",
            new SequenceType[] { NODES_PARAMETER, GEOMETRY_PARAMETER },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "the nodes in $nodes that contain a geometry which contains geometry $geometry")
        ),
        new FunctionSignature(
            new QName("overlaps", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the nodes in $nodes that contain a geometry which overlaps geometry $geometry",
            new SequenceType[] { NODES_PARAMETER, GEOMETRY_PARAMETER },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "the nodes in $nodes that contain a geometry which overlaps geometry $geometry")
        )
    };

    public FunSpatialSearch(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        Sequence result = null;
        Sequence nodes = args[0];

        if (nodes.isEmpty()) {
            result = Sequence.EMPTY_SEQUENCE;
        } else if (args[1].isEmpty()) {
            //TODO : to be discussed. We could also return an empty sequence here
            result = nodes;
        } else {
            try {
                AbstractGMLJDBCIndexWorker indexWorker = (AbstractGMLJDBCIndexWorker)
                    context.getBroker().getIndexController().getWorkerByIndexId(AbstractGMLJDBCIndex.ID);
                if (indexWorker == null) {
                    logger.error("Unable to find a spatial index worker");
                    throw new XPathException(this, "Unable to find a spatial index worker");
                }
                Geometry EPSG4326_geometry = null;
                NodeValue geometryNode = (NodeValue) args[1].itemAt(0);
                if (geometryNode.getImplementationType() == NodeValue.PERSISTENT_NODE) 
                    //Get the geometry from the index if available
                    EPSG4326_geometry = indexWorker.getGeometryForNode(context.getBroker(), (NodeProxy)geometryNode, true);
                if (EPSG4326_geometry == null) {
                    String sourceCRS = ((Element)geometryNode.getNode()).getAttribute("srsName").trim();
                    Geometry geometry = indexWorker.streamNodeToGeometry(context, geometryNode);
                    EPSG4326_geometry = indexWorker.transformGeometry(geometry, sourceCRS, "EPSG:4326");
                }
                if (EPSG4326_geometry == null) {
                    logger.error("Unable to get a geometry from the node");
                    throw new XPathException(this, "Unable to get a geometry from the node");
                }
                int spatialOp = SpatialOperator.UNKNOWN;
                if (isCalledAs("equals"))
                    spatialOp = SpatialOperator.EQUALS;
                else if (isCalledAs("disjoint"))
                    spatialOp = SpatialOperator.DISJOINT;
                else if (isCalledAs("intersects"))
                    spatialOp = SpatialOperator.INTERSECTS;
                else if (isCalledAs("touches"))
                    spatialOp = SpatialOperator.TOUCHES;
                else if (isCalledAs("crosses"))
                    spatialOp = SpatialOperator.CROSSES;
                else if (isCalledAs("within"))
                    spatialOp = SpatialOperator.WITHIN;
                else if (isCalledAs("contains"))
                    spatialOp = SpatialOperator.CONTAINS;
                else if (isCalledAs("overlaps"))
                    spatialOp = SpatialOperator.OVERLAPS;
                //Search the EPSG:4326 in the index
                result = indexWorker.search(context.getBroker(), nodes.toNodeSet(), EPSG4326_geometry, spatialOp);
                hasUsedIndex = true;
            } catch (SpatialIndexException e) {
                logger.error(e.getMessage(), e);
                throw new XPathException(this, e);
            }
        }
        return result;
    }

    public boolean hasUsedIndex() {
        return hasUsedIndex;
    }
}
