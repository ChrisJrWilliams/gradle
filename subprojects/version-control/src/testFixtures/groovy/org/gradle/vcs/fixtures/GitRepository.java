/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.vcs.fixtures;

import com.google.common.collect.Lists;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.gradle.api.Named;
import org.gradle.internal.UncheckedException;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.util.GFileUtils;
import org.junit.rules.ExternalResource;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

public class GitRepository extends ExternalResource implements Named {
    private final String repoName;
    private final File parentDirectory;
    private Git git;

    public GitRepository(String repoName, File parentDirectory) {
        this.repoName = repoName;
        this.parentDirectory = parentDirectory;
    }

    public GitRepository(File parentDirectory) {
        this("repo", parentDirectory);
    }

    /**
     * Returns the underlying Git object from JGit.
     *
     * Please consider adding more convenience methods to this fixture over using "raw" Git APIs.
     */
    public Git getGit() {
        return git;
    }

    @Override
    public String getName() {
        return repoName;
    }

    /**
     * Factory method for creating a GitRepository without using a JUnit @Rule.
     *
     * Creates a repository in the given repoDir.
     */
    public static GitRepository init(File repoDir) throws GitAPIException {
        GitRepository repo = new GitRepository(repoDir);
        repo.createGitRepo(repoDir);
        return repo;
    }

    @Override
    protected void before() throws Throwable {
        createGitRepo(new File(parentDirectory, repoName));
    }

    private void createGitRepo(File repoDir) throws GitAPIException {
        git = Git.init().setDirectory(repoDir).call();
    }

    @Override
    protected void after() {
        close();
    }

    /**
     * Clean up any held resources
     * (called automatically when used as a @Rule)
     */
    public void close() {
        git.close();
    }

    public RevCommit addSubmodule(GitRepository submoduleRepo) throws GitAPIException {
        Repository submodule = null;
        try {
            submodule = git.submoduleAdd().
                setURI(submoduleRepo.getWorkTree().toString()).
                setPath(submoduleRepo.getName()).
                call();
            return commit("add submodule " + submoduleRepo.getName(), submoduleRepo.getName());
        } finally {
            if (submodule != null) {
                submodule.close();
            }
        }
    }

    /**
     * Updates any submodules in this repository to the latest in the submodule origin repository
     */
    public RevCommit updateSubmodulesToLatest() throws GitAPIException {
        List<String> submodulePaths = Lists.newArrayList();
        try {
            SubmoduleWalk walker = SubmoduleWalk.forIndex(git.getRepository());
            try {
                while (walker.next()) {
                    Repository submodule = walker.getRepository();
                    try {
                        submodulePaths.add(walker.getPath());
                        Git.wrap(submodule).pull().call();
                    } finally {
                        submodule.close();
                    }
                }
            } finally {
                walker.close();
            }
            return commit("update submodules", submodulePaths.toArray(new String[submodulePaths.size()]));
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    /**
     * Commits changes to the given paths.  The paths are passed as-is to the underlying JGit library.
     */
    public RevCommit commit(String message, String... paths) throws GitAPIException {
        AddCommand add = git.add();
        for (String path : paths) {
            add.addFilepattern(path);
        }
        add.call();
        return git.commit().setMessage(message).call();
    }

    /**
     * Commits all changes in the working tree. This is approximately {@code git commit -am "message"}
     */
    public RevCommit commit(String message) throws GitAPIException {
        // Commit all changes in the working tree
        AddCommand add = git.add();
        for (File file : GFileUtils.listFiles(getWorkTree(), null, true)) {
            add.addFilepattern(relativePath(file));
        }
        add.call();
        return git.commit().setMessage(message).call();
    }

    public Ref createBranch(String branchName) throws GitAPIException {
        return git.branchCreate().setName(branchName).call();
    }

    public Ref createLightWeightTag(String tagName) throws GitAPIException {
        return git.tag().setName(tagName).call();
    }

    public Ref createAnnotatedTag(String tagName, String message) throws GitAPIException {
        return git.tag().setName(tagName).setAnnotated(true).setMessage(message).call();
    }

    public Ref getHead() throws IOException {
        return git.getRepository().findRef("HEAD");
    }

    public TestFile getWorkTree() {
        return new TestFile(git.getRepository().getWorkTree());
    }

    public TestFile file(Object... path) {
        return getWorkTree().file(path);
    }

    public URI getUrl() throws URISyntaxException {
        return getWorkTree().toURI();
    }

    private String relativePath(File file) {
        try {
            return getUrl().relativize(file.toURI()).toString();
        } catch (URISyntaxException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public String getId() {
        try {
            return "git-repo:" + getUrl().toASCIIString();
        } catch (URISyntaxException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
