#!/bin/sh
#
# This test script is to be executed after the hook is enabled
#
. ./common_functions.sh

convert_bad2good_commit() {
    prepare_repo
    dos2unix initial_bad_file
    git commit -am "converitng bad to good"
    git push
    assert_result 0 "convert bad2good"
    popd
}

convert_good2bad_commit() {
    prepare_repo
    unix2dos initial_good_file
    git commit -am "converitng good to bad"
    git push
    assert_result 1 "convert_good2bad_commit"
    popd
}

commit_bad_on_bad() {
    prepare_repo
    echo -e "\rqqq\rddd\r" >> initial_bad_file
    git commit -am "Committing bad 2 bad"
    git push
    assert_result 0 "commit_bad_on_bad"
}

commit_bad_on_bad
convert_good2bad_commit
convert_bad2good_commit
