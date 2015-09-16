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

GitHub pull requests or Gitblit Tickets are preferred.  Any contributions must be distributed under the terms of the [Apache Software Foundation license, version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

**Workflow**

Gitblit practices the [git-flow][1] branching model.

- **master** is the current stable release + fixes accumulated since release.
- **develop** is the integration branch for the next major release.
- **ticket/N** are feature or hotfix branches to be merged to **master** or **develop**, as appropriate.

**Feature Development**

Development of new features is mostly done using [Gitblit Tickets][2] hosted at [dev.gitblit.com][3].  This allows continuous dogfooding and improvement of Gitbit's own issue-tracker and pull-request mechanism.

**Release Planning**

Release planning is mostly done using Gitblit Milestones and Gitblit Tickets hosted at [dev.gitblit.com][3].

**Releasing**

When Gitblit is preparing for a release, a **release-{milestone}** branch will be created, tested, & fixed until it is ready to be merged to **master** and tagged as the next major release.  After the release is tagged, the **release-{milestone}** branch will also be merged back into **develop** and then the release branch will be removed.

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

[1]: http://nvie.com/posts/a-successful-git-branching-model
[2]: http://gitblit.com/tickets_overview.html
[3]: https://dev.gitblit.com
