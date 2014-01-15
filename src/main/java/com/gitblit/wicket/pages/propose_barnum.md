    pt clone --gitblit ${url}
    cd ${repo}
    git checkout -b ticket/${number} ${integrationBranch}
    ...
    git commit
    pt push ${number}
