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

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.exist.xquery.XPathException;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.labels.CategoryItemLabelGenerator;
import org.jfree.chart.labels.StandardCategoryToolTipGenerator;
import org.jfree.chart.labels.StandardPieSectionLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.plot.MultiplePiePlot;
import org.jfree.chart.plot.SpiderWebPlot;
import org.jfree.chart.renderer.category.CategoryItemRenderer;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.general.PieDataset;
import org.jfree.data.xml.DatasetReader;
import org.jfree.ui.RectangleEdge;

/**
 * Wrapper for JFreeChart's ChartFactory.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 * @author Andrzej Taramina (andrzej@chaeron.com)
 * @author Leif-Jöran Olsson (ljo@exist-db.org)
 */
public class JFreeChartFactory {

    private final static Logger logger = Logger.getLogger(JFreeChartFactory.class);

    /**
     *  Create JFreeChart graph using the supplied parameters.
     *
     * @param chartType One of the many chart types.
     * @param conf      Chart configuration
     * @param is        Inputstream containing chart data
     * @return          Initialized chart or NULL in case of issues.
     * @throws IOException Thrown when a problem is reported while parsing XML data.
     */
    public static JFreeChart createJFreeChart(String chartType, Configuration conf, InputStream is)
            throws XPathException {

        logger.debug("Generating "+chartType);

        // Currently two dataset types supported
        CategoryDataset categoryDataset = null;
        PieDataset pieDataset = null;

        try{
            if ("PieChart".equals(chartType)
                    || "PieChart3D".equals(chartType)
                    || "RingChart".equals(chartType)) {
                logger.debug("Reading XML PieDataset");
                pieDataset = DatasetReader.readPieDatasetFromXML(is);

            } else {
                logger.debug("Reading XML CategoryDataset");
                categoryDataset = DatasetReader.readCategoryDatasetFromXML(is);
            }
            
        } catch(IOException ex){
            throw new XPathException(ex.getMessage());

        } finally {
            try {
                is.close();
            } catch (IOException ex) {
                //
            }
        }

        // Return chart
        JFreeChart chart = null;

        // Big chart type switch
        if ("AreaChart".equalsIgnoreCase(chartType)) {
            chart = ChartFactory.createAreaChart(
                    conf.getTitle(), conf.getCategoryAxisLabel(), conf.getValueAxisLabel(), categoryDataset,
                    conf.getOrientation(), conf.isGenerateLegend(), conf.isGenerateTooltips(), conf.isGenerateUrls());
                    
                    setCategoryChartParameters( chart, conf );

        } else if ("BarChart".equalsIgnoreCase(chartType)) {
            chart = ChartFactory.createBarChart(
                    conf.getTitle(), conf.getCategoryAxisLabel(), conf.getValueAxisLabel(), categoryDataset,
                    conf.getOrientation(), conf.isGenerateLegend(), conf.isGenerateTooltips(), conf.isGenerateUrls());
                    
                    setCategoryChartParameters( chart, conf );

        } else if ("BarChart3D".equalsIgnoreCase(chartType)) {
            chart = ChartFactory.createBarChart3D(
                    conf.getTitle(), conf.getCategoryAxisLabel(), conf.getValueAxisLabel(), categoryDataset,
                    conf.getOrientation(), conf.isGenerateLegend(), conf.isGenerateTooltips(), conf.isGenerateUrls());
                    
                    setCategoryChartParameters( chart, conf );

        } else if ("LineChart".equalsIgnoreCase(chartType)) {
            chart = ChartFactory.createLineChart(
                    conf.getTitle(), conf.getCategoryAxisLabel(), conf.getValueAxisLabel(), categoryDataset,
                    conf.getOrientation(), conf.isGenerateLegend(), conf.isGenerateTooltips(), conf.isGenerateUrls());
                    
                    setCategoryChartParameters( chart, conf );

        } else if ("LineChart3D".equalsIgnoreCase(chartType)) {
            chart = ChartFactory.createLineChart3D(
                    conf.getTitle(), conf.getCategoryAxisLabel(), conf.getValueAxisLabel(), categoryDataset,
                    conf.getOrientation(), conf.isGenerateLegend(), conf.isGenerateTooltips(), conf.isGenerateUrls());
                    
                    setCategoryChartParameters( chart, conf );

        } else if ("MultiplePieChart".equalsIgnoreCase(chartType)) {
            chart = ChartFactory.createMultiplePieChart(
                    conf.getTitle(), categoryDataset, conf.getOrder(),
                    conf.isGenerateLegend(), conf.isGenerateTooltips(), conf.isGenerateUrls());
                    
                    setPieChartParameters( chart, conf );

        } else if ("MultiplePieChart3D".equalsIgnoreCase(chartType)) {
            chart = ChartFactory.createMultiplePieChart3D(
                    conf.getTitle(), categoryDataset, conf.getOrder(),
                    conf.isGenerateLegend(), conf.isGenerateTooltips(), conf.isGenerateUrls());
                    
                    setPieChartParameters( chart, conf );

        } else if ("PieChart".equalsIgnoreCase(chartType)) {
            chart = ChartFactory.createPieChart(
                    conf.getTitle(), pieDataset,
                    conf.isGenerateLegend(), conf.isGenerateTooltips(), conf.isGenerateUrls());
                    
                    setPieChartParameters( chart, conf );

        } else if ("PieChart3D".equalsIgnoreCase(chartType)) {
            chart = ChartFactory.createPieChart3D(
                    conf.getTitle(), pieDataset,
                    conf.isGenerateLegend(), conf.isGenerateTooltips(), conf.isGenerateUrls());
                    
                    setPieChartParameters( chart, conf );

        } else if ("RingChart".equalsIgnoreCase(chartType)) {
            chart = ChartFactory.createRingChart(
                    conf.getTitle(), pieDataset,
                    conf.isGenerateLegend(), conf.isGenerateTooltips(), conf.isGenerateUrls());
                    
                     setPieChartParameters( chart, conf );
        } else if ("SpiderWebChart".equalsIgnoreCase(chartType)) {
            SpiderWebPlot plot = new SpiderWebPlot(categoryDataset);
            if (conf.isGenerateTooltips()) {
                plot.setToolTipGenerator(new StandardCategoryToolTipGenerator());
            }
            chart = new JFreeChart(conf.getTitle(), JFreeChart.DEFAULT_TITLE_FONT, plot, false);

            if (conf.isGenerateLegend()) {
                LegendTitle legend = new LegendTitle(plot);
                legend.setPosition(RectangleEdge.BOTTOM);
                chart.addSubtitle(legend);
            } else {
                TextTitle subTitle = new TextTitle(" ");
                subTitle.setPosition(RectangleEdge.BOTTOM);
                chart.addSubtitle(subTitle);
            }


            setCategoryChartParameters(chart, conf);

        } else if ("StackedAreaChart".equalsIgnoreCase(chartType)) {
            chart = ChartFactory.createStackedAreaChart(
                    conf.getTitle(), conf.getCategoryAxisLabel(), conf.getValueAxisLabel(), categoryDataset,
                    conf.getOrientation(), conf.isGenerateLegend(), conf.isGenerateTooltips(), conf.isGenerateUrls());
                    
                    setCategoryChartParameters( chart, conf );

        } else if ("StackedBarChart".equalsIgnoreCase(chartType)) {
            chart = ChartFactory.createStackedBarChart(
                    conf.getTitle(), conf.getDomainAxisLabel(), conf.getRangeAxisLabel(), categoryDataset,
                    conf.getOrientation(), conf.isGenerateLegend(), conf.isGenerateTooltips(), conf.isGenerateUrls());
                    
                    setCategoryChartParameters( chart, conf );

        } else if ("StackedBarChart3D".equalsIgnoreCase(chartType)) {
            chart = ChartFactory.createStackedBarChart3D(
                    conf.getTitle(), conf.getCategoryAxisLabel(), conf.getValueAxisLabel(), categoryDataset,
                    conf.getOrientation(), conf.isGenerateLegend(), conf.isGenerateTooltips(), conf.isGenerateUrls());
                    
                    setCategoryChartParameters( chart, conf );

        } else if ("WaterfallChart".equalsIgnoreCase(chartType)) {
            chart = ChartFactory.createWaterfallChart(
                    conf.getTitle(), conf.getCategoryAxisLabel(), conf.getValueAxisLabel(), categoryDataset,
                    conf.getOrientation(), conf.isGenerateLegend(), conf.isGenerateTooltips(), conf.isGenerateUrls());
                    
                    setCategoryChartParameters( chart, conf );
        } else {
            logger.error("Illegal chartype. Choose one of " +
                    "AreaChart BarChart BarChart3D LineChart LineChart3D " +
                    "MultiplePieChart MultiplePieChart3D PieChart PieChart3D " +
                    "RingChart SpiderWebChart StackedAreaChart StackedBarChart " +
                    "StackedBarChart3D WaterfallChart");
        }
        
        setCommonParameters( chart, conf );

        return chart;
    }
    
    
    private static void setCategoryChartParameters( JFreeChart chart, Configuration config ) throws XPathException
    {
        setCategoryRange( chart, config );
        setCategoryItemLabelGenerator( chart, config );
	setCategoryLabelPositions( chart, config );
        setSeriesColors( chart, config );
	setAxisColors( chart, config );
    }
    
    
    private static void setCategoryRange( JFreeChart chart, Configuration config )
    {
        Double rangeLowerBound          = config.getRangeLowerBound();
        Double rangeUpperBound          = config.getRangeUpperBound();

        if( rangeUpperBound != null ) {
            if (chart.getPlot() instanceof SpiderWebPlot) {
                ((SpiderWebPlot) chart.getPlot()).setMaxValue(rangeUpperBound.doubleValue());
                return;
            } else {
                ((CategoryPlot)chart.getPlot()).getRangeAxis().setUpperBound( rangeUpperBound.doubleValue() );
            }
        } 
        
        if( rangeLowerBound != null ) {
            ((CategoryPlot)chart.getPlot()).getRangeAxis().setLowerBound( rangeLowerBound.doubleValue() );
        }
    }
    
    private static void setCategoryItemLabelGenerator( JFreeChart chart, Configuration config ) throws XPathException
    {
        String                      className  = config.getCategoryItemLabelGeneratorClass();
        CategoryItemLabelGenerator  generator  = null;
        
        if( className != null ) {
            try {
                Class generatorClass = Class.forName( className );
                
                 Class[] argsClass  = new Class[] { String.class, NumberFormat.class };
                 String param       = config.getCategoryItemLabelGeneratorParameter();
                 NumberFormat fmt   = new DecimalFormat( config.getCategoryItemLabelGeneratorNumberFormat() );
                 Object[] args      = new Object[] { param, fmt };
                 
                 Constructor argsConstructor = generatorClass.getConstructor( argsClass );
                
                 generator = (CategoryItemLabelGenerator)argsConstructor.newInstance( args );
            }
            catch( Exception e ) {
                throw( new XPathException( "Cannot instantiate CategoryItemLabelGeneratorClass: " + className + ", exception: " + e ) );
            }

            if (chart.getPlot() instanceof SpiderWebPlot) {
                ((SpiderWebPlot) chart.getPlot()).setLabelGenerator(generator);
            } else {
                CategoryItemRenderer renderer = ((CategoryPlot)chart.getPlot()).getRenderer();
                
                renderer.setBaseItemLabelGenerator( generator );
                
                renderer.setItemLabelsVisible( true );
            }
        }
    }
    
    private static void setCategoryLabelPositions( JFreeChart chart, Configuration config )
    {
        CategoryLabelPositions positions  = config.getCategoryLabelPositions();
	if (chart.getPlot() instanceof CategoryPlot) {
	    ((CategoryPlot)chart.getPlot()).getDomainAxis().setCategoryLabelPositions(positions);
	}
    }
    
    private static void setSeriesColors( JFreeChart chart, Configuration config )
    {
        String seriesColors          = config.getSeriesColors();
        

        if (chart.getPlot() instanceof SpiderWebPlot) {
            setSeriesColors((SpiderWebPlot) chart.getPlot(), seriesColors);
        } else {
            CategoryItemRenderer renderer = ((CategoryPlot)chart.getPlot()).getRenderer();
            setSeriesColors(renderer, seriesColors);
        }
    }

    private static void setSeriesColors(Object renderer, final String seriesColors) {
        if (seriesColors != null) {
            StringTokenizer st = new StringTokenizer( seriesColors, "," );

            int i = 0;
            while( st.hasMoreTokens() ) {
                String colorName = st.nextToken().trim();
                Color color = null;
                try {
                    color = Colour.getColor( colorName );
                } catch( XPathException e ) {              
                }
                   
                if( color != null ) {
                    if (renderer instanceof SpiderWebPlot) {
                        ((SpiderWebPlot) renderer).setSeriesPaint(i, color);
                    } else {
                        ((CategoryItemRenderer) renderer).setSeriesPaint( i, color );
                    }
                } else {
                    logger.warn( "Invalid colour name or hex value specified for SeriesColors: " + colorName + ", default colour will be used instead." );
                }
                
                i++;
            }
        }
    }	
        
	private static void setAxisColors( JFreeChart chart, Configuration config )
    {
        Color categoryAxisColor          = config.getCategoryAxisColor();
		Color valueAxisColor          	 = config.getValueAxisColor();
        
        if( categoryAxisColor != null ) {
            if (chart.getPlot() instanceof SpiderWebPlot) {
                ((SpiderWebPlot) chart.getPlot()).setAxisLinePaint(categoryAxisColor);
            } else {
                ((CategoryPlot)chart.getPlot()).getDomainAxis().setLabelPaint(categoryAxisColor);         }
        }
		
		if( valueAxisColor != null ) {
            if (chart.getPlot() instanceof SpiderWebPlot) {
                //((SpiderWebPlot) chart.getPlot()).setAxisLinePaint(valueAxisColor);
            } else {
                ((CategoryPlot)chart.getPlot()).getRangeAxis().setLabelPaint( valueAxisColor );  
            }
        }
    }
    
    
    private static void setPieChartParameters( JFreeChart chart, Configuration config )
    {
        setPieSectionLabel( chart, config );
        setSectionColors( chart, config );
    }
    
    
    private static void setPieSectionLabel( JFreeChart chart, Configuration config )
    {
        String pieSectionLabel          = config.getPieSectionLabel();
        String pieSectionNumberFormat   = config.getPieSectionNumberFormat();
        String pieSectionPercentFormat  = config.getPieSectionPercentFormat();
        
        if( pieSectionLabel != null ) {
	    if (chart.getPlot() instanceof MultiplePiePlot) {
		((PiePlot) ((MultiplePiePlot)chart.getPlot()).getPieChart().getPlot()).setLabelGenerator( new StandardPieSectionLabelGenerator( pieSectionLabel, new DecimalFormat( pieSectionNumberFormat ), new DecimalFormat( pieSectionPercentFormat ) ) );
	    } else {
            ((PiePlot)chart.getPlot()).setLabelGenerator( new StandardPieSectionLabelGenerator( pieSectionLabel, new DecimalFormat( pieSectionNumberFormat ), new DecimalFormat( pieSectionPercentFormat ) ) );
	    }
        }
    }
    
    
    private static void setSectionColors( JFreeChart chart, Configuration config )
    {
        String sectionColors          = config.getSectionColors();
        String sectionColorsDelimiter = config.getSectionColorsDelimiter();
        
        if( sectionColors != null ) {
            PiePlot plot = ((PiePlot)chart.getPlot());
            
            StringTokenizer st = new StringTokenizer( sectionColors, sectionColorsDelimiter );
            
            while( st.hasMoreTokens() ) {
                String sectionName = st.nextToken().trim();
                String colorName = "";
                
                if( st.hasMoreTokens() ) {
                    colorName = st.nextToken().trim();
                }
                
                Color color = null;
                
                try {
                    color = Colour.getColor( colorName );
                } 
                catch( XPathException e ) {              
                }
                   
                if( color != null ) {
                    plot.setSectionPaint( sectionName, color );
                } else {
                    logger.warn( "Invalid colour name or hex value specified for SectionColors: " + colorName + ", default colour will be used instead. Section Name: " + sectionName );
                }
            }
        }
    }
    
    
    private static void setCommonParameters( JFreeChart chart, Configuration config )
    {
        setColors( chart, config );
    }
    
    
     private static void setColors( JFreeChart chart, Configuration config )
    {
        Color titleColor            = config.getTitleColor();
        Color chartBackgroundColor  = config.getChartBackgroundColor();
        Color plotBackgroundColor   = config.getPlotBackgroundColor();
        
        if( titleColor != null ) {
            chart.getTitle().setPaint( titleColor );
        }
        
        if( chartBackgroundColor != null ) {
            chart.setBackgroundPaint( chartBackgroundColor );
        }
        
        if( plotBackgroundColor != null ) {
            chart.getPlot().setBackgroundPaint( plotBackgroundColor );
        }
    }
}
