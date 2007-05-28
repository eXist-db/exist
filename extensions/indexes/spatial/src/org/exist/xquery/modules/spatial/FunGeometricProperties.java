package org.exist.xquery.modules.spatial;        

import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.indexing.spatial.AbstractGMLJDBCIndex;
import org.exist.indexing.spatial.AbstractGMLJDBCIndexWorker;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
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
import com.vividsolutions.jts.io.WKTWriter;

/**
 * Created by IntelliJ IDEA.
 * User: wolf
 * Date: 28-Feb-2007
 * Time: 15:18:59
 * To change this template use File | Settings | File Templates.
 */
public class FunGeometricProperties extends BasicFunction {

    public final static FunctionSignature[] signatures = {
    	//Functions that might depend from the SRS
    	new FunctionSignature(
            new QName("getWKT", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the WKT representation of geometry $a",
            new SequenceType[]{
                    new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE),
            },
            new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
        ),
    	new FunctionSignature(
    		new QName("getWKT", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the WKT representation of geometry $a in the CRS specified by $b",
            new SequenceType[]{
	    		new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE),
	    		new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
            },
            new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
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
    		new QName("getMinX", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
    		"Returns the minimal X of geometry $a in the CRS specified by $b",
            new SequenceType[]{
	    		new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE),
	    		new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
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
    		new QName("getMaxX", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
    		"Returns the maximal X of geometry $a in the CRS specified by $b",
            new SequenceType[]{
	    		new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE),
	    		new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
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
    		new QName("getMinY", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
    		"Returns the minimal Y of geometry $a in the CRS specified by $b",
            new SequenceType[]{
	    		new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE),
	    		new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
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
    		new QName("getMaxY", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
    		"Returns the maximal Y of geometry $a in the CRS specified by $b",
            new SequenceType[]{
	    		new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE),
	    		new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
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
    		new QName("getCentroidX", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
    		"Returns the X of centroid of geometry $a in the CRS specified by $b",
            new SequenceType[]{
	    		new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE),
	    		new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
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
    		new QName("getCentroidY", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
    		"Returns the Y of centroid of geometry $a in the CRS specified by $b",
            new SequenceType[]{
	    		new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE),
	    		new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
            },
            new SequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE)
        ),
    	new FunctionSignature(
        		new QName("area", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
        		"Returns the area of geometry $a",
                new SequenceType[]{
    	    		new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
                },
                new SequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE)
            ),
    	new FunctionSignature(
    		new QName("area", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
    		"Returns the area of geometry $a in the CRS specified by $b",
            new SequenceType[]{
	    		new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE),
	    		new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
            },
            new SequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE)
        ),            
        //Functions that do no depend of the CRS
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
    
    private Geometry currentGeometry;

    public FunGeometricProperties(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
    	Sequence result = null;
    	Sequence nodes = args[0];        
        if (nodes.isEmpty())
            result = Sequence.EMPTY_SEQUENCE;
        else {
        	String targetSrsName= null;
        	Geometry geometry = null;
        	if (getArgumentCount() == 2) 
        		targetSrsName = args[1].itemAt(0).getStringValue().trim();
        	AbstractGMLJDBCIndexWorker indexWorker = (AbstractGMLJDBCIndexWorker)
	        	context.getBroker().getIndexController().getIndexWorkerById(AbstractGMLJDBCIndex.ID);
	        if (indexWorker == null)
	        	throw new XPathException("Unable to find a spatial index worker");
	        NodeValue geometryNode = (NodeValue) nodes.itemAt(0);
			if (geometryNode.getImplementationType() == NodeValue.PERSISTENT_NODE) {
	        	boolean optimizeOnEpsg4326 = false;
	        	//TODO : try to spot equivalent CRS 
	        	optimizeOnEpsg4326 = "EPSG:4326".equalsIgnoreCase(targetSrsName);
	        	String propertyName = null;
				if (isCalledAs("getWKT")) {
					propertyName = optimizeOnEpsg4326 ? "EPSG4326_WKT" : "WKT";
				} else if (isCalledAs("getMinX")) {
					propertyName = optimizeOnEpsg4326 ? "EPSG4326_MINX" : "MINX";
				} else if (isCalledAs("getMaxX")) {					
					propertyName = optimizeOnEpsg4326 ? "EPSG4326_MAXX" : "MAXX";
				} else if (isCalledAs("getMinY")) {
					propertyName = optimizeOnEpsg4326 ? "EPSG4326_MINY" : "MINY";
				} else if (isCalledAs("getMaxY")) {
					propertyName = optimizeOnEpsg4326 ? "EPSG4326_MAXY" : "MAXY";
				} else if (isCalledAs("getCentroidX")) {
					propertyName = optimizeOnEpsg4326 ? "EPSG4326_CENTROID_X" : "CENTROID_X";
				} else if (isCalledAs("getCentroidY")) {
					propertyName = optimizeOnEpsg4326 ? "EPSG4326_CENTROID_Y" : "CENTROID_Y";
				} else if (isCalledAs("area")) {
					propertyName = optimizeOnEpsg4326 ? "EPSG4326_AREA" : "AREA";
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
				} else
					throw new XPathException("Unknown spatial property: " + mySignature.getName().getLocalName());
				if (propertyName != null)
					//The node should be indexed : get its properties
					result = indexWorker.getGeometricPropertyForNode(context.getBroker(), (NodeProxy)geometryNode, propertyName);
				else
					//Or, at least, its geometry for further processing
					geometry = indexWorker.getGeometryForNode(context.getBroker(), (NodeProxy)geometryNode);
			}
			if (result == null) {
				if (geometry == null) {
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
				}
				//Transform the geometry if necessary
				String originSrsName = ((Element)geometryNode).getAttribute("srsName").trim();
				if (targetSrsName != null && !originSrsName.equalsIgnoreCase(targetSrsName)) {
			        MathTransform mathTransform = indexWorker.getTransform(originSrsName, targetSrsName);
		            if (mathTransform == null) {
		        		throw new XPathException("Unable to get a transformation from '" + originSrsName + "' to '" + targetSrsName +"'");        		           	
		            }
		            indexWorker.getCoordinateTransformer().setMathTransform(mathTransform);
		            try {
		            	geometry = indexWorker.getCoordinateTransformer().transform(currentGeometry);
		            } catch (TransformException e) {
		            	throw new XPathException(e);
		            }
	            //No need to transform
				} else
					geometry = currentGeometry;
				if (isCalledAs("getWKT")) {
					WKTWriter wktWriter = new WKTWriter();
					result = new StringValue(wktWriter.write(geometry));
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
				} else
					throw new XPathException("Unknown spatial property: " + mySignature.getName().getLocalName());
	        }
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
