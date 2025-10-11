/*
 * Copyright (c) 2023-2025, Stellardrift and contributors
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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.kyori.mammoth.test.TestContext;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class GitPatcherTest {
    @GitPatcherFunctionalTest
    @DisplayName("pluginSimplyApplies")
    void testPluginSimplyApplies(final TestContext ctx) throws IOException {
        ctx.copyInput("build.gradle");
        ctx.copyInput("settings.gradle");

        assertDoesNotThrow(() -> ctx.build("help"));
    }

    @GitPatcherFunctionalTest
    @DisplayName("singleRepo")
    void testSingleRepo(final TestContext ctx) throws IOException {
        // init project
        ctx.copyInput("build.gradle");
        ctx.copyInput("settings.gradle");
        new Git(ctx.outputDirectory()).run("init").expectSuccess();

        final Path upstream = ctx.outputDirectory().resolve("upstream");
        final Git git = createTestingGit(upstream);
        git.run("init").expectSuccess();
        ctx.copyInput("apple.txt", "upstream/apple.txt");
        ctx.copyInput("ball.txt", "upstream/ball.txt");
        ctx.copyInput("cat.txt", "upstream/cat.txt");
        git.add("apple.txt", "ball.txt", "cat.txt").expectSuccess();
        git.run("commit", "-m", "initial commit").expectSuccess();

        // deposit patches
        ctx.writeText("patches/0001-foo.patch", this.readResourceText("singleRepo/out/0001-foo.patch"));
        ctx.writeText("patches/0002-bar.patch", this.readResourceText("singleRepo/out/0002-bar.patch"));

        // then fire away
        ctx.build("applyPatches");
        // check that the files match what is expected
        ctx.assertOutputEquals("apple.txt", "patched/apple.txt");
        ctx.assertOutputEquals("cat-patched.txt", "patched/cat.txt");
        assertFalse(Files.exists(ctx.outputDirectory().resolve("patched/ball.txt")));
        ctx.assertOutputEquals("dog-patched.txt", "patched/dog.txt");

        // and make sure we are ok here
        ctx.build("makePatches");
        ctx.assertOutputEquals("0001-foo.patch", "patches/0001-foo.patch");
        ctx.assertOutputEquals("0002-bar.patch", "patches/0002-bar.patch");
    }

    Git createTestingGit(final Path repo) throws IOException {
        Files.createDirectories(repo);
        final Git ret = new Git(repo.toFile());

        ret.setCommitterNameOverride("gitpatcher");
        ret.setCommitterEmailOverride("gitpatcher@localhost");

        return ret;
    }

    String readResourceText(final String resourceName) throws IOException {
        try (final InputStream is = this.getClass().getResourceAsStream(resourceName)) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
