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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.BiFunction;

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
import org.waxeye.ast.IPreParsedNonTerminal;
import org.waxeye.ast.Position;
import org.waxeye.input.IParserInput;
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
 *       <li>parse-within-element If set to the local name (without namespace prefix) of an element, only text within elements with this name will be parsed.</li>
 *       <li>parse-within-namespace If 'parse-within-element' is set, this may be set to the namespace URI of elements within which the parser will work.</li>
 *       <li>complete-match Set to true if the complete input text must be parsed as one matched fragment. (Default is false.)</li>
 *       <li>adjacent-matches Set to true if the complete input must be consumed as adjacent matched fragments. (Default is false.)</li>
 *       <li>match-whole-words Set to true to only match whole words. (Default is false.)</li>
 *       <li>cache Set to true to cache the generated parser. Only parsers generated from grammars stored on the file system can be cached.</li>
 *       <li>parse-errors Set to true to include errors in the output and not trigger an exception. (Default is false.)</li>
 *       <li>normalize Set to true if characters in the input must be converted to low ASCII characters, removing diacritics and ligatures. (Default is false.)</li>
 *       <li>show-parse-tree Not yet implemented. Set to true to show the parse tree in an XML comment in the output. (Default is false.)</li>
 *       <li>namespace-prefix The namespace prefix used for elements that are inserted for non-terminals. Default is empty (no prefix).
 *       <li>namespace-uri The namespace URI used for elements that are inserted for non-terminals. Default is empty (no namespace).
 *           This option must be present if the 'namespace-prefix' option is defined.
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

  // Work directory for all waxeye parsers.
  private static Path workDir;

  // Path to the Waxeye executable. Waxeye MUST be installed on the host system.
  private static String waxeyePath = "waxeye";

  // Cache for parsers, to prevent repeated grammar compilation.
  class ParserCacheEntry {
    public long modified;
    public Parser<?> parser;
    public ParserCacheEntry(Parser<?> parser) {
      this.modified = new Date().getTime();
      this.parser = parser;
    }
  }
  private static Map<String, ParserCacheEntry> parserCache = new HashMap<String, ParserCacheEntry>();

  // An internal name for the grammar, used for a copy of the grammar in a local file.
  private String internalName;

  private boolean modular;
  private String parseWithinElement;
  private String parseWithinNamespace;
  private boolean completeMatch;
  private boolean adjacentMatches;
  private boolean allowUnmatchedText;
  private boolean matchWholeWords;
  private boolean cache;
  private boolean showParseErrors;
  private boolean showParseTree;
  private boolean normalize;
  private String namespacePrefix;
  private String namespaceUri;

  private Parser<?> parser;
  private String grammarURL;


  public WaxeyePEGParser(URL grammarURL, Map<String, String> options, Logger logger)
  {
    initFirst(options, logger);
    this.grammarURL = grammarURL.toString();
    try {
      readGrammar(grammarURL);
    } catch (IOException | QueryException e) {
      logger.error("Grammar from URL ["+grammarURL+"] cannot be read, written or processed: "+e.getMessage());
      throw new RuntimeException(e);
    }
  }


  public WaxeyePEGParser(String grammarURL, Map<String, String> options, Logger logger)
  {
    initFirst(options, logger);
    this.grammarURL = grammarURL;
    try {
      readGrammar(grammarURL);
    } catch (IOException | QueryException e) {
      logger.error("Grammar in string cannot be read, written or processed: "+e.getMessage());
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
    this.parseWithinElement = getOption(options, "parse-within-element", null);
    this.parseWithinNamespace = getOption(options, "parse-within-namespace", null);
    this.completeMatch = getOption(options, "complete-match", false);
    this.adjacentMatches = getOption(options, "adjacent-matches", false);
    this.allowUnmatchedText = !(completeMatch || adjacentMatches);
    this.matchWholeWords = getOption(options, "match-whole-words", false);
    this.cache = getOption(options, "cache", false);
    this.showParseErrors = getOption(options, "parse-errors", false);
    this.showParseTree = getOption(options, "show-parse-tree", false);
    this.normalize = getOption(options, "normalize", false);
    this.namespacePrefix = getOption(options, "namespace-prefix", null);
    this.namespaceUri = getOption(options, "namespace-uri", null);
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
          logger.info("WaxeyePEGParser: Created work directory for "+this.getClass().getName()+" : "+workDir);
        }
        catch (IOException e)
        {
          logger.error("WaxeyePEGParser: Work directory for "+this.getClass().getName()+" cannot be created: "+e.getMessage());
          throw new RuntimeException(e);
        }
      }
    }
  }


  private String getOption(Map<String, String> options, String key, String defaultValue) {
    return Optional.ofNullable(options.get(key)).orElse(defaultValue);
  }


  private boolean getOption(Map<String, String> options, String key, boolean defaultValue) {
    return Optional.ofNullable(options.get(key)).map(v -> Boolean.parseBoolean(v)).orElse(defaultValue);
  }


  private synchronized void readGrammar(String grammar) throws IOException, QueryException
  {
    // A string grammar is never cached, so write it to a file and compile.
    File grammarFile = workDir.resolve(this.internalName+".waxeye").toFile();
    try (
      PrintWriter grammarWriter = new PrintWriter(grammarFile.getAbsolutePath());
    ) {
      grammarWriter.print(grammar);
    }
    readCompileLoadGrammarFile(grammarFile);
  }


  private synchronized void readGrammar(URL grammar) throws IOException, QueryException
  {
    String grammarFilePath = grammar.toString();
    if (grammarFilePath.startsWith("file:/")) {
      // A file: URL points to a local file.
      if (grammarFilePath.matches("^file:/+[A-Za-z]:/.*")) {
        // Windows: file:///C:/path => C:/path
        grammarFilePath = grammarFilePath.replaceAll("^file:/+", "");
      } else {
        // Unix: file:///path => /path
        grammarFilePath = grammarFilePath.replaceAll("^file:/+", "/");
      }
      // Try to get the parser from the cache.
      File grammarFile = new File(grammarFilePath);
      ParserCacheEntry cached = parserCache.get(grammarFilePath);
      if (cached != null && cached.modified > grammarFile.lastModified()) {
        logger.info("WaxeyePEGParser: Parser for ["+grammarFilePath+"] retrieved from cache.");
        this.parser = cached.parser;
      } else {
        readCompileLoadGrammarFile(grammarFile);
        if (cache) {
          parserCache.put(grammarFilePath, new ParserCacheEntry(this.parser));
          logger.info("WaxeyePEGParser: Parser for ["+grammarFilePath+"] entered into cache.");
        }
      }
    } else {
      // A non-file: URL will be read and copied into the workDir. It is not cached.
      File grammarFile = workDir.resolve(this.internalName+".waxeye").toFile();
      try (
        InputStream grammarStream = grammar.openStream();
        OutputStream grammarFileStream = new FileOutputStream(grammarFile.getAbsolutePath());
      ) {
        grammarStream.transferTo(grammarFileStream);
      }
      readCompileLoadGrammarFile(grammarFile);
    }
  }


  private synchronized void readCompileLoadGrammarFile(File grammar) throws IOException, QueryException
  {
    String grammarFilePath = grammar.getAbsolutePath();
    // Make a Java directory name by removing the extension from the filename.
    String javaDirName = grammar.getName().replaceFirst("\\.[^./]*$", "");
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
    logger.info("WaxeyePEGParser: Compiling waxeye grammar; "+String.join(" ", waxeyeCommand));
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
      logger.error("Error compiling waxeye grammar ["+grammarFilePath+"]:\n"+ex.getMessage()+"\n"+waxeyeOutput.toString());
      throw new QueryException("Error compiling waxeye grammar ["+grammarFilePath+"]: "+ex.getMessage()+". See the log file for details.");
    } finally {
        if (waxeyeProcess != null) {
          waxeyeProcess.destroyForcibly();
        }
        if (waxeyeOutputReader != null) {
          waxeyeOutputReader.close();
        }
    }
    logger.info("WaxeyePEGParser: "+waxeyeOutput.toString());
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
    long startTime = System.currentTimeMillis();
    CharSequence textFragment = smaxDocument.getContent();
    parser.setEofCheck(completeMatch);
    long nrScans;
    if (parseWithinElement != null) {
      // Traverse the DOM tree and only scan within the elements indicated by parseWithinElement and parseWithinNamespace.
      nrScans = traverseAndScan(smaxDocument, textFragment, smaxDocument.getMarkup());
    } else {
      // Scan within the root element.
      nrScans = scanFragment(smaxDocument, smaxDocument.getMarkup(), textFragment, 0);
    }
    long elapsedTime = System.currentTimeMillis()-startTime;
    logger.info("WaxeyePEGParser: Parsing with "+grammarURL+" took "+elapsedTime+" ms, for "+nrScans+" scans.");
  }

  /**
   * Traverse the DOM tree and only scan within the elements indicated by parseWithinElement and parseWithinNamespace.
   * @param smaxDocument
   * @param textFragment
   * @param element
   * @return the number of scans (parsing attempts)
   * @throws QueryException
   */
  private long traverseAndScan(SmaxDocument smaxDocument, CharSequence textFragment, SmaxElement element) throws QueryException
  {
    long nrScans = 0L;
    String elementNsURI = element.getNamespaceURI();
    if ( parseWithinElement.equals(element.getLocalName()) &&
         ( (parseWithinNamespace == null || parseWithinNamespace.isEmpty()) && (elementNsURI == null || elementNsURI.isEmpty()) ||
           parseWithinNamespace.equals(elementNsURI)
         )) {
      int textStart = element.getStartPos();
      int textEnd = element.getEndPos();
      nrScans = scanFragment(smaxDocument, element, textFragment.subSequence(textStart, textEnd), textStart);
    } else if (element.hasChildNodes()) {
      List <SmaxElement> children = element.getChildren();
      for (SmaxElement child : children) {
        nrScans += traverseAndScan(smaxDocument, textFragment, child);
      }
    }
    return nrScans;
  }

  /**
   * Scan a SMAX document or a fragment of it.
   * @param smaxDocument
   * @param withinElement the element within which the textFragment is located.
   * @param textFragment the text of the fragment to scan
   * @param textStart the start position of the fragment within the document
   * @return the number of scans (parsing attempts)
   * @throws QueryException
   */
  private long scanFragment(SmaxDocument smaxDocument, SmaxElement withinElement, CharSequence textFragment, int textStart) throws QueryException
  {
    long nrScans = 0L;
    // Make an ParserSmaxInput for the textFragment.
    final ParserSmaxInput input;
    if (normalize) {
      // The character positions in fragment and input must be the same.
      input = new ParserSmaxInput(StringUtils.charSequenceToCharArray(StringUtils.normalizeOneToOne(textFragment)));
    } else {
      input = new ParserSmaxInput(StringUtils.charSequenceToCharArray(textFragment));
    }
    // Scan the text fragment.
    int textPosition = 0;
    int textEnd = textFragment.length();
    int previousTextPosition = -1;
    StringBuilder unmatched = new StringBuilder(); // Collects unmatched characters, up to the next match.
    // A function that checks if a pre-parsed non-terminal is present at the current position in the input.
    final BiFunction<String, IParserInput<SmaxElement>,Integer> preparsedNonTerminalAt =
        (String nonTerminalName, IParserInput<SmaxElement> smaxInput) -> this.preparsedNonTerminalAt(smaxDocument, nonTerminalName, (ParserSmaxInput)smaxInput);
    // Allow textPosition to go up to textEnd (textPosition <= textEnd), to allow zero-length pre-parsed non-terminal matches at the end of the input.
    // Stop if textPosition does not advance, to prevent infinite loops.
    while (textPosition <= textEnd && textPosition > previousTextPosition) {
      // Skip spaces if unmatched text is allowed and only whole words are matched.
      if (allowUnmatchedText && matchWholeWords) {
        while (textPosition < textEnd && Character.isWhitespace(textFragment.charAt(textPosition))) {
          unmatched.append(textFragment.charAt(textPosition++));
        }
      }
      // The previous text position is where we start parsing.
      previousTextPosition = textPosition;
      // Is there still text to parse after skipping spaces?
      if (textPosition <= textEnd) {
        // Match the input from textPosition.
        input.setPosition(textPosition);
        // Try parsing from the current position.
        ++nrScans;
        long startTime = System.currentTimeMillis();
        final ParseResult<?> parseResult = parser.parse(input, preparsedNonTerminalAt);
        long milliSecondsUsed = System.currentTimeMillis() - startTime;
        // Parse errors are significant if there is unmatched text, and it is not allowed.
        ParseError parseError = parseResult.getError();
        boolean unmatchedTextExists = textPosition < textEnd;
        if (parseError != null && unmatchedTextExists && !allowUnmatchedText) {
          // There was a parse error.
          if (showParseErrors) {
            new XmlVisitor(parseResult, withinElement, textStart, smaxDocument);
          } else {
            String message = "Parse error: "+parseError.toString()+"\n"+
                "Parsing ["+textFragment.subSequence(textPosition, Math.min(textFragment.length(), textPosition+12))+"]";
            throw new QueryException(message);
          }
        } else {
          boolean hasNonEmptyParseTree = parseResult.getAST() != null && !( parseResult.getAST().getType().toString().equals("_Empty") );
          // If there was a match, the next position is after the match. If the match is empty, the text position has not advanced.
          int nextPosition = hasNonEmptyParseTree ? parseResult.getAST().getPosition().getEndIndex() : textPosition;
          // If there was an empty match, the text position has not advanced, and we do that explicitly.
          if (nextPosition == previousTextPosition) {
            nextPosition++;
          }
          boolean nextCharacterInWord = nextPosition < textEnd && Character.isLetterOrDigit(textFragment.charAt(nextPosition));
          if (hasNonEmptyParseTree && (!matchWholeWords || !nextCharacterInWord)) {
            // Insert XML elements for a non-empty match.
            handleText(unmatched);
            if (showParseTree) {
              String parseTree = parseResult.toString();
              insertComment("Parsing took " + milliSecondsUsed + " ms.\n" + parseTree);
            }
            new XmlVisitor(parseResult, withinElement, textStart, smaxDocument);
            textPosition = nextPosition;
          } else if (allowUnmatchedText && textPosition < textEnd) {
            // Skip one character if there is an ignored error or empty match, and more text is available.
            char unmatchedChar = textFragment.charAt(textPosition++);
            unmatched.append(unmatchedChar);
            // If only whole words are matched, and the current character was part of a word, skip the rest of the word.
            if (matchWholeWords && Character.isLetterOrDigit(unmatchedChar)) {
              while (textPosition < textEnd && Character.isLetterOrDigit(textFragment.charAt(textPosition))) {
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
    return nrScans;
  }


  private void handleText(StringBuilder sb) {
    if (sb.length() > 0) {
      sb.delete(0, sb.length());
    }
  }


  private void insertComment(String comment) {
    // Implementation requires SMAX support for comments.
  }


  /**
   * Check if a pre-parsed non-terminal is present at the given position in the document.
   * @param smaxDocument the document that is being parsed / scanned.
   * @param nonTerminalName the name of a pre-parsed non-terminal, as specified by the grammar.
   * @param input the current input, with its position and extended data.
   * @return the number of character positions within the pre-parsed non-terminal, or -1 if there is no pre-parsed non-terminal with the given name at the given position.
   */
  private int preparsedNonTerminalAt(SmaxDocument smaxDocument, String nonTerminalName, ParserSmaxInput input)
  {
    int startPos = input.getPosition();
    // The last visited element. One of the elements following it can be the pre-parsed non-terminal.
    SmaxElement element = input.getExtendedData();
    // The next element that may contain the pre-parsed non-terminal.
    if (element == null) {
      element = smaxDocument.getMarkup(); // Start at the root element
    } else {
      element = input.getNextChildOrSiblingElement(element);
    }
    // Find the first element at the required start position.
    while (element != input.endElement && element.getStartPos() < startPos) {
      // Skip this element.
      element = input.getNextElement(element);
    }
    // Search for the pre-parsed non-terminal at the required start position.
    while (element != input.endElement && element.getStartPos() == startPos) {
      if (nonTerminalName.equals(element.getLocalName())) {
        // The pre-parsed non-terminal has been found.
        input.setExtendedData(element); // This is now the last visited element.
        return element.getEndPos() - element.getStartPos();
      }
      element = input.getNextElement(element);
    }
    return -1;
  }


  /**
   * The XmlVisitor processes the parse result, handling errors or inserting XML markup.
   */
  private class XmlVisitor implements IASTVisitor {

    private final SmaxDocument smaxDocument;
    private int startPosition;
    /* The set of parent elements, used to determine where to insert new elements. */
    private HashSet<SmaxElement> parentElements = new HashSet<>();

    public XmlVisitor(ParseResult<?> parseResult, SmaxElement withinElement, int startPosition, SmaxDocument smaxDocument) throws QueryException
    {
      this.smaxDocument = smaxDocument;
      this.startPosition = startPosition;
      parentElements.add(withinElement); // Use INNER balancing when inserting a non-terminal element in withinElement.
      if (parseResult.getAST() != null) {
        parseResult.getAST().acceptASTVisitor(this);
      } else if (parseResult.getError() != null) {
        error(parseResult.getError(), startPosition);
      } else {
        throw new QueryException("Unknown error occurred during parsing. There is no parse result and no error.");
      }
    }

    public void error(ParseError error, int startPosition) {
      SmaxElement errorElement = new SmaxElement(FN_NS_URI, "error");
      errorElement.setAttribute("NT", error.getNT());
      errorElement.setAttribute("line", ""+error.getLine());
      errorElement.setAttribute("column", ""+error.getColumn());
      errorElement.setAttribute("position", ""+error.getPosition());
      errorElement.setAttribute("message", error.toString());
      this.smaxDocument.insertMarkup(errorElement, Balancing.START, startPosition, startPosition);
    }

    /**
     * Visit a node in the parse tree.
     * @param node the node that is being visited.
     */
    @Override
    public void visitAST(IAST<?> node) {
      Position pos = node.getPosition();
      String localName = node.getType().toString();
      SmaxElement nonTerminalElement =
        ( namespaceUri == null )
        ? new SmaxElement(localName)
        : new SmaxElement(namespaceUri, (namespacePrefix == null ? localName : String.join(":", namespacePrefix, localName)));
      // Insert the new element. When inserting in one of the elements in parentElements, use INNER balancing instead of OUTER.
      nonTerminalElement = this.smaxDocument.insertMarkup(nonTerminalElement, Balancing.OUTER, startPosition + pos.getStartIndex(), startPosition + pos.getEndIndex(), parentElements);
      parentElements.add(nonTerminalElement);
      // Visit the children of this node. Note if there are any pre-parsed non-terminals.
      boolean hasPPNT = false;
      for (IAST<?> child : node.getChildren()) {
        if (child instanceof IPreParsedNonTerminal<?>) {
          hasPPNT = true;
        }
        child.acceptASTVisitor(this);
      }
      if (hasPPNT) {
        adoptPreParsedNonTerminalChildren(node, nonTerminalElement);
      }
      // Here we know the actual content of the non-terminal element, which may contain empty pre-parsed non-terminals.
      parentElements.remove(nonTerminalElement);
    }

    /**
     * Move all child SmaxElements that correspond to pre-parsed non-terminals and other child elements in between into the given non-terminal element.
     * @param node the AST node whose children are to be processed.
     * @param nonTerminalElement the SmaxElement that corresponds to the AST node.
     */
    private void adoptPreParsedNonTerminalChildren(IAST<?> node, SmaxElement nonTerminalElement) {
      // Find the SmaxElements corresponding to the first and last pre-parsed non-terminals among the children of this AST.
      SmaxElement[] firstLastPPNTElement = node.getChildren().stream()
        .reduce(new SmaxElement[2],
          (firstLast, next) -> {
            if (next instanceof IPreParsedNonTerminal<?>) {
              SmaxElement childElement = ((IPreParsedNonTerminal<SmaxElement>)next).getExtendedData();
              if (childElement != null) {
                if (firstLast[0] == null) firstLast[0] = childElement;
                firstLast[1] = childElement;
              }
            }
            return firstLast;
          },
          (part1, part2) -> {
            if (part1[0] == null) part1[0] = part2[0];
            if (part2[1] != null) part1[1] = part2[1];
            return part1;
          });
      if (firstLastPPNTElement[0] != null && firstLastPPNTElement[0].getParentNode() != firstLastPPNTElement[1].getParentNode()) {
        throw new RuntimeException("Internal error: Pre-parsed non-terminals in a single AST node have different parent nodes.");
      }
      // Move all elements between first and last pre-parsed non-terminal into the non-terminal element.
      SmaxElement childElement = firstLastPPNTElement[0];
      while (childElement != null) {
        SmaxElement nextSibling = childElement.getNextSiblingElement();
        childElement.moveInto(nonTerminalElement);
        if (childElement == firstLastPPNTElement[1]) break;
        childElement = nextSibling;
      }
    }

    @Override
    public void visitEmpty(IEmpty tree) {
    }

    @Override
    public void visitChar(IChar tree) {
    }

    @Override
    public void visitPreParsedNonTerminal(IPreParsedNonTerminal tree) {
    }

  }

}
