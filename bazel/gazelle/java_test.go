package gazelle

import (
	"github.com/bazelbuild/bazel-gazelle/config"
	"github.com/bazelbuild/bazel-gazelle/language"
	"os"
	"path/filepath"
	"reflect"
	"testing"
)

func TestSourceMembershipAndAdapterBoundary(t *testing.T) {
	root := t.TempDir()
	if err := os.MkdirAll(filepath.Join(root, "core/src/p"), 0755); err != nil {
		t.Fatal(err)
	}
	for _, name := range []string{"Z.java", "A.java", "package-info.java", "ignore.txt"} {
		if err := os.WriteFile(filepath.Join(root, "core/src/p", name), []byte("package p;"), 0644); err != nil {
			t.Fatal(err)
		}
	}
	j := &java{root: root, sets: []sourceSet{{Directory: "core", Root: "src", Name: "core", Deps: []string{"@maven//:truffle"}}}}
	generated := j.GenerateRules(language.GenerateArgs{Config: &config.Config{RepoRoot: root}, Rel: "core"})
	if len(generated.Gen) != 1 {
		t.Fatalf("expected one source-set target: %v", generated)
	}
	r := generated.Gen[0]
	want := []string{"src/p/A.java", "src/p/Z.java", "src/p/package-info.java"}
	if !reflect.DeepEqual(r.AttrStrings("srcs"), want) {
		t.Fatalf("sources: %v", r.AttrStrings("srcs"))
	}
	if !reflect.DeepEqual(r.AttrStrings("deps"), []string{"@maven//:truffle"}) {
		t.Fatal("adapter boundary changed")
	}
	if len(j.GenerateRules(language.GenerateArgs{Rel: "other"}).Gen) != 0 {
		t.Fatal("modified an unowned package")
	}
}
