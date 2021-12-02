# How to contribute

Hiii! It is lovely that you are reading this! Since Gitblit has been and still is a labour of love, it can use all the help it can get. It also means that, since this is Open Source and everyone is working on it in their limited free time, to make your contribution count, e.g. get your pull request merged, you should make it easy to review and include your contribution.
This usually works by spending some of your time and putting some effort into your contribution in order to save others the time, who have to review all the various contributions. It would be such a shame if you created a great feature or fixed a nasty bug but it is not getting included because the maintainers do not find the time to review and test your code because it is too much work.

Speaking of tests, this is certainly an area that is lacking and where help would be very appreciated. Gitblit is currently lacking test coverage, so if you would like to help to speed up development, you could add some unit tests to get more code covered by tests.

Maybe you found a bug. Then it is also great if you just open an issue in the Github issue tracker. But please make sure to include all necessary information that lets others understand what your problem is, what you encountered and actually expected, and (important!) how to reproduce it.

Maybe you found a bug and have already fixed it. Fantastic! Please make sure that you include tests in your pull request for this bug. These tests should demonstrate the bug, i.e. they should fail when run without your fix. They can then be used as regression tests to make sure that the bug does not come back.

The same is true if you added a feature and give us a pull request to get it included. Please make sure that you have covered your feature with tests. And also, update the documentation, or describe it in enough detail in your pull request so that we can update the documentation accordingly.


But wait, there are so many ways to contribute!

Maybe you see an existing issue, which you also have with Gitblit, but the existing issue is lacking details. You could triage issues, add steps how to reproduce a bug, provide more insight or even add a unit test which reproduces the bug. Also look out for issues with the label `bug triage`. These are ones we would like to fix but haven't reproduced the problem yet.

Or maybe you think that Gitblit's documentation could be improved. Well, yes, it could. You might have come across something which isn't covered in the documentation or could be clearer. Heck, there are even Github issues concerning documentation. Or you were trying to make sense of Gitblit's code and architecture and started documenting it for your understanding. Whatever it is, if it can help others, too, you could contribute it to the project, share with the community.

Or, browse through open issues and vote for the ones you would like to see fixed or implemeted because you could use it yourself, by leaving a thumbs up feedback.

Or, ....


## Pull requests

So, pull requests. Pull requests get reviewed and you can help to make this easy and faster. Which makes us happy and makes you happy. When you create a pull request, pease follow these basic rules:

Every pull request should be from a new, separate branch. Do not create multiple pull requests from the same branch. Do not create a pull request from your `master` branch. This makes it much, much harder to merge them. Why? Because you will keep adding new commits to your `master` branch which have nothing to do with the pull request. But the commits show up in the pull request.

That is why each pull request should have it's own branch, and each branch should have only one topic. That is to say each feature, fix, change set, should be on a separate branch. Maybe we totally love one of your features, but fo some reason will not add a different chnage from you. If both changes were on two branches and therefore two pull requests, the one is easy to merge. If everything is sitting on one branch and in one pull request, then we are left with the work of picking together the commits that we want to merge.

We follow a linear or semi-linear Git history. That means that your pull request should be based on the tip of our `master` (or whatever branch you choose as a target). If it isn't, chances are that we will have to rebase it onto our branch tip. Which takes time, which makes it take longer to merge ....

Tests, did I mention tests? Please remember to include a reasonable amount of test cases.



In addition to the above, if would be great if you could also keep the following in mind:


Provide enough comments in your pull request for others to understand what you changed any why. This helps with the review. Feel free to [reference any issue](https://docs.github.com/en/free-pro-team@latest/github/writing-on-github/autolinked-references-and-urls#issues-and-pull-requests) that is related.

Kindly keep your merge request in a mergeable state. If the checks run on pull requests fail, investigate why and fix it. If the main branch has moved on, it would be tops if you could rebase your changes branch and re-test, so that we don't have to do that.


## Commits

Your commits should be atomic, which is to say, put everything that belongs to that change into one commit, but only that and not more than that. The code should compile after each commit. Craft your commits so that they could be reverted individually.

You know that git allows you to clean up your commit history locally, before pushing, so that you can get rid of all the second, third and fourth try to get it right, and it looks like you wrote perfect code the first time around, do you? If you know how, feel free to clean up your branch before pushing it for a pull request.

We also love good commit messages! You may know why you did what you did in your commit, but the rest of the worl doesn't know about your genious, yet. So let us know the Why and What of your change. The How can be found in the code, but all the background is very valuable, too! Feel free to write it into the commit message, share your thoughts with us.

Remember that commit messages are like emails - with a subject line. Did you know that git commit messages are meant to be written like an email, with a subject line? It's true!

Well, others have already written about how to write good commit messages, so let's not repeat it here. If you'd like to know more, you can go and read [Chris' article](https://cbea.ms/git-commit/).


## Coding conventions

Gitblit's code has a bit of a mix of styles. It would be easier to digest for everyone and less time-consuming to review and understand, if it would follow a single, common code style. But at least please do not mix different styles in one file. So the first important rule is:

* Keep the style in existing files and use it for code that you add. Do not mix different code styles (braces, naming, casing) in one file.

If you create new files, it would be great if you could adhere to the following for Java code, to establish a common style going forward:

* Indentation is four spaces. No tabs.
* Sun Java code style
* A `if`, `while` etc. block with only one statement either uses braces around the statement, or it is all on one line.

This is open source software. Your code will be public and read by many others. Please consider the people who will read your code, and make it look nice and easy to follow for them.

Pleease, do never mix actual functional changes with reformatting or lines with just changed indentation or whitespace in one commit. This makes it very hard to figure out what the actual changes are. Which means it takes longer to review, which means merging the pull request is delayed, .... the lot. If something requires whitespace changes, they should be in their own commit. Mention that in the commit message.

Actually, [do not reformat entire files](https://github.com/rails/rails/pull/13771#issuecomment-32746700).

----

All in all, you should have fun and feel good contributing. So if the above is too much to ask, we would still like your contributions. But it will make it harder for us to include them and thus take a long time and make things slower. These rules are made so that the workload is shared among everyone, since developing software is not only the fun and exciting part but also includes the necessary more mundane tasks.
