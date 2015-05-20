package it.com.pbaranchikov.stash.checks;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.atlassian.plugins.osgi.test.AtlassianPluginsTestRunner;

import it.com.pbaranchikov.stash.checks.utils.WrappersFactory;

/**
 * Tests unit to cover patterns work.
 * @author Pavel Baranchikov
 */
@RunWith(AtlassianPluginsTestRunner.class)
public class ExcludedPatternsTest extends AbstractGitCheck {

    private static final String EXCLUDED_FILE1 = "excluded";
    private static final String EXCLUDED_FILE2 = "ezcluded";
    private static final String EXCLUDED_PATTERN = "ex.*";
    private static final String EXCLUDED_PATTERN_BOTH = "ex.*,ez.*";

    protected ExcludedPatternsTest(WrappersFactory wrappersFactory) {
        super(wrappersFactory);
    }

    @Before
    public void init() throws Exception {
        getWorkspace().commitNewFile(GOOD_FILE, GOOD_CONTENTS);
        getWorkspace().push();
    }

    @Test
    public void testSingleExcluded() throws Exception {
        getRepository().setHookSettings(EXCLUDED_PATTERN, false);
        getRepository().enableHook();
        getWorkspace().commitNewFile(EXCLUDED_FILE1 + getNextFileNumber(), WRONG_CONTENTS);
        Assert.assertTrue(getWorkspace().push());
        getWorkspace().commitNewFile(EXCLUDED_FILE2 + getNextFileNumber(), WRONG_CONTENTS);
        Assert.assertFalse(getWorkspace().push());
    }

    @Test
    public void testTwoExcluded() throws Exception {
        getRepository().setHookSettings(EXCLUDED_PATTERN_BOTH, false);
        getRepository().enableHook();
        getWorkspace().commitNewFile(EXCLUDED_FILE1 + getNextFileNumber(), WRONG_CONTENTS);
        Assert.assertTrue(getWorkspace().push());
        getWorkspace().commitNewFile(EXCLUDED_FILE2 + getNextFileNumber(), WRONG_CONTENTS);
        Assert.assertTrue(getWorkspace().push());
        getWorkspace().commitNewFile(BAD_FILE + getNextFileNumber(), WRONG_CONTENTS);
        Assert.assertFalse(getWorkspace().push());
    }

    @Test
    public void testOverlappedPatternsExcluded() throws Exception {
        getRepository().setHookSettings("ex.*,excl.*", false);
        getRepository().enableHook();
        getWorkspace().commitNewFile(EXCLUDED_FILE1 + getNextFileNumber(), WRONG_CONTENTS);
        Assert.assertTrue(getWorkspace().push());
        getWorkspace().commitNewFile(EXCLUDED_FILE2 + getNextFileNumber(), WRONG_CONTENTS);
        Assert.assertFalse(getWorkspace().push());
    }

}
