/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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

package org.exist.xquery.functions.xmldb;

import java.io.InputStream;
import org.exist.dom.QName;
import org.exist.storage.BrokerPool;
import org.exist.validation.ValidationReport;
import org.exist.validation.Validator;
import org.exist.validation.internal.ResourceInputStream;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;


/**
 *   xQuery function for filtering strings from text that match the specified
 * pattern. E.g.  AABBBBCBBC and BB.*BB results in BBBBCBB
 *
 * @author dizzzz
 */
public class XMLDBValidator extends BasicFunction {
    
    private Validator validator;
    private BrokerPool brokerPool;
    
    // Setup function signature
    public final static FunctionSignature signature = new FunctionSignature(
            new QName("validate", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
            "Validate document specified by $a.",
            new SequenceType[]{
        new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
    },
            new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE)
            );
    
    
    
    
    /** Creates a new instance of RegexpMatcher */
    public XMLDBValidator(XQueryContext context) {
        super(context, signature);
        brokerPool = context.getBroker().getBrokerPool();
        validator = new Validator( brokerPool );
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
     */
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws org.exist.xquery.XPathException {
        
        // Check input parameters
        if(args.length != 1){
            return Sequence.EMPTY_SEQUENCE;
        }
        
        
        // Get inputstream
        InputStream is = new ResourceInputStream(brokerPool, args[0].getStringValue());
        
        ValidationReport vr = validator.validate(is);
        
        // Create response
        Sequence result = new ValueSequence();
        result.add( new BooleanValue( !vr.hasErrorsAndWarnings() ) );
        
        
        return result;
    }
    
}

