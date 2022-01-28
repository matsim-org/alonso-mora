# MATSim / Alonso-Mora et al. (2017)

This repository contains an open implementation of the pooled taxi fleet dispatching
algorithm developed by Alonso-Mora et al. (2017):

> Alonso-Mora, J., S. Samaranayake, A. Wallar, E. Frazzoli, D. Rus (2017) [On-demand high-capacity ride-sharing via dynamic trip-vehicle assignment](https://doi.org/10.1073/pnas.1611675114), Proceedings of the National Academy of Sciences of the United States of America, 114, 462–467.

We provide a well-documented implementation with a good trade-off between
flexibility and runtime. While a number of parallelizations and speed-ups have
been implemented, we provide generic interfaces to add additional constraints when
performing route constructions. To get started, we provide a simple best-response
heuristic for solving the vehicle-trip assignment problem, but we also provide
interfaces to run the algorithm using **GLPK**, **Cbc**, **Gurobi**, and **CPLEX**.

## Getting started

**TODO** Add some instructions on how to run some basic simulations. Add example for
some mini test case, and add tutorial on how to replicate the study for Manhattan.

## Configuration

**TODO** Discuss the various configuration options we have (for resetting / no resetting the expected pickup time after assignment; checks for deterministic travel times; congestion mitigation tools from our TRB paper; binding vs non binding relocation; how to set the rejection penalties, etc.)

**TODO** Update the instructions below. Basically, we should install the external jars for Gurobi and CPLEX in the maven repository using `mvn install:install-file` on each system where we want to run the simulations and then add a standard dependency to the pom.

## Running with MPS-based solvers

While the basic implementation in the *core* package uses simple best-response
heuristics to solve the trip selection and relocation problems, the original
article from Alonso-Mora et al. (2017) proposes to solve an ILP. To avoid the
additional effort of configuring third-party libraries to solve the problem,
we provide an MPS-based solver interface. MPS is a standard file format to
describe problems for optimization software. The MPS-based solver internally
write out the respective problems in MPS format and then call the solver. After,
they read back in the solution. These solvers are suppose for *small-scale*
scenarios and *test runs* because the file-based communication comes with a
large overhead in computation time.

Currently, two MPS-based solvers are implemented:

- **GLPK**: This solver expects that GLPK is installed on the system and can
be called via the command line. In general, calling `glpsol --version` on your system
should give you some reasonable output. On recent versions of Ubuntu, you can install
GLPK by installing the `glpk-utils` package: `apt install glpk-utils`.
- **Cbc**: This solver expects that the Cbc solver is isntalled on the system an
can be called via the command line. Calling `cbc version` should give some useful
output, if it is installed. On recent versions of Ubuntu, you can install
Cbc by installing the `coinor-cbc` package: `apt install coinor-cbc`.

You can test whether the solvers are avaialble on your system by running the
respective unit tests, i.e. `GlpkMpsAssignmentSolverTest` and `CbcMpsAssignmentSolverTest`
in the `core` package.

**TODO** EXAMPLE HOW TO SET UP CONFIG

## Running with GLPK

For larger instances, it is recommended to use an optimizer that has a direct
Java API. As an open and freely available version, you can use the GLPK solver
via the JNI interface. To do so, you need to have the Java interfaces for GLPK
installed on your system. On recent versions of Ubuntu, this can be achieved
by installing the `libglpk-java` package:

```sh
apt install libglpk-java
```

For other distributions, you may need to build the library manually. Instructions
for that can be found [here](http://glpk-java.sourceforge.net/). There you can
also find precompiled packages for Windows.

Whenever running a simulation that is configured to use the JNI GLPK solver,
make sure to pass the path to the library via the command line, e.g.:

```sh
java -Djava.library.path=/usr/lib/x86_64-linux-gnu/jni [...] RunSimulation [...]
```

The path provided here is the standard path if you have installed `libglpk-java`
on Ubuntu. You can test the functioning by running the unit tests in the `glpk`
package that is part of the present repository. Usually, when you first run
the tests on Eclipse, for instance, they will fail. You can then go to the
*Run configurations* and set the library path using `-D[...]` as
above in the *VM Arguments* of the test run configuration.

**TODO** EXAMPLE HOW TO SET UP CONFIG

## Running with Gurobi

The optimization problems can also be solved using the Gurobi interface for
Java. In that case, you should have downloaded a recent version of Gurobi from
their website and unpacked somewhere. Let's say, the path to the Gurobi runtime
is `/path/to/gurobi912`. Furthermore, you will need to have requested and downloaded
a license file, e.g. at `/path/to/gurobi.lic`.

To use the solver, you need to follow four steps. *(1)* You need to add the Gurobi
Java library to your project. Open, for instance, in Eclipse, the `gurobi` package
in this repository. Then, in Eclipse, you can go to the project properties, to
*Java Build Path* where you can add an *External JAR* to the *Classpath*. Here, you
should add `/path/to/gurobi912/linux64/lib/gurobi.jar`. After, no compile errors
should remain in your IDE.

To run the unit tests, you have to modify multiple things in your *Run configuration*
of the test. *(2)* Add the path pointing to the directory containing the Gurobi JAR
files to the library path:

```
-Djava.library.path=/path/to/gurobi912/linux64/lib
```

*(3)* Additionally, you need to point the `LD_LIBRARY_PATH` environment variable
to the same directory. In Eclipse, you can do that by going to the *Environment*
tab in your test configuration. When running a simulation on the command line,
you can either modify the environment variable before running the simulation in
a shell script, or set it only for the run, e.g.,

```sh
LD_LIBRARY_PATH=/path/to/gurobi912/linux64/lib java [...]
```

Finally, *(4)* you also need to set the path to your license file via the
environment variable `GRB_LICENSE_FILE`. Again, you can do this in Eclipse
for the test configuration or prepend your command line when running on a
Linux shell:

```sh
GRB_LICENSE_FILE=/path/to/gurobi.lic java [...]
```

To test your setup, run the respective unit tests in the `gurobi` package of this
repository.

**TODO** EXAMPLE HOW TO SET UP CONFIG

In case you have developed simulation and want to create a self-contained
jar (e.g. using the Maven shade plugin), you will need to tell Maven where
to retrieve the required JAR for Gurobi. For any packagable project you create,
you will need to add a dependency of the following form to your `pom.xml`:

```xml
<dependency>
  <groupId>gurobi</groupId>
  <artifactId>gurobi</artifactId>
  <version>1.0</version>
  <scope>system</scope>
  <systemPath>/path/to/gurobi912/linux64/lib/gurobi.jar</systemPath>
</dependency>
```

## Running with CPLEX

If you want to run a simulation with CPLEX, you need to have CPLEX installed
on your system. Usually, after following the installation procedure, you should
have CPLEX, e.g. in `/path/to/cplex`.

Similar to Gurobi, you need to add the respective jar to the `cplex` package
of this repository when you open it in Eclipse. Check out the instructions
for Gurobi and, instead, add `/path/to/cplex/lib/cplex.jar` to the
classpath.

Furthermore, you need to add CPLEX to the library path when running the unit
tests or any simulation from the command line (see again detailed instructions above for GLPK and Gurobi):

```sh
java -Djava.library.path=/path/to/cplex/bin/x86-64_linux [...] RunSimulation
```

To test your setup, run the respective unit tests in the `cplex` package of this
repository.

**TODO** EXAMPLE HOW TO SET UP CONFIG
