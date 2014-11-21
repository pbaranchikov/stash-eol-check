# Git/Stash EOL-style check plugin

There are multiple problems developing software on different operating
systems. One of the most popular - mixing of different end-of-line
styles, which have CRLF for Windows, LF for Linux, and CR for MacOS.

Git defines, that sources should be stored using Linux-style EOF.
It is described in ProGit book v2
http://git-scm.com/book/en/v2/Customizing-Git-Git-Configuration#Formatting-and-Whitespace

This plugin performs both pre-receive hook and merge-check for restricting
EOL-style of the committed code. Only Linux-style code is allowed.

## Version history

### Version 0.2
- Added ablility to switch on/off pull request merge check
- Fixed false error when converting EOL from CRLF to LF
- Added hook (and merge check) settings form validation

### Version 0.1
- Initial release