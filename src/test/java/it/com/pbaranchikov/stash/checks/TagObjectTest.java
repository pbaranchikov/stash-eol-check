package it.com.pbaranchikov.stash.checks;

/**
 * Unit test for tag objects, i.e. tags with comments.
 * @author Pavel Baranchikov
 */
public class TagObjectTest extends AbstractTagTest {

    @Override
    protected void createTag(String tagName) {
        getWorkspace().createTag(tagName, "New tag " + tagName + " created");
    }

}
