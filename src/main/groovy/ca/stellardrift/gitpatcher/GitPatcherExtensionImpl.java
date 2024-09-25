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

import javax.inject.Inject;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;

import java.util.Collections;

class GitPatcherExtensionImpl implements GitPatcherExtension {
    private final NamedDomainObjectContainer<RepoPatchDetailsImpl> patchedRepos;
    private final Property<Boolean> addAsSafeDirectory;
    private final Property<String> committerNameOverride;
    private final Property<String> committerEmailOverride;
    private final ListProperty<String> applyExtraArguments;

    @Inject
    public GitPatcherExtensionImpl(final ObjectFactory objects, final ProviderFactory providers) {
        this.patchedRepos = objects.domainObjectContainer(RepoPatchDetailsImpl.class);
        this.addAsSafeDirectory = objects.property(Boolean.class)
            .convention(
                providers.environmentVariable("GITPATCHER_ADD_GIT_SAFEDIR")
                    .map(it -> it.equals("true"))
                    .orElse(false)
            );
        this.committerNameOverride = objects.property(String.class).convention("GitPatcher");
        this.committerEmailOverride = objects.property(String.class).convention("gitpatcher@noreply");
        this.applyExtraArguments = objects.listProperty(String.class);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public NamedDomainObjectContainer<RepoPatchDetails> getPatchedRepos() {
        return (NamedDomainObjectContainer) this.patchedRepos;
    }

    @Override
    public Property<Boolean> getAddAsSafeDirectory() {
        return this.addAsSafeDirectory;
    }

    @Override
    public Property<String> getCommitterNameOverride() {
        return this.committerNameOverride;
    }

    @Override
    public Property<String> getCommitterEmailOverride() {
        return this.committerEmailOverride;
    }

    @Override
    public ListProperty<String> getApplyExtraArguments() {
        return this.applyExtraArguments;
    }
}
