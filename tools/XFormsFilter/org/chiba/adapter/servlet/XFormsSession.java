package org.chiba.adapter.servlet;

import org.chiba.adapter.ChibaAdapter;
import org.chiba.tools.xslt.UIGenerator;

/**
 * encapsulates the objects needed by a Chiba form session.
 *
 * @author joern turner</a>
 * @version $Id: XFormsSession.java,v 1.1 2006/05/24 14:55:00 joernt Exp $
 */
public class XFormsSession{
    private ChibaAdapter adapter;
    private UIGenerator uiGenerator;
    private String key;
    public static final String ADAPTER_PREFIX = "A";
    public static final String UIGENERATOR_PREFIX = "U";

    public XFormsSession(){
        this.key = "" + System.currentTimeMillis();
    }

    public String getKey(){
        return this.key;
    }

    public ChibaAdapter getAdapter() {
        return adapter;
    }

    public void setAdapter(ChibaAdapter adapter) {
        this.adapter = adapter;
    }

    public UIGenerator getUIGenerator() {
        return uiGenerator;
    }

    public void setUIGenerator(UIGenerator uiGenerator) {
        this.uiGenerator = uiGenerator;
    }

}
