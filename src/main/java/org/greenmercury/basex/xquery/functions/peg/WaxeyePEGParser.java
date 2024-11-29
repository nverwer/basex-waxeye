package org.greenmercury.basex.xquery.functions.peg;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.basex.query.QueryException;
import org.greenmercury.smax.Balancing;
import org.greenmercury.smax.SmaxDocument;
import org.greenmercury.smax.SmaxElement;
import org.waxeye.ast.IAST;
import org.waxeye.ast.IASTVisitor;
import org.waxeye.ast.IChar;
import org.waxeye.ast.IEmpty;
import org.waxeye.ast.Position;
import org.waxeye.input.InputBuffer;
import org.waxeye.parser.ParseError;
import org.waxeye.parser.ParseResult;
import org.waxeye.parser.Parser;

/**
 * A SMAX document transformer that uses a Parsing ExpressionGrammar to insert markup around non-terminals specified by a grammar.
 * This is used in the <code>peg:waxeye-peg-parser</code> function.
 *<p>
 * The transformer takes the following parameters:
 * <ul>
 *   <li>grammar A string or URL containing the grammar, see https://waxeye.org/manual.
 *   </li>
 *   <li>options A map with options. The following options are recognized:
 *     <ul>
 *       <li>modular Set to true if the grammar is modular [https://waxeye.org/manual#_modular_grammars]. (Default is false.)</li>
 *       <li>complete-match Set to true if the complete input text must be parsed as one matched fragment. (Default is false.)</li>
 *       <li>adjacent-matches Set to true if the complete input must be consumed as adjacent matched fragments. (Default is false.)</li>
 *       <li>match-whole-words Set to true to only match whole words. (Default is false.)</li>
 *       <li>parse-errors Set to true to include errors in the output and not trigger an exception. (Default is false.)</li>
 *       <li>normalize Set to true if characters in the input must be converted to low ASCII characters, removing diacritics and ligatures. (Default is false.)</li>
 *       <li>show-parse-tree Not yet implemented. Set to true to show the parse tree in an XML comment in the output. (Default is false.)</li>
 *     </ul>
 *   </li>
 * </ul>
 *<p>
 * If `complete-match` is true, `adjacent-matches` is ignored because there must be only one match.
 * If `adjacent-matches` is true, there may be multiple adjacent matched fragments, but no unmatched text.
 * If `parse-errors` is true, errors are represented by <fn:error> elements in the "http://www.w3.org/2005/xpath-functions" namespace.
 * If both `complete-match` and `adjacent-matches` are false, the result is a mix of unmatched text and an arbitrary number of matched fragments.
 * In this case, no parsing errors will be generated, and `parse-errors` is ignored.
 */
public class WaxeyePEGParser
{

  private static final String FN_NS_URI = "http://www.w3.org/2005/xpath-functions";

  private Logger logger;

  private static Path workDir; // Work directory for all waxeye parsers.
  private static String waxeyePath = "waxeye"; // Path to the Waxeye executable. Waxeye should be installed on the host system.

  private String internalName; // An internal name for the grammar, used for a copy of the grammar in a local file.

  private boolean modular;
  private boolean completeMatch;
  private boolean adjacentMatches;
  private boolean matchWholeWords;
  private boolean parseErrors;
  private boolean showParseTree;
  private boolean normalize;

  private Parser<?> parser;


  public WaxeyePEGParser(URL grammar, Map<String, String> options, Logger logger)
  {
    initFirst(options, logger);
    try
    {
      readGrammar(grammar);
    }
    catch (IOException e)
    {
      logger.error("Grammar from URL ["+grammar+"] cannot be written to file: "+e.getMessage());
      throw new RuntimeException(e);
    }
    catch (QueryException e)
    {
      logger.error("Grammar from URL ["+grammar+"] cannot be processed: "+e.getMessage());
      throw new RuntimeException(e);
    }
  }


  public WaxeyePEGParser(String grammar, Map<String, String> options, Logger logger)
  {
    initFirst(options, logger);
    try
    {
      readGrammar(grammar);
    }
    catch (IOException e)
    {
      logger.error("Grammar in string cannot be written to file: "+e.getMessage());
      throw new RuntimeException(e);
    }
    catch (QueryException e)
    {
      logger.error("Grammar cannot be processed: "+e.getMessage());
      throw new RuntimeException(e);
    }
  }


  /**
   * Initialization actions for all constructors.
   * @param options
   * @param logger
   */
  private void initFirst(Map<String, String> options, Logger logger)
  {
    this.logger = logger;
    this.modular = getOption(options, "modular", false);
    this.completeMatch = getOption(options, "complete-match", false);
    this.adjacentMatches = getOption(options, "adjacent-matches", false);
    this.matchWholeWords = getOption(options, "match-whole-words", false);
    this.parseErrors = getOption(options, "parse-errors", false);
    this.showParseTree = getOption(options, "show-parse-tree", false);
    this.normalize = getOption(options, "normalize", false);
    // Make a random internal name, used in the filename for a local copy of the grammar.
    Random random = new Random();
    this.internalName = "G" + random.ints(48, 123)
        .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
        .limit(8)
        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
        .toString();
    // Create a temporary directory, if that has not yet been done.
    synchronized (this.getClass()) {
      if (workDir == null) {
        try
        {
          workDir = Files.createTempDirectory("waxeye");
        }
        catch (IOException e)
        {
          logger.error("Work directory for "+this.getClass().getName()+" cannot be created: "+e.getMessage());
          throw new RuntimeException(e);
        }
      }
    }
  }


  private boolean getOption(Map<String, String> options, String key, boolean defaultValue) {
    return Optional.ofNullable(options.get(key)).map(v -> Boolean.parseBoolean(v)).orElse(defaultValue);
  }


  private void readGrammar(String grammar) throws IOException, QueryException
  {
    File grammarFile = workDir.resolve(this.internalName+".waxeye").toFile();
    try (
      PrintWriter grammarWriter = new PrintWriter(grammarFile.getAbsolutePath());
    ) {
      grammarWriter.print(grammar);
    }
    readGrammar(grammarFile);
  }


  private void readGrammar(URL grammar) throws IOException, QueryException
  {
    String grammarFilePath = grammar.toString();
    if (grammarFilePath.startsWith("file:/")) {
      if (grammarFilePath.matches("^file:/+[A-Za-z]:/.*")) {
        // Windows: file:///C:/path => C:/path
        grammarFilePath = grammarFilePath.replaceAll("^file:/+", "");
      } else {
        // Unix: file:///path => /path
        grammarFilePath = grammarFilePath.replaceAll("^file:/+", "/");
      }
      readGrammar(new File(grammarFilePath));
    } else {
      File grammarFile = workDir.resolve(this.internalName+".waxeye").toFile();
      try (
        InputStream grammarStream = grammar.openStream();
        OutputStream grammarFileStream = new FileOutputStream(grammarFile.getAbsolutePath());
      ) {
        grammarStream.transferTo(grammarFileStream);
      }
      readGrammar(grammarFile);
    }
  }


  private synchronized void readGrammar(File grammar) throws IOException, QueryException
  {
    String grammarFilePath = grammar.getAbsolutePath();
    String javaDirName = grammarFilePath.replaceFirst("\\.[^./]*$", "").replaceAll("[^\\./_A-Za-z0-9]", "_");
    File javaCodeDir = workDir.resolve(javaDirName).toFile();
    javaCodeDir.mkdirs();
    compileGrammar(grammarFilePath, javaCodeDir);
    this.parser = loadParser(javaCodeDir);
  }


  /**
   * Compile the Waxeye grammar into Java code using the Waxeye executable.
   * This produces .java source-code files.
   * @param grammarFilePath
   * @param javaCodeDir
   * @throws IOException
   * @throws MalformedURLException
   * @throws QueryException
   */
  private void compileGrammar(String grammarFilePath, File javaCodeDir)
      throws IOException, MalformedURLException, QueryException {
    String javaCodeDirPath = javaCodeDir.getAbsolutePath();
    if (!javaCodeDir.mkdirs() && !javaCodeDir.exists()) {
      throw new IOException("Unable to create directory ["+javaCodeDirPath+"] for Waxeye java files.");
    }
    /* Compile the grammar into Java code. */
    // The String[] waxeyeCommand must not contain empty strings, which will give an empty argument on OSX.
    String[] waxeyeCommand =
        modular ? new String[]{waxeyePath, "-g", "java", javaCodeDirPath, "-m", grammarFilePath}
                : new String[]{waxeyePath, "-g", "java", javaCodeDirPath,       grammarFilePath};
    logger.info("Compiling waxeye grammar: "+String.join(" ", waxeyeCommand));
    // String that collects output from the waxeye process.
    StringBuilder waxeyeOutput = new StringBuilder();
    BufferedReader waxeyeOutputReader = null;
    Process waxeyeProcess = null;
    try {
      waxeyeProcess = new ProcessBuilder(waxeyeCommand).redirectErrorStream(true).start();
      waxeyeOutputReader = new BufferedReader(new InputStreamReader(waxeyeProcess.getInputStream()));
      waxeyeProcess.waitFor();
      for (String line = waxeyeOutputReader.readLine(); line != null; line = waxeyeOutputReader.readLine()) {
        waxeyeOutput.append(line + "\n");
      }
      if (waxeyeProcess.exitValue() != 0) {
        throw new QueryException("Waxeye process exited with error code: "+waxeyeProcess.exitValue());
      }
    } catch (Throwable ex) {
      logger.error("Error compiling waxeye grammar ["+grammarFilePath+"]:\n"+waxeyeOutput.toString());
      throw new QueryException("Error compiling waxeye grammar ["+grammarFilePath+"]: "+ex.getMessage()+". See the log file for details.");
    } finally {
        if (waxeyeProcess != null) {
          waxeyeProcess.destroyForcibly();
        }
        if (waxeyeOutputReader != null) {
          waxeyeOutputReader.close();
        }
    }
    logger.info(waxeyeOutput.toString());
  }


  /**
   * Compile the Java files for the grammar and loads the class-files.
   * It then returns an instance of the Parser class.
   * @param javaCodeDir
   * @return a Parser<?> instance.
   * @throws QueryException
   * @throws MalformedURLException
   * @throws IOException
   */
  private Parser<?> loadParser(File javaCodeDir)
    throws QueryException, MalformedURLException, IOException
  {
    /* Compile the Java files into a class. */
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    try (
      StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);
    )
    {
      Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjects(javaCodeDir.toPath().resolve("Parser.java"), javaCodeDir.toPath().resolve("Type.java"));
      List<String> options = new ArrayList<>();
      //List<String> classpathEntries;
      //options.add("-classpath");
      //options.add(String.join(System.getProperty("path.separator"), classpathEntries));
      JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits);
      boolean success = task.call(); // see https://34codefactory.medium.com/java-how-to-dynamically-compile-and-load-external-java-classes-code-factory-dd517eec9b3
      fileManager.close();
      if (!success) {
        throw new QueryException("Some generated Java files had compilation errors.");
      }
      URLClassLoader urlClassLoader = URLClassLoader.newInstance(new URL[] {javaCodeDir.toURI().toURL()});
      Parser<?> parser = (Parser<?>) urlClassLoader.loadClass("Parser").getConstructor().newInstance();
      //Parser<?> parser = (Parser<?>) Class.forName("Parser").getConstructor().newInstance();
      return parser;
    }
    catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
        | NoSuchMethodException | SecurityException | ClassNotFoundException e)
    {
      throw new QueryException("Cannot load PEG parser ("+e.getClass().getName()+"): "+e.getMessage());
    }
  }


  /**
   * Scan a SMAX document for parsing matches.
   * @param smaxDocument
   * @throws QueryException
   */
  public void scan(SmaxDocument smaxDocument) throws QueryException
  {
    CharSequence textFragment = smaxDocument.getContent();
    parser.setEofCheck(completeMatch);
    boolean allowUnmatchedText = !(completeMatch || adjacentMatches);
    final InputBuffer input;
    if (normalize) {
      // The character positions in fragment and input must be the same.
      input = new InputBuffer(StringUtils.charSequenceToCharArray(StringUtils.normalizeOneToOne(textFragment)));
    } else {
      input = new InputBuffer(StringUtils.charSequenceToCharArray(textFragment));
    }
    int textPosition = 0;
    int textEnd = textFragment.length();
    StringBuilder unmatched = new StringBuilder(); // Collects unmatched characters, up to the next match.
    while (textPosition < textEnd) {
      // Skip spaces if unmatched text is allowed and only whole words are matched.
      if (allowUnmatchedText && matchWholeWords) {
        while (textPosition < textEnd && Character.isWhitespace(textFragment.charAt(textPosition))) {
          unmatched.append(textFragment.charAt(textPosition++));
        }
      }
      if (textPosition < textEnd) {
        // Match the input from textPosition.
        input.setPosition(textPosition);
        long startTime = new Date().getTime();
        // This is where the parser does its work.
        final ParseResult<?> parseResult = parser.parse(input);
        // Parse errors are significant if completeMatch or adjacentMatches.
        if (!allowUnmatchedText && parseResult.getError() != null) {
          // There was a parse error.
          if (parseErrors) {
            new XmlVisitor(parseResult, textFragment, textPosition, smaxDocument);
          } else {
            String message = "Parser error: "+parseResult.getError().toString()+"\n"+
                "Parsing ["+textFragment.subSequence(textPosition, Math.min(textFragment.length(), textPosition+12))+"]";
            throw new QueryException(message);
          }
        } else {
          boolean hasNonEmptyParseTree = parseResult.getAST() != null && parseResult.getAST().getChildren().size() > 0;
          int nextPosition = hasNonEmptyParseTree ? parseResult.getAST().getPosition().getEndIndex() : textEnd;
          boolean nextCharacterInWord = nextPosition < textEnd && Character.isLetterOrDigit(textFragment.charAt(nextPosition));
          if (hasNonEmptyParseTree && (!matchWholeWords || !nextCharacterInWord)) {
            // Insert XML elements for a non-empty match.
            handleText(unmatched);
            if (showParseTree) {
              long milliSeconds = new Date().getTime() - startTime;
              String parseTree = parseResult.toString();
              insertComment("Parsing took " + milliSeconds + " ms.\n" + parseTree);
            }
            new XmlVisitor(parseResult, textFragment, textPosition, smaxDocument);
            textPosition = nextPosition;
          } else if (allowUnmatchedText) {
            // Skip one character if there is an ignored error or empty match.
            char unmatchedChar = textFragment.charAt(textPosition++);
            unmatched.append(unmatchedChar);
            // If only whole words are matched, and the current character was part of a word, skip the rest of the word.
            if (matchWholeWords && Character.isLetterOrDigit(unmatchedChar)) {
              while (textPosition < textEnd
                  && Character.isLetterOrDigit(textFragment.charAt(textPosition))) {
                unmatched.append(textFragment.charAt(textPosition++));
              }
            }
          } else {
            // There is no good match possible, skip to the end.
            textPosition = textEnd;
          }
        }
      }
    }
    handleText(unmatched);
  }


  private void handleText(StringBuilder sb) {
    if (sb.length() > 0) {
      sb.delete(0, sb.length());
    }
  }


  private void insertComment(String comment) {
    // Implementation requires SMAX support.
  }


  /**
   * The XmlVisitor processes the parse result, handling errors or insertin XML markup.
   */
  private class XmlVisitor implements IASTVisitor {

    private final SmaxDocument smaxDocument;

    public XmlVisitor(ParseResult<?> parseResult, CharSequence fragment, int position, SmaxDocument smaxDocument) throws QueryException
    {
      this.smaxDocument = smaxDocument;
      if (parseResult.getAST() != null) {
        parseResult.getAST().acceptASTVisitor(this);
      } else if (parseResult.getError() != null) {
        error(parseResult.getError(), position);
      } else {
        throw new QueryException("Unknown error occurred during parsing. There is no parse result and no error.");
      }
    }

    public void error(ParseError error, int position) {
      SmaxElement errorElement = new SmaxElement(FN_NS_URI, "error");
      errorElement.setAttribute("NT", error.getNT());
      errorElement.setAttribute("line", ""+error.getLine());
      errorElement.setAttribute("column", ""+error.getColumn());
      errorElement.setAttribute("position", ""+error.getPosition());
      errorElement.setAttribute("message", error.toString());
      this.smaxDocument.insertMarkup(errorElement, Balancing.START, position, position);
    }

    @Override
    public void visitAST(IAST<?> tree) {
      Position pos = tree.getPosition();
      SmaxElement ntElement = new SmaxElement(tree.getType().toString());
      this.smaxDocument.insertMarkup(ntElement, Balancing.OUTER, pos.getStartIndex(), pos.getEndIndex(), true);
      for (IAST<?> child : tree.getChildren()) {
        child.acceptASTVisitor(this);
        }
    }

    @Override
    public void visitEmpty(IEmpty tree) {
    }

    @Override
    public void visitChar(IChar tree) {
    }

  }

}
