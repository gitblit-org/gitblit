#### To review with Barnum

on a detached HEAD...

    pt refresh && pt checkout ${patchId}

on a new branch...

    pt refresh && pt checkout ${patchId} -b ${reviewBranch}

#### To review with Git

on a detached HEAD...

    git fetch ${repositoryUrl} ${patchRef} && git checkout FETCH_HEAD

on a new branch...

    git fetch ${repositoryUrl} ${patchRef} && git checkout -b ${reviewBranch} FETCH_HEAD

