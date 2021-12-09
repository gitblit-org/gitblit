#!/bin/bash
# --------------------------------------------------------------------------
# This is for Lucene search integration.
#
# Allows you to add an indexed branch specification to the repository config
# for all matching repositories in the specified folder.
#
# All repositories are included unless excluded using a --skip parameter.
# --skip supports simple wildcard fuzzy matching however only 1 asterisk is
# allowed per parameter.
#
# Always use forward-slashes for the path separator in your parameters!!
#
# Set FOLDER to the server's git.repositoriesFolder
# Set BRANCH ("default" or fully qualified ref - i.e. refs/heads/master)
# Set EXCLUSIONS for any repositories that you do not want to change
# --------------------------------------------------------------------------
FOLDER=data/git
EXCLUSIONS="--skip test.git --skip group/test*"
BRANCH=default
java -cp gitblit.jar:"ext/*" com.gitblit.AddIndexedBranch --repositoriesFolder "$FOLDER" --branch "$BRANCH" "$EXCLUSIONS"
