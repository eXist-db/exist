xquery version "1.0";

(: prints a list of all built-in functions :)
declare namespace util="http://exist-db.org/xquery/util";

<document xmlns:xi="http://www.w3.org/2001/XInclude">
	
	<xi:include href="context://header.xml"/>
	<xi:include href="sidebar.xml"/>

	<body>
		<section title="Builtin Functions">
        
            <p>The following tables show all built-in functions available within
            the current execution context. The information is automatically generated 
            using the extension function <link href="#util:describe-function">
            util:describe-function</link>. Function descriptions are directly extracted
            from the function signature provided by each function implementation.</p>
            
                <builtin-functions>
                {
                    for $f in util:builtin-functions()
                    return
                        util:describe-function($f)
                }
                </builtin-functions>


			<p>
				<small>View <a href="source/functions.xq">source code</a>
				</small>
			</p>
        </section>
    </body>
</document>
