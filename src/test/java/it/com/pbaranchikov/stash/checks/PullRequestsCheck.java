package it.com.pbaranchikov.stash.checks;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.atlassian.plugins.osgi.test.AtlassianPluginsTestRunner;

import it.com.pbaranchikov.stash.checks.utils.Repository;
import it.com.pbaranchikov.stash.checks.utils.Workspace;
import it.com.pbaranchikov.stash.checks.utils.WrappersFactory;

/**
 * Integration test for pull request merging vetoes.
 * @author Pavel Baranchikov
 */
@RunWith(AtlassianPluginsTestRunner.class)
public class PullRequestsCheck extends AbstractGitCheck {

    private static final String BRANCH_BAD = "anotherBranch";

    private Repository personalRepository;
    private Workspace personalWorkspace;

    protected PullRequestsCheck(WrappersFactory wrappersFactory) {
        super(wrappersFactory);
    }

    @Before
    public void createForksAndBranches() throws Exception {
        commitGood();
        Assert.assertTrue(getWorkspace().push());
        getWorkspace().branch(BRANCH_BAD);
        commitGood();
        Assert.assertTrue(getWorkspace().push());
        getWorkspace().checkout(BRANCH_BAD);
        commitBad();
        Assert.assertTrue(getWorkspace().push(BRANCH_BAD));
        getRepository().enableHook();

        personalRepository = getRepository().fork();
        personalWorkspace = personalRepository.cloneRepository();
    }

    @Test
    public void testBadAfterGoodMerge() {
        personalWorkspace.checkout(BRANCH_MASTER);
        personalWorkspace.commitNewFile(getNextFileName(), GOOD_CONTENTS);
        personalWorkspace.commitNewFile(getNextFileName(), WRONG_CONTENTS);
        Assert.assertTrue(personalWorkspace.push());
        Assert.assertFalse(personalRepository.tryCreatePullRequest(getRepository(), BRANCH_MASTER));

        personalWorkspace.checkout(BRANCH_BAD);
        personalWorkspace.commitNewFile(getNextFileName(), GOOD_CONTENTS);
        personalWorkspace.commitNewFile(getNextFileName(), WRONG_CONTENTS);
        Assert.assertTrue(personalWorkspace.push());
        Assert.assertFalse(personalRepository.tryCreatePullRequest(getRepository(), BRANCH_BAD));
    }

    @Test
    public void testGoodAfterBadMerge() {
        personalWorkspace.checkout(BRANCH_MASTER);
        personalWorkspace.commitNewFile(getNextFileName(), WRONG_CONTENTS);
        personalWorkspace.commitNewFile(getNextFileName(), GOOD_CONTENTS);
        Assert.assertTrue(personalWorkspace.push());
        Assert.assertFalse(personalRepository.tryCreatePullRequest(getRepository(), BRANCH_MASTER));

        personalWorkspace.checkout(BRANCH_BAD);
        personalWorkspace.commitNewFile(getNextFileName(), WRONG_CONTENTS);
        personalWorkspace.commitNewFile(getNextFileName(), GOOD_CONTENTS);
        Assert.assertTrue(personalWorkspace.push());
        Assert.assertFalse(personalRepository.tryCreatePullRequest(getRepository(), BRANCH_BAD));
    }

    @Test
    public void testSingleGoodCommitMerge() {
        personalWorkspace.checkout(BRANCH_MASTER);
        personalWorkspace.commitNewFile(getNextFileName(), GOOD_CONTENTS);
        Assert.assertTrue(personalWorkspace.push());
        Assert.assertTrue(personalRepository.tryCreatePullRequest(getRepository(), BRANCH_MASTER));

        personalWorkspace.checkout(BRANCH_BAD);
        personalWorkspace.commitNewFile(getNextFileName(), GOOD_CONTENTS);
        Assert.assertTrue(personalWorkspace.push());
        Assert.assertTrue(personalRepository.tryCreatePullRequest(getRepository(), BRANCH_BAD));
    }

    @Test
    public void testSingleBadCommitMerge() {
        personalWorkspace.checkout(BRANCH_MASTER);
        personalWorkspace.commitNewFile(getNextFileName(), WRONG_CONTENTS);
        Assert.assertTrue(personalWorkspace.push());
        Assert.assertFalse(personalRepository.tryCreatePullRequest(getRepository(), BRANCH_MASTER));

        personalWorkspace.checkout(BRANCH_BAD);
        personalWorkspace.commitNewFile(getNextFileName(), WRONG_CONTENTS);
        Assert.assertTrue(personalWorkspace.push());
        Assert.assertFalse(personalRepository.tryCreatePullRequest(getRepository(), BRANCH_BAD));
    }

}
