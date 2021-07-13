[Goethe](https://en.wikipedia.org/wiki/Johann_Wolfgang_von_Goethe)
======

Goethe is a code formatter library for [square/javapoet](https://github.com/square/javapoet) using
[palantir-java-format](https://github.com/palantir/palantir-java-format).

The [palantir-java-format](https://github.com/palantir/palantir-java-format) that this library uses is shaded
into a nested package to prevent coupling between human-generated code formatting and validation, and components
which generate code automatically.

Usage
-----

Formatting a javapoet `JavaFile` as a String:
```java
String formatted = Goethe.formatAsString(javaFile);
```

Formatting a `JavaFile` while writing it to an annotation processing `Filer`:
```java
Goethe.formatAndEmit(javaFile, filer);
```

Gradle Tasks
------------
`./gradlew tasks` - to get the list of gradle tasks


Start Developing
----------------
Run one of the following commands:

* `./gradlew idea` for IntelliJ
* `./gradlew eclipse` for Eclipse
