# coff
A minimal implementation of causal profiling for Java applications running on Jikes RVM. Currently, this basically doesn't work. It'll only profile a very specific microbenchmark (included in the repo under the test package in the `coff/src` folder) at 100x overhead. So, it's basically useless but it's a start!

To run Coff on the microbenchmark, run the following command from the `coff/src` directory (where "rvm" is the path to the Jikes RVM executable in this repository): `rvm -Xcoff test.Test` after compiling Test.java with javac

For more information about Jikes RVM, visit the [Jikes RVM website](https://jikesrvm.org). Coff is built on top of Jikes RVM version 3.1.3.

Our work is based on [Coz: Finding Code that Counts with Causal Profiling](http://sigops.org/sosp/sosp15/current/2015-Monterey/printable/090-curtsinger.pdf) and you can find their code on github [here](https://github.com/plasma-umass/coz).
