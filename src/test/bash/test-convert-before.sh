#!/bin/sh
#
# These functions are to be called before hook enabled
#

. ./common_functions.sh

create_good_branch() {
    prepare_repo
    git branch -D good_convert_branch
    make_good_commit
    git push origin HEAD:good_convert_branch
    assert_result 0 "create_good_branch"
    popd
}

create_bad_branch() {
    prepare_repo
    git branch -D bad_convert_branch
    make_bad_commit
    git push origin HEAD:bad_convert_branch
    assert_result 0 "create_bad_branch"
    popd
}

prepare_convert_files() {
    prepare_repo
    test -f initial_bad_file && git rm initial_bad_file
    test -f initial_good_file && git rm initial_good_file
    git commit -m "Removing old files"
    git push
    echo -e "Initial bad file\r\n" > initial_bad_file
    echo -e "Initial good file\n" > initial_good_file
    git add initial_bad_file
    git add initial_good_file
    git commit -m "Added initial good and bad files"
    git push
}

prepare_convert_files
create_good_branch
create_bad_branch
