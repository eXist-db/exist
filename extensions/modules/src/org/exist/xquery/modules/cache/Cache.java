package org.exist.xquery.modules.cache;

import java.util.HashMap;
import java.util.Properties;

import javax.xml.transform.OutputKeys;

import org.exist.storage.serializers.Serializer;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.xml.sax.SAXException;

/**
 * Static Global cache model
 * 
 * @author Evgeny Gazdovsky <gazdovsky@gmail.com>
 * @version 1.0
 */
public class Cache {
	
	private static HashMap<String, Sequence> cache = new HashMap<String, Sequence>();
	
    private final static Properties OUTPUT_PROPERTIES = new Properties();
    static {
        OUTPUT_PROPERTIES.setProperty(OutputKeys.INDENT, "no");
        OUTPUT_PROPERTIES.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
    }
    
	private static String serialize(Sequence q, XQueryContext context) throws SAXException, XPathException {
		String tmp = "";
		Serializer serializer = context.getBroker().getSerializer();
        serializer.reset();
        serializer.setProperties(OUTPUT_PROPERTIES);
        for (SequenceIterator i = q.iterate(); i.hasNext();){
        	Item item = i.nextItem();
        	try {
            	NodeValue node = (NodeValue)item;
            	tmp += serializer.serialize(node);
        	} catch (ClassCastException e){
        		tmp += item.getStringValue();
        	}
        	
        }
        return tmp;
	}
	
	public static void put(Sequence key, Sequence value, XQueryContext context) throws XPathException, SAXException{
		cache.put(serialize(key, context), value);
	}
	
	public static Sequence get(Sequence key, XQueryContext context) throws XPathException, SAXException{
		return cache.get(serialize(key, context));
	}
	
	public static void remove(Sequence key, XQueryContext context) throws XPathException, SAXException{
		cache.get(serialize(key, context));
	}
	
	public static void clear(){
		cache.clear();
	}
	
}
