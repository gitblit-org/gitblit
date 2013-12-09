    git clone ${url}
    cd ${repo}
    git checkout -b ${reviewBranch} ${integrationBranch}
    ...
    git push origin HEAD:refs/for/${ticketId}
