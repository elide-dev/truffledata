// Package gazelle maintains Java source-set targets. Dependencies are explicit adapter boundaries.
package gazelle

import (
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"

	"github.com/bazelbuild/bazel-gazelle/config"
	"github.com/bazelbuild/bazel-gazelle/label"
	"github.com/bazelbuild/bazel-gazelle/language"
	"github.com/bazelbuild/bazel-gazelle/repo"
	"github.com/bazelbuild/bazel-gazelle/resolve"
	"github.com/bazelbuild/bazel-gazelle/rule"
)

type sourceSet struct {
	Directory string   `json:"directory"`
	Root      string   `json:"root"`
	Name      string   `json:"name"`
	Deps      []string `json:"deps"`
	Plugins   []string `json:"plugins"`
	Testonly  bool     `json:"testonly"`
}
type java struct {
	sets []sourceSet
	root string
}

func NewLanguage() language.Language                              { return &java{} }
func (*java) Name() string                                        { return "truffledata_java" }
func (*java) RegisterFlags(*flag.FlagSet, string, *config.Config) {}
func (j *java) CheckFlags(_ *flag.FlagSet, c *config.Config) error {
	j.root = c.RepoRoot
	data, err := os.ReadFile(filepath.Join(j.root, "bazel/gazelle/sources.json"))
	if err != nil {
		return err
	}
	return json.Unmarshal(data, &j.sets)
}
func (*java) KnownDirectives() []string                                           { return nil }
func (*java) Configure(*config.Config, string, *rule.File)                        {}
func (*java) Fix(*config.Config, *rule.File)                                      {}
func (*java) Imports(*config.Config, *rule.Rule, *rule.File) []resolve.ImportSpec { return nil }
func (*java) Embeds(*rule.Rule, label.Label) []label.Label                        { return nil }
func (*java) Resolve(*config.Config, *resolve.RuleIndex, *repo.RemoteCache, *rule.Rule, interface{}, label.Label) {
}
func (*java) Loads() []rule.LoadInfo {
	return []rule.LoadInfo{{Name: "//bazel:java.bzl", Symbols: []string{"data_java_library"}}}
}
func (*java) Kinds() map[string]rule.KindInfo {
	return map[string]rule.KindInfo{"data_java_library": {
		MatchAttrs: []string{"name"}, NonEmptyAttrs: map[string]bool{"srcs": true},
		MergeableAttrs: map[string]bool{"srcs": true, "deps": true, "plugins": true, "testonly": true},
	}}
}
func (j *java) GenerateRules(args language.GenerateArgs) language.GenerateResult {
	result := language.GenerateResult{}
	for _, set := range j.sets {
		if set.Directory != args.Rel {
			continue
		}
		var sources []string
		err := filepath.WalkDir(filepath.Join(j.root, set.Directory, set.Root), func(path string, entry os.DirEntry, err error) error {
			if err != nil {
				return err
			}
			if !entry.IsDir() && strings.HasSuffix(path, ".java") {
				relative, err := filepath.Rel(filepath.Join(j.root, set.Directory), path)
				if err != nil {
					return err
				}
				sources = append(sources, filepath.ToSlash(relative))
			}
			return nil
		})
		if err != nil {
			panic(fmt.Sprintf("source set %s: %v", set.Directory, err))
		}
		sort.Strings(sources)
		r := rule.NewRule("data_java_library", set.Name)
		r.SetAttr("srcs", sources)
		r.SetAttr("deps", set.Deps)
		if set.Testonly {
			r.SetAttr("testonly", true)
		}
		if len(set.Plugins) != 0 {
			r.SetAttr("plugins", set.Plugins)
		}
		result.Gen = append(result.Gen, r)
		result.Imports = append(result.Imports, nil)
	}
	return result
}
