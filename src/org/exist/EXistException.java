
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
package org.exist;

import java.io.StringWriter;
import java.io.PrintWriter;

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    24. Juni 2002
 */
public class EXistException extends Exception {

    protected Throwable inner = null;


    /**  Constructor for the EXistException object */
    public EXistException() {
        super();
    }


    /**
     *  Constructor for the EXistException object
     *
     *@param  inner  Description of the Parameter
     */
    public EXistException( Throwable inner ) {
        super( inner.getMessage() );
        this.inner = inner;
    }


    /**
     *  Constructor for the EXistException object
     *
     *@param  message  Description of the Parameter
     */
    public EXistException( String message ) {
        super( message );
    }


    /**
     *  Gets the exception attribute of the EXistException object
     *
     *@return    The exception value
     */
    public Throwable getException() {
        return inner;
    }


    /**
     *  Gets the message attribute of the EXistException object
     *
     *@return    The message value
     */
    public String getMessage() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter( sw );
        pw.println( super.getMessage() );
        if ( inner != null )
            inner.printStackTrace( pw );
        return sw.toString();
    }
}

