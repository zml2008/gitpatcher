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

import ca.stellardrift.gitpatcher.Git;
import ca.stellardrift.gitpatcher.task.SubmoduleTask;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.gradle.api.GradleException;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Internal;
import org.jspecify.annotations.Nullable;

public abstract class PatchTask extends SubmoduleTask {
    private static final String SAFE_DIRECTORY = "safe.directory";

    @Internal
    public abstract DirectoryProperty getRoot();

    public abstract DirectoryProperty getPatchDir();

    @Console
    public abstract Property<Boolean> getAddAsSafeDirectory();

    @Console
    public abstract Property<String> getCommitterName();

    @Console
    public abstract Property<String> getCommitterEmail();

    protected File[] getPatches() {
        final File patchDir = this.getPatchDir().get().getAsFile();
        if (!patchDir.isDirectory()) {
            return new File[0];
        }

        final File[] patchFiles = patchDir.listFiles((dir, name) -> name.endsWith(".patch"));
        if (patchFiles != null) {
            Arrays.sort(patchFiles);
        }
        return patchFiles;
    }

    @Internal
    public Provider<Directory> getSubmoduleRoot() {
        return this.getRoot().zip(this.getSubmodule(), Directory::dir);
    }

    @Internal
    Provider<Directory> getGitDir() {
        return this.getRepo().map(it -> it.dir(".git"));
    }

    Provider<RegularFile> getRefCache() {
        return this.getGitDir().map(it -> it.file(".gitpatcher_ref"));
    }

    private List<String> cachedRefs;

    private void readCache() {
        if (this.cachedRefs == null) {
            final File refCache = this.getRefCache().get().getAsFile();
            if (refCache.isFile()) {
                try (final Stream<String> lines = Files.lines(refCache.toPath())) {
                    this.cachedRefs = lines
                        .map(String::trim)
                        .filter(it -> !it.isEmpty() && !it.startsWith("#"))
                        .toList();
                } catch (final IOException ex) {
                    this.cachedRefs = List.of();
                    throw new GradleException("Unable to read ref cache", ex);
                }
            } else {
                this.cachedRefs = List.of();
            }
        }
    }

    @Internal
    String getCachedRef() {
        this.readCache();
        return this.cachedRefs.get(0);
    }

    @Internal
    String getCachedSubmoduleRef() {
        this.readCache();
        return this.cachedRefs.get(1);
    }

    protected RepoState setupGit(final Git git) {
        if (this.getCommitterName().isPresent()) {
          git.setCommitterNameOverride(this.getCommitterName().get());
        }

        if (this.getCommitterEmail().isPresent()) {
          git.setCommitterEmailOverride(this.getCommitterEmail().get());
        }

        return this.addAsSafeRepo(git);
    }

    /**
     * Maybe add the configured {@code repo} as a git safe repository.
     *
     * @return whether the repo was added by us, so should be removed at the end of the task
     */
    protected @Nullable RepoState addAsSafeRepo(Git git) {
        if (!this.getAddAsSafeDirectory().get()) {
            this.getLogger().info("Not adding submodules as safe directories due to configuration parameter being disabled");
            return null;
        }

        final List<String> safeDirs = safeDirs(git);
        final boolean hasPatched = safeDirs.contains(this.getRepo().get().getAsFile().getAbsolutePath());
        final boolean hasUpstream = safeDirs.contains(this.getSubmoduleRoot().get().getAsFile().getAbsolutePath());

       if (!hasPatched) {
            // add patched root
            git.config("--global", "--add", SAFE_DIRECTORY, this.getRepo().get().getAsFile().getAbsolutePath()).expectSuccessSilently();
       }

       if (!hasUpstream) {
            // add submodule
            git.config("--global", "--add", SAFE_DIRECTORY, this.getSubmoduleRoot().get().getAsFile().getAbsolutePath()).expectSuccessSilently();
        }

        return new RepoState(hasUpstream, hasPatched);
    }

    protected void cleanUpSafeRepo(Git git, RepoState state) {
        if (state == null) {
            return;
        }

        final List<String> safeDirs = safeDirs(git);

        boolean changed = false;
        if (!state.hadPatched()) {
            safeDirs.remove(this.getRepo().get().getAsFile().getAbsolutePath());
            changed = true;
        }

        if (!state.hadUpstream()) {
            safeDirs.remove(this.getSubmoduleRoot().get().getAsFile().getAbsolutePath());
            changed = true;
        }

        if (changed) {
            git.config("--global", "--unset-all", SAFE_DIRECTORY).awaitCompletion();
            safeDirs.forEach(it ->
                git.config("--global", "--add", SAFE_DIRECTORY, it).awaitCompletion()
            );
        }
    }

    private List<String> safeDirs(final Git git) {
        String safeDirs = git.config("--global", "--get-all", SAFE_DIRECTORY).forceGetText();
        return safeDirs == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(safeDirs.split("\n", -1)));
    }

    public record RepoState(
        boolean hadUpstream,
        boolean hadPatched
    ) {}

}
