/*
 *  eXist xml document repository and xpath implementation
 *  Copyright (C) 2000,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU Library General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.exist.util;

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *created    24. Juni 2002
 */
public class SyntaxException extends Exception {

	private static final long serialVersionUID = -4603471595319223797L;

	/**  Constructor for the PermissionDeniedException object */
    public SyntaxException() {
        super();
    }


    /**
     *  Constructor for the PermissionDeniedException object
     *
     *@param  message  Description of the Parameter
     */
    public SyntaxException( String message ) {
        super( message );
    }
}

