/*
 * Copyright (c) 2015-2023, Stellardrift and contributors
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

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider

import static java.lang.System.out

import ca.stellardrift.gitpatcher.Git
import ca.stellardrift.gitpatcher.task.UpdateSubmodulesTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask

@UntrackedTask(because = "State is tracked by git")
abstract class ApplyPatchesTask extends PatchTask {

    @Internal
    UpdateSubmodulesTask updateTask

    @Override @Internal
    abstract DirectoryProperty getPatchDir()

    @Override @InputFiles
    File[] getPatches() {
        return super.getPatches()
    }

    @OutputDirectory
    abstract DirectoryProperty getDestRepo()

    @Override @OutputFile
    Provider<RegularFile> getRefCache() {
        return super.getRefCache()
    }

    {
        destRepo.set(repo)
        outputs.upToDateWhen {
            if (!repo.get().asFile.directory) {
                return false
            }

            def git = new Git(repo)
            return git.status.empty && cachedRef == git.ref && cachedSubmoduleRef == updateTask.ref
        }
    }

    @TaskAction
    void applyPatches() {
        def git = new Git(submoduleRoot.get().asFile)
        def safeState = setupGit(git)
        try {
            git.branch('-f', 'upstream') >> null

            def gitDir = repo.get().dir('.git').asFile
            if (!gitDir.isDirectory() || gitDir.list().length == 0) {
                logger.lifecycle 'Creating {} repository...', repo

                assert gitDir.deleteDir()
                git.repo = root
                git.clone('--recursive', submodule.get(), repo.get().asFile.absolutePath, '-b', 'upstream') >> out
            }

            logger.lifecycle 'Resetting {}...', repo

            git.setRepo(repo)
            git.fetch('origin') >> null
            git.checkout('-B', 'master', 'origin/upstream') >> null
            git.reset('--hard') >> out

            if (!patchDir.get().asFile.directory) {
                assert patchDir.get().asFile.mkdirs(), 'Failed to create patch directory'
            }

            if ('true'.equalsIgnoreCase(git.config('commit.gpgsign').readText())) {
                logger.warn("Disabling GPG signing for the gitpatcher repository")
                git.config('commit.gpgsign', 'false') >> out
            }

            def patches = this.patches
            if (patches.length > 0) {
                logger.lifecycle 'Applying patches from {} to {}', patchDir.get().asFile, repo.get().asFile

                git.am('--abort') >>> null
                git.am('--3way', *patches.collect { it.absolutePath }) >> out

                logger.lifecycle 'Successfully applied patches from {} to {}', patchDir.get().asFile, repo.get().asFile
            }

            refCache.get().asFile.text = git.ref + '\n' + updateTask.ref
        } finally {
            cleanUpSafeRepo(git, safeState)
        }
    }

}
