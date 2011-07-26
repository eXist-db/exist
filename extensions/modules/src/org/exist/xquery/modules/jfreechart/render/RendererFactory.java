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

import org.apache.log4j.Logger;

/**
 *   Renderer factory, defaults to PNG is anything fails.
 *
 * @author Dannes Wessels (dannes@exist-db.org)
 */
public class RendererFactory {
    
    private final static Logger LOG = Logger.getLogger(RendererFactory.class);

    public static Renderer getRenderer(String type) {
        
        // Handle for created renderer
        Renderer retVal = null;

        // Catch for default, make Uppercase
        if (type == null) {
            type = "png";
        }
        type = type.toUpperCase();


        // Construct the object
        try {
            String renderer = "org.exist.xquery.modules.jfreechart.render." + type + "renderer";
            Class clazz = Class.forName(renderer);
            Object obj = clazz.newInstance();

            if (obj instanceof Renderer) {
                retVal = (Renderer) obj;
            }


        } catch (ClassNotFoundException ex) {
            LOG.error(ex.getMessage());
            
        } catch (InstantiationException ex) {
            LOG.error(ex.getMessage());
            
        } catch (IllegalAccessException ex) {
            LOG.error(ex.getMessage());
        }

        // If no object was consructed, fallback to default.
        if(retVal==null){
            LOG.debug("Fall back to default renderer");
            retVal = new PNGrenderer();
        }

        return retVal;
    }
}
