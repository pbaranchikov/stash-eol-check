package it.com.pbaranchikov.stash.checks.utils;

/**
 * Interface for repositories in Stash.
 * @author Pavel Baranchikov;
 */
public interface Repository {
    Workspace cloneRepository() throws Exception;

    String getCloneUrl();

    void delete();

    void enableHook();

    void disableHook();

    void setHookSettings(String excludedFiles, boolean allowInherited);

    /**
     * Creates a repository fork in user's namespace.
     * @return new forked repository
     */
    Repository fork();

    boolean tryCreatePullRequest(Repository targetRepository, String branchName);
}
