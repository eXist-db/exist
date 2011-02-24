xquery version "1.0";
import module namespace style = "http://www.danmccreary.com/library" at "../../../modules/style.xqm";
(: Type ahead compleation test.
   Step 1: Configure eXist to be your default XQuery editor.
   You can do this by going to the main menu of oXygen and selection
   Options > Preferences > XSLT - FO - XQuery > XQuery and then selecting your eXist data source 
      :)
(: type "$s" into the area below.  You should stee the style variables appear.
   now type "style:"  You should also see the style functions now appear. :)
   
   
   
   let $app-path := $style:db-path-to-app

<return>
{$app-path}
</return>