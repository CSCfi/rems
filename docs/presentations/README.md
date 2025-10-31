# Presentations

Here we have presentation materials for REMS. Each presentation is in the form of a simple Markdown file for easy updates and maintenance but the downside of course is that they are not pretty as presentations as such.

## How to use – reveal.js

Follow the [instructions](https://revealjs.com/installation/#full-setup) to setup reveal.js, and off you go.

## How to use – CSC's training template

This is a bit more cumbersome than the previous example but does not require setting up a local web server. [Pandoc](https://pandoc.org) is required, however.

1. `git clone https://github.com/csc-training/slide-template`

2. `ln -s slide-template/theme theme`

3. `cd slide-template && python convert.py ../<slidesetname>.md`
