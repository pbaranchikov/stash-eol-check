package it.com.pbaranchikov.stash.checks.utils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.springframework.beans.factory.DisposableBean;

import com.atlassian.bitbucket.hook.repository.DisableRepositoryHookRequest;
import com.atlassian.bitbucket.hook.repository.EnableRepositoryHookRequest;
import com.atlassian.bitbucket.hook.repository.RepositoryHookService;
import com.atlassian.bitbucket.hook.repository.SetRepositoryHookSettingsRequest;
import com.atlassian.bitbucket.permission.Permission;
import com.atlassian.bitbucket.permission.PermissionAdminService;
import com.atlassian.bitbucket.permission.SetPermissionRequest;
import com.atlassian.bitbucket.project.ProjectCreateRequest;
import com.atlassian.bitbucket.project.ProjectService;
import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.pull.PullRequestMergeability;
import com.atlassian.bitbucket.pull.PullRequestService;
import com.atlassian.bitbucket.repository.RepositoryCreateRequest;
import com.atlassian.bitbucket.repository.RepositoryForkRequest;
import com.atlassian.bitbucket.repository.RepositoryService;
import com.atlassian.bitbucket.repository.RepositoryUpdateRequest;
import com.atlassian.bitbucket.scope.RepositoryScope;
import com.atlassian.bitbucket.server.ApplicationPropertiesService;
import com.atlassian.bitbucket.setting.Settings;
import com.atlassian.bitbucket.user.ApplicationUser;
import com.atlassian.bitbucket.user.EscalatedSecurityContext;
import com.atlassian.bitbucket.user.SecurityService;
import com.atlassian.bitbucket.user.UserAdminService;
import com.atlassian.bitbucket.user.UserService;
import com.atlassian.bitbucket.util.Operation;
import com.google.common.io.Files;
import com.pbaranchikov.stash.checks.Constants;

/**
 * Class provides various wrappers for interfaces, used by test cases.
 * @author Pavel Baranchikov
 */
public class WrappersFactory implements DisposableBean {
    private static final String STASH_USER = "admin";
    private static final String STASH_PASSWORD = STASH_USER;
    private static final String HOOK_KEY = "com.pbaranchikov.stash-eol-check:stash-check-eol-hook";
    private static final String MERGE_CHECK_KEY =
            "com.pbaranchikov.stash-eol-check:stash-check-eol-merge-check";
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
    private final UserService userService;
    private final UserAdminService userAdminService;
    private final PermissionAdminService permissionAdminService;
    private final SecurityService securityService;

    /**
     * Regular user to perform operations.
     */
    private final StashUser simpleUser;
    /**
     * Admin user to perform privileged operations.
     */
    private final StashUser adminUser;
    private final StashUser ownerUser;

    private final PullRequestService pullRequestService;

    private final URL stashUrl;

    private final Collection<Repository> createdRepositories = new ArrayList<>();
    private final Collection<Project> createdProjects = new ArrayList<>();
    private final Collection<Workspace> createdWorkspaces = new ArrayList<>();

    private final Logger logger = Logger.getLogger(getClass());

    public WrappersFactory(ProjectService projectService, RepositoryService repositoryService,
            RepositoryHookService repositoryHookService,
            ApplicationPropertiesService applicationPropertiesService,
            SecurityService securityService, UserService userService,
            PullRequestService pullRequestService, UserAdminService userAdminService,
            PermissionAdminService permissionAdminService) {
        this.projectService = projectService;
        this.repositoryService = repositoryService;
        this.repositoryHookService = repositoryHookService;
        this.pullRequestService = pullRequestService;
        this.userService = userService;
        this.userAdminService = userAdminService;
        this.permissionAdminService = permissionAdminService;
        this.securityService = securityService;

        final ApplicationUser adminApplicationUser = userService.getUserByName(STASH_USER);
        final EscalatedSecurityContext securityContext = securityService.impersonating(
                adminApplicationUser, "tests running");
        adminUser = new StashUser(adminApplicationUser, STASH_PASSWORD, securityContext);

        this.simpleUser = createNewUser("SimpleUser");
        this.ownerUser = createNewUser("RepoOwner");

        try {
            stashUrl = applicationPropertiesService.getBaseUrl().toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Deletes all the created repositories, projects and workspaces.
     * @throws IOException on workspace delete errors
     */
    public void cleanup() throws IOException {
        for (Repository repository : createdRepositories) {
            repository.delete();
        }
        createdRepositories.clear();
        for (Project project : createdProjects) {
            project.delete();
        }
        createdProjects.clear();
        for (Workspace workspace : createdWorkspaces) {
            if (workspace.getWorkDir().exists()) {
                FileUtils.forceDelete(workspace.getWorkDir());
            }
        }
        createdWorkspaces.clear();
    }

    /**
     * Method creates new user with the suggested name. Method will try to
     * distinguish user name, that is not used, appending suggested user name
     * with suffixes
     * @param userName suggested user name
     * @return user structure
     */
    private StashUser createNewUser(final String userName) {
        final String password = "z";
        final String resultUserName = findFreeUserName(userName);
        adminUser.getSecurityContext().call(new Operation<Void, RuntimeException>() {
            @Override
            public Void perform() throws RuntimeException {
                userAdminService.createUser(resultUserName, password, resultUserName,
                        resultUserName + "@foo.bar");
                return null;
            }
        });
        final ApplicationUser user = userService.getUserByName(resultUserName);
        final EscalatedSecurityContext userContext = securityService.impersonating(user,
                "test operation as " + userName);

        return new StashUser(user, password, userContext);
    }

    private String findFreeUserName(final String userName) {
        boolean foundFreeUserName = false;
        int suffix = 0;
        String resultUserName = userName;
        while (!foundFreeUserName) {
            resultUserName = MessageFormat.format("{0}_{1}", userName, suffix++);
            foundFreeUserName = userService.getUserByName(resultUserName) == null;
        }
        return resultUserName;
    }

    /**
     * Method searches for a suffix, that we can add to the specified key to
     * ensure, that there is no projects already created with this key.
     * @param key requested key
     * @return suitable suffix
     * @throws IllegalStateException if suffix cannot be found
     */
    private String findSuitableSuffix(String key) {
        final List<String> existingProjects = adminUser.getSecurityContext()
                .call(new Operation<List<String>, RuntimeException>() {
                    @Override
                    public List<String> perform() throws RuntimeException {
                        return projectService.findAllKeys();
                    }
                });

        if (!existingProjects.contains(key)) {
            return "";
        }
        for (int i = 0; i < Integer.MAX_VALUE; i++) {
            final String suffix = "_" + i;
            final String supposedKey = key + suffix;
            if (!existingProjects.contains(supposedKey)) {
                return suffix;
            }
        }
        throw new IllegalStateException("Could not determine suitable suffix for project with key "
                + key + ". Please, restart Bitbucket server");
    }

    public Project createProject(String key, String name) {
        final String keySuffix = findSuitableSuffix(key);
        final ProjectCreateRequest request = new ProjectCreateRequest.Builder()
                .key(key + keySuffix).name(name + keySuffix)
                .description("description for project " + name).build();
        logger.debug("Creating project " + request.getKey());
        final com.atlassian.bitbucket.project.Project stashProject = adminUser.getSecurityContext()
                .call(new Operation<com.atlassian.bitbucket.project.Project, RuntimeException>() {
                    @Override
                    public com.atlassian.bitbucket.project.Project perform()
                            throws RuntimeException {
                        return projectService.create(request);
                    }
                });
        final SetPermissionRequest userPermissionsRequest = new SetPermissionRequest.Builder()
                .user(simpleUser.getApplicationUser())
                .projectPermission(Permission.PROJECT_READ, stashProject).build();
        final SetPermissionRequest ownerPermissionsRequest = new SetPermissionRequest.Builder()
                .user(ownerUser.getApplicationUser())
                .projectPermission(Permission.PROJECT_ADMIN, stashProject).build();
        adminUser.getSecurityContext().call(new Operation<Void, RuntimeException>() {
            @Override
            public Void perform() throws RuntimeException {
                permissionAdminService.setPermission(userPermissionsRequest);
                permissionAdminService.setPermission(ownerPermissionsRequest);
                return null;
            }
        });
        return new ProjectImpl(stashProject);
    }

    public void deleteProject(Project project) {
        final com.atlassian.bitbucket.project.Project stashProject = projectService
                .getByKey(project.getKey());
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

    @Override
    public void destroy() throws Exception {
        // Users are not changed across the tests, so we remove it once at the
        // very end.
        adminUser.getSecurityContext().call(new Operation<Void, RuntimeException>() {
            @Override
            public Void perform() throws RuntimeException {
                userAdminService.deleteUser(simpleUser.getUserName());
                userAdminService.deleteUser(ownerUser.getUserName());
                return null;
            }
        });
    }

    /**
     * Project implementation via REST.
     */
    private class ProjectImpl implements Project {

        private final com.atlassian.bitbucket.project.Project project;

        ProjectImpl(com.atlassian.bitbucket.project.Project project) {
            this.project = project;
            createdProjects.add(this);
        }

        @Override
        public Repository createRepository(String name) {
            logger.debug("Creating repository " + name + " in project " + project.getKey());
            final RepositoryCreateRequest request = new RepositoryCreateRequest.Builder()
                    .project(project).name(name).scmId(GIT).build();
            final Repository repository = ownerUser.getSecurityContext()
                    .call(new Operation<Repository, RuntimeException>() {
                        @Override
                        public Repository perform() throws RuntimeException {
                            return new WiredRepository(repositoryService.create(request), ownerUser);
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
            adminUser.getSecurityContext().call(new Operation<Void, RuntimeException>() {
                @Override
                public Void perform() throws RuntimeException {
                    projectService.delete(project);
                    return null;
                }
            });
        }

        @Override
        public void removeRepository(String key) {
            final com.atlassian.bitbucket.repository.Repository repository = repositoryService
                    .getBySlug(project.getKey(), key);
            if (repository != null) {
                ownerUser.getSecurityContext().call(new Operation<Void, RuntimeException>() {
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
            return createRepository(key);
        }

    }

    /**
     * REST implementation of repository.
     */
    private class WiredRepository implements Repository {

        private final com.atlassian.bitbucket.repository.Repository repository;
        private final StashUser user;

        WiredRepository(com.atlassian.bitbucket.repository.Repository repository, StashUser user) {
            this.repository = repository;
            this.user = user;
            createdRepositories.add(this);
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
                    user.getUserName(), user.getPassword(), stashUrl.getHost(), stashUrl.getPort(),
                    stashUrl.getPath(), repository.getProject().getKey(), repository.getSlug());
        }

        @Override
        public void enableHook() {
            user.getSecurityContext().call(new Operation<Void, RuntimeException>() {
                @Override
                public Void perform() throws RuntimeException {
                    repositoryHookService.enable(
                            new EnableRepositoryHookRequest.Builder(new RepositoryScope(repository),
                                    HOOK_KEY).build());
                    repositoryHookService.enable(
                            new EnableRepositoryHookRequest.Builder(new RepositoryScope(repository),
                                    MERGE_CHECK_KEY).build());
                    return null;
                }
            });
        }

        @Override
        public void disableHook() {
            user.getSecurityContext().call(new Operation<Void, RuntimeException>() {
                @Override
                public Void perform() throws RuntimeException {
                    repositoryHookService.disable(new DisableRepositoryHookRequest.Builder(
                            new RepositoryScope(repository), HOOK_KEY).build());
                    repositoryHookService.disable(new DisableRepositoryHookRequest.Builder(
                            new RepositoryScope(repository), MERGE_CHECK_KEY).build());
                    return null;
                }
            });
        }

        @Override
        public void delete() {
            user.getSecurityContext().call(new Operation<Void, RuntimeException>() {
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
            user.getSecurityContext().call(new Operation<Void, RuntimeException>() {
                @Override
                public Void perform() throws RuntimeException {
                    repositoryHookService.setSettings(new SetRepositoryHookSettingsRequest.Builder(
                            new RepositoryScope(repository), HOOK_KEY).settings(hookSettings)
                            .build());
                    repositoryHookService.setSettings(new SetRepositoryHookSettingsRequest.Builder(
                            new RepositoryScope(repository), MERGE_CHECK_KEY).settings(hookSettings)
                            .build());
                    return null;
                }
            });
        }

        @Override
        public Repository fork() {
            return simpleUser.getSecurityContext().call(new Operation<Repository, RuntimeException>() {
                @Override
                public Repository perform() throws RuntimeException {
                    final RepositoryForkRequest request = new RepositoryForkRequest.Builder().parent(
                            repository).build();
                    final com.atlassian.bitbucket.repository.Repository fork = repositoryService
                            .fork(request);
                    final RepositoryUpdateRequest updateRequest = new RepositoryUpdateRequest.Builder(
                            fork).publiclyAccessible(false).build();
                    repositoryService.update(updateRequest);
                    return new WiredRepository(fork, simpleUser);
                }
            });
        }

        @Override
        public boolean tryCreatePullRequest(final Repository targetRepository,
                final String branchName) {
            final WiredRepository targetWiredRepository = (WiredRepository) targetRepository;
            final PullRequest pullRequest = user.getSecurityContext().call(
                    new Operation<PullRequest, RuntimeException>() {
                        @Override
                        public PullRequest perform() throws RuntimeException {
                            return pullRequestService.create("pull-request-n",
                                    "New pull request from repo " + repository.getName(),
                                    Collections.emptySet(), repository, branchName,
                                    targetWiredRepository.repository, branchName);
                        }
                    });
            final boolean canMergeOwner = ownerUser.getSecurityContext().call(
                    new Operation<Boolean, RuntimeException>() {
                        @Override
                        public Boolean perform() throws RuntimeException {
                            final PullRequestMergeability mergeability = pullRequestService
                                    .canMerge(targetWiredRepository.repository.getId(),
                                            pullRequest.getId());
                            return mergeability.canMerge();
                        }
                    });
            final boolean canMergeAdmin = ownerUser.getSecurityContext().call(
                    new Operation<Boolean, RuntimeException>() {
                        @Override
                        public Boolean perform() throws RuntimeException {
                            final PullRequestMergeability mergeability = pullRequestService
                                    .canMerge(targetWiredRepository.repository.getId(),
                                            pullRequest.getId());
                            return mergeability.canMerge();
                        }
                    });
            Assert.assertEquals(canMergeOwner, canMergeAdmin);
            return canMergeOwner;
        }

        @Override
        public String toString() {
            return repository.toString();
        }
    }

    /**
     * Implementation of workspace.
     */
    private class WorkspaceImpl implements Workspace {

        private final File workspaceDir;

        WorkspaceImpl(File workspaceDir) {
            this.workspaceDir = workspaceDir;
            config("user.name", "TestUser");
            config("user.email", "test@gmail.com");
            createdWorkspaces.add(this);
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

        @Override
        public String toString() {
            return workspaceDir.toString();
        }
    }

    /**
     * Class represents process execution result.
     */
    private static class ExecutionResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;

        ExecutionResult(int exitCode, String stdout, String stderr) {
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

    /**
     * Class represents Stash user.
     */
    private static class StashUser {
        private final String password;
        private final ApplicationUser applicationUser;
        private final EscalatedSecurityContext context;

        StashUser(ApplicationUser applicationUser, String password, EscalatedSecurityContext context) {
            this.applicationUser = applicationUser;
            this.password = password;
            this.context = context;
        }

        public String getUserName() {
            return applicationUser.getName();
        }

        public String getPassword() {
            return password;
        }

        public ApplicationUser getApplicationUser() {
            return applicationUser;
        }

        public EscalatedSecurityContext getSecurityContext() {
            return context;
        }

        @Override
        public String toString() {
            return applicationUser.toString();
        }
    }

}
