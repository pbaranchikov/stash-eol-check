package ut.com.pbaranchikov.stash.checks;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.After;
import org.junit.Before;

import ut.com.pbaranchikov.stash.checks.utils.Project;
import ut.com.pbaranchikov.stash.checks.utils.Repository;
import ut.com.pbaranchikov.stash.checks.utils.Workspace;

import com.google.common.io.Files;
import com.pbaranchikov.stash.checks.Constants;

/**
 * Abstract suitcase for git work copies.<br/>
 * This code is rather dirty and should be rewritten since Atlassian fix several
 * issues with integration tests.
 * @see STASH-5523
 * @see STASH-4616
 * @author Pavel Baranchikov
 */
public abstract class AbstractGitCheck {

    protected static final String WRONG_CONTENTS = "This\r\nis file with wrong contents\r\n";
    protected static final String GOOD_CONTENTS = "This\nis file with wrong contents\n";
    protected static final String GOOD_FILE = "goodFile";
    protected static final String BAD_FILE = "badFile";

    private static final String STASH_HOST = "localhost:7990/stash";
    private static final String STASH_URL = "http://" + STASH_HOST;

    private static final String STASH_REST_URL = STASH_URL + "/rest/api/1.0";
    private static final String REST_PROJECTS = "/projects";
    private static final String REST_REPOSITORIES = "/repos";
    private static final String REST_DELIMITER = "/";
    private static final String BLOCK_QUOTE = "\"";
    private static final String NAME = "name";
    private static final String KEY = "key";
    private static final String STASH_USER = "admin";
    private static final String STASH_PASSWORD = STASH_USER;
    private static final String HOOK_KEY = "com.pbaranchikov.stash-eol-check:check-eol-hook";

    private static final String PROJECT_KEY = "PROJECT_FOR_TESTS";
    private static final String REPOSITORY_KEY = "TEST_REPOSITORY";

    private static final String RANDOM_FILE = "randomFile";

    private static final String GIT_BRANCH = "branch";
    private static final String GIT_PUSH = "push";
    private static final String GIT_ORIGIN = "origin";
    private static final String GIT = "git";

    private static final String EOL = "\n";

    private final HttpClient httpClient;
    private final HttpClientContext httpContext;

    private Repository repository;
    private Project project;
    private Workspace workspace;
    private int fileCounter;

    protected AbstractGitCheck() {
        final CredentialsProvider provider = new BasicCredentialsProvider();
        final UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(STASH_USER,
                STASH_PASSWORD);
        provider.setCredentials(AuthScope.ANY, credentials);

        final AuthCache authCache = new BasicAuthCache();
        final HttpHost host = new HttpHost("localhost", 7990);
        authCache.put(host, new BasicScheme());

        httpClient = HttpClientBuilder.create().setDefaultCredentialsProvider(provider).build();

        httpContext = HttpClientContext.create();
        httpContext.setCredentialsProvider(provider);
        httpContext.setAuthCache(authCache);
    }

    @Before
    public void createInitialConfig() throws Exception {
        fileCounter = 0;
        project = createProject(PROJECT_KEY);
        repository = project.forceCreateRepository(REPOSITORY_KEY);
        repository.setHookSettings("", false);
        workspace = repository.cloneRepository();
        workspace.setCrlf("false");
        workspace.config("push.default", "simple");
    }

    @After
    public void sweepWorkspace() throws Exception {
        repository.delete();
        project.delete();
        FileUtils.forceDelete(workspace.getWorkDir());
    }

    protected Project createProject(String projectKey) {

        final Project oldProject = new RestProject(PROJECT_KEY);
        final Repository oldRepository = new RestRepository(oldProject, REPOSITORY_KEY);
        try {
            try {
                oldRepository.delete();
            } catch (Exception e) {

            }
            oldProject.delete();
        } catch (Exception e) {

        }

        performPostQuery(STASH_REST_URL + REST_PROJECTS, new BuildJSON().addKey(KEY, projectKey)
                .addKey(NAME, projectKey).addKey("description", projectKey).build());
        return new RestProject(projectKey);
    }

    private void performQuery(HttpRequestBase method, String data) {
        try {
            final HttpResponse response = httpClient.execute(method, httpContext);
            if (response.getStatusLine().getStatusCode() / 100 != 2) {
                throw new RuntimeException("Error performing HTTP request: "
                        + response.getStatusLine().getReasonPhrase());
            }
        } catch (ClientProtocolException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            method.reset();
        }
    }

    private void performEntityQuery(HttpEntityEnclosingRequestBase method, String data) {
        try {
            method.setEntity(createHttpEntity(data));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        performQuery(method, data);
    }

    protected void performPostQuery(String url, String data) {
        performEntityQuery(new HttpPost(url), data);
    }

    protected void performPutQuery(String url, String data) {
        performEntityQuery(new HttpPut(url), data);
    }

    protected void performDeleteQuery(String url) {
        performQuery(new HttpDelete(url), (String) null);
    }

    private HttpEntity createHttpEntity(String data) throws UnsupportedEncodingException {
        if (data == null) {
            return null;
        }
        final StringEntity entity = new StringEntity(data);
        entity.setContentType("application/json");
        return entity;
    }

    public Workspace getWorkspace() {
        return workspace;
    }

    public Repository getRepository() {
        return repository;
    }

    protected String getNextFileName() {
        return RANDOM_FILE + (fileCounter++);
    }

    protected static ExecutionResult executeGitCommand(File workspaceDir, String... parameters)
            throws Exception {
        final Runtime rt = Runtime.getRuntime();
        final String[] cmdArray = new String[parameters.length + 1];
        System.arraycopy(parameters, 0, cmdArray, 1, parameters.length);
        cmdArray[0] = GIT;
        final Process pr = rt.exec(cmdArray, new String[] {}, workspaceDir);
        final int exitCode = pr.waitFor();
        final String output = IOUtils.toString(pr.getInputStream());
        final String err = IOUtils.toString(pr.getErrorStream());
        return new ExecutionResult(exitCode, output, err);
    }

    /**
     * JSON request builder.
     */
    private static class BuildJSON {
        private final StringBuilder sb;
        private boolean hasData = false;
        private boolean finilized = false;

        public BuildJSON() {
            sb = new StringBuilder();
            sb.append("{ ");
        }

        public BuildJSON addKey(String key, String value) {
            return addRawKey(key, createString(value));
        }

        private String createString(String name) {
            return BLOCK_QUOTE + name + BLOCK_QUOTE;
        }

        private BuildJSON addRawKey(String key, String value) {
            if (finilized) {
                throw new IllegalStateException(
                        "This JSON builder is finilized. No additional data can be added");
            }
            if (hasData) {
                sb.append(',');
            }
            sb.append("\n\"").append(key).append("\": ").append(value);
            hasData = true;
            return this;

        }

        public String build() {
            finilized = true;
            sb.append(EOL).append("}");
            return sb.toString();
        }

        public BuildJSON addKey(String key, boolean value) {
            return addRawKey(key, Boolean.toString(value));
        }
    }

    /**
     * Project implementation via REST.
     */
    private class RestProject implements Project {
        private final String key;

        public RestProject(String key) {
            this.key = key;
        }

        @Override
        public Repository createRepository(String name) {
            performPostQuery(getUrl() + REST_REPOSITORIES, new BuildJSON().addKey(NAME, name)
                    .addKey("scmId", GIT).addKey("forkable", true).build());
            return new RestRepository(this, name);
        }

        @Override
        public String getKey() {
            return key;
        }

        public String getUrl() {
            return STASH_REST_URL + REST_PROJECTS + REST_DELIMITER + key;
        }

        @Override
        public void delete() {
            performDeleteQuery(getUrl());
        }

        @Override
        public void removeRepository(String key) {
            new RestRepository(this, key).delete();
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
    private class RestRepository implements Repository {

        private final Project project;
        private final String key;

        public RestRepository(Project project, String key) {
            this.key = key;
            this.project = project;
        }

        @Override
        public Workspace cloneRepository() throws Exception {
            final Runtime rt = Runtime.getRuntime();
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
            return "http://admin:admin@" + STASH_HOST + "/scm/" + project.getKey() + REST_DELIMITER
                    + key;
        }

        @Override
        public String getUrl() {
            return project.getUrl() + REST_REPOSITORIES + REST_DELIMITER + key;
        }

        private String getHookUrl(String hookKey) {
            return getUrl() + "/settings/hooks/" + hookKey;
        }

        private String getHookEnableUrl(String hookKey) {
            return getHookUrl(hookKey) + "/enabled";
        }

        private String getSettingsUrl(String hookKey) {
            return getHookUrl(hookKey) + "/settings";
        }

        @Override
        public void enableHook() {
            performPutQuery(getHookEnableUrl(HOOK_KEY), null);
        }

        @Override
        public void disableHook() {
            performDeleteQuery(getHookEnableUrl(HOOK_KEY));
        }

        @Override
        public void delete() {
            performDeleteQuery(getUrl());
        }

        @Override
        public void setHookSettings(String excludedFiles, boolean allowInherited) {
            performPutQuery(
                    getSettingsUrl(HOOK_KEY),
                    new BuildJSON().addKey(Constants.SETTING_EXCLUDED_FILES, excludedFiles)
                            .addKey(Constants.SETTING_ALLOW_INHERITED_EOL, allowInherited).build());
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

        private ExecutionResult executeGitCommandImpl(String... parameters) throws Exception {
            return executeGitCommand(workspaceDir, parameters);
        }

        private void executeCommand(String... parameters) throws Exception {
            final ExecutionResult result = executeGitCommandImpl(parameters);
            if (result.getExitCode() != 0) {
                throw new GitExecutionException(result);
            }
        }

        @Override
        public File commitNewFile(String filename, String contents) throws Exception {
            final File newFile = new File(workspaceDir, filename);
            FileUtils.write(newFile, contents);
            add(newFile.getAbsolutePath());
            commit(filename + " added");
            return newFile;
        }

        @Override
        public void add(String filename) throws Exception {
            executeCommand("add", filename);
        }

        public void commit(String message) throws Exception {
            executeCommand("commit", "-m", message);
        }

        @Override
        public void checkout(String branchName) throws Exception {
            executeCommand("checkout", branchName);
        }

        @Override
        public void branch(String branchName) throws Exception {
            executeCommand(GIT_BRANCH, branchName);
        }

        @Override
        public boolean push() throws Exception {
            final ExecutionResult result = executeGitCommandImpl(GIT_PUSH);
            return result.getExitCode() == 0;
        }

        @Override
        public boolean push(String branchName) throws Exception {
            final ExecutionResult result = executeGitCommandImpl(GIT_PUSH, GIT_ORIGIN, branchName);
            return result.getExitCode() == 0;
        }

        @Override
        public boolean pushRemoval(String branchName) throws Exception {
            final ExecutionResult result = executeGitCommandImpl(GIT_PUSH, GIT_ORIGIN, ":"
                    + branchName);
            return result.getExitCode() == 0;
        }

        @Override
        public void setCrlf(String crlf) throws Exception {
            config("core.autocrlf", crlf);
        }

        @Override
        public File getWorkDir() {
            return workspaceDir;
        }

        @Override
        public void config(String... parameters) throws Exception {
            executeCommand((String[]) ArrayUtils.addAll(new String[] {"config", "--local"},
                    parameters));
        }

        @Override
        public void commitNewContents(File targetFile, String newContents) throws Exception {
            FileUtils.write(targetFile, newContents);
            add(targetFile.getPath());
            commit("Changed contents of file " + targetFile.getPath());
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

}
