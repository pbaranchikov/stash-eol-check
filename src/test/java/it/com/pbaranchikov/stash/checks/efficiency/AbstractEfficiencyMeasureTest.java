package it.com.pbaranchikov.stash.checks.efficiency;

import it.com.pbaranchikov.stash.checks.AbstractGitCheck;
import it.com.pbaranchikov.stash.checks.utils.WrappersFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

/**
 * Class to perform efficiency measuring of the hook. All the tests here are
 * skipped, as they are heavy-duty and they are not tests - but just a code to
 * measure efficiency
 * @author Pavel Baranchikov
 */
public abstract class AbstractEfficiencyMeasureTest extends AbstractGitCheck {

    private static final int STRINGS_COUNT = 10000000;

    public AbstractEfficiencyMeasureTest(WrappersFactory wrappersFactory) {
        super(wrappersFactory);
    }

    @Rule
    public TestName name = new TestName();

    @Before
    public void init() {
        getWorkspace().commitNewFile(getNextFileName(), GOOD_CONTENTS);
        Assert.assertTrue(getWorkspace().push());
    }

    @Test
    public void testLargeGoodFileAdd() throws Exception {
        final File file = new File(getWorkspace().getWorkDir(), getNextFileName());
        appendFileAndPush(file, true);
    }

    @Test
    public void testLargeBadFileAdd() throws Exception {
        final File file = new File(getWorkspace().getWorkDir(), getNextFileName());
        appendFileAndPush(file, false);
    }

    @Test
    public void testLargeGoodFileModify() throws Exception {
        testLargeFileModify(true);
    }

    @Test
    public void testLargeBadFileModify() throws Exception {
        testLargeFileModify(false);
    }

    public void testLargeFileModify(boolean contents) throws Exception {
        final File file = getWorkspace().commitNewFile(getNextFileName(), GOOD_CONTENTS);
        appendFileAndPush(file, contents);
    }

    private void push(boolean expectedSuccess) {
        final long startTime = System.currentTimeMillis();
        final boolean success = getWorkspace().push();
        final long endTime = System.currentTimeMillis();
        System.out.println(getClass().getSimpleName() + "." + name.getMethodName() + " - "
                + (endTime - startTime) + " ms");
        Assert.assertEquals(expectedSuccess, success);
    }

    private void appendFileAndPush(File file, boolean contents) throws IOException {
        try (Writer writer = new FileWriter(file)) {
            for (int i = 0; i < STRINGS_COUNT; i++) {
                writer.write(contents ? GOOD_CONTENTS : WRONG_CONTENTS);
            }
        }
        getWorkspace().add(file.getAbsolutePath());
        getWorkspace().commit("Changed file committed");
        push(contents);
    }

}
