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

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.provider.Property;

public interface GitPatcherExtension {
    /**
     * Container holding the repos to patch.
     *
     * <p>Each {@link RepoPatchDetails} will
     * have a {@code apply[CapitalizedName]Patches}, {@code make[CapitalizedName]Patches},
     * and {@code update[CapitalizedName]Submodules} task.</p>
     *
     * <p>{@code applyPatches}, {@code rebuildPatches}, and {@code updateSubmodules}
     * depend on the respective tasks of all registered repos.</p>
     *
     * @return repo container
     * @since 1.1.0
     */
    NamedDomainObjectContainer<RepoPatchDetails> getPatchedRepos();

    /**
     * Whether to add the patched repo to git's safe directories list.
     *
     * @return the add as safe directory property
     * @since 1.1.0
     */
    Property<Boolean> getAddAsSafeDirectory();

    /**
     * A temporary committer name to use for applied patches.
     *
     * @return the committer name property
     * @since 1.1.0
     */
    Property<String> getCommitterNameOverride();

    /**
     * A temporary committer name to use for applied patches.
     *
     * @return the committer name property
     * @since 1.1.0
     */
    Property<String> getCommitterEmailOverride();
}
