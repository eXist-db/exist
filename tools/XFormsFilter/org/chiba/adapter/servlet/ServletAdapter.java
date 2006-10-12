// Copyright 2005 Chibacon
/*
 *
 *    Artistic License
 *
 *    Preamble
 *
 *    The intent of this document is to state the conditions under which a Package may be copied, such that
 *    the Copyright Holder maintains some semblance of artistic control over the development of the
 *    package, while giving the users of the package the right to use and distribute the Package in a
 *    more-or-less customary fashion, plus the right to make reasonable modifications.
 *
 *    Definitions:
 *
 *    "Package" refers to the collection of files distributed by the Copyright Holder, and derivatives
 *    of that collection of files created through textual modification.
 *
 *    "Standard Version" refers to such a Package if it has not been modified, or has been modified
 *    in accordance with the wishes of the Copyright Holder.
 *
 *    "Copyright Holder" is whoever is named in the copyright or copyrights for the package.
 *
 *    "You" is you, if you're thinking about copying or distributing this Package.
 *
 *    "Reasonable copying fee" is whatever you can justify on the basis of media cost, duplication
 *    charges, time of people involved, and so on. (You will not be required to justify it to the
 *    Copyright Holder, but only to the computing community at large as a market that must bear the
 *    fee.)
 *
 *    "Freely Available" means that no fee is charged for the item itself, though there may be fees
 *    involved in handling the item. It also means that recipients of the item may redistribute it under
 *    the same conditions they received it.
 *
 *    1. You may make and give away verbatim copies of the source form of the Standard Version of this
 *    Package without restriction, provided that you duplicate all of the original copyright notices and
 *    associated disclaimers.
 *
 *    2. You may apply bug fixes, portability fixes and other modifications derived from the Public Domain
 *    or from the Copyright Holder. A Package modified in such a way shall still be considered the
 *    Standard Version.
 *
 *    3. You may otherwise modify your copy of this Package in any way, provided that you insert a
 *    prominent notice in each changed file stating how and when you changed that file, and provided that
 *    you do at least ONE of the following:
 *
 *        a) place your modifications in the Public Domain or otherwise make them Freely
 *        Available, such as by posting said modifications to Usenet or an equivalent medium, or
 *        placing the modifications on a major archive site such as ftp.uu.net, or by allowing the
 *        Copyright Holder to include your modifications in the Standard Version of the Package.
 *
 *        b) use the modified Package only within your corporation or organization.
 *
 *        c) rename any non-standard executables so the names do not conflict with standard
 *        executables, which must also be provided, and provide a separate manual page for each
 *        non-standard executable that clearly documents how it differs from the Standard
 *        Version.
 *
 *        d) make other distribution arrangements with the Copyright Holder.
 *
 *    4. You may distribute the programs of this Package in object code or executable form, provided that
 *    you do at least ONE of the following:
 *
 *        a) distribute a Standard Version of the executables and library files, together with
 *        instructions (in the manual page or equivalent) on where to get the Standard Version.
 *
 *        b) accompany the distribution with the machine-readable source of the Package with
 *        your modifications.
 *
 *        c) accompany any non-standard executables with their corresponding Standard Version
 *        executables, giving the non-standard executables non-standard names, and clearly
 *        documenting the differences in manual pages (or equivalent), together with instructions
 *        on where to get the Standard Version.
 *
 *        d) make other distribution arrangements with the Copyright Holder.
 *
 *    5. You may charge a reasonable copying fee for any distribution of this Package. You may charge
 *    any fee you choose for support of this Package. You may not charge a fee for this Package itself.
 *    However, you may distribute this Package in aggregate with other (possibly commercial) programs as
 *    part of a larger (possibly commercial) software distribution provided that you do not advertise this
 *    Package as a product of your own.
 *
 *    6. The scripts and library files supplied as input to or produced as output from the programs of this
 *    Package do not automatically fall under the copyright of this Package, but belong to whomever
 *    generated them, and may be sold commercially, and may be aggregated with this Package.
 *
 *    7. C or perl subroutines supplied by you and linked into this Package shall not be considered part of
 *    this Package.
 *
 *    8. The name of the Copyright Holder may not be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 *    9. THIS PACKAGE IS PROVIDED "AS IS" AND WITHOUT ANY EXPRESS OR IMPLIED
 *    WARRANTIES, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTIES OF
 *    MERCHANTIBILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 *
 */
package org.chiba.adapter.servlet;

import org.apache.log4j.Category;
import org.chiba.adapter.AbstractChibaAdapter;
import org.chiba.adapter.ChibaEvent;
import org.chiba.xml.events.ChibaEventNames;
import org.chiba.xml.events.XMLEvent;
import org.chiba.xml.xforms.exception.XFormsException;
import org.w3c.dom.Element;
import org.w3c.dom.events.Event;
import org.w3c.dom.events.EventListener;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * integrates XForms Processor into Web-applications and handles request
 * processing. This is the default implementation of ChibaAdapter and besides
 * handling the interaction it also manages a UIGenerator to build the rendered
 * output for the browser.
 *
 * @author joern turner
 * @version $Id: ServletAdapter.java,v 1.11 2006/08/24 11:37:26 unl Exp $
 */
public class ServletAdapter extends AbstractChibaAdapter implements EventListener {

    private static final Category LOGGER = Category.getInstance(ServletAdapter.class);
    public static final String HTTP_SERVLET_REQUEST = "chiba.web.request";
    //public static final String HTTP_SESSION_OBJECT = "chiba.web.session";
    public static final String HTTP_UPLOAD_DIR = "chiba.web.uploadDir";

    //private ChibaBean chibaBean = null;
    //private String formURI = null;
    //private String actionUrl = null;
    //private String CSSFile = null;
    //private String stylesheet = null;
    //private UIGenerator generator = null;
    //private String stylesheetPath = null;
    //private HashMap context = null;
    public static final String USERAGENT = "chiba.useragent";
    public static final Object XSLT_PATH = "xslt-path";

    private HttpRequestHandler httpRequestHandler;

    /**
     * Creates a new ServletAdapter object.
     */
    public ServletAdapter() {
        this.chibaBean = createXFormsProcessor();
    }

    /**
     * place to put application-specific params or configurations before
     * actually starting off the XFormsProcessor. It's the responsibility of
     * this method to call chibaBean.init() to finish up the processor setup.
     *
     * @throws XFormsException If an error occurs
     */
    public void init() throws XFormsException {
        // todo: register for load/replace/message before init

        this.chibaBean.init();

        // todo: check for load/replace/message during init, signal to servlet ?
    }


    /**
     * ServletAdapter knows and executes only one ChibaEvent: 'http-request'
     * which will contain the HttpServletRequest as contextInfo.
     *
     * @param event only events of type 'http-request' will be handled
     * @throws XFormsException
     */
    public void dispatch(ChibaEvent event) throws XFormsException {
        if (event.getEventName().equalsIgnoreCase("http-request")) {
            HttpServletRequest request = (HttpServletRequest) event.getContextInfo();
            getHttpRequestHandler().handleRequest(request);
        }
        else {
            LOGGER.warn("ignoring unknown event '" + event.getEventName() + "'");
        }
    }

    /**
     * terminates the XForms processing. right place to do cleanup of
     * resources.
     *
     * @throws org.chiba.xml.xforms.exception.XFormsException
     */
    public void shutdown() throws XFormsException {
        this.chibaBean.shutdown();
    }

    // todo: remove
    /**
     * Instructs the application environment to forward the given response.
     *
     * @param response a map containing at least a response stream and optional
     * header information.
     * @deprecated
     */
    public void forward(Map response) {
        this.chibaBean.getContext().put(SUBMISSION_RESPONSE, response);
    }

    // event handling
    // todo: should be moved up to base class

    /**
     * This method is called whenever an event occurs of the type for which the
     * <code> EventListener</code> interface was registered.
     *
     * @param event The <code>Event</code> contains contextual information about
     * the event. It also contains the <code>stopPropagation</code> and
     * <code>preventDefault</code> methods which are used in determining the
     * event's flow and default action.
     */
    public void handleEvent(Event event) {
        String type = event.getType();
        String targetId = ((Element) event.getTarget()).getAttributeNS(null, "id");
        XMLEvent xmlEvent = (XMLEvent) event;

        if (ChibaEventNames.LOAD_URI.equals(type)) {
            handleLoadURI(targetId, (String) xmlEvent.getContextInfo("uri"), (String) xmlEvent.getContextInfo("show"));
            return;
        }
        if (ChibaEventNames.RENDER_MESSAGE.equals(type)) {
            handleMessage(targetId, (String) xmlEvent.getContextInfo("message"), (String) xmlEvent.getContextInfo("level"));
            return;
        }
        if (ChibaEventNames.REPLACE_ALL.equals(type)) {
            handleReplaceAll(targetId, (Map) xmlEvent.getContextInfo("header"), xmlEvent.getContextInfo("body"));
            return;
        }

        // unknown event ignored
    }

    // todo: *either* move up these three methods as abstract template methods *or* use event log ?
    public void handleLoadURI(String targetId, String uri, String show) {
        // todo
    }

    public void handleMessage(String targetId, String message, String level) {
        // todo
    }

    public void handleReplaceAll(String targetId, Map header, Object body) {
        // todo
    }

    // todo: should be moved up to web adapter
    protected HttpRequestHandler getHttpRequestHandler() {
        if (this.httpRequestHandler == null) {
            this.httpRequestHandler = new HttpRequestHandler(this.chibaBean);
            this.httpRequestHandler.setUploadRoot(this.uploadDestination);
        }

        return this.httpRequestHandler;
    }
}

// end of class
