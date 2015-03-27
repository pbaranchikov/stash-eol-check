package it.com.pbaranchikov.stash.checks.efficiency;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.runner.RunWith;

import com.atlassian.plugins.osgi.test.AtlassianPluginsTestRunner;

import it.com.pbaranchikov.stash.checks.utils.WrappersFactory;

/**
 * Efficiency measuring test for inherited EOL-style checks.
 * @author Pavel Baranchikov
 */
@Ignore("These is not a real test - just a code to measure efficiency")
@RunWith(AtlassianPluginsTestRunner.class)
public class InheritedEfficiencyMeasureTest extends AbstractEfficiencyMeasureTest {

    public InheritedEfficiencyMeasureTest(WrappersFactory wrappersFactory) {
        super(wrappersFactory);
    }

    @Before
    public void initialize() {
        getRepository().setHookSettings("", true);
        getRepository().enableHook();
    }

}
