package org.exist.xslt;

import java.util.Arrays;
import java.util.Collection;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized;
import org.junit.runner.RunWith;
import java.util.Hashtable;
import org.exist.util.Configuration;
import javax.xml.transform.sax.SAXTransformerFactory;
import org.easymock.EasyMock;
import org.exist.storage.BrokerPool;
import org.junit.Test;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.verify;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import org.junit.runners.Parameterized.Parameter;

/**
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
@RunWith(value = Parameterized.class)
public class TransformerFactoryAllocatorTest {

    @Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            { "net.sf.saxon.TransformerFactoryImpl" },
            { "org.apache.xalan.processor.TransformerFactoryImpl" }
        });
    }

    @Parameter
    public String transformerFactoryClass;

    @Test
    public void getTransformerFactory() {

        final  Hashtable<String,Object> testAttributes = new Hashtable<String,Object>();

        BrokerPool mockBrokerPool = EasyMock.createMock(BrokerPool.class);
        Configuration mockConfiguration = EasyMock.createMock(Configuration.class);

        expect(mockBrokerPool.getConfiguration()).andReturn(mockConfiguration);
        expect(mockConfiguration.getProperty(TransformerFactoryAllocator.PROPERTY_TRANSFORMER_CLASS)).andReturn(transformerFactoryClass);
        expect(mockBrokerPool.getConfiguration()).andReturn(mockConfiguration);
        expect(mockConfiguration.getProperty(TransformerFactoryAllocator.PROPERTY_TRANSFORMER_ATTRIBUTES)).andReturn(testAttributes);

        replay(mockBrokerPool, mockConfiguration);

        SAXTransformerFactory transformerFactory = TransformerFactoryAllocator.getTransformerFactory(mockBrokerPool);
        assertEquals(transformerFactoryClass, transformerFactory.getClass().getName());

        verify(mockBrokerPool, mockConfiguration);
    }
}
