/*
 * Copyright (c) 2015, Minecrell <https://github.com/Minecrell>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package ca.stellardrift.gitpatcher.task.patch

import groovy.transform.CompileStatic
import groovy.transform.Immutable
import ca.stellardrift.gitpatcher.Git
import ca.stellardrift.gitpatcher.task.SubmoduleTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Console
import org.gradle.api.tasks.Internal

abstract class PatchTask extends SubmoduleTask {

    private static final String SAFE_DIRECTORY = "safe.directory"

    @Internal
    File root

    File patchDir

    @Console
    public abstract Property<Boolean> getAddAsSafeDirectory()

    @Console
    public abstract Property<String> getCommitterName()

    @Console
    public abstract Property<String> getCommitterEmail()

    protected File[] getPatches() {
        if (!patchDir.directory) {
            return new File[0]
        }

        return patchDir.listFiles({ dir, name -> name.endsWith('.patch') } as FilenameFilter).sort()
    }

    @Internal
    File getSubmoduleRoot() {
        return new File(root, submodule)
    }

    @Internal
    File getGitDir() {
        return new File(repo, '.git')
    }

    File getRefCache() {
        return new File(gitDir, '.gitpatcher_ref')
    }

    private List<String> cachedRefs

    @CompileStatic
    private void readCache() {
        if (cachedRefs == null) {
            File refCache = this.refCache
            if (refCache.file) {
                this.cachedRefs = refCache.readLines().findResults {
                    def trimmed = it.trim()
                    !trimmed.empty && !trimmed.startsWith('#') ? trimmed : null
                }.asList().asImmutable()
            } else {
                this.cachedRefs = Collections.emptyList()
            }
        }
    }

    @Internal
    String getCachedRef() {
        readCache()
        return cachedRefs[0]
    }

    @Internal
    String getCachedSubmoduleRef() {
        readCache()
        return cachedRefs[1]
    }

    protected RepoState setupGit(final Git git) {
        if (this.committerName.isPresent()) {
          git.committerNameOverride = this.committerName.get()
        }

        if (this.committerEmail.isPresent()) {
          git.committerEmailOverride = this.committerEmail.get()
        }

        return this.addAsSafeRepo(git)
    }

    /**
     * Maybe add the configured {@code repo} as a git safe repository.
     *
     * @return whether the repo was added by us, so should be removed at the end of the task
     */
    protected RepoState addAsSafeRepo(Git git) {
        if (!this.addAsSafeDirectory.get()) {
            this.getLogger().info("Not adding submodules as safe directories due to configuration parameter being disabled")
            return null
        }

        def safeDirs = safeDirs(git)
        def hasPatched = safeDirs.contains(repo.absolutePath)
        def hasUpstream = safeDirs.contains(this.submoduleRoot.absolutePath)

       if (!hasPatched) {
            // add patched root
            git.config('--global', '--add', SAFE_DIRECTORY, repo.absolutePath) >> null
       }

       if (!hasUpstream) {
            // add submodule
            git.config('--global', '--add', SAFE_DIRECTORY, this.submoduleRoot.absolutePath) >> null
        }

        return new RepoState(hasUpstream, hasPatched)
    }

    protected void cleanUpSafeRepo(Git git, RepoState state) {
        if (state == null) {
            return
        }

        def safeDirs = safeDirs(git)

        def changed = false
        if (!state.hadPatched) {
            safeDirs.remove(repo.absolutePath)
            changed = true
        }

        if (!state.hadUpstream) {
            safeDirs.remove(this.submoduleRoot.absolutePath)
            changed = true
        }

        if (changed) {
            git.config('--global', '--unset-all', SAFE_DIRECTORY)
            safeDirs.each {
                git.config('--global', '--add', SAFE_DIRECTORY, it)
            }
        }
    }

    private List<String> safeDirs(final Git git) {
        String safeDirs = git.config('--global', '--get-all', SAFE_DIRECTORY).readText()
        return safeDirs == null ? [] : safeDirs.split('\n').toList()
    }

    @Immutable
    static class RepoState {
        boolean hadUpstream
        boolean hadPatched
    }

}
