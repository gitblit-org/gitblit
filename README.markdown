A fork of [gitblit] to fix various test failures, and to improve the build infrastructure going forward.

TODO
----

* [x] Re-creation of the missing hello-world.git repo (used to be at [/git/hello-world.git]). The absence of this repo at GitHub causes a lot of unit test failures in the project.

* [x] Fix various test failures requiring the hello-world repo.

* [x] Zip all required repos used during testing, and check them in to the project, to avoid network fetch from GitHub during testing.

* [x] Re-organize the test classes in src/test according to test categories - the purpose is for ease of test execution and failure investigation:
  * src/test: for pure unit tests.
  * src/repoTest: for tests requiring some existing repos but no need for a gitblit server.
  * src/serverTest: for tests requiring the gitbit server to be running. The current GitBlitSuite class will go here, plus other test classes requiring a gitblit server.
  * src/uiTest: for tests of the gitblit UI.
  * src/extServiceTest: for tests requiring some external service to be available.

* [ ] The above test class re-org requires a new build tool for the project, as Moxie doesn't support it, hence the conversion to Gradle build tool:
  * less radical: initial conversion to Gradle keeping the src/main/java intact (i.e. no subprojects.)
  * more radical: re-organize the src/main/java into subprojects: core, war, go, federation, etc.; each subproject produces its own build artefact:
    * core: would produce the core gitblit.jar library
    * war: would produce gitblit.war which includes the above gitblit.jar
    * go: would produce gitblitGO.zip and/or gitblitGO.tar.gz
    * etc.

* [ ] upgrade to Java 8 as a minimum
  * upgrade selenium to at least version 3, for UI testing
  * replace [pegdown] (deprecated) library with [flexmark-java] library, to work with Java 8, when generating the docs.

[gitblit]: https://github.com/gitblit/gitblit
[pegdown]: https://github.com/sirthias/pegdown
[flexmark-java]: https://github.com/vsch/flexmark-java


----


Gitblit
=================

Gitblit is an open source, pure Java Git solution for managing, viewing, and serving [Git](http://git-scm.com) repositories.  It can serve repositories over the GIT, HTTP, and SSH transports; it can authenticate against multiple providers; and it allows you to get up-and-running with an attractive, capable Git server in less than 5 minutes.

More information about Gitblit can be found [here](http://gitblit.com).

<a href='https://bintray.com/gitblit/releases/gitblit/_latestVersion'><img src='https://api.bintray.com/packages/gitblit/releases/gitblit/images/download.png'></a>

License
-------

Gitblit is distributed under the terms of the [Apache Software Foundation license, version 2.0](http://www.apache.org/licenses/LICENSE-2.0).
The text of the license is included in the file LICENSE in the root of the project.

Java Runtime Requirement
------------------------------------

Gitblit requires at Java 7 Runtime Environment (JRE) or a Java 7 Development Kit (JDK).

Getting help
------------

| Source        | Location                                               |
| ------------- |--------------------------------------------------------|
| Documentation | [Gitblit website](http://gitblit.com)                  |
| Forums        | [Google Groups](https://groups.google.com/forum/#!forum/gitblit) |
| Twitter       | @gitblit or @jamesmoger                                |
| Google+       | +gitblit or +jamesmoger                                |

Contributing
------------

GitHub pull requests are preferred.  Any contributions must be distributed under the terms of the [Apache Software Foundation license, version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

Building Gitblit
----------------

Gitblit uses submodules.
Make sure to clone using `--recursive` OR to execute `git submodule update --init --recursive`.

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

