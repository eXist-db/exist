/*
Copyright (c) 2012, Adam Retter
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of Adam Retter Consulting nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL Adam Retter BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.exist.extensions.exquery.restxq.impl.adapters;

import java.util.Iterator;

import com.evolvedbinary.j8fu.function.RunnableE;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.NodeProxy;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.SequenceIterator;
import org.exquery.xquery.Sequence;
import org.exquery.xquery.Type;
import org.exquery.xquery.TypedValue;

import javax.annotation.Nullable;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class SequenceAdapter implements Sequence<Item> {
    
    private final static Logger LOG = LogManager.getLogger(SequenceAdapter.class);
    
    private final org.exist.xquery.value.Sequence sequence;
    @Nullable private final RunnableE<SequenceException> closer;

    public SequenceAdapter(final org.exist.xquery.value.Sequence sequence) {
        this(sequence, null);
    }

    public SequenceAdapter(final org.exist.xquery.value.Sequence sequence, @Nullable final RunnableE<SequenceException> closer) {
        this.sequence = sequence;
        this.closer = closer;
    }
    
    @Override
    public Iterator<TypedValue<Item>> iterator() {
        return new Iterator<TypedValue<Item>>(){

            private SequenceIterator iterator;
            
            private SequenceIterator getIterator() {
                if(iterator == null) {
                    try {
                         iterator = sequence.iterate();
                    } catch(final XPathException xpe) {
                        LOG.error("Unable to extract the underlying Sequence Iterator: " + xpe.getMessage() + ". Falling back to EMPTY_ITERATOR", xpe);
                        
                        iterator = SequenceIterator.EMPTY_ITERATOR;
                    }
                }
                return iterator;
            }
            
            @Override
            public boolean hasNext() {
                return getIterator().hasNext();
            }

            @Override
            public TypedValue<Item> next() {
                return createTypedValue(getIterator().nextItem());
            }

            @Override
            public void remove() {
                //do nothing
            }
        };
    }
    
    private TypedValue<Item> createTypedValue(final Item item) {
        return new TypedValue<Item>(){
            @Override
            public Type getType() {
                return TypeAdapter.toExQueryType(item.getType());
            }

            @Override
            public Item getValue() {
                if(item instanceof NodeProxy) {
                    return DomEnhancingNodeProxyAdapter.create((NodeProxy)item); //RESTXQ expects to find DOM Nodes not NodeProxys
                } else {
                    return item;
                }
            }
        };
    }

    @Override
    public TypedValue<Item> head() {
        if(sequence.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE.head();
        } else {
            return createTypedValue(sequence.itemAt(0));
        }
    }
    
    @Override
    public Sequence<Item> tail() {
        try {
            return new SequenceAdapter(sequence.tail());
        } catch(final XPathException xpe) {
            LOG.error(xpe.getMessage(), xpe);
            return new SequenceAdapter(org.exist.xquery.value.Sequence.EMPTY_SEQUENCE);
        }
    }

    @Override
    public void close() throws SequenceException {
        closer.run();
    }

    public org.exist.xquery.value.Sequence getExistSequence() {
        return sequence;
    }


}