package ut.com.pbaranchikov.stash.checks.utils;

import java.io.File;

/**
 * Interface for git workspace.
 * @author Pavel Baranchikov
 */

public interface Workspace {
    void commit(String message) throws Exception;

    void config(String... parameters) throws Exception;

    void setCrlf(String crlf) throws Exception;

    File commitNewFile(String filename, String contents) throws Exception;

    void checkout(String branchName) throws Exception;

    void branch(String branchName) throws Exception;

    void add(String filename) throws Exception;

    boolean push() throws Exception;

    boolean push(String branchName) throws Exception;

    boolean pushRemoval(String branchname) throws Exception;

    File getWorkDir();

    void commitNewContents(File targetFile, String newContents) throws Exception;
}
