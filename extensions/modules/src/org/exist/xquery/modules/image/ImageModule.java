/*
 *  eXist Image Module Extension
 *  Copyright (C) 2006 Adam Retter <adam.retter@devon.gov.uk>
 *  www.adamretter.co.uk
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
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */

package org.exist.xquery.modules.image;

import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;

/**
 * eXist Image Module Extension
 * 
 * An extension module for the eXist Native XML Database that allows operations
 * on images stored in the eXist database.
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @serial 2006-03-10
 * @version 1.0
 *
 * @see org.exist.xquery.AbstractInternalModule#AbstractInternalModule(org.exist.xquery.FunctionDef[])
 */

/*
 * TODO: scale/resample an image (with compression?!?)
 * TODO: metadata extraction from images, especially JPEG's
 * TODO: creation of thumbnails from a collection of images
 */

public class ImageModule extends AbstractInternalModule {

	public final static String NAMESPACE_URI = "http://exist-db.org/xquery/image";
	
	public final static String PREFIX = "image";
	
	private final static FunctionDef[] functions = {
		new FunctionDef(GetWidthFunction.signature, GetWidthFunction.class),
		new FunctionDef(GetHeightFunction.signature, GetHeightFunction.class),
		new FunctionDef(GetThumbnailsFunction.signature, GetThumbnailsFunction.class)
	};
	
	public ImageModule() {
		super(functions);
	}

	public String getNamespaceURI() {
		return NAMESPACE_URI;
	}

	public String getDefaultPrefix() {
		return PREFIX;
	}

	public String getDescription() {
		return "A module for performing operations on Images stored in the eXist db";
	}
}
