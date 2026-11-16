load("@rules_java//java:defs.bzl", "java_library")

NULLAWAY_OPTS = [
    "-Werror",
    "-XepDisableAllChecks",
    "-Xep:NullAway:ERROR",
    "-Xep:RequireExplicitNullMarking:ERROR",
    "-Xep:JSpecifyUnrecognizedAnnotationLocation:ERROR",
    "-XepOpt:NullAway:OnlyNullMarked=true",
    "-XepOpt:NullAway:JSpecifyMode=true",
    "-XepOpt:NullAway:TreatGeneratedAsUnannotated=true",
    "-XepExcludedPaths:.*/_javac/.*",
]

# trufflecheck (third_party/trufflecheck, profiles/jvm/errorprone.args): every Truffle rule at the
# profile's severity, and the profile's Native Image class-init and context-state hazards. Under
# -Werror a WARN finding fails the build too, so each one is fixed or suppressed with a reason.
#
# Two rules are off until they are scoped to compiled code (reported upstream):
# - TruffleStackfulException flags every JDK exception constructed anywhere. Nearly all of ours are
#   host-side API validation (schema, limits, options) that partial evaluation never reaches, where
#   a stack trace is what a caller needs.
# - TruffleSkippedException flags UnexpectedResultException thrown from typed reads that the Truffle
#   API itself declares `throws UnexpectedResultException`, and ContractViolation on the read nodes'
#   error paths; changing those is a measured SVM change, not a lint fix.
TRUFFLECHECK_OPTS = [
    "-XepOpt:Trufflecheck:Policy=bazel/trufflecheck/trufflecheck.properties",
    "-Xep:TruffleStackfulException:OFF",
    "-Xep:TruffleForbiddenCollection:ERROR",
    "-Xep:TruffleHostInterop:ERROR",
    "-Xep:TruffleBlockingInNode:ERROR",
    "-Xep:TruffleCompilationFinalDimensions:ERROR",
    "-Xep:TruffleHostValueInImageHeap:ERROR",
    "-Xep:TruffleNodeChildren:ERROR",
    "-Xep:TruffleCompilationFinalWrite:ERROR",
    "-Xep:TruffleMissingBoundary:ERROR",
    "-Xep:TruffleSkippedException:OFF",
    "-Xep:TruffleChildWriteWithoutInsert:ERROR",
    "-Xep:TruffleStaticContextState:ERROR",
    "-Xep:TruffleFrameEscape:ERROR",
    "-Xep:TruffleNonConstantNodeState:ERROR",
    "-Xep:TruffleGenericCatch:ERROR",
    "-Xep:TruffleDslSuppressions:ERROR",
    "-Xep:TruffleChildrenLoop:WARN",
    "-Xep:TruffleDeoptLoop:WARN",
    "-Xep:TruffleGuestLoop:WARN",
    "-Xep:TruffleFastPathRecursion:WARN",
    "-Xep:TruffleDeprecatedApi:WARN",
    "-Xep:TruffleLegacyProfile:WARN",
    "-Xep:TruffleUncachedOnFastPath:WARN",
    "-Xep:TruffleFieldUpdaterConstant:WARN",
    "-Xep:TruffleDeclaredType:WARN",
    "-Xep:TruffleBoundaryExceptionPolicy:WARN",
    "-Xep:TruffleSlowPathInline:WARN",
    "-Xep:TruffleBytecodeSwitch:WARN",
    "-Xep:DefaultCharset:ERROR",
    "-Xep:DefaultLocale:ERROR",
    "-Xep:UnsafeLocaleUsage:ERROR",
    "-Xep:ClassInitializationDeadlock:ERROR",
    "-Xep:ThreadLocalUsage:ERROR",
    "-Xep:StaticAssignmentInConstructor:ERROR",
    "-Xep:DoubleCheckedLocking:ERROR",
]

def data_java_library(name, deps = [], plugins = [], **kwargs):
    java_library(
        name = name,
        deps = deps + ["@maven//:org_jspecify_jspecify", "//bazel:truffle_processor"],
        plugins = plugins + ["//bazel:nullaway", "//bazel:trufflecheck"],
        javacopts = NULLAWAY_OPTS + TRUFFLECHECK_OPTS,
        **kwargs
    )
