<Q1>
  {
    for $n in distinct-values(
   		for $i in (doc("action.xml")//* | doc("action.xml")//@*)
        	return namespace-uri($i) 
        )
    return  <ns>{$n}</ns>
  }
</Q1>
