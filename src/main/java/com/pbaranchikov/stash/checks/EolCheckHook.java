package com.pbaranchikov.stash.checks;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.bitbucket.content.AbstractChangeCallback;
import com.atlassian.bitbucket.content.Change;
import com.atlassian.bitbucket.content.DiffSegmentType;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.scm.CommandOutputHandler;
import com.atlassian.bitbucket.scm.git.command.GitCommand;
import com.atlassian.bitbucket.scm.git.command.GitCommandBuilderFactory;
import com.atlassian.bitbucket.scm.git.command.diff.GitDiffBuilder;
import com.atlassian.bitbucket.setting.Settings;
import com.atlassian.utils.process.ProcessException;
import com.atlassian.utils.process.Watchdog;
import com.google.common.base.Stopwatch;

/**
 * Abstract class holding logic common for verifying EOL style in incoming source code.
 * @author Pavel Baranchikov
 */
public abstract class EolCheckHook {

    private static final int BUFFER_SIZE = 1024;

    private final GitCommandBuilderFactory builderFactory;

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final Supplier<AbstractEolHandler> strictHandlerCreator;
    private final Supplier<AbstractEolHandler> inheritedEolCreator;

    public EolCheckHook(@Nonnull GitCommandBuilderFactory builderFactory) {
        this.builderFactory = Objects.requireNonNull(builderFactory);
        this.strictHandlerCreator = StrictEolHandler::new;
        this.inheritedEolCreator = AllowInheritedStyleEolHandler::new;
    }

    @Nonnull
    protected Collection<String> checkForWrongEol(Collection<String> changedPaths, Repository repo,
            String since, String to, Settings settings) {
        final Collection<String> wrongPaths = new HashSet<String>();
        final boolean allowInheritedEol = Boolean.TRUE.equals(settings
                .getBoolean(Constants.SETTING_ALLOW_INHERITED_EOL));
        final Supplier<AbstractEolHandler> handlerCreator = allowInheritedEol ? inheritedEolCreator
                : strictHandlerCreator;
        for (String path : changedPaths) {
            final GitDiffBuilder builder = builderFactory.builder(repo).diff().rev(to).path(path);
            if (since != null) {
                builder.ancestor(since);
            }
            final AbstractEolHandler outputHandler = handlerCreator.get();
            builder.contextLines(outputHandler.getRequiredContext());
            final GitCommand<Boolean> cmd = builder.build(outputHandler);
            final Boolean result = cmd.call();
            if (!result) {
                wrongPaths.add(path);
            }
        }
        return wrongPaths;
    }

    @Nonnull
    protected static Collection<Pattern> getExcludeFiles(Settings settings) {
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

    protected Collection<String> filterFiles(Collection<String> files,
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

    @Nonnull
    protected Logger getLog() {
        return log;
    }

    /**
     * Callback, collecting all the paths, changed in the requested change
     * range.
     */
    protected static class ChangesPathsCollector extends AbstractChangeCallback {
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
            final Stopwatch streamRead = Stopwatch.createUnstarted();
            final Stopwatch work = Stopwatch.createUnstarted();
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
