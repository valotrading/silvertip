# Silvertip

[![Build Status](https://secure.travis-ci.org/valotrading/silvertip.png)](http://travis-ci.org/valotrading/silvertip)

Silvertip is a networking library, which aims to be easy to use, while hiding
the complexities of the Java NIO API. While similar libraries exist, they are
either hard to use, discontinued, or part of a bigger application framework
that offer an API for using different transports.

## Installation

Silvertip is made available through a Maven repository. If you're using Apache
Ivy, update the resolver chain in your `ivysettings.xml`:

    <resolvers>
      <chain name="main">
        <ibiblio name="silvertip-repository"
          root="http://valotrading.github.com/maven"
          m2compatible="true"/>
      </chain>
    </resolvers>

and declare a dependency to Silvertip by updating `ivy.xml`:

    <dependencies>
      <dependency org="silvertip" name="silvertip" rev="0.3.3"/>
    </dependencies>

If you're using SBT, amend your `build.sbt` with:

    resolvers += "valotrading" at "http://valotrading.github.com/maven"

    libraryDependencies += "silvertip" % "silvertip" % "0.3.3"

## License

Silvertip is released under the Apache License, Version 2.0.
