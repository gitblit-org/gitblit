    pt clone --gitblit ${url}
    cd ${repo}
    git checkout -b ticket/${number} ${integrationBranch}
    ...
    pt push ${number}
