/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2011 The eXist Project
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
package org.exist.xquery.modules.jfreechart.render;

import java.awt.Rectangle;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGraphics2D;
import org.apache.commons.io.output.ByteArrayOutputStream;

import org.exist.xquery.modules.jfreechart.Configuration;

import org.jfree.chart.JFreeChart;

import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;

/**
 *   SVG renderer
 *
 * @author Dannes Wessels (dannes@exist-db.org)
 */
public class SVGrenderer implements Renderer {

    @Override
    public void render(JFreeChart chart, Configuration config, OutputStream os) throws IOException {

        Rectangle bounds = new Rectangle(config.getImageWidth(), config.getImageHeight());

        // Get a DOMImplementation and create an XML document
        DOMImplementation domImpl =
                GenericDOMImplementation.getDOMImplementation();
        Document document = domImpl.createDocument(null, "svg", null);

        // Create an instance of the SVG Generator
        SVGGraphics2D svgGenerator = new SVGGraphics2D(document);

        // draw the chart in the SVG generator
        chart.draw(svgGenerator, bounds);

        Writer out = new OutputStreamWriter(os, "UTF-8");
        svgGenerator.stream(out, true /* use css */);
        os.flush();
        os.close();
    }

    @Override
    public String getContentType() {
        return ("image/svg+xml");
    }

    @Override
    public String getContentEncoding() {
        return null;
    }

    @Override
    public byte[] render(JFreeChart chart, Configuration config) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        render(chart, config, os);
        return os.toByteArray();
    }
}
