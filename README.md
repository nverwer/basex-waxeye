# basex-waxeye

An implementation of the [Waxeye PEG parser generator](https://waxeye.org/) for BaseX.


# Installing and using

Run `maven install`.
This will produce `target/basex-waxeye-n.m.l.jar`, according to the specification in the [BaseX documentation](https://docs.basex.org/main/Repository#java).
To install this into BaseX, simply copy the jar to `basex/lib/custom`.

The Java code follows the integration pattern described in the [BaseX documentation](https://docs.basex.org/main/Java_Bindings#integration).
The Java function `FuncItem waxeyPegParser(Object, Map<String, String>)`
is available in XQuery as 
```xquery
peg:waxey-peg-parser($grammar as (xs:string | xs:anyURI), $options as map(xs:string, xs:string))
  as function((xs:string | node())) as node()*
```
where the `peg` namespace is `org.greenmercury.basex.xquery.functions.peg.PEGModule`.

The following is an example of using the waxeye-peg-parser function in XQuery:
```xquery
import module namespace peg='org.greenmercury.basex.xquery.functions.peg.PEGModule';
let $grammar := ``[palindrome <- 'a' :?palindrome 'a' | 'b' ?:palindrome 'b' | 'c' ?:palindrome 'c' | 'a' | 'b' | 'c']``
let $options := map {  }
let $peg := peg:waxey-peg-parser($grammar, $options)
let $input := ``[abcbaabba]``
let $output := $peg($input)
return $output
```
This will return two elements:
```
<Palindrome>abcba</Palindrome>
<Palindrome>abba</Palindrome>
```


## Options

The `peg:waxey-peg-parser` function accepts the following options:

* `modular` Set to true if the grammar is [modular](https://waxeye.org/manual#_modular_grammars). (Default is false.)
* `complete-match` Set to true if the complete input text must be parsed as one matched fragment. (Default is false.)
* `adjacent-matches` Set to true if the complete input must be consumed as adjacent matched fragments. (Default is false.)
* `match-whole-words` Set to true to only match whole words. (Default is false.)
* `parse-errors` Set to true to include errors in the output and not trigger an exception. (Default is false.)
* `normalize` Set to true if characters in the input must be converted to low ASCII characters, removing diacritics and ligatures. (Default is false.)

If `complete-match` is true, `adjacent-matches` is ignored because there must be only one match.

If `adjacent-matches` is true, there may be multiple adjacent matched fragments, but no unmatched text.

If `parse-errors` is true, errors are represented by `<fn:error>` elements in the "http://www.w3.org/2005/xpath-functions" namespace.

If both `complete-match` and `adjacent-matches` are false, the result is a mix of unmatched text and an arbitrary number of matched fragments.
In this case, no parsing errors will be generated, and `parse-errors` is ignored.


# Building and installing Waxeye

A version of Waxeye is already available in 'src/main/resources`.
The following instructions are only relevant when a new version of waxeye must be used.

Waxeye is an external program, which must be compiled separately.
We use a [modified version](https://github.com/nverwer/waxeye) of waxeye.
Make sure that this is the version that you have cloned.

To support both Linux / MacOS and Windows, two binaries and a jar file must be generated.
You can do this on a Windows machine by installing WSL and Ubuntu.
It is also possible to only support Linux / MacOS when nobody uses Windows anymore after 14 October 2025. 

On both Windows and Linux, [Racket](http://racket-lang.org) must be installed.
See the README.md of waxeye for detailed instructions.

In Windows, go to the waxeye root directory (where you cloned waxeye) and execute `.\build\exe.bat`.
This will generate a `waxeye.exe` in the waxeye root directory, and a `waxeye.jar` in the `lib` directory.

In Linux / MacOS, go to the waxeye root directory (where you cloned waxeye) and execute `make compiler`.
This will generate a `waxeye` executable in the `bin` directory, and a `waxeye.jar` in the `lib` directory.

The newly generated files must now be copied into basex-waxeye.

1. Copy `waxeye/lib/waxeye.jar` to `basex-waxeye/lib/waxeye.jar`.

2. Copy `waxeye/waxeye.exe`, `waxeye/bin` (directory), and `waxeye/lib` (directory) into the `basex-waxeye/src/main/resources` directory.