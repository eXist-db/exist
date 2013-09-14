/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009-2013 The eXist-db Project
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
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.util.TableOrder;
import org.w3c.dom.Node;

import java.awt.Color;

/**
 * Class for storing all configuration items for charts, except chart type.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 * @author Andrzej Taramina (andrzej@chaeron.com)
 * @author Leif-Jöran Olsson (ljo@exist-db.org)
 */
public class Configuration {

    private final static Logger logger = Logger.getLogger(Configuration.class);

    // Default dimension of image
    private int imageHeight = 300;
    private int imageWidth = 400;

    // Chart title
    private String title;
    
    // Image type
    private String imageType = "png";


    // Labels
    private String categoryAxisLabel;
    private String domainAxisLabel;
    private String rangeAxisLabel;
    private String timeAxisLabel;
    private String valueAxisLabel;
    private String pieSectionLabel;
    private String pieSectionNumberFormat                   = "0";
    private String pieSectionPercentFormat                  = "0.0%";
    
    private String categoryItemLabelGeneratorClass;
    private String categoryItemLabelGeneratorParameter      = "{2}";
    private String categoryItemLabelGeneratorNumberFormat   = "0";
    private CategoryLabelPositions categoryLabelPositions = CategoryLabelPositions.STANDARD;
    
    // Orientation and Order
    private TableOrder order = TableOrder.BY_COLUMN;
    private PlotOrientation orientation = PlotOrientation.HORIZONTAL;
    
    // Colors   
    private Color titleColor;
    private Color chartBackgroundColor;
    private Color plotBackgroundColor;
	
	private Color categoryAxisColor;
    private Color timeAxisColor;
    private Color valueAxisColor;
    
    private String seriesColors;
    
    private String sectionColors;
    private String sectionColorsDelimiter                   = ",";
    
    
    // Range  
    private Double  rangeLowerBound;
    private Double  rangeUpperBound;

    // Misc flags
    private boolean generateLegend = false;
    private boolean generateTooltips = false;
    private boolean generateUrls = false;

    
    // =========================
    // Getters
    
    public String getImageType() {
        return imageType;
    }

    public String getTimeAxisLabel() {
        return timeAxisLabel;
    }
	
	public Color getTimeAxisColor() {
        return timeAxisColor;
    }

    public String getCategoryAxisLabel() {
        return categoryAxisLabel;
    }
	
	public Color getCategoryAxisColor() {
        return categoryAxisColor;
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
	
	public Color getValueAxisColor() {
        return valueAxisColor;
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
    
    public String getPieSectionLabel() {
        return pieSectionLabel;
    }
    
    public String getPieSectionNumberFormat() {
        return pieSectionNumberFormat;
    }
    
    public String getPieSectionPercentFormat() {
        return pieSectionPercentFormat;
    }
    
    public Color getTitleColor() {
        return titleColor;
    }
    
    public Color getChartBackgroundColor() {
        return chartBackgroundColor;
    }
    
    public Color getPlotBackgroundColor() {
        return plotBackgroundColor;
    }
    
    public Double getRangeLowerBound() {
        return rangeLowerBound;
    }
    
    public Double getRangeUpperBound() {
        return rangeUpperBound;
    }
    
    public String getCategoryItemLabelGeneratorClass() {
        return categoryItemLabelGeneratorClass;
    }
    
    public String getCategoryItemLabelGeneratorParameter() {
        return categoryItemLabelGeneratorParameter;
    }
    
    public String getCategoryItemLabelGeneratorNumberFormat() {
        return categoryItemLabelGeneratorNumberFormat;
    }

    public CategoryLabelPositions getCategoryLabelPositions() {
        return categoryLabelPositions;
    }
    
    public String getSeriesColors() {
        return seriesColors;
    }
    
    public String getSectionColors() {
        return sectionColors;
    }
    
    public String getSectionColorsDelimiter() {
        return sectionColorsDelimiter;
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
						
					} else if (child.getLocalName().equals("categoryAxisColor")) {
                        Color value = Colour.getColor(getValue(child));
                        if (value == null) {
                            throw new XPathException("Value for 'categoryAxisColor' cannot be parsed");
                        } else {
                            categoryAxisColor = value;
                        }

                    } else if (child.getLocalName().equals("valueAxisLabel")) {
                        String value = getValue(child);
                        if (value == null) {
                            throw new XPathException("Value for 'valueAxisLabel' cannot be parsed");
                        } else {
                            valueAxisLabel = value;
                        }
						
					} else if (child.getLocalName().equals("valueAxisColor")) {
                        Color value = Colour.getColor(getValue(child));
                        if (value == null) {
                            throw new XPathException("Value for 'valueAxisColor' cannot be parsed");
                        } else {
                            valueAxisColor = value;
                        }

                    } else if (child.getLocalName().equals("timeAxisLabel")) {
                        String value = getValue(child);
                        if (value == null) {
                            throw new XPathException("Value for 'timeAxisLabel' cannot be parsed");
                        } else {
                            timeAxisLabel = value;
                        }
						
					} else if (child.getLocalName().equals("timeAxisColor")) {
                        Color value = Colour.getColor(getValue(child));
                        if (value == null) {
                            throw new XPathException("Value for 'timeAxisColor' cannot be parsed");
                        } else {
                            timeAxisColor = value;
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
						
					} else if (child.getLocalName().equals("pieSectionLabel")) {
                        String value = getValue(child);
                        if (value == null) {
                            throw new XPathException("Value for 'pieSectionLabel' cannot be parsed");
                        } else {
                            pieSectionLabel = value;
                        }
                        
                     } else if (child.getLocalName().equals("pieSectionNumberFormat")) {
                        String value = getValue(child);
                        if (value == null) {
                            throw new XPathException("Value for 'pieSectionNumberFormat' cannot be parsed");
                        } else {
                            pieSectionNumberFormat = value;
                        }
                        
                     } else if (child.getLocalName().equals("pieSectionPercentFormat")) {
                        String value = getValue(child);
                        if (value == null) {
                            throw new XPathException("Value for 'pieSectionPercentFormat' cannot be parsed");
                        } else {
                            pieSectionPercentFormat = value;
                        }


                    } else if (child.getLocalName().equals("orientation")) {
                        String value = getValue(child);
                        if (value == null) {
                            throw new XPathException("Value for 'orientation' cannot be parsed");

                        } else if ("HORIZONTAL".equalsIgnoreCase(value)) {
                            orientation = PlotOrientation.HORIZONTAL;

                        } else if ("VERTICAL".equalsIgnoreCase(value)) {
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

                    } else if (child.getLocalName().equals("titleColor")) {
                        Color value = Colour.getColor(getValue(child));
                        if (value == null) {
                            throw new XPathException("Value for 'titleColor' cannot be parsed");
                        } else {
                            titleColor = value;
                        }

                    } else if (child.getLocalName().equals("chartBackgroundColor")) {
                        Color value = Colour.getColor(getValue(child));
                        if (value == null) {
                            throw new XPathException("Value for 'chartBackgroundColor' cannot be parsed");
                        } else {
                            chartBackgroundColor = value;
                        }
                        
                    } else if (child.getLocalName().equals("plotBackgroundColor")) {
                        Color value = Colour.getColor(getValue(child));
                        if (value == null) {
                            throw new XPathException("Value for 'plotBackgroundColor' cannot be parsed");
                        } else {
                            plotBackgroundColor = value;
                        }
                        
                    } else if (child.getLocalName().equals("seriesColors")) {
                        String value = getValue(child);
                        if (value == null) {
                            throw new XPathException("Value for 'seriesColors' cannot be parsed");
                        } else {
                            seriesColors = value;
                        }
                        
                     } else if (child.getLocalName().equals("sectionColors")) {
                        String value = getValue(child);
                        if (value == null) {
                            throw new XPathException("Value for 'sectionColors' cannot be parsed");
                        } else {
                            sectionColors = value;
                        }
                        
                     } else if (child.getLocalName().equals("sectionColorsDelimiter")) {
                        String value = getValue(child);
                        if (value == null) {
                            throw new XPathException("Value for 'sectionColorsDelimiter' cannot be parsed");
                        } else {
                            sectionColorsDelimiter = value;
                        }
                        
                    } else if (child.getLocalName().equals("rangeLowerBound")) {
                        Double value = parseDouble(getValue(child));
                        if (value == null) {
                            throw new XPathException("Value for 'rangeLowerBound' cannot be parsed");
                        } else {
                            rangeLowerBound = value;
                        }
                        
                    } else if (child.getLocalName().equals("rangeUpperBound")) {
                        Double value = parseDouble(getValue(child));
                        if (value == null) {
                            throw new XPathException("Value for 'rangeUpperBound' cannot be parsed");
                        } else {
                            rangeUpperBound = value;
                        }
                        
                   } else if (child.getLocalName().equals("categoryItemLabelGeneratorClass")) {
                        String value = getValue(child);
                        if (value == null) {
                            throw new XPathException("Value for 'categoryItemLabelGeneratorClass' cannot be parsed");
                        } else {
                            categoryItemLabelGeneratorClass = value;
                        }
                        
                    } else if (child.getLocalName().equals("categoryItemLabelGeneratorParameter")) {
                        String value = getValue(child);
                        if (value == null) {
                            throw new XPathException("Value for 'categoryItemLabelGeneratorParameter' cannot be parsed");
                        } else {
                            categoryItemLabelGeneratorParameter = value;
                        }
                    
                    } else if (child.getLocalName().equals("categoryItemLabelGeneratorNumberFormat")) {
                        String value = getValue(child);
                        if (value == null) {
                            throw new XPathException("Value for 'categoryItemLabelGeneratorNumberFormat' cannot be parsed");
                        } else {
                            categoryItemLabelGeneratorNumberFormat = value;
                        }

                   } else if (child.getLocalName().equals("categoryLabelPositions")) {
                        String value = getValue(child);
                        if (value == null) {
                            throw new XPathException("Value for 'categoryLabelPostions' cannot be parsed");
                        } else if ("UP_45".equalsIgnoreCase(value)) {
                            categoryLabelPositions = CategoryLabelPositions.UP_45;
                        } else if ("UP_90".equalsIgnoreCase(value)) {
                            categoryLabelPositions = CategoryLabelPositions.UP_90;
                        } else if ("DOWN_45".equalsIgnoreCase(value)) {
                            categoryLabelPositions = CategoryLabelPositions.DOWN_45;

                        } else if ("DOWN_90".equalsIgnoreCase(value)) {
                            categoryLabelPositions = CategoryLabelPositions.DOWN_90;

                        } else {
                            throw new XPathException("Wrong value for 'categoryLabelPositions'");
                        }
                      
                    } else if (child.getLocalName().equals("imageType")) {
                        String value = getValue(child);
                        if (value == null) {
                            throw new XPathException("Value for 'imageType' cannot be parsed");
                        } else {
                            imageType = value;
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
     *  Parse text and return Double. NULL is returned when value
     * cannot be converted.
     */
    private Double parseDouble(String value) {

        try {
            return Double.valueOf(value);

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
