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
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.IndexUseReporter;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Base64Binary;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.w3c.dom.Element;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.WKBWriter;
import com.vividsolutions.jts.io.WKTWriter;

public class FunGeometricProperties extends BasicFunction implements IndexUseReporter {
	
	boolean hasUsedIndex = false;
	
	protected WKTWriter wktWriter = new WKTWriter();
	protected WKBWriter wkbWriter = new WKBWriter();
	
    public final static FunctionSignature[] signatures = {
    	new FunctionSignature(
            new QName("getWKT", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the WKT representation of geometry $a",
            new SequenceType[]{
                    new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE),
            },
            new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
        ),
    	new FunctionSignature(
    		new QName("getWKB", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
    		"Returns the WKB representation of geometry $a",
            new SequenceType[]{
	    		new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
            },
            new SequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE)
        ),        
    	new FunctionSignature(
    		new QName("getMinX", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
    		"Returns the minimal X of geometry $a",
            new SequenceType[]{
	    		new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
            },
            new SequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE)
        ),
    	new FunctionSignature(
    		new QName("getMaxX", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
    		"Returns the maximal X of geometry $a",
            new SequenceType[]{
	    		new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
            },
            new SequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE)
        ),
    	new FunctionSignature(
    		new QName("getMinY", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
    		"Returns the minimal Y of geometry $a",
            new SequenceType[]{
	    		new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
            },
            new SequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE)
        ),
    	new FunctionSignature(
    		new QName("getMaxY", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
    		"Returns the maximal Y of geometry $a",
            new SequenceType[]{
	    		new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
            },
            new SequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE)
        ),
    	new FunctionSignature(
    		new QName("getCentroidX", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
    		"Returns the X of centroid of geometry $a",
            new SequenceType[]{
	    		new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
            },
            new SequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE)
        ),
    	new FunctionSignature(
    		new QName("getCentroidY", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
    		"Returns the Y of centroid of geometry $a",
            new SequenceType[]{
	    		new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
            },
            new SequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE)
        ),
    	new FunctionSignature(
    		new QName("getArea", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
    		"Returns the area of geometry $a",
            new SequenceType[]{
	    		new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
            },
            new SequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE)
        ),
    	new FunctionSignature(
                new QName("getEPSG4326WKT", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
                "Returns the WKT representation of geometry $a in the EPSG:4326 SRS",
                new SequenceType[]{
                        new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE),
                },
                new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
            ),         
    	new FunctionSignature(
    		new QName("getEPSG4326WKB", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
    		"Returns the WKB representation of geometry $a in the EPSG:4326 SRS",
            new SequenceType[]{
	    		new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
            },
            new SequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE)
        ),         
    	new FunctionSignature(
    		new QName("getEPSG4326MinX", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
    		"Returns the minimal X of geometry $a in the EPSG:4326 SRS",
            new SequenceType[]{
	    		new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
            },
            new SequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE)
        ),
    	new FunctionSignature(
    		new QName("getEPSG4326MaxX", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
    		"Returns the maximal X of geometry $a in the EPSG:4326 SRS",
            new SequenceType[]{
	    		new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
            },
            new SequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE)
        ),
    	new FunctionSignature(
    		new QName("getEPSG4326MinY", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
    		"Returns the minimal Y of geometry $a in the EPSG:4326 SRS",
            new SequenceType[]{
	    		new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
            },
            new SequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE)
        ),
    	new FunctionSignature(
    		new QName("getEPSG4326MaxY", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
    		"Returns the maximal Y of geometry $a in the EPSG:4326 SRS",
            new SequenceType[]{
	    		new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
            },
            new SequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE)
        ),
    	new FunctionSignature(
    		new QName("getEPSG4326CentroidX", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
    		"Returns the X of centroid of geometry $a in the EPSG:4326 SRS",
            new SequenceType[]{
	    		new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
            },
            new SequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE)
        ),
    	new FunctionSignature(
    		new QName("getEPSG4326CentroidY", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
    		"Returns the Y of centroid of geometry $a in the EPSG:4326 SRS",
            new SequenceType[]{
	    		new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
            },
            new SequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE)
        ),
    	new FunctionSignature(
    		new QName("getEPSG4326Area", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
    		"Returns the area of geometry $a in the EPSG:4326 SRS",
            new SequenceType[]{
	    		new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
            },
            new SequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE)
        ),            
        new FunctionSignature(
            new QName("getSRS", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the spatial reference system of geometry $a",
            new SequenceType[]{
                new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE),
            },
            new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
        ),
        new FunctionSignature(
            new QName("getGeometryType", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the type of geometry $a",
            new SequenceType[]{
                new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE),
            },
            new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
        ),
        new FunctionSignature(
            new QName("isClosed", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns if geometry $a is closed",
            new SequenceType[]{
                new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE),
            },
            new SequenceType(Type.BOOLEAN, Cardinality.ZERO_OR_ONE)
        ),
        new FunctionSignature(
            new QName("isSimple", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns if geometry $a is simple",
            new SequenceType[]{
                new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE),
            },
            new SequenceType(Type.BOOLEAN, Cardinality.ZERO_OR_ONE)
        ),
        new FunctionSignature(
            new QName("isValid", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns if geometry $a is valid",
            new SequenceType[]{
                new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE),
            },
            new SequenceType(Type.BOOLEAN, Cardinality.ZERO_OR_ONE)
        )
   	};

    public FunGeometricProperties(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
    	Sequence result = null;
    	Sequence nodes = args[0];        
        if (nodes.isEmpty())
            result = Sequence.EMPTY_SEQUENCE;
        else {
        	try {
	        	Geometry geometry = null;
	        	String sourceCRS = null;
	        	AbstractGMLJDBCIndexWorker indexWorker = (AbstractGMLJDBCIndexWorker)
		        	context.getBroker().getIndexController().getWorkerByIndexId(AbstractGMLJDBCIndex.ID);
		        if (indexWorker == null)
		        	throw new XPathException("Unable to find a spatial index worker");
	        	String propertyName = null;
				if (isCalledAs("getWKT")) {
					propertyName = "WKT";
				} else if (isCalledAs("getWKB")) {
					propertyName = "WKB";						
				} else if (isCalledAs("getMinX")) {
					propertyName = "MINX";						
				} else if (isCalledAs("getMaxX")) {					
					propertyName = "MAXX";
				} else if (isCalledAs("getMinY")) {
					propertyName = "MINY";
				} else if (isCalledAs("getMaxY")) {
					propertyName = "MAXY";
				} else if (isCalledAs("getCentroidX")) {
					propertyName = "CENTROID_X";
				} else if (isCalledAs("getCentroidY")) {
					propertyName = "CENTROID_Y";
				} else if (isCalledAs("getArea")) {
					propertyName = "AREA";
				} else if (isCalledAs("getEPSG4326WKT")) {
					propertyName = "EPSG4326_WKT";
				} else if (isCalledAs("getEPSG4326WKB")) {
					propertyName = "EPSG4326_WKB";							
				} else if (isCalledAs("getEPSG4326MinX")) {
					propertyName = "EPSG4326_MINX";						
				} else if (isCalledAs("getEPSG4326MaxX")) {					
					propertyName = "EPSG4326_MAXX";
				} else if (isCalledAs("getEPSG4326MinY")) {
					propertyName = "EPSG4326_MINY";
				} else if (isCalledAs("getEPSG4326MaxY")) {
					propertyName = "EPSG4326_MAXY";
				} else if (isCalledAs("getEPSG4326CentroidX")) {
					propertyName = "EPSG4326_CENTROID_X";
				} else if (isCalledAs("getEPSG4326CentroidY")) {
					propertyName = "EPSG4326_CENTROID_Y";
				} else if (isCalledAs("getEPSG4326Area")) {
					propertyName = "EPSG4326_AREA";
				} else if (isCalledAs("getSRS")) {
					propertyName = "SRS_NAME";
				} else if (isCalledAs("getGeometryType")) {
					propertyName = "GEOMETRY_TYPE";
				} else if (isCalledAs("isClosed")) {
					propertyName = "IS_CLOSED";
				} else if (isCalledAs("isSimple")) {
					propertyName = "IS_SIMPLE";
				} else if (isCalledAs("isValid")) {
					propertyName = "IS_VALID";
				} else {
					throw new XPathException("Unknown spatial property: " + mySignature.getName().getLocalName());
				}
		        NodeValue geometryNode = (NodeValue) nodes.itemAt(0);
				if (geometryNode.getImplementationType() == NodeValue.PERSISTENT_NODE) {
					if (propertyName != null) {
						//The node should be indexed : get its property
						result = indexWorker.getGeometricPropertyForNode(context.getBroker(), (NodeProxy)geometryNode, propertyName);
						hasUsedIndex = true;
					} else {
						//Or, at least, its geometry for further processing
						if (propertyName.indexOf("EPSG4326") != Constants.STRING_NOT_FOUND) {
							geometry = indexWorker.getGeometryForNode(context.getBroker(), (NodeProxy)geometryNode, true);
							sourceCRS = "EPSG:4326";
						} else {
							geometry = indexWorker.getGeometryForNode(context.getBroker(), (NodeProxy)geometryNode, false);
							sourceCRS = indexWorker.getGeometricPropertyForNode(context.getBroker(), (NodeProxy)geometryNode, "SRS_NAME").getStringValue();
						}
					}
				}
				if (result == null) {
		        	//builds the geometry
					if (geometry == null) {
						sourceCRS = ((Element)geometryNode.getNode()).getAttribute("srsName").trim();
			        	geometry = indexWorker.streamNodeToGeometry(context, geometryNode);		            	
					}					
		        	if (geometry == null) 
		        		throw new XPathException("Unable to get a geometry from the node");
					//Transform the geometry to EPSG:4326 if relevant
					if (propertyName.indexOf("EPSG4326") != Constants.STRING_NOT_FOUND) {
						geometry = indexWorker.transformGeometry(geometry, sourceCRS, "EPSG:4326");
						if (isCalledAs("getEPSG4326WKT")) {
							result = new StringValue(wktWriter.write(geometry));
						} else if (isCalledAs("getEPSG4326WKB")) {
							result = new Base64Binary(wkbWriter.write(geometry));
						} else if (isCalledAs("getEPSG4326MinX")) {
							result = new DoubleValue(geometry.getEnvelopeInternal().getMinX());
						} else if (isCalledAs("getEPSG4326MaxX")) {
							result = new DoubleValue(geometry.getEnvelopeInternal().getMaxX());
						} else if (isCalledAs("getEPSG4326MinY")) {
							result = new DoubleValue(geometry.getEnvelopeInternal().getMinY());
						} else if (isCalledAs("getEPSG4326MaxY")) {
							result = new DoubleValue(geometry.getEnvelopeInternal().getMaxY());
						} else if (isCalledAs("getEPSG4326CentroidX")) {
							result = new DoubleValue(geometry.getCentroid().getX());
						} else if (isCalledAs("getEPSG4326CentroidY")) {
							result = new DoubleValue(geometry.getCentroid().getY());
						} else if (isCalledAs("getEPSG4326Area")) {
							result = new DoubleValue(geometry.getArea());
						}
					} else if (isCalledAs("getWKT")) {						
						result = new StringValue(wktWriter.write(geometry));
					} else if (isCalledAs("getWKB")) {
			            result = new Base64Binary(wkbWriter.write(geometry));
					} else if (isCalledAs("getMinX")) {
						result = new DoubleValue(geometry.getEnvelopeInternal().getMinX());
					} else if (isCalledAs("getMaxX")) {
						result = new DoubleValue(geometry.getEnvelopeInternal().getMaxX());
					} else if (isCalledAs("getMinY")) {
						result = new DoubleValue(geometry.getEnvelopeInternal().getMinY());
					} else if (isCalledAs("getMaxY")) {
						result = new DoubleValue(geometry.getEnvelopeInternal().getMaxY());
					} else if (isCalledAs("getCentroidX")) {
						result = new DoubleValue(geometry.getCentroid().getX());
					} else if (isCalledAs("getCentroidY")) {
						result = new DoubleValue(geometry.getCentroid().getY());
					} else if (isCalledAs("getArea")) {
						result = new DoubleValue(geometry.getArea());
					} else if (isCalledAs("getSRS")) {
						result = new StringValue(((Element)geometryNode).getAttribute("srsName"));
					} else if (isCalledAs("getGeometryType")) {
						result = new StringValue(geometry.getGeometryType());
					} else if (isCalledAs("isClosed")) {
						result = new BooleanValue(!geometry.isEmpty());
					} else if (isCalledAs("isSimple")) {
						result = new BooleanValue(geometry.isSimple());
					} else if (isCalledAs("isValid")) {
						result = new BooleanValue(geometry.isValid());
					} else {
						throw new XPathException("Unknown spatial property: " + mySignature.getName().getLocalName());
					}
		        }
        	} catch (SpatialIndexException e) {
        		throw new XPathException(e);
        	}
        }
        return result;
    }
    
    public boolean hasUsedIndex() {
        return hasUsedIndex;
    }    
    
}
