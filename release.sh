#!/usr/bin/env bash
set -euo pipefail

PROPERTIES_FILE="gradle.properties"

if [[ ! -f "$PROPERTIES_FILE" ]]; then
  echo "Error: $PROPERTIES_FILE not found in current directory."
  exit 1
fi

CURRENT_VERSION=$(grep '^helperVersionName=' "$PROPERTIES_FILE" | cut -d'=' -f2 | tr -d ' \r\n')
echo "Current version: $CURRENT_VERSION"

IFS='.' read -r MAJOR MINOR PATCH _ <<< "$CURRENT_VERSION"
MAJOR="${MAJOR:-1}"
MINOR="${MINOR:-0}"
PATCH="${PATCH:-0}"

MODE="${1:-patch}"

case "$MODE" in
  patch)
    PATCH=$((PATCH + 1))
    ;;
  minor)
    MINOR=$((MINOR + 1))
    PATCH=0
    ;;
  major)
    MAJOR=$((MAJOR + 1))
    MINOR=0
    PATCH=0
    ;;
  v*|*.*.*)
    CUSTOM_VER="${MODE#v}"
    IFS='.' read -r MAJOR MINOR PATCH _ <<< "$CUSTOM_VER"
    ;;
  *)
    echo "Usage: $0 [patch | minor | major | X.Y.Z]"
    exit 1
    ;;
esac

NEW_VERSION="${MAJOR}.${MINOR}.${PATCH}"
NEW_CODE=$((MAJOR * 1000000 + MINOR * 10000 + PATCH * 100))
NEW_TAG="v${NEW_VERSION}"

echo "Bumping to: $NEW_VERSION (versionCode: $NEW_CODE, tag: $NEW_TAG)"

# Update gradle.properties
sed -i -E "s/^helperVersionName=.*/helperVersionName=${NEW_VERSION}/" "$PROPERTIES_FILE"
sed -i -E "s/^helperVersionCode=.*/helperVersionCode=${NEW_CODE}/" "$PROPERTIES_FILE"

# Commit and tag
git add "$PROPERTIES_FILE"
git commit -m "chore: release ${NEW_TAG}"
git tag -a "${NEW_TAG}" -m "Release ${NEW_TAG}"

echo "Tag ${NEW_TAG} created. Pushing to origin..."
git push origin main
git push origin "${NEW_TAG}"

echo "Successfully pushed ${NEW_TAG} to origin!"
