#### To review with Git

on a detached HEAD...

    git fetch ${repositoryUrl} ${patchRef} && git checkout FETCH_HEAD

on a new branch...

    git fetch ${repositoryUrl} ${patchRef} && git checkout -b ${reviewBranch} FETCH_HEAD

