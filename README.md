<div align="center" id="logo">
<a href="https://exist-db.org/" target="_blank">
<img src="https://raw.githubusercontent.com/eXist-db/exist/develop/exist-jetty-config/src/main/resources/webapp/logo.jpg" alt="eXist Logo" width="333" height="132"></img>
</a>
</div>

## eXist-db Native XML Database

[![Build Status](https://github.com/eXist-db/exist/actions/workflows/ci-test.yml/badge.svg?branch=develop)](https://github.com/eXist-db/exist/actions/workflows/ci-build.yml)
[![Coverage Status](https://coveralls.io/repos/github/eXist-db/exist/badge.svg?branch=develop)](https://coveralls.io/github/eXist-db/exist?branch=develop)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/ae1c8a7eb1164e919b0ac3c8588560c6)](https://www.codacy.com/gh/eXist-db/exist/dashboard?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=eXist-db/exist&amp;utm_campaign=Badge_Grade)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=eXist-db_exist&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=eXist-db_exist)
[![Java 21](https://img.shields.io/badge/java-21-blue.svg)](https://adoptopenjdk.net/)
[![License](https://img.shields.io/badge/license-LGPL%202.1-blue.svg)](https://www.gnu.org/licenses/lgpl-2.1.html)
[![Download](https://img.shields.io/github/v/release/eXist-db/exist.svg)](https://github.com/eXist-db/exist/releases/)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.exist-db/exist/badge.svg)](https://search.maven.org/search?q=g:org.exist-db)
[![Slack](https://img.shields.io/badge/exist--db-slack-3e103f.svg)](https://exist-db.slack.com)
<a href="https://opencollective.com/existdb#backer">
		<img src="https://opencollective.com/existdb/backers/badge.svg">
	</a>

[![Code of Conduct](https://img.shields.io/badge/code%20of%20conduct-contributor%20covenant-%235e0d73.svg)](https://contributor-covenant.org/version/1/4/)

eXist-db is a high-performance open source native XML database—a NoSQL document database and application platform built entirely around XML technologies. The main homepage for eXist-db can be found at [exist-db.org](https://exist-db.org "eXist Homepage"). This is the GitHub repository of eXist source code, and this page links to resources for downloading, building, and contributing to eXist-db, below.

The eXist-db community has adopted the [Contributor Covenant](https://www.contributor-covenant.org/) [Code of Conduct](https://www.contributor-covenant.org/version/1/4/code-of-conduct).

## Open Community Calls
We hold an open Community Call each week on Monday, from 19:30-20:30 CET. The meetings are posted to this [public Google Calendar](https://calendar.google.com/calendar/u/0?cid=OHVnNmtwcnFnNWNvNmRwZGZxc2FrY283MWtAZ3JvdXAuY2FsZW5kYXIuZ29vZ2xlLmNvbQ). 

If you wish to participate, please join the #community channel on our Slack workspace (invitation link below). Pinned to that channel is a link to the upcoming meeting's agenda, which contains the link to the call, as well as a link to timeanddate.com to look up the time of the meeting for your local time zone. 

The notes of past Community Calls are located [here](https://drive.google.com/drive/folders/0B4NLNdpw86LPc2JsV294NDFfTjQ?resourcekey=0-NQPHfHbtiDuZULNDi06dbA&usp=sharing).

## Resources

-   **Homepage:** [https://exist-db.org](https://exist-db.org)
-   **Binaries:** [https://exist-db.org/exist/apps/homepage/index.html#download](https://exist-db.org/exist/apps/homepage/index.html#download)
-   **Documentation:** [https://exist-db.org/exist/apps/doc](https://exist-db.org/exist/apps/doc)
-   **Book:** [https://www.oreilly.com/library/view/exist/9781449337094/](https://www.oreilly.com/library/view/exist/9781449337094/)
-   **Packages:** [https://exist-db.org/exist/apps/public-repo](https://exist-db.org/exist/apps/public-repo)
-   **Source code:** [https://github.com/eXist-db/exist](https://github.com/eXist-db/exist)
-   **Git clone URL:** `git://github.com/eXist-db/exist.git`
-   **Mailing list:** [https://lists.sourceforge.net/lists/listinfo/exist-open](https://lists.sourceforge.net/lists/listinfo/exist-open)
-   **Slack:** [https://exist-db.slack.com](https://join.slack.com/t/exist-db/shared_invite/enQtNjQ4MzUyNTE4MDY3LWNkYjZjMmZkNWQ5MDBjODQ3OTljNjMyODkwNmY1MzQwNjUwZjMzZTY1MGJkMjY5NDFhOWZjMDZiMDdhMzY4NGY)

New developers may find the notes in [BUILD.md](https://github.com/eXist-db/exist/blob/develop/BUILD.md) and [CONTRIBUTING.md](https://github.com/eXist-db/exist/blob/develop/CONTRIBUTING.md) helpful to start using and sharing your work with the eXist community.

## Installation
### Prerequisites
Ensure you have the following installed on your system:
- **Java 11+** (e.g., OpenJDK or Oracle JDK)
- **Git** (optional, for cloning the repository)

### Installing eXist-db
#### Option 1: Download Binary Release
1. Go to the [eXist-db Releases](https://github.com/eXist-db/exist/releases) page.
2. Download the latest stable release.
3. Extract the archive and navigate to the installation folder.
4. Run the following command to start the server:
   ```sh
   ./bin/startup.sh # For macOS/Linux
   bin\startup.bat  # For Windows
   ```
5. Access the web-based interface at [http://localhost:8080/exist](http://localhost:8080/exist).

#### Option 2: Build from Source
1. Clone the repository:
   ```sh
   git clone https://github.com/eXist-db/exist.git
   cd exist
   ```
2. Build using Gradle:
   ```sh
   ./gradlew clean assemble
   ```
3. Run the server:
   ```sh
   ./build/install/exist/bin/startup.sh
   ```

## Usage
Once eXist-db is running, you can:
- Use the **Dashboard** to manage collections and run XQueries.
- Access the **REST API** at `http://localhost:8080/exist/rest/db`.
- Connect programmatically via **Java, Python, or HTTP requests**.

## User Guide
### Accessing the Dashboard
1. Open your web browser and go to [http://localhost:8080/exist](http://localhost:8080/exist).
2. Log in using the default credentials (admin:admin) or your configured user.
3. Navigate through the **Dashboard** to explore collections, run queries, and manage resources.

### Managing Collections
- Collections are directories that store XML documents.
- To create a new collection:
  1. Go to the **Dashboard**.
  2. Click on **Collections**.
  3. Click **New Collection**, enter a name, and save.
- To upload an XML file:
  1. Open a collection.
  2. Click **Upload File** and select your XML file.

### Running XQuery Queries
1. Open the **Query Editor** from the Dashboard.
2. Enter your XQuery, for example:
   ```xquery
   for $x in collection("/db/mycollection")/book
   where $x/author = "John Doe"
   return $x/title
   ```
3. Click **Run Query** to execute and view the results.

### Using the REST API
- Retrieve an XML document:
  ```sh
  curl -X GET http://localhost:8080/exist/rest/db/mycollection/myfile.xml
  ```
- Store a new XML document:
  ```sh
  curl -X PUT --data-binary @file.xml -H "Content-Type: application/xml" http://localhost:8080/exist/rest/db/mycollection/newfile.xml
  ```

## Credits

The eXist-db developers use the YourKit Java Profiler.

<img src="https://www.yourkit.com/images/yklogo.png" alt="YourKit Logo"/>

YourKit kindly supports open source projects with its full-featured Java Profiler.
YourKit, LLC is the creator of <a href="https://www.yourkit.com/java/profiler/">YourKit Java Profiler</a>
and <a href="https://www.yourkit.com/.net/profiler/">YourKit .NET Profiler</a>,
innovative and intelligent tools for profiling Java and .NET applications.

![sauce-labs_horiz_red-grey_rgb_200x28](https://user-images.githubusercontent.com/6205362/49570521-27bcc400-f937-11e8-9bfd-1a3ffc721d3d.png)

Cross-browser Testing Platform and Open Source <3 Provided by [Sauce Labs](https://saucelabs.com)
