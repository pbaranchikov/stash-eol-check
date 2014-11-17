#!/bin/sh

REPO=/home/pavel/apps/stash-hooks/rep_1/add_file

assert_result() {
    local result=$?
    local expected=$1
    local message=$2
    if [ "$result" != "$1" ]; then
       echo "Error in test $message" >&2
       exit 1;
    fi
}

prepare_repo() {
    pushd $REPO
    git checkout master
    git reset --hard origin/master
    git config --local core.autocrlf false
}

make_good_commit() {
    local filename=good-file-`date +%F-%T.%N`.file
    echo -e "\nthis\nis\ngood\ncommit\n" > $filename
    git add $filename
    git commit -m "Added good file $filename"
}

make_bad_commit() {
    local filename=bad-file-`date +%F-%T.%N`.file
    echo -e "\nthis\r\nis\r\nbad\r\ncommit\r\n" > $filename
    unix2dos $filename
    git add $filename
    git commit -m "Added bad file $filename"
}

make_bad_archive() {
    local arcname=bad-archive-`date +%F-%N`.file
    local filename=`mktemp`
    echo -e "\r\n This should be in the archive\r\n" > $filename
    tar -cf "$arcname" $filename
    rm $filename
    git add "$arcname"
    git commit -m "Added bad archive $arcname"
}

make_good_archive() {
    local arcname=good-archive-`date +%F-%N`.file
    local filename=`mktemp`
    echo -e "\n This should be in the archive\n" > $filename
    tar -cf "$arcname" $filename
    rm $filename
    git add "$arcname"
    git commit -m "Added good archive $arcname"
}

test_bad_eol_commit() {
    prepare_repo
    make_bad_commit
    git push
    assert_result 1 "bad_eol_commit"
    popd
}

test_good_eol_commit() {
    prepare_repo
    make_good_commit
    git push
    assert_result 0 "good_eol_commit"
    popd
}

test_good_archive() {
    prepare_repo
    make_good_archive
    git push
    assert_result 0 "test_good_archive"
}

test_bad_archive() {
    prepare_repo
    make_bad_archive
    git push
    assert_result 0 "test_bad_archive"
}

test_new_clear_branch() {
    prepare_repo
    local branchname=branch$RANDOM
    git checkout -b $branchname
    git push origin $branchname
    assert_result 0 "new_clear_branch"
    popd
}

test_new_good_unclear_branch() {
    prepare_repo
    local branchname=branch$RANDOM
    git checkout -b $branchname
    make_good_commit
    git push origin $branchname
    assert_result 0 "new_good_unclear_branch"
    popd
}

test_new_bad_unclear_branch() {
    prepare_repo
    local branchname=branch$RANDOM
    git checkout -b $branchname
    make_bad_commit
    git push origin $branchname
    assert_result 1 "new_bad_unclear_branch"
    popd
}

test_new_mixed_unclear_branch() {
    prepare_repo
    local branchname=branch$RANDOM
    git checkout -b $branchname
    make_good_commit
    make_bad_commit
    make_bad_commit
    make_good_commit
    git push origin $branchname
    assert_result 1 "new_mixed_unclear_branch"
    popd
}


