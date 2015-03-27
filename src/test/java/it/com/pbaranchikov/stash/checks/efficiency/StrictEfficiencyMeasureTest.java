package it.com.pbaranchikov.stash.checks.efficiency;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.runner.RunWith;

import com.atlassian.plugins.osgi.test.AtlassianPluginsTestRunner;

import it.com.pbaranchikov.stash.checks.utils.WrappersFactory;

/**
 * Efficiency measuring test for strict EOL-style checks.
 * @author Pavel Baranchikov
 */
@Ignore("These is not a real test - just a code to measure efficiency")
@RunWith(AtlassianPluginsTestRunner.class)
public class StrictEfficiencyMeasureTest extends AbstractEfficiencyMeasureTest {

    public StrictEfficiencyMeasureTest(WrappersFactory wrappersFactory) {
        super(wrappersFactory);
    }

    @Before
    public void initialize() {
        getRepository().setHookSettings("", false);
        getRepository().enableHook();
    }

}
