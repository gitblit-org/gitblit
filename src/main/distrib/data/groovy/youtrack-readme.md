# GitBlit YouTrack Receive Hook

GitBlit receive hook for updating referenced YouTrack issues.

This script has only been tested with the cloud hosted YouTrack instance.

## Usage

Due to limited authentication options when using the YouTrack REST API, you have to store a username and password for an account with appropriate permissions for adding comments to any issue. Hopefully in the future YouTrack will support API keys or similar.

1. Update your `gitblit.properties` file with the following entries:
    *   `groovy.customFields = "youtrackProjectID=YouTrack Project ID" ` *(or append to existing setting)*
    *   `youtrack.host = example.myjetbrains.com`
    *   `youtrack.user = ytUser`
    *   `youtrack.pass = insecurep@sswordsRus`

    (But using your own host and credential info).

2. Copy the `youtrack.groovy` script to the `<gitblit-data-dir>/groovy` scripts directory.
3. In GitBlit, go to a repository, click the *edit* button, then click the *receive* link. In the *post0receive scripts* section you should see `youtrack` as an option. Move it over to the *Selected* column.
4. At the bottom of this same screen should should be a *custom fields* section with a **YouTrack Project ID** field. Enter the YouTrack Project ID associated with the repository.
5. When you commit changes, reference YouTrack issues with `#{projectID}-{issueID}` where `{projectID}` is the YouTrack Project ID, and `{issueID}` is the issue number. For example, to references issue `34` in project `fizz`:

        git commit -m'Changed bazinator to fix issue #fizz-34.'

    Multiple issues may be referenced in the same commit message.

## Attribution

Much of this script was cobbled together from the example receive hooks in the official [GitBlit](https://github.com/gitblit/gitblit) distribution.
