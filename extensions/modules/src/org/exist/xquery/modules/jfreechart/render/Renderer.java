
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

import org.exist.xquery.modules.jfreechart.Configuration;

import org.jfree.chart.JFreeChart;

/**
 *   Renderer interface.
 *
 * @author Dannes Wessels (dannes@exist-db.org)
 */
public interface Renderer {
    
    /**
     *  Render chart to outputstream.
     * 
     * @param chart     The jfreechart
     * @param config    Chart configuration
     * @param os        The Outputstream
     * @throws IOException Thrown when something bad happens
     */
    public void render(JFreeChart chart, Configuration config, OutputStream os ) throws IOException;
         
    /**
     *  Render chart to byte arrau.
     * @param chart     The jfreechart
     * @param config    Chart configuration
     * @return  The rendered image
     * @throws IOException Thrown when something bad happens
     */
    public byte[] render(JFreeChart chart, Configuration config) throws IOException;
    
    /**
     *  Get the Content type of the rendered image (mimetype).
     * 
     * @return Content type of in=mage
     */
    public String getContentType();
    
    /**
     *  Get content encoding of image.
     * 
     * @return Normally returns null, or gzip when result is gzip-ped.
     */
    public String getContentEncoding();
    
}
