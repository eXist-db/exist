/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.performance.xquery;

import org.exist.dom.DocumentSet;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.util.Occurrences;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.util.UtilModule;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RandomText extends BasicFunction {

    public final static FunctionSignature signature = new FunctionSignature(
            new QName("random-text", PerfTestModule.NAMESPACE_URI, PerfTestModule.PREFIX),
                "",
                new SequenceType[] {
                    new SequenceType(Type.INT, Cardinality.EXACTLY_ONE)
                },
            new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE));

    private static String[] words = null;

    private Random random = new Random();

    public RandomText(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (words == null)
            generateWordList();
        int max = ((IntegerValue)args[0].itemAt(0)).getInt();
        max = random.nextInt(max) + 1;
        StringBuffer text = new StringBuffer();
        for (int i = 0; i < max; i++) {
            if (text.length() > 0)
                text.append(' ');
            text.append(words[random.nextInt(words.length)]);
        }
        return new StringValue(text.toString());
    }

    private void generateWordList() throws XPathException {
		try {
			DocumentSet docs = new DocumentSet();
            docs = context.getBroker().getAllXMLResources(docs);
            Occurrences[] occurrences =
                    context.getBroker().getTextEngine().scanIndexTerms(docs, docs.toNodeSet(), null, null);
            List list = new ArrayList();
            for (int i = 0; i < occurrences.length; i++) {
                list.add(occurrences[i].getTerm().toString());
            }
            words = new String[list.size()];
            list.toArray(words);
        } catch (PermissionDeniedException e) {
			throw new XPathException(getASTNode(), e.getMessage(), e);
		}
    }
}
