package org.exist.util.serializer;

import java.io.Writer;
import javax.xml.transform.TransformerException;
import org.exist.util.hashtable.ObjectHashSet;

public class HTML5Writer extends XHTMLWriter {

    private final static ObjectHashSet<String> inlineTags = new ObjectHashSet<String>(31);
    
    static {
    	inlineTags.add("a");
    	inlineTags.add("abbr");
    	inlineTags.add("area");
    	inlineTags.add("audio");
    	inlineTags.add("b");
    	inlineTags.add("bdi");
    	inlineTags.add("bdo");
    	inlineTags.add("br");
    	inlineTags.add("button");
    	inlineTags.add("canvas");
    	inlineTags.add("cite");
    	inlineTags.add("code");
    	inlineTags.add("command");
    	inlineTags.add("datalist");
    	inlineTags.add("del");
    	inlineTags.add("dfn");
    	inlineTags.add("em");
    	inlineTags.add("embed");
    	inlineTags.add("i");
    	inlineTags.add("img");
    	inlineTags.add("ins");
    	inlineTags.add("input");
    	inlineTags.add("kbd");
    	inlineTags.add("keygen");
    	inlineTags.add("label");
    	inlineTags.add("map");
    	inlineTags.add("mark");
    	inlineTags.add("math");
    	inlineTags.add("meter");
    	inlineTags.add("object");
    	inlineTags.add("output");
    	inlineTags.add("progress");
    	inlineTags.add("q");
    	inlineTags.add("ruby");
    	inlineTags.add("s");
    	inlineTags.add("samp");
    	inlineTags.add("select");
    	inlineTags.add("small");
    	inlineTags.add("span");
    	inlineTags.add("strong");
    	inlineTags.add("sub");
    	inlineTags.add("sup");
    	inlineTags.add("svg");
    	inlineTags.add("textarea");
    	inlineTags.add("time");
    	inlineTags.add("tt");
    	inlineTags.add("u");
    	inlineTags.add("var");
    	inlineTags.add("video");
    }
    
    public HTML5Writer() {
            super();
    }

    public HTML5Writer(final Writer writer) {
            super(writer);
    }

    @Override
    protected void writeDoctype(final String rootElement) throws TransformerException {
        if(doctypeWritten) {
            return;
        }

        documentType(rootElement, null, null);
        doctypeWritten = true;
    }

    @Override
    protected boolean isInlineTag(final String namespaceURI, final String localName) {
        return inlineTags.contains(localName);
    }

    @Override
    protected boolean needsEscape(final char ch) {
        if("script".equals(currentTag)) {
            return !(ch == '<' || ch == '>' || ch == '&');
        }
        return super.needsEscape(ch);
    }
}
