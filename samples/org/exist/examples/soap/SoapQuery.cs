using System;

public class SoapQuery {
    
    static void Main(string[] args) {
        string query;
        if(args.Length < 1) {
            Console.Write("Enter a query: ");
            query = Console.ReadLine();
        } else
            query = args[0];
        QueryService qs = new QueryService();
        
        // execute the query
        QueryResponse resp = qs.query(query);
        Console.WriteLine("found: {0} hits in {1} ms.", resp.hits, 
        resp.queryTime);
        
        // print a table of hits by document for every collection
        foreach (QueryResponseCollection collection in resp.collections) {
            Console.WriteLine(collection.collectionName);
            QueryResponseDocument[] docs = collection.documents;
            foreach (QueryResponseDocument doc in docs)
                Console.WriteLine('\t' + doc.documentName.PadRight(40, '.') + 
                    doc.hitCount.ToString().PadLeft(10, '.'));
        }
        
        // print some results
        Console.WriteLine("\n\nRetrieving results 1..5");
        for(int i = 1; i <= 5 && i <= resp.hits; i++) {
            byte[] record = qs.retrieve(resp.resultSetId, i, "UTF-8", true);
            string str = System.Text.Encoding.UTF8.GetString(record);
            Console.WriteLine(str);
        }
    }
}
