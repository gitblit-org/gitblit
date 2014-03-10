    git clone ${url}
    cd ${repo}
    git checkout -b ${reviewBranch} origin/${integrationBranch}
    ...
    git push --set-upstream origin ${reviewBranch}

