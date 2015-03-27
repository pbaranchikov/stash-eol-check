package it.com.pbaranchikov.stash.checks;

import org.junit.runner.RunWith;

import com.atlassian.plugins.osgi.test.AtlassianPluginsTestRunner;

import it.com.pbaranchikov.stash.checks.utils.WrappersFactory;

/**
 * Unit test for light-weight tags, i.e. tags without comments.
 * @author Pavel Baranchikov
 */
@RunWith(AtlassianPluginsTestRunner.class)
public class TagReferenceTest extends AbstractTagTest {

    public TagReferenceTest(WrappersFactory wrappersFactory) {
        super(wrappersFactory);
    }

    @Override
    protected void createTag(String tagName) {
        getWorkspace().createTag(tagName);
    }

}
