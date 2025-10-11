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
import ca.stellardrift.gitpatcher.Utils;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;

import static java.lang.System.out;

import ca.stellardrift.gitpatcher.task.UpdateSubmodulesTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.jetbrains.annotations.Nullable;

@UntrackedTask(because = "State is tracked by git")
public abstract class ApplyPatchesTask extends PatchTask {
    @Internal
    public abstract UpdateSubmodulesTask getUpdateTask();

    public abstract void setUpdateTask(final UpdateSubmodulesTask task);

    @Override @Internal
    public abstract DirectoryProperty getPatchDir();

    @Override
    @InputFiles
    public File@Nullable[] getPatches() {
        return super.getPatches();
    }

    @OutputDirectory
    public abstract DirectoryProperty getDestRepo();

    @Override
    @OutputFile
    public Provider<RegularFile> getRefCache() {
        return super.getRefCache();
    }

    {
        this.getDestRepo().set(this.getRepo());
        this.getOutputs().upToDateWhen($ -> {
            if (!this.getRepo().get().getAsFile().isDirectory()) {
                return false;
            }

            final Git git = new Git(this.getRepo().get());
            return git.getStatus().isEmpty()
                && Objects.equals(this.getCachedRef(), git.getRef())
                && Objects.equals(this.getCachedSubmoduleRef(), this.getUpdateTask().getRef());
        });
    }

    @TaskAction
    void applyPatches() throws IOException {
        final File repoFile = this.getRepo().get().getAsFile();
        final Git git = new Git(this.getSubmoduleRoot());
        final RepoState safeState = this.setupGit(git);
        try {
            git.branch("-f", "upstream").awaitCompletionSilently();

            final Path rootDir = this.getRepo().get().getAsFile().toPath();
            final Path gitDir = rootDir.resolve(".git");
            if (!Files.isDirectory(gitDir) || isEmptyDir(gitDir)) {
                this.getLogger().lifecycle("Creating {} repository...", repoFile);

                if (!Utils.deleteRecursively(gitDir)) {
                    // deleteRecursively
                    throw new GradleException("Failed to delete existing patched repo.");
                }

                // remove any .gitkeep files within the tree and the directories that are within them
                git.setRepo(this.getRoot());
                final Path gitKeep = rootDir.resolve(".gitkeep");
                if (Files.deleteIfExists(gitKeep)) {
                    git.updateIndex("--assume-unchanged", gitKeep.toAbsolutePath().toString()).awaitCompletion();
                }

                Files.walkFileTree(rootDir, new FileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
                        final Path keep = dir.resolve(".gitkeep");
                        if (Files.deleteIfExists(keep)) {
                            git.deleteIndex("--assume-unchanged", keep.toAbsolutePath().toString()).awaitCompletion();
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(final Path dir, final @Nullable IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(final Path file, final IOException exc) {
                        return FileVisitResult.CONTINUE;
                    }
                });

                git.clone("--recursive", this.getSubmodule().get(), this.getRepo().get().getAsFile().getAbsolutePath(), "-b", "upstream").writeTo(out);
            }

            this.getLogger().lifecycle("Resetting {}...", repoFile);

            git.setRepo(this.getRepo());
            // reset origin url to handle cases where the project has been moved
            git.remote("set-url", "origin", this.getSubmoduleRoot().get().getAsFile().getAbsolutePath()).expectSuccessSilently();
            git.fetch("origin").expectSuccessSilently();
            git.checkout("-B", "master", "origin/upstream").expectSuccessSilently();
            git.reset("--hard").writeTo(out);

            final File patchDir = this.getPatchDir().get().getAsFile();
            if (!patchDir.isDirectory()) {
                if (patchDir.mkdirs()) {
                    throw new GradleException("Failed to create patch directory");
                }
            }

            if ("true".equalsIgnoreCase(git.config("commit.gpgsign").forceGetText())) {
                this.getLogger().warn("Disabling GPG signing for the gitpatcher repository");
                git.config("commit.gpgsign", "false").writeTo(out);
            }

            final File[] patches = this.getPatches();
            if (patches != null && patches.length > 0) {
                this.getLogger().lifecycle("Applying patches from {} to {}", this.getPatchDir().get().getAsFile(), repoFile);

                git.am("--abort").awaitCompletionSilently();
                git.am(Stream.concat(
                        Stream.of("--3way"),
                        Arrays.stream(patches).map(File::getAbsolutePath)
                    ).toArray(String[]::new))
                    .writeTo(out);

                this.getLogger().lifecycle("Successfully applied patches from {} to {}", this.getPatchDir().get().getAsFile(), repoFile);
            }

            Files.writeString(this.getRefCache().get().getAsFile().toPath(), git.getRef() + "\n" + getUpdateTask().getRef());
        } finally {
            cleanUpSafeRepo(git, safeState);
        }
    }

    static boolean isEmptyDir(final Path dir) throws IOException {
        try (final Stream<Path> children = Files.list(dir)) {
            return children.findAny().isEmpty();
        }
    }
}
