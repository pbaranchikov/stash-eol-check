package it.com.pbaranchikov.stash.checks;

import org.junit.runner.RunWith;

import it.com.pbaranchikov.stash.checks.utils.WrappersFactory;

/**
 * Unit test for tag objects, i.e. tags with comments.
 * @author Pavel Baranchikov
 */
@RunWith(com.atlassian.plugins.osgi.test.AtlassianPluginsTestRunner.class)
public class TagObjectTest extends AbstractTagTest {

    public TagObjectTest(WrappersFactory wrappersFactory) {
        super(wrappersFactory);
    }

    @Override
    protected void createTag(String tagName) {
        getWorkspace().createTag(tagName, "New tag " + tagName + " created");
    }

}
