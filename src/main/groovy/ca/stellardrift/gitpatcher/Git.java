/*
 * Copyright (c) 2025, Stellardrift and contributors
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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.gradle.api.GradleException;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Provider;
import org.jspecify.annotations.Nullable;

/**
 * Git invocation harness.
 *
 * <p>All unexpected exceptions will be rethrown as {@link org.gradle.api.GradleException}s.</p>
 */
public final class Git {
    private static final ExecutorService IO_EXECUTOR = Executors.newCachedThreadPool(); // todo: build service??
    private static final Logger LOGGER = Logging.getLogger(Git.class);

    private File repo;
    private @Nullable String committerNameOverride;
    private @Nullable String committerEmailOverride;

    public Git(final Provider<? extends Directory> repo) {
        this(repo.get().getAsFile());
    }

    public Git(final Directory repo) {
        this(repo.getAsFile());
    }

    public Git(final File repo) {
        this.repo = repo;
    }

    // scaffolding //

    public void setRepo(final File repo) {
        this.repo = repo;
        assert repo.exists();
    }

    public void setRepo(final Directory repo) {
        this.setRepo(repo.getAsFile());
    }

    public void setRepo(final DirectoryProperty repo) {
        this.setRepo(repo.get().getAsFile());
    }

    public void setCommitterNameOverride(final @Nullable String committerNameOverride) {
        this.committerNameOverride = committerNameOverride;
    }

    public void setCommitterEmailOverride(final @Nullable String committerEmailOverride) {
        this.committerEmailOverride = committerEmailOverride;
    }

    private void decorateEnv(final Map<String, String> env) {
        if (this.committerNameOverride != null) {
            env.put("GIT_COMMITTER_NAME", this.committerNameOverride);
        }

        if (this.committerEmailOverride != null) {
            env.put("GIT_COMMITTER_EMAIL", this.committerEmailOverride);
        }
    }

    // standard git operations //

    public String getStatus() {
        return run("status", "-z").getText();
    }

    public String getRef() {
        return run("rev-parse", "HEAD").getLines().get(0).trim();
    }

    public Command add(final String... args) {
        return this.run("add", args);
    }

    public Command am(final String... args) {
        return this.run("am", args);
    }

    public Command branch(final String... args) {
        return this.run("branch", args);
    }

    public Command clone(final String... args) {
        return this.run("clone", args);
    }

    public Command checkout(final String... args) {
        return this.run("checkout", args);
    }

    public Command config(final String... args) {
        return this.run("config", args);
    }

    public Command deleteIndex(final String... args) {
        return this.run("delete-index", args);
    }

    public Command diff(final String... args) {
        return this.run("diff", args);
    }

    public Command fetch(final String... args) {
        return this.run("fetch", args);
    }

    public Command formatPatch(final String... args) {
        return this.run("format-patch", args);
    }

    public Command remote(final String... args) {
        return this.run("remote", args);
    }

    public Command reset(final String... args) {
        return this.run("reset", args);
    }

    public Command updateIndex(final String... args) {
        return this.run("update-index", args);
    }

    public Command version(final String... args) {
        return this.run("version", args);
    }

    public Command submodule(final String... args) {
        return this.run("submodule", args);
    }


    // the actual execution operation //

    public Command run(final String subcommand, final String... input) {
        final List<String> args = new ArrayList<>(input.length + 3);
        args.addAll(List.of("git", "--no-pager", subcommand));
        args.addAll(Arrays.asList(input));

        LOGGER.info("gitpatcher: executing {}", args);
        final ProcessBuilder builder = new ProcessBuilder(args);
        this.decorateEnv(builder.environment());
        builder.directory(repo);
        try {
            return new Command(builder.start());
        } catch (final IOException ex) {
            throw new GradleException("Failed to start command '" + args + "'", ex);
        }
    }

    public static final class Command {
        private final Process process;

        private Command(final Process process) {
            this.process = process;
        }

        public int awaitCompletion() {
            int result;
            try {
                result = process.waitFor();
            } catch (final InterruptedException ex) {
                throw new GradleException("Interrupted while waiting for process completion", ex);
            }
            return result;
        }

        public void expectSuccess() {
            final int result = awaitCompletion();
            if (result != 0) {
                throw new GradleException("""
                    Process returned error code %d.
                    Invoked process: %s
                    """.formatted(result, this.process.info().commandLine().orElseThrow())
                );
            }
        }

        public void writeTo(OutputStream out) {
            consumeStream(this.process.getInputStream(), out);
            consumeStream(this.process.getErrorStream(), System.err);
            this.expectSuccess();
        }

        void forceWriteTo(OutputStream out) {
            consumeStream(this.process.getInputStream(), out);
            consumeStream(this.process.getErrorStream(), out);
            this.awaitCompletion();
        }

        public void awaitCompletionSilently() {
            consumeStream(this.process.getErrorStream(), System.err);
            this.awaitCompletion();
        }

        public void expectSuccessSilently() {
            consumeStream(this.process.getErrorStream(), System.err);
            this.expectSuccess();
        }

        public String getText() {
            consumeStream(this.process.getErrorStream(), System.err);
            final String stdout = this.readText0();
            expectSuccess();
            return stdout;
        }

        public List<String> getLines() {
            consumeStream(this.process.getErrorStream(), System.err);
            try (final BufferedReader reader = this.process.inputReader(StandardCharsets.UTF_8)) {
                final List<String> ret = reader.lines().toList();
                this.expectSuccess();
                return ret;
            } catch (final IOException ex) {
                throw new GradleException("Unable to read command output", ex);
            }
        }

        /**
         * Get process stdout no matter the process return code
         *
         * @return the standard output of the process no matter whether or not the execution was successful
         */
        public @Nullable String forceGetText() {
            consumeStream(this.process.getErrorStream(), System.err);
            final String text = this.readText0();
            return awaitCompletion() == 0 ? text : null;
        }

        private static void consumeStream(final InputStream processStream, final OutputStream target) {
            IO_EXECUTOR.submit(() -> processStream.transferTo(target));
        }

        private static final int SKIP_SIZE = 2048;

        private static void swallowStream(final InputStream swallowStream) {
            IO_EXECUTOR.submit(() -> {
                long skipped;
                do {
                    skipped = swallowStream.skip(SKIP_SIZE);
                } while (skipped == SKIP_SIZE);
                return null;
            });
        }

        ///  Read stdout with no return code validation
        private String readText0() {
            final String stdout;
            try {
                stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (final IOException ex) {
                throw new GradleException(
                    "Failed to read standard output from process '" + this.process.info().commandLine().orElseThrow() + "'",
                    ex
                );
            }
            return stdout;
        }
    }
}
