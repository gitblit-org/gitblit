#### To review with Git

on a detached HEAD...

    git fetch ${repositoryUrl} ${ticketRef} && git checkout FETCH_HEAD

on a new branch...

    git fetch ${repositoryUrl} ${ticketRef} && git checkout -B ${reviewBranch} FETCH_HEAD


