//go:build linux

package supervisor

import (
	"os"
	"testing"
)

func TestReadProcMetrics(t *testing.T) {
	metrics, err := readProcMetrics(os.Getpid())
	if err != nil {
		t.Fatal(err)
	}
	if metrics.totalTicks == 0 {
		t.Fatal("totalTicks is zero")
	}
	if metrics.rssBytes == 0 {
		t.Fatal("rssBytes is zero")
	}
}
