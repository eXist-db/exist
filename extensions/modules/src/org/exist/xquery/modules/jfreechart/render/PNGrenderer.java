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

import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.io.output.ByteArrayOutputStream;

import org.exist.xquery.modules.jfreechart.Configuration;

import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;

/**
 *   PNG renderer
 *
 * @author Dannes Wessels (dannes@exist-db.org)
 */
public class PNGrenderer implements Renderer {

    @Override
    public void render(JFreeChart chart, Configuration config, OutputStream os) throws IOException {
        ChartUtilities.writeChartAsPNG(os, chart, config.getImageWidth(), config.getImageHeight());
    }

    @Override
    public String getContentType() {
        return("image/png");
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
