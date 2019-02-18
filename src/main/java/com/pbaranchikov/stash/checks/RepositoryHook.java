package com.pbaranchikov.stash.checks;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.TreeSet;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.springframework.util.StopWatch;

import com.atlassian.bitbucket.commit.CommitService;
import com.atlassian.bitbucket.content.ChangesRequest;
import com.atlassian.bitbucket.hook.repository.PreRepositoryHook;
import com.atlassian.bitbucket.hook.repository.PreRepositoryHookContext;
import com.atlassian.bitbucket.hook.repository.RepositoryHookRequest;
import com.atlassian.bitbucket.hook.repository.RepositoryHookResult;
import com.atlassian.bitbucket.repository.RefChange;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.scm.git.command.GitCommandBuilderFactory;
import com.atlassian.bitbucket.setting.Settings;

/**
 * Commit pre-receive hook to enforce EOL style in source files.
 */
public class RepositoryHook extends EolCheckHook implements
        PreRepositoryHook<RepositoryHookRequest> {

    private static final String GIT_WHITESPACE_REFERENCE = "http://git-scm.com/book/en/v2/Customizing-Git-Git-Configuration#Formatting-and-Whitespace";
    private static final char EOL = '\n';

    private final RealParentResolver realParentResolver;
    private final CommitService commitService;

    public RepositoryHook(@Nonnull GitCommandBuilderFactory builderFactory,
            @Nonnull RealParentResolver realParentResolver, @Nonnull CommitService commitService) {
        super(builderFactory);
        this.realParentResolver = Objects.requireNonNull(realParentResolver);
        this.commitService = Objects.requireNonNull(commitService);
    }

    @Nonnull
    @Override
    public RepositoryHookResult preUpdate(@Nonnull PreRepositoryHookContext context,
            @Nonnull RepositoryHookRequest request) {
        final Collection<Pattern> excludedFiles = getExcludeFiles(context.getSettings());
        final Collection<String> files = new TreeSet<>();
        for (RefChange refChange : request.getRefChanges()) {
            files.addAll(processChange(request.getRepository(), context.getSettings(), refChange,
                    excludedFiles));
        }
        if (files.isEmpty()) {
            return RepositoryHookResult.accepted();
        } else {
            return RepositoryHookResult.rejected("Wrong EOL found", printError(files));
        }
    }

    @Nonnull
    private static String printError(@Nonnull Collection<String> files) {
        final StringBuilder sb = new StringBuilder();
        printLn(sb, "The following files have wrong EOL-style:");
        // Collections.sort(paths);
        for (String path : files) {
            printLn(sb, "  " + path);
        }
        printLn(sb, "Please, take a look at " + GIT_WHITESPACE_REFERENCE + " for more information");
        return sb.toString();
    }

    private static void printLn(@Nonnull StringBuilder sb, @Nonnull String message) {
        sb.append(message);
        sb.append(EOL);
    }

    @Nonnull
    private Collection<String> processChange(@Nonnull Repository repository,
            @Nonnull Settings settings, @Nonnull RefChange refChange,
            @Nonnull Collection<Pattern> excludeFiles) {
        final StopWatch stopwatch = new StopWatch("Processing changes hook");
        stopwatch.start("getting real parent");
        final String fromId = realParentResolver.getRealParent(repository, refChange);
        stopwatch.stop();
        stopwatch.start("local comps");
        final String toId = refChange.getToHash();
        // If sha are equal, no new commits a passed, so nothing to check.
        // if toId = 00..00, the branch is being deleted. Nothing to check.
        if (toId.equals(fromId) || (toId.equals(Constants.NON_ID))) {
            return Collections.emptyList();
        }
        stopwatch.stop();
        stopwatch.start("getting changedPaths");
        final Collection<String> changedPaths =
                getChangedPaths(repository, fromId, toId, excludeFiles);
        stopwatch.stop();
        stopwatch.start("performing main check");
        final Collection<String> result =
                checkForWrongEol(changedPaths, repository, fromId, toId, settings);
        stopwatch.stop();
        if (getLog().isDebugEnabled()) {
            getLog().debug(stopwatch.prettyPrint());
        }
        return result;
    }

    @Nonnull
    private Collection<String> getChangedPaths(@Nonnull Repository repository, @Nullable String fromId, @Nonnull String toId,
            @Nonnull Collection<Pattern> excludeFiles) {
        final ChangesRequest.Builder builder = new ChangesRequest.Builder(repository, toId);
        if (fromId != null) {
            builder.sinceId(fromId);
        }
        final ChangesRequest pathsRequest = builder.build();
        final ChangesPathsCollector pathsCallback = new ChangesPathsCollector();
        commitService.streamChanges(pathsRequest, pathsCallback);
        final Collection<String> changedFiles = pathsCallback.getChangedPaths();
        filterFiles(changedFiles, excludeFiles);
        return changedFiles;
    }
}
