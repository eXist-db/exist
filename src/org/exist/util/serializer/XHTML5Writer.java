package org.exist.util.serializer;

import java.io.Writer;
import javax.xml.transform.TransformerException;
import org.exist.util.hashtable.ObjectHashSet;

/**
 * A writer which produces well-formed XHTML5.
 */
public class XHTML5Writer extends XHTMLWriter {

    protected final static ObjectHashSet<String> XHTML_INLINE_TAGS = new ObjectHashSet<String>(31);
    
    static {
    	XHTML_INLINE_TAGS.add("a");
    	XHTML_INLINE_TAGS.add("abbr");
    	XHTML_INLINE_TAGS.add("area");
    	XHTML_INLINE_TAGS.add("audio");
    	XHTML_INLINE_TAGS.add("b");
    	XHTML_INLINE_TAGS.add("bdi");
    	XHTML_INLINE_TAGS.add("bdo");
    	XHTML_INLINE_TAGS.add("br");
    	XHTML_INLINE_TAGS.add("button");
    	XHTML_INLINE_TAGS.add("canvas");
    	XHTML_INLINE_TAGS.add("cite");
    	XHTML_INLINE_TAGS.add("code");
    	XHTML_INLINE_TAGS.add("command");
    	XHTML_INLINE_TAGS.add("datalist");
    	XHTML_INLINE_TAGS.add("del");
    	XHTML_INLINE_TAGS.add("dfn");
    	XHTML_INLINE_TAGS.add("em");
    	XHTML_INLINE_TAGS.add("embed");
    	XHTML_INLINE_TAGS.add("i");
    	XHTML_INLINE_TAGS.add("img");
    	XHTML_INLINE_TAGS.add("ins");
    	XHTML_INLINE_TAGS.add("input");
    	XHTML_INLINE_TAGS.add("kbd");
    	XHTML_INLINE_TAGS.add("keygen");
    	XHTML_INLINE_TAGS.add("label");
    	XHTML_INLINE_TAGS.add("map");
    	XHTML_INLINE_TAGS.add("mark");
    	XHTML_INLINE_TAGS.add("math");
    	XHTML_INLINE_TAGS.add("meter");
    	XHTML_INLINE_TAGS.add("object");
    	XHTML_INLINE_TAGS.add("output");
    	XHTML_INLINE_TAGS.add("progress");
    	XHTML_INLINE_TAGS.add("q");
    	XHTML_INLINE_TAGS.add("ruby");
    	XHTML_INLINE_TAGS.add("s");
    	XHTML_INLINE_TAGS.add("samp");
    	XHTML_INLINE_TAGS.add("select");
    	XHTML_INLINE_TAGS.add("small");
    	XHTML_INLINE_TAGS.add("span");
    	XHTML_INLINE_TAGS.add("strong");
    	XHTML_INLINE_TAGS.add("sub");
    	XHTML_INLINE_TAGS.add("sup");
    	XHTML_INLINE_TAGS.add("svg");
    	XHTML_INLINE_TAGS.add("textarea");
    	XHTML_INLINE_TAGS.add("time");
    	XHTML_INLINE_TAGS.add("tt");
    	XHTML_INLINE_TAGS.add("u");
    	XHTML_INLINE_TAGS.add("var");
    	XHTML_INLINE_TAGS.add("video");
    }
    
    public XHTML5Writer() {
        this(EMPTY_TAGS, XHTML_INLINE_TAGS);
    }

    public XHTML5Writer(final Writer writer) {
        this(writer, EMPTY_TAGS, XHTML_INLINE_TAGS);
    }

    public XHTML5Writer(ObjectHashSet<String> emptyTags, ObjectHashSet<String> inlineTags) {
        super(emptyTags, inlineTags);
    }

    public XHTML5Writer(Writer writer, ObjectHashSet<String> emptyTags, ObjectHashSet<String> inlineTags) {
        super(writer, emptyTags, inlineTags);
    }

    @Override
    protected void writeDoctype(String rootElement) throws TransformerException {
        if (doctypeWritten) {
            return;
        }

        documentType("html", null, null);
        doctypeWritten = true;
    }
}
