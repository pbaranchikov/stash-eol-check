package com.pbaranchikov.stash.checks;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import com.atlassian.stash.commit.CommitService;
import com.atlassian.stash.content.Changeset;
import com.atlassian.stash.io.LineReader;
import com.atlassian.stash.io.LineReaderOutputHandler;
import com.atlassian.stash.repository.RefChange;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.scm.CommandOutputHandler;
import com.atlassian.stash.scm.git.GitCommandBuilderFactory;
import com.atlassian.stash.scm.git.merge.GitMergeBaseBuilder;

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
    public String getRealParent(Repository repository, RefChange refChange) {
        if (!refChange.getFromHash().equals(Constants.NON_ID)) {
            return refChange.getFromHash();
        }
        final Collection<String> branches = getNearestBranches(repository, refChange);
        if (branches.isEmpty()) {
            return null;
        }
        final Changeset branch = commitService.getChangeset(repository, branches.iterator().next());
        final Changeset newChangeset = commitService.getChangeset(repository,
                getLastCommit(repository, refChange));
        if (branch.equals(newChangeset)) {
            return branch.getId();
        }

        final GitMergeBaseBuilder builder = builderFactory.builder(repository).mergeBase()
                .between(branch.getId(), newChangeset.getId());
        final String sha = builder.build(new FirstLineOutputHandler()).call();
        return sha;
    }

    private String getLastCommit(Repository repository, RefChange refChange) {
        final Collection<String> revlist = builderFactory.builder(repository).revList()
                .rev(refChange.getToHash()).limit(1).build(new MultilineReader()).call();
        if (revlist.size() != 1) {
            throw new IllegalStateException("Number of revisions is " + revlist.size());
        }
        return revlist.iterator().next();
    }

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

        public MultilineReader() {
            super("UTF-8");
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
