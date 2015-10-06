package it.com.pbaranchikov.stash.checks;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.StringReader;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import it.com.pbaranchikov.stash.checks.utils.WrappersFactory;

/**
 * Unit tests for regular commits.
 * @author Pavel Baranchikov
 */
@RunWith(com.atlassian.plugins.osgi.test.AtlassianPluginsTestRunner.class)
public class RegularCommitsTest extends AbstractGitCheck {

    public RegularCommitsTest(WrappersFactory wrappersFactory) {
        super(wrappersFactory);
    }

    @Test
    public void testSimpleBadFile() throws Exception {
        getRepository().enableHook();
        getWorkspace().commitNewFile("badFile1", WRONG_CONTENTS);
        Assert.assertFalse(getWorkspace().push());
    }

    @Test
    public void testSimpleGoodFile() throws Exception {
        getRepository().enableHook();
        getWorkspace().commitNewFile("goodFile1", GOOD_CONTENTS);
        Assert.assertTrue(getWorkspace().push());
    }

    @Test
    @Ignore
    // TODO fix this check
    public void testBadGoodInitialSequence() throws Exception {
        getRepository().enableHook();
        getWorkspace().commitNewFile(BAD_FILE, WRONG_CONTENTS);
        getWorkspace().commitNewFile(GOOD_FILE, GOOD_CONTENTS);
        Assert.assertFalse(getWorkspace().push());
    }

    @Test
    public void testGoodBadInitialSequence() throws Exception {
        getRepository().enableHook();
        getWorkspace().commitNewFile(GOOD_FILE, GOOD_CONTENTS);
        getWorkspace().commitNewFile(BAD_FILE, WRONG_CONTENTS);
        Assert.assertFalse(getWorkspace().push());
    }

    @Test
    public void testBadGoodSequence() throws Exception {
        getWorkspace().commitNewFile(getNextFileName(), WRONG_CONTENTS);
        Assert.assertTrue(getWorkspace().push());
        getRepository().enableHook();
        getWorkspace().commitNewFile(BAD_FILE, WRONG_CONTENTS);
        getWorkspace().commitNewFile(GOOD_FILE, GOOD_CONTENTS);
        Assert.assertFalse(getWorkspace().push());
    }

    @Test
    public void testGoodBadSequence() throws Exception {
        getWorkspace().commitNewFile(getNextFileName(), WRONG_CONTENTS);
        Assert.assertTrue(getWorkspace().push());
        getRepository().enableHook();
        getWorkspace().commitNewFile(GOOD_FILE, GOOD_CONTENTS);
        getWorkspace().commitNewFile(BAD_FILE, WRONG_CONTENTS);
        Assert.assertFalse(getWorkspace().push());
    }

    @Test
    public void testGoodAfterBad() throws Exception {
        getWorkspace().commitNewFile(BAD_FILE, WRONG_CONTENTS);
        Assert.assertTrue(getWorkspace().push());
        getRepository().enableHook();
        getWorkspace().commitNewFile(GOOD_FILE, GOOD_CONTENTS);
        Assert.assertTrue(getWorkspace().push());
    }

    @Test
    public void testBadAfterBad() throws Exception {
        getWorkspace().commitNewFile(BAD_FILE, WRONG_CONTENTS);
        Assert.assertTrue(getWorkspace().push());
        getRepository().enableHook();
        getWorkspace().commitNewFile(BAD_FILE + 2, WRONG_CONTENTS);
        Assert.assertFalse(getWorkspace().push());
    }

    @Test
    public void testGood2BadConvert() throws Exception {
        final File file = getWorkspace().commitNewFile(GOOD_FILE, GOOD_CONTENTS);
        Assert.assertTrue(getWorkspace().push());
        getRepository().enableHook();
        getWorkspace().commitNewContents(file, WRONG_CONTENTS);
        Assert.assertFalse(getWorkspace().push());
    }

    @Test
    public void testBad2GoodConvert() throws Exception {
        final File file = getWorkspace().commitNewFile(BAD_FILE, WRONG_CONTENTS);
        Assert.assertTrue(getWorkspace().push());
        getRepository().enableHook();
        getWorkspace().commitNewContents(file, GOOD_CONTENTS);
        Assert.assertTrue(getWorkspace().push());
    }

    @Test
    public void testGoodArchive() throws Exception {
        getRepository().enableHook();
        getWorkspace().commitNewFile("good archive", getArchive(GOOD_FILE, GOOD_CONTENTS));
        Assert.assertTrue(getWorkspace().push());
    }

    @Test
    public void testBadArchive() throws Exception {
        getRepository().enableHook();
        getWorkspace().commitNewFile("bad archive", getArchive(BAD_FILE, WRONG_CONTENTS));
        Assert.assertTrue(getWorkspace().push());
    }

    protected static String getArchive(String filename, String contents) throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final TarArchiveOutputStream stream = new TarArchiveOutputStream(out);
        final TarArchiveEntry entry = new TarArchiveEntry(filename);
        entry.setSize(contents.length());
        stream.putArchiveEntry(entry);
        IOUtils.copy(new StringReader(contents), stream);
        stream.closeArchiveEntry();
        stream.close();
        out.close();
        return out.toString();
    }

}
