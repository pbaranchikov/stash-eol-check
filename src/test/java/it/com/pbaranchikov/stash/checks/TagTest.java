package it.com.pbaranchikov.stash.checks;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test for pushing and removing tags.
 * @author Pavel Baranchikov
 */
public class TagTest extends AbstractGitCheck {

    private static final String TAG_NAME = "new-tag";

    @Test
    public void tagFromGoodBranch() {
        commitGood();
        Assert.assertTrue(getWorkspace().push());
        getRepository().enableHook();
        getWorkspace().createTag(TAG_NAME);
        Assert.assertTrue(getWorkspace().push(TAG_NAME));
    }

    @Test
    public void tagFromBadBranch() {
        commitBad();
        Assert.assertTrue(getWorkspace().push());
        getRepository().enableHook();
        getWorkspace().createTag(TAG_NAME);
        Assert.assertTrue(getWorkspace().push(TAG_NAME));
    }

    @Test
    public void tagFromBadGoodBranch() {
        commitBad();
        commitGood();
        Assert.assertTrue(getWorkspace().push());
        getRepository().enableHook();
        getWorkspace().createTag(TAG_NAME);
        Assert.assertTrue(getWorkspace().push(TAG_NAME));
    }

    @Test
    public void tagFromGoodBadBranch() {
        commitGood();
        commitBad();
        Assert.assertTrue(getWorkspace().push());
        getRepository().enableHook();
        getWorkspace().createTag(TAG_NAME);
        Assert.assertTrue(getWorkspace().push(TAG_NAME));
    }

    @Test
    public void tagFromMiddleOfBranch() {
        commitBad();
        commitBad();
        Assert.assertTrue(getWorkspace().push());
        getWorkspace().createTag(TAG_NAME);
        commitBad();
        Assert.assertTrue(getWorkspace().push());
        getRepository().enableHook();
        Assert.assertTrue(getWorkspace().push(TAG_NAME));
    }

    @Test
    public void removeTag() {
        commitGood();
        commitBad();
        Assert.assertTrue(getWorkspace().push());
        getRepository().enableHook();
        getWorkspace().createTag(TAG_NAME);
        Assert.assertTrue(getWorkspace().push(TAG_NAME));
        Assert.assertTrue(getWorkspace().pushRemoval(TAG_NAME));
    }

}
