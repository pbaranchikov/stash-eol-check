package com.pbaranchikov.stash.checks;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.regex.Pattern;

import com.atlassian.stash.commit.CommitService;
import com.atlassian.stash.content.AbstractChangeCallback;
import com.atlassian.stash.content.Change;
import com.atlassian.stash.content.ChangesRequest;
import com.atlassian.stash.content.Changeset;
import com.atlassian.stash.hook.HookResponse;
import com.atlassian.stash.hook.repository.PreReceiveRepositoryHook;
import com.atlassian.stash.hook.repository.RepositoryHookContext;
import com.atlassian.stash.hook.repository.RepositoryMergeRequestCheck;
import com.atlassian.stash.hook.repository.RepositoryMergeRequestCheckContext;
import com.atlassian.stash.i18n.I18nService;
import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.pull.PullRequestRef;
import com.atlassian.stash.repository.RefChange;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.scm.CommandOutputHandler;
import com.atlassian.stash.scm.git.GitCommand;
import com.atlassian.stash.scm.git.GitCommandBuilderFactory;
import com.atlassian.stash.scm.git.diff.GitDiffBuilder;
import com.atlassian.stash.scm.pull.MergeRequest;
import com.atlassian.stash.setting.Settings;
import com.atlassian.utils.process.ProcessException;
import com.atlassian.utils.process.Watchdog;

/**
 * Pre-receive hook and pull request check, enforcing the correct (Linux-like)
 * EOL style of the data, committed to stash/git.
 * @author Pavel Baranchikov
 */
public class EolCheckHook implements PreReceiveRepositoryHook, RepositoryMergeRequestCheck {

    private static final char EOL = '\n';
    private static final int CR = 0x0D;
    private static final String PATTERNS_SEPARATOR = ",";
    private static final String EXCLUDE_FILES_NAME = "excludeFiles";
    private static final String GIT_WHITESPACE_REFERENCE = "http://git-scm.com/book/en/v2/Customizing-Git-Git-Configuration#Formatting-and-Whitespace";

    private final CommitService commitService;
    private final GitCommandBuilderFactory builderFactory;
    private final MergeBaseResolver mergeBaseResolver;
    private final RealParentResolver realParentResolver;
    private final I18nService i18service;

    public EolCheckHook(CommitService commitService, GitCommandBuilderFactory builderFactory,
            MergeBaseResolver mergeBaseResolver, RealParentResolver realParentResolver,
            I18nService i18service) {
        this.commitService = commitService;
        this.builderFactory = builderFactory;
        this.mergeBaseResolver = mergeBaseResolver;
        this.realParentResolver = realParentResolver;
        this.i18service = i18service;
    }

    @Override
    public boolean onReceive(RepositoryHookContext context, Collection<RefChange> changes,
            HookResponse response) {
        final Collection<Pattern> excludedFiles = getExcludeFiles(context.getSettings());
        final Collection<String> files = new TreeSet<String>();
        for (RefChange refChange : changes) {
            files.addAll(processChange(context, refChange, excludedFiles));
        }
        if (files.isEmpty()) {
            return true;
        } else {
            printError(files, response);
            return false;
        }
    }

    @Override
    public void check(RepositoryMergeRequestCheckContext context) {
        final MergeRequest request = context.getMergeRequest();
        final PullRequest pr = request.getPullRequest();
        final Changeset prFrom = getChangeSet(pr.getFromRef());
        final Changeset prTo = getChangeSet(pr.getToRef());
        final Changeset base = mergeBaseResolver.findMergeBase(prFrom, prTo);
        final Collection<Pattern> excludeFiles = getExcludeFiles(context.getSettings());
        final Collection<String> wrongFiles = processMergedChanges(base, prFrom, excludeFiles);
        if (!wrongFiles.isEmpty()) {
            request.veto(i18service.getText("wrong.eol.style.check.error",
                    "Wrong EOL-style used in the pull request"), getPullRequestError(wrongFiles));
        }
    }

    private String getPullRequestError(Collection<String> wrongFiles) {
        final StringBuilder sb = new StringBuilder();
        sb.append(i18service.getText("wrong.eol.style.description",
                "End-of-line style must be LF (Linux-style) on committing changes to Git"));
        sb.append("\n");
        final Iterator<String> iter = wrongFiles.iterator();
        while (iter.hasNext()) {
            sb.append(iter.next());
            if (iter.hasNext()) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    private Collection<String> processMergedChanges(Changeset base, Changeset prFrom,
            Collection<Pattern> excludeFiles) {
        final Collection<String> changedFiles = getChangedPaths(prFrom.getRepository(),
                base.getId(), prFrom.getId(), excludeFiles);
        return checkForWrongEol(changedFiles, prFrom.getRepository(), base.getId(), prFrom.getId());
    }

    private Changeset getChangeSet(PullRequestRef prRef) {
        final Changeset changeSet = commitService.getChangeset(prRef.getRepository(),
                prRef.getLatestChangeset());
        return changeSet;
    }

    private static void printError(Collection<String> files, HookResponse response) {
        printLn(response, "The following files have wrong EOL-style:");
        // Collections.sort(paths);
        for (String path : files) {
            printLn(response, "  " + path);
        }
        printLn(response, "Please, take a loot at " + GIT_WHITESPACE_REFERENCE
                + " for more information");
    }

    private static void printLn(HookResponse response, String message) {
        response.out().write(message);
        response.out().write(EOL);
    }

    private Collection<String> getChangedPaths(Repository repository, String fromId, String toId,
            Collection<Pattern> excludeFiles) {
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

    private Collection<String> processChange(RepositoryHookContext context, RefChange refChange,
            Collection<Pattern> excludeFiles) {
        final String fromId = realParentResolver.getRealParent(context.getRepository(), refChange);
        final String toId = refChange.getToHash();
        // If sha are equal, no new commits a passed, so nothing to check.
        // if toId = 00..00, the branch is being deleted. Nothing to check.
        if (toId.equals(fromId) || (toId.equals(Constants.NON_ID))) {
            return Collections.emptyList();
        }
        final Collection<String> changedPaths = getChangedPaths(context.getRepository(), fromId,
                toId, excludeFiles);
        return checkForWrongEol(changedPaths, context.getRepository(), fromId, toId);
    }

    private Collection<String> checkForWrongEol(Collection<String> changedPaths, Repository repo,
            String since, String to) {
        final Collection<String> wrongPaths = new HashSet<String>();
        for (String path : changedPaths) {
            final GitDiffBuilder builder = builderFactory.builder(repo).diff().rev(to)
                    .contextLines(0).path(path);
            if (since != null) {
                builder.ancestor(since);
            }
            final GitCommand<Boolean> cmd = builder.build(new Handler());
            final Boolean result = cmd.call();
            if (!result) {
                wrongPaths.add(path);
            }
        }
        return wrongPaths;
    }

    private static Collection<Pattern> getExcludeFiles(Settings settings) {
        final String includeFiles = settings.getString(EXCLUDE_FILES_NAME);
        if (includeFiles == null) {
            return Collections.emptyList();
        }
        final String[] patternStrings = includeFiles.split(PATTERNS_SEPARATOR);
        final Collection<Pattern> patterns = new ArrayList<Pattern>(patternStrings.length);
        for (String patternString : patternStrings) {
            final Pattern pattern = Pattern.compile(patternString);
            patterns.add(pattern);
        }
        return patterns;
    }

    private Collection<String> filterFiles(Collection<String> files,
            Collection<Pattern> excludeFiles) {
        if (excludeFiles.isEmpty()) {
            return files;
        }
        final Iterator<String> iter = files.iterator();
        while (iter.hasNext()) {
            final String filename = iter.next();
            for (Pattern pattern : excludeFiles) {
                if (pattern.matcher(filename).matches()) {
                    iter.remove();
                }
            }
        }
        return files;
    }

    /**
     * Callback, collecting all the paths, changed in the requested change
     * range.
     */
    private static class ChangesPathsCollector extends AbstractChangeCallback {
        private final Collection<String> changedPaths = new HashSet<String>();

        @Override
        public boolean onChange(Change change) throws IOException {
            changedPaths.add(change.getPath().toString());
            return true;
        }

        public Collection<String> getChangedPaths() {
            return changedPaths;
        }

    }

    /**
     * Output handler, looking for CR symbol. The result command returns false,
     * if CR symbol is found.
     */
    private static class Handler implements CommandOutputHandler<Boolean> {
        private boolean allOk = true;

        @Override
        public void process(InputStream output) throws ProcessException {
            try {
                for (int nextChar = output.read(); nextChar >= 0; nextChar = output.read()) {
                    if (nextChar == CR) {
                        allOk = false;
                        break;
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Error reading data from diff file", e);
            }
        }

        @Override
        public void complete() throws ProcessException {
        }

        @Override
        public void setWatchdog(Watchdog watchdog) {
        }

        @Override
        public Boolean getOutput() {
            return allOk;
        }

    }

}
