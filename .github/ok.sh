#!/usr/bin/env sh
# # A GitHub API client library written in POSIX sh
#
# https://github.com/whiteinge/ok.sh
# BSD licensed.
#
# ## Requirements
#
# * A POSIX environment (tested against Busybox v1.19.4)
# * curl (tested against 7.32.0)
#
# ## Optional requirements
#
# * jq <http://stedolan.github.io/jq/> (tested against 1.3)
#   If jq is not installed commands will output raw JSON; if jq is installed
#   the output will be formatted and filtered for use with other shell tools.
#
# ## Setup
#
# Authentication credentials are read from a `$HOME/.netrc` file on UNIX
# machines or a `_netrc` file in `%HOME%` for UNIX environments under Windows.
# [Generate the token on GitHub](https://github.com/settings/tokens) under
# "Account Settings -> Applications".
# Restrict permissions on that file with `chmod 600 ~/.netrc`!
#
#     machine api.github.com
#         login <username>
#         password <token>
#
#     machine uploads.github.com
#         login <username>
#         password <token>
#
# Or set an environment `GITHUB_TOKEN=token`
#
# ## Configuration
#
# The following environment variables may be set to customize ${NAME}.
#
# * OK_SH_URL=${OK_SH_URL}
#   Base URL for GitHub or GitHub Enterprise.
# * OK_SH_ACCEPT=${OK_SH_ACCEPT}
#   The 'Accept' header to send with each request.
# * OK_SH_JQ_BIN=${OK_SH_JQ_BIN}
#   The name of the jq binary, if installed.
# * OK_SH_VERBOSE=${OK_SH_VERBOSE}
#   The debug logging verbosity level. Same as the verbose flag.
# * OK_SH_RATE_LIMIT=${OK_SH_RATE_LIMIT}
#   Output current GitHub rate limit information to stderr.
# * OK_SH_DESTRUCTIVE=${OK_SH_DESTRUCTIVE}
#   Allow destructive operations without prompting for confirmation.
# * OK_SH_MARKDOWN=${OK_SH_MARKDOWN}
#   Output some text in Markdown format.

# shellcheck disable=SC2039,SC2220

NAME=$(basename "$0")
export NAME
export VERSION='0.7.0'

export OK_SH_URL=${OK_SH_URL:-'https://api.github.com'}
export OK_SH_ACCEPT=${OK_SH_ACCEPT:-'application/vnd.github.v3+json'}
export OK_SH_JQ_BIN="${OK_SH_JQ_BIN:-jq}"
export OK_SH_VERBOSE="${OK_SH_VERBOSE:-0}"
export OK_SH_RATE_LIMIT="${OK_SH_RATE_LIMIT:-0}"
export OK_SH_DESTRUCTIVE="${OK_SH_DESTRUCTIVE:-0}"
export OK_SH_MARKDOWN="${OK_SH_MARKDOWN:-0}"

# Detect if jq is installed.
command -v "$OK_SH_JQ_BIN" 1>/dev/null 2>/dev/null
NO_JQ=$?

# Customizable logging output.
exec 4>/dev/null
exec 5>/dev/null
exec 6>/dev/null
export LINFO=4      # Info-level log messages.
export LDEBUG=5     # Debug-level log messages.
export LSUMMARY=6   # Summary output.

# Generate a carriage return so we can match on it.
# Using a variable because these are tough to specify in a portable way.
crlf=$(printf '\r\n')

# ## Main
# Generic functions not necessarily specific to working with GitHub.

# ### Help
# Functions for fetching and formatting help text.

 _cols() {
    sort | awk '
        { w[NR] = $0 }
        END {
            cols = 3
            per_col = sprintf("%.f", NR / cols + 0.5)  # Round up if decimal.

            for (i = 1; i < per_col + 1; i += 1) {
                for (j = 0; j < cols; j += 1) {
                    printf("%-24s", w[i + per_col * j])
                }
                printf("\n")
            }
        }
    '
 }
 _links() { awk '{ print "* [" $0 "](#" $0 ")" }'; }
 _funcsfmt() { if [ "$OK_SH_MARKDOWN" -eq 0 ]; then _cols; else _links; fi; }

help() {
    # Output the help text for a command
    #
    # Usage:
    #
    #     help commandname
    #
    # Positional arguments
    #
    local fname="$1"
    #   Function name to search for; if omitted searches whole file.

    # Short-circuit if only producing help for a single function.
    if [ $# -gt 0 ]; then
        awk -v fname="^$fname\\\(\\\) \\\{$" '$0 ~ fname, /^}/ { print }' "$0" \
            | _helptext
        return
    fi

    _helptext < "$0"
    printf '\n'
    help __main
    printf '\n'

    printf '## Table of Contents\n'
    printf '\n### Utility and request/response commands\n\n'
    _all_funcs public=0 | _funcsfmt
    printf '\n### GitHub commands\n\n'
    _all_funcs private=0 | _funcsfmt
    printf '\n## Commands\n\n'

    for cmd in $(_all_funcs public=0); do
        printf '### %s\n\n' "$cmd"
        help "$cmd"
        printf '\n'
    done

    for cmd in $(_all_funcs private=0); do
        printf '### %s\n\n' "$cmd"
        help "$cmd"
        printf '\n'
    done
}

_all_funcs() {
    # List all functions found in the current file in the order they appear
    #
    # Keyword arguments
    #
    local public=1
    #   `0` do not output public functions.
    local private=1
    #   `0` do not output private functions.

    for arg in "$@"; do
        case $arg in
            (public=*) public="${arg#*=}";;
            (private=*) private="${arg#*=}";;
        esac
    done

    awk -v public="$public" -v private="$private" '
        $1 !~ /^__/ && /^[a-zA-Z0-9_]+\s*\(\)/ {
            sub(/\(\)$/, "", $1)
            if (!public && substr($1, 1, 1) != "_") next
            if (!private && substr($1, 1, 1) == "_") next
            print $1
        }
    ' "$0"
}

__main() {
    # ## Usage
    #
    # `${NAME} [<flags>] (command [<arg>, <name=value>...])`
    #
    #     ${NAME} -h              # Short, usage help text.
    #     ${NAME} help            # All help text. Warning: long!
    #     ${NAME} help command    # Command-specific help text.
    #     ${NAME} command         # Run a command with and without args.
    #     ${NAME} command foo bar baz=Baz qux='Qux arg here'
    #
    # Flag | Description
    # ---- | -----------
    # -V   | Show version.
    # -h   | Show this screen.
    # -j   | Output raw JSON; don't process with jq.
    # -q   | Quiet; don't print to stdout.
    # -r   | Print current GitHub API rate limit to stderr.
    # -v   | Logging output; specify multiple times: info, debug, trace.
    # -x   | Enable xtrace debug logging.
    # -y   | Answer 'yes' to any prompts.
    #
    # Flags _must_ be the first argument to `${NAME}`, before `command`.

    local cmd
    local ret
    local opt
    local OPTARG
    local OPTIND
    local quiet=0
    local temp_dir
    temp_dir="${TMPDIR-/tmp}/${NAME}.${$}.$(awk 'BEGIN {srand(); printf "%d\n", rand() * 10^10}')"
    local summary_fifo="${temp_dir}/oksh_summary.fifo"

    # shellcheck disable=SC2154
    trap '
        excode=$?; trap - EXIT;
        exec 4>&-
        exec 5>&-
        exec 6>&-
        rm -rf '"$temp_dir"'
        exit $excode
    ' INT TERM EXIT

    while getopts Vhjqrvxy opt; do
        case $opt in
        V)  printf 'Version: %s\n' $VERSION
            exit;;
        h) help __main
            printf '\nAvailable commands:\n\n'
            _all_funcs private=0 | _cols
            printf '\n'
            exit;;
        j)  NO_JQ=1;;
        q)  quiet=1;;
        r)  OK_SH_RATE_LIMIT=1;;
        v)  OK_SH_VERBOSE=$(( OK_SH_VERBOSE + 1 ));;
        x)  set -x;;
        y)  OK_SH_DESTRUCTIVE=1;;
        esac
    done
    shift $(( OPTIND - 1 ))

    if [ -z "$1" ] ; then
        printf 'No command given. Available commands:\n\n%s\n' \
            "$(_all_funcs private=0 | _cols)" 1>&2
        exit 1
    fi

    [ $OK_SH_VERBOSE -gt 0 ] && exec 4>&2
    [ $OK_SH_VERBOSE -gt 1 ] && exec 5>&2
    if [ $quiet -eq 1 ]; then
        exec 1>/dev/null 2>/dev/null
    fi

    if [ "$OK_SH_RATE_LIMIT" -eq 1 ] ; then
        mkdir -m 700 "$temp_dir" || {
            printf 'failed to create temp_dir\n' >&2; exit 1;
        }
        mkfifo "$summary_fifo"
        # Hold the fifo open so it will buffer input until emptied.
        exec 6<>"$summary_fifo"
    fi

    # Run the command.
    cmd="$1" && shift
    _log debug "Running command ${cmd}."
    "$cmd" "$@"
    ret=$?
    _log debug "Command ${cmd} exited with ${?}."

    # Output any summary messages.
    if [ "$OK_SH_RATE_LIMIT" -eq 1 ] ; then
        cat "$summary_fifo" 1>&2 &
        exec 6>&-
    fi

    exit $ret
}

_log() {
    # A lightweight logging system based on file descriptors
    #
    # Usage:
    #
    #     _log debug 'Starting the combobulator!'
    #
    # Positional arguments
    #
    local level="${1:?Level is required.}"
    #   The level for a given log message. (info or debug)
    local message="${2:?Message is required.}"
    #   The log message.

    shift 2

    local lname

    case "$level" in
        info) lname='INFO'; level=$LINFO ;;
        debug) lname='DEBUG'; level=$LDEBUG ;;
        *) printf 'Invalid logging level: %s\n' "$level" ;;
    esac

    printf '%s %s: %s\n' "$NAME" "$lname" "$message" 1>&$level
}

_helptext() {
    # Extract contiguous lines of comments and function params as help text
    #
    # Indentation will be ignored. She-bangs will be ignored. Local variable
    # declarations and their default values can also be pulled in as
    # documentation. Exits upon encountering the first blank line.
    #
    # Exported environment variables can be used for string interpolation in
    # the extracted commented text.
    #
    # Input
    #
    # * (stdin)
    #   The text of a function body to parse.

    awk '
    NR != 1 && /^\s*#/ {
        line=$0
        while(match(line, "[$]{[^}]*}")) {
            var=substr(line, RSTART+2, RLENGTH -3)
            gsub("[$]{"var"}", ENVIRON[var], line)
        }
        gsub(/^\s*#\s?/, "", line)
        print line
    }
    /^\s*local/ {
        sub(/^\s*local /, "")
        sub(/\$\{/, "$", $0)
        sub(/:.*}/, "", $0)
        print "* `" $0 "`\n"
    }
    !NF { exit }'
}

# ### Request-response
# Functions for making HTTP requests and processing HTTP responses.

_format_json() {
    # Create formatted JSON from name=value pairs
    #
    # Usage:
    # ```
    # ok.sh _format_json foo=Foo bar=123 baz=true qux=Qux=Qux quux='Multi-line
    # string' quuz=\'5.20170918\' \
    #   corge="$(ok.sh _format_json grault=Grault)" \
    #   garply="$(ok.sh _format_json -a waldo true 3)"
    # ```
    #
    # Return:
    # ```
    # {
    #   "garply": [
    #     "waldo",
    #     true,
    #     3
    #   ],
    #   "foo": "Foo",
    #   "corge": {
    #     "grault": "Grault"
    #   },
    #   "baz": true,
    #   "qux": "Qux=Qux",
    #   "quux": "Multi-line\nstring",
    #   "quuz": "5.20170918",
    #   "bar": 123
    # }
    # ```
    #
    # Tries not to quote numbers, booleans, nulls, or nested structures.
    # Note, nested structures must be quoted since the output contains spaces.
    #
    # The `-a` option will create an array instead of an object. This option
    # must come directly after the _format_json command and before any
    # operands. E.g., `_format_json -a foo bar baz`.
    #
    # If jq is installed it will also validate the output.
    #
    # Positional arguments
    #
    # * $1 - $9
    #
    #   Each positional arg must be in the format of `name=value` which will be
    #   added to a single, flat JSON object.

    local opt
    local OPTIND
    local is_array=0
    local use_env=1
    while getopts a opt; do
        case $opt in
        a)  is_array=1; unset use_env;;
        esac
    done
    shift $(( OPTIND - 1 ))

    _log debug "Formatting ${#} parameters as JSON."

    env -i -- ${use_env+"$@"} awk -v is_array="$is_array" '
    function isnum(x){ return (x == x + 0) }
    function isnull(x){ return (x == "null" ) }
    function isbool(x){ if (x == "true" || x == "false") return 1 }
    function isnested(x) {
      if (substr(x, 0, 1) == "[" || substr(x, 0, 1) == "{") {
        return 1
      }
    }
    function castOrQuote(val) {
        if (!isbool(val) && !isnum(val) && !isnull(val) && !isnested(val)) {
            sub(/^('\''|")/, "", val) # Remove surrounding quotes
            sub(/('\''|")$/, "", val)

            gsub(/"/, "\\\"", val)  # Escape double-quotes.
            gsub(/\n/, "\\n", val)  # Replace newlines with \n text.
            val = "\"" val "\""
            return val
        } else {
            return val
        }
    }

    BEGIN {
        printf("%s", is_array ? "[" : "{")

        for (i = 1; i < length(ARGV); i += 1) {
            arg = ARGV[i]

            if (is_array == 1) {
                val = castOrQuote(arg)
                printf("%s%s", sep, val)
            } else {
                name = substr(arg, 0, index(arg, "=") - 1)
                val = castOrQuote(ENVIRON[name])
                printf("%s\"%s\": %s", sep, name, val)
            }

            sep = ", "
            ARGV[i] = ""
        }
        printf("%s", is_array ? "]" : "}")
    }' "$@"
}

_format_urlencode() {
    # URL encode and join name=value pairs
    #
    # Usage:
    # ```
    # _format_urlencode foo='Foo Foo' bar='<Bar>&/Bar/'
    # ```
    #
    # Return:
    # ```
    # foo=Foo%20Foo&bar=%3CBar%3E%26%2FBar%2F
    # ```
    #
    # Ignores pairs if the value begins with an underscore.

    _log debug "Formatting ${#} parameters as urlencoded"

    env -i -- "$@" awk '
    function escape(str, c, i, len, res) {
        len = length(str)
        res = ""
        for (i = 1; i <= len; i += 1) {
            c = substr(str, i, 1);
            if (c ~ /[0-9A-Za-z]/)
                res = res c
            else
                res = res "%" sprintf("%02X", ord[c])
        }
        return res
    }

    BEGIN {
        for (i = 0; i <= 255; i += 1) ord[sprintf("%c", i)] = i;

        for (j = 1; j < length(ARGV); j += 1) {
            arg = ARGV[j]
            name = substr(arg, 0, index(arg, "=") - 1)
            if (substr(name, 1, 1) == "_") continue
            val = ENVIRON[name]

            printf("%s%s=%s", sep, name, escape(val))
            sep = "&"
            ARGV[j] = ""
        }
    }' "$@"
}

_filter_json() {
    # Filter JSON input using jq; outputs raw JSON if jq is not installed
    #
    # Usage:
    #
    #     printf '[{"foo": "One"}, {"foo": "Two"}]' | \
    #         ok.sh _filter_json '.[] | "\(.foo)"'
    #
    # * (stdin)
    #   JSON input.
    local _filter="$1"
    #   A string of jq filters to apply to the input stream.

    _log debug 'Filtering JSON.'

    if [ $NO_JQ -ne 0 ] ; then
        _log debug 'Bypassing jq processing.'
        cat
        return
    fi

    "${OK_SH_JQ_BIN}" -c -r "${_filter}" || printf 'jq parse error; invalid JSON.\n' 1>&2
}

_get_mime_type() {
    # Guess the mime type for a file based on the file extension
    #
    # Usage:
    #
    #     local mime_type
    #     _get_mime_type "foo.tar"; printf 'mime is: %s' "$mime_type"
    #
    # Sets the global variable `mime_type` with the result. (If this function
    # is called from within a function that has declared a local variable of
    # that name it will update the local copy and not set a global.)
    #
    # Positional arguments
    #
    local filename="${1:?Filename is required.}"
    #   The full name of the file, with extension.

    # Taken from Apache's mime.types file (public domain).
    case "$filename" in
        *.bz2) mime_type=application/x-bzip2 ;;
        *.exe) mime_type=application/x-msdownload ;;
        *.tar.gz | *.gz | *.tgz) mime_type=application/x-gzip ;;
        *.jpg | *.jpeg | *.jpe | *.jfif) mime_type=image/jpeg ;;
        *.json) mime_type=application/json ;;
        *.pdf) mime_type=application/pdf ;;
        *.png) mime_type=image/png ;;
        *.rpm) mime_type=application/x-rpm ;;
        *.svg | *.svgz) mime_type=image/svg+xml ;;
        *.tar) mime_type=application/x-tar ;;
        *.txt) mime_type=text/plain ;;
        *.yaml) mime_type=application/x-yaml ;;
        *.apk) mime_type=application/vnd.android.package-archive ;;
        *.zip) mime_type=application/zip ;;
        *.jar) mime_type=application/java-archive ;;
        *.war) mime_type=application/zip ;;
    esac

    _log debug "Guessed mime type of '${mime_type}' for '${filename}'."
}

_get_confirm() {
    # Prompt the user for confirmation
    #
    # Usage:
    #
    #     local confirm; _get_confirm
    #     [ "$confirm" -eq 1 ] && printf 'Good to go!\n'
    #
    # If global confirmation is set via `$OK_SH_DESTRUCTIVE` then the user
    # is not prompted. Assigns the user's confirmation to the `confirm` global
    # variable. (If this function is called within a function that has a local
    # variable of that name, the local variable will be updated instead.)
    #
    # Positional arguments
    #
    local message="${1:-Are you sure?}"
    #   The message to prompt the user with.

    local answer

    if [ "$OK_SH_DESTRUCTIVE" -eq 1 ] ; then
        confirm=$OK_SH_DESTRUCTIVE
        return
    fi

    printf '%s ' "$message"
    read -r answer

    ! printf '%s\n' "$answer" | grep -Eq "$(locale yesexpr)"
    confirm=$?
}

_opts_filter() {
    # Extract common jq filter keyword options and assign to vars
    #
    # Usage:
    #
    #     local filter
    #     _opts_filter "$@"

    for arg in "$@"; do
        case $arg in
            (_filter=*) _filter="${arg#*=}";;
        esac
    done
}

_opts_pagination() {
    # Extract common pagination keyword options and assign to vars
    #
    # Usage:
    #
    #     local _follow_next
    #     _opts_pagination "$@"

    for arg in "$@"; do
        case $arg in
            (_follow_next=*) _follow_next="${arg#*=}";;
            (_follow_next_limit=*) _follow_next_limit="${arg#*=}";;
        esac
    done
}

_opts_qs() {
    # Extract common query string keyword options and assign to vars
    #
    # Usage:
    #
    #     local qs
    #     _opts_qs "$@"
    #     _get "/some/path${qs}"

    local querystring
    querystring=$(_format_urlencode "$@")
    qs="${querystring:+?$querystring}"
}

_request() {
    # A wrapper around making HTTP requests with curl
    #
    # Usage:
    # ```
    # # Get JSON for all issues:
    # _request /repos/saltstack/salt/issues
    #
    # # Send a POST request; parse response using jq:
    # printf '{"title": "%s", "body": "%s"}\n' "Stuff" "Things" \
    #   | _request /some/path | jq -r '.[url]'
    #
    # # Send a PUT request; parse response using jq:
    # printf '{"title": "%s", "body": "%s"}\n' "Stuff" "Things" \
    #   | _request /repos/:owner/:repo/issues method=PUT | jq -r '.[url]'
    #
    # # Send a conditional-GET request:
    # _request /users etag=edd3a0d38d8c329d3ccc6575f17a76bb
    # ```
    #
    # Input
    #
    # * (stdin)
    #   Data that will be used as the request body.
    #
    # Positional arguments
    #
    local path="${1:?Path is required.}"
    #   The URL path for the HTTP request.
    #   Must be an absolute path that starts with a `/` or a full URL that
    #   starts with http(s). Absolute paths will be append to the value in
    #   `$OK_SH_URL`.
    #
    # Keyword arguments
    #
    local method='GET'
    #   The method to use for the HTTP request.
    local content_type='application/json'
    #   The value of the Content-Type header to use for the request.
    local etag
    #   An optional Etag to send as the If-None-Match header.

    shift 1

    local cmd
    local arg
    local has_stdin
    local trace_curl

    case $path in
        (http*) : ;;
        *) path="${OK_SH_URL}${path}" ;;
    esac

    for arg in "$@"; do
        case $arg in
            (method=*) method="${arg#*=}";;
            (content_type=*) content_type="${arg#*=}";;
            (etag=*) etag="${arg#*=}";;
        esac
    done

    case "$method" in
        POST | PUT | PATCH) has_stdin=1;;
    esac

    [ $OK_SH_VERBOSE -eq 3 ] && trace_curl=1

    [ "$OK_SH_VERBOSE" -eq 1 ] && set -x
    # shellcheck disable=SC2086
    curl -nsSig \
        -H "Accept: ${OK_SH_ACCEPT}" \
        -H "Content-Type: ${content_type}" \
        ${GITHUB_TOKEN:+-H "Authorization: token ${GITHUB_TOKEN}"} \
        ${etag:+-H "If-None-Match: \"${etag}\""} \
        ${has_stdin:+--data-binary @-} \
        ${trace_curl:+--trace-ascii /dev/stderr} \
        -X "${method}" \
        "${path}"
    set +x
}

_response() {
    # Process an HTTP response from curl
    #
    # Output only headers of interest followed by the response body. Additional
    # processing is performed on select headers to make them easier to parse
    # using shell tools.
    #
    # Usage:
    # ```
    # # Send a request; output the response and only select response headers:
    # _request /some/path | _response status_code ETag Link_next
    #
    # # Make request using curl; output response with select response headers;
    # # assign response headers to local variables:
    # curl -isS example.com/some/path | _response status_code status_text | {
    #   local status_code status_text
    #   read -r status_code
    #   read -r status_text
    # }
    # ```
    #
    # Header reformatting
    #
    # * HTTP Status
    #
    #   The HTTP line is split into separate `http_version`, `status_code`, and
    #   `status_text` variables.
    #
    # * ETag
    #
    #   The surrounding quotes are removed.
    #
    # * Link
    #
    #   Each URL in the Link header is expanded with the URL type appended to
    #   the name. E.g., `Link_first`, `Link_last`, `Link_next`.
    #
    # Positional arguments
    #
    # * $1 - $9
    #
    #   Each positional arg is the name of an HTTP header. Each header value is
    #   output in the same order as each argument; each on a single line. A
    #   blank line is output for headers that cannot be found.

    local hdr
    local val
    local http_version
    local status_code=100
    local status_text
    local headers output

    _log debug 'Processing response.'

    while [ "${status_code}" = "100" ]; do
        read -r http_version status_code status_text
        status_text="${status_text%${crlf}}"
        http_version="${http_version#HTTP/}"

        _log debug "Response status is: ${status_code} ${status_text}"

        if [ "${status_code}" = "100" ]; then
            _log debug "Ignoring response '${status_code} ${status_text}', skipping to real response."
            while IFS=": " read -r hdr val; do
                # Headers stop at the first blank line.
                [ "$hdr" = "$crlf" ] && break
                val="${val%${crlf}}"
                _log debug "Unexpected additional header: ${hdr}: ${val}"
            done

        fi
    done

    headers="HTTP_VERSION: ${http_version}
STATUS_CODE: ${status_code}
STATUS_TEXT: ${status_text}
"
    while IFS=": " read -r hdr val; do
        # Headers stop at the first blank line.
        [ "$hdr" = "$crlf" ] && break
        val="${val%${crlf}}"

        # Headers are case insensitive
        hdr="$(printf '%s' "$hdr" | awk '{print toupper($0)}')"

        # Process each header; reformat some to work better with sh tools.
        case "$hdr" in
            # Update the GitHub rate limit trackers.
            X-RATELIMIT-REMAINING)
                printf 'GitHub remaining requests: %s\n' "$val" 1>&$LSUMMARY ;;
            X-RATELIMIT-RESET)
                awk -v gh_reset="$val" 'BEGIN {
                    srand(); curtime = srand()
                    print "GitHub seconds to reset: " gh_reset - curtime
                }' 1>&$LSUMMARY ;;

            # Remove quotes from the etag header.
            ETAG) val="${val#\"}"; val="${val%\"}" ;;

            # Split the URLs in the Link header into separate pseudo-headers.
            LINK) headers="${headers}$(printf '%s' "$val" | awk '
                BEGIN { RS=", "; FS="; "; OFS=": " }
                {
                    sub(/^rel="/, "", $2); sub(/"$/, "", $2)
                    sub(/^ *</, "", $1); sub(/>$/, "", $1)
                    print "LINK_" toupper($2), $1
                }')
"  # need trailing newline
            ;;
        esac

        headers="${headers}${hdr}: ${val}
"  # need trailing newline

    done

    # Output requested headers in deterministic order.
    for arg in "$@"; do
        _log debug "Outputting requested header '${arg}'."
        arg="$(printf '%s' "$arg" | awk '{print toupper($0)}')"
        output=$(printf '%s' "$headers" | while IFS=": " read -r hdr val; do
            [ "$hdr" = "$arg" ] && printf '%s' "$val"
        done)
        printf '%s\n' "$output"
    done

    # Output the response body.
    cat
}

_get() {
    # A wrapper around _request() for common GET patterns
    #
    # Will automatically follow 'next' pagination URLs in the Link header.
    #
    # Usage:
    #
    #     _get /some/path
    #     _get /some/path _follow_next=0
    #     _get /some/path _follow_next_limit=200 | jq -c .
    #
    # Positional arguments
    #
    local path="${1:?Path is required.}"
    #   The HTTP path or URL to pass to _request().
    #
    # Keyword arguments
    #
    # * _follow_next=1
    #
    #   Automatically look for a 'Links' header and follow any 'next' URLs.
    #
    # * _follow_next_limit=50
    #
    #   Maximum number of 'next' URLs to follow before stopping.

    shift 1
    local status_code
    local status_text
    local next_url

    # If the variable is unset or empty set it to a default value. Functions
    # that call this function can pass these parameters in one of two ways:
    # explicitly as a keyword arg or implicitly by setting variables of the same
    # names within the local scope.
    # shellcheck disable=SC2086
    if [ -z ${_follow_next+x} ] || [ -z "${_follow_next}" ]; then
        local _follow_next=1
    fi
    # shellcheck disable=SC2086
    if [ -z ${_follow_next_limit+x} ] || [ -z "${_follow_next_limit}" ]; then
        local _follow_next_limit=50
    fi

    _opts_pagination "$@"

    _request "$path" | _response status_code status_text Link_next | {
        read -r status_code
        read -r status_text
        read -r next_url

        case "$status_code" in
            20*) : ;;
            4*) printf 'Client Error: %s %s\n' \
                "$status_code" "$status_text" 1>&2; exit 1 ;;
            5*) printf 'Server Error: %s %s\n' \
                "$status_code" "$status_text" 1>&2; exit 1 ;;
        esac

        # Output response body.
        cat

        [ "$_follow_next" -eq 1 ] || return

        _log info "Remaining next link follows: ${_follow_next_limit}"
        if [ -n "$next_url" ] && [ $_follow_next_limit -gt 0 ] ; then
            _follow_next_limit=$(( _follow_next_limit - 1 ))

            _get "$next_url" "_follow_next_limit=${_follow_next_limit}"
        fi
    }
}

_post() {
    # A wrapper around _request() for common POST / PUT patterns
    #
    # Usage:
    #
    #     _format_json foo=Foo bar=Bar | _post /some/path
    #     _format_json foo=Foo bar=Bar | _post /some/path method='PUT'
    #     _post /some/path filename=somearchive.tar
    #     _post /some/path filename=somearchive.tar mime_type=application/x-tar
    #     _post /some/path filename=somearchive.tar \
    #       mime_type=$(file -b --mime-type somearchive.tar)
    #
    # Input
    #
    # * (stdin)
    #   Optional. See the `filename` argument also.
    #   Data that will be used as the request body.
    #
    # Positional arguments
    #
    local path="${1:?Path is required.}"
    #   The HTTP path or URL to pass to _request().
    #
    # Keyword arguments
    #
    local method='POST'
    #   The method to use for the HTTP request.
    local filename
    #   Optional. See the `stdin` option above also.
    #   Takes precedence over any data passed as stdin and loads a file off the
    #   file system to serve as the request body.
    local mime_type
    #   The value of the Content-Type header to use for the request.
    #   If the `filename` argument is given this value will be guessed from the
    #   file extension. If the `filename` argument is not given (i.e., using
    #   stdin) this value defaults to `application/json`. Specifying this
    #   argument overrides all other defaults or guesses.

    shift 1

    for arg in "$@"; do
        case $arg in
            (method=*) method="${arg#*=}";;
            (filename=*) filename="${arg#*=}";;
            (mime_type=*) mime_type="${arg#*=}";;
        esac
    done

    # Make either the file or stdin available as fd7.
    if [ -n "$filename" ] ; then
        if [ -r "$filename" ] ; then
            _log debug "Using '${filename}' as POST data."
            [ -n "$mime_type" ] || _get_mime_type "$filename"
            : ${mime_type:?The MIME type could not be guessed.}
            exec 7<"$filename"
        else
            printf 'File could not be found or read.\n' 1>&2
            exit 1
        fi
    else
        _log debug "Using stdin as POST data."
        mime_type='application/json'
        exec 7<&0
    fi

    _request "$path" method="$method" content_type="$mime_type" 0<&7 \
            | _response status_code status_text \
            | {
        read -r status_code
        read -r status_text

        case "$status_code" in
            20*) : ;;
            4*) printf 'Client Error: %s %s\n' \
                "$status_code" "$status_text" 1>&2; exit 1 ;;
            5*) printf 'Server Error: %s %s\n' \
                "$status_code" "$status_text" 1>&2; exit 1 ;;
        esac

        # Output response body.
        cat
    }
}

_delete() {
    # A wrapper around _request() for common DELETE patterns
    #
    # Usage:
    #
    #     _delete '/some/url'
    #
    # Return: 0 for success; 1 for failure.
    #
    # Positional arguments
    #
    local url="${1:?URL is required.}"
    #   The URL to send the DELETE request to.

    local status_code

    _request "${url}" method='DELETE' | _response status_code | {
        read -r status_code
        [ "$status_code" = "204" ]
        exit $?
    }
}

# ## GitHub
# Friendly functions for common GitHub tasks.

# ### Authorization
# Perform authentication and authorization.

show_scopes() {
    # Show the permission scopes for the currently authenticated user
    #
    # Usage:
    #
    #     show_scopes

    local oauth_scopes

    _request '/' | _response X-OAuth-Scopes | {
        read -r oauth_scopes

        printf '%s\n' "$oauth_scopes"

        # Dump any remaining response body.
        cat >/dev/null
    }
}

# ### Repository
# Create, update, delete, list repositories.

org_repos() {
    # List organization repositories
    #
    # Usage:
    #
    #     org_repos myorg
    #     org_repos myorg type=private per_page=10
    #     org_repos myorg _filter='.[] | "\(.name)\t\(.owner.login)"'
    #
    # Positional arguments
    #
    local org="${1:?Org name required.}"
    #   Organization GitHub login or id for which to list repos.
    #
    # Keyword arguments
    #
    local _follow_next
    #   Automatically look for a 'Links' header and follow any 'next' URLs.
    local _follow_next_limit
    #   Maximum number of 'next' URLs to follow before stopping.
    local _filter='.[] | "\(.name)\t\(.ssh_url)"'
    #   A jq filter to apply to the return data.
    #
    # Querystring arguments may also be passed as keyword arguments:
    #
    # * `per_page`
    # * `type`

    shift 1
    local qs

    _opts_pagination "$@"
    _opts_filter "$@"
    _opts_qs "$@"

    _get "/orgs/${org}/repos${qs}" | _filter_json "${_filter}"
}

org_teams() {
    # List teams
    #
    # Usage:
    #
    #     org_teams org
    #
    # Positional arguments
    #
    local org="${1:?Org name required.}"
    #   Organization GitHub login or id.
    #
    # Keyword arguments
    #
    local _filter='.[] | "\(.name)\t\(.id)\t\(.permission)"'
    #   A jq filter to apply to the return data.

    shift 1

    _opts_filter "$@"

    _get "/orgs/${org}/teams" \
        | _filter_json "${_filter}"
}

org_members() {
    # List organization members
    #
    # Usage:
    #
    #     org_members org
    #
    # Positional arguments
    #
    local org="${1:?Org name required.}"
    #   Organization GitHub login or id.
    #
    # Keyword arguments
    #
    local _filter='.[] | "\(.login)\t\(.id)"'
    #   A jq filter to apply to the return data.

    local qs

    shift 1

    _opts_filter "$@"
    _opts_qs "$@"

    _get "/orgs/${org}/members${qs}" | _filter_json "${_filter}"
}

org_collaborators() {
    # List organization outside collaborators
    #
    # Usage:
    #
    #     org_collaborators org
    #
    # Positional arguments
    #
    local org="${1:?Org name required.}"
    #   Organization GitHub login or id.
    #
    # Keyword arguments
    #
    local _filter='.[] | "\(.login)\t\(.id)"'
    #   A jq filter to apply to the return data.

    local qs

    shift 1

    _opts_filter "$@"
    _opts_qs "$@"

    _get "/orgs/${org}/outside_collaborators${qs}" | _filter_json "${_filter}"
}

org_auditlog() {
    # Interact with the Github Audit Log
    #
    # Usage:
    #
    #     org_auditlog org
    #
    # Positional arguments
    #
    local org="${1:?Org name required.}"
    #   Organization GitHub login or id.
    #
    # Keyword arguments
    #
    local _filter='.[] | "\(.actor)\t\(.action)"'
    #   A jq filter to apply to the return data.

    local qs

    shift 1

    _opts_filter "$@"
    _opts_qs "$@"

    _get "/orgs/${org}/audit-log${qs}" | _filter_json "${_filter}"
}

team_members() {
    # List team members
    #
    # Usage:
    #
    #     team_members team_id
    #
    # Positional arguments
    #
    local team_id="${1:?Team id required.}"
    #   Team id.
    #
    # Keyword arguments
    #
    local _filter='.[] | "\(.login)\t\(.id)"'
    #   A jq filter to apply to the return data.

    shift 1

    _opts_filter "$@"

    _get "/teams/${team_id}/members" \
        | _filter_json "${_filter}"

}

list_repos() {
    # List user repositories
    #
    # Usage:
    #
    #     list_repos
    #     list_repos user
    #
    # Positional arguments
    #
    local user="$1"
    #   Optional GitHub user login or id for which to list repos.
    #
    # Keyword arguments
    #
    local _filter='.[] | "\(.name)\t\(.html_url)"'
    #   A jq filter to apply to the return data.
    #
    # Querystring arguments may also be passed as keyword arguments:
    #
    # * `direction`
    # * `per_page`
    # * `sort`
    # * `type`

    # User is optional; is this a keyword arg?
    case "$user" in *=*) user='' ;; esac
    if [ -n "$user" ]; then shift 1; fi

    local qs

    _opts_filter "$@"
    _opts_qs "$@"

    if [ -n "$user" ] ; then
        url="/users/${user}/repos"
    else
        url='/user/repos'
    fi

    _get "${url}${qs}" | _filter_json "${_filter}"
}

list_branches() {
    # List branches of a specified repository.
    # ( https://developer.github.com/v3/repos/#list_branches )
    #
    # Usage:
    #
    #     list_branches user repo
    #
    # Positional arguments
    #
    #   GitHub user login or id for which to list branches
    #   Name of the repo for which to list branches
    #
    local user="${1:?User name required.}"
    local repo="${2:?Repo name required.}"
    shift 2
    #
    # Keyword arguments
    #
    local _filter='.[] | "\(.name)"'
    #   A jq filter to apply to the return data.
    #
    # Querystring arguments may also be passed as keyword arguments:
    #
    # * `direction`
    # * `per_page`
    # * `sort`
    # * `type`

    local qs

    _opts_filter "$@"
    _opts_qs "$@"

    url="/repos/${user}/${repo}/branches"

    _get "${url}${qs}" | _filter_json "${_filter}"
}

list_commits() {
    # List commits of a specified repository.
    # ( https://developer.github.com/v3/repos/commits/#list-commits-on-a-repository )
    #
    # Usage:
    #
    #     list_commits user repo
    #
    # Positional arguments
    #
    #   GitHub user login or id for which to list branches
    #   Name of the repo for which to list branches
    #

    local user="${1:?User name required.}"
    local repo="${2:?Repo name required.}"
    shift 2

    #   A jq filter to apply to the return data.
    #

    local _filter='.[] | "\(.sha) \(.author.login) \(.commit.author.email) \(.committer.login) \(.commit.committer.email)"'

    # Querystring arguments may also be passed as keyword arguments:
    #
    # * `sha` 
    # * `path`
    # * `author`
    # * `since` Only commits after this date will be returned. This is a timestamp in ISO 8601 format: YYYY-MM-DDTHH:MM:SSZ.
    # * `until`     

    local qs

    _opts_filter "$@"
    _opts_qs "$@"

    url="/repos/${user}/${repo}/commits"

    _get "${url}${qs}" | _filter_json "${_filter}"
}

list_contributors() {
    # List contributors to the specified repository, sorted by the number of commits per contributor in descending order.
    # ( https://developer.github.com/v3/repos/#list-contributors )
    #
    # Usage:
    #
    #     list_contributors user repo
    #
    # Positional arguments
    #
    local user="${1:?User name required.}"
    #   GitHub user login or id for which to list contributors
    local repo="${2:?Repo name required.}"
    #   Name of the repo for which to list contributors
    #
    # Keyword arguments
    #
    local _filter='.[] | "\(.login)\t\(.type)\tType:\(.type)\tContributions:\(.contributions)"'
    #   A jq filter to apply to the return data.
    #
    # Querystring arguments may also be passed as keyword arguments:
    #
    # * `direction`
    # * `per_page`
    # * `sort`
    # * `type`

    shift 2

    local qs

    _opts_filter "$@"
    _opts_qs "$@"

    url="/repos/${user}/${repo}/contributors"

    _get "${url}${qs}" | _filter_json "${_filter}"
}

list_collaborators() {
    # List collaborators to the specified repository, sorted by the number of commits per collaborator in descending order.
    # ( https://developer.github.com/v3/repos/#list-collaborators )
    #
    # Usage:
    #
    #     list_collaborators someuser/somerepo
    #
    # Positional arguments
    #   GitHub user login or id for which to list collaborators
    #   Name of the repo for which to list collaborators
    #
    local repo="${1:?Repo name required.}"
    #
    # Keyword arguments
    #
    local _filter='.[] | "\(.login)\t\(.type)\tType:\(.type)\tPermissions:\(.permissions)"'
    #   A jq filter to apply to the return data.
    #
    # Querystring arguments may also be passed as keyword arguments:
    #
    # * `direction`
    # * `per_page`
    # * `sort`
    # * `type`

    shift 1

    local qs

    _opts_filter "$@"
    _opts_qs "$@"

    url="/repos/${repo}/collaborators"

    _get "${url}${qs}" | _filter_json "${_filter}"
}

list_hooks() {
    # List webhooks from the specified repository.
    # ( https://developer.github.com/v3/repos/hooks/#list-hooks )
    #
    # Usage:
    #
    #     list_hooks owner/repo
    #
    # Positional arguments
    #
    local repo="${1:?Repo name required.}"
    #   Name of the repo for which to list contributors
    #   Owner is mandatory, like 'owner/repo'
    #
    local _filter='.[] | "\(.name)\t\(.config.url)"'
    #   A jq filter to apply to the return data.
    #

    shift 1

    _opts_filter "$@"

    url="/repos/${repo}/hooks"

    _get "${url}" | _filter_json "${_filter}"
}

list_gists() {
    # List gists for the current authenticated user or a specific user
    #
    # https://developer.github.com/v3/gists/#list-a-users-gists
    #
    # Usage:
    #
    #     list_gists
    #     list_gists <username>
    #
    # Positional arguments
    #
    local username="$1"
    #   An optional user to filter listing
    #
    # Keyword arguments
    #
    local _follow_next
    #   Automatically look for a 'Links' header and follow any 'next' URLs.
    local _follow_next_limit
    #   Maximum number of 'next' URLs to follow before stopping.
    local _filter='.[] | "\(.id)\t\(.description)"'
    #   A jq filter to apply to the return data.

    local url
    case "$username" in
        ('') url='/gists';;
        (*=*) url='/gists';;
        (*) url="/users/${username}/gists"; shift 1;;
    esac

    _opts_pagination "$@"
    _opts_filter "$@"

    _get "${url}" | _filter_json "${_filter}"
}

public_gists() {
    # List public gists
    #
    # https://developer.github.com/v3/gists/#list-all-public-gists
    #
    # Usage:
    #
    #     public_gists
    #
    # Keyword arguments
    #
    local _follow_next
    #   Automatically look for a 'Links' header and follow any 'next' URLs.
    local _follow_next_limit
    #   Maximum number of 'next' URLs to follow before stopping.
    local _filter='.[] | "\(.id)\t\(.description)"'
    #   A jq filter to apply to the return data.

    _opts_pagination "$@"
    _opts_filter "$@"

    _get '/gists/public' | _filter_json "${_filter}"
}

gist() {
    # Get a single gist
    #
    # https://developer.github.com/v3/gists/#get-a-single-gist
    #
    # Usage:
    #
    #     get_gist
    #
    # Positional arguments
    #
    local gist_id="${1:?Gist ID required.}"
    #   ID of gist to fetch.
    #
    # Keyword arguments
    #
    local _filter='.files | keys | join(", ")'
    #   A jq filter to apply to the return data.

    shift 1

    _opts_filter "$@"

    _get "/gists/${gist_id}" | _filter_json "${_filter}"
}

add_collaborator() {
    # Add a collaborator to a repository
    #
    # Usage:
    #
    #     add_collaborator someuser/somerepo collaboratoruser permission
    #
    # Positional arguments
    #
    local repo="${1:?Repo name required.}"
    #   A GitHub repository.
    local collaborator="${2:?Collaborator name required.}"
    #   A new collaborator.
    local permission="${3:?Permission required. One of: push pull admin}"
    #   The permission level for this collaborator. One of `push`, `pull`,
    #   `admin`. The `pull` and `admin` permissions are valid for organization
    #   repos only.
    case $permission in
        push|pull|admin) :;;
        *) printf 'Permission invalid: %s\nMust be one of: push pull admin\n' \
                "$permission" 1>&2; exit 1 ;;
    esac
    #
    # Keyword arguments
    #
    local _filter='"\(.name)\t\(.color)"'
    #   A jq filter to apply to the return data.

    _opts_filter "$@"

    _format_json permission="$permission" \
        | _post "/repos/${repo}/collaborators/${collaborator}" method='PUT' \
        | _filter_json "$_filter"
}

delete_collaborator() {
    # Delete a collaborator to a repository
    #
    # Usage:
    #
    #     delete_collaborator someuser/somerepo collaboratoruser permission
    #
    # Positional arguments
    #
    local repo="${1:?Repo name required.}"
    #   A GitHub repository.
    local collaborator="${2:?Collaborator name required.}"
    #   A new collaborator.

    shift 2

    local confirm

    _get_confirm 'This will permanently delete the collaborator from this repo. Continue?'
    [ "$confirm" -eq 1 ] || exit 0

    _delete "/repos/${repo}/collaborators/${collaborator}"
    exit $?
}

create_repo() {
    # Create a repository for a user or organization
    #
    # Usage:
    #
    #     create_repo foo
    #     create_repo bar description='Stuff and things' homepage='example.com'
    #     create_repo baz organization=myorg
    #
    # Positional arguments
    #
    local name="${1:?Repo name required.}"
    #   Name of the new repo
    #
    # Keyword arguments
    #
    local _filter='"\(.name)\t\(.html_url)"'
    #   A jq filter to apply to the return data.
    #
    # POST data may also be passed as keyword arguments:
    #
    # * `auto_init`,
    # * `description`
    # * `gitignore_template`
    # * `has_downloads`
    # * `has_issues`
    # * `has_wiki`,
    # * `homepage`
    # * `organization`
    # * `private`
    # * `team_id`

    shift 1

    _opts_filter "$@"

    local url
    local organization

    for arg in "$@"; do
        case $arg in
            (organization=*) organization="${arg#*=}";;
        esac
    done

    if [ -n "$organization" ] ; then
        url="/orgs/${organization}/repos"
    else
        url='/user/repos'
    fi

    export OK_SH_ACCEPT="application/vnd.github.nebula-preview+json"
    _format_json "name=${name}" "$@" | _post "$url" | _filter_json "${_filter}"
}

delete_repo() {
    # Delete a repository for a user or organization
    #
    # Usage:
    #
    #     delete_repo owner repo
    #
    # The currently authenticated user must have the `delete_repo` scope. View
    # current scopes with the `show_scopes()` function.
    #
    # Positional arguments
    #
    local owner="${1:?Owner name required.}"
    #   Name of the new repo
    local repo="${2:?Repo name required.}"
    #   Name of the new repo

    shift 2

    local confirm

    _get_confirm 'This will permanently delete a repository! Continue?'
    [ "$confirm" -eq 1 ] || exit 0

    _delete "/repos/${owner}/${repo}"
    exit $?
}

fork_repo() {
    # Fork a repository from a user or organization to own account or organization
    #
    # Usage:
    #
    #     fork_repo owner repo
    #
    # Positional arguments
    #
    local owner="${1:?Owner name required.}"
    #   Name of existing user or organization
    local repo="${2:?Repo name required.}"
    #   Name of the existing repo
    #
    #
    # Keyword arguments
    #
    local _filter='"\(.clone_url)\t\(.ssh_url)"'
    #   A jq filter to apply to the return data.
    #
    # POST data may also be passed as keyword arguments:
    # 
    # * `organization` (The organization to clone into; default: your personal account)

    shift 2

    _opts_filter "$@"

    _format_json "$@" | _post "/repos/${owner}/${repo}/forks" \
        | _filter_json "${_filter}"
    exit $?  # might take a bit time...
}

# ### Releases
# Create, update, delete, list releases.

list_releases() {
    # List releases for a repository
    #
    # https://developer.github.com/v3/repos/releases/#list-releases-for-a-repository
    #
    # Usage:
    #
    #     list_releases org repo '\(.assets[0].name)\t\(.name.id)'
    #
    # Positional arguments
    #
    local owner="${1:?Owner name required.}"
    #   A GitHub user or organization.
    local repo="${2:?Repo name required.}"
    #   A GitHub repository.
    #
    # Keyword arguments
    #
    local _filter='.[] | "\(.name)\t\(.tag_name)\t\(.id)\t\(.html_url)"'
    #   A jq filter to apply to the return data.

    shift 2

    _opts_filter "$@"

    _get "/repos/${owner}/${repo}/releases" \
        | _filter_json "${_filter}"
}

release() {
    # Get a release
    #
    # https://developer.github.com/v3/repos/releases/#get-a-single-release
    #
    # Usage:
    #
    #     release user repo 1087855
    #
    # Positional arguments
    #
    local owner="${1:?Owner name required.}"
    #   A GitHub user or organization.
    local repo="${2:?Repo name required.}"
    #   A GitHub repository.
    local release_id="${3:?Release ID required.}"
    #   The unique ID of the release; see list_releases.
    #
    # Keyword arguments
    #
    local _filter='"\(.author.login)\t\(.published_at)"'
    #   A jq filter to apply to the return data.

    shift 3

    _opts_filter "$@"

    _get "/repos/${owner}/${repo}/releases/${release_id}" \
        | _filter_json "${_filter}"
}

create_release() {
    # Create a release
    #
    # https://developer.github.com/v3/repos/releases/#create-a-release
    #
    # Usage:
    #
    #     create_release org repo v1.2.3
    #     create_release user repo v3.2.1 draft=true
    #
    # Positional arguments
    #
    local owner="${1:?Owner name required.}"
    #   A GitHub user or organization.
    local repo="${2:?Repo name required.}"
    #   A GitHub repository.
    local tag_name="${3:?Tag name required.}"
    #   Git tag from which to create release.
    #
    # Keyword arguments
    #
    local _filter='"\(.name)\t\(.id)\t\(.html_url)"'
    #   A jq filter to apply to the return data.
    #
    # POST data may also be passed as keyword arguments:
    #
    # * `body`
    # * `draft`
    # * `name`
    # * `prerelease`
    # * `target_commitish`

    shift 3

    _opts_filter "$@"

    _format_json "tag_name=${tag_name}" "$@" \
        | _post "/repos/${owner}/${repo}/releases" \
        | _filter_json "${_filter}"
}

edit_release() {
    # Edit a release
    #
    # https://developer.github.com/v3/repos/releases/#edit-a-release
    #
    # Usage:
    #
    #     edit_release org repo 1087855 name='Foo Bar 1.4.6'
    #     edit_release user repo 1087855 draft=false
    #
    # Positional arguments
    #
    local owner="${1:?Owner name required.}"
    #   A GitHub user or organization.
    local repo="${2:?Repo name required.}"
    #   A GitHub repository.
    local release_id="${3:?Release ID required.}"
    #   The unique ID of the release; see list_releases.
    #
    # Keyword arguments
    #
    local _filter='"\(.tag_name)\t\(.name)\t\(.html_url)"'
    #   A jq filter to apply to the return data.
    #
    # POST data may also be passed as keyword arguments:
    #
    # * `tag_name`
    # * `body`
    # * `draft`
    # * `name`
    # * `prerelease`
    # * `target_commitish`

    shift 3

    _opts_filter "$@"

    _format_json "$@" \
        | _post "/repos/${owner}/${repo}/releases/${release_id}" method="PATCH" \
        | _filter_json "${_filter}"
}

delete_release() {
    # Delete a release
    #
    # https://developer.github.com/v3/repos/releases/#delete-a-release
    #
    # Usage:
    #
    #     delete_release org repo 1087855
    #
    # Return: 0 for success; 1 for failure.
    #
    # Positional arguments
    #
    local owner="${1:?Owner name required.}"
    #   A GitHub user or organization.
    local repo="${2:?Repo name required.}"
    #   A GitHub repository.
    local release_id="${3:?Release ID required.}"
    #   The unique ID of the release; see list_releases.

    shift 3

    local confirm

    _get_confirm 'This will permanently delete a release. Continue?'
    [ "$confirm" -eq 1 ] || exit 0

    _delete "/repos/${owner}/${repo}/releases/${release_id}"
    exit $?
}

release_assets() {
    # List release assets
    #
    # https://developer.github.com/v3/repos/releases/#list-assets-for-a-release
    #
    # Usage:
    #
    #     release_assets user repo 1087855
    #
    # Example of downloading release assets:
    #
    #     ok.sh release_assets <user> <repo> <release_id> \
    #             _filter='.[] | .browser_download_url' \
    #         | xargs -L1 curl -L -O
    #
    # Example of the multi-step process for grabbing the release ID for
    # a specific version, then grabbing the release asset IDs, and then
    # downloading all the release assets (whew!):
    #
    #     username='myuser'
    #     repo='myrepo'
    #     release_tag='v1.2.3'
    #     ok.sh list_releases "$myuser" "$myrepo" \
    #         | awk -F'\t' -v tag="$release_tag" '$2 == tag { print $3 }' \
    #         | xargs -I{} ./ok.sh release_assets "$myuser" "$myrepo" {} \
    #             _filter='.[] | .browser_download_url' \
    #         | xargs -L1 curl -n -L -O
    #
    # Positional arguments
    #
    local owner="${1:?Owner name required.}"
    #   A GitHub user or organization.
    local repo="${2:?Repo name required.}"
    #   A GitHub repository.
    local release_id="${3:?Release ID required.}"
    #   The unique ID of the release; see list_releases.
    #
    # Keyword arguments
    #
    local _filter='.[] | "\(.id)\t\(.name)\t\(.updated_at)"'
    #   A jq filter to apply to the return data.

    shift 3

    _opts_filter "$@"

    _get "/repos/${owner}/${repo}/releases/${release_id}/assets" \
        | _filter_json "$_filter"
}

upload_asset() {
    # Upload a release asset
    #
    # https://developer.github.com/v3/repos/releases/#upload-a-release-asset
    #
    # Usage:
    #
    #     upload_asset https://<upload-url> /path/to/file.zip
    #
    # The upload URL can be gotten from `release()`. There are multiple steps
    # required to upload a file: get the release ID, get the upload URL, parse
    # the upload URL, then finally upload the file. For example:
    #
    # ```sh
    # USER="someuser"
    # REPO="somerepo"
    # TAG="1.2.3"
    # FILE_NAME="foo.zip"
    # FILE_PATH="/path/to/foo.zip"
    #
    # # Create a release then upload a file:
    # ok.sh create_release "$USER" "$REPO" "$TAG" _filter='.upload_url' \
    #     | sed 's/{.*$/?name='"$FILE_NAME"'/' \
    #     | xargs -I@ ok.sh upload_asset @ "$FILE_PATH"
    #
    # # Find a release by tag then upload a file:
    # ok.sh list_releases "$USER" "$REPO" \
    #     | awk -v "tag=$TAG" -F'\t' '$2 == tag { print $3 }' \
    #     | xargs -I@ ok.sh release "$USER" "$REPO" @ _filter='.upload_url' \
    #     | sed 's/{.*$/?name='"$FILE_NAME"'/' \
    #     | xargs -I@ ok.sh upload_asset @ "$FILE_PATH"
    # ```
    #
    # Positional arguments
    #
    local upload_url="${1:?upload_url is required.}"
    # The _parsed_ upload_url returned from GitHub.
    #
    local file_path="${2:?file_path is required.}"
    #   A path to the file that should be uploaded.
    #
    # Keyword arguments
    #
    local _filter='"\(.state)\t\(.browser_download_url)"'
    #   A jq filter to apply to the return data.
    #
    # Also any other keyword arguments accepted by `_post()`.

    shift 2

    _opts_filter "$@"

    _post "$upload_url" filename="$file_path" "$@" \
        | _filter_json "$_filter"
}

delete_asset() {
    # Delete a release asset
    #
    # https://docs.github.com/en/rest/reference/releases#delete-a-release-asset
    #
    # Usage:
    #
    #     delete_asset user repo 51955388
    #
    # Example of deleting release assets:
    #
    #     ok.sh release_assets <user> <repo> <release_id> \
    #             _filter='.[] | .id' \
    #         | xargs -L1 ./ok.sh delete_asset "$myuser" "$myrepo"
    #
    # Example of the multi-step process for grabbing the release ID for
    # a specific version, then grabbing the release asset IDs, and then
    # deleting all the release assets (whew!):
    #
    #     username='myuser'
    #     repo='myrepo'
    #     release_tag='v1.2.3'
    #     ok.sh list_releases "$myuser" "$myrepo" \
    #         | awk -F'\t' -v tag="$release_tag" '$2 == tag { print $3 }' \
    #         | xargs -I{} ./ok.sh release_assets "$myuser" "$myrepo" {} \
    #             _filter='.[] | .id' \
    #         | xargs -L1 ./ok.sh -y delete_asset "$myuser" "$myrepo"
    #
    # Positional arguments
    #
    local owner="${1:?Owner name required.}"
    #   A GitHub user or organization.
    local repo="${2:?Repo name required.}"
    #   A GitHub repository.
    local asset_id="${3:?Release asset ID required.}"
    #   The unique ID of the release asset; see release_assets.

    shift 3

    local confirm

    _get_confirm 'This will permanently delete a release asset. Continue?'
    [ "$confirm" -eq 1 ] || exit 0

    _delete "/repos/${owner}/${repo}/releases/assets/${asset_id}"
    exit $?
}

# ### Issues
# Create, update, edit, delete, list issues and milestones.

list_milestones() {
    # List milestones for a repository
    #
    # Usage:
    #
    #     list_milestones someuser/somerepo
    #     list_milestones someuser/somerepo state=closed
    #
    # Positional arguments
    #
    local repository="${1:?Repo name required.}"
    #   A GitHub repository.
    #
    # Keyword arguments
    #
    local _follow_next
    #   Automatically look for a 'Links' header and follow any 'next' URLs.
    local _follow_next_limit
    #   Maximum number of 'next' URLs to follow before stopping.
    local _filter='.[] | "\(.number)\t\(.open_issues)/\(.closed_issues)\t\(.title)"'
    #   A jq filter to apply to the return data.
    #
    # GitHub querystring arguments may also be passed as keyword arguments:
    #
    # * `direction`
    # * `per_page`
    # * `sort`
    # * `state`

    shift 1
    local qs

    _opts_pagination "$@"
    _opts_filter "$@"
    _opts_qs "$@"

    _get "/repos/${repository}/milestones${qs}" | _filter_json "$_filter"
}

create_milestone() {
    # Create a milestone for a repository
    #
    # Usage:
    #
    #     create_milestone someuser/somerepo MyMilestone
    #
    #     create_milestone someuser/somerepo MyMilestone \
    #         due_on=2015-06-16T16:54:00Z \
    #         description='Long description here
    #     that spans multiple lines.'
    #
    # Positional arguments
    #
    local repo="${1:?Repo name required.}"
    #   A GitHub repository.
    local title="${2:?Milestone name required.}"
    #   A unique title.
    #
    # Keyword arguments
    #
    local _filter='"\(.number)\t\(.html_url)"'
    #   A jq filter to apply to the return data.
    #
    # Milestone options may also be passed as keyword arguments:
    #
    # * `description`
    # * `due_on`
    # * `state`

    shift 2

    _opts_filter "$@"

    _format_json title="$title" "$@" \
        | _post "/repos/${repo}/milestones" \
        | _filter_json "$_filter"
}

list_issue_comments() {
    # List comments of a specified issue.
    # ( https://developer.github.com/v3/issues/comments/#list-issue-comments )
    #
    # Usage:
    #
    #     list_issue_comments someuser/somerepo number
    #
    # Positional arguments
    #
    #   GitHub owner login or id for which to list branches
    #   Name of the repo for which to list branches
    #   Issue number
    #
    local repo="${1:?Repo name required.}"
    local number="${2:?Issue number is required.}"
    shift 2

    local _follow_next
    #   Automatically look for a 'Links' header and follow any 'next' URLs.
    local _follow_next_limit
    #   Maximum number of 'next' URLs to follow before stopping.
    local _filter='.[] | "\(.body)"'
    #   A jq filter to apply to the return data.

    _opts_pagination "$@"

    #   A jq filter to apply to the return data.
    #
    # Querystring arguments may also be passed as keyword arguments:
    #
    # * `direction`
    # * `sort`
    # * `since`
    local qs
    _opts_filter "$@"
    _opts_qs "$@"
    url="/repos/${repo}/issues/${number}/comments"
    _get "${url}${qs}" | _filter_json "${_filter}"
}

add_comment() {
    # Add a comment to an issue
    #
    # Usage:
    #
    #     add_comment someuser/somerepo 123 'This is a comment'
    #
    # Positional arguments
    #
    local repository="${1:?Repo name required}"
    #   A GitHub repository
    local number="${2:?Issue number required}"
    #   Issue Number
    local comment="${3:?Comment required}"
    #   Comment to be added
    #
    # Keyword arguments
    #
    local _filter='"\(.id)\t\(.html_url)"'
    #   A jq filter to apply to the return data.

    shift 3
    _opts_filter "$@"

    _format_json body="$comment" \
        | _post "/repos/${repository}/issues/${number}/comments" \
        | _filter_json "${_filter}"
}

list_commit_comments() {
    # List comments of a specified commit.
    # ( https://developer.github.com/v3/repos/comments/#list-commit-comments )
    #
    # Usage:
    #
    #     list_commit_comments someuser/somerepo sha
    #
    # Positional arguments
    #
    #   GitHub owner login or id for which to list branches
    #   Name of the repo for which to list branches
    #   Commit SHA
    #
    local repo="${1:?Repo name required.}"
    local sha="${2:?Commit SHA is required.}"
    shift 2

    local _follow_next
    #   Automatically look for a 'Links' header and follow any 'next' URLs.
    local _follow_next_limit
    #   Maximum number of 'next' URLs to follow before stopping.
    local _filter='.[] | "\(.body)"'
    #   A jq filter to apply to the return data.

    _opts_pagination "$@"

    #   A jq filter to apply to the return data.
    #
    # Querystring arguments may also be passed as keyword arguments:
    #
    # * `direction`
    # * `sort`
    # * `since`
    local qs
    _opts_filter "$@"
    _opts_qs "$@"
    url="/repos/${repo}/commits/${sha}/comments"
    _get "${url}${qs}" | _filter_json "${_filter}"
}


add_commit_comment() {
    # Add a comment to a commit
    #
    # Usage:
    #
    #     add_commit_comment someuser/somerepo 123 'This is a comment'
    #
    # Positional arguments
    #
    local repository="${1:?Repo name required}"
    #   A GitHub repository
    local hash="${2:?Commit hash required}"
    #   Commit hash
    local comment="${3:?Comment required}"
    #   Comment to be added
    #
    # Keyword arguments
    #
    local _filter='"\(.id)\t\(.html_url)"'
    #   A jq filter to apply to the return data.

    shift 3
    _opts_filter "$@"

    _format_json body="$comment" \
        | _post "/repos/${repository}/commits/${hash}/comments" \
        | _filter_json "${_filter}"
}

close_issue() {
    # Close an issue
    #
    # Usage:
    #
    #     close_issue someuser/somerepo 123
    #
    # Positional arguments
    #
    local repository="${1:?Repo name required}"
    #   A GitHub repository
    local number="${2:?Issue number required}"
    #   Issue Number
    #
    # Keyword arguments
    #
    local _filter='"\(.id)\t\(.state)\t\(.html_url)"'
    #   A jq filter to apply to the return data.
    #
    # POST data may also be passed as keyword arguments:
    #
    # * `assignee`
    # * `labels`
    # * `milestone`

    shift 2
    _opts_filter "$@"

    _format_json state="closed" "$@" \
        | _post "/repos/${repository}/issues/${number}" method='PATCH' \
        | _filter_json "${_filter}"
}

list_issues() {
    # List issues for the authenticated user or repository
    #
    # Usage:
    #
    #     list_issues
    #     list_issues someuser/somerepo
    #     list_issues <any of the above> state=closed labels=foo,bar
    #
    # Positional arguments
    #
    # user or user/repository
    #
    # Keyword arguments
    #
    local _follow_next
    #   Automatically look for a 'Links' header and follow any 'next' URLs.
    local _follow_next_limit
    #   Maximum number of 'next' URLs to follow before stopping.
    local _filter='.[] | "\(.number)\t\(.title)"'
    #   A jq filter to apply to the return data.
    #
    # GitHub querystring arguments may also be passed as keyword arguments:
    #
    # * `assignee`
    # * `creator`
    # * `direction`
    # * `labels`
    # * `mentioned`
    # * `milestone`
    # * `per_page`
    # * `since`
    # * `sort`
    # * `state`

    local url
    local qs

    case $1 in
        ('') url='/user/issues' ;;
        (*=*) url='/user/issues' ;;
        (*/*) url="/repos/${1}/issues"; shift 1 ;;
    esac

    _opts_pagination "$@"
    _opts_filter "$@"
    _opts_qs "$@"

    _get "${url}${qs}" | _filter_json "$_filter"
}

user_issues() {
    # List all issues across owned and member repositories for the authenticated user
    #
    # Usage:
    #
    #     user_issues
    #     user_issues since=2015-60-11T00:09:00Z
    #
    # Keyword arguments
    #
    local _follow_next
    #   Automatically look for a 'Links' header and follow any 'next' URLs.
    local _follow_next_limit
    #   Maximum number of 'next' URLs to follow before stopping.
    local _filter='.[] | "\(.repository.full_name)\t\(.number)\t\(.title)"'
    #   A jq filter to apply to the return data.
    #
    # GitHub querystring arguments may also be passed as keyword arguments:
    #
    # * `direction`
    # * `filter`
    # * `labels`
    # * `per_page`
    # * `since`
    # * `sort`
    # * `state`

    local qs

    _opts_pagination "$@"
    _opts_filter "$@"
    _opts_qs "$@"

    _get "/issues${qs}" | _filter_json "$_filter"
}

create_issue() {
    # Create an issue
    #
    # Usage:
    #
    #     create_issue owner repo 'Issue title' body='Add multiline body
    #     content here' labels="$(./ok.sh _format_json -a foo bar)"
    #
    # Positional arguments
    #
    local owner="${1:?Owner name required.}"
    #   A GitHub repository.
    local repo="${2:?Repo name required.}"
    #   A GitHub repository.
    local title="${3:?Issue title required.}"
    #   A GitHub repository.
    #
    # Keyword arguments
    #
    local _filter='"\(.id)\t\(.number)\t\(.html_url)"'
    #   A jq filter to apply to the return data.
    #
    # Additional issue fields may be passed as keyword arguments:
    #
    # * `body` (string)
    # * `assignee` (string)
    # * `milestone` (integer)
    # * `labels` (array of strings)
    # * `assignees` (array of strings)

    shift 3

    _opts_filter "$@"

    _format_json title="$title" "$@" \
        | _post "/repos/${owner}/${repo}/issues" \
        | _filter_json "$_filter"
}

org_issues() {
    # List all issues for a given organization for the authenticated user
    #
    # Usage:
    #
    #     org_issues someorg
    #
    # Positional arguments
    #
    local org="${1:?Organization name required.}"
    #   Organization GitHub login or id.
    #
    # Keyword arguments
    #
    local _follow_next
    #   Automatically look for a 'Links' header and follow any 'next' URLs.
    local _follow_next_limit
    #   Maximum number of 'next' URLs to follow before stopping.
    local _filter='.[] | "\(.number)\t\(.title)"'
    #   A jq filter to apply to the return data.
    #
    # GitHub querystring arguments may also be passed as keyword arguments:
    #
    # * `direction`
    # * `filter`
    # * `labels`
    # * `per_page`
    # * `since`
    # * `sort`
    # * `state`

    shift 1
    local qs

    _opts_pagination "$@"
    _opts_filter "$@"
    _opts_qs "$@"

    _get "/orgs/${org}/issues${qs}" | _filter_json "$_filter"
}

list_starred() {
    # List starred repositories
    #
    # Usage:
    #
    #     list_starred
    #     list_starred user
    #
    # Positional arguments
    #
    local user="$1"
    #   Optional GitHub user login or id for which to list the starred repos.
    #
    # Keyword arguments
    #
    local _filter='.[] | "\(.name)\t\(.html_url)"'
    #   A jq filter to apply to the return data.
    #
    # Querystring arguments may also be passed as keyword arguments:
    #
    # * `direction`
    # * `per_page`
    # * `sort`
    # * `type`

    # User is optional; is this a keyword arg?
    case "$user" in *=*) user='' ;; esac
    if [ -n "$user" ]; then shift 1; fi

    local qs

    _opts_filter "$@"
    _opts_qs "$@"

    if [ -n "$user" ] ; then
        url="/users/${user}/starred"
    else
        url='/user/starred'
    fi

    _get "${url}${qs}" | _filter_json "${_filter}"
}

list_my_orgs() {
    # List your organizations
    #
    # Usage:
    #
    #     list_my_orgs
    #
    # Keyword arguments
    #
    local _follow_next
    #   Automatically look for a 'Links' header and follow any 'next' URLs.
    local _follow_next_limit
    #   Maximum number of 'next' URLs to follow before stopping.
    local _filter='.[] | "\(.login)\t\(.id)"'
    #   A jq filter to apply to the return data.

    local qs

    _opts_pagination "$@"
    _opts_filter "$@"
    _opts_qs "$@"

    _get "/user/orgs" | _filter_json "$_filter"
}

list_orgs() {
    # List all organizations
    #
    # Usage:
    #
    #     list_orgs
    #
    # Keyword arguments
    #
    local _follow_next
    #   Automatically look for a 'Links' header and follow any 'next' URLs.
    local _follow_next_limit
    #   Maximum number of 'next' URLs to follow before stopping.
    local _filter='.[] | "\(.login)\t\(.id)"'
    #   A jq filter to apply to the return data.

    local qs

    _opts_pagination "$@"
    _opts_filter "$@"
    _opts_qs "$@"

    _get "/organizations" | _filter_json "$_filter"
}

list_users() {
    # List all users
    #
    # Usage:
    #
    #     list_users
    #
    # Keyword arguments
    #
    local _follow_next
    #   Automatically look for a 'Links' header and follow any 'next' URLs.
    local _follow_next_limit
    #   Maximum number of 'next' URLs to follow before stopping.
    local _filter='.[] | "\(.login)\t\(.id)"'
    #   A jq filter to apply to the return data.

    local qs

    _opts_pagination "$@"
    _opts_filter "$@"
    _opts_qs "$@"
    _get "/users" | _filter_json "$_filter"
}

labels() {
    # List available labels for a repository
    #
    # Usage:
    #
    #     labels someuser/somerepo
    #
    # Positional arguments
    #
    local repo="$1"
    #   A GitHub repository.
    #
    # Keyword arguments
    #
    local _follow_next
    #   Automatically look for a 'Links' header and follow any 'next' URLs.
    local _follow_next_limit
    #   Maximum number of 'next' URLs to follow before stopping.
    local _filter='.[] | "\(.name)\t\(.color)"'
    #   A jq filter to apply to the return data.

    _opts_pagination "$@"
    _opts_filter "$@"

    _get "/repos/${repo}/labels" | _filter_json "$_filter"
}

add_label() {
    # Add a label to a repository
    #
    # Usage:
    #
    #     add_label someuser/somerepo LabelName color
    #
    # Positional arguments
    #
    local repo="${1:?Repo name required.}"
    #   A GitHub repository.
    local label="${2:?Label name required.}"
    #   A new label.
    local color="${3:?Hex color required.}"
    #   A color, in hex, without the leading `#`.
    #
    # Keyword arguments
    #
    local _filter='"\(.name)\t\(.color)"'
    #   A jq filter to apply to the return data.

    _opts_filter "$@"

    _format_json name="$label" color="$color" \
        | _post "/repos/${repo}/labels" \
        | _filter_json "$_filter"
}

update_label() {
    # Update a label
    #
    # Usage:
    #
    #     update_label someuser/somerepo OldLabelName \
    #         label=NewLabel color=newcolor
    #
    # Positional arguments
    #
    local repo="${1:?Repo name required.}"
    #   A GitHub repository.
    local label="${2:?Label name required.}"
    #   The name of the label which will be updated
    #
    # Keyword arguments
    #
    local _filter='"\(.name)\t\(.color)"'
    #   A jq filter to apply to the return data.
    #
    # Label options may also be passed as keyword arguments, these will update
    # the existing values:
    #
    # * `color`
    # * `name`

    shift 2

    _opts_filter "$@"

    _format_json "$@" \
        | _post "/repos/${repo}/labels/${label}" method='PATCH' \
        | _filter_json "$_filter"
}

add_team_repo() {
    # Add a team repository
    #
    # Usage:
    #
    #     add_team_repo team_id organization repository_name permission
    #
    # Positional arguments
    #
    local team_id="${1:?Team id required.}"
    #   Team id to add repository to
    local organization="${2:?Organization required.}"
    #   Organization to add repository to
    local repository_name="${3:?Repository name required.}"
    #   Repository name to add
    local permission="${4:?Permission required.}"
    #   Permission to grant: pull, push, admin
    #
    local url="/teams/${team_id}/repos/${organization}/${repository_name}"

    export OK_SH_ACCEPT="application/vnd.github.ironman-preview+json"

    _format_json "name=${name}" "permission=${permission}" | _post "$url" method='PUT' | _filter_json "${_filter}"
}

list_pulls() {
    # Lists the pull requests for a repository
    #
    # Usage:
    #
    #     list_pulls user repo
    #
    # Positional arguments
    #
    local owner="${1:?Owner required.}"
    #   A GitHub owner.
    local repo="${2:?Repo name required.}"
    #   A GitHub repository.
    #
    # Keyword arguments
    #
    local _follow_next
    #   Automatically look for a 'Links' header and follow any 'next' URLs.
    local _follow_next_limit
    #   Maximum number of 'next' URLs to follow before stopping.
    local _filter='.[] | "\(.number)\t\(.user.login)\t\(.head.repo.clone_url)\t\(.head.ref)"'
    #   A jq filter to apply to the return data.

    _opts_pagination "$@"
    _opts_filter "$@"

    _get "/repos/${owner}/${repo}/pulls" | _filter_json "$_filter"
}

create_pull_request() {
    # Create a pull request for a repository
    #
    # Usage:
    #
    #     create_pull_request someuser/somerepo title head base
    #
    #     create_pull_request someuser/somerepo title head base body='Description here.'
    #
    # Positional arguments
    #
    local repo="${1:?Repo name required.}"
    #   A GitHub repository.
    local title="${2:?Pull request title required.}"
    #   A title.
    local head="${3:?Pull request head required.}"
    #   A head.
    local base="${4:?Pull request base required.}"
    #   A base.
    #
    # Keyword arguments
    #
    local _filter='"\(.number)\t\(.html_url)"'
    #   A jq filter to apply to the return data.
    #
    # Pull request options may also be passed as keyword arguments:
    #
    # * `body`
    # * `maintainer_can_modify`

    shift 4

    _opts_filter "$@"

    _format_json title="$title" head="$head" base="$base" "$@" \
        | _post "/repos/${repo}/pulls" \
        | _filter_json "$_filter"
}

update_pull_request() {
    # Update a pull request for a repository
    #
    # Usage:
    #
    #     update_pull_request someuser/somerepo number title='New title' body='New body'
    #
    # Positional arguments
    #
    local repo="${1:?Repo name required.}"
    #   A GitHub repository.
    local number="${2:?Pull request number required.}"
    #   A pull request number.
    #
    # Keyword arguments
    #
    local _filter='"\(.number)\t\(.html_url)"'
    #   A jq filter to apply to the return data.
    #
    # Pull request options may also be passed as keyword arguments:
    #
    # * `base`
    # * `body`
    # * `maintainer_can_modify`
    # * `state` (either open or closed)
    # * `title`

    shift 2

    _opts_filter "$@"

    _format_json "$@" \
        | _post "/repos/${repo}/pulls/${number}" method='PATCH' \
        | _filter_json "$_filter"
}

transfer_repo() {
    # Transfer a repository to a user or organization
    #
    # Usage:
    #
    #     transfer_repo owner repo new_owner
    #     transfer_repo owner repo new_owner team_ids='[ 12, 345 ]'
    #
    # Positional arguments
    #
    local owner="${1:?Owner name required.}"
    #   Name of the current owner
    #
    local repo="${2:?Repo name required.}"
    #   Name of the current repo
    #
    local new_owner="${3:?New owner name required.}"
    #   Name of the new owner
    #
    # Keyword arguments
    #
    local _filter='"\(.name)"'
    #   A jq filter to apply to the return data.
    #
    # POST data may also be passed as keyword arguments:
    #
    # * `team_ids`

    shift 3

    _opts_filter "$@"

    export OK_SH_ACCEPT='application/vnd.github.nightshade-preview+json'
    _format_json "new_owner=${new_owner}" "$@" | _post "/repos/${owner}/${repo}/transfer" | _filter_json "${_filter}"
}

archive_repo() {
    # Archive a repo
    #
    # Usage:
    #
    #     archive_repo owner/repo
    #
    # Positional arguments
    #
    local repo="${1:?Repo name required.}"
    #   A GitHub repository.
    #
    local _filter='"\(.name)\t\(.html_url)"'
    #   A jq filter to apply to the return data.
    #

    shift 1

    _opts_filter "$@"

    _format_json "archived=true" \
        | _post "/repos/${repo}" method='PATCH' \
        | _filter_json "$_filter"
}

__main "$@"
