/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.xquery.modules.jfreechart;

import org.apache.log4j.Logger;
import org.exist.xquery.XPathException;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.util.TableOrder;
import org.w3c.dom.Node;

/**
 * Class for storing all configuration items for charts, except chart type.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class Configuration {

    private final static Logger logger = Logger.getLogger(Configuration.class);

    // Default dimension of image
    private int imageHeight = 300;
    private int imageWidth = 400;

    // Chart title
    private String title;

    // Labels
    private String categoryAxisLabel;
    private String domainAxisLabel;
    private String rangeAxisLabel;
    private String timeAxisLabel;
    private String valueAxisLabel;
    
    // Orientation and Order
    private TableOrder order = TableOrder.BY_COLUMN;
    private PlotOrientation orientation = PlotOrientation.HORIZONTAL;

    // Misc flags
    private boolean generateLegend = false;
    private boolean generateTooltips = false;
    private boolean generateUrls = false;

    // =========================
    // Getters

    public String getTimeAxisLabel() {
        return timeAxisLabel;
    }

    public String getCategoryAxisLabel() {
        return categoryAxisLabel;
    }

    public boolean isGenerateLegend() {
        return generateLegend;
    }

    public boolean isGenerateTooltips() {
        return generateTooltips;
    }

    public boolean isGenerateUrls() {
        return generateUrls;
    }

    public PlotOrientation getOrientation() {
        return orientation;
    }

    public TableOrder getOrder() {
        return order;
    }

    public String getTitle() {
        return title;
    }

    public String getValueAxisLabel() {
        return valueAxisLabel;
    }

    public int getImageHeight() {
        return imageHeight;
    }

    public int getImageWidth() {
        return imageWidth;
    }

    public String getDomainAxisLabel() {
        return domainAxisLabel;
    }

    public String getRangeAxisLabel() {
        return rangeAxisLabel;
    }

    /**
     *  Read configuration from node and initialize configuration.
     * @throws XPathException Thrown when an element cannot be read.
     */
    public void parse(Node configuration) throws XPathException {

        if (configuration.getNodeType() == Node.ELEMENT_NODE && configuration.getLocalName().equals("configuration")) {

            //Get the First Child
            Node child = configuration.getFirstChild();
            while (child != null) {
                //Parse each of the child nodes
                if (child.getNodeType() == Node.ELEMENT_NODE && child.hasChildNodes()) {
                    if (child.getLocalName().equals("title")) {
                        String value = getValue(child);
                        if (value == null) {
                            throw new XPathException("Value for 'title' cannot be parsed");
                        } else {
                            title = value;
                        }

                    } else if (child.getLocalName().equals("categoryAxisLabel")) {
                        String value = getValue(child);
                        if (value == null) {
                            throw new XPathException("Value for 'categoryAxisLabel' cannot be parsed");
                        } else {
                            categoryAxisLabel = value;
                        }

                    } else if (child.getLocalName().equals("valueAxisLabel")) {
                        String value = getValue(child);
                        if (value == null) {
                            throw new XPathException("Value for 'valueAxisLabel' cannot be parsed");
                        } else {
                            valueAxisLabel = value;
                        }

                    } else if (child.getLocalName().equals("timeAxisLabel")) {
                        String value = getValue(child);
                        if (value == null) {
                            throw new XPathException("Value for 'timeAxisLabel' cannot be parsed");
                        } else {
                            timeAxisLabel = value;
                        }

                    } else if (child.getLocalName().equals("domainAxisLabel")) {
                        String value = getValue(child);
                        if (value == null) {
                            throw new XPathException("Value for 'domainAxisLabel' cannot be parsed");
                        } else {
                            domainAxisLabel = value;
                        }
                        
                    } else if (child.getLocalName().equals("rangeAxisLabel")) {
                        String value = getValue(child);
                        if (value == null) {
                            throw new XPathException("Value for 'rangeAxisLabel' cannot be parsed");
                        } else {
                            rangeAxisLabel = value;
                        }

                    } else if (child.getLocalName().equals("orientation")) {
                        String value = getValue(child);
                        if (value == null) {
                            throw new XPathException("Value for 'orientation' cannot be parsed");

                        } else if ("HORIZONTAL".equalsIgnoreCase(value)) {
                            orientation = PlotOrientation.HORIZONTAL;

                        } else if ("HORIZONTAL".equalsIgnoreCase(value)) {
                            orientation = PlotOrientation.VERTICAL;

                        } else {
                            throw new XPathException("Wrong value for 'orientation'");
                        }


                    } else if (child.getLocalName().equals("tableOrder")) {
                        String value = getValue(child);
                        if (value == null) {
                            throw new XPathException("Value for 'tableOrder' cannot be parsed");

                        } else if ("COLUMN".equalsIgnoreCase(value)) {
                            order = TableOrder.BY_COLUMN;

                        } else if ("ROW".equalsIgnoreCase(value)) {
                            order = TableOrder.BY_ROW;

                        } else {
                            throw new XPathException("Wrong value for 'tableOrder'");
                        }

                    } else if (child.getLocalName().equals("legend")) {
                        Boolean value = parseBoolean(getValue(child));
                        if (value == null) {
                            throw new XPathException("Value for 'legend' cannot be parsed");
                        } else {
                            generateLegend = value;
                        }

                    } else if (child.getLocalName().equals("tooltips")) {
                        Boolean value = parseBoolean(getValue(child));
                        if (value == null) {
                            throw new XPathException("Value for 'tooltips' cannot be parsed");
                        } else {
                            generateTooltips = value;
                        }

                    } else if (child.getLocalName().equals("urls")) {
                        Boolean value = parseBoolean(getValue(child));
                        if (value == null) {
                            throw new XPathException("Value for 'urls' cannot be parsed");
                        } else {
                            generateUrls = value;
                        }

                    } else if (child.getLocalName().equals("width")) {
                        Integer value = parseInteger(getValue(child));
                        if (value == null) {
                            throw new XPathException("Value for 'width' cannot be parsed");
                        } else {
                            imageWidth = value;
                        }

                    } else if (child.getLocalName().equals("height")) {
                        Integer value = parseInteger(getValue(child));
                        if (value == null) {
                            throw new XPathException("Value for 'height' cannot be parsed");
                        } else {
                            imageHeight = value;
                        }

                    }
                }

                //next node
                child = child.getNextSibling();

            }

        }
    }



    /**
     * Parse text and return boolean. Accepted values Yes No True False,
     * otherwise NULL is returned.
     */
    private Boolean parseBoolean(String value) {
        if (value == null) {
            return null;

        } else if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes")) {
            return true;

        } else if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("no")) {
            return false;
        }
        return null;
    }

    /**
     *  Parse text and return Integer. NULL is returned when value
     * cannot be converted.
     */
    private Integer parseInteger(String value) {

        try {
            return Integer.valueOf(value);

        } catch (NumberFormatException ex) {
            logger.debug(ex.getMessage());
        }
        return null;
    }

    /**
     * Helper method for getting the value of the (first) node.
     */
    private String getValue(Node child) {
        return child.getFirstChild().getNodeValue();
    }
}
