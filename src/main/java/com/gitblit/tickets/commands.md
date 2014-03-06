#### To review with Git

To review an updated patchset

    git fetch && git checkout ${reviewBranch} && git pull --ff-only

To review a rewritten patchset

    git fetch && git checkout ${reviewBranch} && git reset --hard origin/${reviewBranch}


