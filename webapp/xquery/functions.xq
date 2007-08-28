xquery version "1.0";
(: $Id$ :)
(: prints a list of all built-in functions :)

declare namespace util="http://exist-db.org/xquery/util";

<book>
  <bookinfo>
    <graphic fileref="logo.jpg"/>

    <productname>Open Source Native XML Database</productname>
    <title>XQuery Function Documentation</title>

    <author>
      <firstname>Wolfgang M.</firstname>
      <surname>Meier</surname>
      <affiliation>
        <address format="linespecific">
          <email>wolfgang at exist-db.org</email>
        </address>
      </affiliation>
    </author>
    <style href="styles/functions.css"/>
  </bookinfo>
    
    <xi:include xmlns:xi="http://www.w3.org/2001/XInclude" href="sidebar.xml"/>

    <chapter>
		<title>XQuery Function Documentation</title>
		        
            <para>The following tables show all built-in functions available within
            the current execution context (<b>eXist version: {util:system-property("product-version")}, build: {util:system-property("product-build")}</b>). The information is automatically generated 
            using the extension function <ulink url="#util:describe-function">
            util:describe-function</ulink>. Function descriptions are directly extracted
            from the function signature provided by each function implementation.</para>
            
            <builtin-functions>
            {
                for $mod in util:registered-modules()
				let $functions := util:registered-functions($mod)
				order by $mod descending
                return
					<module namespace="{$mod}">
						<description>{util:get-module-description($mod)}</description>
					{
						for $f in $functions 
						return
                        	util:describe-function($f)
					}
					</module>
            }
            </builtin-functions>


			<para>
				<small>View <a href="source/functions.xq">source code</a>
				</small>
			</para>
    </chapter>
</book>
