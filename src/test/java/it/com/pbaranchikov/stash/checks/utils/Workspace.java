package it.com.pbaranchikov.stash.checks.utils;

import java.io.File;

/**
 * Interface for git workspace.
 * @author Pavel Baranchikov
 */

public interface Workspace {
    void commit(String message);

    void config(String... parameters);

    void setCrlf(String crlf);

    File commitNewFile(String filename, String contents);

    void checkout(String branchName);

    void branch(String branchName);

    void add(String filename);

    boolean push();

    boolean push(String branchName);

    /**
     * Performs push --force to the target branch from the current branch.
     * @param targetBranch branch to force push to
     * @return true on success
     */
    boolean pushForce(String targetBranch);

    boolean pushRemoval(String branchname);

    File getWorkDir();

    void commitNewContents(File targetFile, String newContents);

    /**
     * Method creates tag. Locally, not pushing it to the repository.
     * @param tagName new tag name
     */
    void createTag(String tagName);

}
