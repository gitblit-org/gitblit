    git clone ${url}
    cd ${repo}
    git checkout -b ticket/${number} ${integrationBranch}
    ...
    git push origin HEAD:refs/for/${number}
