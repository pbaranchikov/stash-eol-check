package it.com.pbaranchikov.stash.checks;

import java.io.File;

import org.junit.After;
import org.junit.Before;

import it.com.pbaranchikov.stash.checks.utils.Project;
import it.com.pbaranchikov.stash.checks.utils.Repository;
import it.com.pbaranchikov.stash.checks.utils.Workspace;
import it.com.pbaranchikov.stash.checks.utils.WrappersFactory;

/**
 * Abstract suitcase for git work copies.<br/>
 * @author Pavel Baranchikov
 */
public abstract class AbstractGitCheck {

    protected static final String WRONG_CONTENTS = "This\r\nis file with wrong contents\r\n";
    protected static final String GOOD_CONTENTS = "This\nis file with wrong contents\n";
    protected static final String GOOD_FILE = "goodFile";
    protected static final String BAD_FILE = "badFile";
    protected static final String BRANCH_MASTER = "master";

    private static final String PROJECT_KEY = "PROJECT_FOR_TESTS";
    private static final String REPOSITORY_KEY = "TEST_REPOSITORY";

    private static final String RANDOM_FILE = "randomFile";

    private Repository repository;
    private Project project;
    private Workspace workspace;
    private int fileCounter;

    private final WrappersFactory wrappersFactory;

    protected AbstractGitCheck(WrappersFactory wrappersFactory) {
        this.wrappersFactory = wrappersFactory;
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
        wrappersFactory.cleanup();
    }

    protected Project createProject(String projectKey) {
        return wrappersFactory.createProject(projectKey, projectKey + " name");
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

    protected File commitBad() {
        return getWorkspace().commitNewFile("bad" + getNextFileName(), WRONG_CONTENTS);
    }

    protected File commitGood() {
        return getWorkspace().commitNewFile("good" + getNextFileName(), GOOD_CONTENTS);
    }

}
