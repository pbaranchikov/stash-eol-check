package it.com.pbaranchikov.stash.checks;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit test for branching checks.
 * @author Pavel Baranchikov
 */
public class CreateBranchTest extends AbstractGitCheck {

    private static final String BRANCH_NAME = "newBranch";

    @Test
    public void testCreateNewClearBranchFromGood() throws Exception {
        getWorkspace().commitNewFile(getNextFileName(), GOOD_CONTENTS);
        Assert.assertTrue(getWorkspace().push());
        getRepository().enableHook();
        getWorkspace().branch(BRANCH_NAME);
        getWorkspace().checkout(BRANCH_NAME);
        Assert.assertTrue(getWorkspace().push(BRANCH_NAME));
    }

    @Test
    public void testCreateNewClearBranchFromBad() throws Exception {
        initDefaultBranch();
        Assert.assertTrue(getWorkspace().push(BRANCH_NAME));
    }

    @Test
    public void testCreateSimpleGoodBranch() throws Exception {
        initDefaultBranch();
        getWorkspace().commitNewFile(GOOD_FILE, GOOD_CONTENTS);
        Assert.assertTrue(getWorkspace().push(BRANCH_NAME));
    }

    @Test
    public void testCreateSimpleBadBranch() throws Exception {
        initDefaultBranch();
        getWorkspace().commitNewFile(BAD_FILE, WRONG_CONTENTS);
        Assert.assertFalse(getWorkspace().push(BRANCH_NAME));
    }

    @Test
    public void testCreateGoodBadBranch() throws Exception {
        initDefaultBranch();
        getWorkspace().commitNewFile(GOOD_FILE, GOOD_CONTENTS);
        getWorkspace().commitNewFile(BAD_FILE, WRONG_CONTENTS);
        Assert.assertFalse(getWorkspace().push(BRANCH_NAME));
    }

    @Test
    public void testCreateBadGoodBranch() throws Exception {
        initDefaultBranch();
        getWorkspace().commitNewFile(BAD_FILE, WRONG_CONTENTS);
        getWorkspace().commitNewFile(GOOD_FILE, GOOD_CONTENTS);
        Assert.assertFalse(getWorkspace().push(BRANCH_NAME));
    }

    @Test
    public void testDeleteBadBranch() throws Exception {
        initDefaultBranch();
        getRepository().disableHook();
        getWorkspace().commitNewFile(BAD_FILE, WRONG_CONTENTS);
        Assert.assertTrue(getWorkspace().push(BRANCH_NAME));
        getRepository().enableHook();
        Assert.assertTrue(getWorkspace().pushRemoval(BRANCH_NAME));
    }

    @Test
    public void testDeleteGoodBranch() throws Exception {
        initDefaultBranch();
        getRepository().disableHook();
        getWorkspace().commitNewFile(GOOD_FILE, GOOD_CONTENTS);
        Assert.assertTrue(getWorkspace().push(BRANCH_NAME));
        getRepository().enableHook();
        Assert.assertTrue(getWorkspace().pushRemoval(BRANCH_NAME));
    }

    private void initDefaultBranch() throws Exception {
        getWorkspace().commitNewFile(getNextFileName(), WRONG_CONTENTS);
        Assert.assertTrue(getWorkspace().push());
        getRepository().enableHook();
        getWorkspace().branch(BRANCH_NAME);
        getWorkspace().checkout(BRANCH_NAME);

    }
}
