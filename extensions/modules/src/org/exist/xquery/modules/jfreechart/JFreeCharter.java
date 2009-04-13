/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 The eXist Project
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.exist.dom.QName;

import org.exist.validation.internal.node.NodeInputStream;

import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.response.ResponseModule;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;

import org.exist.http.servlets.ResponseWrapper;
import org.exist.xquery.value.NodeValue;


/**
 * JFreechart extension functions.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class JFreeCharter extends BasicFunction {

    private static final String functionTxt =
            "Render chart using JFreechart. Generate " +
            "chart of type $a with configuration $b the data" +
            "in $c.";
    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
        new QName("render", JFreeChartModule.NAMESPACE_URI, JFreeChartModule.PREFIX),
        functionTxt,
        new SequenceType[]{
            new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
            new SequenceType(Type.NODE, Cardinality.EXACTLY_ONE),
            new SequenceType(Type.NODE, Cardinality.EXACTLY_ONE),},
        new SequenceType(Type.BOOLEAN, Cardinality.EMPTY)),};

    public JFreeCharter(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        // TODO : check with other functions

        try {
            // Get chart type
            String chartType = args[0].getStringValue();
            
            // Get configuration
            Configuration config = new Configuration();
            config.parse(((NodeValue)args[1].itemAt(0)).getNode());

            // Get datastream
            InputStream is = new NodeInputStream(context, args[2].iterate());
            
            // get chart
            JFreeChart chart = null;
            try {
                chart = JFreeChartFactory.createJFreeChart(chartType, config, is);
            } catch (IllegalArgumentException ex){
                throw new XPathException(ex.getMessage());
            }
            if(chart==null){
               throw new XPathException("Unable to create chart '"+chartType+"'");
            }

            // Generate chart
            ResponseWrapper response = getResponseWrapper(context);
            writePNG(config, response, chart);

            // clean up
            is.close();

        } catch (IOException ex) {
            LOG.error(ex);
            ex.printStackTrace();
        }

        return Sequence.EMPTY_SEQUENCE;
    }

    /**
     *  Get HTTP response wrapper which provides access to the servler
     * outputstream.
     *
     * @throws XPathException Thrown when something bad happens.
     */
    private ResponseWrapper getResponseWrapper(XQueryContext context) throws XPathException {
        ResponseModule myModule = (ResponseModule) context.getModule(ResponseModule.NAMESPACE_URI);
        // response object is read from global variable $response
        Variable respVar = myModule.resolveVariable(ResponseModule.RESPONSE_VAR);
        if (respVar == null) {
            throw new XPathException(getASTNode(), "No response object found in the current XQuery context.");
        }
        if (respVar.getValue().getItemType() != Type.JAVA_OBJECT) {
            throw new XPathException(getASTNode(), "Variable $response is not bound to an Java object.");
        }
        JavaObjectValue respValue = (JavaObjectValue) respVar.getValue().itemAt(0);
        if (!"org.exist.http.servlets.HttpResponseWrapper".equals(respValue.getObject().getClass().getName())) {
            throw new XPathException(getASTNode(), signatures[1].toString() +
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
    private void writePNG(Configuration config, ResponseWrapper response, JFreeChart chart) throws XPathException {
        OutputStream os = null;
        try {
            response.setContentType("image/png");
            os = response.getOutputStream();
            ChartUtilities.writeChartAsPNG(os, chart, config.getImageWidth(), config.getImageHeight());
            os.close();

        } catch (IOException ex) {
            LOG.error(ex);
            throw new XPathException(getASTNode(), "IO issue while serializing image. " + ex.getMessage());

        } finally {
            try {
                os.close();
            } catch (IOException ex) {
                // Ignore
                LOG.debug(ex);
            }
        }

    }
}
