Silvertip
=========

Silvertip is a networking library, which aims to be easy to use, while hiding
the complexities of the Java NIO API. While similar libraries exist, they are
either hard to use, discontinued, or part of a bigger application framework
that offer an API for using different transports.

Installation
------------

Silvertip is made available through a Maven repository. If you're using Apache
Ivy, update the resolver chain in your `ivysettings.xml`:

    <resolvers>
      <chain name="main">
        <ibiblio name="silvertip-repository"
          root="http://valotrading.github.com/silvertip/maven"
          m2compatible="true"/>
      </chain>
    </resolvers>

and declare a dependency to Silvertip by updating `ivy.xml`:

    <dependencies>
      <dependency org="silvertip" name="silvertip" rev="0.2.5"/>
    </dependencies>

If you're using SBT, amend your `build.sbt` with:

    resolvers += "silvertip-repository" at "http://valotrading.github.com/silvertip/maven"

    libraryDependencies += "silvertip" % "silvertip" % "0.2.5"

Releasing
---------

To release a version, make a clone of `gh-pages` branch:

    git clone git@github.com:valotrading/silvertip.git $HOME/silvertip-gh-pages -b gh-pages

Then deploy:

    mvn deploy -DaltDeploymentRepository=mine::default::file://$HOME/silvertip-gh-pages/maven

And finally:
 
    cd $HOME/silvertip-gh-pages
    git add .
    git commit -a -s
    git push origin gh-pages
