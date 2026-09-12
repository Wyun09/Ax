package supervisor

import (
	"reflect"
	"testing"
)

func TestRingTail(t *testing.T) {
	r := NewRing(3)
	_, _ = r.Write([]byte("one\ntwo\nthree\nfour\n"))
	got := r.Tail(2)
	want := []string{"three", "four"}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("Tail(2) = %#v, want %#v", got, want)
	}
}
