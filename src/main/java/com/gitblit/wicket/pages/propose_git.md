    git clone ${url}
    cd ${repo}
    git checkout -b ticket/${number} ${integrationBranch}
    ...
    git commit
    git push origin HEAD:refs/tickets/${number}
