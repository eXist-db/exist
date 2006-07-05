declare namespace atom="http://www.w3.org/2005/Atom";
<service xmlns="http://purl.org/atom/app#">
   <workspace title="{/atom:feed/atom:title}">
   { 
     "&#x0a;",
     let $current := substring-before(base-uri(/atom:feed),'/.feed.atom'),
         $current-path := substring-after($current,'/db')
        for $i in (collection($current)/atom:feed) 
           let $path :=  substring-after(substring-before(base-uri($i),'/.feed.atom'),'/db')
              return ( <collection title="{$i/atom:title}" href="/atom/edit{$path}">
                         <accept>text/*,iamge/*,application/*</accept>
                       </collection>,
                       "&#x0a;"
                     )
   }
   </workspace>
</service>