# yaml-test-suite data release data-2022-01-17 (test-only). One directory per case (or per
# sub-case, e.g. Y79Y/004) holding in.yaml, test.event and, for cases that must fail, error.
# The name/ and tags/ trees are symlink indexes over the same cases and are excluded.
package(default_visibility = ["//visibility:public"])

_EXCLUDE = [
    "name/**",
    "tags/**",
    "BUILD.bazel",
    "WORKSPACE",
    "WORKSPACE.bazel",
    "MODULE.bazel",
]

filegroup(
    name = "cases",
    srcs = glob(
        ["**"],
        exclude = _EXCLUDE,
    ),
)

# One in.yaml per case: tests receive these paths and read sibling files from :cases.
filegroup(
    name = "inputs",
    srcs = glob(
        ["**/in.yaml"],
        exclude = _EXCLUDE,
    ),
)
