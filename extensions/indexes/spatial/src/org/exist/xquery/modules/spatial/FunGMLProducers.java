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
package org.exist.xquery.modules.spatial;        

import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.indexing.spatial.AbstractGMLJDBCIndex;
import org.exist.indexing.spatial.AbstractGMLJDBCIndexWorker;
import org.exist.indexing.spatial.SpatialIndexException;
import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.memtree.MemTreeBuilder;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.IndexUseReporter;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;
import com.vividsolutions.jts.operation.buffer.BufferOp;

public class FunGMLProducers extends BasicFunction implements IndexUseReporter {
	
	boolean hasUsedIndex = false;

    public final static FunctionSignature[] signatures = {
    	new FunctionSignature(
            new QName("transform", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the GML representation of geometry $a with the SRS $b",
            new SequenceType[]{
            	new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE),
            	new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
            },
            new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
        ),
    	new FunctionSignature(
            new QName("WKTtoGML", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the GML representation of WKT $a with the SRS $b",
            new SequenceType[]{
            	new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
            	new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
            },
            new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
        ),
       	new FunctionSignature(
            new QName("buffer", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the GML representation of a buffer around geometry $a having width $b in its CRS. " +
            "Curves will be represented by 8 segments per circle quadrant.",
            new SequenceType[]{
            	new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE),
            	new SequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE)
            },
            new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
        ),
       	new FunctionSignature(
            new QName("buffer", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the GML representation of a buffer around geometry $a having width $b in its CRS. " +
            "Curves will be represented by $c segments per circle quadrant.",
            new SequenceType[]{
            	new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE),
            	new SequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE),
            	new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE)
            },
            new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
        ),
       	new FunctionSignature(
            new QName("buffer", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the GML representation of a buffer around geometry $a having width $b in its CRS. " +
            "Curves will be represented by $c segments per circle quadrant.",
            new SequenceType[]{
            	new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE),
            	new SequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE),
            	new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE),
            	new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE)
            },
            new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
        ),
    	new FunctionSignature(
            new QName("getBbox", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the GML representation of the bounding box of geometry $a.",
            new SequenceType[]{
            	new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
            },
            new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
        ),         
    	new FunctionSignature(
            new QName("convexHull", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the GML representation of the convex hull of geometry $a.",
            new SequenceType[]{
            	new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
            },
            new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
        ), 
    	new FunctionSignature(
            new QName("boundary", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the GML representation of the boundary of geometry $a.",
            new SequenceType[]{
            	new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
            },
            new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
        ), 
    	new FunctionSignature(
            new QName("intersection", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the GML representation of the intersection of geometry $a and geometry $b.",
            new SequenceType[]{
            	new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE),
            	new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
            },
            new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
        ), 
    	new FunctionSignature(
            new QName("union", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the GML representation of the union of geometry $a and geometry $b.",
            new SequenceType[]{
            	new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE),
            	new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
            },
            new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
        ), 
    	new FunctionSignature(
            new QName("difference", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the GML representation of the difference of geometry $a and geometry $b.",
            new SequenceType[]{
            	new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE),
            	new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
            },
            new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
        ), 
    	new FunctionSignature(
            new QName("symetricDifference", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the GML representation of the symetric difference of geometry $a and geometry $b.",
            new SequenceType[]{
            	new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE),
            	new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
            },
            new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
        ) 
   	};
    
    public FunGMLProducers(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
    	Sequence result = null; 
    	try {
        	AbstractGMLJDBCIndexWorker indexWorker = (AbstractGMLJDBCIndexWorker)
	        	context.getBroker().getIndexController().getIndexWorkerById(AbstractGMLJDBCIndex.ID);
	        if (indexWorker == null)
	        	throw new XPathException("Unable to find a spatial index worker");
	        Geometry geometry = null;
	        String srsName = null;
	        if (isCalledAs("transform")) {
	        	if (args[0].isEmpty())
	                result = Sequence.EMPTY_SEQUENCE;
	        	else {
		        	NodeValue geometryNode = (NodeValue) args[0].itemAt(0);
		        	String sourceSRS = null;
		        	//Try to get the geometry from the index
		        	if (geometryNode.getImplementationType() == NodeValue.PERSISTENT_NODE) {
		        		geometry = indexWorker.getGeometryForNode(context.getBroker(), (NodeProxy)geometryNode);
		        		sourceSRS = indexWorker.getGeometricPropertyForNode(context.getBroker(), (NodeProxy)geometryNode, "SRS_NAME").getStringValue();
		        		hasUsedIndex = true;
		        	//Otherwise, build it
		        	} else { 		        		
		        		geometry = indexWorker.streamGeometryForNode(context, geometryNode);
		            	//Argl ! No SRS !
		            	//sourceSRS = ((Element)geometryNode).getAttribute("srsName").trim();
		            	//Erroneous workaround
		        		sourceSRS = "osgb:BNG";
		        	}
		        	srsName = args[1].itemAt(0).getStringValue().trim();
		        	geometry = indexWorker.transformGeometry(geometry, sourceSRS, srsName);
	        	}	        	
	        } else if (isCalledAs("WKTtoGML")) {
	        	if (args[0].isEmpty())
	                result = Sequence.EMPTY_SEQUENCE;
	        	else {
		        	String wkt = args[0].itemAt(0).getStringValue();
			        WKTReader wktReader = new WKTReader();
			        try {
			        	geometry = wktReader.read(wkt);
			        	srsName = args[1].itemAt(0).getStringValue().trim();
			        } catch (ParseException e) {
			        	throw new XPathException(e);	
			        }
	        	}
	        } else if (isCalledAs("buffer")) {
	        	if (args[0].isEmpty())
	                result = Sequence.EMPTY_SEQUENCE;
	        	else {
		        	NodeValue geometryNode = (NodeValue) args[0].itemAt(0);	        		        	
		        	//Try to get the geometry from the index
		        	if (geometryNode.getImplementationType() == NodeValue.PERSISTENT_NODE) {		        		
		        		geometry = indexWorker.getGeometryForNode(context.getBroker(), (NodeProxy)geometryNode);
		        		srsName = indexWorker.getGeometricPropertyForNode(context.getBroker(), (NodeProxy)geometryNode, "SRS_NAME").getStringValue();
		        		hasUsedIndex = true;
		        	//Otherwise, build it
		        	} else { 		        		
		        		geometry = indexWorker.streamGeometryForNode(context, geometryNode);
		            	//Argl ! No SRS !
		            	//srsName = ((Element)geometryNode).getAttribute("srsName").trim();
		            	//Erroneous workaround
		            	srsName = "osgb:BNG";
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
			        		throw new XPathException("Invalid line end style");	
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
		        		geometry = indexWorker.getGeometryForNode(context.getBroker(), (NodeProxy)geometryNode);
		        		srsName = indexWorker.getGeometricPropertyForNode(context.getBroker(), (NodeProxy)geometryNode, "SRS_NAME").getStringValue();
		        		hasUsedIndex = true;
		        	//Otherwise, build it
		        	} else { 		        		
		        		geometry = indexWorker.streamGeometryForNode(context, geometryNode);
		            	//Argl ! No SRS !
		            	//srsName = ((Element)geometryNode).getAttribute("srsName").trim();
		            	//Erroneous workaround
		            	srsName = "osgb:BNG";
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
		        		geometry = indexWorker.getGeometryForNode(context.getBroker(), (NodeProxy)geometryNode);
		        		srsName = indexWorker.getGeometricPropertyForNode(context.getBroker(), (NodeProxy)geometryNode, "SRS_NAME").getStringValue();
		        		hasUsedIndex = true;
		        	//Otherwise, build it
		        	} else { 		        		
		        		geometry = indexWorker.streamGeometryForNode(context, geometryNode);
		            	//Argl ! No SRS !
		            	//srsName = ((Element)geometryNode).getAttribute("srsName").trim();
		            	//Erroneous workaround
		            	srsName = "osgb:BNG";
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
		        		geometry = indexWorker.getGeometryForNode(context.getBroker(), (NodeProxy)geometryNode);
		        		srsName = indexWorker.getGeometricPropertyForNode(context.getBroker(), (NodeProxy)geometryNode, "SRS_NAME").getStringValue();
		        		hasUsedIndex = true;
		        	//Otherwise, build it
		        	} else { 		        		
		        		geometry = indexWorker.streamGeometryForNode(context, geometryNode);
		            	//Argl ! No SRS !
		            	//srsName = ((Element)geometryNode).getAttribute("srsName").trim();
		            	//Erroneous workaround
		            	srsName = "osgb:BNG";
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
		        		geometry1 = indexWorker.getGeometryForNode(context.getBroker(), (NodeProxy)geometryNode1);
		        		srsName1 = indexWorker.getGeometricPropertyForNode(context.getBroker(), (NodeProxy)geometryNode1, "SRS_NAME").getStringValue();
		        		hasUsedIndex = true;
		        	}
		        	if (geometryNode2.getImplementationType() == NodeValue.PERSISTENT_NODE) {
		        		geometry2 = indexWorker.getGeometryForNode(context.getBroker(), (NodeProxy)geometryNode2);
		        		srsName2 = indexWorker.getGeometricPropertyForNode(context.getBroker(), (NodeProxy)geometryNode2, "SRS_NAME").getStringValue();
		        		hasUsedIndex = true;
		        	}
		        	//Otherwise build them
		            if (geometry1 == null) {
		            	geometry1 = indexWorker.streamGeometryForNode(context, geometryNode1);	
		            	//Argl ! No SRS !
		            	//srsName1 = ((Element)geometryNode1).getAttribute("srsName").trim();
		            	//Erroneous workaround
		            	srsName1 = "osgb:BNG";
		            }
		        	if (geometry2 == null) {
		            	geometry2 = indexWorker.streamGeometryForNode(context, geometryNode2);
		        		//Argl ! No SRS !
		            	//srsName2 = ((Element)geometryNode2).getAttribute("srsName").trim();
		            	//Erroneous workaround
		            	srsName2 = "osgb:BNG";		            	
		        	}
					
					//Transform the second geometry if necessary
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
	        	}
	        }
	        
	        if (result == null) {	        
		        String gmlPrefix = context.getPrefixForURI(AbstractGMLJDBCIndexWorker.GML_NS);
		        if (gmlPrefix == null) 
		        	throw new XPathException("'" + AbstractGMLJDBCIndexWorker.GML_NS + "' namespace is not defined");	
		
	     		context.pushDocumentContext();
				try {
					MemTreeBuilder builder = context.getDocumentBuilder();
			        DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder);
					result = (NodeValue)indexWorker.getGML(geometry, srsName, receiver);
				} finally {
		            context.popDocumentContext();
		        }
	        }
    	} catch (SpatialIndexException e) {
    		throw new XPathException(e);
    	}        
        return result;
    }
    
    public boolean hasUsedIndex() {
        return hasUsedIndex;
    }    
    
}
