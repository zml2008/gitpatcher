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
package ca.stellardrift.gitpatcher

import groovy.transform.CompileStatic
import ca.stellardrift.gitpatcher.task.FindGitTask
import ca.stellardrift.gitpatcher.task.UpdateSubmodulesTask
import ca.stellardrift.gitpatcher.task.patch.ApplyPatchesTask
import ca.stellardrift.gitpatcher.task.patch.MakePatchesTask
import ca.stellardrift.gitpatcher.task.patch.PatchTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

class GitPatcher implements Plugin<Project> {

    protected Project project
    protected GitPatcherExtension extension

    @Override
    void apply(Project project) {
        this.project = project

        def rootApply = project.tasks.register("applyPatches")
        def rootRebuild = project.tasks.register("makePatches")
        def rootUpdate = project.tasks.register("updateSubmodules")

        project.with {
            this.extension = extensions.create(GitPatcherExtension, 'gitPatcher', GitPatcherExtensionImpl)

            def findGit = tasks.register('findGit', FindGitTask)

            extension.patchedRepos.all { RepoPatchDetails r ->
                r.addAsSafeDirectory.convention(extension.addAsSafeDirectory)
                r.committerNameOverride.convention(extension.committerNameOverride)
                r.committerEmailOverride.convention(extension.committerEmailOverride)

                def capitalizedName = r.name.capitalize()

                def updateSubmodules = tasks.register('update' + capitalizedName + 'Submodules', UpdateSubmodulesTask) { dependsOn findGit }
                rootUpdate.configure { dependsOn(updateSubmodules) }

                def apply = tasks.register('apply' + capitalizedName  +'Patches', ApplyPatchesTask/*, dependsOn: 'updateSubmodules' We don't want to update the submodule if we're targeting a specific commit */)
                rootApply.configure { dependsOn(apply) }

                def rebuild = tasks.register('make' + capitalizedName + 'Patches', MakePatchesTask) { dependsOn findGit }
                rootRebuild.configure { dependsOn(rebuild) }

                // groovy moment?
                List<TaskProvider<? extends PatchTask>> patchTasks = new ArrayList<>()
                patchTasks.add(apply)
                patchTasks.add(rebuild)

                patchTasks.each { taskProvider ->
                    taskProvider.configure {
                        addAsSafeDirectory.convention(r.addAsSafeDirectory)
                        committerName.convention(r.committerNameOverride)
                        committerEmail.convention(r.committerEmailOverride)

                        repo.set(r.target)
                        root.set(r.root)
                        submodule.set(r.submodule)
                        patchDir.set(r.patches)
                    }
                }

                updateSubmodules.configure {
                    repo.convention(r.root)
                    submodule.convention(r.submodule)
                }

                afterEvaluate {
                    apply.configure { updateTask = updateSubmodules.get() }
                }
            }
        }
    }

    @CompileStatic
    Project getProject() {
        return project
    }

    @CompileStatic
    GitPatcherExtension getExtension() {
        return extension
    }

}
