    pt clone --gitblit ${url}
    cd ${repo}
    git checkout -b ticket/${number}
    ...
    git commit
    pt push ${number}
