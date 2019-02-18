package com.pbaranchikov.stash.checks;

import java.util.Collection;
import java.util.Objects;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import com.atlassian.bitbucket.hook.repository.PreRepositoryHookContext;
import com.atlassian.bitbucket.hook.repository.PullRequestMergeHookRequest;
import com.atlassian.bitbucket.hook.repository.RepositoryHookResult;
import com.atlassian.bitbucket.hook.repository.RepositoryMergeCheck;
import com.atlassian.bitbucket.i18n.I18nService;
import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestChangesRequest;
import com.atlassian.bitbucket.pull.PullRequestService;
import com.atlassian.bitbucket.scm.git.command.GitCommandBuilderFactory;
import com.atlassian.bitbucket.setting.Settings;

/**
 * Pull requests merge check to enforce EOL style in merge requests.
 */
public class MergeCheck extends EolCheckHook implements RepositoryMergeCheck {

    private final I18nService i18service;
    private final PullRequestService pullRequestService;

    public MergeCheck(@Nonnull GitCommandBuilderFactory builderFactory,
            @Nonnull I18nService i18service, @Nonnull PullRequestService pullRequestService) {
        super(builderFactory);
        this.pullRequestService = Objects.requireNonNull(pullRequestService);
        this.i18service = Objects.requireNonNull(i18service);
    }

    @Override
    @Nonnull
    public RepositoryHookResult preUpdate(@Nonnull PreRepositoryHookContext context,
            @Nonnull PullRequestMergeHookRequest pullRequestMergeHookRequest) {
        final PullRequest pr = pullRequestMergeHookRequest.getPullRequest();
        final Settings settings = context.getSettings();

        final Collection<Pattern> excludeFiles = getExcludeFiles(settings);
        final Collection<String> changedFiles = getChangedPaths(pr, excludeFiles);
        final Collection<String> wrongFiles = checkForWrongEol(changedFiles, pr, settings);
        if (wrongFiles.isEmpty()) {
            return RepositoryHookResult.accepted();
        } else {
            return RepositoryHookResult.rejected(
                    i18service.getText("stash.check.eol.wrong.eol.style.pr.check.summary",
                            "Wrong EOL-style used in the pull request"),
                    i18service.getText("stash.check.eol.wrong.eol.style.pr.check.detail",
                            "End-of-line style must be LF (Linux-style) on committing changes to Git: {0}",
                            String.join(", ", wrongFiles)));
        }
    }

    @Nonnull
    private Collection<String> getChangedPaths(PullRequest pullRequest,
            Collection<Pattern> excludeFiles) {
        final PullRequestChangesRequest request =
                new PullRequestChangesRequest.Builder(pullRequest).build();

        final ChangesPathsCollector pathsCallback = new ChangesPathsCollector();
        pullRequestService.streamChanges(request, pathsCallback);
        final Collection<String> changedFiles = pathsCallback.getChangedPaths();
        filterFiles(changedFiles, excludeFiles);
        return changedFiles;
    }

    @Nonnull
    private Collection<String> checkForWrongEol(Collection<String> changedPaths,
            PullRequest pullRequest, Settings settings) {
        return checkForWrongEol(changedPaths, pullRequest.getToRef().getRepository(),
                pullRequest.getToRef().getLatestCommit(),
                pullRequest.getFromRef().getLatestCommit(), settings);
    }
}
