#!/bin/sh
# Reject pushing annotation branches to the public origin remote.
set -e

remote="$1"
url="$2"

public_patterns='github.com[:/]hxx30992670/prototype-manage.git'
blocked_branches='feat/spatial-annotation
feat/prototype-spatial-annotation-comment
fix/collaboration-bug
develop'

case "$url" in
  *$public_patterns* | *hxx30992670/prototype-manage.git*)
    ;;
  *)
    exit 0
    ;;
esac

if [ "$remote" != "origin" ] && [ "$remote" != "public" ]; then
  # Still block if the URL is the public repo, regardless of remote name.
  :
fi

while read -r local_ref local_sha remote_ref remote_sha
do
  [ -z "$local_ref" ] && continue
  branch=${local_ref#refs/heads/}
  echo "$blocked_branches" | grep -qx "$branch" || continue
  echo "拒绝：不要把 $branch 推到公开仓库 $url" >&2
  echo "公开仓库只接收 master。标注请推 private（prototype-manage-pro）。" >&2
  exit 1
done

exit 0
