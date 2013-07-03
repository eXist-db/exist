/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009 The eXist Project
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
package org.exist.xquery.modules.jfreechart;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.commons.io.IOUtils;

import org.apache.log4j.Logger;

import org.exist.dom.QName;
import org.exist.http.servlets.ResponseWrapper;
import org.exist.storage.serializers.Serializer;
import org.exist.validation.internal.node.NodeInputStream;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.response.ResponseModule;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.exist.xquery.modules.jfreechart.render.Renderer;
import org.exist.xquery.modules.jfreechart.render.RendererFactory;
import org.exist.xquery.value.Base64BinaryValueType;
import org.exist.xquery.value.BinaryValueFromInputStream;

import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;




/**
 * JFreechart extension functions.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 * @author Andrzej Taramina (andrzej@chaeron.com)
 */
public class JFreeCharting extends BasicFunction {
	
	protected static final Logger logger = Logger.getLogger(JFreeCharting.class);

    private static final String function1Txt =
            "Render chart using JFreechart. Check documentation on " +
            "http://www.jfree.org/jfreechart/ for details about chart types, " +
            "parameters and data structures.";

     private static final String function2Txt = function1Txt +
             " Output is directly streamed into the servlet output stream.";

    private static final String chartText="The type of chart to render.  Supported chart types: " +
            "AreaChart, BarChart, BarChart3D, LineChart, LineChart3D, " +
            "MultiplePieChart, MultiplePieChart3D, PieChart, PieChart3D, RingChart, " +
            "SpiderWebChart, StackedAreaChart, StackedBarChart, StackedBarChart3D, " +
            "WaterfallChart.";

    private static final String parametersText="The configuration for the chart.  The " +
            "configuration should be supplied as follows: <configuration>"+
            "<param1>Value1</param1><param2>Value2</param2>/<configuration>.  " +
            "Supported parameters: width height title categoryAxisLabel timeAxisLabel " +
            "valueAxisLabel domainAxisLabel rangeAxisLabel pieSectionLabel pieSectionNumberFormat pieSectionPercentFormat orientation " +
            "titleColor chartBackgroundColor plotBackgroundColor rangeLowerBound rangeUpperBound categoryItemLabelGeneratorClass seriesColors sectionColors sectionColorsDelimiter " +
			"categoryAxisColor valueAxisColortimeAxisColor " +
            "order legend tooltips urls.";

    public final static FunctionSignature signatures[] = {

        new FunctionSignature(
            new QName("render", JFreeChartModule.NAMESPACE_URI, JFreeChartModule.PREFIX),
            function1Txt,
            new SequenceType[]{
                new FunctionParameterSequenceType("chart-type", Type.STRING, Cardinality.EXACTLY_ONE, chartText),
                new FunctionParameterSequenceType("configuration", Type.NODE, Cardinality.EXACTLY_ONE, parametersText),
                new FunctionParameterSequenceType("data", Type.NODE, Cardinality.EXACTLY_ONE,
                        "The CategoryDataset or PieDataset, supplied as JFreechart XML.")
            },
            new FunctionReturnSequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE, "the generated PNG image file")
        ),

        new FunctionSignature(
            new QName("stream-render", JFreeChartModule.NAMESPACE_URI, JFreeChartModule.PREFIX),
            function2Txt,
            new SequenceType[]{
                new FunctionParameterSequenceType("chart-type", Type.STRING, Cardinality.EXACTLY_ONE, chartText),
                new FunctionParameterSequenceType("configuration", Type.NODE, Cardinality.EXACTLY_ONE, parametersText),
                new FunctionParameterSequenceType("data", Type.NODE, Cardinality.EXACTLY_ONE,
                        "The CategoryDataset or PieDataset, supplied as JFreechart XML.")
            },
            new SequenceType(Type.EMPTY, Cardinality.EMPTY)
        )
    };

    public JFreeCharting(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
    	
        //was an image and a mime-type speficifed
		if(args[1].isEmpty() || args[2].isEmpty()){
			return Sequence.EMPTY_SEQUENCE;
        }

        try {
            // Get chart type
            String chartType = args[0].getStringValue();
            
            // Get configuration
            Configuration config = new Configuration();
            config.parse(((NodeValue)args[1].itemAt(0)).getNode());

            // Get datastream
            Serializer serializer=context.getBroker().getSerializer();

            NodeValue node = (NodeValue) args[2].itemAt(0);
            InputStream is = new NodeInputStream(serializer, node);
            
            // get chart
            JFreeChart chart = null;
            try {
                chart = JFreeChartFactory.createJFreeChart(chartType, config, is);

            } catch (IllegalArgumentException ex){
                throw new XPathException(this, ex.getMessage());
            }

            // Verify if chart is present
            if(chart==null){
               throw new XPathException(this, "Unable to create chart '"+chartType+"'");
            }
            
            Renderer renderer = RendererFactory.getRenderer(config.getImageType());

            // Render output
            if(isCalledAs("render")){
                byte[] image=renderer.render(chart, config);
                return BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), new ByteArrayInputStream(image));

            } else {
                ResponseWrapper response = getResponseWrapper(context);
                writeToResponseWrapper(config, response, chart, renderer);
            }
            

        } catch (Exception ex) {
            LOG.error(ex);
            throw new XPathException(this, ex.getMessage());
        }

        return Sequence.EMPTY_SEQUENCE;
    }

    /**
     *  Get HTTP response wrapper which provides access to the servlet
     * outputstream.
     *
     * @throws XPathException Thrown when something bad happens.
     */
    private ResponseWrapper getResponseWrapper(XQueryContext context) throws XPathException {
        ResponseModule myModule = (ResponseModule) context.getModule(ResponseModule.NAMESPACE_URI);
        // response object is read from global variable $response
        Variable respVar = myModule.resolveVariable(ResponseModule.RESPONSE_VAR);
        if (respVar == null) {
            throw new XPathException(this, "No response object found in the current XQuery context.");
        }
        if (respVar.getValue().getItemType() != Type.JAVA_OBJECT) {
            throw new XPathException(this, "Variable $response is not bound to an Java object.");
        }
        JavaObjectValue respValue = (JavaObjectValue) respVar.getValue().itemAt(0);
        if (!"org.exist.http.servlets.HttpResponseWrapper".equals(respValue.getObject().getClass().getName())) {
            throw new XPathException(this, signatures[1].toString() +
                    " can only be used within the EXistServlet or XQueryServlet");
        }
        ResponseWrapper response = (ResponseWrapper) respValue.getObject();

        return response;
    }

    /**
     *  Writes chart to response wrapper as PNG image.
     *
     * @throws XPathException Thrown when an IO exception is thrown,
     */
    private void writeToResponseWrapper(Configuration config, ResponseWrapper response, JFreeChart chart, Renderer renderer)
            throws XPathException {
        OutputStream os = null;
        try {
            response.setContentType(renderer.getContentType());
            
            String contentEncoding = renderer.getContentEncoding();
            if(contentEncoding!=null){
                response.setHeader("Content-Encoding", contentEncoding);
            }
            
            os = response.getOutputStream();
            renderer.render(chart, config, os);
            

        } catch (IOException ex) {
            LOG.error(ex);
            throw new XPathException(this, "IO issue while serializing image. " + ex.getMessage());

        } finally {
            IOUtils.closeQuietly(os);
        }

    }

}
