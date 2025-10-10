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
package ca.stellardrift.gitpatcher;

import ca.stellardrift.gitpatcher.task.FindGitTask;
import ca.stellardrift.gitpatcher.task.UpdateSubmodulesTask;
import ca.stellardrift.gitpatcher.task.patch.ApplyPatchesTask;
import ca.stellardrift.gitpatcher.task.patch.MakePatchesTask;
import java.util.stream.Stream;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.tasks.TaskProvider;

public abstract class GitPatcher implements Plugin<Project> {
    private static final String GITPATCHER_TASK_GROUP = "gitpatcher";

    private Project project;
    private GitPatcherExtension extension;

    public Project getProject() {
        return project;
    }

    public GitPatcherExtension getExtension() {
        return extension;
    }

    static void applyGitPatcherGroup(final Task t) {
        t.setGroup(GITPATCHER_TASK_GROUP);
    }

    @Override
    public void apply(final Project p) {
        this.project = p;

        final TaskProvider<Task> rootApply = p.getTasks().register("applyPatches", GitPatcher::applyGitPatcherGroup);
        final TaskProvider<Task> rootRebuild = p.getTasks().register("makePatches", GitPatcher::applyGitPatcherGroup);
        final TaskProvider<Task> rootUpdate = p.getTasks().register("updateSubmodules", GitPatcher::applyGitPatcherGroup);

        this.extension = createExtension(p.getExtensions());

        final TaskProvider<FindGitTask> findGit = p.getTasks().register("findGit", FindGitTask.class, GitPatcher::applyGitPatcherGroup);

        getExtension().getPatchedRepos().all(r -> {
            r.getAddAsSafeDirectory().convention(getExtension().getAddAsSafeDirectory());
            r.getCommitterNameOverride().convention(getExtension().getCommitterNameOverride());
            r.getCommitterEmailOverride().convention(getExtension().getCommitterEmailOverride());

            final String capitalizedName = Utils.capitalize(r.getName());

            final TaskProvider<UpdateSubmodulesTask> updateSubmodules = p.getTasks().register("update" + capitalizedName + "Submodules", UpdateSubmodulesTask.class, upSub -> {
                upSub.setGroup(GITPATCHER_TASK_GROUP);
                upSub.dependsOn(findGit);
            });
            rootUpdate.configure(it ->  it.dependsOn(updateSubmodules));

            final TaskProvider<ApplyPatchesTask> apply = p.getTasks().register("apply" + capitalizedName + "Patches", ApplyPatchesTask.class, GitPatcher::applyGitPatcherGroup);
            rootApply.configure(it -> it.dependsOn(apply));

            TaskProvider<MakePatchesTask> rebuild = p.getTasks().register("make" + capitalizedName + "Patches", MakePatchesTask.class, it -> {
                it.setGroup(GITPATCHER_TASK_GROUP);
                it.dependsOn(findGit);
            });
            rootRebuild.configure(it -> it.dependsOn(rebuild));

            Stream.of(apply, rebuild).forEach(tP -> {
                tP.configure(t -> {
                    t.getAddAsSafeDirectory().convention(r.getAddAsSafeDirectory());
                    t.getCommitterName().convention(r.getCommitterNameOverride());
                    t.getCommitterEmail().convention(r.getCommitterEmailOverride());

                    t.getRepo().set(r.getTarget());
                    t.getRoot().set(r.getRoot());
                    t.getSubmodule().set(r.getSubmodule());
                    t.getPatchDir().set(r.getPatches());
                });
            });

            updateSubmodules.configure(it -> {
                it.getRepo().convention(r.getRoot());
                it.getSubmodule().convention(r.getSubmodule());
            });

            p.afterEvaluate(p2 -> apply.configure(it -> it.setUpdateTask(updateSubmodules.get())));
        });
    }

    @SuppressWarnings("deprecation")
    private GitPatcherExtension createExtension(final ExtensionContainer extensions) {
        final GitPatcherExtension extension = extensions.create(GitPatcherExtension.class, "gitPatcher", GitPatcherExtensionImpl.class);
        extensions.create(PatchExtension.class, "patches", PatchExtensionImpl.class, this.extension);
        return extension;
    }
}
