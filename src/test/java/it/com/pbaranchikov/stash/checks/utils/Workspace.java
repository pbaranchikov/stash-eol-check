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

    boolean pushRemoval(String branchname);

    File getWorkDir();

    void commitNewContents(File targetFile, String newContents);

    boolean createTag(String tagName);

    boolean removeTag(String tagName);
}
