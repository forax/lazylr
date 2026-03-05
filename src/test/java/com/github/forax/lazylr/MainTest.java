package com.github.forax.lazylr;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MainTest {

  private static ProcessResult runProcess(Path workingDir, Path... args) throws IOException, InterruptedException {
    var command = new ArrayList<String>();
    command.add(System.getProperty("java.home") + "/bin/java");
    command.add("-cp");
    command.add(System.getProperty("java.class.path"));
    command.add(Main.class.getName());
    for (var arg : args) {
      command.add(arg.toString());
    }

    var pb = new ProcessBuilder(command);
    pb.directory(workingDir.toFile());

    var process = pb.start();  // FIXME when moving to 26

    var runnable = new Runnable() {
      private String stderr;

      @Override
      public void run() {
        try {
          stderr = new String(process.getErrorStream().readAllBytes(), UTF_8);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    };
    var thread = Thread.ofVirtual().start(runnable);
    var stdout = new String(process.getInputStream().readAllBytes(), UTF_8);
    var exitCode = process.waitFor();
    thread.join();
    var stderr = runnable.stderr;

    return new ProcessResult(exitCode, stdout, stderr);
  }

  private record ProcessResult(int exitCode, String stdout, String stderr) {
    public ProcessResult {
      Objects.requireNonNull(stdout);
      Objects.requireNonNull(stderr);
    }
  }


  @Nested
  public class ArgumentValidation {

    @Test
    public void noArgumentsShouldPrintUsageAndExit1(@TempDir Path tempDir) throws Exception {
      var result = runProcess(tempDir);
      assertEquals(1, result.exitCode());
      assertTrue(result.stderr().contains("lazylr"));
    }

    @Test
    public void invalidNumberOfArgumentsShouldPrintUsageAndExit1(@TempDir Path tempDir) throws Exception {
      var result = runProcess(tempDir, Path.of("foo1"), Path.of("foo2"), Path.of("foo3"));
      assertEquals(1, result.exitCode());
      assertTrue(result.stderr().contains("lazylr"));
    }
  }


  @Nested
  public class GrammarFileIOErrors {

    @Test
    public void missingGrammarFileShouldExit1(@TempDir Path tempDir) throws Exception {
      var result = runProcess(tempDir, tempDir.resolve("does_not_exist.txt"));
      assertEquals(1, result.exitCode());
      assertTrue(result.stderr().contains("grammar"));
    }

    @Test
    public void grammarFileIsADirectoryShouldExit1(@TempDir Path tempDir) throws Exception {
      var dir = tempDir.resolve("adir");
      Files.createDirectory(dir);

      var result = runProcess(tempDir, dir);
      assertEquals(1, result.exitCode());
      assertTrue(result.stderr().contains("grammar"));
    }
  }


  @Nested
  public class GrammarParsingErrors {

    @Test
    public void malformedGrammarShouldExit1(@TempDir Path tempDir) throws Exception {
      var grammar = tempDir.resolve("grammar.txt");
      Files.writeString(grammar, "this is not a valid grammar at all %%%");

      var result = runProcess(tempDir, grammar);
      assertEquals(1, result.exitCode());
      assertTrue(result.stderr().contains("grammar"));
    }

    @Test
    public void grammarWithMissingGrammarBlockShouldExit1(@TempDir Path tempDir) throws Exception {
      var grammar = tempDir.resolve("grammar.txt");
      Files.writeString(grammar, """
          tokens {
            num: /[0-9]+/
          }
          """);

      var result = runProcess(tempDir, grammar);
      assertEquals(1, result.exitCode());
    }

    @Test
    public void emptyGrammarFileShouldExit1(@TempDir Path tempDir) throws Exception {
      var grammar = tempDir.resolve("grammar.txt");
      Files.writeString(grammar, "");

      var result = runProcess(tempDir, grammar);
      assertEquals(1, result.exitCode());
    }
  }


  @Nested
  public class GrammarVerifyErrors {

    @Test
    public void reduceReduceConflictShouldExit2(@TempDir Path tempDir) throws Exception {
      var grammar = tempDir.resolve("grammar.txt");
      // Two rules that both reduce to E via num — reduce/reduce conflict
      Files.writeString(grammar, """
          tokens {
            num: /[0-9]+/
          }
          grammar {
            E: A
            E: B
            A: num
            B: num
          }
          """);

      var result = runProcess(tempDir, grammar);
      assertEquals(2, result.exitCode());
      assertTrue(result.stdout().isEmpty());
      assertTrue(result.stderr().contains("conflict") || result.stderr().contains("reduce/reduce"));
    }

    @Test
    public void verifierErrorShouldNotProduceStdout(@TempDir Path tempDir) throws Exception {
      var grammar = tempDir.resolve("grammar.txt");
      Files.writeString(grammar, """
          tokens {
            num: /[0-9]+/
          }
          grammar {
            E: E '+' E
            E: num
          }
          """);

      var result = runProcess(tempDir, grammar);
      assertEquals(2, result.exitCode());
      assertTrue(result.stdout().isEmpty());
      assertTrue(result.stderr().contains("conflict") || result.stderr().contains("shift/reduce"));
    }
  }


  @Nested
  public class GrammarNoError {

    @Test
    public void grammarOnlyMinimalShouldExit0(@TempDir Path tempDir) throws Exception {
      var grammar = tempDir.resolve("grammar.txt");
      Files.writeString(grammar, """
          tokens {
            num: /[0-9]+/
          }
          grammar {
            E: num
          }
          """);

      var result = runProcess(tempDir, grammar);
      assertEquals(0, result.exitCode());
      assertTrue(result.stderr().isEmpty());
      assertEquals("""
          E:
          ○─[num]─►
          """, result.stdout());
    }

    @Test
    public void grammarShouldDisplayRailroadDiagramAndExit0(@TempDir Path tempDir) throws Exception {
      var grammar = tempDir.resolve("grammar.txt");
      Files.writeString(grammar, """
          tokens {
            num: /[0-9]+/
          }
          precedence {
            left: '+'
            left: '*'
          }
          grammar {
            E: E '+' E
            E: E '*' E
            E: num
          }
          """);

      var result = runProcess(tempDir, grammar);
      assertEquals(0, result.exitCode());
      assertTrue(result.stderr().isEmpty());
      assertEquals("""
          E:
          ○─┌─<E>──[+]──<E>─┐─►
            ├─<E>──[*]──<E>─┤
            └─[num]─────────┘
          """, result.stdout());
    }
  }


  @Nested
  public class InputFileIOErrors {

    @Test
    public void missingInputFileShouldExit1(@TempDir Path tempDir) throws Exception {
      var grammar = tempDir.resolve("grammar.txt");
      Files.writeString(grammar, """
          tokens {
            num: /[0-9]+/
          }
          grammar {
            E: num
          }
          """);
      var input = tempDir.resolve("does_not_exist.txt");

      var result = runProcess(tempDir, grammar, input);
      assertEquals(1, result.exitCode());
      assertTrue(result.stderr().contains("input"));
    }

    @Test
    public void inputFileIsADirectoryShouldExit1(@TempDir Path tempDir) throws Exception {
      var grammar = tempDir.resolve("grammar.txt");
      Files.writeString(grammar, """
          tokens {
            num: /[0-9]+/
          }
          grammar {
            E: num
          }
          """);
      var dir = tempDir.resolve("adir");
      Files.createDirectory(dir);

      var result = runProcess(tempDir, grammar, dir);
      assertEquals(1, result.exitCode());
      assertTrue(result.stderr().contains("input"));
    }
  }

  @Nested
  public class InputParsingErrors {

    @Test
    public void inputThatFailsToParseGrammarShouldExit1(@TempDir Path tempDir) throws Exception {
      var grammar = tempDir.resolve("grammar.txt");
      var input = tempDir.resolve("input.txt");
      Files.writeString(grammar, """
          tokens {
            num: /[0-9]+/
          }
          precedence {
            left: '+'
          }
          grammar {
            E: E '+' E
            E: num
          }
          """);
      // "1++2" is not valid — two consecutive operators
      Files.writeString(input, "1++2");

      var result = runProcess(tempDir, grammar, input);
      assertEquals(1, result.exitCode());
      assertTrue(result.stderr().contains("input"));
    }

    @Test
    public void inputWithUnrecognizedTokenShouldExit1(@TempDir Path tempDir) throws Exception {
      var grammar = tempDir.resolve("grammar.txt");
      var input = tempDir.resolve("input.txt");
      Files.writeString(grammar, """
          tokens {
            num: /[0-9]+/
          }
          precedence {
            left: '+'
          }
          grammar {
            E: E '+' E
            E: num
          }
          """);
      // '$' is not a recognized token
      Files.writeString(input, "1+$2");

      var result = runProcess(tempDir, grammar, input);
      assertEquals(1, result.exitCode());
      assertTrue(result.stderr().contains("input"));
    }
  }

  @Nested
  public class InputNoError {
    @Test
    public void singleTokenInputShouldProduceSingleNodeTree(@TempDir Path tempDir) throws Exception {
      var grammar = tempDir.resolve("grammar.txt");
      var input = tempDir.resolve("input.txt");
      Files.writeString(grammar, """
          tokens {
            num: /[0-9]+/
          }
          grammar {
            E: num
          }
          """);
      Files.writeString(input, "42");

      var result = runProcess(tempDir, grammar, input);
      assertEquals(0, result.exitCode());
      assertTrue(result.stderr().isEmpty());
      assertEquals("""
          └── <E>
              └── [num=42]
          """, result.stdout());
    }

    @Test
    public void operatorTokenShouldUseNameOnlyFormat(@TempDir Path tempDir) throws Exception {
      // Verifies the [+] branch (name == value) vs [num=1] branch (name != value)
      var grammar = tempDir.resolve("grammar.txt");
      var input = tempDir.resolve("input.txt");
      Files.writeString(grammar, """
          tokens {
            num: /[0-9]+/
          }
          precedence {
            left: '+'
          }
          grammar {
            E: E '+' E
            E: num
          }
          """);
      Files.writeString(input, "1+2");

      var result = runProcess(tempDir, grammar, input);
      assertEquals(0, result.exitCode());
      assertTrue(result.stderr().isEmpty());
      assertEquals("""
          └── <E>
              ├── <E>
              │   └── [num=1]
              ├── [+]
              └── <E>
                  └── [num=2]
          """, result.stdout());
    }

    @Test
    public void operatorPrecedenceIsLeftAssociative(@TempDir Path tempDir) throws Exception {
      // 1+2+4 with left-associative '+' should give ((1+2)+4), not (1+(2+4))
      var grammar = tempDir.resolve("grammar.txt");
      var input = tempDir.resolve("input.txt");
      Files.writeString(grammar, """
          tokens {
            num: /[0-9]+/
          }
          precedence {
            left: '+'
          }
          grammar {
            E: E '+' E
            E: num
          }
          """);
      Files.writeString(input, "1+2+4");

      var result = runProcess(tempDir, grammar, input);
      assertEquals(0, result.exitCode());
      assertTrue(result.stderr().isEmpty());
      assertEquals("""
          └── <E>
              ├── <E>
              │   ├── <E>
              │   │   └── [num=1]
              │   ├── [+]
              │   └── <E>
              │       └── [num=2]
              ├── [+]
              └── <E>
                  └── [num=4]
          """, result.stdout());
    }

    @Test
    public void operatorPrecedenceIsRightAssociative(@TempDir Path tempDir) throws Exception {
      // 1^2^4 with right-associative '^' should give ((1^2)^4), not (1^(2^4))
      var grammar = tempDir.resolve("grammar.txt");
      var input = tempDir.resolve("input.txt");
      Files.writeString(grammar, """
          tokens {
            num: /[0-9]+/
          }
          precedence {
            right: '^'
          }
          grammar {
            E: E '^' E
            E: num
          }
          """);
      Files.writeString(input, "1^2^4");

      var result = runProcess(tempDir, grammar, input);
      assertEquals(0, result.exitCode());
      assertTrue(result.stderr().isEmpty());
      assertEquals("""
          └── <E>
              ├── <E>
              │   └── [num=1]
              ├── [^]
              └── <E>
                  ├── <E>
                  │   └── [num=2]
                  ├── [^]
                  └── <E>
                      └── [num=4]
          """, result.stdout());
    }
  }
}