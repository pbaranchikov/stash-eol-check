package it.com.pbaranchikov.stash.checks.utils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;

import com.atlassian.stash.hook.repository.RepositoryHookService;
import com.atlassian.stash.project.ProjectCreateRequest;
import com.atlassian.stash.project.ProjectService;
import com.atlassian.stash.repository.RepositoryCreateRequest;
import com.atlassian.stash.repository.RepositoryService;
import com.atlassian.stash.server.ApplicationPropertiesService;
import com.atlassian.stash.setting.Settings;
import com.atlassian.stash.user.EscalatedSecurityContext;
import com.atlassian.stash.user.SecurityService;
import com.atlassian.stash.user.StashUser;
import com.atlassian.stash.user.UserService;
import com.atlassian.stash.util.Operation;
import com.google.common.io.Files;
import com.pbaranchikov.stash.checks.Constants;

/**
 * Class provides various wrappers for interfaces, used by test cases.
 * @author Pavel Baranchikov
 */
public class WrappersFactory {
    private static final String STASH_USER = "admin";
    private static final String STASH_PASSWORD = STASH_USER;
    private static final String HOOK_KEY = "com.pbaranchikov.stash-eol-check:stash-check-eol-hook";
    private static final String GIT_BRANCH = "branch";
    private static final String GIT_TAG = "tag";
    private static final String GIT_PUSH = "push";
    private static final String GIT_ORIGIN = "origin";
    private static final String GIT_M = "-m";
    private static final String GIT = "git";
    private static final String DEL_PREFIX = ":";

    private static final String EOL = "\n";

    private final ProjectService projectService;
    private final RepositoryService repositoryService;
    private final RepositoryHookService repositoryHookService;
    // private final SecurityService securityService;
    private final EscalatedSecurityContext securityContext;

    private final URL stashUrl;

    public WrappersFactory(ProjectService projectService, RepositoryService repositoryService,
            RepositoryHookService repositoryHookService,
            ApplicationPropertiesService applicationPropertiesService,
            SecurityService securityService, UserService userService) {
        this.projectService = projectService;
        this.repositoryService = repositoryService;
        this.repositoryHookService = repositoryHookService;

        final StashUser adminUser = userService.getUserByName(STASH_USER);
        this.securityContext = securityService.impersonating(adminUser, "tests running");

        try {
            stashUrl = applicationPropertiesService.getBaseUrl().toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public Project createProject(String key, String name) {
        final ProjectCreateRequest request = new ProjectCreateRequest.Builder().key(key).name(name)
                .description("description for project " + name).build();
        final com.atlassian.stash.project.Project stashProject = securityContext
                .call(new Operation<com.atlassian.stash.project.Project, RuntimeException>() {
                    @Override
                    public com.atlassian.stash.project.Project perform() throws RuntimeException {
                        return projectService.create(request);
                    }
                });
        return new ProjectImpl(stashProject);

    }

    public void deleteProject(Project project) {
        final com.atlassian.stash.project.Project stashProject = projectService.getByKey(project
                .getKey());
        projectService.delete(stashProject);
    }

    protected static ExecutionResult executeGitCommand(File workspaceDir, String... parameters) {
        final Runtime rt = Runtime.getRuntime();
        final String[] cmdArray = new String[parameters.length + 1];
        System.arraycopy(parameters, 0, cmdArray, 1, parameters.length);
        cmdArray[0] = GIT;
        try {
            final Process pr = rt.exec(cmdArray, new String[] {}, workspaceDir);
            final int exitCode = pr.waitFor();
            final String output = IOUtils.toString(pr.getInputStream());
            final String err = IOUtils.toString(pr.getErrorStream());
            return new ExecutionResult(exitCode, output, err);
        } catch (Exception e) {
            throw new GitException("Error executing command " + Arrays.toString(cmdArray), e);

        }
    }

    /**
     * Project implementation via REST.
     */
    private class ProjectImpl implements Project {

        private final com.atlassian.stash.project.Project project;

        public ProjectImpl(com.atlassian.stash.project.Project project) {
            this.project = project;
        }

        @Override
        public Repository createRepository(String name) {
            final RepositoryCreateRequest request = new RepositoryCreateRequest.Builder()
                    .project(project).name(name).scmId(GIT).build();
            final Repository repository = securityContext
                    .call(new Operation<Repository, RuntimeException>() {
                        @Override
                        public Repository perform() throws RuntimeException {
                            return new WiredRepository(repositoryService.create(request));
                        }
                    });
            return repository;
        }

        @Override
        public String getKey() {
            return project.getKey();
        }

        @Override
        public void delete() {
            securityContext.call(new Operation<Void, RuntimeException>() {
                @Override
                public Void perform() throws RuntimeException {
                    projectService.delete(project);
                    return null;
                }
            });
        }

        @Override
        public void removeRepository(String key) {
            final com.atlassian.stash.repository.Repository repository = repositoryService
                    .getBySlug(project.getKey(), key);
            if (repository != null) {
                securityContext.call(new Operation<Void, RuntimeException>() {
                    @Override
                    public Void perform() throws RuntimeException {
                        repositoryService.delete(repository);
                        return null;
                    }
                });
            }
        }

        @Override
        public Repository forceCreateRepository(String key) {
            try {
                removeRepository(key);
            } catch (Exception e) {

            }
            return createRepository(key);
        }

    }

    /**
     * REST implementation of repository.
     */
    private class WiredRepository implements Repository {

        private final com.atlassian.stash.repository.Repository repository;

        public WiredRepository(com.atlassian.stash.repository.Repository repository) {
            this.repository = repository;
        }

        @Override
        public Workspace cloneRepository() {
            final File tempDir = Files.createTempDir();
            final ExecutionResult result = executeGitCommand(tempDir.getParentFile(), "clone",
                    getCloneUrl(), tempDir.getAbsolutePath());
            if (result.getExitCode() != 0) {
                throw new GitExecutionException(result);
            }
            return new WorkspaceImpl(tempDir);
        }

        @Override
        public String getCloneUrl() {
            return String.format("%s://%s:%s@%s:%d%s/scm/%s/%s", stashUrl.getProtocol(),
                    STASH_USER, STASH_PASSWORD, stashUrl.getHost(), stashUrl.getPort(),
                    stashUrl.getPath(), repository.getProject().getKey(), repository.getSlug());
        }

        @Override
        public void enableHook() {
            securityContext.call(new Operation<Void, RuntimeException>() {
                @Override
                public Void perform() throws RuntimeException {
                    repositoryHookService.enable(repository, HOOK_KEY);
                    return null;
                }
            });
        }

        @Override
        public void disableHook() {
            securityContext.call(new Operation<Void, RuntimeException>() {
                @Override
                public Void perform() throws RuntimeException {
                    repositoryHookService.disable(repository, HOOK_KEY);
                    return null;
                }
            });
        }

        @Override
        public void delete() {
            securityContext.call(new Operation<Void, RuntimeException>() {
                @Override
                public Void perform() throws RuntimeException {
                    repositoryService.delete(repository);
                    return null;
                }
            });
        }

        @Override
        public void setHookSettings(String excludedFiles, boolean allowInherited) {
            final Settings hookSettings = repositoryHookService.createSettingsBuilder()
                    .add(Constants.SETTING_EXCLUDED_FILES, excludedFiles)
                    .add(Constants.SETTING_ALLOW_INHERITED_EOL, allowInherited).build();
            securityContext.call(new Operation<Void, RuntimeException>() {
                @Override
                public Void perform() throws RuntimeException {
                    repositoryHookService.setSettings(repository, HOOK_KEY, hookSettings);
                    return null;
                }
            });
        }
    }

    /**
     * Implementation of workspace.
     */
    private class WorkspaceImpl implements Workspace {

        private final File workspaceDir;

        public WorkspaceImpl(File workspaceDir) {
            this.workspaceDir = workspaceDir;
        }

        private ExecutionResult executeGitCommandImpl(String... parameters) {
            return executeGitCommand(workspaceDir, parameters);
        }

        private void executeCommand(String... parameters) {
            final ExecutionResult result = executeGitCommandImpl(parameters);
            if (result.getExitCode() != 0) {
                throw new GitExecutionException(result);
            }
        }

        private void writeToFile(File file, String contents) {
            try {
                FileUtils.write(file, contents);
            } catch (IOException e) {
                throw new GitException("Error writing to file " + file, e);
            }
        }

        @Override
        public File commitNewFile(String filename, String contents) {
            final File newFile = new File(workspaceDir, filename);
            writeToFile(newFile, contents);
            add(newFile.getAbsolutePath());
            commit(filename + " added");
            return newFile;
        }

        @Override
        public void add(String filename) {
            executeCommand("add", filename);
        }

        public void commit(String message) {
            executeCommand("commit", GIT_M, message);
        }

        @Override
        public void checkout(String branchName) {
            executeCommand("checkout", branchName);
        }

        @Override
        public void branch(String branchName) {
            executeCommand(GIT_BRANCH, branchName);
        }

        private boolean isSucceeded(String... parameters) {
            final ExecutionResult result = executeGitCommandImpl(parameters);
            return result.getExitCode() == 0;
        }

        @Override
        public boolean push() {
            return isSucceeded(GIT_PUSH);
        }

        @Override
        public boolean push(String branchName) {
            return isSucceeded(GIT_PUSH, GIT_ORIGIN, branchName);
        }

        @Override
        public boolean pushForce(String targetBranch) {
            final ExecutionResult result = executeGitCommandImpl(GIT_PUSH, "--force", GIT_ORIGIN,
                    "HEAD:" + targetBranch);
            return result.getExitCode() == 0;
        }

        @Override
        public boolean pushRemoval(String branchName) {
            final ExecutionResult result = executeGitCommandImpl(GIT_PUSH, GIT_ORIGIN, DEL_PREFIX
                    + branchName);
            return result.getExitCode() == 0;
        }

        @Override
        public void setCrlf(String crlf) {
            config("core.autocrlf", crlf);
        }

        @Override
        public File getWorkDir() {
            return workspaceDir;
        }

        @Override
        public void config(String... parameters) {
            executeCommand((String[]) ArrayUtils.addAll(new String[] {"config", "--local"},
                    parameters));
        }

        @Override
        public void commitNewContents(File targetFile, String newContents) {
            writeToFile(targetFile, newContents);
            add(targetFile.getPath());
            commit("Changed contents of file " + targetFile.getPath());
        }

        @Override
        public void createTag(String tagName) {
            executeCommand(GIT_TAG, tagName);
        }

        @Override
        public void createTag(String tagName, String comment) {
            executeCommand(GIT_TAG, tagName, GIT_M, comment);
        }

    }

    /**
     * Class represents process execution result.
     */
    private static class ExecutionResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;

        public ExecutionResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        public int getExitCode() {
            return exitCode;
        }

        public String getStdout() {
            return stdout;
        }

        public String getStderr() {
            return stderr;
        }

    }

    /**
     * Exception to throw on git operations error.
     * @author Pavel Baranchikov
     */
    public static class GitExecutionException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public GitExecutionException(ExecutionResult result) {
            super("Error executing git command\n" + result.getStdout() + EOL + result.getStderr());
        }
    }

    /**
     * Exception to throw on git operations error.
     * @author Pavel Baranchikov
     */
    public static class GitException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public GitException(String commands, Throwable cause) {
            super(commands, cause);
        }
    }

}
