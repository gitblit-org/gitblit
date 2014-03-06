/*
 * Copyright 2014 Berke Viktor.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import com.gitblit.GitBlit
import com.gitblit.Keys
import com.gitblit.models.RepositoryModel
import com.gitblit.models.UserModel
import com.gitblit.utils.JGitUtils
import org.eclipse.jgit.lib.Repository
import org.slf4j.Logger

/*
 * This script triggers automatic repo fetches in Redmine upon pushes.
 * It won't work out-of-box, you need to configure a few things.
 *
 * Redmine
 *  - Go to Administration / Settings / Repositories, and make sure that the
 *    "Enable WS for repository management" option is checked. Also generate an
 *    API key and take note of it.
 *  - Open a project page, go to Settings / Repositories and add a repo. Take
 *    note of the Identifier.
 *
 * Gitblit
 *  - Set the redmineProject custom field in gitblit.properties, e.g.
 *    groovy.customFields = "redmineProject=Redmine Project Identifier"
 *  - Edit the repo you added to Redmine, go to hook scripts and add this script
 *    to the post-receive hooks. Also specify the Redmine project's identifier
 *    under custom fields.
 *
 * Troubleshooting
 *  - run Gitblit interactively and check its console output
 *  - on the Redmine server, tail -f log/access.log
 *
 * If you want your repos to work with multiple Redmine projects, you don't need
 * to add the repos to all of them. Instead, add the repo to a single project,
 * then go to Administration / Settings / Repositories and enable the "Allow
 * issues of all the other projects to be referenced and fixed" option.
 */

/* specify the URL of your Redmine instance here */
def redmineURL = "http://redmine.foo.bar/"

/* specify the API key you generated in Redmine Administration here */
def apiKey = "FIXME"

/*
 * construct the URL from global and repo properties, for more info refer to
 * http://www.redmine.org/projects/redmine/wiki/RedmineSettings#Fetch-commits-automatically
 */
def triggerURL = redmineURL + "sys/fetch_changesets?id=" + repository.customFields.redmineProject + "&key=" + apiKey

/* log the action */
logger.info("Redmine Fetch hook triggered by ${user.username} for ${repository.name}: GET ${triggerURL}")

/* send the HTTP GET query */
new URL(triggerURL).getContent()
