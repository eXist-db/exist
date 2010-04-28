(: Advanced Example code for jfreechart module :)
(: Load the data files into /db :)
(: $Id: categorydata.xq 8838 2009-04-14 18:01:51Z dizzzz $ :)

declare namespace jfreechart = "http://exist-db.org/xquery/jfreechart";

(: Example use of all advanced config parameters :)

let $config := 
    <configuration>
        <orientation>Horizontal</orientation>
        <height>500</height>
        <width>700</width>
        <title>Example 1</title>
        <titleColor>red</titleColor>
        <rangeLowerBound>0.0</rangeLowerBound>
        <rangeUpperBound>100.0</rangeUpperBound>
        <categoryItemLabelGeneratorClass>org.jfree.chart.labels.StandardCategoryItemLabelGenerator</categoryItemLabelGeneratorClass>
        <categoryItemLabelGeneratorParameter>- {{2}}</categoryItemLabelGeneratorParameter>
        <seriesColors>orange,blue</seriesColors>
    </configuration>

return
    jfreechart:stream-render( "BarChart", $config, doc( '/db/categorydata-advanced.xml' ) )
