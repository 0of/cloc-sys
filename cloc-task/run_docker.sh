#!/bin/bash

docker run -d -t -i --env USER_REPO=${USER_REPO} REPO_BRANCH=${REPO_BRANCH} 0of:cloc 