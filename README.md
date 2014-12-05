# Git/Stash EOL-style check plugin

There are multiple problems developing software on different operating
systems. One of the most popular - mixing of different end-of-line
styles, which have CRLF for Windows, LF for Linux, and CR for MacOS.

Git defines, that sources should be stored using Linux-style EOF.
It is described in ProGit book v2
http://git-scm.com/book/en/v2/Customizing-Git-Git-Configuration#Formatting-and-Whitespace

This plugin performs both pre-receive hook and merge-check for restricting
EOL-style of the committed code. Only Linux-style code is allowed.

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
* error occurs on pushing tags

## Version history

### Version 0.3
- Integration tests implemented. Theu are now performed automatically

### Version 0.2
- Added ablility to switch on/off pull request merge check
- Fixed false error when converting EOL from CRLF to LF
- Added hook (and merge check) settings form validation
- Added checkbox to allow inherited EOL-style
- Added manually-executed integration tests

### Version 0.1
- Initial release