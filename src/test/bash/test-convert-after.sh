#!/bin/sh
#
# This test script is to be executed after the hook is enabled
#
. ./common_functions.sh

good_branch=good_convert_branch
bad_branch=bad_convert_branch

checkout_branch() {
    local branchname=$1
    git checkout $branchname
    git reset --hard origin/$branchname
}

create_good2good_commit() {
    prepare_repo
    checkout_branch $good_branch
    make_good_commit
    git push origin $good_branch
    assert_result 0 "create_good2good_commit"
    popd
}

create_good2bad_commit() {
    prepare_repo
    checkout_branch $bad_branch
    make_good_commit
    git push origin $bad_branch
    assert_result 0 "create_good2bad_commit"
    popd
}

create_bad2good_commit() {
    prepare_repo
    checkout_branch $good_branch
    make_bad_commit
    git push origin $good_branch
    assert_result 1 "create_bad2good_commit"
    popd
}

create_bad2bad_commit() {
    prepare_repo
    checkout_branch $bad_branch
    make_bad_commit
    git push origin $bad_branch
    assert_result 1 "create_bad2bad_commit"
    popd
}

create_good2bad_convert() {
    prepare_repo
    unix2dos initial_good_file;
    git add initial_good_file;
    git commit -m "converting good2bad"
    git push
    assert_result 1 "create_good2bad_convert"
    popd
}

create_bad2good_convert() {
    prepare_repo
    dos2unix initial_bad_file;
    git add initial_bad_file;
    git commit -m "converting bad2good"
    git push
    assert_result 0 "create_bad2good_convert"
    popd
}

create_good2good_commit
create_good2bad_commit
create_bad2good_commit
create_bad2bad_commit
create_good2bad_convert
create_bad2good_convert
