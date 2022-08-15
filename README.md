# mill-giter8

A [Mill][mill] plugin for testing your [Giter8][giter8] templates. Modeled after
the [Giter8 sbt plugin][giter8-sbt].

## Requirements

- This plugin assumes that your Giter8 template is following the [src
    layout][src-layout], meaning that it expects your template to be in
    `src/main/g8`.
- If using a [`default.properties`][default-properties] property file it needs
    to be located at `src/main/g8/default-properties`.

_A minimal example structure_

```
.
├── build.sc
├── mill
├── README.md
└── src
   └── main
      └── g8
         ├── build.sc
         ├── default.properties
         ├── mill
         └── example
            └── src
               └── $package$
                  └── Main.scala
```

## Quickstart

Assuming the above requirements are true, then to use this plugin you'll want to
include the plugin and extend the `G8Module` like so:

```scala
import $ivy.`io.chris-kipp::mill-giter8::0.2.0`

import io.kipp.mill.giter8.G8Module

object g8 extends G8Module {
  override def validationTargets =
    Seq("example.compile", "example.fix", "example.reformat")
}
```

You can then run `mill g8.validate` which will first check to ensure your
project can be generated from the current template and then ensure the
`validationTargets` can run against your generated project.

## Available Targets


- `generate` - This will test the generation of your project.
- `validate` - This will both check the generation of your project and also run
    the `validationTargets` against it.
- `validationTargets` - A `Seq` of targets that you'd like run against your
    project.


[mill]: https://com-lihaoyi.github.io/mill/mill/Intro_to_Mill.html
[giter8]: http://www.foundweekends.org/giter8/index.html
[giter8-sbt]: http://www.foundweekends.org/giter8/testing.html#Using+the+Giter8Plugin
[src-layout]: http://www.foundweekends.org/giter8/template.html#src+layout
[default-properties]: http://www.foundweekends.org/giter8/template.html#default.properties
