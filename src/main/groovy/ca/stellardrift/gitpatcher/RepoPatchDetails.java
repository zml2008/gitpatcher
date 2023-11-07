/*
 * Copyright (c) 2023, Stellardrift and contributors
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

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

public interface RepoPatchDetails {
    /**
     * Get the name of this {@link RepoPatchDetails}.
     *
     * @return the name
     */
    String getName();

    /**
     * The root/project directory.
     *
     * <p>This usually does not need to be manually set.</p>
     *
     * @return the root
     * @since 2.0.0
     */
    DirectoryProperty getRoot();

    /**
     * The name of the submodule directory created.
     *
     * @return the submodule
     * @since 2.0.0
     */
    Property<String> getSubmodule();

    /**
     * The target folder for the patched repository.
     *
     * @return the target folder
     * @since 2.0.0
     */
    DirectoryProperty getTarget();

    /**
     * The folder where the patches are saved
     *
     * @return the patch directory
     * @since 2.0.0
     */
    DirectoryProperty getPatches();

    /**
     * Whether to add the patched repo to git's safe directories list.
     *
     * @return the add as safe directory property
     * @since 2.0.0
     */
    Property<Boolean> getAddAsSafeDirectory();

    /**
     * A temporary committer name to use for applied patches.
     *
     * @return the committer name property
     * @since 2.0.0
     */
    Property<String> getCommitterNameOverride();

    /**
     * A temporary committer name to use for applied patches.
     *
     * @return the committer name property
     * @since 2.0.0
     */
    Property<String> getCommitterEmailOverride();
}
