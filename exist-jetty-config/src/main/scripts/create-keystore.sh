# generate new keypair for testing SSL connectivity on local development instances
rm exist-jetty-config/src/main/resources/org/exist/jetty/etc/keystore.p12
keytool -genkeypair -keystore exist-jetty-config/src/main/resources/org/exist/jetty/etc/keystore.p12 -storetype PKCS12 \
  -storepass $1 -alias existdb -keyalg RSA -keysize 4096 \
  -dname "CN=eXist-db Test SSL Certificate, OU=eXist-db developers, O=exist-db.org, L=Berlin, ST=Berlin, C=DE" \
  -ext san=dns:localhost,ip:127.0.0.1,ip:[::1] \
  -v -validity 1000