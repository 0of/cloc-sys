# cloc-sys
[![Build Status](https://travis-ci.org/0of/cloc-sys.svg?branch=master)](https://travis-ci.org/0of/cloc-sys)

Implementations of count lines of code(CLOC) system including scheduler task and web renderer

> Badge sample ![sample](/doc/res/sample_badge.png)

![system](/doc/res/cloc_sys.png)

## cloc-migration
RethinkDB migrations and [ragtime] (https://github.com/weavejester/ragtime) rehinkdb adapter implementations

## cloc-reporter
HTTP API gathers the counting result

## cloc-scheduler
scheduling pending and cancenllation requests and starting the task in container and removing the one needs to be cancelled

## cloc-task
counting task running in container written in Nodejs and submit the result to reporter

## cloc-web
acquire the svg badge from counting result

## cloc-webhook
Github commit hook and queue counting request to scheduler

# License
  Apache License Version 2.0
