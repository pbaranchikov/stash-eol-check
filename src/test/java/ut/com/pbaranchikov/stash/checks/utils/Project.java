package ut.com.pbaranchikov.stash.checks.utils;

/**
 * Interface for projects in Stash.
 * @author Pavel Baranchikov;
 */
public interface Project {
    Repository createRepository(String key);

    void removeRepository(String key);

    Repository forceCreateRepository(String key);

    void delete();

    String getKey();

    String getUrl();
}
