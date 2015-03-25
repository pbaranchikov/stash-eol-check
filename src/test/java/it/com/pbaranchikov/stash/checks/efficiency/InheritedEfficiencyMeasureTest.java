package it.com.pbaranchikov.stash.checks.efficiency;

import org.junit.Before;
import org.junit.Ignore;

/**
 * Efficiency measuring test for inherited EOL-style checks.
 * @author Pavel Baranchikov
 */
@Ignore("These is not a real test - just a code to measure efficiency")
public class InheritedEfficiencyMeasureTest extends AbstractEfficiencyMeasureTest {
    @Before
    public void initialize() {
        getRepository().setHookSettings("", true);
        getRepository().enableHook();
    }

}
