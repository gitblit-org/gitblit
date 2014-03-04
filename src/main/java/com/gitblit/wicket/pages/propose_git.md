    git clone ${url}
    cd ${repo}
    git checkout -b ${reviewBranch} ${integrationBranch}
    ...
    git push --up-stream origin ${reviewBranch}

