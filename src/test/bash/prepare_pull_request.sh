#!/bin/sh

. ./common_functions.sh

add_remote() {
    git remote add admin http://admin@localhost:7990/stash/scm/~admin/rep_1.git
    git fetch admin
}

create_good_pr() {
    prepare_repo
    git checkout -b good_pr
    make_good_commit
    git push admin good_pr
    popd
}

create_bad_pr() {
    prepare_repo
    git checkout -b bad_pr
    make_bad_commit
    git push admin bad_pr
    popd
}

#create_good_pr
create_bad_pr