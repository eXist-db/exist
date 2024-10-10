/*
 * Copyright © 2001, Adam Retter
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.exist.extensions.exquery.restxq.impl.adapters;

import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.exist.xquery.value.Type;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class TypeAdapter {
    
    //eXist-db XQuery Type <-> EXQuery Type
    private final static BidiMap mappings = new DualHashBidiMap();
    static {
            mappings.put(Type.NODE,
                org.exquery.xquery.Type.NODE);
            
            mappings.put(Type.ELEMENT,
                org.exquery.xquery.Type.ELEMENT);
                
            mappings.put(Type.ATTRIBUTE,
                org.exquery.xquery.Type.ATTRIBUTE);
                
            mappings.put(Type.TEXT,
                org.exquery.xquery.Type.TEXT);
            
            mappings.put(Type.PROCESSING_INSTRUCTION,
                org.exquery.xquery.Type.PROCESSING_INSTRUCTION);
                
            mappings.put(Type.COMMENT,
                org.exquery.xquery.Type.COMMENT);
                    
            mappings.put(Type.DOCUMENT,
                org.exquery.xquery.Type.DOCUMENT);
                    
            mappings.put(Type.ITEM,
                org.exquery.xquery.Type.ITEM);
                
            mappings.put(Type.ANY_TYPE,
                org.exquery.xquery.Type.ANY_TYPE);
                
            mappings.put(Type.ANY_SIMPLE_TYPE,
                org.exquery.xquery.Type.ANY_SIMPLE_TYPE);
                
            mappings.put(Type.UNTYPED,
                org.exquery.xquery.Type.UNTYPED);
                
            mappings.put(Type.STRING,
                org.exquery.xquery.Type.STRING);
                
            mappings.put(Type.BOOLEAN,
                org.exquery.xquery.Type.BOOLEAN);
                
            mappings.put(Type.QNAME,
                org.exquery.xquery.Type.QNAME);
                
            mappings.put(Type.ANY_URI,
                org.exquery.xquery.Type.ANY_URI);
                
            mappings.put(Type.BASE64_BINARY,
                org.exquery.xquery.Type.BASE64_BINARY);
                
            mappings.put(Type.HEX_BINARY,
                org.exquery.xquery.Type.HEX_BINARY);
            
            mappings.put(Type.NOTATION,
                org.exquery.xquery.Type.NOTATION);
               
            mappings.put(Type.INTEGER,
                org.exquery.xquery.Type.INTEGER);
                    
            mappings.put(Type.DECIMAL,
                org.exquery.xquery.Type.DECIMAL);
                
            mappings.put(Type.FLOAT,
                org.exquery.xquery.Type.FLOAT);
                    
            mappings.put(Type.DOUBLE,
                org.exquery.xquery.Type.DOUBLE);
                
            mappings.put(Type.NON_POSITIVE_INTEGER,
                org.exquery.xquery.Type.NON_POSITIVE_INTEGER);
                
            mappings.put(Type.NEGATIVE_INTEGER,
                org.exquery.xquery.Type.NEGATIVE_INTEGER);
                    
            mappings.put(Type.LONG,
                org.exquery.xquery.Type.LONG);
                
            mappings.put(Type.INT,
                org.exquery.xquery.Type.INT);
                
            mappings.put(Type.SHORT,
                org.exquery.xquery.Type.SHORT);
                
            mappings.put(Type.BYTE,
                org.exquery.xquery.Type.BYTE);
                
            mappings.put(Type.NON_NEGATIVE_INTEGER,
                org.exquery.xquery.Type.NON_NEGATIVE_INTEGER);
                
            mappings.put(Type.UNSIGNED_LONG,
                org.exquery.xquery.Type.UNSIGNED_LONG);

            mappings.put(Type.UNSIGNED_SHORT,
                org.exquery.xquery.Type.UNSIGNED_SHORT);
                
            mappings.put(Type.UNSIGNED_BYTE,
                org.exquery.xquery.Type.UNSIGNED_BYTE);
                
            mappings.put(Type.POSITIVE_INTEGER,
                org.exquery.xquery.Type.POSITIVE_INTEGER);
    
            mappings.put(Type.DATE_TIME,
                org.exquery.xquery.Type.DATE_TIME);
                    
            mappings.put(Type.DATE,
                org.exquery.xquery.Type.DATE);
                
            mappings.put(Type.TIME,
                org.exquery.xquery.Type.TIME);
                
            mappings.put(Type.DURATION,
                org.exquery.xquery.Type.DURATION);
                
            mappings.put(Type.YEAR_MONTH_DURATION,
                org.exquery.xquery.Type.YEAR_MONTH_DURATION);
                
            mappings.put(Type.DAY_TIME_DURATION,
                org.exquery.xquery.Type.DAY_TIME_DURATION);
                
            mappings.put(Type.G_YEAR,
                org.exquery.xquery.Type.G_YEAR);
                
            mappings.put(Type.G_MONTH,
                org.exquery.xquery.Type.G_MONTH);
                
            mappings.put(Type.G_DAY,
                org.exquery.xquery.Type.G_DAY);
                
            mappings.put(Type.G_YEAR_MONTH,
                org.exquery.xquery.Type.G_YEAR_MONTH);
                
            mappings.put(Type.G_MONTH_DAY,
                org.exquery.xquery.Type.G_MONTH_DAY);
    
            mappings.put(Type.TOKEN,
                org.exquery.xquery.Type.TOKEN);
                
            mappings.put(Type.NORMALIZED_STRING,
                org.exquery.xquery.Type.NORMALIZED_STRING);
                
            mappings.put(Type.LANGUAGE,
                org.exquery.xquery.Type.LANGUAGE);
                
            mappings.put(Type.NMTOKEN,
                org.exquery.xquery.Type.NM_TOKEN);
                
            mappings.put(Type.NAME,
                org.exquery.xquery.Type.NAME);
                
            mappings.put(Type.NCNAME,
                org.exquery.xquery.Type.NC_NAME);
                
            mappings.put(Type.ID,
                org.exquery.xquery.Type.ID);
                
            mappings.put(Type.IDREF,
                org.exquery.xquery.Type.ID_REF);
                
            mappings.put(Type.ENTITY,
                org.exquery.xquery.Type.ENTITY);
    }
    
    public static org.exquery.xquery.Type toExQueryType(int type) {
        org.exquery.xquery.Type exQueryType = (org.exquery.xquery.Type)mappings.get(type);
        if(exQueryType == null) {
            exQueryType =  org.exquery.xquery.Type.ANY_TYPE;
        }
        
        return exQueryType;
    }
    
    public static int toExistType(org.exquery.xquery.Type type) {
        Integer existType = (Integer)mappings.getKey(type);
        if(existType == null) {
            existType = Type.ANY_TYPE;
        }
        
        return existType;
    }
}