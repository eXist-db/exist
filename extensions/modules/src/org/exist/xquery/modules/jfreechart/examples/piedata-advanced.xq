(: Advanced 
Example code for jfreechart module :)
(: Load the data files into /db :)
(: $Id: piedata.xq 8838 2009-04-14 18:01:51Z dizzzz $ :)

declare namespace jfreechart = "http://exist-db.org/xquery/jfreechart";

(: Example use of all advanced config parameters :)

let $config := 
    <configuration>
        <orientation>Horizontal</orientation>
        <height>500</height>
        <width>700</width>
        <title>Example 2</title>
        <pieSectionLabel>{{0}} - {{1}} ({{2}})</pieSectionLabel>
        <sectionColors>Java; orange; C++; green; PHP; blue; Python; black</sectionColors>
        <sectionColorsDelimiter>;</sectionColorsDelimiter>
    </configuration>

return
    jfreechart:stream-render( "PieChart", $config, doc( '/db/piedata-advanced.xml' ) )
