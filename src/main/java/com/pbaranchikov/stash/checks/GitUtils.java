package com.pbaranchikov.stash.checks;

import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.scm.CommandBuilderSupport;
import com.atlassian.stash.scm.git.GitScmConfig;

/**
 * Git Utilities class.
 * @author Pavel Baranchikov
 */
public class GitUtils {

    private GitUtils() {
    }

    public static void setAlternateIfCrossRepository(CommandBuilderSupport builder,
            Repository repository, Repository secondRepository, GitScmConfig config) {
        if (!repository.getId().equals(secondRepository.getId())) {
            builder.environment("GIT_ALTERNATE_OBJECT_DIRECTORIES",
                    config.getObjectsDir(secondRepository).getAbsolutePath());
        }
    }

}
