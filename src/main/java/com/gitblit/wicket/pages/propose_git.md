    git clone ${url}
    cd ${repo}
    git checkout -b ${reviewBranch} ${integrationBranch}
    ...
    git push --set-upstream origin ${reviewBranch}

