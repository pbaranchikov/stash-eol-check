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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StopWatch;

import com.atlassian.stash.commit.CommitService;
import com.atlassian.stash.content.AbstractChangeCallback;
import com.atlassian.stash.content.Change;
import com.atlassian.stash.content.ChangesRequest;
import com.atlassian.stash.content.Changeset;
import com.atlassian.stash.content.DiffSegmentType;
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
import com.google.common.base.Stopwatch;

/**
 * Pre-receive hook and pull request check, enforcing the correct (Linux-like)
 * EOL style of the data, committed to stash/git.
 * @author Pavel Baranchikov
 */
public class EolCheckHook implements PreReceiveRepositoryHook, RepositoryMergeRequestCheck {

    private static final char EOL = '\n';
    private static final int BUFFER_SIZE = 1024;
    private static final String GIT_WHITESPACE_REFERENCE = "http://git-scm.com/book/en/v2/Customizing-Git-Git-Configuration#Formatting-and-Whitespace";

    private final CommitService commitService;
    private final GitCommandBuilderFactory builderFactory;
    private final MergeBaseResolver mergeBaseResolver;
    private final RealParentResolver realParentResolver;
    private final I18nService i18service;
    private final Logger log = LoggerFactory.getLogger(getClass());

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
        final Collection<String> wrongFiles = processMergedChanges(base, prFrom, excludeFiles,
                context.getSettings());
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
            Collection<Pattern> excludeFiles, Settings settings) {
        final Collection<String> changedFiles = getChangedPaths(prFrom.getRepository(),
                base.getId(), prFrom.getId(), excludeFiles);
        return checkForWrongEol(changedFiles, prFrom.getRepository(), base.getId(), prFrom.getId(),
                settings);
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
        printLn(response, "Please, take a look at " + GIT_WHITESPACE_REFERENCE
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
        final StopWatch stopwatch = new StopWatch("Processing changes hook");
        stopwatch.start("getting real parent");
        final String fromId = realParentResolver.getRealParent(context.getRepository(), refChange);
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
        final Collection<String> changedPaths = getChangedPaths(context.getRepository(), fromId,
                toId, excludeFiles);
        stopwatch.stop();
        stopwatch.start("performing main check");
        final Collection<String> result = checkForWrongEol(changedPaths, context.getRepository(),
                fromId, toId, context.getSettings());
        stopwatch.stop();
        if (log.isDebugEnabled()) {
            log.debug(stopwatch.prettyPrint());
        }
        return result;
    }

    private Collection<String> checkForWrongEol(Collection<String> changedPaths, Repository repo,
            String since, String to, Settings settings) {
        final boolean allowInheritedEol = Boolean.TRUE.equals(settings
                .getBoolean(Constants.SETTING_ALLOW_INHERITED_EOL));
        final Collection<String> wrongPaths = new HashSet<String>();
        for (String path : changedPaths) {
            final GitDiffBuilder builder = builderFactory.builder(repo).diff().rev(to).path(path);
            if (since != null) {
                builder.ancestor(since);
            }
            final AbstractEolHandler outputHandler = allowInheritedEol ? new AllowInheritedStyleEolHandler()
                    : new StrictEolHandler();
            builder.contextLines(outputHandler.getRequiredContext());
            final GitCommand<Boolean> cmd = builder.build(outputHandler);
            final Boolean result = cmd.call();
            if (!result) {
                wrongPaths.add(path);
            }
        }
        return wrongPaths;
    }

    private static Collection<Pattern> getExcludeFiles(Settings settings) {
        final String includeFiles = settings.getString(Constants.SETTING_EXCLUDED_FILES);
        if (includeFiles == null) {
            return Collections.emptyList();
        }
        final String[] patternStrings = includeFiles.split(Constants.PATTERNS_SEPARATOR);
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
                    break;
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
     * Output handler, looking for CR symbol. <br/>
     * Returns <code>true</code> if all the changes look Ok. <br/>
     * Class is statefull. It is to be used strictly for one and only one output
     * handling.
     */
    private abstract static class AbstractEolHandler implements CommandOutputHandler<Boolean> {

        private boolean allOk = true;
        private final Logger log = LoggerFactory.getLogger(getClass());

        @Override
        public void process(InputStream output) throws ProcessException {
            final Stopwatch streamRead = new Stopwatch();
            final Stopwatch work = new Stopwatch();
            try {
                boolean newLine = true;
                DiffSegmentType segmentType = DiffSegmentType.CONTEXT;
                final byte[] buffer = new byte[BUFFER_SIZE];
                // Just initial value for while cycle
                int readCount = 1;
                while (readCount >= 0) {
                    streamRead.start();
                    readCount = output.read(buffer);
                    streamRead.stop();
                    work.start();
                    for (int i = 0; i < readCount; i++) {
                        final byte nextChar = buffer[i];
                        final boolean nextCharIsNewline = nextChar == Constants.CR
                                || nextChar == Constants.LF;
                        if (newLine && !nextCharIsNewline) {
                            if (nextChar == '+') {
                                segmentType = DiffSegmentType.ADDED;
                            } else if (nextChar == '-') {
                                segmentType = DiffSegmentType.REMOVED;
                            } else {
                                segmentType = DiffSegmentType.CONTEXT;
                            }
                        }
                        // Catch only newline characters to reduce method calls
                        if (nextCharIsNewline && !process(segmentType, nextChar)) {
                            break;
                        }
                        newLine = nextCharIsNewline;
                    }
                    work.stop();
                }
            } catch (IOException e) {
                throw new ProcessException("Error reading data from diff file", e);
            }
            log.debug("Stream read time {}", streamRead);
            log.debug("Stream processing time {}", work);
        }

        /**
         * Processes the next char according to the specified segment type.
         * Method is only called for newline characters
         * @param segmentType segment type this char belongs to
         * @param nextChar next character
         * @return whether the further processing should be performed
         */
        protected abstract boolean process(DiffSegmentType segmentType, byte nextChar);

        protected void setResult(boolean allOk) {
            this.allOk = allOk;
        }

        protected boolean getResult() {
            return allOk;
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

        /**
         * Returns number of context lines, that are required for the handler to
         * perform analysis.
         * @return number of context lines to load within diff
         */
        public abstract int getRequiredContext();

    }

    /**
     * Strict Eol checker - forces that every file has only LF-style
     * end-of-line. <br/>
     * Handler just search for the first occurrence of the CR symbol in the
     * added lines. When it finds - it returns error result immediately
     */
    private static class StrictEolHandler extends AbstractEolHandler {
        @Override
        protected boolean process(DiffSegmentType segmentType, byte nextChar) {
            if (segmentType == DiffSegmentType.ADDED && nextChar == Constants.CR) {
                setResult(false);
                return false;
            }
            return true;
        }

        @Override
        public int getRequiredContext() {
            // No context is needed for this type of check.
            return 0;
        }
    }

    /**
     * Eol-checker, allowing the EOL-style, that file have before the commit. <br/>
     * Handler searches for CR in all the lines. If it finds CR in the removed
     * lines, handler returns non-error result immediately. In other case, if
     * there is no CR in ALL added lines, handler returns non-error result after
     * all the parsing. Otherwise, error result is returned.
     */
    private static class AllowInheritedStyleEolHandler extends AbstractEolHandler {
        @Override
        protected boolean process(DiffSegmentType segmentType, byte nextChar) {
            // We get info about old EOL-style from both context and removed
            // lines. Descriptive lines, that are generated by git itself are
            // always in LF style
            if (segmentType != DiffSegmentType.ADDED && nextChar == Constants.CR) {
                setResult(true);
                return false;
            }
            if (getResult() && segmentType == DiffSegmentType.ADDED && nextChar == Constants.CR) {
                setResult(false);
            }
            return true;
        }

        @Override
        public int getRequiredContext() {
            // At lease one line of context is needed to determine file's
            // initial EOL-style
            return 1;
        }
    }

}
