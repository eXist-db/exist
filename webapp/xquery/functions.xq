xquery version "1.0";

(: prints a list of all built-in functions :)
declare namespace util="http://exist-db.org/xquery/util";

<document xmlns:xi="http://www.w3.org/2001/XInclude">
	
	<header>
    	<logo src="logo.jpg"/>
    	<title>Open Source Native XML Database</title>
		<author email="wolfgang@exist-db.org">Wolfgang M. Meier</author>
		<style href="styles/functions.css"/>
	</header>
	
	<xi:include href="sidebar.xml"/>

	<body>
		<section title="Builtin Functions">
        
            <p>The following tables show all built-in functions available within
            the current execution context (<b>eXist version: {util:system-property("product-version")}, build: {util:system-property("product-build")}</b>). The information is automatically generated 
            using the extension function <link href="#util:describe-function">
            util:describe-function</link>. Function descriptions are directly extracted
            from the function signature provided by each function implementation.</p>
            
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


			<p>
				<small>View <a href="source/functions.xq">source code</a>
				</small>
			</p>
        </section>
    </body>
</document>
