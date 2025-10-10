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

import ca.stellardrift.gitpatcher.Git;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

public abstract class UpdateSubmodulesTask extends SubmoduleTask {
    private String ref;

    @Internal
    public String getRef() {
        return this.ref;
    }

    @Override
    @InputDirectory
    public abstract DirectoryProperty getRepo();

    @TaskAction
    public void updateSubmodules() {
        final Git git = new Git(this.getRepo());
        final String result = git.submodule("status", "--", this.getSubmodule().get()).getText();

        this.ref = result.substring(1, result.indexOf(' ', 1) - 1);

        if (result.startsWith(" ")) {
            this.setDidWork(false);
            return;
        }

        git.submodule("update", "--init", "--recursive").writeTo(System.out);
    }
}
