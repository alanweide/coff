# coff
A minimal implementation of causal profiling for Java applications running on Jikes RVM. Currently, this basically doesn't work. It'll only profile a very specific microbenchmark (included in the repo under the test package in the `coff/src` folder) at 100x overhead. So, it's basically useless but it's a start!

To run Coff on the microbenchmark, run the following command from the `coff/src` directory (where "rvm" is the path to the Jikes RVM executable in this repository): `rvm -Xcoff test.Test`

Our work is based on [Coz: Finding Code that Counts with Causal Profiling](https://web.cs.umass.edu/publication/docs/2015/UM-CS-2015-008.pdf) and you can find their code on github [here](https://github.com/plasma-umass/coz).
