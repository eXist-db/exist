using System;
using System.Collections;
using System.IO;
using System.Text;
using System.Text.RegularExpressions;
using System.Web.Services.Protocols;

public class Parse {
    
    static QueryService qs = new QueryService();
    static AdminService admin = new AdminService();
    
    static void Main(string[] args) {
        string collection = "/db";
        string[] list = readCollection(collection);
        if(list == null) {
            Console.Write("ERROR: root collection not found.");
            return;
        }
        Console.Write("exist:{0}> ", collection);
        string cmd;
        int pos = 0;
        QueryResponse qr = null;
        Regex re = new Regex("[,\\s]+");
        while((cmd = Console.ReadLine()) != "quit") {
            string[] opt = re.Split(cmd);
            switch(opt[0].ToLower()) {
                case "quit":
                    return;
                case "ls":
                    foreach(string elem in list) {
                        Console.WriteLine(elem);
                    }
                    break;
                case "cd":
                    if(opt.Length == 2)
                        collection = collection + '/' + opt[1];
                    else
                        collection = "/db";
                    list = readCollection(collection);
                    break;
                case "get":
                    if(opt.Length < 2) {
                        Console.WriteLine("Usage: get fileName");
                        break;
                    }
                    byte[] docData = qs.getResource(collection + '/' + opt[1],
                        "UTF-8", true);
                    string str = Encoding.UTF8.GetString(docData);
                    Console.WriteLine(str);
                    break;
                case "put":
                    if(opt.Length < 2) {
                        Console.WriteLine("ERROR: parse fileName");
                        break;
                    }
                    FileInfo info = new FileInfo(opt[1]);
                    if(!info.Exists) {
                        Console.WriteLine("ERROR: file does not exist");
                        break;
                    }
                    byte[] data = readFile(opt[1]);
                    Console.Write("storing document {0}/{1} ...",
                        collection, info.Name);
                    admin.store(data, "UTF-8", 
                        collection + '/' + info.Name, false);
                    Console.WriteLine("ok.");
                    break;
                case "rmcol":
                    if(opt.Length < 2) {
                        Console.WriteLine("Usage: rmcol collection");
                        break;
                    }
                    Console.Write("removing collection {0}/{1} ...",
                        collection, opt[1]);
                    admin.removeCollection(collection + '/' + opt[1]);
                    Console.WriteLine("ok.");
                    list = readCollection( collection );
                    break;
                case "mkcol":
                    if(opt.Length < 2) {
                        Console.WriteLine("Usage: mkcol collection");
                        break;
                    }
                    Console.Write("creating collection {0}/{1} ...",
                        collection, opt[1]);
                    admin.createCollection(collection + '/' + opt[1]);
                    Console.WriteLine("ok.");
                    list = readCollection( collection );
                    break;
                case "find":
                    if(opt.Length < 2) {
                        Console.WriteLine("Usage: find xpath-expression");
                        break;
                    }
                    Console.WriteLine("query: {0}", opt[1]);
                    qr = qs.query(opt[1]);
                    Console.WriteLine("found: {0} hits in {1} ms.", qr.hits, 
                        qr.queryTime);
                    pos = 0;
                    break;
                case "show":
                    if(qr == null) {
                        Console.WriteLine("no query result set!");
                        break;
                    }
                    if(opt.Length == 2)
                        pos = Convert.ToInt32(opt[1]);
                    else if(pos <= qr.hits)
                        ++pos;
                    else {
                        Console.WriteLine("no more hits.");
                        break;
                    }
                    Console.WriteLine("retrieving hit {0} of {1} ...", pos, qr.hits);
                    byte[] record = qs.retrieve(qr.resultSetId, pos, "UTF-8", true);
                    string xml = System.Text.Encoding.UTF8.GetString(record);
                    Console.WriteLine(xml);
                    break;
                default:
                    Console.WriteLine("Unknown command: {0}", cmd);
                    break;
            }
            Console.Write("exist:{0}> ", collection);
        }
        return;
        /*
        
        */
    }
    
    static string[] readCollection(string collection) {
        try {
            ArrayList list = new ArrayList();
            Collection coll = qs.listCollection( collection );
            if(coll.resources != null) {
                foreach(string res in coll.resources) {
                    list.Add(res);
                }
            }
            if(coll.collections != null) {
                foreach(string sub in coll.collections) {
                    list.Add(sub + '/');
                }
            }
            return (string[])list.ToArray(Type.GetType("System.String"));
        } catch(SoapException e) {
            Console.WriteLine("ERROR: {0}", e.Message);
            return null;
        }
    }
    
    static byte[] readFile(string fname) {
        StreamReader reader = 
            new StreamReader(fname, Encoding.GetEncoding(1252));
        reader.BaseStream.Seek(0, SeekOrigin.Begin);
        string data = reader.ReadToEnd();
        return Encoding.UTF8.GetBytes(data);
    }
}
