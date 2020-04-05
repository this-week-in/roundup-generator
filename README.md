# This Week In Roundup Generator

![Build Status](https://github.com/this-week-in/roundup-generator/workflows/CI/badge.svg)

Every week I look at all the news that's fit to blog, annotate the bookmarks in Pinboard, and then, using this Spring Shell-based shell, generate a Markdown list of bullet points  that I can then dump into any of a number of blogging tools for publication. I do this every Tuesday for "This Week in Spring" on [the Spring Blog (http://Spring.io/blog)](http://Spring.io/blog).

## Building
* you can build it by runnning `./mvnw clean package` in the root directory. Make sure that you have an environment variable, `PINBOARD_TOKEN`, somewhere in your environment before launching this application.
