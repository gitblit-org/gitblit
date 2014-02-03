#!/usr/bin/env python3
#
# Barnum, a Patchset Tool (pt)
#
# This Git wrapper script is designed to reduce the ceremony of working with Gitblit patchsets.
#
# Copyright 2014 gitblit.com.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
#
# Usage:
#
#    pt fetch <id> [-p,--patchset <n>]
#    pt checkout <id> [-p,--patchset <n>] [--force]
#    pt pull <id> [-p,--patchset <n>]
#    pt push [<id>] [--force] [-m,--milestone <milestone>] [-t,--topic <topic>] [-cc <user> <user>]
#    pt start <topic>
#    pt propose [new | <branch> | <id>] [-m,--milestone <milestone>] [-t,--topic <topic>] [-cc <user> <user>]
#    pt cleanup <id>
#

__author__ = 'James Moger'

import subprocess
import argparse
import errno
import sys


def fetch(args):
    """
    fetch(args)

    Fetches the specified patchset for the ticket from the specified remote.
    """

    __resolve_remote(args)
    __resolve_patchset(args)

    # fetch the patchset from the remote repository
    print("Fetching ticket {} patchset {} from the '{}' repository".format(args.id, args.patchset, args.remote))
    patchset_ref = 'refs/tickets/{:02d}/{:d}/{:d}'.format(args.id % 100, args.id, args.patchset)
    __call(['git', 'fetch', args.remote, patchset_ref])

    return


def checkout(args):
    """
    checkout(args)

    Checkout the patchset on a named branch.
    """

    __resolve_uncommitted_changes(args)
    fetch(args)

    # collect local branch names
    branches = []
    for branch in __call(['git', 'branch']):
        if branch[0] == '*':
            branches.append(branch[1:].strip())
        else:
            branches.append(branch.strip())

    branch = 'tickets/{:d}/{:d}'.format(args.id, args.patchset)

    # ensure there are no local branch names that will interfere with branch creation
    illegals = set(branches) & set(['tickets', 'tickets/{:d}'.format(args.id)])
    if len(illegals) > 0:
        print('')
        print('Sorry, can not complete the checkout of ticket {} patchset {}.'.format(args.id, args.patchset))
        print("The following branches are blocking '{}' branch creation:".format(branch))
        for illegal in illegals:
            print('  ' + illegal)
        exit(errno.EINVAL)

    __checkout(args.remote, args.id, args.patchset, branch, args.force)

    return


def pull(args):
    """
    pull(args)

    Pull (fetch & merge) a ticket patchset into the current branch.
    """

    __resolve_uncommitted_changes(args)
    __resolve_remote(args)
    __resolve_patchset(args)

    # reset the checkout before pulling
    __call(['git', 'reset', '--hard'])

    # pull the patchset from the remote repository
    print("Pulling ticket {} patchset {} from the '{}' repository".format(args.id, args.patchset, args.remote))
    patchset_ref = 'refs/tickets/{:02d}/{:d}/{:d}'.format(args.id % 100, args.id, args.patchset)

    if args.squash:
        __call(['git', 'pull', '--squash', '--no-log', '--no-rebase', args.remote, patchset_ref], echo=True)
    else:
        __call(['git', 'pull', '--commit', '--no-ff', '--no-log', '--no-rebase', args.remote, patchset_ref], echo=True)

    return


def push(args):
    """
    push(args)

    Push your patchset update or a patchset rewrite.
    """

    if args.id is None:
        # try to determine ticket and patchset from current branch name
        for line in __call(['git', 'status', '-b', '-s']):
            if line[0:2] == '##':
                branch = line[2:].strip()
                segments = branch.split('/')
                if len(segments) == 3:
                    if segments[0] == 'tickets':
                        args.id = int(segments[1])
                        args.patchset = int(segments[2])

    if args.id is None:
        print('Please specify a ticket id for the push command.')
        exit(errno.EINVAL)

    __resolve_uncommitted_changes(args)
    __resolve_remote(args)
    __resolve_patchset(args)

    if args.force:
       # rewrite a patchset for an existing ticket
        push_ref = 'refs/for/' + str(args.id)
    else:
        # fast-forward update to an existing patchset
        push_ref = 'refs/tickets/{:02d}/{:d}/{:d}'.format(args.id % 100, args.id, args.patchset)

    ref_params = __get_pushref_params(args)
    ref_spec = 'HEAD:' + push_ref + ref_params

    print("Pushing your patchset to the '{}' repository".format(args.remote))
    __call(['git', 'push', args.remote, ref_spec], echo=True)

    return


def start(args):
    """
    start(args)

    Start development of a topic on a new branch.
    """

    # collect local branch names
    branches = []
    for branch in __call(['git', 'branch']):
        if branch[0] == '*':
            branches.append(branch[1:].strip())
        else:
            branches.append(branch.strip())

    branch = 'topic/' + args.topic
    illegals = set(branches) & set(['topic', branch])

    # ensure there are no local branch names that will interfere with branch creation
    if len(illegals) > 0:
        print('Sorry, can not complete the creation of the topic branch.')
        print("The following branches are blocking '{}' branch creation:".format(branch))
        for illegal in illegals:
            print('  ' + illegal)
        exit(errno.EINVAL)

    __call(['git', 'checkout', '-b', branch])

    return


def propose(args):
    """
    propose_patchset(args)

    Push a patchset to create a new proposal ticket or to attach a proposal patchset to an existing ticket.
    """

    __resolve_uncommitted_changes(args)
    __resolve_remote(args)

    push_ref = None
    if args.target is None:
        # see if the topic is a ticket id
        # else default to new
        for branch in __call(['git', 'branch']):
            if branch[0] == '*':
                b = branch[1:].strip()
                if b.startswith('topic/'):
                    topic = b[6:].strip()
                    try:
                        int(topic)
                        push_ref = topic
                    except ValueError:
                        pass
        if push_ref is None:
            push_ref = 'new'
    else:
        push_ref = args.target

    try:
        # check for current patchset and current branch
        args.id = int(push_ref)
        args.patchset = __get_current_patchset(args.remote, args.id)
        if args.patchset > 0:
            print('You can not propose a patchset for ticket {} because it already has one.'.format(args.id))

            # check current branch for accidental propose instead of push
            for line in __call(['git', 'status', '-b', '-s']):
                if line[0:2] == '##':
                    branch = line[2:].strip()
                    segments = branch.split('/')
                    if len(segments) == 3:
                        if segments[0] == 'tickets':
                            args.id = int(segments[1])
                            args.patchset = int(segments[2])
                            print("You are on the '{}' branch, perhaps you meant to push instead?".format(branch))
            exit(errno.EINVAL)
    except ValueError:
        pass

    ref_params = __get_pushref_params(args)
    ref_spec = 'HEAD:refs/for/{}{}'.format(push_ref, ref_params)
    print(ref_spec)
    print("Pushing your proposal to the '{}' repository".format(args.remote))
    __call(['git', 'push', args.remote, ref_spec], echo=True)

    return


def cleanup(args):
    """
    cleanup(args)

    Removes local branches for the ticket.
    """

    if args.id is None:
        branches = __call(['git', 'branch', '--list', 'tickets/*'])
    else:
        branches = __call(['git', 'branch', '--list', 'tickets/{:d}/*'.format(args.id)])

    if len(branches) == 0:
        print("No local branches found for ticket {}, cleanup skipped.".format(args.id))
        return

    if not args.force:
        print('Cleanup would remove the following local branches for ticket {}.'.format(args.id))
        for branch in branches:
            if branch[0] == '*':
                print('  ' + branch[1:].strip() + ' (skip)')
            else:
                print('  ' + branch)
        print("To discard these local branches, repeat this command with '--force'.")
        exit(errno.EINVAL)

    for branch in branches:
        if branch[0] == '*':
            print('Skipped {} because it is the current branch.'.format(branch[1:].strip()))
            continue
        __call(['git', 'branch', '-D', branch.strip()], echo=True)

    return


def __resolve_uncommitted_changes(args):
    """
    __resolve_uncommitted_changes(args)

    Ensures the current checkout has no uncommitted changes.
    """

    status = __call(['git', 'status', '--porcelain'])
    for line in status:
        if not args.force and line[0] != '?':
            print('Your local changes to the following files would be overwritten by {}:'.format(args.command))
            print('')
            for line in status:
                print(line)
            print('')
            print("To discard your local changes, repeat the {} with '--force'.".format(args.command))
            print('NOTE: forcing a {} will HARD RESET your working directory!'.format(args.command))
            exit(errno.EINVAL)


def __resolve_remote(args):
    """
    __resolve_remote(args)

    Identifies the git remote to use for fetching and pushing patchsets by parsing .git/config.
    """

    remotes = __call(['git', 'remote'])

    if len(remotes) == 0:
        # no remotes defined
        print("Please define a Git remote")
        exit(errno.EINVAL)
    elif len(remotes) == 1:
        # only one remote, use it
        args.remote = remotes[0]
        return
    else:
        # multiple remotes, read .git/config
        output = __call(['git', 'config', '--local', 'patchsets.remote'], fail=False)
        patchsets_remote = output[0] if len(output) > 0 else ''

        if len(patchsets_remote) == 0:
            print("You have multiple remote repositories and you have not configured 'patchsets.remote'.")
            print("")
            print("Available remotes:")
            for remote in remotes:
                print('  ' + remote)
            print("")
            print("Please set the remote to use for patchsets.")
            print("  git config --local patchsets.remote <remote>")
            exit(errno.EINVAL)
        else:
            try:
                remotes.index(patchsets_remote)
            except ValueError:
                print("The '{}' repository specified in 'patchsets.remote' is not a valid Git remote!".format(patchsets_remote))
                print("")
                print("Available remotes:")
                for remote in remotes:
                    print('  ' + remote)
                print("")
                print("Please set the remote repository to use for patchsets.")
                print("  git config --local patchsets.remote <remote>")
                exit(errno.EINVAL)

            args.remote = patchsets_remote
    return


def __resolve_patchset(args):
    """
    __resolve_patchset(args)

    Resolves the current patchset or validates the the specified patchset exists.
    """
    if args.patchset is None:
        # resolve current patchset
        args.patchset = __get_current_patchset(args.remote, args.id)

        if args.patchset == 0:
            # there are no patchsets for the ticket or the ticket does not exist
            print("There are no patchsets for ticket {} in the '{}' repository".format(args.id, args.remote))
            exit(errno.EINVAL)
    else:
        # validate specified patchset
        args.patchset = __validate_patchset(args.remote, args.id, args.patchset)

        if args.patchset == 0:
            # there are no patchsets for the ticket or the ticket does not exist
            print("Patchset {} for ticket {} can not be found in the '{}' repository".format(args.patchset, args.id, args.remote))
            exit(errno.EINVAL)

    return


def __validate_patchset(remote, ticket, patchset):
    """
    __validate_patchset(remote, ticket, patchset)

    Validates that the specified ticket patchset exists.
    """

    nps = 0
    patchset_ref = 'refs/tickets/{:02d}/{:d}/{:d}'.format(ticket % 100, ticket, patchset)
    for line in __call(['git', 'ls-remote', remote, patchset_ref]):
        ps = int(line.split('/')[4])
        if ps > nps:
            nps = ps

    if nps == patchset:
        return patchset
    return 0


def __get_current_patchset(remote, ticket):
    """
    __get_current_patchset(remote, ticket)

    Determines the most recent patchset for the ticket by listing the remote patchset refs
    for the ticket and parsing the patchset numbers from the resulting set.
    """

    nps = 0
    patchset_refs = 'refs/tickets/{:02d}/{:d}/*'.format(ticket % 100, ticket)
    for line in __call(['git', 'ls-remote', remote, patchset_refs]):
        ps = int(line.split('/')[4])
        if ps > nps:
            nps = ps

    return nps


def __checkout(remote, ticket, patchset, branch, force=False):
    """
    __checkout(remote, ticket, patchset, branch)
    __checkout(remote, ticket, patchset, branch, force)

    Checkout the patchset on a detached head or on a named branch.
    """

    has_branch = False
    on_branch = False

    if branch is None or len(branch) == 0:
        # checkout the patchset on a detached head
        print('Checking out ticket {} patchset {} on a detached HEAD'.format(ticket, patchset))
        __call(['git', 'checkout', 'FETCH_HEAD'], echo=True)
        return
    else:
        # checkout on named branch

        # determine if we are already on the target branch
        for line in __call(['git', 'branch', '--list', branch]):
            has_branch = True
            if line[0] == '*':
                # current branch (* name)
                on_branch = True

        if not has_branch:
            if force:
                # force the checkout the patchset to the new named branch
                # used when there are local changes to discard
                print("Forcing checkout of ticket {} patchset {} on named branch '{}'".format(ticket, patchset, branch))
                __call(['git', 'checkout', '-b', branch, 'FETCH_HEAD', '--force'], echo=True)
            else:
                # checkout the patchset to the new named branch
                __call(['git', 'checkout', '-b', branch, 'FETCH_HEAD'], echo=True)
            return

        if not on_branch:
            # switch to existing local branch
            __call(['git', 'checkout', branch], echo=True)

        #
        # now we are on the local branch for the patchset
        #

        if force:
            # reset HEAD to FETCH_HEAD, this drops any local changes
            print("Forcing checkout of ticket {} patchset {} on named branch '{}'".format(ticket, patchset, branch))
            __call(['git', 'reset', '--hard', 'FETCH_HEAD'], echo=True)
            return
        else:
            # try to merge the existing ref with the FETCH_HEAD
            merge = __call(['git', 'merge', '--ff-only', branch, 'FETCH_HEAD'], echo=True, fail=False)
            if len(merge) is 1:
                up_to_date = merge[0].lower().index('up-to-date') > 0
                if up_to_date:
                    return
            elif len(merge) is 0:
                print('')
                print("Your '{}' branch has diverged from patchset {} on the '{}' repository.".format(branch, patchset, remote))
                print('')
                print("To discard your local changes, repeat the checkout with '--force'.")
                print('NOTE: forcing a checkout will HARD RESET your working directory!')
                exit(errno.EINVAL)
    return


def __get_pushref_params(args):
    """
    __get_pushref_params(args)

    Returns the push ref parameters for ticket field assignments.
    """

    params = []

    if args.milestone is not None:
        params.append('m=' + args.milestone)

    if args.topic is not None:
        params.append('t=' + args.topic)
    else:
        for branch in __call(['git', 'branch']):
            if branch[0] == '*':
                b = branch[1:].strip()
                if b.startswith('topic/'):
                    params.append('t=' + b[len('topic/'):])

    if args.responsible is not None:
        params.append('r=' + args.responsible)

    if args.cc is not None:
        for cc in args.cc:
            params.append('cc=' + cc)

    if len(params) > 0:
        return '%' + ','.join(params)

    return ''


def __call(cmd_args, echo=False, fail=True):
    """
    __call(cmd_args)

    Executes the specified command as a subprocess.  The output is parsed and returned as a list
    of strings.  If the process returns a non-zero exit code, the script terminates with that
    exit code.  Std err of the subprocess is passed-through to the std err of the parent process.
    """

    p = subprocess.Popen(cmd_args, stdout=subprocess.PIPE, universal_newlines=True)
    lines = []
    for line in iter(p.stdout.readline, b''):
        line_str = str(line).strip()
        if len(line_str) is 0:
            break
        lines.append(line_str)
        if echo:
            print(line_str)
    p.wait()
    if fail and p.returncode is not 0:
        exit(p.returncode)

    return lines

#
# define the acceptable arguments and their usage/descriptions
#

# force argument
force_args = argparse.ArgumentParser(add_help=False)
force_args.add_argument('-f', '--force', default=False, help='force the command to complete', action='store_true')

# ticket & patchset arguments
ticket_args = argparse.ArgumentParser(add_help=False)
ticket_args.add_argument('id', help='the ticket id', type=int)
ticket_args.add_argument('-p', '--patchset', help='the patchset number', type=int)

# push refspec arguments
push_args = argparse.ArgumentParser(add_help=False)
push_args.add_argument('-m', '--milestone', help='set the milestone')
push_args.add_argument('-r', '--responsible', help='set the responsible user')
push_args.add_argument('-t', '--topic', help='set the topic')
push_args.add_argument('-cc', nargs='+', help='specify accounts to add to the watch list')

# the commands
parser = argparse.ArgumentParser(description='a Patchset Tool for Gitblit Tickets')
commands = parser.add_subparsers(dest='command', title='commands')

fetch_parser = commands.add_parser('fetch',  parents=[ticket_args], help='fetch a patchset')
fetch_parser.set_defaults(func=fetch)

checkout_parser = commands.add_parser('checkout', aliases=['co'], parents=[ticket_args, force_args], help='fetch & checkout a patchset to a branch')
checkout_parser.set_defaults(func=checkout)

pull_parser = commands.add_parser('pull', parents=[ticket_args, force_args], help='fetch & merge a patchset into the current branch')
pull_parser.add_argument('-s', '--squash', default=False, help='squash the pulled patchset into your working directory', action='store_true')
pull_parser.set_defaults(func=pull)

push_parser = commands.add_parser('push', aliases=['up'], parents=[push_args, force_args], help='upload your patchset changes')
push_parser.add_argument('id', nargs='?', help='the ticket id', type=int)
push_parser.add_argument('-p', '--patchset', help='the patchset number', type=int)
push_parser.set_defaults(func=push)

propose_parser = commands.add_parser('propose', parents=[push_args], help='propose a new ticket or the first patchset')
propose_parser.add_argument('target', nargs='?', help="the ticket id, 'new', or the integration branch")
propose_parser.set_defaults(func=propose)

cleanup_parser = commands.add_parser('cleanup', aliases=['rm'], parents=[force_args], help='remove local ticket branches')
cleanup_parser.add_argument('id', nargs='?', help='the ticket id', type=int)
cleanup_parser.set_defaults(func=cleanup)

start_parser = commands.add_parser('start', help='start a new branch for the topic or ticket')
start_parser.add_argument('topic', help="the topic or ticket id")
start_parser.set_defaults(func=start)

# parse the command-line arguments
args = parser.parse_args()

if len(sys.argv) < 2:
    parser.parse_args(['--help'])
else:
    # parse the command-line arguments
    args = parser.parse_args()

    # exec the specified command
    args.func(args)
