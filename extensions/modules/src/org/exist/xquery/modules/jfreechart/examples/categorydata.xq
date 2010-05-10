(: 
    Example code for jfreechart module 
    Load the data files into /db
    
    $Id$ 
:)
declare namespace jfreechart = "http://exist-db.org/xquery/jfreechart";

jfreechart:stream-render("BarChart",

<configuration>
    <orientation>Horizontal</orientation>
    <height>500</height>
    <width>500</width>
    <title>Example 1</title>
</configuration>, 

doc('/db/categorydata.xml'))
