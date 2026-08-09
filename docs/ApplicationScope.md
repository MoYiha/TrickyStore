# Application Scope

## Purpose

Application Scope controls which Android application users may receive certificate or identity compatibility handling. Targeted mode is the preferred default because it limits work and reduces compatibility risk.

## Targeted mode

The `target.txt` file contains exact package names or bounded wildcard rules. A package is resolved through Android Package Manager to the calling user identifier. The module then applies its policy to that Android user rather than trusting a package name supplied by the caller.

Applications that share one Android user identifier also share the same process identity from the perspective of Binder. If one package in that shared identity matches a rule, the decision applies to the shared caller. This follows Android security semantics.

## Global mode

Global mode targets application user identifiers without requiring an entry in `target.txt`. System identities and protected infrastructure remain outside substitution scope. Unknown package resolution fails closed, so a transient Package Manager failure does not turn into a broad hook decision.

Global mode is useful for controlled testing. It increases the number of Binder calls that require a policy decision and can expose applications that never needed compatibility handling. Targeted mode is more predictable for daily use.

## Live updates

Package rules are parsed into a prefix trie. Decision results are cached for a short bounded period. Updating the target file or changing global mode replaces the policy state and its cache together, preventing readers from observing results created for an older rule set.

All file input is size limited, validated, and read only from a regular file. Invalid updates leave the previous valid state active.

[Return to the project overview](../README.md)
