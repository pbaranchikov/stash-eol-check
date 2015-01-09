package it.com.pbaranchikov.stash.checks;

/**
 * Unit test for light-weight tags, i.e. tags without comments.
 * @author Pavel Baranchikov
 */
public class TagReferenceTest extends AbstractTagTest {

    @Override
    protected void createTag(String tagName) {
        getWorkspace().createTag(tagName);
    }

}
