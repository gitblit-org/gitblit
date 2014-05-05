    git clone ${url}
    cd ${repo}
    git checkout -b ${reviewBranch} origin/${integrationBranch}
    ...
    git push -u origin ${reviewBranch}

