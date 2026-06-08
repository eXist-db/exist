# Note on Native Binaries

## Windows Binary
The Windows Binary is taken directly from the native binaries for Windows package provided by the Apache Commons Daemon project. For example:
```
wget https://dlcdn.apache.org/commons/daemon/binaries/windows/commons-daemon-1.6.0-bin-windows.zip
unzip commons-daemon-1.6.0-bin-windows.zip
```

## macOS Binary
The macOS Binary is compiled as a Universal Binary for x86_64 and arm64 from the native source code for unix provided by the Apache Commons Daemon project.
It is compiled for a minimum version of 11.0 of macOS, matching the minimum required by Java 21.

For example:

```bash
wget https://dlcdn.apache.org/commons/daemon/source/commons-daemon-1.6.0-native-src.tar.gz
tar zxvf commons-daemon-1.6.0-native-src.tar.gz

cd commons-daemon-1.6.0-native-src/unix
export CFLAGS="-mmacosx-version-min=11.0 -arch x86_64 -arch arm64"
export LDFLAGS="-mmacosx-version-min=11.0 -arch x86_64 -arch arm64"
sh support/buildconf.sh
./configure
make
```

## Linux Binary
The Linux binary is compiled for x86_64 from the native source code for unix provided by the Apache Commons Daemon project.
It is compiled for a minimum glibc of 2.17 to provide some backwards compatibility between Linux distributions.

Rocky Linux 9 is the latest stable release, with glibc 2.34. Use the following Docker-based build:

```bash
wget https://dlcdn.apache.org/commons/daemon/source/commons-daemon-1.6.0-native-src.tar.gz
tar zxvf commons-daemon-1.6.0-native-src.tar.gz

docker run -it -v ./commons-daemon-1.6.0-native-src:/commons-daemon-1.6.0-native-src rocky:9

dnf install -y gcc make libcap-devel java-17-openjdk-headless java-17-openjdk-devel

cd /commons-daemon-1.6.0-native-src/unix
export CFLAGS="-m64 -std=gnu11"
export LDFLAGS="-m64 -std=gnu11"
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
sh support/buildconf.sh
./configure
make
```

Compilation directly under Linux as Fedora 44

```bash
wget https://dlcdn.apache.org/commons/daemon/source/commons-daemon-1.6.0-native-src.tar.gz
tar zxvf commons-daemon-1.6.0-native-src.tar.gz

cd /commons-daemon-1.6.0-native-src/unix
export CFLAGS="-m64 -std=gnu11"
export LDFLAGS="-m64 -std=gnu11"
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
sh support/buildconf.sh
./configure
make
```

For building a linux binary on MacOs Mx processors run docker like as follows and the rest as described for the Linux 
docker file.

```
docker run -it --platform linux/amd64 -v ./commons-daemon-1.6.0-native-src/unix:/commons-daemon-1.6.0-native-src rocky:9
```
