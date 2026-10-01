#!/usr/bin/env bash
# Publishes the sibling mods this one compiles against to mavenLocal, so the build resolves them as on a dev machine.
# dev: each sibling at its release tag if it exists, else at the head of its branch for the same Minecraft version
# (the default branch while a repo has no version branches).
# release: the release tag only; a missing tag fails the build, so siblings are always released first.
set -euo pipefail
mode=${1:-dev}
prop() { grep "^$1=" "${2:-gradle.properties}" | cut -d= -f2; }
label=$(prop minecraft_label)

clone() {   # repo version: clones into deps/<repo>
    local repo=$1 ref="v$2"
    if ! git ls-remote --exit-code --tags "https://github.com/Darkona/${repo}.git" "refs/tags/${ref}" >/dev/null; then
        [ "$mode" = release ] && { echo "::error::${repo} has no tag ${ref}"; exit 1; }
        if git ls-remote --exit-code --heads "https://github.com/Darkona/${repo}.git" "refs/heads/${label}" >/dev/null; then
            ref=$label
        else
            ref=main
        fi
    fi
    echo "${repo} @ ${ref}"
    git clone -q --depth 1 --branch "$ref" "https://github.com/Darkona/${repo}.git" "deps/${repo}"
}
publish() { (cd "deps/$1" && ./gradlew -q publishToMavenLocal); }

case "$(prop mod_id)" in
    surveyor) ;;
    antique_atlas)
        clone surveyor-neoforge "$(prop surveyor_version)"
        publish surveyor-neoforge ;;
    *) echo "::error::unknown mod_id"; exit 1 ;;
esac
