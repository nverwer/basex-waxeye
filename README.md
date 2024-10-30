# basex-waxeye

An implementation of the [Waxeye PEG parser generator](https://waxeye.org/) for BaseX.

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