/*
 *  The Apache Software License, Version 1.1
 *
 *
 *  Copyright (c) 1999 The Apache Software Foundation.  All rights
 *  reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *
 *  1. Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the
 *  distribution.
 *
 *  3. The end-user documentation included with the redistribution,
 *  if any, must include the following acknowledgment:
 *  "This product includes software developed by the
 *  Apache Software Foundation (http://www.apache.org/)."
 *  Alternately, this acknowledgment may appear in the software itself,
 *  if and wherever such third-party acknowledgments normally appear.
 *
 *  4. The names "Xalan" and "Apache Software Foundation" must
 *  not be used to endorse or promote products derived from this
 *  software without prior written permission. For written
 *  permission, please contact apache@apache.org.
 *
 *  5. Products derived from this software may not be called "Apache",
 *  nor may "Apache" appear in their name, without prior written
 *  permission of the Apache Software Foundation.
 *
 *  THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 *  WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 *  OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 *  ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 *  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 *  LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 *  USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 *  OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 *  OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 *  SUCH DAMAGE.
 *  ====================================================================
 *
 *  This software consists of voluntary contributions made by many
 *  individuals on behalf of the Apache Software Foundation and was
 *  originally based on software copyright (c) 1999, Lotus
 *  Development Corporation., http://www.lotus.com.  For more
 *  information on the Apache Software Foundation, please see
 *  <http://www.apache.org/>.
 */
package org.exist.util;

import java.util.*;
import java.lang.ref.SoftReference;

/**
 *  <meta name="usage" content="internal"/> Pool of object of a given type to
 *  pick from to help memory usage
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    27. Juni 2002
 */
public class ObjectPool implements java.io.Serializable {

    /**
     *  Type of objects in this pool.
     *
     *@serial
     */
    private final Class objectType;

    /**
     *  Vector of given objects this points to.
     *
     *@serial
     */
    private final Vector freeStack;


    /**
     *  Constructor ObjectPool
     *
     *@param  type  Type of objects for this pool
     */
    public ObjectPool( Class type ) {
        objectType = type;
        freeStack = new Vector();
    }


    /**
     *  Constructor ObjectPool
     *
     *@param  className  Fully qualified name of the type of objects for this
     *      pool.
     */
    public ObjectPool( String className ) {
        try {
            objectType = Class.forName( className );
        } catch ( ClassNotFoundException cnfe ) {
            throw new RuntimeException( cnfe.getMessage() );
        }
        freeStack = new Vector();
    }


    /**
     *  Constructor ObjectPool
     *
     *@param  type  Type of objects for this pool
     *@param  size  Size of vector to allocate
     */
    public ObjectPool( Class type, int size ) {
        objectType = type;
        freeStack = new Vector( size );
    }


    /**  Constructor ObjectPool */
    public ObjectPool() {
        objectType = null;
        freeStack = new Vector();
    }


    /**
     *  Get an instance of the given object in this pool if available
     *
     *@return    an instance of the given object if available or null
     */
    public synchronized Object getInstanceIfFree() {

        // Check if the pool is empty.
        if ( !freeStack.isEmpty() ) {
            Object obj = null;
            do {
                // Remove object from end of free pool.
                SoftReference ref = (SoftReference) freeStack.lastElement();
                obj = ref.get();
                freeStack.setSize( freeStack.size() - 1 );
            } while ( obj == null && freeStack.size() > 0 );
            return obj;
        }

        return null;
    }


    /**
     *  Get an instance of the given object in this pool
     *
     *@return    An instance of the given object
     */
    public synchronized Object getInstance() {
        Object obj = null;
        if ( !freeStack.isEmpty() )
            do {
                // Remove object from end of free pool.
                SoftReference ref = (SoftReference) freeStack.lastElement();
                obj = ref.get();
                freeStack.setSize( freeStack.size() - 1 );
            } while ( freeStack.size() > 0 && obj == null );
        if ( obj == null ) {
            // Create a new object if so.
            try {
                return objectType.newInstance();
            } catch ( InstantiationException ex ) {} catch ( IllegalAccessException ex ) {}

            // Throw unchecked exception for error in pool configuration.
            throw new RuntimeException( "error creating new object in pool" );
        }

        return obj;
    }


    /**
     *  Add an instance of the given object to the pool
     *
     *@param  obj  Object to add.
     */
    public synchronized void freeInstance( Object obj ) {

        // Make sure the object is of the correct type.
        // Remove safety.  -sb
        // if (objectType.isInstance(obj))
        // {
        freeStack.addElement( new SoftReference( obj ) );
        // }
        // else
        // {
        //  throw new IllegalArgumentException("argument type invalid for pool");
        // }
    }


    /**  Description of the Method */
    public synchronized void reset() {
        freeStack.clear();
    }
}

