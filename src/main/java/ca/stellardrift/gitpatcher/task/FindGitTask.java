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
package ca.stellardrift.gitpatcher.task;

import ca.stellardrift.gitpatcher.internal.Git;
import ca.stellardrift.gitpatcher.internal.GitService;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.services.ServiceReference;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

@UntrackedTask(because = "Always check for Git in the environment when requested")
public abstract class FindGitTask extends DefaultTask {
    @Internal
    public abstract DirectoryProperty getRootDir();

    @ServiceReference(GitService.SERVICE_NAME)
    protected abstract Property<GitService> getGitService();

    public FindGitTask() {
        this.getRootDir().set(this.getProject().getRootDir()); // todo: pull this from ProjectLayout in 8.13+
    }

    @TaskAction
    void findGit() {
        final Git git = this.getGitService().get().git().create(this.getRootDir());
        try {
            final String version = String.join(",", git.version().getLines());
            this.getLogger().lifecycle("Using {} for patching submodules.", version);
        } catch (final Throwable ex) {
            throw new UnsupportedOperationException(
                "Failed to verify Git version. Make sure running the Gradle build in an environment where Git is in your PATH.",
                ex
            );
        }
    }

}
