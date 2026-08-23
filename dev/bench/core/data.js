window.BENCHMARK_DATA = {
  "lastUpdate": 1787464463571,
  "repoUrl": "https://github.com/eXist-db/exist",
  "entries": {
    "exist-core-jmh": [
      {
        "commit": {
          "author": {
            "name": "Duncan Paterson",
            "username": "duncdrum",
            "email": "duncdrum@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "a8afa8f2485c59748d851d798281cfa659a4e566",
          "message": "Merge pull request #6475 from joewiz/feat/external-var-default-override\n\n[bugfix] External variable: supplied value overrides declared default",
          "timestamp": "2026-08-19T08:41:10Z",
          "url": "https://github.com/eXist-db/exist/commit/a8afa8f2485c59748d851d798281cfa659a4e566"
        },
        "date": 1787131582907,
        "tool": "jmh",
        "benches": [
          {
            "name": "org.exist.storage.lock.LockTableBenchmark.testEvent",
            "value": 1065168734.3073314,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproach ( {\"numOfStrings\":\"1\"} )",
            "value": 83618536.08572893,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproach ( {\"numOfStrings\":\"2\"} )",
            "value": 19810646.900710892,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproach ( {\"numOfStrings\":\"5\"} )",
            "value": 7863549.028536839,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproach ( {\"numOfStrings\":\"10\"} )",
            "value": 4500979.166812321,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproach ( {\"numOfStrings\":\"100\"} )",
            "value": 500125.70624494273,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproach ( {\"numOfStrings\":\"1000\"} )",
            "value": 43945.56881541275,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproach ( {\"numOfStrings\":\"10000\"} )",
            "value": 2934.585584162208,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproachRadek ( {\"numOfStrings\":\"1\"} )",
            "value": 28775703.55119171,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproachRadek ( {\"numOfStrings\":\"2\"} )",
            "value": 19071769.04533099,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproachRadek ( {\"numOfStrings\":\"5\"} )",
            "value": 8268068.165748532,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproachRadek ( {\"numOfStrings\":\"10\"} )",
            "value": 4696260.961992966,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproachRadek ( {\"numOfStrings\":\"100\"} )",
            "value": 413024.64328224125,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproachRadek ( {\"numOfStrings\":\"1000\"} )",
            "value": 44395.12058567816,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproachRadek ( {\"numOfStrings\":\"10000\"} )",
            "value": 2929.835116865778,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproach ( {\"numOfStrings\":\"1\"} )",
            "value": 48875197.91127363,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproach ( {\"numOfStrings\":\"2\"} )",
            "value": 21522594.620970678,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproach ( {\"numOfStrings\":\"5\"} )",
            "value": 8564815.18871245,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproach ( {\"numOfStrings\":\"10\"} )",
            "value": 5148284.42977682,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproach ( {\"numOfStrings\":\"100\"} )",
            "value": 530412.7461067064,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproach ( {\"numOfStrings\":\"1000\"} )",
            "value": 48214.56524258972,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproach ( {\"numOfStrings\":\"10000\"} )",
            "value": 3349.668792727828,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproachRadek ( {\"numOfStrings\":\"1\"} )",
            "value": 20646603.265451927,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproachRadek ( {\"numOfStrings\":\"2\"} )",
            "value": 15839847.699891498,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproachRadek ( {\"numOfStrings\":\"5\"} )",
            "value": 8364501.873784078,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproachRadek ( {\"numOfStrings\":\"10\"} )",
            "value": 4424962.293019598,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproachRadek ( {\"numOfStrings\":\"100\"} )",
            "value": 414873.7246296477,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproachRadek ( {\"numOfStrings\":\"1000\"} )",
            "value": 44700.914452243655,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproachRadek ( {\"numOfStrings\":\"10000\"} )",
            "value": 2917.7410050194894,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.jdkApproach ( {\"numOfStrings\":\"1\"} )",
            "value": 49010839.44697729,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.jdkApproach ( {\"numOfStrings\":\"2\"} )",
            "value": 27087417.39913693,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.jdkApproach ( {\"numOfStrings\":\"5\"} )",
            "value": 13914027.505945971,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.jdkApproach ( {\"numOfStrings\":\"10\"} )",
            "value": 6999016.026482165,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.jdkApproach ( {\"numOfStrings\":\"100\"} )",
            "value": 819285.9879255244,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.jdkApproach ( {\"numOfStrings\":\"1000\"} )",
            "value": 74659.9100704921,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.jdkApproach ( {\"numOfStrings\":\"10000\"} )",
            "value": 3477.5650207450935,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.URIUtilsBenchmark.encodeForURI",
            "value": 6488550.050407021,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.following ( {\"shape\":\"1500_20\"} )",
            "value": 10.000982804113217,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.following ( {\"shape\":\"500_100\"} )",
            "value": 17.191556145592006,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.following ( {\"shape\":\"100_500\"} )",
            "value": 16.41108437076854,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.followingSibling ( {\"shape\":\"1500_20\"} )",
            "value": 175.57094552609195,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.followingSibling ( {\"shape\":\"500_100\"} )",
            "value": 1820.5664399333334,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.followingSibling ( {\"shape\":\"100_500\"} )",
            "value": 17501.6022706,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.preceding ( {\"shape\":\"1500_20\"} )",
            "value": 10.312040341209313,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.preceding ( {\"shape\":\"500_100\"} )",
            "value": 17.334765964111796,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.preceding ( {\"shape\":\"100_500\"} )",
            "value": 16.80047763988512,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.precedingSibling ( {\"shape\":\"1500_20\"} )",
            "value": 182.11693188439153,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.precedingSibling ( {\"shape\":\"500_100\"} )",
            "value": 1969.8124014666664,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.precedingSibling ( {\"shape\":\"100_500\"} )",
            "value": 17631.423721799998,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"1000\",\"matchEvery\":\"1\",\"queryVariant\":\"single\",\"verificationMode\":\"STRICT\"} )",
            "value": 38.631356049371696,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"1000\",\"matchEvery\":\"1\",\"queryVariant\":\"unionExplicit\",\"verificationMode\":\"STRICT\"} )",
            "value": 38.64157141471675,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"1000\",\"matchEvery\":\"1\",\"queryVariant\":\"unionCollectionParens\",\"verificationMode\":\"STRICT\"} )",
            "value": 39.2567791171875,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"1000\",\"matchEvery\":\"10\",\"queryVariant\":\"single\",\"verificationMode\":\"STRICT\"} )",
            "value": 12.703092441448309,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"1000\",\"matchEvery\":\"10\",\"queryVariant\":\"unionExplicit\",\"verificationMode\":\"STRICT\"} )",
            "value": 12.719557542275801,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"1000\",\"matchEvery\":\"10\",\"queryVariant\":\"unionCollectionParens\",\"verificationMode\":\"STRICT\"} )",
            "value": 13.318743866794327,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"5000\",\"matchEvery\":\"1\",\"queryVariant\":\"single\",\"verificationMode\":\"STRICT\"} )",
            "value": 696.99190615,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"5000\",\"matchEvery\":\"1\",\"queryVariant\":\"unionExplicit\",\"verificationMode\":\"STRICT\"} )",
            "value": 697.061407675,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"5000\",\"matchEvery\":\"1\",\"queryVariant\":\"unionCollectionParens\",\"verificationMode\":\"STRICT\"} )",
            "value": 726.8671656285715,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"5000\",\"matchEvery\":\"10\",\"queryVariant\":\"single\",\"verificationMode\":\"STRICT\"} )",
            "value": 122.12787622055748,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"5000\",\"matchEvery\":\"10\",\"queryVariant\":\"unionExplicit\",\"verificationMode\":\"STRICT\"} )",
            "value": 121.96750625969803,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"5000\",\"matchEvery\":\"10\",\"queryVariant\":\"unionCollectionParens\",\"verificationMode\":\"STRICT\"} )",
            "value": 123.27119893658536,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"64\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 36.144228970814176,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"64\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 26.64903422907825,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"64\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 166.35506924285716,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"64\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 118.11506344499999,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"128\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 35.83683482671389,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"128\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 24.205536226385064,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"128\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 173.2146951645238,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"128\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 115.14523528277778,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"256\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 33.2321824037096,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"256\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 23.117154752158367,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"256\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 146.44742307619046,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"256\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 122.5984923681746,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"512\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 36.550003389971224,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"512\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 23.843282962283013,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"512\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 168.12678876222225,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"512\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 116.255248755,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"1024\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 35.94275891931087,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"1024\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 31.837603630953204,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"1024\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 154.32858703888888,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"1024\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 121.12757945936508,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"64\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 35.38326367530406,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"64\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 25.893686797475304,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"64\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 154.07153100952382,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"64\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 117.34659745611111,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"128\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 28.135715206697192,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"128\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 24.625515503449094,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"128\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 173.2699348634921,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"128\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 119.84896424000002,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"256\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 39.827590631652484,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"256\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 26.028433721144786,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"256\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 150.43171331944444,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"256\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 129.28333699428575,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"64\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 10.984610185055931,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"64\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 5.6903549141083705,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"64\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 28.52338122031918,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"64\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 16.563110524811076,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"128\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 9.358379114996396,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"128\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 5.082131092974903,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"128\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 37.04892800196565,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"128\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 14.9922514421134,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"256\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 10.842037981526165,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"256\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 4.756350810762133,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"256\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 33.80408869333409,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"256\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 12.476294129101165,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"512\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 8.995423074788144,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"512\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 4.452219780546635,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"512\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 33.50081591800732,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"512\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 14.181061617403932,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"1024\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 9.992570638036845,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"1024\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 4.283069217423643,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"1024\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 30.59590277364166,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"1024\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 14.084835500719937,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"64\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 77.53508335715284,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"64\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 70.66149513847327,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"64\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 401.6652814333333,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"64\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 315.2304552833333,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"128\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 64.80235458844537,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"128\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 67.86033092812525,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"128\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 407.87496419999997,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"128\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 314.60095366666667,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"256\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 75.10779153186812,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"256\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 61.10465189421827,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"256\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 396.91675606666666,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"256\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 314.22052958333336,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"64\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 47.88813491127992,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"64\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 29.14452508989541,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"64\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 190.27523963571429,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"64\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 172.21827110476193,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"128\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 50.48731739268811,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"128\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 30.597865654363638,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"128\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 213.70295826952378,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"128\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 172.54912521904762,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"256\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 50.3692876457517,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"256\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 30.104656710925923,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"256\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 183.0698467285714,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"256\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 172.1426845847619,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"512\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 52.85115841741013,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"512\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 34.72212325117493,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"512\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 210.57818465238097,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"512\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 172.98418421238097,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"1024\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 49.650565056961234,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"1024\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 33.31880473071321,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"1024\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 157.87176884166666,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"1024\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 171.85709517428572,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.UtilExpandHighlightingBenchmark.expandBatchWildcardHighlightingOff",
            "value": 59.642734967703085,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.UtilExpandHighlightingBenchmark.expandBatchWildcardHighlightingOn",
            "value": 328.24660646,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.UtilExpandHighlightingBenchmark.expandSingleHitHighlightingOff",
            "value": 0.05303657904541752,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.UtilExpandHighlightingBenchmark.expandSingleHitHighlightingOn",
            "value": 0.15359321658371217,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.storage.ReindexBenchmark.reindex ( {\"docCount\":\"100\",\"verificationMode\":\"STRICT\"} )",
            "value": 12.581376726915765,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.storage.ReindexBenchmark.reindex ( {\"docCount\":\"500\",\"verificationMode\":\"STRICT\"} )",
            "value": 52.831751162894385,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.storage.ReindexBenchmark.reindex ( {\"docCount\":\"1000\",\"verificationMode\":\"STRICT\"} )",
            "value": 102.59879335989858,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.ArrowOperatorBenchmark.arrowChain ( {\"iterations\":\"100000\"} )",
            "value": 60.4797626,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.ArrowOperatorBenchmark.arrowSingleCall ( {\"iterations\":\"100000\"} )",
            "value": 38.467429203112154,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.ArrowOperatorBenchmark.directChain ( {\"iterations\":\"100000\"} )",
            "value": 61.51276316750979,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.ArrowOperatorBenchmark.directSingleCall ( {\"iterations\":\"100000\"} )",
            "value": 38.02891088787879,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.PrecedingAxisBenchmark.precedingSiblingBaseline ( {\"refPosition\":\"5000\"} )",
            "value": 133.03106760666665,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.PrecedingAxisBenchmark.precedingSiblingBaseline ( {\"refPosition\":\"25000\"} )",
            "value": 151.9684046,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.PrecedingAxisBenchmark.precedingSiblingBaseline ( {\"refPosition\":\"45000\"} )",
            "value": 177.61913140000001,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.PrecedingAxisBenchmark.wildcardPrecedingWithPositionalPredicate ( {\"refPosition\":\"5000\"} )",
            "value": 148.34077318571428,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.PrecedingAxisBenchmark.wildcardPrecedingWithPositionalPredicate ( {\"refPosition\":\"25000\"} )",
            "value": 236.05122799999998,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.PrecedingAxisBenchmark.wildcardPrecedingWithPositionalPredicate ( {\"refPosition\":\"45000\"} )",
            "value": 332.5772498333333,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.value.TypeSubTypeOfBenchmark.subTypeOf ( {\"shape\":\"identical\"} )",
            "value": 0.6250184028839458,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.value.TypeSubTypeOfBenchmark.subTypeOf ( {\"shape\":\"directSuper\"} )",
            "value": 8.1843774151287,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.value.TypeSubTypeOfBenchmark.subTypeOf ( {\"shape\":\"deepSuper\"} )",
            "value": 19.573011798420534,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.value.TypeSubTypeOfBenchmark.subTypeOf ( {\"shape\":\"unionMember\"} )",
            "value": 3.1497745674142656,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.value.TypeSubTypeOfBenchmark.subTypeOf ( {\"shape\":\"unionSubtype\"} )",
            "value": 26.52579172661252,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.value.TypeSubTypeOfBenchmark.subTypeOf ( {\"shape\":\"notSubType\"} )",
            "value": 11.255873329338387,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      },
      {
        "commit": {
          "author": {
            "name": "Duncan Paterson",
            "username": "duncdrum",
            "email": "duncdrum@users.noreply.github.com"
          },
          "committer": {
            "name": "GitHub",
            "username": "web-flow",
            "email": "noreply@github.com"
          },
          "id": "966ad039233fe46effd26d9055a82f7b194f0db6",
          "message": "Merge pull request #6497 from joewiz/bugfix/xmldb-store-binary-xml-mime-npe\n\n[bugfix] xmldb:store: parse binary content stored under an XML mime type",
          "timestamp": "2026-08-20T17:04:29Z",
          "url": "https://github.com/eXist-db/exist/commit/966ad039233fe46effd26d9055a82f7b194f0db6"
        },
        "date": 1787464462071,
        "tool": "jmh",
        "benches": [
          {
            "name": "org.exist.storage.lock.LockTableBenchmark.testEvent",
            "value": 1163294334.9670506,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproach ( {\"numOfStrings\":\"1\"} )",
            "value": 65155972.13453704,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproach ( {\"numOfStrings\":\"2\"} )",
            "value": 30309923.76807927,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproach ( {\"numOfStrings\":\"5\"} )",
            "value": 12858219.61188021,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproach ( {\"numOfStrings\":\"10\"} )",
            "value": 7096393.979423416,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproach ( {\"numOfStrings\":\"100\"} )",
            "value": 459922.3475978257,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproach ( {\"numOfStrings\":\"1000\"} )",
            "value": 47890.55920925878,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproach ( {\"numOfStrings\":\"10000\"} )",
            "value": 5232.406595597871,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproachRadek ( {\"numOfStrings\":\"1\"} )",
            "value": 36771523.14050865,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproachRadek ( {\"numOfStrings\":\"2\"} )",
            "value": 27975621.891218714,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproachRadek ( {\"numOfStrings\":\"5\"} )",
            "value": 9806983.120278796,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproachRadek ( {\"numOfStrings\":\"10\"} )",
            "value": 5731057.770060909,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproachRadek ( {\"numOfStrings\":\"100\"} )",
            "value": 404923.14160943794,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproachRadek ( {\"numOfStrings\":\"1000\"} )",
            "value": 43477.482782219515,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forApproachRadek ( {\"numOfStrings\":\"10000\"} )",
            "value": 4594.003599733812,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproach ( {\"numOfStrings\":\"1\"} )",
            "value": 61778839.71603234,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproach ( {\"numOfStrings\":\"2\"} )",
            "value": 30198192.59007875,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproach ( {\"numOfStrings\":\"5\"} )",
            "value": 14058226.728778815,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproach ( {\"numOfStrings\":\"10\"} )",
            "value": 7490450.070796661,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproach ( {\"numOfStrings\":\"100\"} )",
            "value": 702518.5885528342,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproach ( {\"numOfStrings\":\"1000\"} )",
            "value": 47229.856778865375,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproach ( {\"numOfStrings\":\"10000\"} )",
            "value": 5178.828309515801,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproachRadek ( {\"numOfStrings\":\"1\"} )",
            "value": 35675055.94496248,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproachRadek ( {\"numOfStrings\":\"2\"} )",
            "value": 19673513.320090294,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproachRadek ( {\"numOfStrings\":\"5\"} )",
            "value": 9690542.781024013,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproachRadek ( {\"numOfStrings\":\"10\"} )",
            "value": 5229548.656011266,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproachRadek ( {\"numOfStrings\":\"100\"} )",
            "value": 373628.67472712614,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproachRadek ( {\"numOfStrings\":\"1000\"} )",
            "value": 42961.21149171862,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.forEachApproachRadek ( {\"numOfStrings\":\"10000\"} )",
            "value": 4596.174425714895,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.jdkApproach ( {\"numOfStrings\":\"1\"} )",
            "value": 60836790.67628435,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.jdkApproach ( {\"numOfStrings\":\"2\"} )",
            "value": 45606030.20633812,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.jdkApproach ( {\"numOfStrings\":\"5\"} )",
            "value": 23731927.120429195,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.jdkApproach ( {\"numOfStrings\":\"10\"} )",
            "value": 10804043.379442718,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.jdkApproach ( {\"numOfStrings\":\"100\"} )",
            "value": 1059242.2271055486,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.jdkApproach ( {\"numOfStrings\":\"1000\"} )",
            "value": 111402.92043997953,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.StringJoinBenchmark.jdkApproach ( {\"numOfStrings\":\"10000\"} )",
            "value": 7750.234273118728,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.utils.URIUtilsBenchmark.encodeForURI",
            "value": 8729113.50151061,
            "unit": "ops/s",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.following ( {\"shape\":\"1500_20\"} )",
            "value": 7.747458964923389,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.following ( {\"shape\":\"500_100\"} )",
            "value": 12.933912947239392,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.following ( {\"shape\":\"100_500\"} )",
            "value": 12.746172177946942,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.followingSibling ( {\"shape\":\"1500_20\"} )",
            "value": 147.22615788622866,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.followingSibling ( {\"shape\":\"500_100\"} )",
            "value": 1555.65522345,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.followingSibling ( {\"shape\":\"100_500\"} )",
            "value": 16451.9877424,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.preceding ( {\"shape\":\"1500_20\"} )",
            "value": 8.15495689424919,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.preceding ( {\"shape\":\"500_100\"} )",
            "value": 13.885297307157071,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.preceding ( {\"shape\":\"100_500\"} )",
            "value": 13.512269562709358,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.precedingSibling ( {\"shape\":\"1500_20\"} )",
            "value": 152.4609431094474,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.precedingSibling ( {\"shape\":\"500_100\"} )",
            "value": 1682.471744383333,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.dom.persistent.AxisBenchmark.precedingSibling ( {\"shape\":\"100_500\"} )",
            "value": 23922.708577799996,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"1000\",\"matchEvery\":\"1\",\"queryVariant\":\"single\",\"verificationMode\":\"STRICT\"} )",
            "value": 32.74624797877467,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"1000\",\"matchEvery\":\"1\",\"queryVariant\":\"unionExplicit\",\"verificationMode\":\"STRICT\"} )",
            "value": 32.406756788627504,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"1000\",\"matchEvery\":\"1\",\"queryVariant\":\"unionCollectionParens\",\"verificationMode\":\"STRICT\"} )",
            "value": 32.90196960320608,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"1000\",\"matchEvery\":\"10\",\"queryVariant\":\"single\",\"verificationMode\":\"STRICT\"} )",
            "value": 11.758191339057145,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"1000\",\"matchEvery\":\"10\",\"queryVariant\":\"unionExplicit\",\"verificationMode\":\"STRICT\"} )",
            "value": 11.723504644305342,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"1000\",\"matchEvery\":\"10\",\"queryVariant\":\"unionCollectionParens\",\"verificationMode\":\"STRICT\"} )",
            "value": 12.130626446475649,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"5000\",\"matchEvery\":\"1\",\"queryVariant\":\"single\",\"verificationMode\":\"STRICT\"} )",
            "value": 566.2692807488888,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"5000\",\"matchEvery\":\"1\",\"queryVariant\":\"unionExplicit\",\"verificationMode\":\"STRICT\"} )",
            "value": 553.10190936,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"5000\",\"matchEvery\":\"1\",\"queryVariant\":\"unionCollectionParens\",\"verificationMode\":\"STRICT\"} )",
            "value": 557.3373020711111,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"5000\",\"matchEvery\":\"10\",\"queryVariant\":\"single\",\"verificationMode\":\"STRICT\"} )",
            "value": 106.2469761001773,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"5000\",\"matchEvery\":\"10\",\"queryVariant\":\"unionExplicit\",\"verificationMode\":\"STRICT\"} )",
            "value": 118.0540079709856,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.LucenePhraseQueryBenchmark.phraseQuery ( {\"docCount\":\"5000\",\"matchEvery\":\"10\",\"queryVariant\":\"unionCollectionParens\",\"verificationMode\":\"STRICT\"} )",
            "value": 109.80232095064241,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"64\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 26.65251667438157,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"64\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 19.55752271647883,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"64\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 110.40959961030303,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"64\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 90.67316666090909,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"128\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 23.615152174795135,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"128\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 19.461844635797643,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"128\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 117.18607672285714,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"128\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 94.32793943030302,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"256\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 22.64514337234432,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"256\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 18.50007419970159,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"256\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 123.10212023785714,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"256\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 102.61735643409091,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"512\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 26.530421909084037,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"512\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 19.640025759046928,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"512\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 123.33788874,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"512\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 71.5384643701282,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"1024\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 24.56989674628627,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"1024\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 21.34129211457885,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"1024\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 128.95013656722222,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatches ( {\"batchSize\":\"1024\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 97.84137117454546,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"64\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 25.426536628614702,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"64\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 19.990571358614517,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"64\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 118.96848196603176,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"64\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 95.25227682474748,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"128\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 25.166102516540207,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"128\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 20.46911028656736,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"128\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 107.65242978,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"128\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 94.68975028545454,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"256\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 26.207059116096655,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"256\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 19.937212981292056,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"256\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 116.67819078603175,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesBooleanShouldSweep ( {\"batchSize\":\"256\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 99.42456185818182,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"64\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 7.180641250210104,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"64\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 3.9063371320979554,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"64\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 26.489085486469623,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"64\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 12.69437769488415,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"128\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 7.675393789484654,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"128\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 2.907451724759291,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"128\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 19.004806733632346,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"128\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 11.32405422608716,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"256\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 6.848971560502337,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"256\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 3.3813331386073897,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"256\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 23.929084823113897,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"256\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 9.891661050693084,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"512\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 7.674014455152163,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"512\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 3.1173070250681727,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"512\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 22.786965051359026,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"512\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 10.052105205866981,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"1024\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 6.316866886268362,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"1024\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 2.5119155930856487,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"1024\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 24.06424769867133,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesTermInSetSweep ( {\"batchSize\":\"1024\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 9.904880940011074,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"64\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 62.66226743390339,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"64\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 53.39502924809058,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"64\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 316.8017461,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"64\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 256.88831223,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"128\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 58.57564543458648,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"128\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 50.192134530326086,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"128\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 300.89502046666667,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"128\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 252.52950044,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"256\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 47.67208001362205,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"256\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 47.74046107435221,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"256\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 302.63622539999994,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deleteInBatchesWithNoopCheck ( {\"batchSize\":\"256\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 256.33515923000004,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"64\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 28.999899672818874,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"64\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 21.764639577154917,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"64\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 140.89559952682538,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"64\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 127.67166428055555,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"128\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 30.299882606964776,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"128\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 22.038557810074078,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"128\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 126.14062890920634,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"128\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 100.12277814181819,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"256\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 29.539050047421473,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"256\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 21.81269681557114,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"256\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 125.42438937785714,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"256\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 125.57407648055555,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"512\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 25.90008509394098,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"512\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 23.867136684156975,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"512\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 144.66070092388887,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"512\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 129.78830474682542,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"1024\",\"docCount\":\"1000\",\"nodeDocStride\":\"1\"} )",
            "value": 26.615361055126886,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"1024\",\"docCount\":\"1000\",\"nodeDocStride\":\"10\"} )",
            "value": 22.56904136574753,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"1024\",\"docCount\":\"5000\",\"nodeDocStride\":\"1\"} )",
            "value": 135.85645407777776,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.ReindexDeleteStrategyBenchmark.deletePerDoc ( {\"batchSize\":\"1024\",\"docCount\":\"5000\",\"nodeDocStride\":\"10\"} )",
            "value": 139.07067736666667,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.UtilExpandHighlightingBenchmark.expandBatchWildcardHighlightingOff",
            "value": 60.25824798970335,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.UtilExpandHighlightingBenchmark.expandBatchWildcardHighlightingOn",
            "value": 258.07316244,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.UtilExpandHighlightingBenchmark.expandSingleHitHighlightingOff",
            "value": 0.036210149774312736,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.indexing.lucene.UtilExpandHighlightingBenchmark.expandSingleHitHighlightingOn",
            "value": 0.10608424085008743,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.storage.ReindexBenchmark.reindex ( {\"docCount\":\"100\",\"verificationMode\":\"STRICT\"} )",
            "value": 16.193989739378708,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.storage.ReindexBenchmark.reindex ( {\"docCount\":\"500\",\"verificationMode\":\"STRICT\"} )",
            "value": 58.30642779065552,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.storage.ReindexBenchmark.reindex ( {\"docCount\":\"1000\",\"verificationMode\":\"STRICT\"} )",
            "value": 96.96268624406716,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.ArrowOperatorBenchmark.arrowChain ( {\"iterations\":\"100000\"} )",
            "value": 44.8573696456183,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.ArrowOperatorBenchmark.arrowSingleCall ( {\"iterations\":\"100000\"} )",
            "value": 25.55101749215894,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.ArrowOperatorBenchmark.directChain ( {\"iterations\":\"100000\"} )",
            "value": 43.89484570738368,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.ArrowOperatorBenchmark.directSingleCall ( {\"iterations\":\"100000\"} )",
            "value": 25.73806538022302,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.PrecedingAxisBenchmark.precedingSiblingBaseline ( {\"refPosition\":\"5000\"} )",
            "value": 113.62493445555556,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.PrecedingAxisBenchmark.precedingSiblingBaseline ( {\"refPosition\":\"25000\"} )",
            "value": 126.4177606625,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.PrecedingAxisBenchmark.precedingSiblingBaseline ( {\"refPosition\":\"45000\"} )",
            "value": 146.54476088571428,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.PrecedingAxisBenchmark.wildcardPrecedingWithPositionalPredicate ( {\"refPosition\":\"5000\"} )",
            "value": 133.55838599583333,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.PrecedingAxisBenchmark.wildcardPrecedingWithPositionalPredicate ( {\"refPosition\":\"25000\"} )",
            "value": 257.45816452500003,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.PrecedingAxisBenchmark.wildcardPrecedingWithPositionalPredicate ( {\"refPosition\":\"45000\"} )",
            "value": 366.68706213333337,
            "unit": "ms/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.value.TypeSubTypeOfBenchmark.subTypeOf ( {\"shape\":\"identical\"} )",
            "value": 0.5866132923886815,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.value.TypeSubTypeOfBenchmark.subTypeOf ( {\"shape\":\"directSuper\"} )",
            "value": 7.113012302671041,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.value.TypeSubTypeOfBenchmark.subTypeOf ( {\"shape\":\"deepSuper\"} )",
            "value": 16.595852883404223,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.value.TypeSubTypeOfBenchmark.subTypeOf ( {\"shape\":\"unionMember\"} )",
            "value": 2.8734650833734756,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.value.TypeSubTypeOfBenchmark.subTypeOf ( {\"shape\":\"unionSubtype\"} )",
            "value": 21.704958847808808,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          },
          {
            "name": "org.exist.xquery.value.TypeSubTypeOfBenchmark.subTypeOf ( {\"shape\":\"notSubType\"} )",
            "value": 9.520874114498696,
            "unit": "ns/op",
            "extra": "iterations: 5\nforks: 1\nthreads: 1"
          }
        ]
      }
    ]
  }
}