/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
import org.exist.indexing.spatial.AbstractGMLJDBCIndex.SpatialOperator;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.geotools.gml.GMLFilterDocument;
import org.geotools.gml.GMLFilterGeometry;
import org.geotools.gml.GMLHandlerJTS;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.w3c.dom.Element;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.XMLFilterImpl;

import com.vividsolutions.jts.geom.Geometry;

/**
 * Created by IntelliJ IDEA.
 * User: wolf
 * Date: 28-Feb-2007
 * Time: 15:18:59
 * To change this template use File | Settings | File Templates.
 */
public class FunSpatialSearch extends BasicFunction {

    public final static FunctionSignature[] signatures = {
            new FunctionSignature(
                new QName("equals", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
                "Returns the nodes in $a that contain a geometry which is equal to geometry $b",
                new SequenceType[]{
                        new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
                        new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
                },
                new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE)
            ),
            new FunctionSignature(
                new QName("disjoint", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
                "Returns the nodes in $a that contain a geometry which is disjoint with geometry $b",
                new SequenceType[]{
                        new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
                        new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
                },
                new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE)
            ),
            new FunctionSignature(
                new QName("intersects", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
                "Returns the nodes in $a that contain a geometry which instersects with geometry $b",
                new SequenceType[]{
                        new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
                        new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
                },
                new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE)
            ),
            new FunctionSignature(
                new QName("touches", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
                "Returns the nodes in $a that contain a geometry which touches geometry $b",
                new SequenceType[]{
                        new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
                        new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
                },
                new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE)
            ),
            new FunctionSignature(
                new QName("crosses", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
                "Returns the nodes in $a that contain a geometry which crosses geometry $b",
                new SequenceType[]{
                        new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
                        new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
                },
                new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE)
            ),
            new FunctionSignature(
                new QName("within", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
                "Returns the nodes in $a that contain a geometry which is within geometry $b",
                new SequenceType[]{
                        new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
                        new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
                },
                new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE)
            ),
            new FunctionSignature(
                new QName("contains", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
                "Returns the nodes in $a that contain a geometry which contains geometry $b",
                new SequenceType[]{
                        new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
                        new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
                },
                new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE)
            ),
            new FunctionSignature(
                new QName("overlaps", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
                "Returns the nodes in $a that contain a geometry which overlaps geometry $b",
                new SequenceType[]{
                        new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
                        new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
                },
                new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE)
            )                
    	};
    
    private Geometry currentGeometry;

    public FunSpatialSearch(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        Sequence result = null;
    	Sequence nodes = args[0];        
        if (nodes.isEmpty())
            result = Sequence.EMPTY_SEQUENCE;
        else if (args[1].isEmpty())
        	result = nodes;
        else {
        	AbstractGMLJDBCIndexWorker indexWorker = (AbstractGMLJDBCIndexWorker)        	
	        	context.getBroker().getIndexController().getIndexWorkerById(AbstractGMLJDBCIndex.ID);
	        if (indexWorker == null)
	        	throw new XPathException("Unable to find a spatial index worker");
	        Geometry EPSG4326_geometry = null;
	        NodeValue geometryNode = (NodeValue) args[1].itemAt(0);   
			if (geometryNode.getImplementationType() == NodeValue.PERSISTENT_NODE)
				//The node should be indexed
				EPSG4326_geometry = indexWorker.getGeometryForNode(context.getBroker(), (NodeProxy)geometryNode);		
	        if (EPSG4326_geometry == null) {
	        	//builds the geometry
	        	GMLHandlerJTS geometryHandler = new GeometryHandler(); 
	            GMLFilterGeometry geometryFilter = new GMLFilterGeometry(geometryHandler); 
	            GMLFilterDocument handler = new GMLFilterDocument(geometryFilter);	        
	            try {
	            	geometryNode.toSAX(context.getBroker(), (ContentHandler)handler, null);
	            } catch (SAXException e) {
	            	throw new XPathException("Unable to serialize '" + geometryNode + "' as a valid GML geometry", e); 
 	            }
	            if (currentGeometry == null)
	            	throw new XPathException(geometryNode.getNode().getLocalName() + " is not a GML geometry node");
	            //Transform the geometry to EPSG:4326
	            String originSrsName = ((Element)geometryNode).getAttribute("srsName").trim();
		        MathTransform mathTransform = indexWorker.getTransform(originSrsName, "EPSG:4326");
	            if (mathTransform == null) {
	        		throw new XPathException("Unable to get a transformation from '" + originSrsName + "' to 'EPSG:4326'");        		           	
	            }
	            indexWorker.getCoordinateTransformer().setMathTransform(mathTransform);
	            try {
	            	EPSG4326_geometry = indexWorker.getCoordinateTransformer().transform(currentGeometry);
	            } catch (TransformException e) {
	            	throw new XPathException(e);
	            }	        		        
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
	        result = indexWorker.search(context.getBroker(),  nodes.toNodeSet(), EPSG4326_geometry, spatialOp);
        }
        return result;
    }
    
    public int returnsType() {
        return Type.NODE;
    }
    
    private class GeometryHandler extends XMLFilterImpl implements GMLHandlerJTS {
        public void geometry(Geometry geometry) {
        	currentGeometry = geometry;
        }
    }     
}
