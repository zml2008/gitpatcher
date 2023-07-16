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

package ca.stellardrift.gitpatcher

import groovy.transform.CompileStatic
import ca.stellardrift.gitpatcher.task.FindGitTask
import ca.stellardrift.gitpatcher.task.UpdateSubmodulesTask
import ca.stellardrift.gitpatcher.task.patch.ApplyPatchesTask
import ca.stellardrift.gitpatcher.task.patch.MakePatchesTask
import ca.stellardrift.gitpatcher.task.patch.PatchTask
import org.gradle.api.Plugin
import org.gradle.api.Project

class GitPatcher implements Plugin<Project> {

    protected Project project
    protected PatchExtension extension

    @Override
    void apply(Project project) {
        this.project = project
        project.with {
            this.extension = extensions.create('patches', PatchExtension)
            extension.root = projectDir

            def findGit = tasks.register('findGit', FindGitTask)
            def updateSubmodules = tasks.register('updateSubmodules', UpdateSubmodulesTask) { dependsOn findGit }
            tasks.register('applyPatches', ApplyPatchesTask/*, dependsOn: 'updateSubmodules' We don't want to update the submodule if we're targeting a specific commit */)
            tasks.register('makePatches', MakePatchesTask) { dependsOn findGit }

            tasks.withType(PatchTask).configureEach {
                addAsSafeDirectory.set(extension.addAsSafeDirectory)
                committerName.set(extension.committerNameOverride)
                committerEmail.set(extension.committerEmailOverride)
            }

            afterEvaluate {
                // Configure the settings from our extension
                findGit.configure {
                    submodule = extension.submodule
                }

                configure([tasks.applyPatches, tasks.makePatches]) {
                    repo = extension.target
                    root = extension.root
                    submodule = extension.submodule
                    patchDir = extension.patches
                }

                tasks.applyPatches.updateTask = tasks.updateSubmodules

                updateSubmodules.configure {
                    repo = extension.root
                    submodule = extension.submodule
                }
            }
        }
    }

    @CompileStatic
    Project getProject() {
        return project
    }

    @CompileStatic
    PatchExtension getExtension() {
        return extension
    }

}
