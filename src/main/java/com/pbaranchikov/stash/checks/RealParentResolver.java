package com.pbaranchikov.stash.checks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import javax.annotation.Nullable;

import com.atlassian.bitbucket.commit.Commit;
import com.atlassian.bitbucket.commit.CommitRequest;
import com.atlassian.bitbucket.commit.CommitService;
import com.atlassian.bitbucket.io.LineReader;
import com.atlassian.bitbucket.io.LineReaderOutputHandler;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.scm.CommandOutputHandler;
import com.atlassian.bitbucket.scm.git.command.GitCommandBuilderFactory;
import com.atlassian.bitbucket.scm.git.command.merge.GitMergeBaseBuilder;

/**
 * Helper class to resolve the real parent of the commit. Real parent is a
 * commit, which is the nearest parent to the specified commit and already
 * resides in the repository.
 * @author Pavel Baranchikov
 */
public class RealParentResolver {
    private final GitCommandBuilderFactory builderFactory;
    private final CommitService commitService;

    public RealParentResolver(GitCommandBuilderFactory builderFactory, CommitService commitService) {
        this.builderFactory = builderFactory;
        this.commitService = commitService;
    }

    /**
     * Returns real parent for the specified commit.Real parent is a commit,
     * which is the nearest parent to the specified commit and already resides
     * in the repository.
     * @param repository repository to search parent into
     * @param refChange commit to search existing parent for
     * @return nearest existing parent commit SHA1
     */
    @Nullable
    public String getRealParent(Repository repository, RefChange refChange) {
        if (!refChange.getFromHash().equals(Constants.NON_ID)) {
            return refChange.getFromHash();
        }
        final Collection<String> branches = getNearestBranches(repository, refChange);
        if (branches.isEmpty()) {
            return null;
        }
        final Commit branch = getCommitById(repository, branches.iterator().next());
        final Commit newChangeset = getCommitById(repository, refChange.getToHash());
        if (branch.equals(newChangeset)) {
            return branch.getId();
        }

        final GitMergeBaseBuilder builder = builderFactory.builder(repository).mergeBase()
                .between(branch.getId(), newChangeset.getId());
        final String sha = builder.build(new FirstLineOutputHandler()).call();
        return sha;
    }

    private Commit getCommitById(Repository repository, String commitId) {
        final CommitRequest.Builder builder = new CommitRequest.Builder(repository, commitId);
        final CommitRequest request = builder.build();
        return commitService.getCommit(request);
    }

    /**
     * Return branches, that have the latest common commit with the specified
     * head.
     * @param repository repository to look at
     * @param refChange head to look branches
     * @return collection of branch names
     */
    private Collection<String> getNearestBranches(Repository repository, RefChange refChange) {
        final Collection<String> revlist = builderFactory.builder(repository).revList()
                .rev(refChange.getToHash()).build(new MultilineReader()).call();
        for (String revision : revlist) {
            final Collection<String> branches = builderFactory.builder(repository)
                    .command("branch").argument("--contains").argument(revision)
                    .build(new BranchReader()).call();
            if (!branches.isEmpty()) {
                return branches;
            }
        }
        return Collections.emptyList();
    }

    /**
     * Callback, collecting stings from output.
     */
    private static class MultilineReader extends LineReaderOutputHandler implements
            CommandOutputHandler<Collection<String>> {

        private final Collection<String> parents = new ArrayList<String>();

        MultilineReader() {
            super(StandardCharsets.UTF_8);
        }

        @Override
        public Collection<String> getOutput() {
            return parents;
        }

        /**
         * Method saves the next read line of output.
         * @param line next line of output
         */
        protected void saveLine(String line) {
            parents.add(line);
        }

        @Override
        protected void processReader(LineReader reader) throws IOException {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                saveLine(line);
            }
        }

    }

    /**
     * Callback to read branch names. Branch listing has 2 chars prefix left to
     * the branch name.
     */
    private static class BranchReader extends MultilineReader {
        @Override
        protected void saveLine(String line) {
            super.saveLine(line.substring(2));
        }
    }

}
