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

import ca.stellardrift.gitpatcher.internal.DefaultGitFactory;
import ca.stellardrift.gitpatcher.internal.Git;
import ca.stellardrift.gitpatcher.internal.GitFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.kyori.mammoth.test.TestContext;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class GitPatcherTest {
    private static final Logger LOGGER = Logging.getLogger(GitPatcherTest.class);
    private static final String OPT_PROTOCOL_FILE_ALLOW = "protocol.file.allow";
    private static final String OPT_VALUE_ALWAYS = "always";
    private final GitFactory git = new DefaultGitFactory();

    @AfterEach
    void cleanUp() throws Exception {
        this.git.close();
    }

    @GitPatcherFunctionalTest
    @DisplayName("pluginSimplyApplies")
    void testPluginSimplyApplies(final TestContext ctx) throws IOException {
        ctx.copyInput("build.gradle");
        ctx.copyInput("settings.gradle");

        assertDoesNotThrow(() -> ctx.build("help"));
    }

    Path createTestingRepo(final TestContext ctx, final Path tempDir) throws IOException {
        final Path repo = tempDir.resolve("patchable-repo");
        final Git git = createTestingGit(repo);
        git.run("init").expectSuccess();
        Files.writeString(repo.resolve("apple.txt"), this.readResourceText("singleRepo/in/apple.txt"), StandardCharsets.UTF_8);
        Files.writeString(repo.resolve("ball.txt"), this.readResourceText("singleRepo/in/ball.txt"), StandardCharsets.UTF_8);
        Files.writeString(repo.resolve("cat.txt"), this.readResourceText("singleRepo/in/cat.txt"), StandardCharsets.UTF_8);
        git.add("apple.txt", "ball.txt", "cat.txt").writeToLog();
        git.run("commit", "-m", "initial commit").expectSuccess();

        return repo;
    }

    @GitPatcherFunctionalTest
    @DisplayName("singleRepo")
    void testSingleRepo(final TestContext ctx, @TempDir final Path upstreamDir) throws IOException {
        // init project
        final Git projectGit = this.git.create(ctx.outputDirectory(), LOGGER);
        ctx.copyInput("build.gradle");
        ctx.copyInput("settings.gradle");
        final Path upstreamRepo = this.createTestingRepo(ctx, upstreamDir);
        final String previousFileProtocolState = projectGit.config("--global", "--get", OPT_PROTOCOL_FILE_ALLOW).forceGetText();
        if (!OPT_VALUE_ALWAYS.equals(previousFileProtocolState)) {
            projectGit.config("--global", OPT_PROTOCOL_FILE_ALLOW, OPT_VALUE_ALWAYS).expectSuccess();
        }

        try {
            projectGit.run("init").expectSuccess();
            projectGit.submodule("add", upstreamRepo.toAbsolutePath().toString(), "upstream/").writeToLog();
            projectGit.run("commit", "-m", "initial commit").expectSuccess();

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
        } finally {
            if (!OPT_VALUE_ALWAYS.equals(previousFileProtocolState)) {
                if (previousFileProtocolState == null) {
                    projectGit.config("--global", "--unset", OPT_PROTOCOL_FILE_ALLOW).expectSuccess();
                } else {
                    projectGit.config("--global", OPT_PROTOCOL_FILE_ALLOW, previousFileProtocolState).expectSuccess();
                }
            }
        }
    }

    Git createTestingGit(final Path repo) throws IOException {
        Files.createDirectories(repo);
        final Git ret = this.git.create(repo, LOGGER);

        ret.setCommitterNameOverride("gitpatcher");
        ret.setCommitterEmailOverride("gitpatcher@localhost");
        ret.setAuthorNameOverride("gitpatcher");
        ret.setAuthorEmailOverride("gitpatcher@localhost");

        return ret;
    }

    String readResourceText(final String resourceName) throws IOException {
        try (final InputStream is = this.getClass().getResourceAsStream(resourceName)) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
