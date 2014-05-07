Gitblit
=================

Gitblit is an open source, pure Java Git solution for managing, viewing, and serving [Git](http://git-scm.com) repositories.
More information about Gitblit can be found [here](http://gitblit.com).

[ ![Download](https://api.bintray.com/packages/gitblit/releases/stable/images/download.png) ](https://bintray.com/gitblit/releases/stable/_latestVersion)

License
-------

Gitblit is distributed under the terms of the [Apache Software Foundation license, version 2.0](http://www.apache.org/licenses/LICENSE-2.0).
The text of the license is included in the file LICENSE in the root of the project.

Java Runtime Requirement
------------------------------------

Gitblit requires at Java 7 Runtime Environment (JRE) or a Java 7 Development Kit (JDK).

Getting help
------------

Read the online documentation available at the [Gitblit website](http://gitblit.com)
Issues, binaries, & sources @ [Google Code](http://code.google.com/p/gitblit)

Building Gitblit
----------------
[Eclipse](http://eclipse.org) is recommended for development as the project settings are preconfigured.

1. Import the gitblit project into your Eclipse workspace.
*There will be lots of build errors.*
2. Using Ant, execute the `build.xml` script in the project root.
*This will download all necessary build dependencies and will also generate the Keys class for accessing settings.*
3. Select your gitblit project root and **Refresh** the project, this should correct all build problems.
4. Using JUnit, execute the `com.gitblit.tests.GitBlitSuite` test suite.
*This will clone some repositories from the web and run through the unit tests.*
5. Execute the *com.gitblit.GitBlitServer* class to start Gitblit GO.

Building Tips & Tricks
----------------------
1. If you are running Ant from an ANSI-capable console, consider setting the `MX_COLOR` environment variable before executing Ant.<pre>set MX_COLOR=true</pre>
2. The build script will honor your Maven proxy settings.  If you need to fine-tune this, please review the [settings.moxie](http://gitblit.github.io/moxie/settings.html) documentation.