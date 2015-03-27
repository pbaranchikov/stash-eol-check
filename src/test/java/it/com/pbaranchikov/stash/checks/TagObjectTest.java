package it.com.pbaranchikov.stash.checks;

import org.junit.runner.RunWith;

import com.atlassian.plugins.osgi.test.AtlassianPluginsTestRunner;

import it.com.pbaranchikov.stash.checks.utils.WrappersFactory;

/**
 * Unit test for tag objects, i.e. tags with comments.
 * @author Pavel Baranchikov
 */
@RunWith(AtlassianPluginsTestRunner.class)
public class TagObjectTest extends AbstractTagTest {

    public TagObjectTest(WrappersFactory wrappersFactory) {
        super(wrappersFactory);
    }

    @Override
    protected void createTag(String tagName) {
        getWorkspace().createTag(tagName, "New tag " + tagName + " created");
    }

}
