package com.pbaranchikov.stash.checks;

import java.io.IOException;

import javax.annotation.Nullable;

import com.atlassian.stash.io.LineReader;
import com.atlassian.stash.io.LineReaderOutputHandler;
import com.atlassian.stash.scm.CommandOutputHandler;

/**
 * Returns the first line of output provided by the git process.
 */
public class FirstLineOutputHandler extends LineReaderOutputHandler implements
        CommandOutputHandler<String> {

    private String sha;

    public FirstLineOutputHandler() {
        super("UTF-8");
    }

    @Nullable
    @Override
    public String getOutput() {
        return sha;
    }

    @Override
    protected void processReader(LineReader lineReader) throws IOException {
        sha = lineReader.readLine();

        // ignore the rest of the output
        String nextLine = lineReader.readLine();
        while (nextLine != null) {
            nextLine = lineReader.readLine();
        }
    }

}
