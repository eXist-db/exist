YAHOO.util.Event.onDOMReady(function () {
    var logger = new YAHOO.tool.TestLogger();

    YAHOO.log(data1);
    
    var suite = new YAHOO.tool.TestSuite("JSON Suite");
    suite.add(testCase1);
    YAHOO.tool.TestRunner.add(suite);
    YAHOO.tool.TestRunner.run();
});

var Assert = YAHOO.util.Assert;

var testCase1 = new YAHOO.tool.TestCase({
    name: 'Simple values',

    testMultipleElements: function () {
        Assert.isObject(data1);
        Assert.isNotNull(data1.h1);
        Assert.areEqual("H", data1.h1);
        Assert.isNotNull(data1.h2);
        Assert.areEqual("HH", data1.h2);
        Assert.areEqual(2, data1.p.length);
        Assert.isNull(data1.p[0]);
        Assert.isString(data1.p[1]);
        Assert.areEqual("some text", data1.p[1]);
    },

    testAttributes: function () {
        Assert.isObject(data2);
        Assert.isNotNull(data2.a['@id']);
        Assert.areEqual('a1', data2.a['@id']);
        Assert.isNotNull(data2.b['@type']);
        Assert.areEqual('t', data2.b['@type']);
        Assert.isNotNull(data2.d['@id']);
        Assert.areEqual('d1', data2.d['@id']);
        Assert.isNotNull(data2.d.e);
        Assert.areEqual('text', data2.d.e);
    },

    testMixed: function () {
        Assert.isNotNull(data2.c['@id']);
        Assert.areEqual('c1', data2.c['@id']);
        Assert.isNotNull(data2.c['#text']);
        Assert.areEqual('text', data2.c['#text']);
        
        Assert.isObject(data3);
        Assert.areEqual('mixed', data3.p.b);
    },

    testSimpleValue: function () {
        Assert.isString(data4);
        Assert.areEqual('Single', data4);
    }
});