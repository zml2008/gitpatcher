/*
 * Copyright (c) 2015-2025, Stellardrift and contributors
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
package ca.stellardrift.gitpatcher.task.patch;

import ca.stellardrift.gitpatcher.internal.Git;
import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.jspecify.annotations.Nullable;

import static java.lang.System.out;

@UntrackedTask(because = "State is tracked by git")
public abstract class MakePatchesTask extends PatchTask {

    private static final Predicate<String> HUNK = it -> it.startsWith("@@");

    @Override @InputDirectory
    public abstract DirectoryProperty getRepo();

    @Override @Internal
    public Provider<RegularFile> getRefCache() { // not used in this task
        return super.getRefCache();
    }

    @Override @OutputDirectory
    public abstract DirectoryProperty getPatchDir();

    @Override @Internal
    public File@Nullable [] getPatches() {
        return super.getPatches();
    }

    {
        this.getOutputs().upToDateWhen($ -> {
            if (!this.getRepo().get().getAsFile().isDirectory()) {
                return false;
            }

            final Git git = this.getGitService().get().git().create(this.getRepo());
            return Objects.equals(this.getCachedRef(), git.getRef());
        });
    }

    @TaskAction
    void makePatches() {
        final File patchDir = this.getPatchDir().get().getAsFile();
        if (patchDir.isDirectory()) {
            final File[] patches = this.getPatches();
            if (patches != null && patches.length > 0) {
                for (final File patch : patches) {
                    if (!patch.delete()) {
                        throw new GradleException("Falied to delete old patch " + patch);
                    }
                }
            }
        } else {
            if (!patchDir.mkdirs()) {
                throw new GradleException("Failed to create patch directory");
            }
        }

        final Git git = this.getGitService().get().git().create(this.getRepo());
        final RepoState safeState = this.setupGit(git);
        try {
            git.formatPatch("--no-stat", "--zero-commit", "--full-index", "--no-signature", "-N", "-o", patchDir.getAbsolutePath(), "origin/upstream").expectSuccessSilently();

            git.setRepo(this.getRoot());
            git.add("-A", patchDir.getAbsolutePath()).writeTo(out);

            this.setDidWork(false);
            for (final File patch : this.getPatches()) {
                List<String> diff = git.diff("--no-color", "-U1", "--staged", patch.getAbsolutePath()).getLines();
                if (isUpToDate(diff)) {
                    this.getLogger().lifecycle("Skipping {} (up-to-date)", patch.getName());
                    git.reset("HEAD", patch.getAbsolutePath()).expectSuccessSilently();
                    git.checkout("--", patch.getAbsolutePath()).expectSuccessSilently();
                } else {
                    this.setDidWork(true);
                    this.getLogger().lifecycle("Generating {}", patch.getName());
                }
            }
        } finally {
            cleanUpSafeRepo(git, safeState);
        }
    }

    private static boolean isUpToDate(List<String> diff) {
        if (diff.isEmpty()) {
            return true;
        }

        if (diff.contains("--- /dev/null")) {
            return false;
        }

        // Check if there are max. 2 diff hunks (once for the hash, and once for the Git version)
        final long count = diff.stream().filter(HUNK).count();
        if (count == 0) {
            return true;
        }

        if (count > 2) {
            return false;
        }

        for (int i = 0; i < diff.size(); i++) {
            if (HUNK.test(diff.get(i))) {
                final String change = diff.get(i + 1);
                if (!change.startsWith("From", 1) && !change.startsWith("--", 1)) {
                    return false;
                }
            }
        }

        return true;
    }

}
