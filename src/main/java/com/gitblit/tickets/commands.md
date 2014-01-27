#### To review with Git

on a detached HEAD...

    git fetch ${repositoryUrl} ${patchsetRef} && git checkout FETCH_HEAD

on a new branch...

    git fetch ${repositoryUrl} ${patchsetRef} && git checkout -b ${reviewBranch} FETCH_HEAD

