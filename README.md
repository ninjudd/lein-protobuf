lein-protobuf is a [Leiningen](https://github.com/technomancy/leiningen) plugin for compiling
[Google Protobuf](http://code.google.com/p/protobuf/) `.proto` files into Java `.class` files. It
can be used with or without [clojure-protobuf](https://github.com/flatland/clojure-protobuf), which
is a Clojure wrapper around the Java protobuf API.

## Getting started

Add the following to your `project.clj` file:

    :plugins [[lein-protobuf "0.4.2"]]

Replace `"0.4.2"` with the actual latest version, which you can find at http://clojars.org/lein-protobuf.

*Note: lein-protobuf requires at least version 2.0 of Leiningen.*

## Usage

By default, lein-protobuf looks for `.proto` files in `resources/proto` in your project
directory. This was chosen as the default location so that `.proto` files would also be included in
your jar files. You can change this with:

    :proto-path "path/to/proto"

To compile all `.proto` files in this directory, just run:

    lein protobuf

You can also compile specific proto files with:

    lein protobuf file1.proto file2.proto

We also add a hook to Leiningen's `compile` task, so `.proto` files will automatically be compiled
before that task runs. So if you like, you can simply run:

    lein compile


## Getting Help

If you have any questions or need help, you can find us on IRC in
[#flatland](irc://irc.freenode.net/#flatland).

## License

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
