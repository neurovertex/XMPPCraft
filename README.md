XMPPCraft
=========

This software is a wrapper for Minecraft that mirrors the game chat to a XMPP Multi-User Chat. It was born out of the
necessity to link a 1.8 server with a chat server when both Bukkit and Forge hadn't updated yet. It captures the log 
from the standard output and injects command to the standard input, thus doesn't actively rely on Minecraft's 
implementation and *potentially* works with older and future versions (that's completely untested).

How to use
==========
Dependencies
------------

To build the software, you need thge [Smack](www.igniterealtime.org/projects/smack/) library (recommended version: 4.0.4)
The software actually use some of Apache's and Google's libraries but they're included with Minecraft.

Installation
------------

There are several ways to install the compiled classes. You can either pack the .class files within minecraft-server.jar
(with Smack too), if you make sure to replace the manifest with XMPPCraft's, or you can pack them into their own jar or 
even not at all, the most important is that Minecraft's and Smack's classes are in the classpath upon launch.

Configuration
-------------
### Main settings

Before launching it for the first time, you'll need to set a few settings. XMPPCraft uses three .json files: users.json,
 language.json and settings.json. You only have to take care of the latter, as the two others will be created and
 populated automatically. Number of settings used by the software have a default value (such has the port), which will
 be written into the JSON file when the software encounters them for the first time. A default settings.json file is
 included at the root of the project, with fields you need to set tagged "REPLACE". They are pretty self-explanatory.

### Language

language.json contains (most of) the text the bot will answer with. Having named my own instance of the bot "GLaDOS",
 those are personalized accordingly. You can change them in the language.json file, or directly in the source code. But
 be aware that the source code's version is a default value, that will be ignored if language.json has one.

Copyright
---------

As specified in the COPYING file, this project is licenced under the WTF public licence, so you can pretty much do
whatever with the source code. Of course I'd appreciate a heads up and credits if you modify/fork/redistribute this
code, but that's up to you.
 
[![WTFPL](http://www.wtfpl.net/wp-content/uploads/2012/12/wtfpl-badge-1.png)](http://www.wtfpl.net/)