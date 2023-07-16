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
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory

import javax.inject.Inject

@CompileStatic
class PatchExtension {

    File root

    String submodule

    File target

    File patches

    final Property<Boolean> addAsSafeDirectory

    final Property<String> committerNameOverride

    final Property<String> committerEmailOverride

    @Inject
    PatchExtension(final ObjectFactory objects, final ProviderFactory providers) {
        this.addAsSafeDirectory = objects.property(Boolean.class)
            .convention(
                providers.environmentVariable("GITPATCHER_ADD_GIT_SAFEDIR")
                    .map { it.equals("true") }
                    .orElse(false)
            )
        this.committerNameOverride = objects.property(String).convention("GitPatcher")
        this.committerEmailOverride = objects.property(String).convention("gitpatcher@noreply")
    }

}
