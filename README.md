# Git/Stash EOL-style check plugin

There are multiple problems developing software on different operating
systems. One of the most popular - mixing of different end-of-line
styles, which have CRLF for Windows, LF for Linux, and CR for MacOS.

Git defines, that sources should be stored using Linux-style EOF.
It is described in ProGit book v2
http://git-scm.com/book/en/v2/Customizing-Git-Git-Configuration#Formatting-and-Whitespace

This plugin performs both pre-receive hook and merge-check for restricting
EOL-style of the committed code. Only Linux-style code is allowed.

Plugin is tested on Bitbucket Server 4.0.0 - 4.0.2. Other versions may work, but
not guaranteed.

## Settings of the hook/merge check

* 'Allow inherited EOL-style' option provides an ability to perform some
  migration of already bad-eol-style projects. Setting this option you
  allow to avoid EOL-style checks of files, which are already committed with
  wrong EOL-style. But still:
  * users are not able to commit new files with wrong EOL-style,
  * users are allowed to convert wrong EOL-style to LF.
* 'Exclude files' option allows administrator to set up comma-separated
  list of filename patterns which should not he checked (should be ignored)
  by this plugin in both pre-commit hook and pull request merge check.

## Known issues
* only last commit is analyzed in pre-receive hook during the initial push
### Build isues
* integration tests are not working for Stash 3.8.x and above.

## Development

As of some errors in Maven dependency configuration, you need to install
Oracle JDBC drivers into your local Maven repository. This is only needed
by Butbucket Maven plugins to startup Bitbucket server instance for integration
testing.
```
mvn install:install-file -DgroupId=com.oracle -DartifactId=ojdbc6 \
     -Dversion=11.2.0.2.0 -Dpackaging=jar -Dfile=${HOME}/download/ojdbc6.jar -DgeneratePom=true
```
Of course, you need to download ojdbc6.jar file preparatorily.

## Version history

### Version 0.7
- Got rid of snapshot dependencies

### Version 0.7
- Added several non-admin users for creating and forking repositories in tests
- Fixed #3: access denied on verifying pull request by non-admin user

### Version 0.6
- Converted plug-in to Bitbucket server API
- Cleaned out Maven dependencies
- Removed Eclipse project and classpath files
- Updated Checkstyle configuration

### Version 0.5
- Fixed exception, thrown when a file matched multiple exclusion patterns

### Version 0.4
- Optimized hook performance by using more effective stream reading
- Enabled Stash Datacenter support in plugin descriptor
- Changed integration tests to use wired testing framework

### Version 0.3
- Integration tests implemented. They are now performed automatically
- Fixed #1: failure when pushing tags with comments

### Version 0.2
- Added ablility to switch on/off pull request merge check
- Fixed false error when converting EOL from CRLF to LF
- Added hook (and merge check) settings form validation
- Added checkbox to allow inherited EOL-style
- Added manually-executed integration tests

### Version 0.1
- Initial release