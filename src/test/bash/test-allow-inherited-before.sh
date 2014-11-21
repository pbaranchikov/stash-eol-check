#!/bin/sh
#
# This test script is to be executed after the hook is enabled
#
. ./common_functions.sh

prepare_commits() {
    prepare_repo
    git rm initial_bad_file
    git rm initial_good_file
    git commit -am "Cleared branch"
    git push

    make_good_commit initial_good_file
    make_bad_commit initial_bad_file
    git push
    assert_result 0 "create_good2good_commit"
    popd
}

prepare_commits
