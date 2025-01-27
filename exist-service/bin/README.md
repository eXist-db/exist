# Note on Native Binaries

## Windows Binary
The Windows Binary is taken directly from the native binaries for Windows package provided by the Apache Commons Daemon project. For example:
```
wget https://dlcdn.apache.org/commons/daemon/binaries/windows/commons-daemon-1.4.1-bin-windows.zip
unzip commons-daemon-1.4.1-bin-windows.zip
```

## macOS Binary
The macOS Binary is compiled as a Universal Binary for x86_64 and arm64 from the native source code for unix provided by the Apache Commons Daemon project.
It is compiled for a minimum version of 10.13 of macOS so as to provide some backwards compatibility.

For example:

```bash
wget https://dlcdn.apache.org/commons/daemon/source/commons-daemon-1.4.1-native-src.tar.gz
tar zxvf commons-daemon-1.4.1-native-src.tar.gz

cd commons-daemon-1.4.1-native-src/unix
export CFLAGS="-mmacosx-version-min=10.13 -arch x86_64 -arch arm64"
export LDFLAGS="-mmacosx-version-min=10.13 -arch x86_64 -arch arm64"
./configure
make
```

## Linux Binary
The Linux binary is compiled for x86_64 from the native source code for unix provided by the Apache Commons Daemon project.
It is compiled for a minimum glibc of 2.17 so as to provide some backwards compatibility between Linux distributions.

CentOS 7 provides a glibc 2.17. If you have Docker, you can build it using the following example:

```bash
wget https://dlcdn.apache.org/commons/daemon/source/commons-daemon-1.4.1-native-src.tar.gz
tar zxvf commons-daemon-1.4.1-native-src.tar.gz

docker run -it -v ./commons-daemon-1.4.1-native-src/unix:/commons-daemon-1.4.1-native-src centos:7

sed -i 's/mirrorlist/#mirrorlist/g' /etc/yum.repos.d/CentOS-*
sed -i 's|#baseurl=http://mirror.centos.org|baseurl=http://vault.centos.org|g' /etc/yum.repos.d/CentOS-*

yum install -y gcc make libcap-devel java-1.8.0-openjdk-headless java-1.8.0-openjdk-devel

cd /commons-daemon-src
export CFLAGS=-m64
export LDFLAGS=-m64
export JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk
./configure
make
```

For building a linux binary on MacOs Mx processors run docker like

```
docker run -it --platform linux/amd64 -v ./commons-daemon-1.4.1-native-src/unix:/commons-daemon-1.4.1-native-src centos:7
```
