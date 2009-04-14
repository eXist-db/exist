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

import java.io.IOException;
import java.io.InputStream;

import org.apache.log4j.Logger;

import org.exist.xquery.XPathException;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.general.PieDataset;
import org.jfree.data.xml.DatasetReader;

/**
 * Wrapper for JFreeChart's ChartFactory.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
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
    public static JFreeChart createJFreeChart(String chartType, Configuration conf, InputStream is) throws XPathException {

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

        } else if ("BarChart".equalsIgnoreCase(chartType)) {
            chart = ChartFactory.createBarChart(
                    conf.getTitle(), conf.getCategoryAxisLabel(), conf.getValueAxisLabel(), categoryDataset,
                    conf.getOrientation(), conf.isGenerateLegend(), conf.isGenerateTooltips(), conf.isGenerateUrls());

        } else if ("BarChart3D".equalsIgnoreCase(chartType)) {
            chart = ChartFactory.createBarChart3D(
                    conf.getTitle(), conf.getCategoryAxisLabel(), conf.getValueAxisLabel(), categoryDataset,
                    conf.getOrientation(), conf.isGenerateLegend(), conf.isGenerateTooltips(), conf.isGenerateUrls());

        } else if ("LineChart".equalsIgnoreCase(chartType)) {
            chart = ChartFactory.createLineChart(
                    conf.getTitle(), conf.getCategoryAxisLabel(), conf.getValueAxisLabel(), categoryDataset,
                    conf.getOrientation(), conf.isGenerateLegend(), conf.isGenerateTooltips(), conf.isGenerateUrls());

        } else if ("LineChart3D".equalsIgnoreCase(chartType)) {
            chart = ChartFactory.createLineChart3D(
                    conf.getTitle(), conf.getCategoryAxisLabel(), conf.getValueAxisLabel(), categoryDataset,
                    conf.getOrientation(), conf.isGenerateLegend(), conf.isGenerateTooltips(), conf.isGenerateUrls());

        } else if ("MultiplePieChart".equalsIgnoreCase(chartType)) {
            chart = ChartFactory.createMultiplePieChart(
                    conf.getTitle(), categoryDataset, conf.getOrder(),
                    conf.isGenerateLegend(), conf.isGenerateTooltips(), conf.isGenerateUrls());

        } else if ("MultiplePieChart3D".equalsIgnoreCase(chartType)) {
            chart = ChartFactory.createMultiplePieChart3D(
                    conf.getTitle(), categoryDataset, conf.getOrder(),
                    conf.isGenerateLegend(), conf.isGenerateTooltips(), conf.isGenerateUrls());

        } else if ("PieChart".equalsIgnoreCase(chartType)) {
            chart = ChartFactory.createPieChart(
                    conf.getTitle(), pieDataset,
                    conf.isGenerateLegend(), conf.isGenerateTooltips(), conf.isGenerateUrls());

        } else if ("PieChart3D".equalsIgnoreCase(chartType)) {
            chart = ChartFactory.createPieChart3D(
                    conf.getTitle(), pieDataset,
                    conf.isGenerateLegend(), conf.isGenerateTooltips(), conf.isGenerateUrls());

        } else if ("RingChart".equalsIgnoreCase(chartType)) {
            chart = ChartFactory.createRingChart(
                    conf.getTitle(), pieDataset,
                    conf.isGenerateLegend(), conf.isGenerateTooltips(), conf.isGenerateUrls());

        } else if ("StackedAreaChart".equalsIgnoreCase(chartType)) {
            chart = ChartFactory.createStackedAreaChart(
                    conf.getTitle(), conf.getCategoryAxisLabel(), conf.getValueAxisLabel(), categoryDataset,
                    conf.getOrientation(), conf.isGenerateLegend(), conf.isGenerateTooltips(), conf.isGenerateUrls());

        } else if ("StackedBarChart".equalsIgnoreCase(chartType)) {
            chart = ChartFactory.createStackedBarChart(
                    conf.getTitle(), conf.getDomainAxisLabel(), conf.getRangeAxisLabel(), categoryDataset,
                    conf.getOrientation(), conf.isGenerateLegend(), conf.isGenerateTooltips(), conf.isGenerateUrls());

        } else if ("StackedBarChart3D".equalsIgnoreCase(chartType)) {
            chart = ChartFactory.createStackedBarChart3D(
                    conf.getTitle(), conf.getCategoryAxisLabel(), conf.getValueAxisLabel(), categoryDataset,
                    conf.getOrientation(), conf.isGenerateLegend(), conf.isGenerateTooltips(), conf.isGenerateUrls());

        } else if ("WaterfallChart".equalsIgnoreCase(chartType)) {
            chart = ChartFactory.createWaterfallChart(
                    conf.getTitle(), conf.getCategoryAxisLabel(), conf.getValueAxisLabel(), categoryDataset,
                    conf.getOrientation(), conf.isGenerateLegend(), conf.isGenerateTooltips(), conf.isGenerateUrls());
        } else {
            logger.error("Illegal chartype. Choose one of " +
                    "AreaChart BarChart BarChart3D LineChart LineChart3D " +
                    "MultiplePieChart MultiplePieChart3D PieChart PieChart3D " +
                    "RingChart StackedAreaChart StackedBarChart " +
                    "StackedBarChart3D WaterfallChart");
        }

        return chart;
    }
}
