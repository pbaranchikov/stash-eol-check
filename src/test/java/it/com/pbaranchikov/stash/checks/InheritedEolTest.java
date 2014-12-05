package it.com.pbaranchikov.stash.checks;

import java.io.File;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for "inherit parent EOL style" functionality.
 * @author Pavel Baranchikov
 */
public class InheritedEolTest extends AbstractGitCheck {

    private File goodFile;
    private File badFile;

    @Before
    public void prepareParentCommits() throws Exception {
        goodFile = getWorkspace().commitNewFile(GOOD_FILE, GOOD_CONTENTS);
        badFile = getWorkspace().commitNewFile(BAD_FILE, WRONG_CONTENTS);
        Assert.assertTrue(getWorkspace().push());
        getRepository().enableHook();
    }

    @Test
    public void testInheritedGood2Good() throws Exception {
        enableInherited(true);
        getWorkspace().commitNewContents(goodFile, GOOD_CONTENTS + GOOD_CONTENTS);
        Assert.assertTrue(getWorkspace().push());
    }

    @Test
    public void testInheritedGood2Bad() throws Exception {
        enableInherited(true);
        getWorkspace().commitNewContents(goodFile, WRONG_CONTENTS);
        Assert.assertFalse(getWorkspace().push());
    }

    @Test
    public void testInheritedBad2Good() throws Exception {
        enableInherited(true);
        getWorkspace().commitNewContents(badFile, GOOD_CONTENTS);
        Assert.assertTrue(getWorkspace().push());
    }

    @Test
    public void testInheritedBad2Bad() throws Exception {
        enableInherited(true);
        getWorkspace().commitNewContents(badFile, WRONG_CONTENTS + WRONG_CONTENTS);
        Assert.assertTrue(getWorkspace().push());
    }

    @Test
    public void testNonInheritedBad2Bad() throws Exception {
        enableInherited(false);
        getWorkspace().commitNewContents(badFile, WRONG_CONTENTS + WRONG_CONTENTS);
        Assert.assertFalse(getWorkspace().push());
    }

    private void enableInherited(boolean enabled) {
        getRepository().setHookSettings("", enabled);
    }
}
