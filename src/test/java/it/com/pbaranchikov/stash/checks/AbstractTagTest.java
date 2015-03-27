package it.com.pbaranchikov.stash.checks;

import org.junit.Assert;
import org.junit.Test;

import it.com.pbaranchikov.stash.checks.utils.WrappersFactory;

/**
 * Test for pushing and removing tags.
 * @author Pavel Baranchikov
 */
public abstract class AbstractTagTest extends AbstractGitCheck {

    private static final String TAG_NAME = "new-tag";

    public AbstractTagTest(WrappersFactory wrappersFactory) {
        super(wrappersFactory);
    }

    /**
     * Method should create tag from the current workspace for the current
     * commit.
     * @param tagName name for the tag
     */
    protected abstract void createTag(String tagName);

    @Test
    public void tagFromGoodBranch() {
        commitGood();
        Assert.assertTrue(getWorkspace().push());
        getRepository().enableHook();
        createTag(TAG_NAME);
        Assert.assertTrue(getWorkspace().push(TAG_NAME));
    }

    @Test
    public void tagFromBadBranch() {
        commitBad();
        Assert.assertTrue(getWorkspace().push());
        getRepository().enableHook();
        createTag(TAG_NAME);
        Assert.assertTrue(getWorkspace().push(TAG_NAME));
    }

    @Test
    public void tagFromBadGoodBranch() {
        commitBad();
        commitGood();
        Assert.assertTrue(getWorkspace().push());
        getRepository().enableHook();
        createTag(TAG_NAME);
        Assert.assertTrue(getWorkspace().push(TAG_NAME));
    }

    @Test
    public void tagFromGoodBadBranch() {
        commitGood();
        commitBad();
        Assert.assertTrue(getWorkspace().push());
        getRepository().enableHook();
        createTag(TAG_NAME);
        Assert.assertTrue(getWorkspace().push(TAG_NAME));
    }

    @Test
    public void tagFromMiddleOfBranch() {
        commitBad();
        commitBad();
        Assert.assertTrue(getWorkspace().push());
        createTag(TAG_NAME);
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
        createTag(TAG_NAME);
        Assert.assertTrue(getWorkspace().push(TAG_NAME));
        Assert.assertTrue(getWorkspace().pushRemoval(TAG_NAME));
    }

}
