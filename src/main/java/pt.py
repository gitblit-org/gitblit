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
#    pt checkout <id> [-p,--patchset <n>] [-f,--force]
#    pt pull <id> [-p,--patchset <n>]
#    pt push [<id>] [-i,--ignore] [-f,--force] [-m,--milestone <milestone>] [-t,--topic <topic>] [-cc <user> <user>]
#    pt start <topic> | <id>
#    pt propose [new | <branch> | <id>] [-i,--ignore] [-m,--milestone <milestone>] [-t,--topic <topic>] [-cc <user> <user>]
#    pt cleanup [<id>]
#

__author__ = 'James Moger'
__version__ = '1.0.6'

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

    # fetch the patchset from the remote repository

    if args.patchset is None:
        # fetch all current ticket patchsets
        print("Fetching ticket patchsets from the '{}' repository".format(args.remote))
        if args.quiet:
            __call(['git', 'fetch', '-p', args.remote, '--quiet'])
        else:
            __call(['git', 'fetch', '-p', args.remote])
    else:
        # fetch specific patchset
        __resolve_patchset(args)
        print("Fetching ticket {} patchset {} from the '{}' repository".format(args.id, args.patchset, args.remote))
        patchset_ref = 'refs/tickets/{:02d}/{:d}/{:d}'.format(args.id % 100, args.id, args.patchset)
        if args.quiet:
            __call(['git', 'fetch', args.remote, patchset_ref, '--quiet'])
        else:
            __call(['git', 'fetch', args.remote, patchset_ref])

    return


def checkout(args):
    """
    checkout(args)

    Checkout the patchset on a named branch.
    """

    __resolve_uncommitted_changes_checkout(args)
    fetch(args)

    # collect local branch names
    branches = []
    for branch in __call(['git', 'branch']):
        if branch[0] == '*':
            branches.append(branch[1:].strip())
        else:
            branches.append(branch.strip())

    if args.patchset is None or args.patchset == 0:
        branch = 'ticket/{:d}'.format(args.id)
        illegals = set(branches) & {'ticket'}
    else:
        branch = 'patchset/{:d}/{:d}'.format(args.id, args.patchset)
        illegals = set(branches) & {'patchset', 'patchset/{:d}'.format(args.id)}

    # ensure there are no local branch names that will interfere with branch creation
    if len(illegals) > 0:
        print('')
        print('Sorry, can not complete the checkout for ticket {}.'.format(args.id))
        print("The following branches are blocking '{}' branch creation:".format(branch))
        for illegal in illegals:
            print('  ' + illegal)
        exit(errno.EINVAL)

    if args.patchset is None or args.patchset == 0:
        # checkout the current ticket patchset
        if args.force:
            __call(['git', 'checkout', '-B', branch, '{}/{}'.format(args.remote, branch)])
        else:
            __call(['git', 'checkout', branch])
    else:
        # checkout a specific patchset
        __checkout(args.remote, args.id, args.patchset, branch, args.force)

    return


def pull(args):
    """
    pull(args)

    Pull (fetch & merge) a ticket patchset into the current branch.
    """

    __resolve_uncommitted_changes_checkout(args)
    __resolve_remote(args)

    # reset the checkout before pulling
    __call(['git', 'reset', '--hard'])

    # pull the patchset from the remote repository
    if args.patchset is None or args.patchset == 0:
        print("Pulling ticket {} from the '{}' repository".format(args.id, args.remote))
        patchset_ref = 'ticket/{:d}'.format(args.id)
    else:
        __resolve_patchset(args)
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
                if len(segments) >= 2:
                    if segments[0] == 'ticket' or segments[0] == 'patchset':
                        if '...' in segments[1]:
                            args.id = int(segments[1][:segments[1].index('...')])
                        else:
                            args.id = int(segments[1])
                        args.patchset = None

    if args.id is None:
        print('Please specify a ticket id for the push command.')
        exit(errno.EINVAL)

    __resolve_uncommitted_changes_push(args)
    __resolve_remote(args)

    if args.force:
       # rewrite a patchset for an existing ticket
        push_ref = 'refs/for/' + str(args.id)
    else:
        # fast-forward update to an existing patchset
        push_ref = 'refs/heads/ticket/{:d}'.format(args.id)

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
    try:
        int(args.topic)
        branch = 'ticket/' + args.topic
    except ValueError:
        pass

    illegals = set(branches) & {'topic', branch}

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

    __resolve_uncommitted_changes_push(args)
    __resolve_remote(args)

    curr_branch = None
    push_ref = None
    if args.target is None:
        # see if the topic is a ticket id
        # else default to new
        for branch in __call(['git', 'branch']):
            if branch[0] == '*':
                curr_branch = branch[1:].strip()
                if curr_branch.startswith('topic/'):
                    topic = curr_branch[6:].strip()
                    try:
                        int(topic)
                        push_ref = topic
                    except ValueError:
                        pass
                if curr_branch.startswith('ticket/'):
                    topic = curr_branch[7:].strip()
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
                    if len(segments) >= 2:
                        if segments[0] == 'ticket':
                            if '...' in segments[1]:
                                args.id = int(segments[1][:segments[1].index('...')])
                            else:
                                args.id = int(segments[1])
                            args.patchset = None
                            print("You are on the '{}' branch, perhaps you meant to push instead?".format(branch))
                        elif segments[0] == 'patchset':
                            args.id = int(segments[1])
                            args.patchset = int(segments[2])
                            print("You are on the '{}' branch, perhaps you meant to push instead?".format(branch))
            exit(errno.EINVAL)
    except ValueError:
        pass

    ref_params = __get_pushref_params(args)
    ref_spec = 'HEAD:refs/for/{}{}'.format(push_ref, ref_params)

    print("Pushing your proposal to the '{}' repository".format(args.remote))
    for line in __call(['git', 'push', args.remote, ref_spec, '-q'], echo=True, err=subprocess.STDOUT):
        fields = line.split(':')
        if fields[0] == 'remote' and fields[1].strip().startswith('--> #'):
            # set the upstream branch configuration
            args.id = int(fields[1].strip()[len('--> #'):])
            __call(['git', 'fetch', '-p', args.remote])
            __call(['git', 'branch', '-u', '{}/ticket/{:d}'.format(args.remote, args.id)])
            break

    return


def cleanup(args):
    """
    cleanup(args)

    Removes local branches for the ticket.
    """

    if args.id is None:
        branches = __call(['git', 'branch', '--list', 'ticket/*'])
        branches += __call(['git', 'branch', '--list', 'patchset/*'])
    else:
        branches = __call(['git', 'branch', '--list', 'ticket/{:d}'.format(args.id)])
        branches += __call(['git', 'branch', '--list', 'patchset/{:d}/*'.format(args.id)])

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


def __resolve_uncommitted_changes_checkout(args):
    """
    __resolve_uncommitted_changes_checkout(args)

    Ensures the current checkout has no uncommitted changes that would be discarded by a checkout or pull.
    """

    status = __call(['git', 'status', '--porcelain'])
    for line in status:
        if not args.force and line[0] != '?':
            print('Your local changes to the following files would be overwritten by {}:'.format(args.command))
            print('')
            for state in status:
                print(state)
            print('')
            print("To discard your local changes, repeat the {} with '--force'.".format(args.command))
            print('NOTE: forcing a {} will HARD RESET your working directory!'.format(args.command))
            exit(errno.EINVAL)


def __resolve_uncommitted_changes_push(args):
    """
    __resolve_uncommitted_changes_push(args)

    Ensures the current checkout has no uncommitted changes that should be part of a propose or push.
    """

    status = __call(['git', 'status', '--porcelain'])
    for line in status:
        if not args.ignore and line[0] != '?':
            print('You have local changes that have not been committed:')
            print('')
            for state in status:
                print(state)
            print('')
            print("To ignore these uncommitted changes, repeat the {} with '--ignore'.".format(args.command))
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
        preferred = output[0] if len(output) > 0 else ''

        if len(preferred) == 0:
            print("You have multiple remote repositories and you have not configured 'patchsets.remote'.")
            print("")
            print("Available remote repositories:")
            for remote in remotes:
                print('  ' + remote)
            print("")
            print("Please set the remote repository to use for patchsets.")
            print("  git config --local patchsets.remote <remote>")
            exit(errno.EINVAL)
        else:
            try:
                remotes.index(preferred)
            except ValueError:
                print("The '{}' repository specified in 'patchsets.remote' is not configured!".format(preferred))
                print("")
                print("Available remotes:")
                for remote in remotes:
                    print('  ' + remote)
                print("")
                print("Please set the remote repository to use for patchsets.")
                print("  git config --local patchsets.remote <remote>")
                exit(errno.EINVAL)

            args.remote = preferred
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
            if len(merge) == 1:
                up_to_date = merge[0].lower().index('up-to-date') > 0
                if up_to_date:
                    return
            elif len(merge) == 0:
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
                    topic = b[len('topic/'):]
                    try:
                        # ignore ticket id topics
                        int(topic)
                    except:
                        # topic is a string
                        params.append('t=' + topic)

    if args.responsible is not None:
        params.append('r=' + args.responsible)

    if args.cc is not None:
        for cc in args.cc:
            params.append('cc=' + cc)

    if len(params) > 0:
        return '%' + ','.join(params)

    return ''


def __call(cmd_args, echo=False, fail=True, err=None):
    """
    __call(cmd_args)

    Executes the specified command as a subprocess.  The output is parsed and returned as a list
    of strings.  If the process returns a non-zero exit code, the script terminates with that
    exit code.  Std err of the subprocess is passed-through to the std err of the parent process.
    """

    p = subprocess.Popen(cmd_args, stdout=subprocess.PIPE, stderr=err, universal_newlines=True)
    lines = []
    for line in iter(p.stdout.readline, b''):
        line_str = str(line).strip()
        if len(line_str) == 0:
            break
        lines.append(line_str)
        if echo:
            print(line_str)
    p.wait()
    if fail and p.returncode != 0:
        exit(p.returncode)

    return lines

#
# define the acceptable arguments and their usage/descriptions
#

# force argument
force_arg = argparse.ArgumentParser(add_help=False)
force_arg.add_argument('-f', '--force', default=False, help='force the command to complete', action='store_true')

# quiet argument
quiet_arg = argparse.ArgumentParser(add_help=False)
quiet_arg.add_argument('-q', '--quiet', default=False, help='suppress git stderr output', action='store_true')

# ticket & patchset arguments
ticket_args = argparse.ArgumentParser(add_help=False)
ticket_args.add_argument('id', help='the ticket id', type=int)
ticket_args.add_argument('-p', '--patchset', help='the patchset number', type=int)

# push refspec arguments
push_args = argparse.ArgumentParser(add_help=False)
push_args.add_argument('-i', '--ignore', default=False, help='ignore uncommitted changes', action='store_true')
push_args.add_argument('-m', '--milestone', help='set the milestone')
push_args.add_argument('-r', '--responsible', help='set the responsible user')
push_args.add_argument('-t', '--topic', help='set the topic')
push_args.add_argument('-cc', nargs='+', help='specify accounts to add to the watch list')

# the commands
parser = argparse.ArgumentParser(description='a Patchset Tool for Gitblit Tickets')
parser.add_argument('--version', action='version', version='%(prog)s {}'.format(__version__))
commands = parser.add_subparsers(dest='command', title='commands')

fetch_parser = commands.add_parser('fetch', help='fetch a patchset', parents=[ticket_args, quiet_arg])
fetch_parser.set_defaults(func=fetch)

checkout_parser = commands.add_parser('checkout', aliases=['co'],
                                      help='fetch & checkout a patchset to a branch',
                                      parents=[ticket_args, force_arg, quiet_arg])
checkout_parser.set_defaults(func=checkout)

pull_parser = commands.add_parser('pull',
                                  help='fetch & merge a patchset into the current branch',
                                  parents=[ticket_args, force_arg])
pull_parser.add_argument('-s', '--squash',
                         help='squash the pulled patchset into your working directory',
                         default=False,
                         action='store_true')
pull_parser.set_defaults(func=pull)

push_parser = commands.add_parser('push', aliases=['up'],
                                  help='upload your patchset changes',
                                  parents=[push_args, force_arg])
push_parser.add_argument('id', help='the ticket id', nargs='?', type=int)
push_parser.set_defaults(func=push)

propose_parser = commands.add_parser('propose', help='propose a new ticket or the first patchset', parents=[push_args])
propose_parser.add_argument('target', help="the ticket id, 'new', or the integration branch", nargs='?')
propose_parser.set_defaults(func=propose)

cleanup_parser = commands.add_parser('cleanup', aliases=['rm'],
                                     help='remove local ticket branches',
                                     parents=[force_arg])
cleanup_parser.add_argument('id', help='the ticket id', nargs='?', type=int)
cleanup_parser.set_defaults(func=cleanup)

start_parser = commands.add_parser('start', help='start a new branch for the topic or ticket')
start_parser.add_argument('topic', help="the topic or ticket id")
start_parser.set_defaults(func=start)

if len(sys.argv) < 2:
    parser.parse_args(['--help'])
else:
    # parse the command-line arguments
    script_args = parser.parse_args()

    # exec the specified command
    script_args.func(script_args)
