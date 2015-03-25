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

    /**
     * Method commits file without pushing it to server.
     * @param filename file name to create and commit to the local repository
     * @param contents file contents
     * @return file object
     */
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
     * Method creates tag reference. Locally, not pushing it to the repository.
     * @param tagName new tag name
     */
    void createTag(String tagName);

    /**
     * Method creates tag object. Locally, not pushing it to the repository.
     * @param tagName new tag name
     * @param comment comment to the tag
     */
    void createTag(String tagName, String comment);

}
