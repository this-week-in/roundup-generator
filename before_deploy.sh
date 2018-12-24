#!/usr/bin/env bash
tag=$(date +'%Y%m%d%H%M%S')-$(git log --format=%h -1)
echo "the release tag will be ${tag}..."
git config --local user.name "${GIT_NAME}"
git config --local user.email "${GIT_EMAIL}"
git tag "$tag"
#git push origin "$tag"