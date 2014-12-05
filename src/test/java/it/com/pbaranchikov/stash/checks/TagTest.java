package it.com.pbaranchikov.stash.checks;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Test for pushing and removing tags.
 * @author Pavel Baranchikov
 */
public class TagTest extends AbstractGitCheck {

    private static final String TAG_NAME = "new-tag";

    @Test
    @Ignore
    public void tagFromGoodBranch() {
        getWorkspace().commitNewFile(getNextFileName(), GOOD_CONTENTS);
        getRepository().enableHook();
        Assert.assertTrue(getWorkspace().createTag(TAG_NAME));
    }

    @Test
    @Ignore
    public void tagFromBadBranch() {
        getWorkspace().commitNewFile(getNextFileName(), GOOD_CONTENTS);
        getRepository().enableHook();
        Assert.assertTrue(getWorkspace().createTag(TAG_NAME));
    }

    @Test
    @Ignore
    public void tagFromBadGoodBranch() {
        getWorkspace().commitNewFile(getNextFileName(), WRONG_CONTENTS);
        getWorkspace().commitNewFile(getNextFileName(), GOOD_CONTENTS);
        getRepository().enableHook();
        Assert.assertTrue(getWorkspace().createTag(TAG_NAME));
    }

    @Test
    @Ignore
    public void tagFromGoodBadBranch() {
        getWorkspace().commitNewFile(getNextFileName(), GOOD_CONTENTS);
        getWorkspace().commitNewFile(getNextFileName(), WRONG_CONTENTS);
        getRepository().enableHook();
        Assert.assertTrue(getWorkspace().createTag(TAG_NAME));
    }

    @Test
    @Ignore
    public void removeTag() {
        getWorkspace().commitNewFile(getNextFileName(), GOOD_CONTENTS);
        getWorkspace().commitNewFile(getNextFileName(), WRONG_CONTENTS);
        getRepository().enableHook();
        Assert.assertTrue(getWorkspace().createTag(TAG_NAME));
        Assert.assertTrue(getWorkspace().removeTag(TAG_NAME));
    }

}
