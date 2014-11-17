package com.pbaranchikov.stash.checks;

import com.atlassian.stash.commit.CommitService;
import com.atlassian.stash.content.Changeset;
import com.atlassian.stash.scm.git.GitCommandBuilderFactory;
import com.atlassian.stash.scm.git.GitScmConfig;
import com.atlassian.stash.scm.git.merge.GitMergeBaseBuilder;

/**
 * Determines the merge base of a pair of commits.
 */
public class MergeBaseResolver {

    private final GitCommandBuilderFactory builderFactory;
    private final GitScmConfig gitScmConfig;
    private final CommitService commitService;

    public MergeBaseResolver(GitCommandBuilderFactory builderFactory, GitScmConfig gitScmConfig,
            CommitService commitService) {
        this.builderFactory = builderFactory;
        this.gitScmConfig = gitScmConfig;
        this.commitService = commitService;
    }

    public Changeset findMergeBase(Changeset a, Changeset b) {
        if (a.equals(b)) {
            return a;
        }
        final GitMergeBaseBuilder builder = builderFactory.builder(a.getRepository()).mergeBase()
                .between(a.getId(), b.getId());
        GitUtils.setAlternateIfCrossRepository(builder, a.getRepository(), b.getRepository(),
                gitScmConfig);

        final String sha = builder.build(new FirstLineOutputHandler()).call();
        if (sha == null) {
            return null;
        }

        return commitService.getChangeset(a.getRepository(), sha);
    }
}
